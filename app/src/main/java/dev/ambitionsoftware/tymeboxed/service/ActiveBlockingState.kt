package dev.ambitionsoftware.tymeboxed.service

import android.os.SystemClock
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId

/**
 * In-memory singleton that holds the current blocking session's state.
 *
 * Written by [HomeViewModel] when a session starts/stops. Read by
 * [AppBlockerAccessibilityService] on every window-change event — must be
 * fast (no DB, no suspension). Thread-safe via a volatile reference to an
 * immutable [Snapshot]; all read-modify-write updates run inside [lock].
 */
object ActiveBlockingState {

    private val lock = Any()

    data class Snapshot(
        val isBlocking: Boolean = false,
        val profileId: String? = null,
        /** Display name for the foreground notification; survives service restarts with a null intent. */
        val profileName: String? = null,
        /** Epoch ms when the current focus session started; used for live duration UI. */
        val sessionStartTimeMs: Long = 0L,
        /** Active profile strategy; drives start/stop/NFC behavior (mirrors iOS StrategyManager). */
        val strategyId: String = BlockingStrategyId.NFC_UNLOCK,
        /**
         * When true, restrictions are lifted until [breakAutoResumeAtMs] (focus + break strategy)
         * or the user ends the session from the break NFC flow.
         */
        val isPauseActive: Boolean = false,
        /** When set and [isPauseActive], break auto-resumes focus blocking at this time. */
        val breakAutoResumeAtMs: Long? = null,
        /** For [BlockingStrategyId.FOCUS_TIMER] — auto end session at this wall time. */
        val focusTimerEndMs: Long? = null,
        /**
         * Profile [enableStrictMode]: block uninstall flows for Tyme Boxed while a session is active.
         */
        val strictModeEnabled: Boolean = false,
        val blockedPackages: Set<String> = emptySet(),
        val isAllowMode: Boolean = false,
        /** Normalized hostnames from the active profile (see [DomainBlocking.normalize]). */
        val domains: Set<String> = emptySet(),
        val isAllowModeDomains: Boolean = false,
        /**
         * When true, the foreground session notification uses Live Activity–style copy (iOS
         * `enableLiveActivity`): app title, profile name, random focus line, timer/status.
         */
        val enableLiveActivityNotification: Boolean = false,
        /** Random quote picked once when the session starts; mirrors iOS `FocusMessages`. */
        val focusQuoteMessage: String? = null,
    )

    @Volatile
    var current: Snapshot = Snapshot()
        private set

    /**
     * Heartbeat tracking for the accessibility service.
     * Updated every ~1 second by the service's tick loop.
     * The UI can check [isServiceAlive] to know if blocking is actually enforced.
     */
    @Volatile
    private var lastHeartbeatElapsed: Long = 0L

    /** How stale a heartbeat can be before we consider the service dead. */
    private const val HEARTBEAT_STALE_MS = 8_000L

    /**
     * Activate blocking for the given profile. Called when a session starts.
     */
    fun activate(
        profileId: String,
        profileName: String,
        blockedPackages: Set<String>,
        isAllowMode: Boolean,
        domains: List<String> = emptyList(),
        isAllowModeDomains: Boolean = false,
        sessionStartTimeMs: Long = 0L,
        strategyId: String = BlockingStrategyId.NFC_UNLOCK,
        isPauseActive: Boolean = false,
        breakAutoResumeAtMs: Long? = null,
        focusTimerEndMs: Long? = null,
        strictModeEnabled: Boolean = false,
        enableLiveActivityNotification: Boolean = false,
        focusQuoteMessage: String? = null,
    ) {
        val normalizedDomains = domains
            .map { it.trim() }
            .mapNotNull { DomainBlocking.normalize(it) }
            .toSet()
        synchronized(lock) {
            current = Snapshot(
                isBlocking = true,
                profileId = profileId,
                profileName = profileName,
                sessionStartTimeMs = sessionStartTimeMs,
                strategyId = strategyId,
                isPauseActive = isPauseActive,
                breakAutoResumeAtMs = breakAutoResumeAtMs,
                focusTimerEndMs = focusTimerEndMs,
                strictModeEnabled = strictModeEnabled,
                blockedPackages = blockedPackages,
                isAllowMode = isAllowMode,
                domains = normalizedDomains,
                isAllowModeDomains = isAllowModeDomains,
                enableLiveActivityNotification = enableLiveActivityNotification,
                focusQuoteMessage = focusQuoteMessage,
            )
        }
    }

    /**
     * Updates Live Activity / lock-screen widget visibility for the active session
     * (e.g. after the user toggles the setting on the profile edit screen).
     */
    fun setLiveActivityNotification(enabled: Boolean, focusQuoteMessage: String? = null) {
        synchronized(lock) {
            val snap = current
            if (!snap.isBlocking) return
            current = snap.copy(
                enableLiveActivityNotification = enabled,
                focusQuoteMessage = if (enabled) focusQuoteMessage else null,
            )
        }
    }

    /** Updates pause/break state without losing package/domain lists (iOS: break scan flow). */
    fun setPause(
        isPauseActive: Boolean,
        breakAutoResumeAtMs: Long? = null,
    ) {
        synchronized(lock) {
            val snap = current
            if (!snap.isBlocking) return
            current = snap.copy(
                isPauseActive = isPauseActive,
                breakAutoResumeAtMs = if (isPauseActive) breakAutoResumeAtMs else null,
            )
        }
    }

    /**
     * After a timed break, push out [focusTimerEndMs] by the break duration so the configured
     * focus length is not consumed while blocking is paused (matches expected “timed break” UX).
     */
    fun extendFocusTimerEnd(extraMs: Long) {
        synchronized(lock) {
            val snap = current
            if (!snap.isBlocking || extraMs <= 0L) return
            val end = snap.focusTimerEndMs ?: return
            current = snap.copy(focusTimerEndMs = end + extraMs)
        }
    }

    /** Clear blocking state. Called when a session ends. */
    fun deactivate() {
        synchronized(lock) {
            current = Snapshot()
        }
    }

    /**
     * Called by [AppBlockerAccessibilityService] on every tick to signal
     * that the service is alive and actively enforcing blocks.
     */
    fun markHeartbeat() {
        synchronized(lock) {
            lastHeartbeatElapsed = SystemClock.elapsedRealtime()
        }
    }

    /** Mark the service as disconnected (called from onDestroy). */
    fun markDisconnected() {
        synchronized(lock) {
            lastHeartbeatElapsed = 0L
        }
    }

    /**
     * Returns true if the accessibility service is alive and actively enforcing.
     * Uses elapsed realtime (survives deep sleep) to detect stale heartbeats.
     */
    val isServiceAlive: Boolean
        get() = synchronized(lock) {
            val last = lastHeartbeatElapsed
            if (last <= 0L) return@synchronized false
            val age = SystemClock.elapsedRealtime() - last
            age in 0..HEARTBEAT_STALE_MS
        }

    /**
     * Returns true if the given [packageName] should be blocked right now.
     *
     * - Allow mode: block everything **except** packages in the list.
     * - Block mode: block only packages **in** the list.
     *
     * Always returns false when blocking is inactive.
     */
    fun shouldBlock(packageName: String): Boolean {
        val snap = current
        if (!snap.isBlocking) return false
        if (snap.isPauseActive) return false
        return if (snap.isAllowMode) {
            packageName !in snap.blockedPackages
        } else {
            packageName in snap.blockedPackages
        }
    }

    /** True when the profile has at least one domain rule to enforce in browsers. */
    fun hasDomainRules(): Boolean {
        val snap = current
        if (!snap.isBlocking || snap.isPauseActive) return false
        return snap.domains.isNotEmpty()
    }

    /**
     * Whether the given [host] (from a browser URL bar) should be blocked.
     *
     * - Block mode: block when [host] matches any listed domain (including subdomains).
     * - Allow mode: block when [host] does **not** match any listed domain.
     */
    fun shouldBlockDomain(host: String): Boolean {
        val snap = current
        if (!snap.isBlocking || snap.isPauseActive || snap.domains.isEmpty()) return false
        val normalizedHost = DomainBlocking.normalize(host) ?: return false
        val matchesRule = snap.domains.any { DomainBlocking.matches(normalizedHost, it) }
        return if (snap.isAllowModeDomains) {
            !matchesRule
        } else {
            matchesRule
        }
    }
}
