package dev.ambitionsoftware.tymeboxed.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale
import dev.ambitionsoftware.tymeboxed.service.inapp.InAppBlockingEnforcementGate
import dev.ambitionsoftware.tymeboxed.service.inapp.InAppBlockingHandler
import dev.ambitionsoftware.tymeboxed.service.inapp.InAppBlockingPreferencesReader
import dev.ambitionsoftware.tymeboxed.service.inapp.InAppToggleKeys

/**
 * Core app-blocking engine, modeled after Switchly's SwitchlyAccessibilityService.
 *
 * Listens for multiple accessibility event types to reliably detect
 * the foreground app. When a blocked package is detected, launches a
 * full-screen [BlockerActivity] overlay that says "This app is blocked
 * by Tyme Boxed" (instead of force-closing the app).
 *
 * Key robustness features (aligned with Switchly):
 * - Programmatic `serviceInfo` configuration in `onServiceConnected`
 * - 1-second heartbeat tick for continuous enforcement + keepalive
 * - Polls `rootInActiveWindow` as a fallback for OEMs with unreliable transitions
 * - Polls `UsageStatsManager` events as a secondary fallback
 * - Event deduplication to reduce overhead under event storms
 * - Screen-off / keyguard-locked awareness
 * - Shows overlay instead of killing the blocked app
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** Background thread for UsageStats queries (the binder can stall on some OEMs). */
    private var usageWorkerThread: HandlerThread? = null
    private var usageWorker: Handler? = null
    @Volatile private var topRefreshInFlight = false

    /**
     * Latched true between [onCreate]/[onServiceConnected] and [onDestroy].
     * Used to refuse any cross-thread work that arrives after the service has
     * started shutting down. Without this, a UsageStats response from
     * [usageWorker] can land on the main handler after [onDestroy] and try to
     * post back to a quit looper — see audit #22 (HandlerThread race on destroy).
     */
    @Volatile private var isShuttingDown: Boolean = false

    /**
     * Most recent accessibility event payload, parked here so the heavy tree /
     * URL extraction can run on the [usageWorker] background thread instead of
     * the accessibility dispatch thread (see audit #21).
     */
    @Volatile private var lastEventTimestamp: Long = 0L

    private lateinit var pm: PowerManager
    private var km: KeyguardManager? = null

    // Current foreground package as tracked by events + polling
    @Volatile private var currentTopPkg: String? = null

    // Event deduplication
    private var lastEventPkg: String? = null
    private var lastEventAt: Long = 0L
    private var lastEventType: Int = 0
    private var lastTransitionAt: Long = 0L

    // Per-package enforcement cadence (prevents duplicate blocks during event storms)
    private val lastEnforceAtByPkg = HashMap<String, Long>()

    /**
     * Minimum gap between two website-tree scans for the same package. Browser
     * scrolls fire hundreds of TYPE_VIEW_SCROLLED events; without throttling
     * here the URL extraction walks the entire accessibility tree on every
     * event and risks ANRs on mid-range devices (audit #21).
     */
    private val lastWebsiteScanAtByPkg = HashMap<String, Long>()
    private val WEBSITE_SCAN_MIN_GAP_MS = 120L

    /**
     * Throttle for [enforceBlockedPipWindows] — interactive-window enumeration
     * walks every window root and is one of the heaviest things this service
     * does, so even at 1Hz it can ANR on slow OEMs while a YouTube PiP is up.
     */
    private var lastPipScanAt: Long = 0L
    private val PIP_SCAN_MIN_GAP_MS = 750L

    // Debounce blocker overlay launches
    private val lastBlockShownAt = HashMap<String, Long>()
    private var lastGlobalBlockTs: Long = 0L

    private val BLOCK_SHOWN_COOLDOWN_MS = 800L

    // Website blocking (browser URL bar) — state mirrored from Switchly
    private val BROWSER_DOMAIN_CONFIRM_MS = 700L
    private var browserCandidateDomain: String? = null
    private var browserCandidateSince: Long = 0L
    private val lastWebsiteSurfaceBlockAt = HashMap<String, Long>()

    // UsageEvents foreground refresh cadence
    private var lastTopRefreshAt: Long = 0L
    private val TOP_REFRESH_INTERVAL_MS = 3_000L

    // -----------------------------------------------------------------------
    // Service lifecycle
    // -----------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AppBlockerAccessibilityService connected.")

        isShuttingDown = false
        pm = getSystemService(POWER_SERVICE) as PowerManager
        km = getSystemService(KeyguardManager::class.java)

        // Programmatically configure — overrides XML so we can be sure it's correct.
        // This matches Switchly's onServiceConnected configuration.
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        // Start the background thread for UsageStats polling.
        // We bind the Handler to the freshly-created thread's looper without a
        // force-unwrap so a startup race (e.g. thread creation failure on a
        // resource-starved device) doesn't crash the service.
        usageWorkerThread?.quitSafely()
        val workerThread = HandlerThread("tymeboxed-usage-worker").apply { start() }
        usageWorkerThread = workerThread
        usageWorker = Handler(workerThread.looper)
        topRefreshInFlight = false

        // Mark alive and start heartbeat tick
        ActiveBlockingState.markHeartbeat()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1_000L)
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onDestroy() {
        // Flip the gate *before* tearing down so any in-flight callback that
        // is about to post back to the main handler bails out (audit #22:
        // HandlerThread race). Worker code below double-checks this flag.
        isShuttingDown = true

        handler.removeCallbacks(tick)
        handler.removeCallbacks(websiteResweepRunnable)
        // Drain queued worker posts before quitting the looper so a stray
        // accessibility-event probe doesn't try to run after quitSafely().
        val worker = usageWorker
        usageWorker = null
        runCatching { worker?.removeCallbacksAndMessages(null) }
        usageWorkerThread?.quitSafely()
        usageWorkerThread = null
        topRefreshInFlight = false

        ActiveBlockingState.markDisconnected()
        Log.i(TAG, "AppBlockerAccessibilityService destroyed.")
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Heartbeat tick — runs every 1 second
    // -----------------------------------------------------------------------

    /** Second pass ~350ms after each tick while in a browser — URL bar often updates after the first tree read. */
    private val websiteResweepRunnable = Runnable {
        val pkg = currentTopPkg
        if (!pkg.isNullOrBlank() && pkg != packageName && ActiveBlockingState.hasDomainRules() &&
            WebsiteBlockingSupport.isBrowserPackage(pkg)
        ) {
            maybeBlock(pkg, event = null)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                ActiveBlockingState.markHeartbeat()

                // Re-enforce blocking on the current foreground package.
                // This catches: schedule boundary changes, profile edits while
                // user is inside a blocked app, and OEMs that miss transitions.
                enforceCurrentForeground()

                // PiP/minimized playback can keep running even when the foreground package is not YouTube
                // (e.g. SystemUI / launcher / another app). Scan interactive windows to catch it.
                enforceBlockedPipWindows()

                val p = currentTopPkg
                if (!p.isNullOrBlank() && ActiveBlockingState.hasDomainRules() &&
                    WebsiteBlockingSupport.isBrowserPackage(p)
                ) {
                    handler.removeCallbacks(websiteResweepRunnable)
                    handler.postDelayed(websiteResweepRunnable, 350L)
                }

                // Poll rootInActiveWindow as a secondary foreground signal
                pollActiveWindowPackage()

                // Strict uninstall: foreground package can disagree across OEM overlays (SysUI sheet,
                // secondary windows). Probe every candidate package once per tick (iOS: denyAppRemoval).
                maybeStrictUninstallFromForegroundCandidates()

                // Poll UsageEvents as a tertiary fallback
                refreshTopPackageViaUsageEvents()

            } catch (e: Throwable) {
                Log.w(TAG, "Tick error: ${e.message}")
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    /**
     * Re-check the current foreground app. Covers the case where a profile
     * changes or a schedule boundary flips while the user is already inside
     * a blocked app (no new accessibility event is emitted in that case).
     */
    private fun enforceCurrentForeground() {
        val pkg = currentTopPkg ?: return
        if (pkg.isBlank() || pkg == packageName) return
        maybeBlock(pkg, event = null)
    }

    /**
     * Detect blocked apps that can run in PiP/minimized mode (notably YouTube).
     *
     * In PiP, the "foreground" package often becomes the host app or SystemUI, so normal
     * foreground detection won't see YouTube. Accessibility interactive windows still expose
     * the PiP window root with the original app's package name on many devices.
     */
    private fun enforceBlockedPipWindows() {
        // Don't enforce when screen is off or device is locked
        if (!pm.isInteractive) return
        if (km?.isKeyguardLocked == true) return

        val snap = ActiveBlockingState.current
        val standaloneInApp = InAppBlockingEnforcementGate.isStandaloneModeActive(applicationContext)
        if ((!snap.isBlocking || snap.isPauseActive) && !standaloneInApp) return

        // PiP detection is expensive (walks every interactive window root).
        // Throttle independently of the tick so we don't ANR on slow OEMs.
        val now = System.currentTimeMillis()
        if (now - lastPipScanAt < PIP_SCAN_MIN_GAP_MS) return
        lastPipScanAt = now

        val top = currentTopPkg
        val shouldBlockYouTubeApp = ActiveBlockingState.shouldBlock(InAppToggleKeys.YOUTUBE)
        val shouldBlockYouTubePipToggle =
            InAppBlockingPreferencesReader.isEnabled(applicationContext, InAppToggleKeys.KEY_BLOCK_YT_PIP, false)

        if (!shouldBlockYouTubeApp && !shouldBlockYouTubePipToggle) return

        val inPip = InAppBlockingHandler.isYouTubePictureInPictureActive(this, event = null)
        val foundYouTubeWindow = hasYouTubeInteractiveWindow()
        val youtubeFloatingWhileBackground = foundYouTubeWindow && top != InAppToggleKeys.YOUTUBE

        // PiP toggle: block while PiP is active (even if UsageStats still reports YouTube foreground)
        // or when a floating YouTube window remains after leaving the app.
        if (shouldBlockYouTubePipToggle && (inPip || youtubeFloatingWhileBackground)) {
            maybePauseMediaForBlockedPkg(InAppToggleKeys.YOUTUBE)
            runCatching { dismissPipWindowsForPackage(InAppToggleKeys.YOUTUBE) }
            if (InAppBlockingHandler.blockYouTubePipIfNeeded(this, event = null)) return
        }

        // Whole-app block: close floating/mini-player when another app is foreground.
        if (shouldBlockYouTubeApp && youtubeFloatingWhileBackground) {
            maybePauseMediaForBlockedPkg(InAppToggleKeys.YOUTUBE)
            runCatching { dismissPipWindowsForPackage(InAppToggleKeys.YOUTUBE) }
            showBlockerOverlay(InAppToggleKeys.YOUTUBE)
        }
    }

    private fun hasYouTubeInteractiveWindow(): Boolean {
        return try {
            windows?.any { w ->
                val r = w.root ?: return@any false
                try {
                    r.packageName?.toString() == InAppToggleKeys.YOUTUBE
                } finally {
                    runCatching { r.recycle() }
                }
            } == true
        } catch (_: Throwable) {
            false
        }
    }

    private data class NodeWorkItem(val node: AccessibilityNodeInfo, val depth: Int, val owned: Boolean)

    /**
     * Best-effort attempt to dismiss a PiP/minimized floating window for [pkg] by scanning
     * interactive window roots and triggering ACTION_DISMISS or a "close" click target.
     *
     * This is intentionally heuristic: PiP is often hosted by SystemUI, and vendors vary.
     */
    private fun dismissPipWindowsForPackage(pkg: String) {
        val ws = windows ?: return

        val closeNeedles = listOf("close", "dismiss", "remove", "exit", "x")

        fun nodeText(node: AccessibilityNodeInfo): String {
            val t = node.text?.toString().orEmpty()
            val cd = node.contentDescription?.toString().orEmpty()
            return "$t $cd".trim().lowercase(Locale.getDefault())
        }

        fun nodeSupportsDismiss(node: AccessibilityNodeInfo): Boolean {
            val fromList = runCatching {
                node.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_DISMISS } == true
            }.getOrDefault(false)
            val fromMask = runCatching {
                (node.actions and AccessibilityNodeInfo.ACTION_DISMISS) != 0
            }.getOrDefault(false)
            return fromList || fromMask
        }

        fun tryDismissFromRoot(root: AccessibilityNodeInfo): Boolean {
            val stack = ArrayDeque<NodeWorkItem>()
            stack.addLast(NodeWorkItem(root, 0, false))
            var visited = 0
            val maxNodes = 220
            val maxDepth = 14

            while (stack.isNotEmpty() && visited < maxNodes) {
                val item = stack.removeLast()
                val n = item.node
                try {
                    visited++

                    if (nodeSupportsDismiss(n)) {
                        if (runCatching { n.performAction(AccessibilityNodeInfo.ACTION_DISMISS) }.getOrDefault(false)) {
                            return true
                        }
                    }

                    val label = nodeText(n)
                    if (label.isNotBlank() && closeNeedles.any { label.contains(it) } && n.isClickable) {
                        if (runCatching { n.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)) {
                            return true
                        }
                    }

                    if (item.depth >= maxDepth) continue
                    val childCount = runCatching { n.childCount }.getOrDefault(0)
                    for (i in childCount - 1 downTo 0) {
                        if (visited + stack.size >= maxNodes) break
                        val child = runCatching { n.getChild(i) }.getOrNull() ?: continue
                        stack.addLast(NodeWorkItem(child, item.depth + 1, true))
                    }
                } finally {
                    if (item.owned) runCatching { n.recycle() }
                }
            }
            return false
        }

        // 1) Prefer roots that *are* the app's window (common on some devices).
        for (w in ws) {
            val r = w.root ?: continue
            try {
                if (r.packageName?.toString() == pkg) {
                    if (tryDismissFromRoot(r)) return
                }
            } finally {
                runCatching { r.recycle() }
            }
        }

        // 2) Fallback: scan all roots (PiP controls are often in SystemUI).
        for (w in ws) {
            val r = w.root ?: continue
            try {
                if (tryDismissFromRoot(r)) return
            } finally {
                runCatching { r.recycle() }
            }
        }
    }

    /**
     * Some apps produce very few accessibility events. Poll the active window
     * as a reliable secondary signal for the true foreground package.
     */
    private fun pollActiveWindowPackage() {
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return
        try {
            val rootPkg = root.packageName?.toString()
            if (rootPkg.isNullOrBlank() || rootPkg == packageName) return
            if (rootPkg != currentTopPkg) {
                InAppBlockingHandler.onAppTransition(rootPkg, System.currentTimeMillis())
                currentTopPkg = rootPkg
                maybeBlock(rootPkg, event = null)
            }
        } finally {
            runCatching { root.recycle() }
        }
    }

    /**
     * Some devices report launcher/installer/sysui across different windows; [rootInActiveWindow]
     * alone misses uninstall sheets. Collect distinct packages from the active root, event-tracked
     * foreground, and (API 24+) all interactive window roots, then run strict interception once each.
     */
    private fun maybeStrictUninstallFromForegroundCandidates() {
        if (!ActiveBlockingState.current.isBlocking || !ActiveBlockingState.current.strictModeEnabled) {
            return
        }
        val myPkg = packageName
        val candidates = LinkedHashSet<String>()
        val activeRoot = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        try {
            activeRoot?.packageName?.toString()?.let { candidates.add(it) }
        } finally {
            runCatching { activeRoot?.recycle() }
        }
        currentTopPkg?.let { candidates.add(it) }
        try {
            windows?.forEach { w ->
                val r = w.root ?: return@forEach
                try {
                    r.packageName?.toString()?.let { candidates.add(it) }
                } finally {
                    runCatching { r.recycle() }
                }
            }
        } catch (_: Throwable) {
            /* ignore */
        }
        for (pkg in candidates) {
            if (pkg.isBlank() || pkg == myPkg) continue
            if (StrictUninstallInterception.interceptIfNeeded(this, pkg)) break
        }
    }

    /**
     * Periodically resolve the foreground app via UsageStatsManager.
     * This is the most reliable cross-device signal (catches OEMs that
     * miss accessibility transitions, especially when switching via Recents).
     */
    private fun refreshTopPackageViaUsageEvents() {
        if (isShuttingDown) return
        val now = System.currentTimeMillis()
        if (now - lastTopRefreshAt < TOP_REFRESH_INTERVAL_MS) return
        if (topRefreshInFlight) return

        lastTopRefreshAt = now
        topRefreshInFlight = true

        val start = (now - 10_000L).coerceAtLeast(0L)
        val posted = usageWorker?.post {
            // Service might be tearing down between schedule and execute.
            if (isShuttingDown) {
                topRefreshInFlight = false
                return@post
            }
            val top = try {
                resolveTopPackageFromUsageEvents(start, now)
            } catch (_: Throwable) {
                null
            }
            topRefreshInFlight = false
            if (top.isNullOrBlank()) return@post

            // Hop back to the main handler so foreground tracking stays single-
            // threaded — but the main thread could ALSO be gone (handler still
            // exists but our callbacks were already removed). Guard with the
            // same flag plus a no-op if we're shutting down by the time we run.
            if (isShuttingDown) return@post
            handler.post {
                if (isShuttingDown) return@post
                if (top != currentTopPkg && top != packageName) {
                    InAppBlockingHandler.onAppTransition(top, System.currentTimeMillis())
                    currentTopPkg = top
                    maybeBlock(top, event = null)
                }
            }
        } ?: false
        if (!posted) {
            topRefreshInFlight = false
        }
    }

    /** [UsageEvents.Event.ACTIVITY_RESUMED] on API 29+; legacy [MOVE_TO_FOREGROUND] below. */
    private fun isUsageForegroundEvent(event: UsageEvents.Event): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
        }
        @Suppress("DEPRECATION")
        return event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    private fun resolveTopPackageFromUsageEvents(start: Long, end: Long): String? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val events = try {
            usm.queryEvents(start, end)
        } catch (_: SecurityException) {
            return null
        }
        val e = UsageEvents.Event()
        var lastPkg: String? = null
        var lastTs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (!isUsageForegroundEvent(e)) continue
            if (e.timeStamp >= lastTs && !e.packageName.isNullOrBlank()) {
                lastTs = e.timeStamp
                lastPkg = e.packageName
            }
        }
        return lastPkg
    }

    // -----------------------------------------------------------------------
    // Accessibility event handling
    // -----------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Refresh heartbeat on every event
        ActiveBlockingState.markHeartbeat()

        val pkg = event?.packageName?.toString()?.trim().orEmpty()
        if (pkg.isBlank()) return
        if (pkg == packageName) return  // avoid loops

        val pendingBackCount = BlockerActivity.consumePendingBackNavigationFor(pkg)
        if (pendingBackCount > 0) {
            InAppBlockingHandler.consumePendingBackNavigation(this, pkg, pendingBackCount)
            return
        }

        val now = System.currentTimeMillis()
        val type = event?.eventType ?: 0
        val websiteRulesActive = WebsiteBlockingSupport.isBrowserPackage(pkg) &&
            ActiveBlockingState.hasDomainRules()
        val domainRulesNeedProbe = websiteRulesActive

        // ── Event deduplication ──
        // Identical WINDOW_CONTENT_CHANGED from the same package within 100ms → skip
        if (pkg == lastEventPkg) {
            val dt = now - lastEventAt
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && dt < 100L && !domainRulesNeedProbe) {
                return
            }
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type == lastEventType && dt < 50L
            ) return
        }

        lastEventPkg = pkg
        lastEventAt = now
        lastEventType = type

        // Update foreground tracking
        if (pkg != currentTopPkg) {
            InAppBlockingHandler.onAppTransition(pkg, now)
        }
        currentTopPkg = pkg

        if (!WebsiteBlockingSupport.isBrowserPackage(pkg)) {
            browserCandidateDomain = null
            browserCandidateSince = 0L
        }

        val isTransition =
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        if (isTransition) {
            lastTransitionAt = now
            maybeBlock(pkg, event)
            // PiP often surfaces as WINDOWS_CHANGED without a reliable activity class name.
            if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                (pkg == InAppToggleKeys.YOUTUBE && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            ) {
                enforceBlockedPipWindows()
            }
            return
        }

        // WINDOW_CONTENT_CHANGED: (1) short post-transition app flash, (2) ongoing browser
        // navigations — URL updates rarely come with TYPE_WINDOW_STATE_CHANGED, so we must
        // keep probing while a browser with domain rules is foreground.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val postTransitionFlash = now - lastTransitionAt <= 500L
            if (postTransitionFlash || domainRulesNeedProbe) {
                maybeBlock(pkg, event)
            }
            return
        }

        // In-app surface blocking (YouTube search/Shorts, IG, X, Snap) needs interactive events.
        // Unlike browsers below, these packages never set [websiteRulesActive], so without this
        // branch only transitions + a 500ms post-transition WC flash ran [maybeBlock]; a tap on
        // Search after that window produced no enforcement until the 1s heartbeat (often missing
        // the click/focus signal entirely).
        if (InAppToggleKeys.isSupportedApp(pkg)) {
            when (type) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                -> maybeBlock(pkg, event)
                else -> Unit
            }
        }

        // Scroll/click in browser: URL / chrome UI updates without WINDOW_CONTENT_CHANGED.
        if (websiteRulesActive) {
            when (type) {
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                -> maybeBlock(pkg, event)
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    if (pkg.startsWith("org.mozilla.")) {
                        maybeBlock(pkg, event)
                    }
                }
                else -> Unit
            }
        }
    }

    // -----------------------------------------------------------------------
    // Blocking logic
    // -----------------------------------------------------------------------

    /**
     * Check whether [pkg] should be blocked and act on it.
     * Instead of force-closing, launches the [BlockerActivity] overlay.
     */
    private fun maybeBlock(pkg: String, event: AccessibilityEvent?) {
        // Don't enforce when screen is off or device is locked
        if (!pm.isInteractive) return
        if (km?.isKeyguardLocked == true) return

        if (StrictUninstallInterception.interceptIfNeeded(this, pkg)) {
            return
        }

        // Never block system-critical packages (see strict-mode uninstall interception above)
        if (pkg in NEVER_BLOCK) return

        if (InAppToggleKeys.isSupportedApp(pkg)) {
            if (withAccessibilityRoot(event) { root ->
                    InAppBlockingHandler.maybeBlock(this, pkg, event, root)
                } == true
            ) {
                return
            }
        }

        // Website rules run **before** the app-level enforcement throttle. Otherwise the first
        // probe often sees an empty URL bar and later updates within 150ms are dropped, so we
        // never block (common on Chrome after in-page navigation).
        // Audit #21: cap how often the browser-tree URL extraction runs so a
        // scroll storm can't pin the accessibility thread. The 120ms gap is
        // short enough that genuine URL transitions still register quickly.
        val websiteCtx = WebsiteBlockingSupport.isBrowserPackage(pkg) && ActiveBlockingState.hasDomainRules()
        if (websiteCtx) {
            val now = System.currentTimeMillis()
            val lastScan = lastWebsiteScanAtByPkg[pkg] ?: 0L
            val isTransition = event == null ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            if (isTransition || now - lastScan >= WEBSITE_SCAN_MIN_GAP_MS) {
                lastWebsiteScanAtByPkg[pkg] = now
                if (maybeBlockWebsite(pkg, event)) return
            }
        }

        // Domain rules apply only inside recognized browsers — not to native apps. Otherwise
        // blocking e.g. instagram.com would also block the Instagram app, which users expect
        // to control separately via the app block list.

        // Enforcement cadence: avoid duplicate app-level blocks during event storms
        if (!shouldRunEnforcement(pkg)) return

        // Check if blocking is active for this package
        if (!ActiveBlockingState.shouldBlock(pkg)) return

        maybePauseMediaForBlockedPkg(pkg)
        showBlockerOverlay(pkg)
    }

    /**
     * For certain apps (e.g. YouTube), a soft overlay does not stop audio/video playback
     * when the content is running in PiP/minimized mode. Best-effort: dispatch media pause/stop.
     */
    private fun maybePauseMediaForBlockedPkg(pkg: String) {
        if (pkg != InAppToggleKeys.YOUTUBE) return
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
        }
        // If YouTube is in PiP/minimized mode, pausing can freeze the window on-screen.
        // Best-effort dismissal keeps the UI clean while the blocker overlay is shown.
        runCatching { dismissPipWindowsForPackage(pkg) }
    }

    /**
     * Resolves the best accessibility root for the current UI. Caller must [AccessibilityNodeInfo.recycle]
     * the returned node — prefer [withAccessibilityRoot] so recycling is automatic.
     */
    private fun currentRoot(event: AccessibilityEvent?): AccessibilityNodeInfo? {
        return rootInActiveWindow
            ?: event?.source
            ?: runCatching { windows?.firstOrNull { it.isActive }?.root }.getOrNull()
            ?: runCatching { windows?.firstOrNull()?.root }.getOrNull()
    }

    /**
     * Runs [block] with a resolved accessibility root and always recycles it afterward
     * ([rootInActiveWindow], [AccessibilityEvent.getSource], and window roots must be recycled).
     */
    private inline fun <T> withAccessibilityRoot(
        event: AccessibilityEvent?,
        block: (AccessibilityNodeInfo) -> T,
    ): T? {
        val root = currentRoot(event) ?: return null
        try {
            return block(root)
        } finally {
            runCatching { root.recycle() }
        }
    }

    /**
     * @return true if a website block overlay was shown (caller should skip app-level block).
     */
    private fun maybeBlockWebsite(pkg: String, event: AccessibilityEvent?): Boolean =
        withAccessibilityRoot(event) { root ->
            if (WebsiteBlockingSupport.isBrowserAddressEditing(root, pkg, event)) {
                if (!pkg.startsWith("org.mozilla.")) {
                    browserCandidateDomain = null
                    browserCandidateSince = 0L
                }
                return@withAccessibilityRoot false
            }

            if (WebsiteBlockingSupport.shouldIgnoreEventForChromiumUrlField(pkg, event)) {
                return@withAccessibilityRoot false
            }

            val host = WebsiteBlockingSupport.tryExtractDomainFromBrowser(root, pkg, event) ?: run {
                browserCandidateDomain = null
                browserCandidateSince = 0L
                return@withAccessibilityRoot false
            }

            val now = System.currentTimeMillis()
            val needStability = WebsiteBlockingSupport.requiresStableDomainConfirmation(pkg)
            if (browserCandidateDomain != host) {
                browserCandidateDomain = host
                browserCandidateSince = now
                if (needStability) return@withAccessibilityRoot false
            }
            if (needStability && (now - browserCandidateSince < BROWSER_DOMAIN_CONFIRM_MS)) {
                return@withAccessibilityRoot false
            }

            if (!ActiveBlockingState.shouldBlockDomain(host)) return@withAccessibilityRoot false

            showWebsiteBlockOverlay(pkg, host)
            true
        } ?: false

    private fun showWebsiteBlockOverlay(pkg: String, host: String) {
        val now = System.currentTimeMillis()

        val blockKey = "$pkg|$host"
        if (BlockerActivity.isVisible && BlockerActivity.visibleBlockKey == blockKey) return

        val sk = blockKey
        val lastSurf = lastWebsiteSurfaceBlockAt[sk] ?: 0L
        if (now - lastSurf < BLOCK_SHOWN_COOLDOWN_MS) return

        val lastShown = lastBlockShownAt[pkg] ?: 0L
        if ((now - lastShown) < BLOCK_SHOWN_COOLDOWN_MS) return
        if ((now - lastGlobalBlockTs) < 250L) return

        lastWebsiteSurfaceBlockAt[sk] = now
        lastBlockShownAt[pkg] = now
        lastGlobalBlockTs = now

        Log.d(TAG, "Website block overlay for $host in $pkg")

        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        handler.postDelayed({
            runCatching {
                BlockerActivity.showForWebsite(this, pkg, host)
            }
        }, 30L)
    }

    /**
     * Rate-limit enforcement per package to avoid spamming the overlay
     * during event storms (many events can fire during a single app transition).
     */
    private fun shouldRunEnforcement(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val minGap = 150L  // At most one enforcement per 150ms per package
        val last = lastEnforceAtByPkg[pkg] ?: 0L
        if (now - last < minGap) return false
        lastEnforceAtByPkg[pkg] = now
        return true
    }

    /**
     * Show the blocker overlay instead of force-closing the app.
     * The overlay covers the blocked app with a "This app is blocked by
     * Tyme Boxed" message and a "Go Back" button that sends the user home.
     */
    private fun showBlockerOverlay(pkg: String) {
        val now = System.currentTimeMillis()

        // Don't re-launch if the overlay is already showing this same block target
        if (BlockerActivity.isVisible && BlockerActivity.visibleBlockKey == pkg) return

        // Debounce overlay launches
        val lastShown = lastBlockShownAt[pkg] ?: 0L
        if ((now - lastShown) < BLOCK_SHOWN_COOLDOWN_MS) return
        if ((now - lastGlobalBlockTs) < 250L) return

        lastBlockShownAt[pkg] = now
        lastGlobalBlockTs = now

        // Resolve the app's display name
        val label = try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Throwable) {
            pkg
        }

        Log.d(TAG, "Showing blocker overlay for $pkg ($label)")

        // Launch the blocker overlay activity
        BlockerActivity.show(this, pkg, label)
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    companion object {
        private const val TAG = "AppBlockerA11y"

        /**
         * Packages that must never be redirected, regardless of block list.
         * Blocking the launcher or system UI would soft-lock the device.
         * Expanded set covers common OEM launchers.
         */
        private val NEVER_BLOCK = setOf(
            "dev.ambitionsoftware.tymeboxed",       // our own app
            "com.android.systemui",                  // status bar, notifications
            "com.android.launcher3",                 // AOSP launcher
            "com.google.android.apps.nexuslauncher", // Pixel launcher
            "com.sec.android.app.launcher",          // Samsung launcher
            "com.samsung.android.app.routines",      // Samsung Routines
            "com.miui.home",                         // Xiaomi launcher
            "com.huawei.android.launcher",           // Huawei launcher
            "com.oppo.launcher",                     // Oppo launcher
            "com.realme.launcher",                   // Realme launcher
            "com.vivo.launcher",                     // Vivo launcher
            "com.oneplus.launcher",                  // OnePlus launcher
            "com.nothing.launcher",                  // Nothing launcher
            "com.teslacoilsw.launcher",              // Nova Launcher
            "com.microsoft.launcher",                // Microsoft Launcher
            "com.android.settings",                  // System settings
            "com.android.packageinstaller",          // Permission dialogs
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "android",                               // System process
            // Accessibility settings — must never be blocked or user can't
            // toggle accessibility on/off
            "com.android.server.accessibility",
            "com.samsung.accessibility",
        )
    }
}
