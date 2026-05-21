package dev.ambitionsoftware.tymeboxed.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.admin.DeviceAdminSupport
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.service.AppBlockerAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for whether each [TymePermission] is currently granted.
 *
 * This is the Android-side analogue of iOS `RequestAuthorizer` (which only has
 * to track Family Controls). Because Android has several distinct checks
 * with different APIs, all of them are centralized here and exposed as a
 * single [StateFlow] that the intro wizard, the Settings > Permissions card,
 * and [PermissionsViewModel] (home banner / session gate) subscribe to.
 *
 * Call [refresh] on app foreground (ON_RESUME) — Android system settings pages
 * don't notify us when a permission changes, so we recompute whenever the user
 * comes back from granting one. Checks run on a background dispatcher so binder
 * IPC (AccessibilityManager, Settings.Secure, AppOps) never blocks the UI thread.
 */
@Singleton
class PermissionsCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private val _states: MutableStateFlow<Map<TymePermission, Boolean>>
    private val _allRequiredGranted: MutableStateFlow<Boolean>
    private val _strictModeEnabled: MutableStateFlow<Boolean>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    init {
        val placeholder = TymePermission.entries.associateWith { false }
        _strictModeEnabled = MutableStateFlow(false)
        _states = MutableStateFlow(placeholder)
        _allRequiredGranted = MutableStateFlow(allRequiredFrom(placeholder, strictEnabled = false))

        scope.launch {
            refreshMutex.withLock { performRefreshLocked() }
        }

        // Device Admin should only be treated as "required" when Strict mode is ON.
        // Keep a local strict flag so we can compute [allRequiredGranted] without blocking.
        scope.launch {
            appPreferences.strictModeEnabled
                .distinctUntilChanged()
                .collect { strictEnabled ->
                    _strictModeEnabled.value = strictEnabled
                    _allRequiredGranted.value = allRequiredFrom(_states.value, strictEnabled)
                }
        }
    }

    /** Map of permission -> granted flag. Updates only when [refresh] is called. */
    val states: StateFlow<Map<TymePermission, Boolean>> = _states.asStateFlow()

    /**
     * Same boolean as [allRequiredFrom] for [states] value — updated in the same [refresh] call
     * so UI never sees a mismatched pair (avoids false "missing permission" flashes).
     */
    val allRequiredGranted: StateFlow<Boolean> = _allRequiredGranted.asStateFlow()

    /**
     * Recomputes all permission flags asynchronously. Safe to call from the main thread
     * (e.g. [android.app.Activity.onResume]); binder work runs on [Dispatchers.IO].
     */
    fun refresh() {
        scope.launch {
            refreshMutex.withLock { performRefreshLocked() }
        }
    }

    private suspend fun performRefreshLocked() {
        val map = computeAll()
        val strictEnabled = _strictModeEnabled.value
        _states.value = map
        _allRequiredGranted.value = allRequiredFrom(map, strictEnabled)

        // Keep the strict-mode pref in lock-step with the admin's real status.
        // If admin is no longer active, the OS will never fire its
        // "Can't uninstall active device admin app" toast, so leaving the pref
        // ON would make our UI claim protection that doesn't exist.
        if (strictEnabled && map[TymePermission.DEVICE_ADMIN] != true) {
            runCatching { appPreferences.setStrictModeEnabled(false) }
        }
    }

    private fun allRequiredFrom(map: Map<TymePermission, Boolean>, strictEnabled: Boolean): Boolean {
        // DEVICE_ADMIN is only required for Strict mode sessions.
        val required = if (strictEnabled) {
            TymePermission.requiredPermissions
        } else {
            TymePermission.requiredPermissions.filter { it != TymePermission.DEVICE_ADMIN }
        }
        return required.all { map[it] == true }
    }

    /** Last value from [refresh]; does not perform synchronous system checks. */
    fun isGranted(perm: TymePermission): Boolean = _states.value[perm] == true

    private fun computeAll(): Map<TymePermission, Boolean> =
        TymePermission.entries.associateWith { computeOne(it) }

    private fun computeOne(perm: TymePermission): Boolean = when (perm) {
        TymePermission.ACCESSIBILITY -> isAccessibilityServiceEnabled()
        TymePermission.USAGE_STATS -> isUsageStatsGranted()
        TymePermission.NOTIFICATIONS -> isNotificationsGranted()
        TymePermission.NFC -> isNfcRequirementSatisfied()
        TymePermission.EXACT_ALARMS -> isExactAlarmsGranted()
        TymePermission.DEVICE_ADMIN -> DeviceAdminSupport.isAdminActive(context)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (isAccessibilityEnabledViaAccessibilityManager()) return true
        return isAccessibilityEnabledViaSettingsSecure()
    }

    /**
     * Matches what the system UI shows under Accessibility — more reliable than parsing
     * [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] alone (some OEMs format strings oddly).
     */
    private fun isAccessibilityEnabledViaAccessibilityManager(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val cn = ComponentName(context, AppBlockerAccessibilityService::class.java)
        val expectedFlat = cn.flattenToString()
        val simpleName = AppBlockerAccessibilityService::class.java.simpleName
        val list = try {
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        } catch (_: Throwable) {
            return false
        }
        return list.any { info ->
            val id = info.id?.trim().orEmpty()
            if (id.isNotEmpty() && id.equals(expectedFlat, ignoreCase = true)) return@any true
            runCatching {
                val parsed = ComponentName.unflattenFromString(id)
                parsed != null &&
                    parsed.packageName == cn.packageName &&
                    (
                        parsed.className == cn.className ||
                            parsed.className.endsWith(".$simpleName") ||
                            parsed.className.endsWith(simpleName)
                        )
            }.getOrDefault(false) ||
                (
                    id.contains(cn.packageName, ignoreCase = true) &&
                        id.contains(simpleName, ignoreCase = true)
                    )
        }
    }

    private fun isAccessibilityEnabledViaSettingsSecure(): Boolean {
        val cn = ComponentName(context, AppBlockerAccessibilityService::class.java)
        val expectedFlat = cn.flattenToString()
        val simpleName = AppBlockerAccessibilityService::class.java.simpleName
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        if (enabled.isBlank()) return false
        val entries = enabled.split(':', ',')
        return entries.any { raw ->
            val entry = raw.trim()
            if (entry.isEmpty()) return@any false
            if (entry.equals(expectedFlat, ignoreCase = true)) return@any true
            val parsed = ComponentName.unflattenFromString(entry)
            if (parsed != null) {
                parsed.packageName == cn.packageName && (
                    parsed.className == cn.className ||
                        parsed.className.endsWith(".$simpleName") ||
                        parsed.className.endsWith(simpleName)
                    )
            } else {
                entry.contains(cn.packageName, ignoreCase = true) &&
                    entry.contains(simpleName, ignoreCase = true)
            }
        }
    }

    private fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        // Only MODE_ALLOWED means the user turned on “App usage access” for this app.
        // Do not use [UsageStatsManager.queryUsageStats] as a probe: it often returns an
        // empty list without throwing when access is denied, which incorrectly looked “granted”.
        val granted = mode == AppOpsManager.MODE_ALLOWED
        if (!granted && Log.isLoggable("PermCoordinator", Log.DEBUG)) {
            Log.d(
                "PermCoordinator",
                "USAGE_STATS mode=$mode (need MODE_ALLOWED=${AppOpsManager.MODE_ALLOWED})",
            )
        }
        return granted
    }

    private fun isNotificationsGranted(): Boolean {
        val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val post = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            // Treat as granted if either path is satisfied (fixes OEMs where one lags the other).
            return notificationsOn || post
        }
        return notificationsOn
    }

    /** True when the device has NFC hardware. False on emulators without NFC. */
    val isNfcHardwareAvailable: Boolean
        get() = NfcAdapter.getDefaultAdapter(context) != null

    /**
     * NFC is required when the device has hardware (must be on). No hardware ⇒ satisfied so
     * onboarding can complete on NFC-less devices.
     */
    private fun isNfcRequirementSatisfied(): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return true
        return adapter.isEnabled
    }

    private fun isExactAlarmsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }
}
