package dev.ambitionsoftware.tymeboxed.ui.screens.profile.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.domain.model.StrategyInfo
import dev.ambitionsoftware.tymeboxed.domain.model.availableStrategies
import dev.ambitionsoftware.tymeboxed.domain.model.strategyInfoById
import dev.ambitionsoftware.tymeboxed.ui.components.CustomToggle
import dev.ambitionsoftware.tymeboxed.ui.screens.profile.ProfileEditViewModel
import androidx.compose.material.icons.filled.Check
import kotlin.math.roundToInt

private const val TOTAL_STEPS = 9

private enum class OnboardStep(val index: Int) {
    NAME(0),
    STRATEGY(1),
    APPS(2),
    DOMAINS(3),
    FOCUS_DURATION(4),
    SCHEDULE(5),
    BREAKS(6),
    NOTIFICATIONS(7),
    REVIEW(8),
}

@Composable
fun ProfileOnboardingScreen(
    onClose: () -> Unit,
    onComplete: () -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenBlockedDomains: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenFullEditor: () -> Unit,
) {
    val vm: ProfileEditViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (stepIndex >= TOTAL_STEPS) {
            stepIndex = OnboardStep.REVIEW.index
        }
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onComplete()
    }

    val step = OnboardStep.entries.firstOrNull { it.index == stepIndex } ?: OnboardStep.NAME
    val visibleSteps = remember(state.strategyId) { visibleOnboardSteps(state.strategyId) }
    val displayStep = remember(stepIndex, visibleSteps) {
        val pos = visibleSteps.indexOfFirst { it.index == stepIndex }
        if (pos >= 0) pos + 1 else visibleSteps.size.coerceAtLeast(1)
    }
    val displayTotalSteps = visibleSteps.size

    LaunchedEffect(state.strategyId, stepIndex) {
        if (shouldSkipStep(stepIndex, state.strategyId)) {
            stepIndex = visibleOnboardSteps(state.strategyId)
                .lastOrNull { it.index < stepIndex }
                ?.index
                ?: visibleOnboardSteps(state.strategyId).first().index
        }
    }

    fun advance() {
        var next = stepIndex + 1
        while (next < TOTAL_STEPS && shouldSkipStep(next, state.strategyId)) {
            next++
        }
        if (next >= TOTAL_STEPS) {
            stepIndex = OnboardStep.REVIEW.index
        } else {
            stepIndex = next
        }
    }

    fun goBack() {
        var prev = stepIndex - 1
        while (prev >= 0 && shouldSkipStep(prev, state.strategyId)) {
            prev--
        }
        if (prev < 0) onClose() else stepIndex = prev
    }

    val nextEnabled = when (step) {
        OnboardStep.NAME -> state.name.isNotBlank()
        OnboardStep.REVIEW -> !state.isSaving
        else -> true
    }

    val nextLabel = when (step) {
        OnboardStep.REVIEW -> "Create Profile"
        else -> "Next"
    }

    val onNext: () -> Unit = when (step) {
        OnboardStep.REVIEW -> ({ vm.save() })
        else -> ({ advance() })
    }

    ProfileOnboardingScaffold(
        displayStep = displayStep,
        displayTotalSteps = displayTotalSteps,
        title = stepTitle(step),
        subtitle = stepSubtitle(step),
        onBack = if (visibleSteps.firstOrNull()?.index != stepIndex) ({ goBack() }) else null,
        onClose = onClose,
        nextLabel = nextLabel,
        nextEnabled = nextEnabled && !state.isSaving,
        onNext = onNext,
    ) {
        when (step) {
            OnboardStep.NAME -> NameStepContent(
                name = state.name,
                onNameChange = vm::onNameChange,
            )
            OnboardStep.STRATEGY -> StrategyStepContent(
                selectedId = state.strategyId,
                onSelect = vm::onStrategyChange,
            )
            OnboardStep.APPS -> AppsStepContent(
                selectedCount = state.blockedPackages.size,
                onOpenApps = onOpenBlockedApps,
            )
            OnboardStep.DOMAINS -> DomainsStepContent(
                domainCount = state.domains.size,
                isAllowModeDomains = state.isAllowModeDomains,
                blockAdultWebsites = state.blockAdultWebsites,
                onAllowModeChange = vm::onAllowModeDomainsChange,
                onBlockAdultWebsitesChange = vm::onBlockAdultWebsitesChange,
                onOpenDomains = onOpenBlockedDomains,
            )
            OnboardStep.FOCUS_DURATION -> FocusDurationStepContent(
                minutes = state.timerMinutes,
                onMinutesChange = vm::onTimerMinutesChange,
                strategyId = state.strategyId,
            )
            OnboardStep.SCHEDULE -> ScheduleStepContent(
                summary = state.schedule.summaryText(),
                onOpenSchedule = onOpenSchedule,
            )
            OnboardStep.BREAKS -> BreaksStepContent(
                minutes = state.breakTimeInMinutes,
                onMinutesChange = vm::onBreakTimeChange,
            )
            OnboardStep.NOTIFICATIONS -> NotificationsStepContent(
                enableLiveActivity = state.enableLiveActivity,
                onLiveActivityChange = vm::onLiveActivityChange,
                enableReminder = state.enableReminder,
                onReminderChange = vm::onReminderChange,
                reminderTimeMinutes = state.reminderTimeMinutes,
                onReminderTimeChange = vm::onReminderTimeChange,
                customReminderMessage = state.customReminderMessage,
                onReminderMessageChange = vm::onReminderMessageChange,
            )
            OnboardStep.REVIEW -> ReviewStepContent(
                state = state,
                errorMessage = state.errorMessage,
                onOpenFullEditor = onOpenFullEditor,
            )
        }
    }
}

private fun visibleOnboardSteps(strategyId: String): List<OnboardStep> =
    OnboardStep.entries.filter { !shouldSkipStep(it.index, strategyId) }

private fun shouldSkipStep(index: Int, strategyId: String): Boolean = when (OnboardStep.entries.firstOrNull { it.index == index }) {
    OnboardStep.FOCUS_DURATION -> strategyId !in timerStrategies
    OnboardStep.BREAKS -> strategyId != BlockingStrategyId.FOCUS_TIMER_BREAK
    else -> false
}

private val timerStrategies = setOf(
    BlockingStrategyId.FOCUS_TIMER,
    BlockingStrategyId.FOCUS_TIMER_BREAK,
)

private fun stepTitle(step: OnboardStep): String = when (step) {
    OnboardStep.NAME -> "Name this profile"
    OnboardStep.STRATEGY -> "Choose how it starts and stops"
    OnboardStep.APPS -> "Choose apps and how to block them"
    OnboardStep.DOMAINS -> "Choose domains and how to block them"
    OnboardStep.FOCUS_DURATION -> "Set focus duration"
    OnboardStep.SCHEDULE -> "Add a schedule"
    OnboardStep.BREAKS -> "Set break duration"
    OnboardStep.NOTIFICATIONS -> "Set notifications"
    OnboardStep.REVIEW -> "Review your profile"
}

private fun stepSubtitle(step: OnboardStep): String = when (step) {
    OnboardStep.NAME ->
        "Profiles group the apps, websites, schedules, and rules you want to use together."
    OnboardStep.STRATEGY ->
        "Pick the blocking method that fits this profile. You can mix how you start and how you stop."
    OnboardStep.APPS ->
        "Select the apps this profile should restrict during focus sessions."
    OnboardStep.DOMAINS ->
        "Add specific domains and decide whether website blocking applies in the browser."
    OnboardStep.FOCUS_DURATION ->
        "Choose how long this timed focus session should run before it ends automatically."
    OnboardStep.SCHEDULE ->
        "Schedules can start this profile automatically on selected days."
    OnboardStep.BREAKS ->
        "Breaks let you pause blocking briefly without ending the whole session."
    OnboardStep.NOTIFICATIONS ->
        "Lock screen updates and reminders can help you manage your session."
    OnboardStep.REVIEW ->
        "Create the profile now, or go back to adjust any section."
}

@Composable
private fun NameStepContent(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(cs.surface)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { inner ->
                Box {
                    if (name.isEmpty()) {
                        Text(
                            text = "Profile Name",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun StrategyStepContent(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val visible = availableStrategies.filter { !it.hidden }
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
    ) {
        OnboardingSettingsCard {
            Text(
                text = "Set Strategy",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            )
            OnboardingCardDivider()
            visible.forEachIndexed { index, strategy ->
                OnboardingStrategyRow(
                    strategy = strategy,
                    isSelected = selectedId == strategy.id,
                    onClick = { onSelect(strategy.id) },
                )
                if (index < visible.lastIndex) {
                    OnboardingCardDivider()
                }
            }
        }
    }
}

@Composable
private fun OnboardingStrategyRow(
    strategy: StrategyInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    val pillBg = cs.surfaceVariant
    val outline = cs.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(pillBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = strategyIcon(strategy.icon),
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = strategy.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accent else cs.onSurface,
            )
            Text(
                text = strategy.description,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            if (strategy.tags.isNotEmpty()) {
                Text(
                    text = strategy.tags.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(pillBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 0.dp else 2.dp,
                    color = if (isSelected) Color.Transparent else outline,
                    shape = CircleShape,
                )
                .background(if (isSelected) accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AppsStepContent(
    selectedCount: Int,
    onOpenApps: () -> Unit,
) {
    val subtitle = when (selectedCount) {
        0 -> "No apps selected"
        1 -> "1 item selected"
        else -> "$selectedCount items selected"
    }
    OnboardingSettingsCard {
        OnboardingNavRow(
            title = "Select Apps to Restrict",
            subtitle = subtitle,
            onClick = onOpenApps,
            showDividerBelow = false,
        )
    }
}

@Composable
private fun DomainsStepContent(
    domainCount: Int,
    isAllowModeDomains: Boolean,
    blockAdultWebsites: Boolean,
    onAllowModeChange: (Boolean) -> Unit,
    onBlockAdultWebsitesChange: (Boolean) -> Unit,
    onOpenDomains: () -> Unit,
) {
    val domainSubtitle = when (domainCount) {
        0 -> "No domains selected"
        1 -> "1 domain selected"
        else -> "$domainCount domains selected"
    }
    OnboardingSettingsCard {
        OnboardingNavRow(
            title = "Select Domains to Restrict",
            subtitle = domainSubtitle,
            onClick = onOpenDomains,
            showDividerBelow = true,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            CustomToggle(
                title = "Domain Allow Mode",
                description = "Pick domains to allow and block everything else. This clears other domain selections.",
                checked = isAllowModeDomains,
                onCheckedChange = onAllowModeChange,
            )
        }
        OnboardingCardDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            CustomToggle(
                title = "Block Adult Websites",
                description = "Blocks common adult sites in the browser during sessions. You can still add extra domains to block.",
                checked = blockAdultWebsites,
                onCheckedChange = onBlockAdultWebsitesChange,
            )
        }
    }
}

@Composable
private fun FocusDurationStepContent(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    strategyId: String,
) {
    val cs = MaterialTheme.colorScheme
    val label = if (strategyId == BlockingStrategyId.FOCUS_TIMER_BREAK) {
        "Session length"
    } else {
        "Focus duration"
    }
    OnboardingSettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$minutes minutes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onMinutesChange(it.roundToInt()) },
                valueRange = 5f..180f,
                steps = 34,
                colors = SliderDefaults.colors(
                    thumbColor = cs.primary,
                    activeTrackColor = cs.primary,
                ),
            )
        }
    }
}

@Composable
private fun ScheduleStepContent(
    summary: String,
    onOpenSchedule: () -> Unit,
) {
    OnboardingSettingsCard {
        OnboardingNavRow(
            title = "Set schedule",
            subtitle = summary,
            onClick = onOpenSchedule,
            showDividerBelow = false,
        )
    }
}

@Composable
private fun BreaksStepContent(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    OnboardingSettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$minutes minutes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Text(
                text = "Break length",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onMinutesChange(it.roundToInt()) },
                valueRange = 5f..60f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = cs.primary,
                    activeTrackColor = cs.primary,
                ),
            )
        }
    }
}

@Composable
private fun NotificationsStepContent(
    enableLiveActivity: Boolean,
    onLiveActivityChange: (Boolean) -> Unit,
    enableReminder: Boolean,
    onReminderChange: (Boolean) -> Unit,
    reminderTimeMinutes: Int,
    onReminderTimeChange: (Int) -> Unit,
    customReminderMessage: String,
    onReminderMessageChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
    ) {
        OnboardingSettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    CustomToggle(
                        title = "Lock screen widget",
                        description = "Shows session status on your lock screen with a focus quote.",
                        checked = enableLiveActivity,
                        onCheckedChange = onLiveActivityChange,
                    )
                }
                OnboardingCardDivider()
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    CustomToggle(
                        title = "Reminder",
                        description = "Sends a reminder to start this profile when a session ends.",
                        checked = enableReminder,
                        onCheckedChange = onReminderChange,
                    )
                }
                if (enableReminder) {
                    OnboardingCardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Reminder time",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                        )
                        Text(
                            text = "$reminderTimeMinutes minutes",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    OnboardingCardDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "Reminder message",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = customReminderMessage,
                            onValueChange = onReminderMessageChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = cs.onSurfaceVariant,
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (customReminderMessage.isEmpty()) {
                                        Text(
                                            text = "Get back to productivity",
                                            color = cs.onSurfaceVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                OnboardingCardDivider()
                Text(
                    text = "Go to settings to disable globally",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = cs.primary,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewStepContent(
    state: dev.ambitionsoftware.tymeboxed.ui.screens.profile.ProfileEditUiState,
    errorMessage: String?,
    onOpenFullEditor: () -> Unit,
) {
    val strategyName = strategyInfoById(state.strategyId)?.name ?: state.strategyId
    val appsLabel = when (val n = state.blockedPackages.size) {
        0 -> "No apps selected"
        1 -> "1 item"
        else -> "$n items"
    }
    val domainsLabel = when {
        state.domains.isEmpty() && !state.blockAdultWebsites -> "No domains selected"
        state.domains.isEmpty() && state.blockAdultWebsites -> "Adult sites blocked"
        state.blockAdultWebsites -> {
            val n = state.domains.size
            "$n domain${if (n != 1) "s" else ""}; Adult sites blocked"
        }
        state.domains.size == 1 -> "1 domain"
        else -> "${state.domains.size} domains"
    }
    val breaksLabel = if (state.strategyId == BlockingStrategyId.FOCUS_TIMER_BREAK) {
        "${state.breakTimeInMinutes} minutes"
    } else {
        "Off"
    }
    val safeguardsLabel = if (state.enableStrictMode) "Strict" else "Standard"
    val notifParts = buildList {
        if (state.enableLiveActivity) add("Lock screen")
        if (state.enableReminder) add("Reminder")
    }
    val notificationsLabel = notifParts.joinToString(", ").ifBlank { "None" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OnboardingSettingsCard {
            OnboardingReviewRow("Name", state.name.ifBlank { "—" })
            OnboardingReviewRow("Strategy", strategyName)
            OnboardingReviewRow("Apps", appsLabel)
            OnboardingReviewRow("Domains", domainsLabel)
            OnboardingReviewRow("Schedule", state.schedule.summaryText())
            OnboardingReviewRow("Breaks", breaksLabel)
            OnboardingReviewRow("Safeguards", safeguardsLabel)
            OnboardingReviewRow("Notifications", notificationsLabel, showDivider = false)
        }
        Text(
            text = "Use the full profile editor",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFullEditor)
                .padding(vertical = 8.dp),
        )
    }
}

private fun strategyIcon(iconHint: String): ImageVector = when (iconHint) {
    "nfc" -> Icons.Default.Nfc
    "timer" -> Icons.Default.Timer
    "pause" -> Icons.Default.Pause
    "touch_app" -> Icons.Default.TouchApp
    else -> Icons.Default.Nfc
}
