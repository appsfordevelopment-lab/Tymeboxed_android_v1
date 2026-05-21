package dev.ambitionsoftware.tymeboxed.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.data.db.dao.ProfileDao
import dev.ambitionsoftware.tymeboxed.domain.model.toDomain
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registers exact alarms for the next schedule start/end per profile (Android analogue of
 * iOS DeviceActivity schedule monitoring).
 *
 * Each profile owns a persistent [dev.ambitionsoftware.tymeboxed.data.db.entities.ProfileEntity.scheduleAlarmRequestCode]
 * so [PendingIntent] request codes never collide via `profileId.hashCode()`.
 */
@Singleton
class ProfileScheduleAlarmScheduler @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val profileDao: ProfileDao,
) {

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val profiles = profileDao.getAllWithAppsSnapshot()
            .map { it.toDomain() to it.profile.scheduleAlarmRequestCode }
        for ((profile, requestCode) in profiles) {
            cancelAlarmsForProfile(profile.id, requestCode)
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        for ((profile, requestCode) in profiles) {
            if (requestCode <= 0) continue
            val sched = profile.schedule ?: continue
            if (!sched.isActive) continue
            val nextStart = sched.nextStartAfter(now - 1, zone)
            val nextEnd = sched.nextEndAfter(now - 1, zone)
            if (nextStart != null) {
                scheduleAlarm(profile.id, requestCode, nextStart, isStart = true)
            }
            if (nextEnd != null) {
                scheduleAlarm(profile.id, requestCode, nextEnd, isStart = false)
            }
        }
        Log.i(
            TAG,
            "Rescheduled alarms for ${profiles.count { (p, _) -> p.schedule?.isActive == true }} profiles",
        )
    }

    fun cancelAlarmsForProfile(profileId: String, scheduleAlarmRequestCode: Int) {
        if (scheduleAlarmRequestCode <= 0) return
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(profileId, scheduleAlarmRequestCode, isStart = true))
        am.cancel(pendingIntent(profileId, scheduleAlarmRequestCode, isStart = false))
    }

    private fun scheduleAlarm(
        profileId: String,
        scheduleAlarmRequestCode: Int,
        triggerAtMillis: Long,
        isStart: Boolean,
    ) {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(profileId, scheduleAlarmRequestCode, isStart)
        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, pi),
                pi,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted: ${e.message}")
        }
    }

    private fun pendingIntent(
        profileId: String,
        scheduleAlarmRequestCode: Int,
        isStart: Boolean,
    ): PendingIntent {
        val intent = Intent(appContext, ProfileScheduleReceiver::class.java).apply {
            action = if (isStart) ProfileScheduleReceiver.ACTION_START else ProfileScheduleReceiver.ACTION_END
            putExtra(ProfileScheduleReceiver.EXTRA_PROFILE_ID, profileId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(scheduleAlarmRequestCode, isStart),
            intent,
            flags,
        )
    }

    /**
     * Two distinct request codes per profile: even = schedule start, odd = schedule end.
     * [scheduleAlarmRequestCode] is unique per profile row (DB-assigned).
     */
    private fun requestCode(scheduleAlarmRequestCode: Int, isStart: Boolean): Int {
        val base = scheduleAlarmRequestCode * 2
        return if (isStart) base else base + 1
    }

    companion object {
        private const val TAG = "ProfileScheduleAlarm"
    }
}
