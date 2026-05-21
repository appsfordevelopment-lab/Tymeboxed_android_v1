package dev.ambitionsoftware.tymeboxed.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class InsightsPeriod {
    THIS_WEEK,
    THIS_MONTH,
}

data class ProfileInsightsUiState(
    val period: InsightsPeriod = InsightsPeriod.THIS_WEEK,
    val periodButtonLabel: String = "This week",
    val avgFocusMinutes: Int = 0,
    /** 7 values: week = each calendar day; month = sum of focus minutes per day-of-week (Sun–Sat). */
    val dailyFocusMinutes: List<Int> = List(7) { 0 },
    val dayLabels: List<String> = emptyList(),
    val totalFocusMinutes: Int = 0,
    val completedSessionCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileInsightsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    /**
     * Single source of truth for which profile we're observing. Holding this in
     * a [MutableStateFlow] (rather than a plain `var` + manual Job swapping —
     * see audit #23 and #26) makes the read/write atomic across coroutines and
     * lets us reactively re-subscribe via [flatMapLatest] whenever it changes.
     */
    private val _profileId = MutableStateFlow<String?>(null)
    val currentProfileId: StateFlow<String?> = _profileId.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(InsightsPeriod.THIS_WEEK)
    val selectedPeriod: StateFlow<InsightsPeriod> = _selectedPeriod.asStateFlow()

    /**
     * Derived state — re-collects automatically whenever the profile or period
     * changes, and stops upstream session collection ~5 s after the screen
     * unsubscribes (via `WhileSubscribed`). Replaces the previous manual
     * `Job` swap which leaked the [SharingStarted] coroutine scope on every
     * `setPeriod` call.
     */
    val uiState: StateFlow<ProfileInsightsUiState> =
        combine(_profileId, _selectedPeriod) { pid, period -> pid to period }
            .distinctUntilChanged()
            .flatMapLatest { (pid, period) ->
                if (pid == null) flowOf(ProfileInsightsUiState())
                else streamForProfile(pid, period)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                ProfileInsightsUiState(),
            )

    fun startCollecting(profileId: String) {
        // Single assignment via the StateFlow — any downstream collect()/state
        // computed from this re-evaluates atomically.
        _profileId.value = profileId
    }

    fun setPeriod(period: InsightsPeriod) {
        _selectedPeriod.value = period
    }

    /**
     * No-op kept for API compatibility with the screen. Upstream collection
     * is now driven by [SharingStarted.WhileSubscribed] and stops
     * automatically when the UI unsubscribes.
     */
    fun stopCollecting() {
        // Intentionally left blank. Lifecycle is handled by WhileSubscribed.
    }

    private fun streamForProfile(
        profileId: String,
        period: InsightsPeriod,
    ) = when (period) {
        InsightsPeriod.THIS_WEEK -> streamWeek(profileId)
        InsightsPeriod.THIS_MONTH -> streamMonth(profileId)
    }

    private fun streamWeek(profileId: String) = run {
        val zone = ZoneId.systemDefault()
        val weekStart = ZonedDateTime.now(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val startMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val labelFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
        val labels = (0 until 7).map { o ->
            weekStart.plusDays(o.toLong()).format(labelFormatter)
        }
        val src = sessionRepository
            .observeCompletedSessionsForProfileBetween(profileId, startMs, endMs)
        flow {
            // Emit a seeded state first so the chart frame appears
            // immediately without waiting for the first DB row.
            emit(
                ProfileInsightsUiState(
                    period = InsightsPeriod.THIS_WEEK,
                    periodButtonLabel = "This week",
                    dayLabels = labels,
                ),
            )
            src.collect { sessions ->
                emit(
                    buildWeekState(
                        sessions = sessions,
                        weekStart = weekStart,
                        zone = zone,
                        labels = labels,
                    ),
                )
            }
        }
    }

    private fun streamMonth(profileId: String) = run {
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.from(ZonedDateTime.now(zone).toLocalDate())
        val firstDay = ym.atDay(1)
        val startMs = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayNames = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        ).map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        val src = sessionRepository
            .observeCompletedSessionsForProfileBetween(profileId, startMs, endMs)
        flow {
            emit(
                ProfileInsightsUiState(
                    period = InsightsPeriod.THIS_MONTH,
                    periodButtonLabel = "This month",
                    dayLabels = dayNames,
                ),
            )
            src.collect { sessions ->
                emit(
                    buildMonthState(
                        sessions = sessions,
                        yearMonth = ym,
                        zone = zone,
                        dayLabels = dayNames,
                    ),
                )
            }
        }
    }

    /**
     * Audit #36: never `Long.toInt()` raw duration math. A bad clock or a
     * malformed row could give us a duration that overflows the `Int` range
     * (~24.8 days in minutes), silently producing a negative chart value.
     * Clamp into `[0, Int.MAX_VALUE]` before narrowing.
     */
    private fun safeMinutesBetween(startMs: Long, endMs: Long): Int {
        val diff = endMs - startMs
        if (diff <= 0L) return 0
        val mins = diff / 60_000L
        return mins.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun buildWeekState(
        sessions: List<Session>,
        weekStart: LocalDate,
        zone: ZoneId,
        labels: List<String>,
    ): ProfileInsightsUiState {
        val daily = MutableList(7) { 0 }
        var total = 0
        for (s in sessions) {
            val end = s.endTime ?: continue
            if (end <= s.startTime) continue
            val durMin = safeMinutesBetween(s.startTime, end)
            // Use Math.addExact via Long math to also guard the rolling sum.
            total = ((total.toLong() + durMin).coerceAtMost(Int.MAX_VALUE.toLong())).toInt()
            val localDate = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate()
            val dayIndex = ChronoUnit.DAYS.between(weekStart, localDate)
                .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                .toInt()
            if (dayIndex in 0..6) {
                daily[dayIndex] = ((daily[dayIndex].toLong() + durMin)
                    .coerceAtMost(Int.MAX_VALUE.toLong())).toInt()
            }
        }
        val n = sessions.size
        val avg = if (n == 0) 0 else (total.toFloat() / n).roundToInt()
        return ProfileInsightsUiState(
            period = InsightsPeriod.THIS_WEEK,
            periodButtonLabel = "This week",
            avgFocusMinutes = avg,
            dailyFocusMinutes = daily.toList(),
            dayLabels = labels,
            totalFocusMinutes = total,
            completedSessionCount = n,
        )
    }

    private fun buildMonthState(
        sessions: List<Session>,
        yearMonth: YearMonth,
        zone: ZoneId,
        dayLabels: List<String>,
    ): ProfileInsightsUiState {
        val byDow = MutableList(7) { 0 }
        var total = 0
        for (s in sessions) {
            val end = s.endTime ?: continue
            if (end <= s.startTime) continue
            val localDate = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate()
            if (YearMonth.from(localDate) != yearMonth) continue
            val durMin = safeMinutesBetween(s.startTime, end)
            total = ((total.toLong() + durMin).coerceAtMost(Int.MAX_VALUE.toLong())).toInt()
            val idx = localDate.dayOfWeek.value % 7
            if (idx in 0..6) {
                byDow[idx] = ((byDow[idx].toLong() + durMin)
                    .coerceAtMost(Int.MAX_VALUE.toLong())).toInt()
            }
        }
        val n = sessions.size
        val avg = if (n == 0) 0 else (total.toFloat() / n).roundToInt()
        return ProfileInsightsUiState(
            period = InsightsPeriod.THIS_MONTH,
            periodButtonLabel = "This month",
            avgFocusMinutes = avg,
            dailyFocusMinutes = byDow.toList(),
            dayLabels = dayLabels,
            totalFocusMinutes = total,
            completedSessionCount = n,
        )
    }

    companion object {
        /** Hand-off window for config changes (rotation) before we stop upstream collection. */
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
