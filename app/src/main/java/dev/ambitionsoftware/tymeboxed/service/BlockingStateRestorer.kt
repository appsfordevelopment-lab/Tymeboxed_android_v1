package dev.ambitionsoftware.tymeboxed.service

import dev.ambitionsoftware.tymeboxed.domain.FocusMessages
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import dev.ambitionsoftware.tymeboxed.domain.model.hasTimedBreakFlow

/**
 * Pushes [Session] + [Profile] from Room into [ActiveBlockingState] after
 * process death or when the app cold-starts with an open session.
 */
object BlockingStateRestorer {

    /**
     * Pushes [Profile.enableLiveActivity] into [ActiveBlockingState] when this profile
     * is the one currently blocking. No-op if there is no matching active session.
     */
    fun syncLiveActivityForActiveProfile(profile: Profile) {
        val snap = ActiveBlockingState.current
        if (!snap.isBlocking || snap.profileId != profile.id) return
        val quote = if (profile.enableLiveActivity) {
            snap.focusQuoteMessage ?: FocusMessages.randomMessage()
        } else {
            null
        }
        ActiveBlockingState.setLiveActivityNotification(
            enabled = profile.enableLiveActivity,
            focusQuoteMessage = quote,
        )
    }

    fun apply(
        profile: Profile,
        session: Session,
        blockedPackages: Set<String>,
        strictModeEnabled: Boolean,
    ) {
        val strategyId = profile.strategyId
        // Keep focus deadline even during a break pause so auto-end still works after resume,
        // and in-memory extensions from [ActiveBlockingState.extendFocusTimerEnd] apply cleanly.
        val focusEnd = when (strategyId) {
            BlockingStrategyId.FOCUS_TIMER, BlockingStrategyId.FOCUS_TIMER_BREAK -> {
                val mins = profile.strategyData?.toIntOrNull() ?: 25
                session.startTime + mins * 60_000L
            }
            else -> null
        }
        val breakResume = if (profile.hasTimedBreakFlow() &&
            session.isPauseActive &&
            session.pauseStartTime != null
        ) {
            val mins = profile.breakTimeInMinutes.coerceIn(5, 60)
            session.pauseStartTime + mins * 60_000L
        } else {
            null
        }
        val focusQuote = if (profile.enableLiveActivity) FocusMessages.randomMessage() else null
        ActiveBlockingState.activate(
            profileId = profile.id,
            profileName = profile.name,
            blockedPackages = blockedPackages,
            isAllowMode = profile.isAllowMode,
            domains = profile.domains,
            isAllowModeDomains = profile.isAllowModeDomains,
            sessionStartTimeMs = session.startTime,
            strategyId = strategyId,
            isPauseActive = session.isPauseActive,
            breakAutoResumeAtMs = breakResume,
            focusTimerEndMs = focusEnd,
            strictModeEnabled = strictModeEnabled,
            enableLiveActivityNotification = profile.enableLiveActivity,
            focusQuoteMessage = focusQuote,
        )
    }
}
