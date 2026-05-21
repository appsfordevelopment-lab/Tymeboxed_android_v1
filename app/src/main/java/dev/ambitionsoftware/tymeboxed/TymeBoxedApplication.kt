package dev.ambitionsoftware.tymeboxed

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dev.ambitionsoftware.tymeboxed.data.db.TymeBoxedDatabase
import dev.ambitionsoftware.tymeboxed.di.PermissionsEntryPoint
import dev.ambitionsoftware.tymeboxed.di.SessionLifecycleEntryPoint
import dev.ambitionsoftware.tymeboxed.domain.model.toDomain
import dev.ambitionsoftware.tymeboxed.service.BlockingStateRestorer
import dev.ambitionsoftware.tymeboxed.service.ActiveBlockingState
import dev.ambitionsoftware.tymeboxed.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hilt application entry point. Mirrors the minimal iOS bootstrap in
 * `TymeBoxedApp.swift` — registers the foreground-service notification
 * channel and rehydrates any active blocking session that was running when
 * the process was killed.
 */
@HiltAndroidApp
class TymeBoxedApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Install the crash recorder FIRST so any subsequent init failure is
        // captured. Until Crashlytics / Sentry is wired up this is the only
        // way support can see what went wrong on a user device.
        CrashReporter.install(this)
        deleteLegacyLowImportanceChannels()
        createSessionNotificationChannel()
        createSessionSilentForegroundChannel()
        createSessionReminderChannel()
        // Ensure Strict mode starts OFF by default (one-time migration).
        appScope.launch {
            runCatching {
                EntryPointAccessors.fromApplication(this@TymeBoxedApplication, PermissionsEntryPoint::class.java)
                    .appPreferences()
                    .ensureStrictModeDefaultsOffOnce()
            }
        }
        // Re-read permission flags after channel creation; cold start must match system state
        // before any Compose screen reads [PermissionsCoordinator].
        EntryPointAccessors.fromApplication(this, PermissionsEntryPoint::class.java)
            .permissionsCoordinator()
            .refresh()
        rehydrateBlockingState()
    }

    /**
     * Live-Activity / focus-session channel.
     *
     * IMPORTANCE_DEFAULT (not LOW) keeps the notification out of the "Silent" group on
     * Pixel/Samsung/Xiaomi/Vivo/OnePlus while still avoiding an every-second heads-up
     * (the builder pairs this with `setOnlyAlertOnce(true)`). Importance is sticky once
     * a channel is created, so the channel ID is versioned — bump the `_v` suffix any
     * time we need to migrate users off an older importance.
     */
    private fun createSessionNotificationChannel() {
        val channel = NotificationChannel(
            SESSION_CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.session_channel_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
            enableVibration(false)
            setSound(null, null)
            setBypassDnd(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Transient channel for the mandatory [startForeground] call when Live Activity is off.
     * The notification is detached immediately so nothing stays in the shade.
     */
    private fun createSessionSilentForegroundChannel() {
        val channel = NotificationChannel(
            SESSION_SILENT_CHANNEL_ID,
            getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.session_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Removes pre-fix channels whose importance is stuck on `LOW`/`MIN`.
     *
     * Android does not let an app raise an existing channel's importance — once
     * created at IMPORTANCE_LOW the user (and only the user) can change it. Existing
     * installs would therefore keep landing the Live Activity in "Silent
     * notifications" even after this fix. Deleting the legacy ID forces Android
     * to honour the new channel created above with IMPORTANCE_DEFAULT.
     */
    private fun deleteLegacyLowImportanceChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (legacyId in LEGACY_SESSION_CHANNEL_IDS) {
            runCatching { nm.deleteNotificationChannel(legacyId) }
        }
    }

    /**
     * If the app process was killed while a session was active (e.g. by the
     * system, or during a crash), rehydrate [ActiveBlockingState] so the
     * accessibility service can immediately enforce blocking when it reconnects.
     *
     * Also restarts the foreground service for the persistent notification.
     */
    private fun rehydrateBlockingState() {
        // Only rehydrate if not already blocking (e.g. HomeViewModel already did it)
        if (ActiveBlockingState.current.isBlocking) return

        appScope.launch {
            try {
                val db = TymeBoxedDatabase.getInstance(this@TymeBoxedApplication)

                val activeSession = db.sessionDao().findActive() ?: return@launch
                val profileWithApps = db.profileDao().getByIdWithApps(activeSession.profileId)
                    ?: return@launch
                val profile = profileWithApps.toDomain()
                val session = activeSession.toDomain()

                val blockedPkgs = profile.blockedPackages.toSet()
                val strictModeEnabled = EntryPointAccessors
                    .fromApplication(this@TymeBoxedApplication, PermissionsEntryPoint::class.java)
                    .appPreferences()
                    .strictModeEnabled
                    .first()
                BlockingStateRestorer.apply(
                    profile = profile,
                    session = session,
                    blockedPackages = blockedPkgs,
                    strictModeEnabled = strictModeEnabled,
                )

                withContext(Dispatchers.Main) {
                    EntryPointAccessors.fromApplication(
                        this@TymeBoxedApplication,
                        SessionLifecycleEntryPoint::class.java,
                    ).activeSessionLifecycleCoordinator().sync()
                }

                android.util.Log.i(
                    "TymeBoxedApp",
                    "Rehydrated blocking for '${profile.name}' with ${blockedPkgs.size} blocked packages.",
                )
            } catch (e: Throwable) {
                android.util.Log.w(
                    "TymeBoxedApp",
                    "Failed to rehydrate blocking state: ${e.message}",
                )
            }
        }
    }

    /**
     * One-shot reminder channel (when a session ends and the user opted in).
     *
     * IMPORTANCE_HIGH so the reminder pops as a heads-up — these are explicit
     * user-scheduled nudges that should not be relegated to the Silent group.
     */
    private fun createSessionReminderChannel() {
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            SESSION_REMINDER_CHANNEL_ID,
            getString(R.string.session_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.session_reminder_channel_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 250L, 200L, 250L)
            setSound(
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION,
                ),
                audioAttrs,
            )
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        /**
         * Live-Activity foreground service channel — VERSIONED.
         *
         * If the importance/sound profile of this channel ever needs to change again,
         * bump the suffix (e.g. `_v3`) and add the previous ID to [LEGACY_SESSION_CHANNEL_IDS].
         * The user-facing channel name stays the same so existing notification settings
         * don't appear to multiply in System Settings (the legacy IDs are deleted on launch).
         */
        const val SESSION_CHANNEL_ID = "tymeboxed_session_v2"
        const val SESSION_SILENT_CHANNEL_ID = "tymeboxed_session_silent"
        const val SESSION_REMINDER_CHANNEL_ID = "tymeboxed_session_reminder_v2"

        /** Channel IDs from older app versions whose importance is stuck on LOW/MIN. */
        private val LEGACY_SESSION_CHANNEL_IDS = listOf(
            "tymeboxed_session",
            "tymeboxed_session_reminder",
        )
    }
}
