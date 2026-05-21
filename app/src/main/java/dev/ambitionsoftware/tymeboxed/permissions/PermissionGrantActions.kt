package dev.ambitionsoftware.tymeboxed.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.ambitionsoftware.tymeboxed.MainActivity

/**
 * Shared grant flow for [TymePermission] rows in Intro and Settings → Permissions.
 * Accessibility always requires the in-app disclosure dialog first (see callers).
 */
fun grantPermission(context: Context, perm: TymePermission) {
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

private fun tryRequestPostNotificationsRuntime(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val activity = context as? MainActivity ?: return false
    val already = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (already) return true
    val canPrompt = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) || isFirstNotificationsRequest(activity)
    if (!canPrompt) return false
    activity.requestPostNotificationsPermission()
    return true
}

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
