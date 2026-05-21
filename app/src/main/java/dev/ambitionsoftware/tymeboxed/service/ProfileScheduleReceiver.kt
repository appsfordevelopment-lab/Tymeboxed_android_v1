package dev.ambitionsoftware.tymeboxed.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.ambitionsoftware.tymeboxed.data.repository.ProfileRepository
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Fires at computed schedule boundaries to start or end focus sessions (iOS: [ScheduleTimerActivity]).
 */
@AndroidEntryPoint
class ProfileScheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var appSessionController: AppSessionController

    @Inject
    lateinit var scheduleAlarmScheduler: ProfileScheduleAlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val action = intent.action ?: return
        val pending = goAsync()

        scope.launch {
            try {
                // Android kills BroadcastReceivers (even with goAsync) after ~10s.
                // Cap our work at 8s so the OS doesn't tear us down mid-DB-write
                // and leave Room / ActiveBlockingState in a half-applied state.
                withTimeout(SCHEDULE_BUDGET_MS) {
                    when (action) {
                        ACTION_START -> handleStart(profileId)
                        ACTION_END -> handleEnd(profileId)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Schedule action timed out after ${SCHEDULE_BUDGET_MS}ms: $action")
            } catch (e: Throwable) {
                Log.e(TAG, "Schedule action failed: ${e.message}", e)
            } finally {
                // Always reschedule the next boundary even if the action itself
                // timed out, so the schedule keeps advancing instead of getting
                // stuck on a poisoned profile.
                runCatching {
                    withTimeout(RESCHEDULE_BUDGET_MS) {
                        scheduleAlarmScheduler.rescheduleAll()
                    }
                }.onFailure { e ->
                    Log.w(TAG, "Reschedule after action failed: ${e.message}")
                }
                pending.finish()
            }
        }
    }

    private suspend fun handleStart(profileId: String) {
        val profile = profileRepository.findById(profileId) ?: return
        val schedule = profile.schedule ?: return
        if (!schedule.isActive) return
        val now = System.currentTimeMillis()
        if (!schedule.isWithinWindow(now)) {
            Log.i(TAG, "Skipping schedule start — outside window")
            return
        }
        val active = sessionRepository.findActive()
        if (active?.profileId == profileId) {
            Log.i(TAG, "Schedule start — session already active for profile")
            return
        }
        if (active != null) {
            Log.i(TAG, "Schedule start — ending other session for $profileId")
            appSessionController.endSessionCompletely()
        }
        appSessionController.startFocusSession(profileId)
        Log.i(TAG, "Started scheduled session for $profileId")
    }

    private suspend fun handleEnd(profileId: String) {
        val active = sessionRepository.findActive() ?: return
        if (active.profileId != profileId) return
        appSessionController.endSessionCompletely()
        Log.i(TAG, "Ended scheduled session for $profileId")
    }

    companion object {
        const val ACTION_START = "dev.ambitionsoftware.tymeboxed.action.SCHEDULE_START"
        const val ACTION_END = "dev.ambitionsoftware.tymeboxed.action.SCHEDULE_END"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val TAG = "ProfileScheduleRx"

        /** Soft budget: BroadcastReceiver is killed by the OS after ~10s. */
        private const val SCHEDULE_BUDGET_MS = 8_000L
        private const val RESCHEDULE_BUDGET_MS = 1_500L
    }
}
