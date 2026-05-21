package dev.ambitionsoftware.tymeboxed.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.MainActivity

/**
 * When a focus session **ends**, schedules a one-shot local notification [delaySec] in the
 * future, mirroring iOS [StrategyManager.scheduleReminder] and [TimersUtil.scheduleNotification].
 * When a new session **starts**, [cancelAll] is called (see [dev.ambitionsoftware.tymeboxed.ui.screens.home.HomeViewModel.startSession]).
 */
@Singleton
class SessionReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private val persistPrefs by lazy {
        context.getSharedPreferences(PERSIST_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Cancels a pending end-of-session reminder (iOS: [TimersUtil.cancelAll] on session start).
     */
    fun cancelAll() {
        val i = Intent(context, SessionReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("tymeboxed://session_reminder")
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            i,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) alarmManager?.cancel(pi)
        clearPersistedReminder()
    }

    /**
     * Schedules the reminder for [Profile.reminderTimeSeconds] from now. No-op if disabled.
     */
    fun scheduleAfterSessionEnd(profile: Profile) {
        if (!profile.isSessionReminderEnabled()) {
            cancelAll()
            return
        }
        val delaySec = profile.reminderTimeSeconds ?: return

        val title = context.getString(R.string.session_reminder_title, profile.name.ifBlank { context.getString(R.string.app_name) })
        val body = profile.customReminderMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.session_reminder_default_body, profile.name.ifBlank { "…" })

        cancelAll()

        val triggerAt = System.currentTimeMillis() + delaySec * 1000L
        scheduleAlarmAt(triggerAt, title, body)
        // Persist so [restorePendingAfterBoot] can re-arm the alarm after a
        // reboot wipes the AlarmManager state (audit #30: reminders lost on reboot).
        persistReminder(triggerAt, title, body)
        Log.i(TAG, "Session reminder in ${delaySec}s for profile ${profile.id}")
    }

    /**
     * Called from [BootCompletedReceiver]. If a reminder was scheduled before
     * reboot, re-arms the AlarmManager for the same wall-clock time. If the
     * time has already passed, fires the notification immediately.
     */
    fun restorePendingAfterBoot() {
        val triggerAt = persistPrefs.getLong(KEY_TRIGGER_AT, 0L)
        if (triggerAt <= 0L) return
        val title = persistPrefs.getString(KEY_TITLE, null) ?: return
        val body = persistPrefs.getString(KEY_BODY, null) ?: return

        val now = System.currentTimeMillis()
        if (triggerAt <= now) {
            // Reboot took longer than the remaining delay — deliver right
            // away through the receiver path (still off the main thread).
            val intent = Intent(context, SessionReminderReceiver::class.java).apply {
                action = ACTION
                data = Uri.parse("tymeboxed://session_reminder")
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
            context.sendBroadcast(intent)
        } else {
            scheduleAlarmAt(triggerAt, title, body)
        }
        // Once re-armed, drop the persisted copy; if the alarm fires the user
        // sees it, if they start a new session [cancelAll] clears it anyway.
        clearPersistedReminder()
        Log.i(TAG, "Restored session reminder for trigger=$triggerAt")
    }

    private fun scheduleAlarmAt(triggerAt: Long, title: String, body: String) {
        val show = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val operation = pendingIntentForBroadcast(title, body)
        val info = AlarmManager.AlarmClockInfo(triggerAt, show)
        alarmManager?.setAlarmClock(info, operation)
    }

    private fun persistReminder(triggerAt: Long, title: String, body: String) {
        persistPrefs.edit {
            putLong(KEY_TRIGGER_AT, triggerAt)
            putString(KEY_TITLE, title)
            putString(KEY_BODY, body)
        }
    }

    private fun clearPersistedReminder() {
        persistPrefs.edit {
            remove(KEY_TRIGGER_AT)
            remove(KEY_TITLE)
            remove(KEY_BODY)
        }
    }

    private fun pendingIntentForBroadcast(title: String?, body: String?): PendingIntent {
        val intent = Intent(context, SessionReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("tymeboxed://session_reminder")
            if (title != null) putExtra(EXTRA_TITLE, title)
            if (body != null) putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "SessionReminder"
        const val ACTION = "dev.ambitionsoftware.tymeboxed.action.SESSION_END_REMINDER"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val NOTIFICATION_ID = 0x4d52
        private const val REQUEST_CODE = 0x4001

        // Persistence used to restore the alarm after a reboot (audit #30).
        private const val PERSIST_PREFS = "tymeboxed_session_reminder_persist"
        private const val KEY_TRIGGER_AT = "trigger_at"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
    }
}
