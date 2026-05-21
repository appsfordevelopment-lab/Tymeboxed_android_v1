package dev.ambitionsoftware.tymeboxed.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.ambitionsoftware.tymeboxed.data.db.TymeBoxedDatabase
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.domain.model.toDomain
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restores an active blocking session after device reboot.
 *
 * On BOOT_COMPLETED / LOCKED_BOOT_COMPLETED, queries Room for any session
 * whose endTime is null. If one exists, rehydrates [ActiveBlockingState]
 * and restarts [SessionBlockerService] so blocking survives a reboot.
 *
 * Uses a short-lived CoroutineScope for the DB query since BroadcastReceiver
 * has a ~10 second execution window.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var database: TymeBoxedDatabase

    @Inject
    lateinit var scheduleAlarmScheduler: ProfileScheduleAlarmScheduler

    @Inject
    lateinit var sessionReminderScheduler: SessionReminderScheduler

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var sessionLifecycle: ActiveSessionLifecycleCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        Log.i(TAG, "Boot completed — checking for active session to restore.")

        val pendingResult = goAsync()

        // Scope is tied to this broadcast only (not a process-wide field) so work
        // completes when [pendingResult.finish] runs and is not leaked across reboots.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Find any active (non-ended) session
                val activeSession = database.sessionDao().findActive()
                if (activeSession == null) {
                    Log.i(TAG, "No active session to restore.")
                } else {
                    // Load the profile for this session
                    val profileWithApps = database.profileDao().getByIdWithApps(activeSession.profileId)
                    if (profileWithApps == null) {
                        Log.w(TAG, "Active session found but profile not found. Ending stale session.")
                        database.sessionDao().endAllActive(System.currentTimeMillis())
                    } else {
                        val profile = profileWithApps.toDomain()
                        val session = activeSession.toDomain()
                        val blockedPkgs = profile.blockedPackages.toSet()
                        val strictModeEnabled = appPreferences.strictModeEnabled.first()

                        BlockingStateRestorer.apply(
                            profile = profile,
                            session = session,
                            blockedPackages = blockedPkgs,
                            strictModeEnabled = strictModeEnabled,
                        )

                        withContext(Dispatchers.Main) {
                            sessionLifecycle.sync()
                        }

                        Log.i(TAG, "Restored active session for profile '${profile.name}' " +
                            "with ${blockedPkgs.size} blocked packages.")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to restore session on boot: ${e.message}", e)
            } finally {
                // We're already in a coroutine on Dispatchers.IO — call the suspend
                // function directly. Using runBlocking here would needlessly pin a
                // worker thread and (in the past, when called from onReceive) risked
                // ANRs by blocking the main thread.
                try {
                    scheduleAlarmScheduler.rescheduleAll()
                } catch (e: Throwable) {
                    Log.w(TAG, "Schedule alarm reschedule after boot: ${e.message}")
                }
                // Audit #30: a one-shot end-of-session reminder set with
                // [AlarmManager.setAlarmClock] is wiped by the kernel reboot.
                // The scheduler persists its parameters so we can re-arm now.
                try {
                    sessionReminderScheduler.restorePendingAfterBoot()
                } catch (e: Throwable) {
                    Log.w(TAG, "Session reminder restore after boot: ${e.message}")
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
