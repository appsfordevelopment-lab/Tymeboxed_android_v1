package dev.ambitionsoftware.tymeboxed.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import dev.ambitionsoftware.tymeboxed.di.PermissionsEntryPoint
import dev.ambitionsoftware.tymeboxed.service.ActiveBlockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Device-admin receiver — the Android counterpart to "owner mode" on iOS.
 *
 * What this actually gives us:
 *
 *  - **Uninstall friction** (this is the headline Brick-style behaviour): when an admin
 *    is active, Settings / Package Installer shows *"Can't uninstall active device admin app"*
 *    and forces the user to deactivate the admin first. We deepen that step in
 *    [onDisableRequested] with a strict-mode warning so the user has to read a real consequence
 *    before they can proceed.
 *  - **Hook for `setUninstallBlocked`** if the install is ever provisioned as Device Owner
 *    (ADB / managed-device setup). Most consumer installs are NOT Device Owner so the call
 *    silently fails — that's expected. See [StrictModeProtection.tryHardenUninstallBlock].
 *
 * What this does NOT give us:
 *  - A true hardware-backed "uninstall blocked" guarantee (only Device Owner / Profile Owner has
 *    that on stock Android, and third-party apps can't be granted it from inside the app — it
 *    requires `dpm set-device-owner` via ADB or factory provisioning).
 *  - Protection in Safe Mode (the user can boot to safe mode, remove admin, then uninstall).
 *  - Protection if the user has ADB enabled (`adb uninstall <pkg>` ignores device admin).
 *
 * Everything beyond the OS-level uninstall dialog is enforced by
 * [dev.ambitionsoftware.tymeboxed.service.StrictUninstallInterception]
 * (accessibility-driven detection + redirect).
 */
class TymeBoxedDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled — uninstall now requires admin deactivation.")
        refreshPermissionsCoordinator(context)
        // Best-effort: if we somehow got Device Owner status (ADB provisioning),
        // also wire setUninstallBlocked so the OS hard-rejects uninstall attempts
        // instead of just showing the friction dialog.
        StrictModeProtection.tryHardenUninstallBlock(context, blocked = true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin disabled — uninstall friction removed; clearing strict-mode pref.")
        refreshPermissionsCoordinator(context)
        StrictModeProtection.tryHardenUninstallBlock(context, blocked = false)
        // Critical: prevent drift between the strict-mode pref and the real
        // OS state. If the user disables admin from system Settings while
        // strict-mode is still "on" in our pref, the next session would
        // start with strictModeEnabled=true but NO active admin → the OS
        // would never fire the "Can't uninstall active device admin app"
        // toast, even though our UI says we're protected.
        // Wiping the pref keeps the UI honest and forces the user to walk
        // through the activation flow again before the next strict session.
        receiverScope.launch {
            runCatching {
                EntryPointAccessors
                    .fromApplication(context.applicationContext, PermissionsEntryPoint::class.java)
                    .appPreferences()
                    .setStrictModeEnabled(false)
            }
        }
    }

    /**
     * Brick-style guard: when the user attempts to deactivate the admin while a strict-mode
     * session is running, we return a CharSequence which Android shows as the body of the
     * "Deactivate device admin app?" system dialog. This is the only Android API that lets a
     * third-party app inject copy into that screen — there is no way to outright block it
     * (the OS reserves that for Device Owner / Profile Owner).
     *
     * On Android 14+ this method's return value also goes through Settings' new "Are you sure?"
     * confirmation, so we craft the copy to discourage casual taps.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val snap = ActiveBlockingState.current
        val strictActive = snap.isBlocking && snap.strictModeEnabled
        Log.i(
            TAG,
            "Device admin disable requested. strictActive=$strictActive isBlocking=${snap.isBlocking}",
        )
        return if (strictActive) {
            "A Tyme Boxed Strict-mode focus session is currently active.\n\n" +
                "If you deactivate admin access:\n" +
                "  • You'll lose strict uninstall protection.\n" +
                "  • The focus session continues, but the app can be removed.\n" +
                "  • You might be tempted to break your commitment.\n\n" +
                "End the session inside Tyme Boxed first if you really need to remove admin."
        } else {
            "Tyme Boxed uses admin access only to make uninstall harder during Strict-mode " +
                "sessions. Removing this won't affect any data, but you'll lose that " +
                "protection on your next session."
        }
    }

    /**
     * Permissions coordinator caches the admin-active state; refresh it so the UI doesn't
     * show a stale "Not enabled" / "Enabled" badge after activation/deactivation completes.
     */
    private fun refreshPermissionsCoordinator(context: Context) {
        runCatching {
            EntryPointAccessors
                .fromApplication(context.applicationContext, PermissionsEntryPoint::class.java)
                .permissionsCoordinator()
                .refresh()
        }
    }

    companion object {
        private const val TAG = "TymeBoxedDeviceAdmin"

        /**
         * Receiver-scoped CoroutineScope so [onDisabled]'s pref clear can complete even after
         * the broadcast returns. The receiver is recreated on every dispatch, but [Companion]
         * is process-scoped so the scope lives as long as the app process — enough.
         */
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Helper for tests / diagnostics. Returns true only when this app is Device Owner
         * (very rare for consumer installs — usually requires `adb shell dpm set-device-owner`).
         */
        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
        }
    }
}
