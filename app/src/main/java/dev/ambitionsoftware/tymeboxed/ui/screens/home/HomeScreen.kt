@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package dev.ambitionsoftware.tymeboxed.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.os.Build
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.data.prefs.ActivityChartType
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.data.repository.AuthRepository
import dev.ambitionsoftware.tymeboxed.data.repository.ProfileRepository
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import dev.ambitionsoftware.tymeboxed.domain.model.hasTimedBreakFlow
import dev.ambitionsoftware.tymeboxed.domain.model.strategyInfoById
import dev.ambitionsoftware.tymeboxed.permissions.PermissionsViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.nfc.NfcTagVerifyResult
import dev.ambitionsoftware.tymeboxed.service.ActiveBlockingState
import dev.ambitionsoftware.tymeboxed.service.AppSessionController
import dev.ambitionsoftware.tymeboxed.service.BlockingStateRestorer
import dev.ambitionsoftware.tymeboxed.service.ActiveSessionLifecycleCoordinator
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCard
import dev.ambitionsoftware.tymeboxed.ui.theme.EmergencyRed
import dev.ambitionsoftware.tymeboxed.ui.screens.insights.ProfileInsightsScreen
import dev.ambitionsoftware.tymeboxed.ui.theme.LocalAccentColor
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val sessionRepository: SessionRepository,
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val appSessionController: AppSessionController,
    private val sessionLifecycle: ActiveSessionLifecycleCoordinator,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val profiles: StateFlow<List<Profile>> = profileRepository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Audit #34: previously the screen showed the "no profiles yet" empty state
     * during initial async load because `initialValue = emptyList()` is
     * indistinguishable from "user has zero profiles". This flag flips
     * `false` after the first emission so the UI can show a spinner only while
     * we're actually waiting on Room.
     */
    val profilesInitialLoading: StateFlow<Boolean> = profileRepository.observeAll()
        .map { false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val activeSession: StateFlow<Session?> = sessionRepository.activeSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _sessionCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sessionCounts: StateFlow<Map<String, Int>> = _sessionCounts.asStateFlow()

    val activityChartVisible: StateFlow<Boolean> = appPreferences.activityChartVisible.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val activityChartType: StateFlow<String> = appPreferences.activityChartType.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityChartType.MONTHLY,
    )

    val emergencyUnblocksRemaining: StateFlow<Int> = appPreferences.emergencyUnblocksRemaining.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences.DEFAULT_EMERGENCY_UNBLOCKS,
    )

    val emergencyResetPeriodWeeks: StateFlow<Int> = appPreferences.emergencyResetPeriodWeeks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences.DEFAULT_EMERGENCY_RESET_WEEKS,
    )

    val emergencyNextResetEpochMs: StateFlow<Long> = appPreferences.emergencyNextResetEpochMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = System.currentTimeMillis(),
    )

    /** Start of local day 120 days ago — enough for monthly + 4-week charts. */
    private val activityHistoryStartMs: Long = run {
        val z = ZoneId.systemDefault()
        LocalDate.now(z).minusDays(120).atStartOfDay(z).toInstant().toEpochMilli()
    }

    /**
     * Total focus minutes per local calendar day (all profiles), from completed sessions.
     */
    val activityMinutesByLocalDay: StateFlow<Map<LocalDate, Float>> =
        sessionRepository.observeCompletedSince(activityHistoryStartMs)
            .map { sessions -> aggregateSessionMinutesByLocalDay(sessions) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    /**
     * Per local day, per profile, focus minutes (completed sessions) — for activity day insights.
     */
    val activityMinutesByProfileByLocalDay: StateFlow<Map<LocalDate, Map<String, Float>>> =
        sessionRepository.observeCompletedSince(activityHistoryStartMs)
            .map { sessions -> aggregateSessionMinutesByLocalDayByProfile(sessions) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    fun setActivityChartVisible(visible: Boolean) {
        viewModelScope.launch { appPreferences.setActivityChartVisible(visible) }
    }

    fun setActivityChartType(type: String) {
        viewModelScope.launch { appPreferences.setActivityChartType(type) }
    }

    init {
        viewModelScope.launch {
            profileRepository.observeAll().collect { list ->
                _sessionCounts.value = list.associate { profile ->
                    profile.id to sessionRepository.countCompletedForProfile(profile.id)
                }
            }
        }
        // Rehydrate blocking state if the app was killed while a session was active.
        viewModelScope.launch {
            val session = sessionRepository.findActive() ?: return@launch
            if (ActiveBlockingState.current.isBlocking) return@launch
            val profile = profileRepository.findById(session.profileId) ?: return@launch
            val strictModeEnabled = appPreferences.strictModeEnabled.first()
            BlockingStateRestorer.apply(
                profile = profile,
                session = session,
                blockedPackages = profile.blockedPackages.toSet(),
                strictModeEnabled = strictModeEnabled,
            )
            sessionLifecycle.sync()
        }
    }

    suspend fun verifyNfcTagId(tagId: String): NfcTagVerifyResult =
        authRepository.verifyNfcTag(tagId)

    /**
     * @param selectedTimerMinutes when strategy is focus timer or focus+break, the chosen
     * duration in minutes; persisted to the profile.
     * @param isBreakDuration when true (session with break), minutes update [Profile.breakTimeInMinutes].
     */
    fun startSession(
        profileId: String,
        selectedTimerMinutes: Int? = null,
        isBreakDuration: Boolean = false,
    ) {
        viewModelScope.launch {
            appSessionController.startFocusSession(
                profileId,
                selectedTimerMinutes = selectedTimerMinutes,
                isBreakDuration = isBreakDuration,
            )
        }
    }

    /**
     * Plain in-app stop (no NFC) — for manual strategy or when NFC is unavailable.
     */
    fun stopSession() {
        viewModelScope.launch {
            appSessionController.endSessionCompletely()
        }
    }

    /**
     * Focus + break: begin the timed break without scanning (used when there is no NFC hardware).
     */
    fun startTimedBreakFromApp() {
        viewModelScope.launch {
            val session = sessionRepository.findActive() ?: return@launch
            val profile = profileRepository.findById(session.profileId) ?: return@launch
            if (!profile.hasTimedBreakFlow()) return@launch
            if (session.isPauseActive) return@launch
            startBreakFromNfcScan(session, profile)
        }
    }

    /**
     * User scanned a valid tag after requesting stop. Matches iOS: most strategies
     * end the session; timed-break flows enter break first when applicable.
     */
    fun onNfcScannedToStop() {
        viewModelScope.launch {
            val session = sessionRepository.findActive() ?: return@launch
            val profile = profileRepository.findById(session.profileId) ?: return@launch
            if (profile.hasTimedBreakFlow()) {
                if (session.isPauseActive) {
                    appSessionController.endSessionCompletely()
                } else {
                    startBreakFromNfcScan(session, profile)
                }
            } else {
                appSessionController.endSessionCompletely()
            }
        }
    }

    private suspend fun startBreakFromNfcScan(session: Session, profile: Profile) {
        val breakMins = profile.breakTimeInMinutes.coerceIn(5, 60)
        val endBreak = System.currentTimeMillis() + breakMins * 60_000L
        val updated = session.copy(
            isPauseActive = true,
            pauseStartTime = System.currentTimeMillis(),
        )
        appSessionController.updateSessionEntity(updated)
        ActiveBlockingState.setPause(isPauseActive = true, breakAutoResumeAtMs = endBreak)
    }

    fun checkAndResetEmergencyUnblocks() {
        viewModelScope.launch { appPreferences.checkAndResetEmergencyUnblocks() }
    }

    fun setEmergencyResetPeriodWeeks(weeks: Int) {
        viewModelScope.launch { appPreferences.setEmergencyResetPeriodWeeks(weeks) }
    }

    /**
     * Foqos-style emergency stop: ends the session without NFC and consumes one
     * of the limited emergency unblocks.
     */
    fun performEmergencyUnblock(onComplete: () -> Unit) {
        viewModelScope.launch {
            if (activeSession.value == null) {
                onComplete()
                return@launch
            }
            if (!appPreferences.tryConsumeEmergencyUnblock()) {
                onComplete()
                return@launch
            }
            appSessionController.endSessionCompletely()
            onComplete()
        }
    }
}

/**
 * Splits each completed session across every **local calendar day** it overlaps (in [zone]),
 * so long sessions and sessions crossing midnight add minutes to the correct days.
 * If [byProfileIdByDay] is null, only [totalByDay] is updated.
 */
private fun addSessionMinutesAcrossLocalDays(
    startMs: Long,
    endMs: Long,
    profileId: String,
    zone: ZoneId,
    totalByDay: MutableMap<LocalDate, Float>,
    byProfileIdByDay: MutableMap<LocalDate, MutableMap<String, Float>>? = null,
) {
    if (endMs <= startMs) return
    var t = startMs
    while (t < endMs) {
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), zone)
        val date = z.toLocalDate()
        val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val segEnd = minOf(endMs, nextDayStart)
        val m = (segEnd - t) / 60_000f
        if (m > 0f) {
            totalByDay[date] = (totalByDay[date] ?: 0f) + m
            if (byProfileIdByDay != null) {
                val per = byProfileIdByDay.getOrPut(date) { mutableMapOf() }
                per[profileId] = (per[profileId] ?: 0f) + m
            }
        }
        t = segEnd
    }
}

/** Sums focus minutes per local day (all profiles). */
private fun aggregateSessionMinutesByLocalDay(sessions: List<Session>): Map<LocalDate, Float> {
    if (sessions.isEmpty()) return emptyMap()
    val zone = ZoneId.systemDefault()
    val out = mutableMapOf<LocalDate, Float>()
    for (s in sessions) {
        val end = s.endTime ?: continue
        if (end <= s.startTime) continue
        addSessionMinutesAcrossLocalDays(s.startTime, end, s.profileId, zone, out, byProfileIdByDay = null)
    }
    return out
}

/**
 * Sums focus minutes per local day and per [Session.profileId] (same day-splitting as totals).
 * Used to pick the dominant profile in the day insight row.
 */
private fun aggregateSessionMinutesByLocalDayByProfile(
    sessions: List<Session>,
): Map<LocalDate, Map<String, Float>> {
    if (sessions.isEmpty()) return emptyMap()
    val zone = ZoneId.systemDefault()
    val totals = mutableMapOf<LocalDate, Float>()
    val byProfile = mutableMapOf<LocalDate, MutableMap<String, Float>>()
    for (s in sessions) {
        val end = s.endTime ?: continue
        if (end <= s.startTime) continue
        addSessionMinutesAcrossLocalDays(s.startTime, end, s.profileId, zone, totals, byProfile)
    }
    return byProfile.mapValues { it.value.toMap() }
}

/**
 * Home screen — Phase 1 skeleton.
 *
 * Layout mirrors iOS `HomeView.swift:68-107`:
 *   - Top row: `AppTitle` ("Tyme Boxed") on the left + settings gear on the right.
 *   - Body: permissions banner if any required permission is missing; then
 *     profile list or an empty-state card (tap empty state to create a profile).
 *
 * Phase 2 will replace the empty-state card with real `ProfileCard`
 * composables and wire the "Start Session" flow. Phase 3 adds the active-session
 * surface with live timer.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onCreateProfile: () -> Unit,
    onOpenFullProfileEditor: () -> Unit = onCreateProfile,
    onEditProfile: (String) -> Unit = {},
) {
    val vm: HomeViewModel = hiltViewModel()
    val permissionsVm: PermissionsViewModel = hiltViewModel()
    val profiles by vm.profiles.collectAsState()
    val profilesInitialLoading by vm.profilesInitialLoading.collectAsState()
    val activeSession by vm.activeSession.collectAsState()
    val sessionCounts by vm.sessionCounts.collectAsState()
    val activityChartVisible by vm.activityChartVisible.collectAsState()
    val activityChartType by vm.activityChartType.collectAsState()
    val activityMinutesByLocalDay by vm.activityMinutesByLocalDay.collectAsState()
    val activityMinutesByProfileByLocalDay by vm.activityMinutesByProfileByLocalDay.collectAsState()
    val allGranted by permissionsVm.allRequiredGranted.collectAsState()
    val emergencyRemaining by vm.emergencyUnblocksRemaining.collectAsState()
    val emergencyResetWeeks by vm.emergencyResetPeriodWeeks.collectAsState()
    val emergencyNextResetMs by vm.emergencyNextResetEpochMs.collectAsState()

    var showManageChartSheet by remember { mutableStateOf(false) }
    var showProfilesSheet by remember { mutableStateOf(false) }
    var showEmergencySheet by remember { mutableStateOf(false) }
    var emergencyUnblockLoading by remember { mutableStateOf(false) }
    var insightsProfile by remember { mutableStateOf<Profile?>(null) }
    var nfcPendingProfileId by remember { mutableStateOf<String?>(null) }
    var nfcStopPending by remember { mutableStateOf(false) }
    var durationPickProfileId by remember { mutableStateOf<String?>(null) }

    val nfcPendingProfile = remember(nfcPendingProfileId, profiles) {
        nfcPendingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    }
    val durationPickProfile = remember(durationPickProfileId, profiles) {
        durationPickProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    }

    fun requestStartSession(profileId: String) {
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        when (profile.strategyId) {
            BlockingStrategyId.FOCUS_TIMER, BlockingStrategyId.FOCUS_TIMER_BREAK -> {
                durationPickProfileId = profileId
            }
            BlockingStrategyId.NFC_MANUAL_START -> {
                // iOS parity: lock manually in-app; NFC is only for unlock/stop — no scan to begin.
                vm.startSession(profileId)
            }
            else -> {
                // NFC_REQUIRED start (e.g. Tyme Boxed Mode): blocks only after scan when hardware exists.
                if (!permissionsVm.isNfcAvailable) vm.startSession(profileId)
                else nfcPendingProfileId = profileId
            }
        }
    }

    fun requestStopSession() {
        val session = activeSession ?: return
        val profile = profiles.firstOrNull { it.id == session.profileId } ?: return
        if (!permissionsVm.isNfcAvailable) {
            if (profile.hasTimedBreakFlow() && !session.isPauseActive) {
                vm.startTimedBreakFromApp()
            } else {
                vm.stopSession()
            }
            return
        }
        nfcStopPending = true
    }

    var selectedActivityDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(activityChartType) {
        selectedActivityDate = null
    }

    LaunchedEffect(nfcPendingProfileId, profiles) {
        val id = nfcPendingProfileId ?: return@LaunchedEffect
        if (profiles.none { it.id == id }) {
            nfcPendingProfileId = null
        }
    }
    LaunchedEffect(durationPickProfileId, profiles) {
        val id = durationPickProfileId ?: return@LaunchedEffect
        if (profiles.none { it.id == id }) {
            durationPickProfileId = null
        }
    }

    // Re-check permissions whenever Home regains focus (e.g. user came back
    // from Android Settings after tapping the banner).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsVm.refreshAfterReturningFromSettings()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cs = MaterialTheme.colorScheme
    val homeBg = cs.background

    if (showManageChartSheet) {
        ManageChartBottomSheet(
            showChart = activityChartVisible,
            onShowChartChange = vm::setActivityChartVisible,
            chartType = activityChartType,
            onChartTypeChange = vm::setActivityChartType,
            onDismiss = { showManageChartSheet = false },
        )
    }

    nfcPendingProfile?.let { profile ->
        NfcIosStyleScanSheet(
            profileName = profile.name.ifBlank { "session" },
            purpose = NfcSessionScanPurpose.Start,
            verifyNfc = { tagId -> vm.verifyNfcTagId(tagId) },
            onTagScanned = {
                vm.startSession(profile.id)
                nfcPendingProfileId = null

            },
            onDismiss = { nfcPendingProfileId = null },
        )
    }

    if (nfcStopPending) {
        val stopSession = activeSession
        val stopProfile = stopSession?.let { s ->
            profiles.firstOrNull { it.id == s.profileId }
        }
        val stopProfileName = stopProfile?.name?.takeIf { it.isNotBlank() } ?: "session"
        val stopBodyOverride = when {
            stopSession == null || stopProfile?.hasTimedBreakFlow() != true -> null
            stopSession.isPauseActive ->
                "Hold your phone near the Tyme Boxed device to end this session while on a break."
            else ->
                "Hold your phone near the Tyme Boxed device to start your break. " +
                    "The next scan while you are on a break ends the session."
        }
        NfcIosStyleScanSheet(
            profileName = stopProfileName,
            purpose = NfcSessionScanPurpose.Stop,
            verifyNfc = { tagId -> vm.verifyNfcTagId(tagId) },
            onTagScanned = {
                vm.onNfcScannedToStop()
                nfcStopPending = false
            },
            onDismiss = { nfcStopPending = false },
            bodyTextOverride = stopBodyOverride,
        )
    }

    durationPickProfile?.let { profile ->
        val timerRange = if (profile.hasTimedBreakFlow()) {
            BREAK_TIMER_SETTINGS_RANGE
        } else {
            FOCUS_TIMER_SETTINGS_RANGE
        }
        val initialMinutes = if (profile.hasTimedBreakFlow()) {
            profile.breakTimeInMinutes
        } else {
            profile.strategyData?.toIntOrNull() ?: 25
        }
        TimerSettingsBottomSheet(
            profileName = profile.name.ifBlank { stringResource(R.string.profile_region_default) },
            range = timerRange,
            initialMinutes = initialMinutes,
            onConfirm = { minutes ->
                vm.startSession(
                    profile.id,
                    selectedTimerMinutes = minutes,
                    isBreakDuration = profile.hasTimedBreakFlow(),
                )
                durationPickProfileId = null
            },
            onDismiss = { durationPickProfileId = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = homeBg,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(homeBg)
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tyme Boxed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Audit #39: 48dp minimum tap target. Visible chrome can
                    // stay 44dp by drawing the icon at a fixed inner size.
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = cs.onBackground,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                if (!allGranted) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Permissions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Some permissions are missing. Open Settings → Permissions to grant them before starting a session.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (profiles.isEmpty() && profilesInitialLoading) {
                        // Audit #34: distinguish "still loading from Room" from
                        // "user has no profiles yet". Without this the welcome
                        // card flashes for ~50 ms on cold start before the real
                        // profile list lands.
                        ProfileListLoadingPlaceholder()
                    } else if (profiles.isEmpty()) {
                        GettingStartedEmptyState(
                            onCreateProfile = onCreateProfile,
                            onOpenFullEditor = onOpenFullProfileEditor,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            ActivitySection(
                                showChart = activityChartVisible,
                                chartType = activityChartType,
                                onManage = { showManageChartSheet = true },
                                minutesByLocalDay = activityMinutesByLocalDay,
                                minutesByProfileByLocalDay = activityMinutesByProfileByLocalDay,
                                profiles = profiles,
                                selectedActivityDate = selectedActivityDate,
                                onSelectActivityDate = { d ->
                                    selectedActivityDate =
                                        if (selectedActivityDate == d) null else d
                                },
                                onViewActivityInsights = { p -> insightsProfile = p },
                            )
                            ProfileRegionHeader(
                                isSessionActive = activeSession != null,
                                onManage = { showProfilesSheet = true },
                                onEmergency = { showEmergencySheet = true },
                            )
                            ProfileList(
                                profiles = profiles,
                                activeSession = activeSession,
                                sessionCounts = sessionCounts,
                                onEditProfile = onEditProfile,
                                onInsightsProfile = { insightsProfile = it },
                                onStartSession = { requestStartSession(it) },
                                onStopSession = { requestStopSession() },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showProfilesSheet) {
            ProfilesManageFullScreen(
                profiles = profiles,
                onDismiss = { showProfilesSheet = false },
                onGuidedSetup = {
                    showProfilesSheet = false
                    onCreateProfile()
                },
                onFullProfileEditor = {
                    showProfilesSheet = false
                    onOpenFullProfileEditor()
                },
                onEditProfile = { id ->
                    showProfilesSheet = false
                    onEditProfile(id)
                },
            )
        }

        insightsProfile?.let { profile ->
            ProfileInsightsScreen(
                profile = profile,
                onDismiss = { insightsProfile = null },
            )
        }

        if (showEmergencySheet) {
            LaunchedEffect(Unit) { vm.checkAndResetEmergencyUnblocks() }
            EmergencyBottomSheet(
                remaining = emergencyRemaining,
                resetPeriodWeeks = emergencyResetWeeks,
                nextResetEpochMs = emergencyNextResetMs,
                isLoading = emergencyUnblockLoading,
                onDismiss = { showEmergencySheet = false },
                onResetPeriodWeeksChange = vm::setEmergencyResetPeriodWeeks,
                onEmergencyUnblock = {
                    emergencyUnblockLoading = true
                    vm.performEmergencyUnblock {
                        emergencyUnblockLoading = false
                        showEmergencySheet = false
                    }
                },
            )
        }
    }
}

/**
 * Audit #34: shown for the brief window between Compose mounting and Room's
 * first emission of the profile list. Without it, the welcome card flashes on
 * cold start because `profiles.isEmpty()` is true while loading too.
 *
 * Intentionally minimal — same vertical rhythm as the real list so the swap
 * doesn't visibly jank.
 */
@Composable
private fun ProfileListLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Empty home — focus messaging, benefit list, “Getting Started”, and accent CTA.
 * Accent color follows Settings → Appearance (Material [colorScheme.primary]).
 */
@Composable
private fun GettingStartedEmptyState(
    onCreateProfile: () -> Unit,
    onOpenFullEditor: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FocusDropHeroText()
        Spacer(modifier = Modifier.height(28.dp))
        FocusBenefitRow(
            icon = Icons.Outlined.Smartphone,
            title = "Focus becomes the default",
            description = "Distraction turns into the effortful choice.",
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = cs.outline.copy(alpha = 0.25f),
        )
        FocusBenefitRow(
            icon = Icons.Outlined.Schedule,
            title = "A real step before the scroll",
            description = "Your brain re-engages before the habit fires.",
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = cs.outline.copy(alpha = 0.25f),
        )
        FocusBenefitRow(
            icon = Icons.Outlined.Check,
            title = "Follow-through, automated",
            description = "2–3x more likely to stick than willpower.",
        )
        Spacer(modifier = Modifier.height(56.dp))
        Text(
            text = "Getting Started",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Let's get you started by creating your first profile. " +
                "You can customize it as much or as little as you'd like.",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onCreateProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 2.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Create Profile",
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Use the full profile editor",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = cs.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFullEditor),
        )
    }
}

@Composable
private fun FocusDropHeroText(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val serifStyle = MaterialTheme.typography.titleLarge.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        color = cs.onBackground,
        lineHeight = 30.sp,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Your focus dropped from",
            style = serifStyle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("2.5 min")
                }
                append(" to ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("47 sec.")
                }
            },
            style = serifStyle,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("One physical tap brings it ")
                withStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = cs.primary,
                    ),
                ) {
                    append("back.")
                }
            },
            style = serifStyle,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FocusBenefitRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surface)
                .border(1.dp, cs.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivitySection(
    showChart: Boolean,
    chartType: String,
    onManage: () -> Unit,
    minutesByLocalDay: Map<LocalDate, Float>,
    minutesByProfileByLocalDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    selectedActivityDate: LocalDate?,
    onSelectActivityDate: (LocalDate) -> Unit,
    onViewActivityInsights: (Profile) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurfaceVariant,
            )
            ManagePill(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                onClick = onManage,
            )
        }
        if (showChart) {
            ActivityHeatmapCard(
                chartType = chartType,
                minutesByLocalDay = minutesByLocalDay,
                minutesByProfileByLocalDay = minutesByProfileByLocalDay,
                profiles = profiles,
                selectedDate = selectedActivityDate,
                onSelectDate = onSelectActivityDate,
                onViewActivityInsights = onViewActivityInsights,
            )
        } else {
            ActivityChartHiddenPlaceholder()
        }
    }
}

@Composable
private fun ActivityChartHiddenPlaceholder() {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(shape)
            .border(1.dp, cs.outline.copy(alpha = 0.45f), shape)
            .background(cs.surface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Activity chart is hidden. Tap Manage to turn it back on.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ManageChartBottomSheet(
    showChart: Boolean,
    onShowChartChange: (Boolean) -> Unit,
    chartType: String,
    onChartTypeChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Manage chart",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = cs.onSurface,
                    )
                }
            }

            Text(
                text = "Visibility",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Show Chart",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface,
                )
                Switch(
                    checked = showChart,
                    onCheckedChange = onShowChartChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = cs.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = cs.outline,
                    ),
                )
            }

            Text(
                text = "Chart Type",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = cs.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ChartTypeOptionRow(
                        selected = chartType == ActivityChartType.FOUR_WEEK,
                        icon = Icons.Default.GridOn,
                        title = "4 Week Activity",
                        description = "View your last 28 days of focus time in a heatmap calendar.",
                        onSelect = { onChartTypeChange(ActivityChartType.FOUR_WEEK) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = cs.outlineVariant,
                    )
                    ChartTypeOptionRow(
                        selected = chartType == ActivityChartType.WEEKLY,
                        icon = Icons.Default.BarChart,
                        title = "Weekly View",
                        description = "See your week-by-week focus patterns with bar charts.",
                        onSelect = { onChartTypeChange(ActivityChartType.WEEKLY) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = cs.outlineVariant,
                    )
                    ChartTypeOptionRow(
                        selected = chartType == ActivityChartType.MONTHLY,
                        icon = Icons.Default.CalendarMonth,
                        title = "Monthly View",
                        description = "Track your monthly progress with a calendar grid.",
                        onSelect = { onChartTypeChange(ActivityChartType.MONTHLY) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChartTypeOptionRow(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    onSelect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = cs.primary,
                unselectedColor = cs.onSurfaceVariant,
            ),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 10.dp)
                .size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

private fun profileRestrictionCount(profile: Profile): Int =
    profile.blockedPackages.size + profile.domains.size

private fun formatProfileUpdatedAgo(updatedAtMs: Long, nowMs: Long): String {
    val s = ((nowMs - updatedAtMs) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "Updated ${s} sec ago"
        s < 3600 -> "Updated ${s / 60} min ago"
        s < 86400 -> "Updated ${s / 3600} hr ago"
        else -> "Updated ${s / 86400} days ago"
    }
}

@Composable
private fun ProfilesManageFullScreen(
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onGuidedSetup: () -> Unit,
    onFullProfileEditor: () -> Unit,
    onEditProfile: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val menuBg = if (isDark) Color(0xEC2C2C2E) else Color(0xF5FAFAFA)
    val menuItemColor = if (isDark) Color.White else cs.onSurface
    var headerMenuExpanded by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = cs.onSurface,
                    )
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = cs.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.22f)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Audit #39: 48dp minimum touch target.
                            IconButton(
                                onClick = { headerMenuExpanded = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = stringResource(R.string.profiles_manage_menu_more),
                                    tint = cs.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(cs.outline.copy(alpha = 0.28f)),
                            )
                            IconButton(
                                onClick = {
                                    headerMenuExpanded = false
                                    onGuidedSetup()
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add profile",
                                    tint = cs.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = headerMenuExpanded,
                        onDismissRequest = { headerMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 260.dp),
                        shape = RoundedCornerShape(18.dp),
                        containerColor = menuBg,
                        shadowElevation = 12.dp,
                        border = BorderStroke(
                            1.dp,
                            cs.outline.copy(alpha = if (isDark) 0.28f else 0.22f),
                        ),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.profiles_manage_guided_setup),
                                    color = menuItemColor,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = menuItemColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            onClick = {
                                headerMenuExpanded = false
                                onGuidedSetup()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.profiles_manage_full_editor),
                                    color = menuItemColor,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = menuItemColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            onClick = {
                                headerMenuExpanded = false
                                onFullProfileEditor()
                            },
                        )
                    }
                }
            }

            Text(
                text = "Profiles",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            )

            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(
                        text = "No profiles yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfilesManageListCard(
                            profile = profile,
                            nowMs = nowMs,
                            onClick = { onEditProfile(profile.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesManageListCard(
    profile: Profile,
    nowMs: Long,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val itemsCount = profileRestrictionCount(profile)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name.ifBlank { "Unnamed Profile" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = formatProfileUpdatedAgo(profile.updatedAt, nowMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceVariant.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "$itemsCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileRegionHeader(
    isSessionActive: Boolean,
    onManage: () -> Unit,
    onEmergency: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(
                if (isSessionActive) R.string.profile_region_active else R.string.profile_region_default,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant,
        )
        if (isSessionActive) {
            EmergencyPill(onClick = onEmergency)
        } else {
            ManagePill(
                icon = Icons.Default.Person,
                onClick = onManage,
            )
        }
    }
}

@Composable
private fun EmergencyPill(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmergencyRed,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.emergency_pill),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ManagePill(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.55f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = cs.onSurface,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Manage",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatAvgFocusMinutes(minutes: Float): String {
    val m = minutes.roundToInt().coerceAtLeast(0)
    return if (m < 1) "0m" else "${m}m"
}

private val activityDayAxisFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d")

private fun activityInsightDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private fun profileForActivityDayInsight(
    date: LocalDate,
    byProfileByDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    totalMinutes: Float,
): Profile? {
    if (profiles.isEmpty()) return null
    if (totalMinutes < 0.5f) return null
    val per = byProfileByDay[date] ?: emptyMap()
    if (per.isEmpty()) return null
    val top = per.entries.maxByOrNull { it.value } ?: return null
    return profiles.firstOrNull { it.id == top.key }
}

private fun displayNameForActivityDay(
    date: LocalDate,
    byProfileByDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    totalMinutes: Float,
): String {
    if (totalMinutes < 0.5f) return "No focus this day"
    return profileForActivityDayInsight(date, byProfileByDay, profiles, totalMinutes)
        ?.name?.takeIf { it.isNotBlank() } ?: "—"
}

@Composable
private fun ActivityHeatmapLegend(legendColors: List<Color>, legendLabels: List<String>) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            legendColors.forEachIndexed { i, c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c),
                    )
                    Text(
                        text = legendLabels[i],
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySelectedDayInsight(
    date: LocalDate,
    totalMinutes: Float,
    profileName: String,
    profile: Profile?,
    onView: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = date.format(activityInsightDateFormatter()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = profileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (profile == null) FontWeight.Normal else FontWeight.SemiBold,
                color = if (profile == null) cs.onSurfaceVariant else cs.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = formatAvgFocusMinutes(totalMinutes),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (profile != null) {
                OutlinedButton(
                    onClick = onView,
                    shape = pill,
                    border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = cs.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = cs.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityHeatmapCard(
    chartType: String,
    minutesByLocalDay: Map<LocalDate, Float>,
    minutesByProfileByLocalDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onViewActivityInsights: (Profile) -> Unit,
) {
    when (chartType) {
        ActivityChartType.WEEKLY -> ActivityWeeklyChartCard(
            minutesByLocalDay = minutesByLocalDay,
            minutesByProfileByLocalDay = minutesByProfileByLocalDay,
            profiles = profiles,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            onViewActivityInsights = onViewActivityInsights,
        )
        ActivityChartType.FOUR_WEEK -> ActivityFourWeekHeatmapCard(
            minutesByLocalDay = minutesByLocalDay,
            minutesByProfileByLocalDay = minutesByProfileByLocalDay,
            profiles = profiles,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            onViewActivityInsights = onViewActivityInsights,
        )
        ActivityChartType.MONTHLY -> ActivityMonthlyGridCard(
            minutesByLocalDay = minutesByLocalDay,
            minutesByProfileByLocalDay = minutesByProfileByLocalDay,
            profiles = profiles,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            onViewActivityInsights = onViewActivityInsights,
        )
        else -> ActivityMonthlyGridCard(
            minutesByLocalDay = minutesByLocalDay,
            minutesByProfileByLocalDay = minutesByProfileByLocalDay,
            profiles = profiles,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            onViewActivityInsights = onViewActivityInsights,
        )
    }
}

@Composable
private fun ActivityWeeklyChartCard(
    minutesByLocalDay: Map<LocalDate, Float>,
    minutesByProfileByLocalDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onViewActivityInsights: (Profile) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalAccentColor.current.value
    val isDark = isSystemInDarkTheme()
    val cardShape = RoundedCornerShape(22.dp)
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { LocalDate.now(zone) }
    val weekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
    val days = remember(weekStart) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }
    val todayIndex = remember(days, today) { days.indexOf(today).coerceIn(0, 6) }
    val minutesPerDay = remember(days, minutesByLocalDay) {
        FloatArray(7) { i -> minutesByLocalDay[days[i]] ?: 0f }
    }
    val avgMinutes = remember(minutesPerDay) { minutesPerDay.average().toFloat() }
    val maxMinutes = remember(minutesPerDay) {
        (minutesPerDay.maxOrNull() ?: 0f).coerceAtLeast(1f)
    }

    val barAccent = accent
    val barMuted = cs.surfaceVariant.copy(alpha = if (isDark) 0.55f else 0.85f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 248.dp)
            .clip(cardShape)
            .border(1.dp, cs.outline.copy(alpha = 0.45f), cardShape)
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Avg Focus Session",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = formatAvgFocusMinutes(avgMinutes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                minutesPerDay.forEachIndexed { i, mins ->
                    val frac = (mins / maxMinutes).coerceIn(0f, 1f)
                    val showMin = frac <= 0f && i == todayIndex
                    val barFrac = when {
                        frac > 0f -> frac
                        showMin -> 0.12f
                        else -> 0.04f
                    }
                    val day = days[i]
                    val selected = selectedDate == day
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, cs.primary, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectDate(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .fillMaxHeight(barFrac)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (i == todayIndex) barAccent else barMuted,
                                    ),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = activityDayAxisFormatter.format(day),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) cs.primary else cs.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 10.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                repeat(4) { row ->
                    val t = 1f - row / 3f
                    Text(
                        text = formatAvgFocusMinutes(maxMinutes * t),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        val sel = selectedDate
        if (sel != null && days.contains(sel)) {
            val total = minutesByLocalDay[sel] ?: 0f
            val name = displayNameForActivityDay(sel, minutesByProfileByLocalDay, profiles, total)
            val prof = profileForActivityDayInsight(sel, minutesByProfileByLocalDay, profiles, total)
            ActivitySelectedDayInsight(
                date = sel,
                totalMinutes = total,
                profileName = name,
                profile = prof,
                onView = { prof?.let(onViewActivityInsights) },
            )
        }
    }
}

@Composable
private fun ActivityFourWeekHeatmapCard(
    minutesByLocalDay: Map<LocalDate, Float>,
    minutesByProfileByLocalDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onViewActivityInsights: (Profile) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalAccentColor.current.value
    val cardShape = RoundedCornerShape(22.dp)
    val cellShape = RoundedCornerShape(8.dp)
    val rowGap = 6.dp
    val cellHeight = 34.dp
    val heatmapAlphas = listOf(0.3f, 0.5f, 0.7f, 0.9f)
    val legendColors = remember(accent) { heatmapAlphas.map { a -> accent.copy(alpha = a) } }
    val legendLabels = listOf("<1h", "1-3h", "3-5h", ">5h")

    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { LocalDate.now(zone) }
    val windowStart = remember(today) { today.minusDays(27) }
    val minutesPerDay = remember(windowStart, minutesByLocalDay) {
        FloatArray(28) { i -> minutesByLocalDay[windowStart.plusDays(i.toLong())] ?: 0f }
    }
    val zeroHoursFill = Color.Gray.copy(alpha = 0.15f)

    fun heatmapBucketColor(minutes: Float): Color = when {
        minutes < 60f -> accent.copy(alpha = 0.3f)
        minutes < 180f -> accent.copy(alpha = 0.5f)
        minutes < 300f -> accent.copy(alpha = 0.7f)
        else -> accent.copy(alpha = 0.9f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 248.dp)
            .clip(cardShape)
            .border(1.dp, cs.outline.copy(alpha = 0.45f), cardShape)
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActivityHeatmapLegend(legendColors = legendColors, legendLabels = legendLabels)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            repeat(4) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(7) { col ->
                        val index = row * 7 + col
                        val date = windowStart.plusDays(index.toLong())
                        val isToday = date == today
                        val mins = minutesPerDay[index]
                        val isSelected = selectedDate == date
                        val baseFill = when {
                            isSelected -> cs.primary.copy(alpha = 0.25f)
                            isToday && mins < 0.5f && !isSelected -> cs.primary.copy(alpha = 0.12f)
                            mins < 0.5f -> zeroHoursFill
                            else -> heatmapBucketColor(mins)
                        }
                        val dayText = if (isSelected) cs.primary else cs.onSurface
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight)
                                .clip(cellShape)
                                .border(2.dp, if (isSelected) cs.primary else Color.Transparent, cellShape)
                                .background(baseFill)
                                .clickable { onSelectDate(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${date.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                color = dayText,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        selectedDate?.let { d ->
            val total = minutesByLocalDay[d] ?: 0f
            val name = displayNameForActivityDay(d, minutesByProfileByLocalDay, profiles, total)
            val prof = profileForActivityDayInsight(d, minutesByProfileByLocalDay, profiles, total)
            ActivitySelectedDayInsight(
                date = d,
                totalMinutes = total,
                profileName = name,
                profile = prof,
                onView = { prof?.let(onViewActivityInsights) },
            )
        }
    }
}

@Composable
private fun ActivityMonthlyGridCard(
    minutesByLocalDay: Map<LocalDate, Float>,
    minutesByProfileByLocalDay: Map<LocalDate, Map<String, Float>>,
    profiles: List<Profile>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onViewActivityInsights: (Profile) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalAccentColor.current.value
    val cardShape = RoundedCornerShape(22.dp)
    val cellShape = RoundedCornerShape(8.dp)
    val rowGap = 6.dp
    val cellHeight = 34.dp
    val heatmapAlphas = listOf(0.3f, 0.5f, 0.7f, 0.9f)
    val legendColors = remember(accent) { heatmapAlphas.map { a -> accent.copy(alpha = a) } }
    val legendLabels = listOf("<1h", "1-3h", "3-5h", ">5h")

    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { LocalDate.now(zone) }
    val month = remember(today) { YearMonth.from(today) }
    val daysInMonth = month.lengthOfMonth()
    val first = remember(month) { month.atDay(1) }
    val leading = remember(first) { first.dayOfWeek.value % 7 }
    val totalCells = leading + daysInMonth
    val rows = (totalCells + 6) / 7

    val zeroHoursFill = Color.Gray.copy(alpha = 0.15f)

    fun heatmapBucketColor(minutes: Float): Color = when {
        minutes < 60f -> accent.copy(alpha = 0.3f)
        minutes < 180f -> accent.copy(alpha = 0.5f)
        minutes < 300f -> accent.copy(alpha = 0.7f)
        else -> accent.copy(alpha = 0.9f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 248.dp)
            .clip(cardShape)
            .border(1.dp, cs.outline.copy(alpha = 0.45f), cardShape)
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActivityHeatmapLegend(legendColors = legendColors, legendLabels = legendLabels)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            repeat(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(7) { col ->
                        val idx = row * 7 + col
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (idx in leading until leading + daysInMonth) {
                                val day = idx - leading + 1
                                val date = month.atDay(day)
                                val isToday = date == today
                                val mins = minutesByLocalDay[date] ?: 0f
                                val isSelected = selectedDate == date
                                val baseFill = when {
                                    isSelected -> cs.primary.copy(alpha = 0.25f)
                                    isToday && mins < 0.5f && !isSelected -> cs.primary.copy(alpha = 0.12f)
                                    mins < 0.5f -> zeroHoursFill
                                    else -> heatmapBucketColor(mins)
                                }
                                val dayText = if (isSelected) cs.primary else cs.onSurface
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(cellShape)
                                        .border(2.dp, if (isSelected) cs.primary else Color.Transparent, cellShape)
                                        .background(baseFill)
                                        .clickable { onSelectDate(date) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$day",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dayText,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedDate?.let { d ->
            if (d.month == month.month && d.year == month.year) {
                val total = minutesByLocalDay[d] ?: 0f
                val name = displayNameForActivityDay(d, minutesByProfileByLocalDay, profiles, total)
                val prof = profileForActivityDayInsight(d, minutesByProfileByLocalDay, profiles, total)
                ActivitySelectedDayInsight(
                    date = d,
                    totalMinutes = total,
                    profileName = name,
                    profile = prof,
                    onView = { prof?.let(onViewActivityInsights) },
                )
            }
        }
    }
}

@Composable
private fun ProfileList(
    profiles: List<Profile>,
    activeSession: Session?,
    sessionCounts: Map<String, Int>,
    onEditProfile: (String) -> Unit,
    onInsightsProfile: (Profile) -> Unit,
    onStartSession: (String) -> Unit,
    onStopSession: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        profiles.forEach { profile ->
            val isActive = activeSession?.profileId == profile.id
            val anotherSessionActive = activeSession != null && !isActive
            ProfileCard(
                profile = profile,
                sessionCount = sessionCounts[profile.id] ?: 0,
                isActive = isActive,
                anotherSessionActive = anotherSessionActive,
                activeSession = if (isActive) activeSession else null,
                onEdit = { onEditProfile(profile.id) },
                onInsights = { onInsightsProfile(profile) },
                onStart = { onStartSession(profile.id) },
                onStop = onStopSession,
            )
        }
    }
}

@Composable
private fun ProfileCardOverflowMenu(
    profileId: String,
    onEdit: () -> Unit,
    onInsights: () -> Unit,
    onStart: () -> Unit,
    isActive: Boolean,
    anotherSessionActive: Boolean,
) {
    var expanded by remember(profileId) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val menuBg = if (isDark) Color(0xEC2C2C2E) else Color(0xF5FAFAFA)
    val itemColor = if (isDark) Color.White else cs.onSurface
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.12f) else cs.outline.copy(alpha = 0.35f)

    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.dp, cs.outline.copy(alpha = 0.4f), CircleShape)
                .background(cs.surface.copy(alpha = 0.6f))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Profile options",
                tint = cs.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = menuBg,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, cs.outline.copy(alpha = if (isDark) 0.28f else 0.22f)),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Edit",
                        color = itemColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = itemColor,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = dividerColor,
                thickness = 0.5.dp,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Insights",
                        color = itemColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = itemColor,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = {
                    expanded = false
                    onInsights()
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = dividerColor,
                thickness = 0.5.dp,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Start",
                        color = itemColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = itemColor,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = {
                    expanded = false
                    if (!isActive && !anotherSessionActive) onStart()
                },
                enabled = !isActive && !anotherSessionActive,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    sessionCount: Int,
    isActive: Boolean,
    anotherSessionActive: Boolean,
    activeSession: Session?,
    onEdit: () -> Unit,
    onInsights: () -> Unit = {},
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val strategy = strategyInfoById(profile.strategyId)
    val accent = LocalAccentColor.current.value
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    var showEditBlockedDialog by remember(profile.id) { mutableStateOf(false) }
    val requestEdit: () -> Unit = {
        if (isActive) {
            showEditBlockedDialog = true
        } else {
            onEdit()
        }
    }
    val cardShape = RoundedCornerShape(24.dp)
    val strategyIcon = when (strategy?.icon) {
        "nfc" -> Icons.Default.Nfc
        "timer" -> Icons.Default.Timer
        "pause" -> Icons.Default.Pause
        else -> Icons.Default.Nfc
    }
    val strategyTitle = strategy?.name ?: "Tyme Boxed"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, cs.outline.copy(alpha = 0.45f), cardShape),
    ) {
        BoxWithConstraints(modifier = Modifier.matchParentSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cs.surface),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDark) 0.42f else 0.28f),
                                accent.copy(alpha = if (isDark) 0.12f else 0.08f),
                                Color.Transparent,
                            ),
                            center = Offset(w * 0.92f, h * 0.35f),
                            radius = w * 0.85f,
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = profile.name.ifBlank { "Unnamed Profile" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { requestEdit() },
                )
                ProfileCardOverflowMenu(
                    profileId = profile.id,
                    onEdit = requestEdit,
                    onInsights = onInsights,
                    onStart = onStart,
                    isActive = isActive,
                    anotherSessionActive = anotherSessionActive,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = strategyIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = strategyTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false),
                )
                VerticalDivider(
                    modifier = Modifier.height(28.dp),
                    color = cs.outlineVariant,
                )
                Text(
                    text = profile.schedule?.summaryText() ?: "No Schedule Set",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProfileStatColumn(
                    label = "Apps & Categories",
                    value = profile.blockedPackages.size,
                )
                ProfileStatColumn(
                    label = "Domains",
                    value = profile.domains.size,
                )
                ProfileStatColumn(
                    label = "Total Sessions",
                    value = sessionCount,
                )
            }

            if (isActive && activeSession != null) {
                ActiveSessionRow(
                    startTime = activeSession.startTime,
                    isOnBreak = activeSession.isPauseActive,
                    timedBreakFlow = profile.hasTimedBreakFlow(),
                    breakDurationMinutes = profile.breakTimeInMinutes,
                    pauseStartTime = activeSession.pauseStartTime,
                    onStop = onStop,
                )
            } else {
                HoldToStartBar(
                    enabled = !anotherSessionActive,
                    onHoldComplete = onStart,
                )
            }
        }

        if (showEditBlockedDialog) {
            AlertDialog(
                onDismissRequest = { showEditBlockedDialog = false },
                title = {
                    Text(
                        "Session running",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Text(
                        "A focus session is using this profile. End the session before you can edit it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showEditBlockedDialog = false }) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileStatColumn(
    label: String,
    value: Int,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.widthIn(min = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )
    }
}

@Composable
private fun HoldToStartBar(
    enabled: Boolean,
    onHoldComplete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (enabled) {
        cs.surfaceVariant.copy(alpha = 0.85f)
    } else {
        cs.surfaceVariant.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (enabled) {
                    Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = { onHoldComplete() },
                        onLongClickLabel = "Start blocking session",
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Hold to Start",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * Active session row: focus elapsed timer on the left (or break countdown when timed-break flow is active),
 * primary action on the right. Timed-break shows **Pause** until the first NFC scan starts the break, then **End**.
 */
@Composable
private fun ActiveSessionRow(
    startTime: Long,
    isOnBreak: Boolean = false,
    timedBreakFlow: Boolean,
    breakDurationMinutes: Int,
    pauseStartTime: Long?,
    onStop: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val isFocusWithBreak = timedBreakFlow
    val breakMins = breakDurationMinutes.coerceIn(1, 24 * 60)
    val totalSecs = when {
        isFocusWithBreak && isOnBreak -> {
            val pauseStart = pauseStartTime ?: startTime
            val breakEndMs = pauseStart + breakMins * 60_000L
            ((breakEndMs - now).coerceAtLeast(0L) / 1000L).toLong()
        }
        else -> {
            ((now - startTime).coerceAtLeast(0L) / 1000L).toLong()
        }
    }
    val hours = (totalSecs / 3600).toInt()
    val minutes = ((totalSecs % 3600) / 60).toInt()
    val seconds = (totalSecs % 60).toInt()
    val timerText = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    val chipColor = if (isOnBreak) {
        Color(0xFFFF9500)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val actionLabel = when {
        isOnBreak -> "End"
        isFocusWithBreak -> "Pause"
        else -> "Stop"
    }
    val actionIcon = when {
        isOnBreak -> Icons.Default.Stop
        isFocusWithBreak -> Icons.Default.Pause
        else -> Icons.Default.Stop
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(chipColor.copy(alpha = 0.12f))
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isFocusWithBreak && isOnBreak) {
                    Text(
                        text = "Break",
                        style = MaterialTheme.typography.labelMedium,
                        color = chipColor,
                    )
                }
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = chipColor,
                )
            }
        }

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53935),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                actionIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(actionLabel)
        }
    }
}

