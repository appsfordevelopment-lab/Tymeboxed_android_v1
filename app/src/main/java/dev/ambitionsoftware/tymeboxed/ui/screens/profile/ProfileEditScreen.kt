package dev.ambitionsoftware.tymeboxed.ui.screens.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId
import dev.ambitionsoftware.tymeboxed.domain.model.ProfileSchedule
import dev.ambitionsoftware.tymeboxed.domain.model.StrategyInfo
import dev.ambitionsoftware.tymeboxed.domain.model.availableStrategies
import dev.ambitionsoftware.tymeboxed.ui.components.CustomToggle
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCard
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCardDivider
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCardRow
import dev.ambitionsoftware.tymeboxed.ui.screens.insights.ProfileInsightsScreen

@Composable
fun ProfileEditScreen(
    profileId: String,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onOpenBlockedApps: () -> Unit = {},
    onOpenBlockedDomains: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
) {
    val vm: ProfileEditViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }
    LaunchedEffect(state.deletedSuccessfully) {
        if (state.deletedSuccessfully) onBack()
    }

    val pendingNavigationProfileId by vm.pendingNavigationProfileId.collectAsState()
    LaunchedEffect(pendingNavigationProfileId) {
        val id = pendingNavigationProfileId ?: return@LaunchedEffect
        onNavigateToProfile(id)
        vm.consumePendingNavigation()
    }

    var showInsights by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showOverflowDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileIosIconButton(
                        onClick = onBack,
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!state.isNew && state.profileReady) {
                        ProfileEditTopActionPill(
                            onInsights = { showInsights = true },
                            onDuplicate = { vm.duplicateProfile() },
                            onViewSessions = { showSessions = true },
                            onDeleteFromMenu = { showOverflowDeleteConfirm = true },
                            deleteEnabled = !state.isActiveSessionForThisProfile,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    ProfileIosIconButton(
                        onClick = { vm.save() },
                        enabled = !state.isSaving,
                        icon = Icons.Default.Check,
                        contentDescription = if (state.isNew) "Create profile" else "Save profile",
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Error banner
                if (state.errorMessage != null) {
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (state.isActiveSessionForThisProfile) {
                    SettingsCard {
                        Text(
                            text = "A focus session is using this profile. You can still save changes. " +
                                "End the session on the home screen before you can delete this profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                Text(
                    text = if (state.isNew) "Create New Profile" else "Profile Details",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                )

                // 1. Name
                NameSection(
                    name = state.name,
                    onNameChange = vm::onNameChange,
                )

                // 2. Blocked apps
                AppPickerSection(
                    selectedPackages = state.blockedPackages,
                    installedApps = state.installedApps,
                    onOpenAppPicker = onOpenBlockedApps,
                )

                // 3. Blocked domains
                DomainSection(
                    isAllowModeDomains = state.isAllowModeDomains,
                    onAllowModeDomainsChange = vm::onAllowModeDomainsChange,
                    blockAdultWebsites = state.blockAdultWebsites,
                    onBlockAdultWebsitesChange = vm::onBlockAdultWebsitesChange,
                    domains = state.domains,
                    onOpenDomainPicker = onOpenBlockedDomains,
                )

                // 4. Schedule (iOS BlockedProfileScheduleSelector + SchedulePicker)
                ScheduleSection(
                    schedule = state.schedule,
                    disabled = state.isActiveSessionForThisProfile,
                    onOpenSchedule = onOpenSchedule,
                )

                // 5. Blocking strategy
                StrategySection(
                    selectedId = state.strategyId,
                    onSelect = vm::onStrategyChange,
                )

                // 6. Notifications
                NotificationsSection(
                    enableLiveActivity = state.enableLiveActivity,
                    onLiveActivityChange = vm::onLiveActivityChange,
                    enableReminder = state.enableReminder,
                    onReminderChange = vm::onReminderChange,
                    reminderTimeMinutes = state.reminderTimeMinutes,
                    onReminderTimeChange = vm::onReminderTimeChange,
                    customReminderMessage = state.customReminderMessage,
                    onReminderMessageChange = vm::onReminderMessageChange,
                    onOpenNotificationSettings = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra("app_package", context.packageName)
                            putExtra("app_uid", context.applicationInfo.uid)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        if (showInsights) {
            vm.profileForInsights()?.let { p ->
                ProfileInsightsScreen(
                    profile = p,
                    onDismiss = { showInsights = false },
                )
            }
        }

        if (showSessions && !state.isNew) {
            ProfileSessionsScreen(
                profileId = profileId,
                profileName = state.name,
                onDismiss = { showSessions = false },
            )
        }

        if (showOverflowDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showOverflowDeleteConfirm = false },
                title = { Text("Delete Profile") },
                text = {
                    Text(
                        "Are you sure you want to delete this profile? " +
                            "It will be removed from this device. This cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showOverflowDeleteConfirm = false
                        vm.delete()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOverflowDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

    }
}

@Composable
private fun ProfileEditTopActionPill(
    onInsights: () -> Unit,
    onDuplicate: () -> Unit,
    onViewSessions: () -> Unit,
    onDeleteFromMenu: () -> Unit,
    deleteEnabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val menuBg = if (isDark) Color(0xEC2C2C2E) else Color(0xF5FAFAFA)
    val itemColor = if (isDark) Color.White else cs.onSurface
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.12f) else cs.outline.copy(alpha = 0.35f)
    val errorColor = MaterialTheme.colorScheme.error
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surface,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.22f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Audit #39: icon buttons must hit the 48dp Material accessibility
                // minimum, even when the visible chrome is smaller. Set the touch
                // surface to 48dp and use a smaller icon (22dp) for the visual.
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More options",
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
                    onClick = onInsights,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Insights",
                        tint = cs.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 260.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = menuBg,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, cs.outline.copy(alpha = if (isDark) 0.28f else 0.22f)),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.profile_edit_menu_duplicate),
                        color = itemColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.profile_edit_menu_duplicate),
                        tint = itemColor,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.profile_edit_menu_view_sessions),
                        color = itemColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = stringResource(R.string.profile_edit_menu_view_sessions),
                        tint = itemColor,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onViewSessions()
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
                        stringResource(R.string.profile_edit_menu_delete),
                        color = if (deleteEnabled) errorColor else errorColor.copy(alpha = 0.38f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.profile_edit_menu_delete),
                        tint = if (deleteEnabled) errorColor else errorColor.copy(alpha = 0.38f),
                        modifier = Modifier.size(22.dp),
                    )
                },
                enabled = deleteEnabled,
                onClick = {
                    menuExpanded = false
                    onDeleteFromMenu()
                },
            )
        }
    }
}

@Composable
private fun ProfileIosIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

// ─── Name ────────────────────────────────────────────────────────────────────

@Composable
private fun NameSection(
    name: String,
    onNameChange: (String) -> Unit,
) {
    SettingsCard(title = stringResource(R.string.profile_edit_name_section_title), elevation = 0.dp) {
        SettingsCardRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.profile_edit_name_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

// ─── Strategy ────────────────────────────────────────────────────────────────

private fun strategyIcon(iconHint: String): ImageVector = when (iconHint) {
    "nfc" -> Icons.Default.Nfc
    "timer" -> Icons.Default.Timer
    "pause" -> Icons.Default.Pause
    "touch_app" -> Icons.Default.TouchApp
    else -> Icons.Default.Nfc
}

@Composable
private fun StrategySection(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val visible = availableStrategies.filter { !it.hidden }

    SettingsCard(title = stringResource(R.string.profile_edit_strategy_section_title), elevation = 0.dp) {
        visible.forEachIndexed { index, strategy ->
            StrategyRow(
                strategy = strategy,
                isSelected = selectedId == strategy.id,
                onClick = { onSelect(strategy.id) },
            )
            if (index < visible.lastIndex) SettingsCardDivider()
        }
    }
}

@Composable
private fun StrategyRow(
    strategy: StrategyInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val pillBg = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(pillBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = strategyIcon(strategy.icon),
                contentDescription = stringResource(
                    R.string.cd_strategy_icon_fmt,
                    strategy.name,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = strategy.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = strategy.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
            if (strategy.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    strategy.tags.forEach { tag ->
                        Text(
                            text = tag.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(pillBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

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
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─── App picker ──────────────────────────────────────────────────────────────

@Composable
private fun AppPickerSection(
    selectedPackages: Set<String>,
    installedApps: List<InstalledApp>,
    onOpenAppPicker: () -> Unit,
) {
    val selectedCount = selectedPackages.size

    SettingsCard(title = stringResource(R.string.profile_edit_apps_section_title), elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAppPicker() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_edit_select_apps),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCardDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (selectedCount == 0) {
                Text(
                    text = stringResource(R.string.profile_edit_no_apps_selected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "$selectedCount app${if (selectedCount != 1) "s" else ""} selected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val preview = installedApps
                    .filter { selectedPackages.contains(it.packageName) }
                    .take(3)
                    .joinToString(", ") { it.label }
                val suffix = if (selectedCount > 3) " +${selectedCount - 3} more" else ""
                Text(
                    text = preview + suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ─── Domains ─────────────────────────────────────────────────────────────────

@Composable
private fun DomainSection(
    isAllowModeDomains: Boolean,
    onAllowModeDomainsChange: (Boolean) -> Unit,
    blockAdultWebsites: Boolean,
    onBlockAdultWebsitesChange: (Boolean) -> Unit,
    domains: List<String>,
    onOpenDomainPicker: () -> Unit,
) {
    SettingsCard(title = stringResource(R.string.profile_edit_websites_section_title), elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDomainPicker() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_edit_select_domains),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCardDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (domains.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_edit_no_domains_selected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${domains.size} domain${if (domains.size != 1) "s" else ""} selected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = domains.take(4).joinToString(", ") +
                        if (domains.size > 4) " +${domains.size - 4} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SettingsCardDivider()

        SettingsCardRow {
            CustomToggle(
                title = stringResource(R.string.profile_edit_domain_allow_mode_title),
                description = stringResource(R.string.profile_edit_domain_allow_mode_desc),
                checked = isAllowModeDomains,
                onCheckedChange = onAllowModeDomainsChange,
            )
        }

        SettingsCardDivider()

        SettingsCardRow {
            CustomToggle(
                title = stringResource(R.string.profile_edit_block_adult_websites_title),
                description = stringResource(R.string.profile_edit_block_adult_websites_desc),
                checked = blockAdultWebsites,
                onCheckedChange = onBlockAdultWebsitesChange,
            )
        }
    }
}

// ─── Schedule (iOS BlockedProfileScheduleSelector) ───────────────────────────

@Composable
private fun ScheduleSection(
    schedule: ProfileSchedule,
    disabled: Boolean,
    onOpenSchedule: () -> Unit,
) {
    SettingsCard(title = stringResource(R.string.profile_edit_schedule_section_title), elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !disabled, onClick = onOpenSchedule)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_edit_set_schedule),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (disabled) {
            Text(
                text = "End the focus session to edit the schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        SettingsCardDivider()
        Text(
            text = schedule.summaryText(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

private val reminderTimePresetMinutes = listOf(5, 10, 15, 30, 45, 60, 120)

// ─── Notifications ───────────────────────────────────────────────────────────

@Composable
private fun NotificationsSection(
    enableLiveActivity: Boolean,
    onLiveActivityChange: (Boolean) -> Unit,
    enableReminder: Boolean,
    onReminderChange: (Boolean) -> Unit,
    reminderTimeMinutes: Int,
    onReminderTimeChange: (Int) -> Unit,
    customReminderMessage: String,
    onReminderMessageChange: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val globalSettingsLinkColor = if (isDark) Color(0xFFC5A377) else cs.primary

    SettingsCard(title = stringResource(R.string.profile_edit_notification_section_title), elevation = 0.dp) {
        SettingsCardRow {
            CustomToggle(
                title = stringResource(R.string.profile_edit_lock_screen_widget_title),
                description = stringResource(R.string.profile_edit_lock_screen_widget_desc),
                checked = enableLiveActivity,
                onCheckedChange = onLiveActivityChange,
            )
        }

        SettingsCardDivider()

        SettingsCardRow {
            CustomToggle(
                title = stringResource(R.string.profile_edit_restart_reminder_title),
                description = stringResource(R.string.profile_edit_restart_reminder_desc),
                checked = enableReminder,
                onCheckedChange = onReminderChange,
            )
        }

        if (enableReminder) {
            SettingsCardDivider()

            SettingsCardRow {
                val menuBg = if (isDark) Color(0xEC2C2C2E) else Color(0xF5FAFAFA)
                val menuItemColor = if (isDark) Color.White else cs.onSurface
                var reminderTimeMenuExpanded by remember { mutableStateOf(false) }
                val reminderMinuteOptions = remember(reminderTimeMinutes) {
                    (reminderTimePresetMinutes + reminderTimeMinutes).distinct().sorted()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Reminder time",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                    )
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { reminderTimeMenuExpanded = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "${reminderTimeMinutes} minutes",
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant,
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(start = 2.dp),
                                verticalArrangement = Arrangement.spacedBy((-8).dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Choose reminder time",
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = reminderTimeMenuExpanded,
                            onDismissRequest = { reminderTimeMenuExpanded = false },
                            modifier = Modifier.widthIn(min = 232.dp),
                            shape = RoundedCornerShape(18.dp),
                            containerColor = menuBg,
                            shadowElevation = 12.dp,
                            border = BorderStroke(
                                1.dp,
                                cs.outline.copy(alpha = if (isDark) 0.28f else 0.22f),
                            ),
                        ) {
                            reminderMinuteOptions.forEach { minutes ->
                                val selected = reminderTimeMinutes == minutes
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "$minutes minutes",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = menuItemColor,
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = cs.primary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onReminderTimeChange(minutes)
                                        reminderTimeMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            SettingsCardDivider()

            SettingsCardRow {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Reminder message",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                    )
                    BasicTextField(
                        value = customReminderMessage,
                        onValueChange = onReminderMessageChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = cs.onSurfaceVariant,
                        ),
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (customReminderMessage.isEmpty()) {
                                        Text(
                                            text = "Time to focus! Start your session.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = cs.onSurfaceVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                    innerTextField()
                                }
                                Text(
                                    text = "${customReminderMessage.length}/178",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                )
                            }
                        },
                    )
                }
            }
        }

        SettingsCardDivider()

        Text(
            text = "Go to settings to disable globally",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = globalSettingsLinkColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenNotificationSettings)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
