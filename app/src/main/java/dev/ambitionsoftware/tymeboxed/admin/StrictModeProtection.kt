package dev.ambitionsoftware.tymeboxed.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/**
 * Central helper for everything strict-mode "Brick-style" uninstall protection touches.
 *
 * Layers, from strongest to weakest:
 *
 *  1. **Device Owner `setUninstallBlocked`** — hard OS-level block. Requires `dpm set-device-owner`
 *     via ADB or factory provisioning. We *try* it here so installs that happen to be Device Owner
 *     (e.g. you wired this app into a kiosk or MDM setup) get the strongest possible guarantee.
 *     For 99% of consumer installs this no-ops.
 *  2. **Device Admin friction dialog** — present any time the admin is active. Provided by the OS
 *     when [TymeBoxedDeviceAdminReceiver] is registered with [android.app.admin.DeviceAdminReceiver].
 *  3. **Accessibility-driven interception** — [dev.ambitionsoftware.tymeboxed.service.StrictUninstallInterception]
 *     watches Settings / installers / launchers and pushes the user back to home + the blocker overlay.
 *  4. **OEM auto-start + battery exemption** — keeps our accessibility service alive so #3 keeps
 *     working. See [dev.ambitionsoftware.tymeboxed.oem.OemAutostartIntents] and
 *     [dev.ambitionsoftware.tymeboxed.oem.BatteryOptimizationHelper].
 *
 * Why uninstall protection still "fails" on some devices:
 *  - The user disables the accessibility service from Settings (no app can prevent this — the
 *    accessibility toggle is privileged), then uninstalls.
 *  - The user boots into safe mode (third-party apps aren't loaded — admins don't apply).
 *  - The user uses `adb uninstall` over USB (ignores admin policy for non-Device-Owner installs).
 *  - The user resets battery / accessibility on OEM ROMs that auto-kill background apps (MIUI,
 *    HyperOS, ColorOS, OriginOS) so the accessibility tick stops and #3 can't fire.
 *  - On Android 14+ Settings adds a confirmation screen to deactivate admin and our copy still
 *    can't outright veto the user's choice.
 *
 * In other words: **without Device Owner, uninstall protection is a deterrent, not a barrier.**
 * The combination of all four layers above is what Brick uses too.
 */
object StrictModeProtection {

    private const val TAG = "StrictModeProtection"

    /**
     * Wires [DevicePolicyManager.setUninstallBlocked] when the app is Device Owner.
     * Silently no-ops when not Device Owner (the common case).
     *
     * Called from [TymeBoxedDeviceAdminReceiver.onEnabled] when admin is activated, and from
     * the session lifecycle so we can toggle the block in lock-step with strict mode.
     */
    fun tryHardenUninstallBlock(context: Context, blocked: Boolean) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val pkg = context.packageName
        val admin = DeviceAdminSupport.adminComponent(context)

        // setUninstallBlocked requires either Device Owner or Profile Owner. A regular
        // device-admin activation isn't enough; the call throws SecurityException otherwise.
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false)
        val isProfileOwner = runCatching { dpm.isProfileOwnerApp(pkg) }.getOrDefault(false)
        if (!isDeviceOwner && !isProfileOwner) {
            Log.d(TAG, "Not Device/Profile Owner — setUninstallBlocked skipped (admin dialog still active).")
            return
        }

        runCatching {
            dpm.setUninstallBlocked(admin, pkg, blocked)
            Log.i(TAG, "setUninstallBlocked($blocked) applied as ${if (isDeviceOwner) "Device Owner" else "Profile Owner"}")
        }.onFailure {
            Log.w(TAG, "setUninstallBlocked($blocked) failed: ${it.message}")
        }
    }

    /**
     * True when the *strongest* uninstall protection is in force — i.e. we are Device/Profile Owner
     * and `setUninstallBlocked` is honored by the OS. Most consumer installs return false even when
     * admin is active.
     */
    fun isHardUninstallBlockActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val pkg = context.packageName
        val admin = DeviceAdminSupport.adminComponent(context)
        val owner = runCatching { dpm.isDeviceOwnerApp(pkg) }.getOrDefault(false) ||
            runCatching { dpm.isProfileOwnerApp(pkg) }.getOrDefault(false)
        if (!owner) return false
        return runCatching { dpm.isUninstallBlocked(admin, pkg) }.getOrDefault(false)
    }

    /**
     * Snapshot of every protection layer's status — useful for the "Permissions" screen and
     * for adb-driven diagnostics ("why isn't strict mode locking the user out?").
     */
    data class Status(
        val adminActive: Boolean,
        val hardUninstallBlocked: Boolean,
        val accessibilityAlive: Boolean,
        val batteryOptimizationExempt: Boolean,
    )

    fun status(context: Context): Status {
        val accessibilityAlive = dev.ambitionsoftware.tymeboxed.service
            .ActiveBlockingState
            .isServiceAlive
        return Status(
            adminActive = DeviceAdminSupport.isAdminActive(context),
            hardUninstallBlocked = isHardUninstallBlockActive(context),
            accessibilityAlive = accessibilityAlive,
            batteryOptimizationExempt = dev.ambitionsoftware.tymeboxed.oem
                .BatteryOptimizationHelper
                .isIgnoringBatteryOptimizations(context),
        )
    }
}
