package dev.ambitionsoftware.tymeboxed.oem

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Brick-style strict-mode protection depends on the accessibility service surviving Doze /
 * App-Standby / OEM background-killers. The single biggest lever that keeps it alive is the
 * **battery optimization exemption** ("Allow background activity / Don't optimize").
 *
 * This object wraps:
 *   - status check ([isIgnoringBatteryOptimizations])
 *   - the canonical request intent ([requestExemptionIntent])
 *   - a fallback to the system battery-optimization list ([openBatterySettingsIntent]) for when
 *     the canonical intent is rejected on locked-down ROMs.
 *
 * Google Play policy permits `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ONLY for apps whose core
 * function genuinely requires foreground execution — e.g. focus-blockers, alarm clocks,
 * accessibility tools, navigation. Tyme Boxed qualifies on all three counts; document the
 * justification in the Play Console review notes.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimizationHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /**
     * The system-provided dialog that grants the exemption with a single tap. Some OEM ROMs
     * (notably MIUI / HyperOS / ColorOS) silently strip this intent and require the user to
     * deep-link into Settings instead — callers should fall back to [openBatterySettingsIntent].
     *
     * Marked with `@SuppressLint("BatteryLife")` because Lint flags any direct use of this
     * action; we've verified our use-case qualifies for the Play Store exemption.
     */
    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Settings page that lists every app and its battery-optimization state. Always resolves —
     * use it as the fallback when [requestExemptionIntent] is rejected.
     */
    fun openBatterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Convenience entry point: ask for the exemption with a single tap. Returns true when the
     * dialog was launched, false when neither intent could be started (extremely rare — would
     * indicate a stripped Settings APK).
     */
    fun requestExemption(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            Log.i(TAG, "Already exempt from battery optimization.")
            return true
        }
        val pm = context.packageManager
        val canonical = requestExemptionIntent(context)
        if (canonical.resolveActivity(pm) != null) {
            return runCatching { context.startActivity(canonical) }
                .onFailure { Log.w(TAG, "Canonical intent rejected: ${it.message}") }
                .isSuccess
        }
        val fallback = openBatterySettingsIntent()
        return runCatching { context.startActivity(fallback) }
            .onFailure { Log.w(TAG, "Fallback intent rejected: ${it.message}") }
            .isSuccess
    }
}
