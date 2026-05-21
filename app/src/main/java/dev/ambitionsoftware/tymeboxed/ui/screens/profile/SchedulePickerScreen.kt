package dev.ambitionsoftware.tymeboxed.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.ambitionsoftware.tymeboxed.domain.model.ProfileSchedule
import dev.ambitionsoftware.tymeboxed.ui.theme.LocalAccentColor
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

/** Help / Shortcuts video — update when a dedicated URL exists. */
private const val ShortcutsHelpUrl = "https://www.tymeboxed.app/"

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
    val neutralCircle = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val destructive = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val linkBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
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

    // Profile data loads asynchronously; seeding only from the first frame leaves [selectedDays]
    // empty forever so Save stays disabled. Sync once the editor state is ready.
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

    val start24 = hour12To24(startHour12, startPm)
    val end24 = hour12To24(endHour12, endPm)
    val startTotal = start24 * 60 + startMinute
    val endTotal = end24 * 60 + endMinute
    val durationMinutes = if (endTotal <= startTotal) {
        (24 * 60 - startTotal) + endTotal
    } else {
        endTotal - startTotal
    }
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

    val helpAnnotated = remember(linkBlue, cs.onSurfaceVariant) {
        buildAnnotatedString {
            withStyle(
                SpanStyle(color = cs.onSurfaceVariant),
            ) {
                append("If you're looking for more granularity, you can use Shortcuts. ")
            }
            pushLink(
                LinkAnnotation.Url(
                    ShortcutsHelpUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkBlue,
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                ),
            )
            append("Here is a quick video")
            pop()
            withStyle(SpanStyle(color = cs.onSurfaceVariant)) {
                append(".")
            }
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
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.surface)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = cs.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 2.dp),
                        tint = cs.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Choose when this profile starts and ends. To end early, use the strategy you set up earlier. The schedule must be at least 1 hour long.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Days",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weekdayDefs.forEach { (dow, label) ->
                    val sel = dow in selectedDays
                    val fg = if (sel) Color.White else cs.onSurface
                    val borderC = if (sel) accent else cs.outlineVariant
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

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Start Time",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
                    .clickable(enabled = hasDays) {
                        showStartPickers = !showStartPickers
                        if (showStartPickers) showEndPickers = false
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "When to start",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    format12hLabel(startHour12, startMinute, startPm),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = if (isDark) 0.55f else 0.45f),
                )
            }
            if (showStartPickers && hasDays) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surface)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
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
            Text(
                text = "End Time",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
                    .clickable(enabled = hasDays) {
                        showEndPickers = !showEndPickers
                        if (showEndPickers) showStartPickers = false
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "When to end",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    format12hLabel(endHour12, endMinute, endPm),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = if (isDark) 0.55f else 0.45f),
                )
            }
            if (showEndPickers && hasDays) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surface)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
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

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
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

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = helpAnnotated,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
            )
        }
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
