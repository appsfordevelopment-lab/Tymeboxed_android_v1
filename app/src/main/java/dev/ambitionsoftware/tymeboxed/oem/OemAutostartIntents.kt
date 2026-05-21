package dev.ambitionsoftware.tymeboxed.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Locale

/**
 * Deep-link intents to the per-OEM "Auto-start" / "Background management" settings page.
 *
 * Why this matters for strict-mode protection: MIUI / HyperOS, ColorOS, OriginOS, FuntouchOS and
 * Hi-OS aggressively kill background apps — including the accessibility service that powers
 * [dev.ambitionsoftware.tymeboxed.service.StrictUninstallInterception]. If the user doesn't
 * whitelist Tyme Boxed in the OEM auto-start manager, the service stops within minutes of the
 * screen turning off and the uninstall redirect stops working.
 *
 * These activities are private APIs and silently change between OS versions — every call
 * goes through [resolve] which falls back to the next intent if a component isn't installed
 * or is private on the current ROM. The final fallback is the app's own details page.
 */
object OemAutostartIntents {

    private const val TAG = "OemAutostartIntents"

    /**
     * @return the first intent that resolves to an installed activity on this device, or `null`
     *   if none did. Caller is expected to fall back to a generic settings page.
     */
    fun resolve(context: Context): Intent? {
        val pm = context.packageManager
        for (candidate in candidates(context)) {
            val resolvable = candidate.resolveActivity(pm) != null
            Log.d(
                TAG,
                "candidate ${candidate.component?.flattenToShortString() ?: candidate.action} resolvable=$resolvable",
            )
            if (resolvable) return candidate
        }
        return null
    }

    /**
     * Heuristic detection of whether the current ROM is *known* to need an auto-start whitelist.
     * Used by the UI to show the "Allow auto-start" card only on devices that actually have one.
     */
    fun isAutostartManagedByOem(): Boolean {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return AUTOSTART_BRANDS.any { brand.contains(it) || manufacturer.contains(it) }
    }

    private val AUTOSTART_BRANDS = listOf(
        "xiaomi", "redmi", "poco", "mi",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "huawei", "honor",
        "letv", "asus",
        "samsung", // for "Battery > Background usage limits" / "Sleeping apps"
    )

    /**
     * Ordered list of candidate intents. We try the most specific OEM activity first, then a
     * broader fallback within the same family, then the generic app-details page.
     */
    private fun candidates(context: Context): List<Intent> {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val isXiaomi = listOf("xiaomi", "redmi", "poco", "mi").any { brand.contains(it) || manufacturer.contains(it) }
        val isOppoFamily = listOf("oppo", "realme").any { brand.contains(it) || manufacturer.contains(it) }
        val isOnePlus = brand.contains("oneplus") || manufacturer.contains("oneplus")
        val isVivo = listOf("vivo", "iqoo").any { brand.contains(it) || manufacturer.contains(it) }
        val isHuawei = listOf("huawei", "honor").any { brand.contains(it) || manufacturer.contains(it) }
        val isSamsung = brand.contains("samsung") || manufacturer.contains("samsung")
        val isLetv = brand.contains("letv") || manufacturer.contains("letv")
        val isAsus = brand.contains("asus") || manufacturer.contains("asus")

        val out = mutableListOf<Intent>()

        if (isXiaomi) {
            out += componentIntent(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            // HyperOS / newer MIUI: same package, sometimes renamed activity.
            out += componentIntent(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
            )
        }
        if (isOppoFamily) {
            out += componentIntent(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            )
            out += componentIntent(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            )
            out += componentIntent(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity",
            )
            // ColorOS 12+ / Realme UI 3+
            out += componentIntent(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            )
        }
        if (isOnePlus) {
            out += componentIntent(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            )
            // OxygenOS 12+ uses oplus
            out += componentIntent(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            )
        }
        if (isVivo) {
            out += componentIntent(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
            out += componentIntent(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            )
            out += componentIntent(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            )
        }
        if (isHuawei) {
            out += componentIntent(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
            out += componentIntent(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity",
            )
            // Honor MagicOS
            out += componentIntent(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
        }
        if (isSamsung) {
            // Samsung has no per-app auto-start; the closest equivalent is the
            // "Sleeping apps" / "Never sleeping apps" list under Battery.
            out += componentIntent(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            )
        }
        if (isLetv) {
            out += componentIntent(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity",
            )
        }
        if (isAsus) {
            out += componentIntent(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity",
            ).apply { setData(android.net.Uri.parse("mobilemanager://function/entry/AutoStart")) }
        }

        // Final, universal fallback — the app's own details page. Always resolvable.
        out += Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

        return out.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))
}
