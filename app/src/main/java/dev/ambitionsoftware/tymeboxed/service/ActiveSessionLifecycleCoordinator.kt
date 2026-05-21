package dev.ambitionsoftware.tymeboxed.service

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Starts/stops the session foreground service and background timer based on whether
 * the active profile has the lock-screen widget (Live Activity) enabled.
 *
 * When the widget is **off**, we do **not** start [SessionBlockerService] at all — Android
 * would still show a silent "Tyme Boxed" stub notification after [startForeground] even
 * with [Service.stopForeground] detach. Timer/auto-end logic runs on a lightweight coroutine
 * instead (blocking still uses [AppBlockerAccessibilityService] + [ActiveBlockingState]).
 */
@Singleton
class ActiveSessionLifecycleCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionTimerHandler: SessionTimerHandler,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var backgroundTimerJob: Job? = null

    /**
     * Call after [BlockingStateRestorer.apply], [ActiveBlockingState.deactivate], or when
     * the user toggles the lock-screen widget on the profile edit screen.
     */
    fun sync() {
        scope.launch(Dispatchers.Main.immediate) {
            val snap = ActiveBlockingState.current
            if (!snap.isBlocking) {
                stopForegroundService()
                stopBackgroundTimer()
                return@launch
            }
            if (snap.enableLiveActivityNotification) {
                stopBackgroundTimer()
                startForegroundService(snap)
            } else {
                stopForegroundService()
                startBackgroundTimer()
            }
        }
    }

    private fun startForegroundService(snap: ActiveBlockingState.Snapshot) {
        val profileName = snap.profileName?.takeUnless { it.isBlank() } ?: "Focus Session"
        val startMs = snap.sessionStartTimeMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val intent = SessionBlockerService.startIntent(context, profileName, startMs)
        ForegroundServiceSafeLauncher.start(context, intent)
    }

    /**
     * Stops the FGS without ending the focus session.
     *
     * Uses [Context.stopService] instead of [Context.startService] with a stop
     * action. Starting a service from the background (e.g. boot receiver, alarm)
     * throws on API 26+ when the app is not in the foreground. [stopService] is
     * allowed from the background and is a no-op when the service is not running.
     */
    private fun stopForegroundService() {
        val intent = Intent(context, SessionBlockerService::class.java)
        if (context.stopService(intent)) {
            Log.i(TAG, "Stopped SessionBlockerService (session continues).")
        }
    }

    private fun startBackgroundTimer() {
        if (backgroundTimerJob?.isActive == true) return
        backgroundTimerJob = scope.launch {
            while (isActive) {
                val snap = ActiveBlockingState.current
                if (!snap.isBlocking || snap.enableLiveActivityNotification) break
                delay(1_000L)
                try {
                    sessionTimerHandler.onServiceTick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Background session timer: ${e.message}")
                }
            }
        }
    }

    private fun stopBackgroundTimer() {
        backgroundTimerJob?.cancel()
        backgroundTimerJob = null
    }

    companion object {
        private const val TAG = "SessionLifecycle"
    }
}
