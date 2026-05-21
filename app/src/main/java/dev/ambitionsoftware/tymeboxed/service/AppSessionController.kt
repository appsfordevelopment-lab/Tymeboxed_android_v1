package dev.ambitionsoftware.tymeboxed.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.admin.DeviceAdminSupport
import dev.ambitionsoftware.tymeboxed.admin.StrictModeProtection
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.data.repository.ProfileRepository
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import dev.ambitionsoftware.tymeboxed.domain.model.withBreakFlagsFromStrategy
import dev.ambitionsoftware.tymeboxed.service.inapp.InAppBlockingHandler
import dagger.Lazy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Central place to start / end a focus session and stop the foreground blocking service
 * (used from [dev.ambitionsoftware.tymeboxed.ui.screens.home.HomeViewModel],
 * [SessionTimerHandler], and schedule alarms).
 */
@Singleton
class AppSessionController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val sessionReminderScheduler: SessionReminderScheduler,
    private val appPreferences: AppPreferences,
    private val sessionLifecycle: Lazy<ActiveSessionLifecycleCoordinator>,
) {

    /**
     * Serializes [startFocusSession] / [endSessionCompletely]. Without this, a
     * schedule alarm + a user tap (or two rapid taps from different surfaces)
     * could race and produce two open sessions in Room, or end a session that
     * was being concurrently started — leaving the foreground service in an
     * inconsistent state with [ActiveBlockingState].
     */
    private val sessionMutex = Mutex()

    /**
     * Starts a focus session for [profileId]. Ends any other active session first.
     * When [selectedTimerMinutes] is set, updates focus-timer profile duration (iOS parity).
     */
    suspend fun startFocusSession(
        profileId: String,
        selectedTimerMinutes: Int? = null,
        isBreakDuration: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
        sessionReminderScheduler.cancelAll()
        var profile = profileRepository.findById(profileId) ?: return@withContext

        if (selectedTimerMinutes != null) {
            val updated = when (profile.strategyId) {
                BlockingStrategyId.FOCUS_TIMER -> {
                    val mins = selectedTimerMinutes.coerceIn(15, 24 * 60)
                    profile.copy(
                        strategyData = mins.toString(),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                BlockingStrategyId.FOCUS_TIMER_BREAK -> {
                    val mins = selectedTimerMinutes.coerceIn(5, 60)
                    if (isBreakDuration) {
                        profile.copy(
                            breakTimeInMinutes = mins,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        profile.copy(
                            strategyData = mins.toString(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }
                else -> null
            }
            if (updated != null) {
                profileRepository.save(updated.withBreakFlagsFromStrategy())
                profile = updated
            }
        }

        sessionRepository.resetActive()

        val now = System.currentTimeMillis()
        val session = Session(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            startTime = now,
        )
        sessionRepository.insert(session)

        // Strict mode requires an actively-registered device admin: without it the OS
        // Package Installer will not show "Can't uninstall active device admin app",
        // and the accessibility redirect alone isn't enough deterrence. If the user
        // disabled admin via system Settings since we last persisted the pref, fall
        // back to a non-strict session and rewrite the pref so the UI stays honest.
        val strictModePref = appPreferences.strictModeEnabled.first()
        val adminActive = DeviceAdminSupport.isAdminActive(appContext)
        val strictModeEnabled = strictModePref && adminActive
        if (strictModePref && !adminActive) {
            Log.w(
                TAG,
                "Strict-mode pref was ON but Device Admin is NOT active — " +
                    "starting a non-strict session and clearing the pref so the UI " +
                    "doesn't report false protection. The user must re-enable admin " +
                    "from Settings to re-arm strict mode.",
            )
            appPreferences.setStrictModeEnabled(false)
        }

        BlockingStateRestorer.apply(
            profile = profile,
            session = session,
            blockedPackages = profile.blockedPackages.toSet(),
            strictModeEnabled = strictModeEnabled,
        )

        // Engage the hard uninstall block while the session runs. No-op when we
        // aren't Device Owner — the accessibility-level interception still applies.
        if (strictModeEnabled) {
            StrictModeProtection.tryHardenUninstallBlock(appContext, blocked = true)
        }

        withContext(Dispatchers.Main) {
            sessionLifecycle.get().sync()
        }
        }
    }

    /**
     * Ends the focus session, stops the blocking service, and (per iOS
     * [StrategyManager] `case .ended`) schedules a local notification if
     * [dev.ambitionsoftware.tymeboxed.domain.model.Profile.reminderTimeSeconds] is set.
     */
    suspend fun endSessionCompletely() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val active = sessionRepository.findActive()
            val profileForReminder = active?.let { profileRepository.findById(it.profileId) }
            ActiveBlockingState.deactivate()
            InAppBlockingHandler.resetCaches()
            sessionRepository.resetActive()
            // Lift the hard uninstall block (Device-Owner only); accessibility-level interception
            // already gates on [ActiveBlockingState.current.isBlocking] so it stops on its own.
            StrictModeProtection.tryHardenUninstallBlock(appContext, blocked = false)
            withContext(Dispatchers.Main) {
                sessionLifecycle.get().sync()
            }
            if (profileForReminder != null) {
                if (profileForReminder.isSessionReminderEnabled()) {
                    sessionReminderScheduler.scheduleAfterSessionEnd(profileForReminder)
                } else {
                    sessionReminderScheduler.cancelAll()
                }
            }
        }
    }

    suspend fun updateSessionEntity(session: Session) = withContext(Dispatchers.IO) {
        sessionRepository.update(session)
    }

    private companion object {
        private const val TAG = "AppSessionController"
    }
}
