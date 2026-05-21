package dev.ambitionsoftware.tymeboxed.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import dev.ambitionsoftware.tymeboxed.MainActivity
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.TymeBoxedApplication
import dev.ambitionsoftware.tymeboxed.di.PermissionsEntryPoint
import dev.ambitionsoftware.tymeboxed.ui.theme.AccentColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires the local notification scheduled by [SessionReminderScheduler] when a focus
 * session ends, matching iOS [TimersUtil.scheduleNotification] / [StrategyManager.scheduleReminder].
 *
 * Audit #29: this used to do its [PackageManager]/[NotificationManager] work on
 * the broadcast-receiver's main thread, which competes with anything else the
 * main thread is doing the moment the alarm fires. We now hand off to a short-
 * lived background coroutine via [BroadcastReceiver.goAsync] so the receiver
 * returns immediately while the actual `nm.notify()` runs off the main thread.
 */
class SessionReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SessionReminderScheduler.ACTION) return
        val title = intent.getStringExtra(SessionReminderScheduler.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(SessionReminderScheduler.EXTRA_BODY) ?: return

        val appCtx = context.applicationContext
        val pending = goAsync()
        backgroundScope.launch {
            try {
                showNotification(appCtx, title, body)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to post session reminder: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showNotification(context: Context, title: String, body: String) {
        val accentArgb = try {
            val prefs = EntryPointAccessors.fromApplication(
                context,
                PermissionsEntryPoint::class.java,
            ).appPreferences()
            AccentColors.byName(prefs.themeColorName.first()).value.toArgb()
        } catch (_: Exception) {
            AccentColors.default.value.toArgb()
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TymeBoxedApplication.SESSION_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_target)
            .setColor(accentArgb)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // PRIORITY_HIGH + VISIBILITY_PUBLIC + DEFAULT_ALL guarantees the
            // reminder is shown as a heads-up alert (not bucketed into "Silent")
            // on Samsung / Pixel / Xiaomi / Vivo / OnePlus. The channel-level
            // IMPORTANCE_HIGH (Android 8+) carries the same intent for newer
            // OS versions where channel importance overrides the legacy priority.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SessionReminderScheduler.NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "SessionReminderRx"
        // Application-scoped scope so individual broadcasts can't leak it.
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
