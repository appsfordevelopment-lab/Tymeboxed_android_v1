package dev.ambitionsoftware.tymeboxed.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Centralised entry point for starting [SessionBlockerService] (or any other
 * `foregroundServiceType="specialUse"` service).
 *
 * Android 14+ (API 34) made foreground-service starts much stricter:
 *
 *  - Apps need [Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE] declared
 *    AND the runtime conditions for that subtype to be satisfied.
 *  - If the OS decides we can't legally start a FGS right now (e.g. process
 *    is no longer in a startable state, manifest subtype not declared, user
 *    revoked the permission via the per-package toggle), it throws
 *    [ForegroundServiceStartNotAllowedException] which crashes the app.
 *
 * This helper:
 *  1. Pre-flights the manifest permission so we can log the cause.
 *  2. Catches [ForegroundServiceStartNotAllowedException] (and the broader
 *     [SecurityException] on older OEM forks) instead of letting it crash.
 *  3. Returns `true` only when the start call was accepted by the OS.
 */
object ForegroundServiceSafeLauncher {

    private const val TAG = "FgsSafeLauncher"

    fun start(context: Context, intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // FOREGROUND_SERVICE_SPECIAL_USE is install-time but can still be
            // missing in a corrupted install or absent in older test APKs.
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.e(
                    TAG,
                    "FOREGROUND_SERVICE_SPECIAL_USE not granted; skipping FGS start to " +
                        "avoid ForegroundServiceStartNotAllowedException.",
                )
                return false
            }
        }

        return try {
            context.startForegroundService(intent)
            true
        } catch (e: Throwable) {
            // Catch ForegroundServiceStartNotAllowedException without referencing the
            // API-34-only class directly so this code still loads on older OS versions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                Log.e(TAG, "FGS start not allowed by OS: ${e.message}", e)
                return false
            }
            if (e is SecurityException) {
                Log.e(TAG, "FGS start rejected (SecurityException): ${e.message}", e)
                return false
            }
            throw e
        }
    }
}
