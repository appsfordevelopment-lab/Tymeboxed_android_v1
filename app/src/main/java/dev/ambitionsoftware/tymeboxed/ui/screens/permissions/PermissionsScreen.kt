package dev.ambitionsoftware.tymeboxed.ui.screens.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.ambitionsoftware.tymeboxed.permissions.grantPermission
import dev.ambitionsoftware.tymeboxed.permissions.PermissionsViewModel
import dev.ambitionsoftware.tymeboxed.permissions.TymePermission
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.ui.components.AccessibilityDisclosureDialog
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
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

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
                        onGrantClick = {
                            if (perm == TymePermission.ACCESSIBILITY) {
                                showAccessibilityDisclosure = true
                            } else {
                                grantPermission(ctx, perm)
                            }
                        },
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

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityDisclosure = false },
            onConfirmOpenSettings = {
                showAccessibilityDisclosure = false
                grantPermission(ctx, TymePermission.ACCESSIBILITY)
            },
        )
    }
}
