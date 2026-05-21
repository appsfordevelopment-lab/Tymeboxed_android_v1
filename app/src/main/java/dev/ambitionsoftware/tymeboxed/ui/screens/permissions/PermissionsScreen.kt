package dev.ambitionsoftware.tymeboxed.ui.screens.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.ambitionsoftware.tymeboxed.MainActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.ambitionsoftware.tymeboxed.permissions.PermissionIntents
import dev.ambitionsoftware.tymeboxed.permissions.PermissionsViewModel
import dev.ambitionsoftware.tymeboxed.permissions.TymePermission
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.ui.components.PermissionRow
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCard
import dev.ambitionsoftware.tymeboxed.ui.components.SettingsCardDivider
/**
 * Standalone permissions screen — Android equivalent of iOS
 * [`PermissionsScreen`](Components/Intro/PermissionsScreen.swift).
 *
 * Reachable from Settings → "Open full permissions screen". Gives a
 * full-screen explanation of why each permission is needed so users who
 * previously denied one can re-request with more context than the card row
 * in Settings provides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
) {
    val vm: PermissionsViewModel = hiltViewModel()
    val states by vm.states.collectAsState()
    val ctx = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAfterReturningFromSettings()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_permissions_reliability_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard {
                val visiblePermissions = TymePermission.requiredPermissions
                    // Admin access is managed via the Strict Mode toggle flow, not from this list.
                    .filter { it != TymePermission.DEVICE_ADMIN }
                visiblePermissions.forEachIndexed { idx, perm ->
                    val nfcUnavailable = perm == TymePermission.NFC && !vm.isNfcAvailable
                    PermissionRow(
                        permission = perm,
                        granted = states[perm] == true,
                        onGrantClick = { openPermissionIntent(ctx, perm) },
                        unavailable = nfcUnavailable,
                    )
                    if (idx < visiblePermissions.lastIndex) {
                        SettingsCardDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun openPermissionIntent(context: Context, perm: TymePermission) {
    // POST_NOTIFICATIONS gets the runtime dialog first; only fall back to the
    // app-notifications settings page once the user has permanently denied it.
    if (perm == TymePermission.NOTIFICATIONS && tryRequestPostNotificationsRuntime(context)) return

    val res = PermissionIntents.tryStart(context, perm)
    if (!res.started) {
        val hint = "Please open system settings manually"
        val err = res.lastError?.javaClass?.simpleName ?: "Unknown"
        Toast.makeText(
            context,
            "Couldn't open ${perm.title} ($err). $hint.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/**
 * Asks the OS for the runtime POST_NOTIFICATIONS permission on Android 13+ via
 * the host activity's pre-registered launcher.
 *
 * Returns true when the dialog was triggered (or the permission is already
 * granted / cannot be re-requested) so the caller skips the settings-page
 * fallback. Returns false when there's no activity context or the OS is pre-13,
 * letting the caller fall back to [PermissionIntents.tryStart].
 */
private fun tryRequestPostNotificationsRuntime(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val activity = context as? MainActivity ?: return false
    val already = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (already) return true
    // If the user has permanently denied, shouldShowRequestPermissionRationale
    // returns false AND the permission isn't granted. Send them to settings instead.
    val canPrompt = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) || isFirstNotificationsRequest(activity)
    if (!canPrompt) return false
    activity.requestPostNotificationsPermission()
    return true
}

/**
 * Heuristic: before the user has ever responded to the dialog the system
 * returns `false` from [ActivityCompat.shouldShowRequestPermissionRationale]
 * too. We still want to show the dialog in that case, so we treat
 * "never granted + no record of a prior denial" as a first-time prompt.
 */
private fun isFirstNotificationsRequest(activity: Activity): Boolean {
    val prefs = activity.getSharedPreferences("perm_state", Context.MODE_PRIVATE)
    val asked = prefs.getBoolean(KEY_ASKED_POST_NOTIFICATIONS, false)
    if (!asked) {
        prefs.edit().putBoolean(KEY_ASKED_POST_NOTIFICATIONS, true).apply()
        return true
    }
    return false
}

private const val KEY_ASKED_POST_NOTIFICATIONS = "asked_post_notifications"
