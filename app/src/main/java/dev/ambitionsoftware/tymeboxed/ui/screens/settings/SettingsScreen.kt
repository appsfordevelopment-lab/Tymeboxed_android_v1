package dev.ambitionsoftware.tymeboxed.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.app.admin.DevicePolicyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.ambitionsoftware.tymeboxed.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ambitionsoftware.tymeboxed.data.db.dao.TagDao
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.data.repository.ProfileRepository
import dev.ambitionsoftware.tymeboxed.data.repository.SessionRepository
import dev.ambitionsoftware.tymeboxed.service.ActiveBlockingState
import dev.ambitionsoftware.tymeboxed.service.SessionBlockerService
import dev.ambitionsoftware.tymeboxed.permissions.PermissionsViewModel
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCard
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCardDivider
import androidx.compose.ui.res.stringResource
import dev.ambitionsoftware.tymeboxed.R
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCardDivider
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsNavigationRow
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsToggleRowGrouped
import dev.ambitionsoftware.tymeboxed.ui.screens.inapp.InAppBlockingSettingsRowGrouped
import dev.ambitionsoftware.tymeboxed.ui.theme.AccentColor
import dev.ambitionsoftware.tymeboxed.ui.theme.AccentColors
import dev.ambitionsoftware.tymeboxed.ui.theme.ThemeController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.ambitionsoftware.tymeboxed.admin.DeviceAdminSupport

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val themeController: ThemeController,
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val tagDao: TagDao,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private companion object {
        const val IN_APP_BLOCKING_PREFS = "tymeboxed_inapp_toggles"
    }

    val accent: StateFlow<AccentColor> = themeController.accent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AccentColors.default,
    )

    /**
     * The Settings toggle is bound to the *effective* strict-mode state — the conjunction
     * of the user pref and the OS device-admin being active. Without this, the toggle
     * would happily display "ON" while the user can in fact uninstall the app (because
     * they disabled admin in system Settings → Device admin apps and the OS-generated
     * "Can't uninstall active device admin app" toast would never fire).
     */
    val strictModeEnabled: StateFlow<Boolean> = appPreferences.effectiveStrictModeEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    val isSessionActive: StateFlow<Boolean> = sessionRepository.activeSession
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActiveBlockingState.current.isBlocking,
        )

    fun selectAccent(accent: AccentColor) {
        viewModelScope.launch { themeController.select(accent) }
    }

    fun resetBlockingState() {
        viewModelScope.launch {
            ActiveBlockingState.deactivate()
            sessionRepository.resetActive()
            appContext.startService(SessionBlockerService.stopIntent(appContext))
        }
    }

    fun setStrictModeEnabled(enabled: Boolean) {
        if (!enabled && ActiveBlockingState.current.isBlocking) return
        viewModelScope.launch { appPreferences.setStrictModeEnabled(enabled) }
    }

    fun deleteAllData(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                ActiveBlockingState.deactivate()
                sessionRepository.resetActive()
                appContext.startService(SessionBlockerService.stopIntent(appContext))
                sessionRepository.deleteAll()
                profileRepository.deleteAll()
                tagDao.deleteAll()
                appPreferences.clearVerifiedNfcTagCache()
                appContext.getSharedPreferences(IN_APP_BLOCKING_PREFS, Context.MODE_PRIVATE)
                    .edit { clear() }
                appPreferences.setIntroCompleted(false)
                withContext(Dispatchers.Main) { onSuccess() }
            }
        }
    }
}

/**
 * Settings screen — mirrors iOS [`SettingsView`] card layout:
 *   1. About card — version + blocking status + "Made in Hyderabad India".
 *   2. Theme (Appearance) card — accent swatch + dropdown of all 15 colours.
 *   3. Safeguards card (Strict + in-app blocking); Permissions &amp; reliability row.
 *   4. Danger card — delete all data. Full permissions + troubleshooting live on [PermissionsScreen].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFullPermissions: () -> Unit,
    onOpenInAppBlocking: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    val settingsVm: SettingsViewModel = hiltViewModel()
    val permissionsVm: PermissionsViewModel = hiltViewModel()
    val accent by settingsVm.accent.collectAsState()
    val strictModeEnabled by settingsVm.strictModeEnabled.collectAsState()
    val isSessionActive by settingsVm.isSessionActive.collectAsState()
    val allRequiredGranted by permissionsVm.allRequiredGranted.collectAsState()
    var pendingStrictEnable by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    // Refresh permission states on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // PermissionsCoordinator.refresh() now auto-clears the strict-mode pref when
                // admin is no longer active, so we don't need a separate reconciliation here.
                permissionsVm.refreshAfterReturningFromSettings()

                val adminActive = DeviceAdminSupport.isAdminActive(ctx)
                if (pendingStrictEnable) {
                    pendingStrictEnable = false
                    if (adminActive && !isSessionActive) {
                        settingsVm.setStrictModeEnabled(true)
                        Toast.makeText(
                            ctx,
                            "Strict mode is on. Uninstall is now blocked during focus sessions.",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(
                            ctx,
                            "Admin access is required to enable Strict mode.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeviceAdminPrompt by remember { mutableStateOf(false) }
    var showStrictDisableConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsCloseButton(onClick = onBack)
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
            )

            AboutCard(
                accessAuthorized = allRequiredGranted,
                onBuyDevice = {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://www.tymeboxed.app/".toUri(),
                            ),
                        )
                    }
                },
            )

            ThemeCard(
                currentAccent = accent,
                onSelect = settingsVm::selectAccent,
            )

            SettingsCard(title = "Safeguards", elevation = 0.dp) {
                SettingsToggleRowGrouped(
                    title = stringResource(R.string.strict_mode_title),
                    subtitle = if (isSessionActive) {
                        stringResource(R.string.strict_mode_settings_subtitle_session_active)
                    } else {
                        stringResource(R.string.strict_mode_settings_subtitle)
                    },
                    icon = Icons.Filled.Lock,
                    checked = strictModeEnabled,
                    enabled = !isSessionActive,
                    onCheckedChange = { enabled ->
                        if (isSessionActive) return@SettingsToggleRowGrouped
                        val adminActive = DeviceAdminSupport.isAdminActive(ctx)
                        if (enabled) {
                            if (adminActive) {
                                settingsVm.setStrictModeEnabled(true)
                            } else {
                                showDeviceAdminPrompt = true
                            }
                        } else {
                            showStrictDisableConfirm = true
                        }
                    },
                )
                SettingsCardDivider()
                InAppBlockingSettingsRowGrouped(onClick = onOpenInAppBlocking)
            }

            SettingsNavigationRow(
                title = stringResource(R.string.settings_permissions_reliability_title),
                subtitle = stringResource(R.string.settings_permissions_reliability_subtitle),
                icon = Icons.Filled.Shield,
                onClick = onOpenFullPermissions,
            )

            DangerCard(
                onDeleteAccountClick = { showDeleteAccountDialog = true },
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showDeviceAdminPrompt) {
        AlertDialog(
            onDismissRequest = { showDeviceAdminPrompt = false },
            title = { Text(stringResource(R.string.strict_mode_admin_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.strict_mode_admin_dialog_body))
                    Text(stringResource(R.string.strict_mode_admin_dialog_footnote))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeviceAdminPrompt = false
                    pendingStrictEnable = true
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            DeviceAdminSupport.adminComponent(ctx),
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            ctx.getString(R.string.strict_mode_admin_explanation),
                        )
                    }
                    runCatching { ctx.startActivity(intent) }
                }) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceAdminPrompt = false }) {
                    Text("Not now")
                }
            },
        )
    }

    if (showStrictDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showStrictDisableConfirm = false },
            title = { Text(stringResource(R.string.strict_mode_disable_dialog_title)) },
            text = {
                Text(stringResource(R.string.strict_mode_disable_dialog_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    showStrictDisableConfirm = false
                    settingsVm.setStrictModeEnabled(false)

                    val adminActive = DeviceAdminSupport.isAdminActive(ctx)
                    if (adminActive) {
                        val removed = DeviceAdminSupport.removeAdmin(ctx)
                        if (!removed) {
                            Toast.makeText(
                                ctx,
                                "Couldn't remove Admin access automatically. You can disable it in Device admin apps.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }) {
                    Text(stringResource(R.string.strict_mode_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStrictDisableConfirm = false }) {
                    Text(stringResource(R.string.strict_mode_disable_keep_on))
                }
            },
        )
    }

    if (showDeleteAccountDialog) {
        val cs = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(
                    text = "Delete Account?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all your modes, schedules, and activity. " +
                        "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        settingsVm.deleteAllData(onSuccess = onAccountDeleted)
                    },
                ) {
                    Text("Delete", color = cs.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsCloseButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(cs.surfaceVariant),
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close settings",
            tint = cs.onSurface,
        )
    }
}

@Composable
private fun ThemeCard(
    currentAccent: AccentColor,
    onSelect: (AccentColor) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    SettingsCard(title = "Theme", elevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Text(
                        text = "Customize how the app looks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            SettingsCardDivider()
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { menuOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Theme Color",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = currentAccent.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    AccentColors.all.forEach { accentOption ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(accentOption.value),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(accentOption.name)
                                }
                            },
                            onClick = {
                                onSelect(accentOption)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    accessAuthorized: Boolean,
    onBuyDevice: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val versionLabel = "v${BuildConfig.VERSION_NAME}"
    SettingsCard(title = "About", elevation = 0.dp) {
        LabelRow(label = "Version", value = versionLabel)
        SettingsCardDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Permission",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (accessAuthorized) Color(0xFF34C759) else Color(0xFFFF9500),
                        ),
                )
                Text(
                    text = if (accessAuthorized) "Authorized" else "Action needed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        SettingsCardDivider()
        LabelRow(label = "Made in", value = "Hyderabad India 🇮🇳")
        SettingsCardDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBuyDevice)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Buy a Tyme Boxed device",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LabelRow(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DangerCard(onDeleteAccountClick: () -> Unit) {
    SettingsCard(title = null, elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDeleteAccountClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Delete Account",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

