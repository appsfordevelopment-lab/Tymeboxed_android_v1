package dev.ambitionsoftware.tymeboxed.service.inapp

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.ambitionsoftware.tymeboxed.service.BlockerActivity
import dev.ambitionsoftware.tymeboxed.R
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * In-app surface blocking (YouTube, Instagram, X, Snapchat). Runs during an **active focus session**
 * (not during a break pause), or anytime when the user enables **Block without a session** in settings.
 * Heuristics adapted from [Switchly public](https://gitlab.com/Saltyy/switchly-public):
 * each [InAppToggleKeys] is evaluated independently; detection/scoring for a surface only runs
 * when that toggle is enabled so e.g. “Block Shorts” only affects the Shorts surface.
 */
object InAppBlockingHandler {

    private const val TAG = "InAppBlocking"

    private const val SURFACE_BLOCK_COOLDOWN_MS = 800L
    private const val SURFACE_CONFIRM_MS = 450L
    private const val INAPP_POST_BLOCK_GRACE_MS = 650L
    private const val SURFACE_HINT_TTL_MS = 900L
    /** Ignore passive search UI on cold start / tab switch; home toolbar mirrors these strings. */
    private const val YT_SEARCH_POST_ENTER_GRACE_MS = 1_200L
    /** Instagram: wait for nav UI to settle before transition-based surface probes. */
    private const val IG_SURFACE_SETTLE_MS = 350L

    /** Bottom-nav Shorts tab view-id fragments (locale-independent). */
    private val youtubeShortsNavViewIdTokens = listOf(
        "shorts_tab",
        "tab_shorts",
        "pivot_shorts",
        "shorts_pivot",
        "shorts_nav",
        "reel_short",
    )

    /** Bottom-nav Reels tab view-id fragments (locale-independent). */
    private val instagramReelsNavViewIdTokens = listOf(
        "reels_tab",
        "tab_reels",
        "clips_tab",
        "tab_clips",
        "reel_tab",
        "reels_nav",
    )

    private val instagramSearchFieldNeedles = listOf(
        "search",
        "suche",
        "buscar",
        "rechercher",
        "search...",
    )

    /** Present on the search sheet / results, not on the plain home feed header. */
    private val instagramSearchActiveSignals = listOf(
        "recent searches",
        "recent search",
        "clear all",
        "search accounts",
        "search accounts and tags",
        "accounts",
        "tags",
        "places",
        "try searching",
        "no results",
        "see more results",
        "not personalized",
    )

    private val instagramSearchViewIdTokens = listOf(
        "search_bar",
        "search_edit",
        "search_text",
        "search_input",
        "search_row",
        "search_container",
        "action_bar_search",
        "search_edit_text",
    )

    /** Full-screen story viewer chrome — not the passive story ring tray on home. */
    private val instagramStoriesViewerSignals = listOf(
        "send message",
        "nachricht senden",
        "send a message",
        "reply",
        "antworten",
        "reply to",
        "antwort an",
        "story settings",
        "view story",
        "seen by",
        "viewers",
        "share story",
        "story by",
        "pause story",
        "story paused",
    )

    private val instagramStoriesTrayLabels = listOf(
        "your story",
        "deine story",
        "tu historia",
        "votre story",
        "add to story",
        "story",
        "stories",
    )

    private val instagramStoriesViewIdTokens = listOf(
        "story_viewer",
        "story_view",
        "story_ring",
        "story_avatar",
        "story_tray",
        "stories_tray",
        "reel_viewer",
        "story_item",
        "story_interactive",
        "story_viewer_list",
        "story_reply",
    )

    private val youtubeSearchPhrases = listOf(
        "search youtube",
        "youtube durchsuchen",
        "search with your voice",
        "voice search",
        "search videos",
        "search channels",
        "videos & channels",
        "videos and channels",
        "buscar en youtube",
        "rechercher sur youtube",
    )

    /**
     * Matched along event-source ancestors; weak items only on [AccessibilityEvent.TYPE_VIEW_CLICKED]
     * so the home search icon does not fire from focus alone at launch.
     */
    private val youtubeSearchWeakLabels = listOf(
        "search",
        "suche",
        "recherche",
        "buscar",
    )

    /** Present in the expanded search sheet / results chrome — absent on the plain home feed. */
    private val youtubeSearchSecondarySignals = listOf(
        "recent searches",
        "recently searched",
        "clear search history",
        "delete recent searches",
        "trending searches",
        "letzte suchanfragen",
        "suchverlauf löschen",
        "search filters",
        "to start typing",
        "filters",
        "sort by",
        "upload date",
    )

    private val youtubeSearchFocusNeedles: List<String>
        get() = youtubeSearchPhrases + youtubeSearchWeakLabels

    /**
     * Labels for the **comment composer** only — not the collapsed "Comments" row on
     * the watch page (that caused false blocks while playing a video).
     */
    private val youtubeCommentComposerPhrases = listOf(
        "add a comment",
        "add comment",
        "write a comment",
        "enter a comment",
        "enter comment",
        "share your thoughts",
        "say something",
        "post a comment",
        "kommentar hinzufügen",
        "kommentar schreiben",
        "kommentar verfassen",
        "añadir un comentario",
        "escribe un comentario",
        "ajouter un commentaire",
        "écrire un commentaire",
    )

    /**
     * Shown when the comments sheet is open or the inline composer is visible — paired with a
     * focused input so we do not block on the passive "Comments" row during playback alone.
     */
    private val youtubeCommentEngagedPhrases = listOf(
        "sort comments",
        "sortieren",
        "add a comment",
        "add comment",
        "kommentar hinzufügen",
        "sort comments by",
    )

    /** Only when the field is focused + editable — not the passive "Comments" section title. */
    private val youtubeCommentComposerWeakPhrases = listOf(
        "comment",
        "kommentar",
        "comentario",
        "commentaire",
    )

    /** View IDs for the input box, not the comments section header. */
    private val youtubeCommentComposerViewIdTokens = listOf(
        "comment_composer",
        "comment_input",
        "comment_box",
        "comment_text",
        "commentbox",
        "add_comment",
        "reply_comment",
        "comment_edit",
        "edit_comment",
        "post_comment",
        "inline_comment",
        "simplebox",
        "simple_box",
        "placeholder_area",
        "comment_dialog",
        "engagement_panel",
        "engagement_panel_section",
        "comment_section",
        "comments_entry",
        "comment_entry",
    )

    @JvmField
    @Volatile
    var currentSurfaceKey: String? = null

    @JvmField
    @Volatile
    var currentSurfacePkg: String? = null

    // Accessibility events arrive on the accessibility service thread and
    // BlockerActivity callbacks can land on the main thread, so every shared
    // map must be thread-safe. Using ConcurrentHashMap also makes iteration
    // (e.g. clearing surface evidence) safe against concurrent mutation.
    private val surfaceEvidenceCount = ConcurrentHashMap<String, Int>()
    private val surfaceEvidenceAt = ConcurrentHashMap<String, Long>()
    private val lastSurfaceBlockAt = ConcurrentHashMap<String, Long>()
    private val lastBlockShownAt = ConcurrentHashMap<String, Long>()
    @Volatile
    private var lastGlobalBlockTs: Long = 0L
    private val inAppGraceUntilByPkg = ConcurrentHashMap<String, Long>()

    private val lastEnforceAtByPkg = ConcurrentHashMap<String, Long>()
    private val recentSurfaceHintKeyByPkg = ConcurrentHashMap<String, String>()
    private val recentSurfaceHintAtByPkg = ConcurrentHashMap<String, Long>()
    private val appEnteredAtByPkg = ConcurrentHashMap<String, Long>()

    /** Throttles the "block enabled but no signal fired" diagnostic log in [doSnap]. */
    private val lastSnapDiagAtBySurface = ConcurrentHashMap<String, Long>()
    private val SNAP_DIAG_THROTTLE_MS = 2_000L

    /** How often we sweep stale entries out of the in-memory caches. */
    private const val CACHE_PRUNE_INTERVAL_MS = 60_000L

    /** Drop timestamped entries older than this (block cooldowns, grace, hints). */
    private const val CACHE_ENTRY_TTL_MS = 3_600_000L

    /** Drop surface-evidence rows not refreshed within this window. */
    private const val SURFACE_EVIDENCE_TTL_MS = 300_000L

    /** Max rows per timestamp map after a prune (prevents unbounded growth). */
    private const val MAX_TIMESTAMP_MAP_ENTRIES = 64

    @Volatile
    private var lastCachePruneAt: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun onAppTransition(pkg: String, now: Long) {
        if (InAppToggleKeys.isSupportedApp(pkg)) {
            appEnteredAtByPkg[pkg] = now
            if (pkg == InAppToggleKeys.YOUTUBE) {
                clearSurfaceEvidence("yt:shorts")
            }
            if (pkg == InAppToggleKeys.INSTAGRAM) {
                clearSurfaceEvidence("ig:reels", "ig:search", "ig:stories")
                clearSurfaceHint(pkg)
            }
        }
        pruneCachesIfNeeded(now)
    }

    /**
     * Clears all in-app blocking caches (call when a focus session ends so state
     * does not leak across sessions).
     */
    fun resetCaches() {
        surfaceEvidenceCount.clear()
        surfaceEvidenceAt.clear()
        lastSurfaceBlockAt.clear()
        lastBlockShownAt.clear()
        inAppGraceUntilByPkg.clear()
        lastEnforceAtByPkg.clear()
        recentSurfaceHintKeyByPkg.clear()
        recentSurfaceHintAtByPkg.clear()
        appEnteredAtByPkg.clear()
        lastSnapDiagAtBySurface.clear()
        lastGlobalBlockTs = 0L
        currentSurfaceKey = null
        currentSurfacePkg = null
        lastCachePruneAt = 0L
    }

    private fun pruneCachesIfNeeded(now: Long) {
        if (now - lastCachePruneAt < CACHE_PRUNE_INTERVAL_MS) return
        lastCachePruneAt = now

        pruneTimestampMap(surfaceEvidenceAt, now, SURFACE_EVIDENCE_TTL_MS)
        surfaceEvidenceCount.keys.toList().forEach { key ->
            if (!surfaceEvidenceAt.containsKey(key)) surfaceEvidenceCount.remove(key)
        }

        pruneTimestampMap(lastSurfaceBlockAt, now, CACHE_ENTRY_TTL_MS)
        pruneTimestampMap(lastBlockShownAt, now, CACHE_ENTRY_TTL_MS)
        pruneTimestampMap(lastEnforceAtByPkg, now, CACHE_ENTRY_TTL_MS)
        pruneTimestampMap(lastSnapDiagAtBySurface, now, CACHE_ENTRY_TTL_MS)

        inAppGraceUntilByPkg.keys.toList().forEach { pkg ->
            val until = inAppGraceUntilByPkg[pkg] ?: return@forEach
            if (until < now || now - until > CACHE_ENTRY_TTL_MS) inAppGraceUntilByPkg.remove(pkg)
        }

        recentSurfaceHintAtByPkg.keys.toList().forEach { pkg ->
            val at = recentSurfaceHintAtByPkg[pkg] ?: return@forEach
            if (now - at > SURFACE_HINT_TTL_MS * 4) {
                recentSurfaceHintAtByPkg.remove(pkg)
                recentSurfaceHintKeyByPkg.remove(pkg)
            }
        }

        appEnteredAtByPkg.keys.toList().forEach { pkg ->
            val at = appEnteredAtByPkg[pkg] ?: return@forEach
            if (!InAppToggleKeys.isSupportedApp(pkg) || now - at > CACHE_ENTRY_TTL_MS) {
                appEnteredAtByPkg.remove(pkg)
            }
        }

        capTimestampMap(lastSurfaceBlockAt, MAX_TIMESTAMP_MAP_ENTRIES)
        capTimestampMap(lastBlockShownAt, MAX_TIMESTAMP_MAP_ENTRIES)
        capTimestampMap(lastEnforceAtByPkg, MAX_TIMESTAMP_MAP_ENTRIES)
        capTimestampMap(lastSnapDiagAtBySurface, MAX_TIMESTAMP_MAP_ENTRIES)
        capTimestampMap(surfaceEvidenceAt, MAX_TIMESTAMP_MAP_ENTRIES)
    }

    private fun pruneTimestampMap(
        map: ConcurrentHashMap<String, Long>,
        now: Long,
        ttlMs: Long,
    ) {
        map.keys.toList().forEach { key ->
            val ts = map[key] ?: return@forEach
            if (now - ts > ttlMs) map.remove(key)
        }
    }

    private fun capTimestampMap(map: ConcurrentHashMap<String, Long>, maxEntries: Int) {
        val excess = map.size - maxEntries
        if (excess <= 0) return
        map.entries
            .sortedBy { it.value }
            .take(excess)
            .forEach { map.remove(it.key) }
    }

    /**
     * True when YouTube is in picture-in-picture. Prefer [AccessibilityWindowInfo.isInPictureInPictureMode]
     * (API 26+); many YouTube builds never put "PictureInPicture" in [AccessibilityEvent.getClassName].
     */
    fun isYouTubePictureInPictureActive(
        service: AccessibilityService,
        event: AccessibilityEvent? = null,
    ): Boolean {
        if (youtubeWindowInPictureInPictureMode(service)) return true
        return youtubeEventSuggestsPictureInPicture(event)
    }

    /**
     * In-app PiP block ([Switchly](https://gitlab.com/Saltyy/switchly-public)-style soft surface).
     * Callable from the accessibility heartbeat when the foreground package is no longer YouTube.
     */
    fun blockYouTubePipIfNeeded(
        service: AccessibilityService,
        event: AccessibilityEvent? = null,
    ): Boolean {
        val c = service.applicationContext
        if (!prefs(c, InAppToggleKeys.KEY_BLOCK_YT_PIP)) return false
        if (!InAppBlockingEnforcementGate.shouldEnforceInAppSurfaces(c)) return false
        val now = System.currentTimeMillis()
        val pkg = InAppToggleKeys.YOUTUBE
        if (now < (inAppGraceUntilByPkg[pkg] ?: 0L)) return false
        if (!isYouTubePictureInPictureActive(service, event)) return false
        if (!shouldAllowInAppEnforce(pkg, event, now)) return false

        val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_pip)) ?: return false
        Log.d(TAG, "Blocking YouTube PiP (event=${event?.eventType} class=${event?.className})")
        softBlockSurface(
            service,
            pkg,
            safeAppLabel(service, pkg),
            m.first,
            m.second,
            backCount = 1,
        )
        return true
    }

    private fun youtubeWindowInPictureInPictureMode(service: AccessibilityService): Boolean {
        val ws = service.windows ?: return false
        for (w in ws) {
            try {
                if (!w.isInPictureInPictureMode) continue
                val r = w.root ?: continue
                try {
                    if (r.packageName?.toString() == InAppToggleKeys.YOUTUBE) return true
                } finally {
                    runCatching { r.recycle() }
                }
            } catch (_: Throwable) {
                /* ignore */
            }
        }
        return false
    }

    private fun youtubeEventSuggestsPictureInPicture(event: AccessibilityEvent?): Boolean {
        val cls = event?.className?.toString().orEmpty()
        if (cls.isBlank()) return false
        val lc = cls.lowercase(Locale.getDefault())
        return lc.contains("pictureinpicture") ||
            lc.contains("picture_in_picture") ||
            lc.contains("pipmode") ||
            lc.contains("pipactivity") ||
            lc.contains(".pip.") ||
            lc.endsWith("pip") ||
            (lc.contains("picture") && lc.contains("pip"))
    }

    /**
     * @return `true` if an in-app block was applied (session / website block should not run for this pass).
     */
    fun maybeBlock(
        service: AccessibilityService,
        pkg: String,
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo,
    ): Boolean {
        if (!InAppToggleKeys.isSupportedApp(pkg)) return false
        if (!packageHasAnyBlockEnabled(service, pkg)) return false
        if (!InAppBlockingEnforcementGate.shouldEnforceInAppSurfaces(service.applicationContext)) return false

        val now = System.currentTimeMillis()
        pruneCachesIfNeeded(now)
        if (now < (inAppGraceUntilByPkg[pkg] ?: 0L)) return false
        // Single cadence: two consecutive shouldThrottleEnforce() calls on the same pass
        // would both update the same timestamp, so the second (150ms) always failed and
        // in-app blocking never reached the per-app handlers.

        val nowEvent = now
        if (nowEvent - (appEnteredAtByPkg[pkg] ?: 0L) > 30_000L) {
            appEnteredAtByPkg[pkg] = nowEvent
        }

        val eventType = event?.eventType ?: 0
        captureSurfaceHintFromEvent(pkg, event, now)

        val isTransition =
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (event != null && !isTransition) {
            if (shouldSkipLowSignalInApp(pkg, event, now)) return false
        }

        if (!shouldAllowInAppEnforce(pkg, event, now)) return false

        return when (pkg) {
            InAppToggleKeys.YOUTUBE -> handleYouTube(service, root, event, now)
            InAppToggleKeys.INSTAGRAM -> handleInstagram(service, root, event, now)
            InAppToggleKeys.X_TWITTER -> handleX(service, root, event, now)
            InAppToggleKeys.SNAPCHAT -> handleSnapchat(service, root, event, now)
            else -> false
        }
    }

    private fun shouldSkipLowSignalInApp(pkg: String, event: AccessibilityEvent, now: Long): Boolean {
        if (pkg == InAppToggleKeys.YOUTUBE && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val cls = event.className?.toString().orEmpty()
            if (youtubeEventSuggestsPictureInPicture(event) ||
                cls.contains("picture", ignoreCase = true) ||
                cls.contains("pip", ignoreCase = true)
            ) {
                return false
            }
            return (now - (appEnteredAtByPkg[pkg] ?: 0L)) in 0..<120
        }
        return false
    }

    private fun shouldThrottleEnforce(pkg: String, now: Long, gap: Long): Boolean {
        val last = lastEnforceAtByPkg[pkg] ?: 0L
        if (now - last < gap) return false
        lastEnforceAtByPkg[pkg] = now
        return true
    }

    /** Never throttle tap/type/focus events — the first probe can arrive before the field is ready. */
    private fun shouldAllowInAppEnforce(pkg: String, event: AccessibilityEvent?, now: Long): Boolean {
        val et = event?.eventType ?: 0
        if (event != null &&
            (
                et == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                    et == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                )
        ) {
            return true
        }
        return shouldThrottleEnforce(pkg, now, 150L)
    }

    private fun prefs(c: android.content.Context, key: String) =
        InAppBlockingPreferencesReader.isEnabled(c, key, false)

    private fun packageHasAnyBlockEnabled(c: android.content.Context, pkg: String): Boolean {
        return when (pkg) {
            InAppToggleKeys.YOUTUBE -> listOf(
                InAppToggleKeys.KEY_BLOCK_YT_SHORTS, InAppToggleKeys.KEY_BLOCK_YT_SEARCH,
                InAppToggleKeys.KEY_BLOCK_YT_COMMENTS, InAppToggleKeys.KEY_BLOCK_YT_PIP,
            ).any { prefs(c, it) }
            InAppToggleKeys.INSTAGRAM -> listOf(
                InAppToggleKeys.KEY_BLOCK_IG_REELS, InAppToggleKeys.KEY_BLOCK_IG_EXPLORE,
                InAppToggleKeys.KEY_BLOCK_IG_SEARCH, InAppToggleKeys.KEY_BLOCK_IG_STORIES,
                InAppToggleKeys.KEY_BLOCK_IG_COMMENTS,
            ).any { prefs(c, it) }
            InAppToggleKeys.X_TWITTER -> listOf(
                InAppToggleKeys.KEY_BLOCK_X_HOME, InAppToggleKeys.KEY_BLOCK_X_SEARCH,
                InAppToggleKeys.KEY_BLOCK_X_GROK, InAppToggleKeys.KEY_BLOCK_X_NOTIFICATIONS,
            ).any { prefs(c, it) }
            InAppToggleKeys.SNAPCHAT -> listOf(
                InAppToggleKeys.KEY_BLOCK_SNAP_MAP, InAppToggleKeys.KEY_BLOCK_SNAP_STORIES,
                InAppToggleKeys.KEY_BLOCK_SNAP_SPOTLIGHT, InAppToggleKeys.KEY_BLOCK_SNAP_FOLLOWING,
            ).any { prefs(c, it) }
            else -> false
        }
    }

    private fun rememberSurfaceHint(pkg: String, key: String, now: Long) {
        recentSurfaceHintKeyByPkg[pkg] = key
        recentSurfaceHintAtByPkg[pkg] = now
    }

    private fun recentSurfaceHintMatches(pkg: String, key: String, now: Long): Boolean {
        val k = recentSurfaceHintKeyByPkg[pkg] ?: return false
        val at = recentSurfaceHintAtByPkg[pkg] ?: return false
        return k == key && (now - at) <= SURFACE_HINT_TTL_MS
    }

    private fun clearSurfaceHint(pkg: String) {
        recentSurfaceHintKeyByPkg.remove(pkg)
        recentSurfaceHintAtByPkg.remove(pkg)
    }

    private fun clearSurfaceEvidence(vararg keys: String) {
        for (k in keys) {
            surfaceEvidenceCount.remove(k)
            surfaceEvidenceAt.remove(k)
        }
    }

    private fun surfaceConfirmed(key: String, detected: Boolean, required: Int = 2): Boolean {
        val now = System.currentTimeMillis()
        if (!detected) {
            clearSurfaceEvidence(key)
            return false
        }
        val lastAt = surfaceEvidenceAt[key] ?: 0L
        val count = if (now - lastAt <= SURFACE_CONFIRM_MS) (surfaceEvidenceCount[key] ?: 0) + 1 else 1
        surfaceEvidenceAt[key] = now
        surfaceEvidenceCount[key] = count
        return count >= required.coerceAtLeast(1)
    }

    private fun timedBlockMsg(
        c: android.content.Context,
        toggle: Boolean,
        label: String,
    ): Pair<String, String>? {
        if (!toggle) return null
        return c.getString(R.string.in_app_content_blocked_title, label) to
            c.getString(R.string.in_app_content_blocked_message, label)
    }

    private fun safeAppLabel(svc: AccessibilityService, pkg: String): String {
        return try {
            val ai = svc.applicationContext.packageManager.getApplicationInfo(pkg, 0)
            svc.applicationContext.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Throwable) {
            pkg
        }
    }

    private fun softBlockSurface(
        service: AccessibilityService,
        pkg: String,
        appLabel: String,
        title: String,
        message: String,
        backCount: Int = 1,
        /**
         * When false, only the back key sequence runs.
         * No full-screen [BlockerActivity] (used for some non-YouTube cases).
         */
        showInAppBlocker: Boolean = true,
        /**
         * YouTube Shorts: jump to the YouTube home feed, then show the in-app overlay.
         * Dismiss is wired to return to YouTube home via [BlockerActivity.showInApp] extras.
         */
        openYouTubeHomeFirst: Boolean = false,
        dismissToYouTubeHome: Boolean = false,
        /**
         * Package whose launcher home should be opened when the user taps "Got it".
         * Defaults to the blocked app itself so the dismiss action *stays inside the app*
         * (e.g. Instagram Reels → Instagram home) rather than exiting to the device
         * launcher. Set to `null` to fall back to the device home screen.
         *
         * Skipped automatically when [dismissToYouTubeHome] is `true` (YouTube uses a
         * URL deeplink instead because `getLaunchIntentForPackage` can resume the
         * Shorts feed it was just on).
         */
        dismissToAppHomePkg: String? = pkg,
        /**
         * When true, back presses run only after the user dismisses the overlay
         * (Switchly-style deferred navigation).
         */
        deferNavigationUntilAcknowledge: Boolean = false,
        /**
         * When true, dismiss only hides the overlay and reveals the app task underneath
         * instead of calling [BlockerActivity.openAppHome] (avoids resuming a blocked tab).
         */
        returnToPackageOnClose: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val sk = "$pkg|$title"
        if (now - (lastSurfaceBlockAt[sk] ?: 0L) < SURFACE_BLOCK_COOLDOWN_MS) return
        val blockKey = "$pkg|$title"
        if (BlockerActivity.isVisible && BlockerActivity.visibleBlockKey == blockKey) return
        if (now - (lastBlockShownAt[pkg] ?: 0L) < 800L || now - lastGlobalBlockTs < 250L) return

        lastSurfaceBlockAt[sk] = now
        lastBlockShownAt[pkg] = now
        lastGlobalBlockTs = now
        inAppGraceUntilByPkg[pkg] = now + INAPP_POST_BLOCK_GRACE_MS
        currentSurfaceKey = null
        currentSurfacePkg = null
        clearSurfaceHint(pkg)
        // Clear all surface evidence in one shot. Iterating .keys and calling
        // remove() on the same map throws ConcurrentModificationException on a
        // plain HashMap; even on ConcurrentHashMap this is clearer.
        surfaceEvidenceAt.clear()
        surfaceEvidenceCount.clear()

        val postAckBackCount = if (deferNavigationUntilAcknowledge) backCount else 0
        val effectiveAppHomePkg = when {
            dismissToYouTubeHome || returnToPackageOnClose -> null
            else -> dismissToAppHomePkg
        }

        val ctx = service.applicationContext
        if (openYouTubeHomeFirst && showInAppBlocker) {
            mainHandler.post {
                runCatching { BlockerActivity.openYouTubeHome(ctx) }
            }
            mainHandler.postDelayed(
                {
                    runCatching {
                        BlockerActivity.showInApp(
                            ctx, pkg, appLabel, title, message,
                            dismissToYouTubeHome = dismissToYouTubeHome,
                            dismissToAppHomePkg = effectiveAppHomePkg,
                        )
                    }
                },
                200L,
            )
            return
        }

        val showOverlay = Runnable {
            if (!showInAppBlocker) return@Runnable
            runCatching {
                BlockerActivity.showInApp(
                    ctx, pkg, appLabel, title, message,
                    dismissToYouTubeHome = dismissToYouTubeHome,
                    dismissToAppHomePkg = effectiveAppHomePkg,
                    postAcknowledgeBackCount = postAckBackCount,
                    returnToPackageOnClose = returnToPackageOnClose,
                )
            }
        }

        if (!deferNavigationUntilAcknowledge && backCount > 0) {
            var step = 0
            val runBack = object : Runnable {
                override fun run() {
                    if (step < backCount) {
                        runCatching { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                        step++
                        mainHandler.postDelayed(this, 120L)
                    } else {
                        mainHandler.postDelayed(showOverlay, 40L)
                    }
                }
            }
            mainHandler.post(runBack)
        } else {
            val showDelay = when {
                deferNavigationUntilAcknowledge -> 0L
                pkg == InAppToggleKeys.SNAPCHAT -> 320L
                else -> 30L
            }
            mainHandler.postDelayed(showOverlay, showDelay)
        }
    }

    private fun softBlockSnapchatSurface(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        pkg: String,
        appLabel: String,
        title: String,
        message: String,
    ) {
        val redirected = InAppSnapchatNavigation.tryNavigateToCamera(service, root)
        softBlockInAppSurfaceWithRedirect(
            service, pkg, appLabel, title, message, redirected,
        )
    }

    private fun softBlockInAppSurfaceWithRedirect(
        service: AccessibilityService,
        pkg: String,
        appLabel: String,
        title: String,
        message: String,
        redirected: Boolean,
    ) {
        softBlockSurface(
            service = service,
            pkg = pkg,
            appLabel = appLabel,
            title = title,
            message = message,
            backCount = if (redirected) 0 else 1,
            deferNavigationUntilAcknowledge = !redirected,
            returnToPackageOnClose = true,
            dismissToAppHomePkg = null,
        )
    }

    // --- YouTube ---------------------------------------------------------------------------

    /**
     * True only when the user **tapped** the bottom-nav Shorts tab — not on app launch,
     * focus traversal, or passive "Shorts" labels on the home feed.
     */
    private fun isYouTubeShortsNavClicked(event: AccessibilityEvent?): Boolean {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        if (InAppA11yNodes.eventSourceIdMatches(event, youtubeShortsNavViewIdTokens)) return true

        val cd = event.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault())
        if (cd == "shorts") return true

        for (cs in event.text.orEmpty()) {
            val t = cs?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: continue
            if (t == "shorts") return true
        }

        var node: AccessibilityNodeInfo? = runCatching { event.source }.getOrNull() ?: return false
        try {
            var depth = 0
            while (node != null && depth < 10) {
                val n = node
                val viewId = runCatching { n.viewIdResourceName?.toString()?.lowercase(Locale.getDefault()) }
                    .getOrNull()
                if (viewId != null && youtubeShortsNavViewIdTokens.any { viewId.contains(it) }) {
                    return true
                }
                val t = n.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val ncd = n.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                if ((t == "shorts" || ncd == "shorts") && (n.isClickable || depth == 0)) {
                    return true
                }
                val parent = n.parent
                n.recycle()
                node = parent
                depth++
            }
            return false
        } finally {
            node?.recycle()
        }
    }

    /**
     * Single walk of the event source chain (recycles nodes). Strong phrases match on click/type/focus
     * (focus only after [YT_SEARCH_POST_ENTER_GRACE_MS]). Weak "search" labels match on **click** only.
     */
    private fun youtubeEventSourceIndicatesSearch(event: AccessibilityEvent?, now: Long): Boolean {
        if (event == null) return false
        val et = event.eventType
        var node: AccessibilityNodeInfo? = event.source ?: return false
        val sinceEnter = now - (appEnteredAtByPkg[InAppToggleKeys.YOUTUBE] ?: 0L)
        try {
            var depth = 0
            while (node != null && depth < 18) {
                val n = node
                if (InAppA11yNodes.nodeSelfTextDescHintMatches(n, youtubeSearchPhrases) &&
                    (et != AccessibilityEvent.TYPE_VIEW_FOCUSED || sinceEnter >= YT_SEARCH_POST_ENTER_GRACE_MS)
                ) {
                    return true
                }
                val cls = n.className?.toString()?.lowercase(Locale.getDefault()).orEmpty()
                val edLike =
                    n.isEditable || cls.contains("edittext") || cls.contains("autocomplete") || cls.contains("edits")
                if (edLike &&
                    InAppA11yNodes.nodeSelfTextDescHintMatches(n, youtubeSearchFocusNeedles) &&
                    (et != AccessibilityEvent.TYPE_VIEW_FOCUSED || sinceEnter >= YT_SEARCH_POST_ENTER_GRACE_MS)
                ) {
                    return true
                }
                if (et == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                    (n.isClickable || n.isCheckable) &&
                    InAppA11yNodes.nodeSelfTextDescHintMatches(n, youtubeSearchWeakLabels)
                ) {
                    return true
                }
                val parent = n.parent
                n.recycle()
                node = parent
                depth++
            }
            return false
        } finally {
            node?.recycle()
        }
    }

    private fun recentYouTubeSearchTapVeryFresh(now: Long): Boolean {
        if (!recentSurfaceHintMatches(InAppToggleKeys.YOUTUBE, "yt:search", now)) return false
        val at = recentSurfaceHintAtByPkg[InAppToggleKeys.YOUTUBE] ?: return false
        return now - at <= 750L
    }

    /**
     * Detects YouTube **search** without treating the whole home feed as search: requires hints where
     * Compose exposes only [AccessibilityNodeInfo.getHintText], plus secondary UI or interactions.
     */
    private fun isYouTubeSearchScreen(root: AccessibilityNodeInfo, event: AccessibilityEvent?, now: Long): Boolean {
        if (recentYouTubeSearchTapVeryFresh(now)) return true

        val et = event?.eventType ?: 0
        if (event != null &&
            (et == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
                et == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                et == AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {
            if (youtubeEventSourceIndicatesSearch(event, now)) return true
        }

        if (InAppA11yNodes.hasFocusedYouTubeSearchField(root, youtubeSearchFocusNeedles, youtubeSearchPhrases)) {
            return true
        }

        if (et == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val sinceEnter = now - (appEnteredAtByPkg[InAppToggleKeys.YOUTUBE] ?: 0L)
            if (sinceEnter >= YT_SEARCH_POST_ENTER_GRACE_MS &&
                InAppA11yNodes.hasFocusedSearchLikeNode(root, youtubeSearchFocusNeedles)
            ) {
                return true
            }
        }

        val strongTree = InAppA11yNodes.nodeTextDescHintMatches(root, youtubeSearchPhrases)
        val passiveOk = strongTree && InAppA11yNodes.nodeTextMatches(root, youtubeSearchSecondarySignals)

        val explicitGesture =
            et == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
                et == AccessibilityEvent.TYPE_VIEW_FOCUSED

        if (passiveOk) return true

        if (strongTree && explicitGesture && et != AccessibilityEvent.TYPE_VIEW_FOCUSED) return true

        return InAppA11yNodes.eventTextMatches(event, youtubeSearchPhrases + youtubeSearchWeakLabels)
    }

    private fun recentYouTubeCommentComposerTapVeryFresh(now: Long): Boolean {
        if (!recentSurfaceHintMatches(InAppToggleKeys.YOUTUBE, "yt:comment_composer", now)) return false
        val at = recentSurfaceHintAtByPkg[InAppToggleKeys.YOUTUBE] ?: return false
        return now - at <= 2_000L
    }

    /** Comments sheet open with an active text target (not the passive watch-page header). */
    private fun isYouTubeCommentSectionEngaged(root: AccessibilityNodeInfo): Boolean {
        if (!InAppA11yNodes.hasFocusedEditableNode(root) &&
            !InAppA11yNodes.hasFocusedYouTubeCommentField(
                root,
                youtubeCommentComposerPhrases,
                youtubeCommentComposerViewIdTokens,
                youtubeCommentComposerWeakPhrases,
            )
        ) {
            return false
        }
        return InAppA11yNodes.nodeTextDescHintMatches(root, youtubeCommentEngagedPhrases)
    }

    private fun youtubeNodeHasCommentComposerViewId(node: AccessibilityNodeInfo): Boolean {
        val viewId = runCatching { node.viewIdResourceName?.toString()?.lowercase(Locale.getDefault()) }
            .getOrNull() ?: return false
        val idTokens = youtubeCommentComposerViewIdTokens.map { it.lowercase(Locale.getDefault()) }
        return idTokens.any { viewId.contains(it) }
    }

    private fun youtubeNodeLooksLikeSearchField(node: AccessibilityNodeInfo): Boolean {
        return InAppA11yNodes.nodeSelfTextDescHintMatches(
            node,
            youtubeSearchPhrases + youtubeSearchWeakLabels,
        )
    }

    /**
     * True when the event targets the comment **input** (tap or typing), not the watch-page
     * "Comments" section title.
     */
    private fun youtubeEventSourceIndicatesCommentComposer(event: AccessibilityEvent?): Boolean {
        if (event == null) return false
        val et = event.eventType
        if (et != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            et != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            et != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED &&
            et != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            return false
        }
        if (et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val typed = event.text.orEmpty().any { !it.isNullOrBlank() }
            if (typed && !InAppA11yNodes.eventTextMatches(event, youtubeSearchPhrases + youtubeSearchWeakLabels)) {
                var probe: AccessibilityNodeInfo? = event.source
                try {
                    var depth = 0
                    while (probe != null && depth < 18) {
                        val n = probe
                        if (InAppA11yNodes.nodeOrAncestorViewIdMatches(n, youtubeCommentComposerViewIdTokens)) {
                            return true
                        }
                        if (InAppA11yNodes.isEditableLike(n) && !youtubeNodeLooksLikeSearchField(n)) {
                            return true
                        }
                        val parent = n.parent
                        n.recycle()
                        probe = parent
                        depth++
                    }
                } finally {
                    probe?.recycle()
                }
            }
        }
        var node: AccessibilityNodeInfo? = event.source ?: return false
        try {
            var depth = 0
            while (node != null && depth < 18) {
                val n = node
                if (youtubeNodeHasCommentComposerViewId(n) ||
                    InAppA11yNodes.nodeOrAncestorViewIdMatches(n, youtubeCommentComposerViewIdTokens)
                ) {
                    return true
                }
                val editableLike = InAppA11yNodes.isEditableLike(n)
                if (editableLike && youtubeNodeLooksLikeSearchField(n)) return false
                if (editableLike &&
                    InAppA11yNodes.nodeSelfTextDescHintMatches(n, youtubeCommentComposerPhrases)
                ) {
                    return true
                }
                if (InAppA11yNodes.nodeSelfTextDescHintMatches(n, youtubeCommentComposerPhrases) &&
                    (n.isClickable || editableLike)
                ) {
                    return true
                }
                if ((et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                        et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) &&
                    editableLike
                ) {
                    return true
                }
                val parent = n.parent
                n.recycle()
                node = parent
                depth++
            }
            return false
        } finally {
            node?.recycle()
        }
    }

    /**
     * Block only when the user tries to **write** a comment (composer focused, tapped, or typing),
     * not when a video plays and the collapsed "Comments" label is on screen.
     */
    private fun isYouTubeCommentComposerActive(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): Boolean {
        if (recentYouTubeCommentComposerTapVeryFresh(now)) return true

        if (InAppA11yNodes.hasFocusedYouTubeCommentField(
                root,
                youtubeCommentComposerPhrases,
                youtubeCommentComposerViewIdTokens,
                youtubeCommentComposerWeakPhrases,
            )
        ) {
            return true
        }

        if (isYouTubeCommentSectionEngaged(root)) return true

        if (InAppA11yNodes.nodeViewIdMatches(root, youtubeCommentComposerViewIdTokens) &&
            InAppA11yNodes.hasFocusedEditableNode(root)
        ) {
            return true
        }

        val et = event?.eventType ?: 0
        if (event == null) return false

        return when (et) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            -> youtubeEventSourceIndicatesCommentComposer(event) ||
                InAppA11yNodes.eventTextMatches(event, youtubeCommentComposerPhrases) ||
                (et != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
                    InAppA11yNodes.eventTextMatches(event, youtubeCommentComposerWeakPhrases))
            else -> false
        }
    }

    private fun handleYouTube(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): Boolean {
        val c = service.applicationContext
        // PiP first: entering PiP must not be missed while other surfaces are evaluated.
        if (blockYouTubePipIfNeeded(service, event)) return true

        val blockShorts = prefs(c, InAppToggleKeys.KEY_BLOCK_YT_SHORTS)
        if (blockShorts) {
            // Shorts: block on an explicit bottom-nav tap (never on app launch / focus).
            if (isYouTubeShortsNavClicked(event)) {
                clearSurfaceEvidence("yt:shorts")
                currentSurfaceKey = "yt:shorts"
                currentSurfacePkg = InAppToggleKeys.YOUTUBE
                val m = timedBlockMsg(
                    c,
                    true,
                    c.getString(R.string.in_app_label_shorts),
                )
                if (m != null) {
                    softBlockSurface(
                        service,
                        InAppToggleKeys.YOUTUBE,
                        safeAppLabel(service, InAppToggleKeys.YOUTUBE),
                        m.first,
                        m.second,
                        backCount = 0,
                        showInAppBlocker = true,
                        openYouTubeHomeFirst = true,
                        dismissToYouTubeHome = true,
                    )
                    return true
                }
            } else {
                clearSurfaceEvidence("yt:shorts")
                if (currentSurfacePkg == InAppToggleKeys.YOUTUBE && currentSurfaceKey == "yt:shorts") {
                    currentSurfaceKey = null
                    currentSurfacePkg = null
                }
            }
        } else {
            clearSurfaceEvidence("yt:shorts")
            if (currentSurfacePkg == InAppToggleKeys.YOUTUBE && currentSurfaceKey == "yt:shorts") {
                currentSurfaceKey = null
                currentSurfacePkg = null
            }
        }

        val blockYtSearch = prefs(c, InAppToggleKeys.KEY_BLOCK_YT_SEARCH)
        if (blockYtSearch) {
            val searchHit = surfaceConfirmed(
                "yt:search",
                isYouTubeSearchScreen(root, event, now),
                required = 1,
            )
            if (searchHit) {
                val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_search)) ?: return false
                softBlockSurface(
                    service,
                    InAppToggleKeys.YOUTUBE,
                    safeAppLabel(service, InAppToggleKeys.YOUTUBE),
                    m.first,
                    m.second,
                    backCount = 0,
                    showInAppBlocker = true,
                    openYouTubeHomeFirst = true,
                    dismissToYouTubeHome = true,
                )
                return true
            }
        } else {
            clearSurfaceEvidence("yt:search")
        }

        val blockYtComments = prefs(c, InAppToggleKeys.KEY_BLOCK_YT_COMMENTS)
        if (blockYtComments) {
            val commentsHit = surfaceConfirmed(
                "yt:comment_composer",
                isYouTubeCommentComposerActive(root, event, now),
                required = 1,
            )
            if (commentsHit) {
                currentSurfaceKey = "yt:comment_composer"
                currentSurfacePkg = InAppToggleKeys.YOUTUBE
                val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_comments)) ?: return false
                softBlockSurface(
                    service,
                    InAppToggleKeys.YOUTUBE,
                    safeAppLabel(service, InAppToggleKeys.YOUTUBE),
                    m.first,
                    m.second,
                    backCount = 1,
                )
                return true
            }
        } else {
            clearSurfaceEvidence("yt:comment_composer")
            if (currentSurfacePkg == InAppToggleKeys.YOUTUBE && currentSurfaceKey == "yt:comment_composer") {
                currentSurfaceKey = null
                currentSurfacePkg = null
            }
        }
        return false
    }

    private fun isInstagramSearchInteractiveEvent(event: AccessibilityEvent?): Boolean {
        val et = event?.eventType ?: 0
        return et == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            et == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
    }

    private fun isInstagramSearchBarClicked(event: AccessibilityEvent?): Boolean {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        if (InAppA11yNodes.eventSourceIdMatches(event, instagramSearchViewIdTokens)) return true
        val cd = event.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault())
        if (cd == "search" || cd == "suche") return true
        for (cs in event.text.orEmpty()) {
            val t = cs?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: continue
            if (t == "search" || t == "suche") return true
        }
        var node: AccessibilityNodeInfo? = runCatching { event.source }.getOrNull() ?: return false
        try {
            var depth = 0
            while (node != null && depth < 10) {
                val n = node
                if (InAppA11yNodes.nodeOrAncestorViewIdMatches(n, instagramSearchViewIdTokens)) return true
                val t = n.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val ncd = n.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                if ((t == "search" || t == "suche" || ncd == "search" || ncd == "suche") &&
                    (n.isClickable || InAppA11yNodes.isEditableLike(n))
                ) {
                    return true
                }
                val parent = n.parent
                n.recycle()
                node = parent
                depth++
            }
            return false
        } finally {
            node?.recycle()
        }
    }

    /**
     * User is actively searching (typing, results sheet, or focused search field) — not the passive
     * "Search" label on the home toolbar. Explore tab uses [exploreTab] in [handleInstagram].
     */
    private fun isInstagramSearchActive(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        if (InAppA11yNodes.hasFocusedSearchLikeNode(root, instagramSearchFieldNeedles)) return true

        if (isInstagramSearchBarClicked(event)) return true

        if (InAppA11yNodes.eventSourceIdMatches(event, instagramSearchViewIdTokens)) return true

        val et = event?.eventType ?: 0
        if (InAppA11yNodes.hasFocusedEditableNode(root)) {
            if (et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                et == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
            ) {
                val typed = event?.text.orEmpty().any { !it.isNullOrBlank() }
                if (typed) return true
            }
        }

        if (InAppA11yNodes.nodeTextDescHintMatches(root, instagramSearchActiveSignals)) return true

        if (isInstagramSearchInteractiveEvent(event) &&
            InAppA11yNodes.eventTextMatches(event, instagramSearchFieldNeedles)
        ) {
            return true
        }

        return false
    }

    /** Suppress blocking the home feed's always-visible search hint before the user interacts. */
    private fun isPassiveInstagramHomeSearchHeader(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        homeSelectedNow: Boolean,
    ): Boolean {
        if (!homeSelectedNow) return false
        if (isInstagramSearchBarClicked(event)) return false
        if (InAppA11yNodes.nodeTextDescHintMatches(root, instagramSearchActiveSignals)) return false
        if (InAppA11yNodes.hasFocusedEditableNode(root)) return false
        if (isInstagramSearchInteractiveEvent(event) &&
            event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            return false
        }
        return InAppA11yNodes.hasFocusedSearchLikeNode(root, instagramSearchFieldNeedles) &&
            !isInstagramSearchInteractiveEvent(event)
    }

    private fun allowInstagramSearchBlock(
        searchActive: Boolean,
        event: AccessibilityEvent?,
        homeSelectedNow: Boolean,
        reelsTabSelectedNow: Boolean,
        now: Long,
    ): Boolean {
        if (!searchActive || reelsTabSelectedNow) return false
        if (recentSurfaceHintMatches(InAppToggleKeys.INSTAGRAM, "ig:search", now)) return true
        if (!homeSelectedNow) return true
        return isInstagramSearchInteractiveEvent(event)
    }

    private fun isInstagramStoriesInteractiveEvent(event: AccessibilityEvent?): Boolean {
        val et = event?.eventType ?: 0
        return et == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            et == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            et == AccessibilityEvent.TYPE_VIEW_SELECTED
    }

    private fun isInstagramStoryRingClicked(event: AccessibilityEvent?): Boolean {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        if (InAppA11yNodes.eventSourceIdMatches(event, instagramStoriesViewIdTokens)) return true

        val cd = event.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        if (instagramStoriesTrayLabels.any { cd == it || cd.startsWith("$it,") }) return true

        for (cs in event.text.orEmpty()) {
            val t = cs?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: continue
            if (instagramStoriesTrayLabels.any { t == it }) return true
        }

        var node: AccessibilityNodeInfo? = runCatching { event.source }.getOrNull() ?: return false
        try {
            var depth = 0
            while (node != null && depth < 12) {
                val n = node
                if (InAppA11yNodes.nodeOrAncestorViewIdMatches(n, instagramStoriesViewIdTokens)) return true
                val t = n.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val ncd = n.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val label = listOf(t, ncd).filter { it.isNotBlank() }.joinToString(" ")
                if (label.isNotBlank() &&
                    instagramStoriesTrayLabels.any { label == it || label.startsWith("$it ") } &&
                    (n.isClickable || depth == 0)
                ) {
                    return true
                }
                val parent = n.parent
                n.recycle()
                node = parent
                depth++
            }
            return false
        } finally {
            node?.recycle()
        }
    }

    /**
     * User opened a story (fullscreen viewer or tapped a story ring) — not passive rings on home.
     */
    private fun isInstagramStoriesActive(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): Boolean {
        if (InAppA11yNodes.nodeTextDescHintMatches(root, instagramStoriesViewerSignals)) return true
        if (isInstagramStoryRingClicked(event)) return true
        if (InAppA11yNodes.eventSourceIdMatches(event, instagramStoriesViewIdTokens)) return true
        if (recentSurfaceHintMatches(InAppToggleKeys.INSTAGRAM, "ig:stories", now)) return true
        if (isInstagramStoriesInteractiveEvent(event) &&
            InAppA11yNodes.eventTextMatches(event, instagramStoriesTrayLabels)
        ) {
            return true
        }
        return false
    }

    /** Story rings visible on home before the user taps one — do not block yet. */
    private fun isPassiveInstagramHomeStoriesTray(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        homeSelectedNow: Boolean,
        now: Long,
    ): Boolean {
        if (!homeSelectedNow) return false
        if (isInstagramStoryRingClicked(event)) return false
        if (InAppA11yNodes.nodeTextDescHintMatches(root, instagramStoriesViewerSignals)) return false
        if (recentSurfaceHintMatches(InAppToggleKeys.INSTAGRAM, "ig:stories", now)) return false
        return InAppA11yNodes.nodeTextDescHintMatches(root, instagramStoriesTrayLabels) &&
            !isInstagramStoriesInteractiveEvent(event)
    }

    private fun allowInstagramStoriesBlock(
        storiesActive: Boolean,
        event: AccessibilityEvent?,
        homeSelectedNow: Boolean,
        now: Long,
    ): Boolean {
        if (!storiesActive) return false
        if (recentSurfaceHintMatches(InAppToggleKeys.INSTAGRAM, "ig:stories", now)) return true
        if (!homeSelectedNow) return true
        return isInstagramStoriesInteractiveEvent(event) || isInstagramStoryRingClicked(event)
    }

    private fun isInstagramCommentsVisible(root: AccessibilityNodeInfo): Boolean {
        return InAppA11yNodes.nodeTextMatches(
            root,
            listOf("comments", "kommentare", "add a comment", "view all comments"),
        )
    }

    /**
     * True only when the user **tapped** the bottom-nav Reels/Clips tab — not on launch focus,
     * not on passive "reels" text in the home feed, and not on [AccessibilityEvent.TYPE_VIEW_FOCUSED].
     */
    private fun isInstagramReelsNavClicked(event: AccessibilityEvent?): Boolean {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        if (InAppA11yNodes.eventSourceIdMatches(event, instagramReelsNavViewIdTokens)) return true

        val cd = event.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault())
        if (cd == "reels" || cd == "clips") return true

        for (cs in event.text.orEmpty()) {
            val t = cs?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: continue
            if (t == "reels" || t == "clips") return true
        }

        var node: AccessibilityNodeInfo? = runCatching { event.source }.getOrNull() ?: return false
        try {
            var depth = 0
            while (node != null && depth < 10) {
                val n = node
                val viewId = runCatching { n.viewIdResourceName?.toString()?.lowercase(Locale.getDefault()) }
                    .getOrNull()
                if (viewId != null && instagramReelsNavViewIdTokens.any { viewId.contains(it) }) {
                    return true
                }
                val t = n.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val ncd = n.contentDescription?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                if ((t == "reels" || t == "clips" || ncd == "reels" || ncd == "clips") && (n.isClickable || depth == 0)) {
                    return true
                }
                val parent = n.parent
                n.recycle()
                node = parent
                depth++
            }
            return false
        } finally {
            node?.recycle()
        }
    }

    /** Fullscreen Reels player chrome (often opened from Home while the Home tab stays selected). */
    private fun isInstagramFullscreenReelsChrome(root: AccessibilityNodeInfo): Boolean {
        return InAppA11yNodes.nodeTextMatches(
            root,
            listOf(
                "original audio",
                "originalton",
                "use this audio",
                "use audio",
                "remix this reel",
            ),
        )
    }

    private fun instagramState(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): String? {
        return when {
            isInstagramStoriesActive(root, event, now) -> "stories"
            isInstagramFullscreenReelsChrome(root) -> "reels"
            InAppA11yNodes.hasSelectedOrCheckedLabelInPackage(
                root,
                listOf("reels"),
                InAppToggleKeys.INSTAGRAM,
            ) -> "reels"
            isInstagramSearchActive(root, event) -> "explore"
            InAppA11yNodes.hasSelectedOrCheckedLabel(
                root,
                listOf("home", "startseite"),
            ) -> "home"
            else -> null
        }
    }

    private fun handleInstagram(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): Boolean {
        val c = service.applicationContext
        val eventType = event?.eventType ?: 0
        val blockReels = prefs(c, InAppToggleKeys.KEY_BLOCK_IG_REELS)
        val blockExplore = prefs(c, InAppToggleKeys.KEY_BLOCK_IG_EXPLORE)
        val blockSearch = prefs(c, InAppToggleKeys.KEY_BLOCK_IG_SEARCH)
        val blockStories = prefs(c, InAppToggleKeys.KEY_BLOCK_IG_STORIES)
        val blockComments = prefs(c, InAppToggleKeys.KEY_BLOCK_IG_COMMENTS)

        val homeSelectedNow = InAppA11yNodes.hasSelectedOrCheckedLabelInPackage(
            root,
            listOf("home", "startseite"),
            InAppToggleKeys.INSTAGRAM,
        )
        val reelsTabSelectedNow = InAppA11yNodes.hasSelectedOrCheckedLabelInPackage(
            root,
            listOf("reels"),
            InAppToggleKeys.INSTAGRAM,
        )
        val exploreTab = InAppA11yNodes.hasSelectedOrCheckedLabelInPackage(
            root,
            listOf("search", "suche", "discover", "entdecken", "explore"),
            InAppToggleKeys.INSTAGRAM,
        )
        val searchNow = isInstagramSearchActive(root, event)
        val passiveHomeSearch = isPassiveInstagramHomeSearchHeader(root, event, homeSelectedNow)
        val storiesActive = isInstagramStoriesActive(root, event, now)
        val passiveHomeStoriesTray = isPassiveInstagramHomeStoriesTray(root, event, homeSelectedNow, now)
        val stateById = instagramState(root, event, now)

        val storiesDetectAllowed = allowInstagramStoriesBlock(
            storiesActive = storiesActive && !passiveHomeStoriesTray,
            event = event,
            homeSelectedNow = homeSelectedNow,
            now = now,
        )
        val storiesRequired = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            -> 1
            else -> 1
        }
        val storiesHit = surfaceConfirmed(
            "ig:stories",
            blockStories && storiesDetectAllowed,
            required = storiesRequired,
        )
        if (storiesHit) {
            clearSurfaceEvidence("ig:reels", "ig:explore", "ig:search")
            currentSurfaceKey = "ig:stories"
            currentSurfacePkg = InAppToggleKeys.INSTAGRAM
            val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_stories)) ?: return false
            softBlockSurface(
                service,
                InAppToggleKeys.INSTAGRAM,
                safeAppLabel(service, InAppToggleKeys.INSTAGRAM),
                m.first,
                m.second,
                backCount = 1,
            )
            return true
        }

        // Reels: block only on an explicit bottom-nav Reels/Clips tap — never on app launch,
        // focus traversal, or passive "reels" text in the home feed.
        if (blockReels && isInstagramReelsNavClicked(event)) {
            clearSurfaceEvidence("ig:reels")
            currentSurfaceKey = "ig:reels"
            currentSurfacePkg = InAppToggleKeys.INSTAGRAM
            val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_reels)) ?: return false
            softBlockSurface(
                service,
                InAppToggleKeys.INSTAGRAM,
                safeAppLabel(service, InAppToggleKeys.INSTAGRAM),
                m.first,
                m.second,
                backCount = 2,
            )
            return true
        }

        if (stateById == "home" ||
            (stateById == null && homeSelectedNow && !reelsTabSelectedNow && !exploreTab)
        ) {
            clearSurfaceEvidence("ig:reels")
        }

        val exploreHit = surfaceConfirmed(
            "ig:explore",
            blockExplore && exploreTab && !searchNow && (stateById == "explore" || exploreTab),
            required = 1,
        )
        if (exploreHit) {
            currentSurfaceKey = "ig:explore"
            currentSurfacePkg = InAppToggleKeys.INSTAGRAM
            val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_explore_tab)) ?: return false
            softBlockSurface(service, InAppToggleKeys.INSTAGRAM, safeAppLabel(service, InAppToggleKeys.INSTAGRAM), m.first, m.second, backCount = 2)
            return true
        }

        val searchDetectAllowed = allowInstagramSearchBlock(
            searchActive = searchNow && !passiveHomeSearch,
            event = event,
            homeSelectedNow = homeSelectedNow,
            reelsTabSelectedNow = reelsTabSelectedNow,
            now = now,
        )
        val searchRequired = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> 1
            else -> 1
        }
        val searchHit = surfaceConfirmed(
            "ig:search",
            blockSearch && searchDetectAllowed,
            required = searchRequired,
        )
        if (searchHit) {
            currentSurfaceKey = "ig:search"
            currentSurfacePkg = InAppToggleKeys.INSTAGRAM
            val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_search)) ?: return false
            softBlockSurface(service, InAppToggleKeys.INSTAGRAM, safeAppLabel(service, InAppToggleKeys.INSTAGRAM), m.first, m.second, backCount = 2)
            return true
        }

        if (blockComments && isInstagramCommentsVisible(root)) {
            val m = timedBlockMsg(c, true, c.getString(R.string.in_app_label_comments)) ?: return false
            softBlockSurface(service, InAppToggleKeys.INSTAGRAM, safeAppLabel(service, InAppToggleKeys.INSTAGRAM), m.first, m.second, backCount = 1)
            return true
        }
        if (!exploreHit && !searchHit && !storiesHit) {
            if (currentSurfacePkg == InAppToggleKeys.INSTAGRAM) {
                currentSurfaceKey = null
                currentSurfacePkg = null
            }
        }
        return false
    }

    // --- X / Twitter ----------------------------------------------------------------------

    private fun handleX(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): Boolean {
        val c = service.applicationContext
        val p = InAppToggleKeys.X_TWITTER
        val blockNotifications = prefs(c, InAppToggleKeys.KEY_BLOCK_X_NOTIFICATIONS)

        val homeN = listOf("home", "home timeline", "startseite", "for you", "following")
        if (handleXOne(
                service, root, event, p, "x:foryou",
                prefs(c, InAppToggleKeys.KEY_BLOCK_X_HOME), homeN, c.getString(R.string.in_app_label_home), now,
            ) { r ->
                if (!blockNotifications) InAppTwitterNavigation.tryNavigateToNotifications(r) else false
            }
        ) return true
        if (handleXOne(
                service, root, event, p, "x:search",
                prefs(c, InAppToggleKeys.KEY_BLOCK_X_SEARCH), listOf("search", "explore"),
                c.getString(R.string.in_app_label_search), now,
            ) { InAppTwitterNavigation.tryNavigateToHome(it) }
        ) return true
        if (handleXOne(
                service, root, event, p, "x:grok",
                prefs(c, InAppToggleKeys.KEY_BLOCK_X_GROK), listOf("grok"),
                c.getString(R.string.in_app_label_grok), now,
            ) { InAppTwitterNavigation.tryNavigateToHome(it) }
        ) return true
        if (handleXOne(
                service, root, event, p, "x:notifications",
                prefs(c, InAppToggleKeys.KEY_BLOCK_X_NOTIFICATIONS),
                listOf("notifications", "notification"), c.getString(R.string.in_app_label_notifications), now,
            ) { InAppTwitterNavigation.tryNavigateToHome(it) }
        ) return true
        if (currentSurfacePkg == p) {
            currentSurfaceKey = null
            currentSurfacePkg = null
        }
        return false
    }

    private fun handleXOne(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        pkg: String,
        surfaceKey: String,
        block: Boolean,
        needles: List<String>,
        label: String,
        now: Long,
        redirect: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        if (!block) {
            clearSurfaceEvidence(surfaceKey)
            return false
        }
        val detected =
            recentSurfaceHintMatches(pkg, surfaceKey, now) ||
                InAppA11yNodes.hasSelectedLabelInPackage(root, needles, pkg) ||
                InAppA11yNodes.eventTextMatches(event, needles)
        val need = if (recentSurfaceHintMatches(pkg, surfaceKey, now) || InAppA11yNodes.eventTextMatches(event, needles)) 1 else 2
        val hit = surfaceConfirmed(surfaceKey, detected, required = need)
        if (!hit) return false
        currentSurfaceKey = surfaceKey
        currentSurfacePkg = pkg
        val m = timedBlockMsg(service.applicationContext, true, label) ?: return false
        val redirected = redirect(root)
        softBlockInAppSurfaceWithRedirect(
            service, pkg, safeAppLabel(service, pkg), m.first, m.second, redirected,
        )
        return true
    }

    // --- Snapchat -------------------------------------------------------------------------

    private fun handleSnapchat(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        now: Long,
    ): Boolean {
        val c = service.applicationContext
        val p = InAppToggleKeys.SNAPCHAT
        // Snapchat's primary nav strip (Map/Chat/Camera/Stories/Spotlight buttons) is
        // visible on multiple screens, so a tree-wide text scan for "map"/"story"
        // would match on every screen and block the whole app. Detection layers
        // narrow signals to identify the surface the user is *actually* on:
        //
        //   needles                  → tap intent / selected nav tab / event text
        //   classNameTokens          → the foreground activity/fragment class itself
        //                              (only the real screen window has this name —
        //                              the nav button does not change the window class)
        //   surfaceUniqueOnScreen    → on-screen labels that only appear once the
        //                              surface is actually rendered (e.g. "Search a
        //                              place" only exists on the open Map screen,
        //                              not on the Map nav icon).
        //
        // We strictly avoid bare "map" everywhere because of "Bitmap"/"Map shortcut".
        if (doSnap(
                service, root, event, "snap:map", prefs(c, InAppToggleKeys.KEY_BLOCK_SNAP_MAP),
                // "map" (bare) is intentional — Snapchat's bottom-nav icon is icon-only
                // with contentDescription "Map" on many builds. Safe because [doSnap]
                // never tree-scans these needles; they only feed event-level checks.
                needles = listOf("map", "snap map", "map view", "live map"),
                classNameTokens = listOf(
                    // Activity / fragment patterns (works on multi-activity builds).
                    ".map.", "mapactivity", "mapfragment", "mappage", "mapscreen",
                    "snapmap", "snapchatmap",
                ),
                // Tree-wide widget classes that *only* exist when an actual map is
                // rendered. Snapchat ships their Map on top of Mapbox; we also keep
                // Google Maps SDK names for older builds and snap-prefixed wrappers.
                viewClassTokens = listOf(
                    "mapview", "googlemap", "supportmapfragment",
                    "mapbox", "mapboxmap", "maplibre",
                    "snapmapview", "snapmap",
                ),
                // View-ID resource names — independent of contentDescription/locale.
                // Filtered to exclude "bitmap" / "sitemap" via the helper logic.
                viewIdTokens = listOf("map_tab", "map_button", "nav_map", "snap_map", "_map_"),
                surfaceUniqueOnScreen = listOf(
                    "search a place",
                    "ghost mode",
                    "place pin",
                    "find friends on snap map",
                    "add friends to snap map",
                    "snap map heatmap",
                    "share my live location",
                    "see when friends",
                    "my bitmoji on the map",
                ),
                label = c.getString(R.string.in_app_label_map), pkg = p, now = now,
            )
        ) return true
        if (doSnap(
                service, root, event, "snap:stories", prefs(c, InAppToggleKeys.KEY_BLOCK_SNAP_STORIES),
                needles = listOf("stories", "story"),
                classNameTokens = listOf(
                    ".stories.", ".story.", "storiesactivity", "storyactivity",
                    "discoverfeed", "subscriptionfeed",
                ),
                viewClassTokens = listOf("discoverview", "storiesfeed", "storyfeedview"),
                viewIdTokens = listOf("stories_tab", "story_tab", "discover_tab", "nav_stories"),
                surfaceUniqueOnScreen = listOf(
                    "subscribed",
                    "discover stories",
                    "for you stories",
                    "watch story",
                    "tap to play",
                ),
                label = c.getString(R.string.in_app_label_stories), pkg = p, now = now,
            )
        ) return true
        if (doSnap(
                service, root, event, "snap:spotlight", prefs(c, InAppToggleKeys.KEY_BLOCK_SNAP_SPOTLIGHT),
                needles = listOf("spotlight"),
                classNameTokens = listOf(
                    ".spotlight.", "spotlightactivity", "spotlightfragment",
                    "spotlightfeed", "spotlightpage",
                ),
                viewClassTokens = listOf("spotlightview", "spotlightplayer"),
                viewIdTokens = listOf("spotlight_tab", "nav_spotlight"),
                surfaceUniqueOnScreen = listOf(
                    "spotlight feed",
                    "view spotlight",
                    "trending spotlight",
                    "send spotlight",
                ),
                label = c.getString(R.string.in_app_label_spotlight), pkg = p, now = now,
            )
        ) return true
        if (doSnap(
                service, root, event, "snap:following", prefs(c, InAppToggleKeys.KEY_BLOCK_SNAP_FOLLOWING),
                needles = listOf("following"),
                classNameTokens = listOf(".following.", "followingactivity", "followingfeed"),
                viewClassTokens = emptyList(),
                viewIdTokens = listOf("following_tab", "nav_following"),
                surfaceUniqueOnScreen = emptyList(),
                label = c.getString(R.string.in_app_label_following), pkg = p, now = now,
            )
        ) return true
        if (currentSurfacePkg == p) {
            currentSurfaceKey = null
            currentSurfacePkg = null
        }
        return false
    }

    /**
     * Confirm a Snapchat surface using a layered, narrow-signal strategy.
     *
     * Strong signals (`required = 1` — block immediately):
     *  - a fresh tap-hint remembered in [captureSurfaceHintFromEvent], OR
     *  - the current event's text/contentDescription matches [needles], OR
     *  - the active window's `className` matches [classNameTokens] (handles
     *    multi-activity builds where each tab is its own Activity), OR
     *  - **a widget in the tree has `className` matching [viewClassTokens]** —
     *    e.g. a `MapView`/`GoogleMap`/`Mapbox` node exists only when an actual
     *    map is rendered. This is the key signal that survives Snapchat's
     *    single-host-activity, fragment-swap architecture (the host Activity's
     *    class name never changes when the user navigates between tabs).
     *
     * Medium signals (`required = 2` within [SURFACE_CONFIRM_MS]):
     *  - the package-scoped selected/checked nav tab, OR
     *  - tree-wide text/desc/hint match against [surfaceUniqueOnScreen] — phrases
     *    that exist on the rendered surface but not on the nav-button tooltip
     *    (e.g. "Search a place" exists only on the open Map, never on the Map
     *    icon in the Camera nav strip). Uses [InAppA11yNodes.nodeTextDescHintMatches]
     *    so Compose placeholders / `hintText` are also picked up.
     *
     * This deliberately avoids a tree-wide scan of the short [needles] — that
     * was the bug that made every Snapchat screen look like Map.
     */
    private fun doSnap(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        surfaceKey: String,
        block: Boolean,
        needles: List<String>,
        classNameTokens: List<String>,
        viewClassTokens: List<String>,
        viewIdTokens: List<String>,
        surfaceUniqueOnScreen: List<String>,
        label: String,
        pkg: String,
        now: Long,
    ): Boolean {
        if (!block) {
            clearSurfaceEvidence(surfaceKey)
            return false
        }
        val freshHint = recentSurfaceHintMatches(pkg, surfaceKey, now)
        val eventHit = InAppA11yNodes.eventTextMatches(event, needles)
        val viewIdHit = InAppA11yNodes.eventSourceIdMatches(event, viewIdTokens)
        val classHit = classNameMatches(event?.className?.toString(), classNameTokens) ||
            classNameMatches(root.className?.toString(), classNameTokens)
        val viewClassHit = viewClassTokens.isNotEmpty() &&
            InAppA11yNodes.hasNodeWithClassNameContaining(root, viewClassTokens)
        val tabSelected = InAppA11yNodes.hasSelectedOrCheckedLabelInPackage(root, needles, pkg)
        val uniqueTextHit = surfaceUniqueOnScreen.isNotEmpty() &&
            InAppA11yNodes.nodeTextDescHintMatches(root, surfaceUniqueOnScreen)

        val strong = freshHint || eventHit || viewIdHit || classHit || viewClassHit
        val medium = tabSelected || uniqueTextHit
        val detected = strong || medium
        if (!detected) {
            // Throttled diagnostic so we can see *why* a block did not fire.
            // Only logs once per [SNAP_DIAG_THROTTLE_MS] per surfaceKey to avoid spam.
            val lastDiag = lastSnapDiagAtBySurface[surfaceKey] ?: 0L
            if (now - lastDiag >= SNAP_DIAG_THROTTLE_MS) {
                lastSnapDiagAtBySurface[surfaceKey] = now
                val evType = event?.eventType
                val evClass = event?.className
                val srcId = runCatching { event?.source?.viewIdResourceName }.getOrNull()
                Log.d(
                    TAG,
                    "Snap miss surface=$surfaceKey (block enabled) evType=$evType " +
                        "evClass=$evClass rootClass=${root.className} srcId=$srcId " +
                        "signals: hint=$freshHint event=$eventHit viewId=$viewIdHit " +
                        "class=$classHit viewClass=$viewClassHit tab=$tabSelected " +
                        "text=$uniqueTextHit",
                )
            }
            clearSurfaceEvidence(surfaceKey)
            return false
        }
        val need = if (strong) 1 else 2
        val hit = surfaceConfirmed(surfaceKey, true, required = need)
        if (!hit) return false
        currentSurfaceKey = surfaceKey
        currentSurfacePkg = pkg
        val m = timedBlockMsg(service.applicationContext, true, label) ?: return false
        Log.d(
            TAG,
            "Snap block surface=$surfaceKey strong=$strong (hint=$freshHint event=$eventHit " +
                "viewId=$viewIdHit class=$classHit viewClass=$viewClassHit) medium=$medium " +
                "(tab=$tabSelected text=$uniqueTextHit)",
        )
        softBlockSnapchatSurface(
            service,
            root,
            pkg,
            safeAppLabel(service, pkg),
            m.first,
            m.second,
        )
        return true
    }

    /**
     * Runs deferred navigation after the user dismisses an in-app surface block.
     * Prefer in-app tab taps over global BACK (Snap Map / X Search are often root tabs).
     */
    fun consumePendingBackNavigation(
        service: AccessibilityService,
        pkg: String,
        pendingBackCount: Int,
    ): Boolean {
        if (pendingBackCount <= 0) return false
        val now = System.currentTimeMillis()
        inAppGraceUntilByPkg[pkg] = now + INAPP_POST_BLOCK_GRACE_MS
        currentSurfaceKey = null
        currentSurfacePkg = null
        clearSurfaceHint(pkg)
        surfaceEvidenceAt.clear()
        surfaceEvidenceCount.clear()

        if (pkg == InAppToggleKeys.SNAPCHAT || pkg == InAppToggleKeys.X_TWITTER) {
            val root = runCatching { service.rootInActiveWindow }.getOrNull()
            if (root != null) {
                try {
                    val navigated = when (pkg) {
                        InAppToggleKeys.SNAPCHAT ->
                            InAppSnapchatNavigation.tryNavigateToCamera(service, root)
                        InAppToggleKeys.X_TWITTER ->
                            InAppTwitterNavigation.tryNavigateToHome(root)
                        else -> false
                    }
                    if (navigated) return true
                } finally {
                    runCatching { root.recycle() }
                }
            }
        }

        var step = 0
        val runBack = object : Runnable {
            override fun run() {
                if (step < pendingBackCount) {
                    runCatching { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                    step++
                    mainHandler.postDelayed(this, 130L)
                }
            }
        }
        mainHandler.post(runBack)
        return true
    }

    /**
     * Case-insensitive substring match used to detect a foreground Snapchat
     * surface by its activity/fragment class (see [doSnap]). Returns false on
     * blank inputs to keep the strong-signal disjunction safe.
     */
    private fun classNameMatches(name: String?, tokens: List<String>): Boolean {
        if (name.isNullOrBlank() || tokens.isEmpty()) return false
        val lc = name.lowercase(Locale.getDefault())
        return tokens.any { it.isNotBlank() && lc.contains(it.lowercase(Locale.getDefault())) }
    }

    private fun captureSurfaceHintFromEvent(pkg: String, event: AccessibilityEvent?, now: Long) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            type != AccessibilityEvent.TYPE_VIEW_SELECTED &&
            type != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            return
        }
        when (pkg) {
            InAppToggleKeys.YOUTUBE -> {
                when (type) {
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        if (isYouTubeShortsNavClicked(event)) {
                            rememberSurfaceHint(pkg, "yt:shorts", now)
                        }
                        if (InAppA11yNodes.eventTextMatches(event, youtubeSearchPhrases) ||
                            InAppA11yNodes.eventTextMatches(event, youtubeSearchWeakLabels)
                        ) {
                            rememberSurfaceHint(pkg, "yt:search", now)
                        }
                        if (youtubeEventSourceIndicatesCommentComposer(event) ||
                            InAppA11yNodes.eventTextMatches(event, youtubeCommentComposerPhrases) ||
                            InAppA11yNodes.eventSourceIdMatches(event, youtubeCommentComposerViewIdTokens)
                        ) {
                            rememberSurfaceHint(pkg, "yt:comment_composer", now)
                        }
                    }
                    AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                        if (youtubeEventSourceIndicatesCommentComposer(event) ||
                            InAppA11yNodes.eventTextMatches(event, youtubeCommentComposerPhrases) ||
                            InAppA11yNodes.eventSourceIdMatches(event, youtubeCommentComposerViewIdTokens)
                        ) {
                            rememberSurfaceHint(pkg, "yt:comment_composer", now)
                        }
                    }
                    AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                        if (InAppA11yNodes.eventTextMatches(event, youtubeSearchPhrases)) {
                            rememberSurfaceHint(pkg, "yt:search", now)
                        }
                    }
                    else -> Unit
                }
            }
            InAppToggleKeys.INSTAGRAM -> {
                when {
                    type == AccessibilityEvent.TYPE_VIEW_CLICKED && isInstagramReelsNavClicked(event) ->
                        rememberSurfaceHint(pkg, "ig:reels", now)
                    type == AccessibilityEvent.TYPE_VIEW_CLICKED && isInstagramSearchBarClicked(event) ->
                        rememberSurfaceHint(pkg, "ig:search", now)
                    type == AccessibilityEvent.TYPE_VIEW_FOCUSED &&
                        InAppA11yNodes.eventTextMatches(event, instagramSearchFieldNeedles) ->
                        rememberSurfaceHint(pkg, "ig:search", now)
                    type == AccessibilityEvent.TYPE_VIEW_CLICKED && isInstagramStoryRingClicked(event) ->
                        rememberSurfaceHint(pkg, "ig:stories", now)
                }
            }
            InAppToggleKeys.X_TWITTER -> {
                when {
                    InAppA11yNodes.eventTextMatches(event, listOf("home", "home timeline", "for you", "following", "startseite")) -> rememberSurfaceHint(
                        pkg, "x:foryou", now
                    )
                    InAppA11yNodes.eventTextMatches(event, listOf("search", "explore")) -> rememberSurfaceHint(
                        pkg, "x:search", now
                    )
                    InAppA11yNodes.eventTextMatches(event, listOf("grok")) -> rememberSurfaceHint(
                        pkg, "x:grok", now
                    )
                    InAppA11yNodes.eventTextMatches(event, listOf("notifications", "notification")) -> rememberSurfaceHint(
                        pkg, "x:notifications", now
                    )
                }
            }
            InAppToggleKeys.SNAPCHAT -> {
                // Snapchat's primary navigation is a row of icon-only buttons. On most
                // builds their contentDescription is "Map" / "Stories" / "Spotlight",
                // but some builds leave them unlabeled — so we also look at the source
                // view's resource ID (e.g. com.snapchat.android:id/map_tab). Both signal
                // sources only fire on the click event itself, so the hint cannot
                // accidentally pre-arm from passive focus or accessibility scans.
                if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    val ev = event
                    when {
                        InAppA11yNodes.eventTextMatches(ev, listOf("map", "snap map", "map view", "live map")) ||
                            InAppA11yNodes.eventSourceIdMatches(ev, listOf("map_tab", "map_button", "nav_map", "snap_map", "_map_")) ->
                            rememberSurfaceHint(pkg, "snap:map", now)
                        InAppA11yNodes.eventTextMatches(ev, listOf("stories", "story")) ||
                            InAppA11yNodes.eventSourceIdMatches(ev, listOf("stories_tab", "story_tab", "discover_tab", "nav_stories")) ->
                            rememberSurfaceHint(pkg, "snap:stories", now)
                        InAppA11yNodes.eventTextMatches(ev, listOf("spotlight")) ||
                            InAppA11yNodes.eventSourceIdMatches(ev, listOf("spotlight_tab", "nav_spotlight")) ->
                            rememberSurfaceHint(pkg, "snap:spotlight", now)
                        InAppA11yNodes.eventTextMatches(ev, listOf("following")) ||
                            InAppA11yNodes.eventSourceIdMatches(ev, listOf("following_tab", "nav_following")) ->
                            rememberSurfaceHint(pkg, "snap:following", now)
                    }
                }
            }
        }
    }
}
