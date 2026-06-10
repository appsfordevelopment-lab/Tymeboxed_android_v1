package dev.ambitionsoftware.tymeboxed.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.domain.model.ProfileSchedule
import dev.ambitionsoftware.tymeboxed.ui.screens.home.BREAK_TIMER_SETTINGS_RANGE
import dev.ambitionsoftware.tymeboxed.ui.theme.LocalAccentColor
import dev.ambitionsoftware.tymeboxed.util.WebsiteUrls
import java.text.DateFormatSymbols
import java.util.Calendar
private val calendarWeekdays = listOf(
    Calendar.SUNDAY,
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY,
    Calendar.FRIDAY,
    Calendar.SATURDAY,
)

@Composable
private fun rememberWeekdayLabels(): List<Pair<Int, String>> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val shortNames = DateFormatSymbols.getInstance(locale).shortWeekdays
        calendarWeekdays.map { day ->
            val label = shortNames.getOrNull(day)?.trim().orEmpty()
            day to label.take(2).ifEmpty { fallbackWeekdayLabel(day) }
        }
    }
}

private fun fallbackWeekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    Calendar.SUNDAY -> "Su"
    Calendar.MONDAY -> "Mo"
    Calendar.TUESDAY -> "Tu"
    Calendar.WEDNESDAY -> "We"
    Calendar.THURSDAY -> "Th"
    Calendar.FRIDAY -> "Fr"
    Calendar.SATURDAY -> "Sa"
    else -> "?"
}

private val minuteSteps = (0..55 step 5).toList()
private val hours12 = (1..12).toList()
private val breakDurationSteps = (BREAK_TIMER_SETTINGS_RANGE.minMinutes..BREAK_TIMER_SETTINGS_RANGE.maxMinutes step BREAK_TIMER_SETTINGS_RANGE.stepMinutes).toList()

/**
 * Full-screen schedule editor — mirrors iOS [SchedulePicker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePickerScreen(
    viewModel: ProfileEditViewModel,
    onBack: () -> Unit,
) {
    val accent = LocalAccentColor.current.value
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val pageBg = if (isDark) Color(0xFF121212) else cs.background
    val cardBg = if (isDark) Color(0xFF2C2C2E) else cs.surface
    val neutralCircle = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val destructive = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val linkBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val selectedDayText = if (isDark) Color(0xFF1C1C1E) else Color(0xFF2C2C2E)
    val weekdayDefs = rememberWeekdayLabels()

    val uiState by viewModel.state.collectAsState()

    var selectedDays by remember { mutableStateOf(emptySet<Int>()) }
    var startHour12 by remember { mutableIntStateOf(9) }
    var startPm by remember { mutableStateOf(false) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour12 by remember { mutableIntStateOf(5) }
    var endPm by remember { mutableStateOf(true) }
    var endMinute by remember { mutableIntStateOf(0) }

    var showStartPickers by remember { mutableStateOf(false) }
    var showEndPickers by remember { mutableStateOf(false) }
    var breakMenuExpanded by remember { mutableStateOf(false) }

    val breakDurationEnabled = uiState.strategyId == BlockingStrategyId.FOCUS_TIMER_BREAK

    LaunchedEffect(uiState.profileReady) {
        if (!uiState.profileReady) return@LaunchedEffect
        val s = uiState.schedule
        selectedDays = s.days.toSet()
        startHour12 = from24To12(s.startHour).first
        startPm = from24To12(s.startHour).second
        startMinute = roundToFive(s.startMinute)
        endHour12 = from24To12(s.endHour).first
        endPm = from24To12(s.endHour).second
        endMinute = roundToFive(s.endMinute)
        showStartPickers = false
        showEndPickers = false
    }

    LaunchedEffect(breakDurationEnabled) {
        if (!breakDurationEnabled) breakMenuExpanded = false
    }

    val start24 = hour12To24(startHour12, startPm)
    val end24 = hour12To24(endHour12, endPm)
    val durationMinutes = durationMinutesBetween(start24, startMinute, end24, endMinute)
    val breakMinutes = BREAK_TIMER_SETTINGS_RANGE.snap(uiState.breakTimeInMinutes.toFloat())
    val hasDays = selectedDays.isNotEmpty()
    val isValid = hasDays && durationMinutes >= 60
    val validationText = when {
        isValid -> null
        !hasDays -> null
        else -> "Schedule must be at least 1 hour long."
    }

    fun applyAndDone() {
        val daysSorted = selectedDays.sorted()
        val sched = ProfileSchedule(
            days = daysSorted,
            startHour = start24,
            startMinute = startMinute,
            endHour = end24,
            endMinute = endMinute,
            updatedAt = System.currentTimeMillis(),
        )
        viewModel.updateSchedule(sched)
        onBack()
    }

    val footerAnnotated = remember(linkBlue, cs.onSurfaceVariant) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = cs.onSurfaceVariant)) {
                append("If you're looking for more granularity, you can use Shortcuts.\n")
            }
            pushLink(
                LinkAnnotation.Url(
                    WebsiteUrls.home,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkBlue,
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                ),
            )
            append("Tymeboxed.app")
            pop()
        }
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    ScheduleCircleIconButton(
                        circleBackground = neutralCircle,
                        onClick = onBack,
                        contentDescription = "Cancel",
                        imageVector = Icons.Filled.Close,
                    )
                },
                actions = {
                    ScheduleCircleIconButton(
                        circleBackground = neutralCircle,
                        onClick = { applyAndDone() },
                        enabled = isValid,
                        contentDescription = "Save",
                        imageVector = Icons.Filled.Check,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = pageBg,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface,
                    actionIconContentColor = cs.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weekdayDefs.forEach { (dow, label) ->
                    val sel = dow in selectedDays
                    val fg = if (sel) selectedDayText else cs.onSurface
                    val borderC = if (sel) accent else cs.onSurface.copy(alpha = 0.35f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(1.dp, borderC, CircleShape)
                            .background(if (sel) accent else Color.Transparent, CircleShape)
                            .clickable {
                                selectedDays = if (sel) selectedDays - dow else selectedDays + dow
                                if (selectedDays.isEmpty()) {
                                    showStartPickers = false
                                    showEndPickers = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Schedules take 15 minutes to update",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            ScheduleSectionLabel("Start Time")
            ScheduleValueRow(
                cardBg = cardBg,
                leftLabel = "When to start",
                rightValue = format12hLabel(startHour12, startMinute, startPm),
                enabled = hasDays,
                onClick = { showStartPickers = !showStartPickers },
            )
            if (showStartPickers && hasDays) {
                ScheduleTimePickerCard(cardBg = cardBg) {
                    DropdownTimeSelectors(
                        hour12 = startHour12,
                        onHour = { startHour12 = it },
                        minute = startMinute,
                        onMinute = { startMinute = it },
                        isPm = startPm,
                        onPm = { startPm = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ScheduleSectionLabel("End Time")
            ScheduleValueRow(
                cardBg = cardBg,
                leftLabel = "When to end",
                rightValue = format12hLabel(endHour12, endMinute, endPm),
                enabled = hasDays,
                onClick = { showEndPickers = !showEndPickers },
            )
            if (showEndPickers && hasDays) {
                ScheduleTimePickerCard(cardBg = cardBg) {
                    DropdownTimeSelectors(
                        hour12 = endHour12,
                        onHour = { endHour12 = it },
                        minute = endMinute,
                        onMinute = { endMinute = it },
                        isPm = endPm,
                        onPm = { endPm = it },
                    )
                }
            }
            validationText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            ScheduleSectionLabel("Default Break duration")
            ScheduleValueRow(
                cardBg = cardBg,
                leftLabel = "Break length",
                rightValue = formatMinutesLabel(breakMinutes),
                enabled = breakDurationEnabled,
                onClick = { if (breakDurationEnabled) breakMenuExpanded = true },
                dropdownExpanded = breakMenuExpanded,
                onDismissDropdown = { breakMenuExpanded = false },
                dropdownContent = {
                    breakDurationSteps.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(formatMinutesLabel(m)) },
                            onClick = {
                                viewModel.onBreakTimeChange(m)
                                breakMenuExpanded = false
                            },
                        )
                    }
                },
            )
            if (!breakDurationEnabled) {
                Text(
                    text = "Only available with Forever session with break.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .clickable {
                        selectedDays = emptySet()
                        startHour12 = 9
                        startPm = false
                        startMinute = 0
                        endHour12 = 5
                        endPm = true
                        endMinute = 0
                        showStartPickers = false
                        showEndPickers = false
                        breakMenuExpanded = false
                        viewModel.updateSchedule(ProfileSchedule.inactive())
                        onBack()
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Remove Schedule",
                    style = MaterialTheme.typography.bodyLarge,
                    color = destructive,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = footerAnnotated,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun ScheduleSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ScheduleValueRow(
    cardBg: Color,
    leftLabel: String,
    rightValue: String,
    enabled: Boolean,
    onClick: () -> Unit,
    dropdownExpanded: Boolean = false,
    onDismissDropdown: () -> Unit = {},
    dropdownContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val labelColor = if (enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.38f)
    val valueColor = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = leftLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
        if (dropdownContent != null) {
            Box {
                Text(
                    text = rightValue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                )
                DropdownMenu(
                    expanded = dropdownExpanded && enabled,
                    onDismissRequest = onDismissDropdown,
                    offset = DpOffset(x = (-80).dp, y = 0.dp),
                ) {
                    dropdownContent()
                }
            }
        } else {
            Text(
                text = rightValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun ScheduleTimePickerCard(
    cardBg: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun ScheduleCircleIconButton(
    circleBackground: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String,
    imageVector: ImageVector,
) {
    val cs = MaterialTheme.colorScheme
    IconButton(onClick = onClick, enabled = enabled) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(circleBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = cs.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DropdownTimeSelectors(
    hour12: Int,
    onHour: (Int) -> Unit,
    minute: Int,
    onMinute: (Int) -> Unit,
    isPm: Boolean,
    onPm: (Boolean) -> Unit,
) {
    var hourExpanded by remember { mutableStateOf(false) }
    var minuteExpanded by remember { mutableStateOf(false) }
    var merExpanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = { hourExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Hour: $hour12", color = cs.primary)
            }
            DropdownMenu(
                expanded = hourExpanded,
                onDismissRequest = { hourExpanded = false },
            ) {
                hours12.forEach { h ->
                    DropdownMenuItem(
                        text = { Text(h.toString()) },
                        onClick = {
                            onHour(h)
                            hourExpanded = false
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = { minuteExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Min: %02d".format(minute), color = cs.primary)
            }
            DropdownMenu(
                expanded = minuteExpanded,
                onDismissRequest = { minuteExpanded = false },
            ) {
                minuteSteps.forEach { m ->
                    DropdownMenuItem(
                        text = { Text("%02d".format(m)) },
                        onClick = {
                            onMinute(m)
                            minuteExpanded = false
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = { merExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isPm) "PM" else "AM", color = cs.primary)
            }
            DropdownMenu(
                expanded = merExpanded,
                onDismissRequest = { merExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("AM") },
                    onClick = {
                        onPm(false)
                        merExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("PM") },
                    onClick = {
                        onPm(true)
                        merExpanded = false
                    },
                )
            }
        }
    }
}

private fun from24To12(hour24: Int): Pair<Int, Boolean> {
    val isPm = hour24 >= 12
    var h = hour24 % 12
    if (h == 0) h = 12
    return h to isPm
}

private fun hour12To24(hour12: Int, isPm: Boolean): Int {
    if (hour12 == 12) return if (isPm) 12 else 0
    return if (isPm) hour12 + 12 else hour12
}

private fun roundToFive(value: Int): Int {
    val rem = value % 5
    val down = value - rem
    val up = (value + (5 - rem)).coerceAtMost(55)
    if (rem == 0) return value
    return if (value - down < up - value) down else up
}

private fun format12hLabel(h12: Int, minute: Int, pm: Boolean): String =
    "$h12:${"%02d".format(minute)} ${if (pm) "PM" else "AM"}"

private fun formatMinutesLabel(minutes: Int): String = "$minutes minutes"

private fun durationMinutesBetween(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
): Int {
    val startTotal = startHour * 60 + startMinute
    val endTotal = endHour * 60 + endMinute
    return if (endTotal <= startTotal) {
        (24 * 60 - startTotal) + endTotal
    } else {
        endTotal - startTotal
    }
}
