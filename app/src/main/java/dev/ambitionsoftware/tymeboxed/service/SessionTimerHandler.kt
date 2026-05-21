package dev.ambitionsoftware.tymeboxed.service

import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drives auto-expiry for the focus-timer strategy and auto-resume after a
 * break (focus + break), matching iOS `NFCTimer` / `NFCPauseTimer` behavior.
 * Invoked on a 1s tick from [SessionBlockerService] (lock-screen widget on) or
 * [ActiveSessionLifecycleCoordinator] (widget off) while a session is active.
 */
@Singleton
class SessionTimerHandler @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val appSessionController: AppSessionController,
) {

    suspend fun onServiceTick() {
        val snap = ActiveBlockingState.current
        if (!snap.isBlocking) return
        val now = System.currentTimeMillis()
        if (snap.isPauseActive) {
            val resumeAt = snap.breakAutoResumeAtMs ?: return
            if (now < resumeAt) return
            resumeFocusAfterBreak()
            return
        }
        val isFocusWithTimer = snap.strategyId == BlockingStrategyId.FOCUS_TIMER ||
            snap.strategyId == BlockingStrategyId.FOCUS_TIMER_BREAK
        if (isFocusWithTimer) {
            val end = snap.focusTimerEndMs ?: return
            if (now >= end) {
                appSessionController.endSessionCompletely()
            }
        }
    }

    private suspend fun resumeFocusAfterBreak() = withContext(Dispatchers.IO) {
        val s = sessionRepository.findActive() ?: return@withContext
        if (!s.isPauseActive) return@withContext
        val pauseStart = s.pauseStartTime ?: return@withContext
        val breakMillis = (System.currentTimeMillis() - pauseStart).coerceAtLeast(0L)
        val cleared = s.copy(
            isPauseActive = false,
            pauseStartTime = null,
        )
        sessionRepository.update(cleared)
        ActiveBlockingState.setPause(isPauseActive = false, breakAutoResumeAtMs = null)
        ActiveBlockingState.extendFocusTimerEnd(breakMillis)
    }
}
