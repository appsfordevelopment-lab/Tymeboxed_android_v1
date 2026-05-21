package dev.ambitionsoftware.tymeboxed.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.EntryPointAccessors
import dev.ambitionsoftware.tymeboxed.MainActivity
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.TymeBoxedApplication
import dev.ambitionsoftware.tymeboxed.data.db.TymeBoxedDatabase
import dev.ambitionsoftware.tymeboxed.di.PermissionsEntryPoint
import dev.ambitionsoftware.tymeboxed.di.ServiceBridgeEntryPoint
import dev.ambitionsoftware.tymeboxed.domain.model.toDomain
import dev.ambitionsoftware.tymeboxed.ui.theme.AccentColors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service for the lock-screen widget / Live Activity notification only.
 *
 * When the widget is **off**, [ActiveSessionLifecycleCoordinator] does not start this
 * service (starting FGS would still leave a silent "Tyme Boxed" stub in the shade).
 */
class SessionBlockerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private var liveActivitySyncJob: Job? = null

    private data class NotificationContent(
        val profileName: String,
        val timerLine: String,
        val quoteLine: String,
        val statusLabel: String?,
    )

    private var lastRenderedContent: NotificationContent? = null
    private var foregroundStarted: Boolean = false

    /** Cached accent ARGB for [NotificationCompat.Builder.setColor]. */
    private var notificationAccentArgb: Int = AccentColors.default.value.toArgb()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_FOREGROUND_ONLY -> {
                Log.i(TAG, "Stopping foreground service (session continues).")
                tickJob?.cancel()
                tickJob = null
                foregroundStarted = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                runCatching {
                    getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (!prepareLiveActivityForeground()) return START_NOT_STICKY
                val snap = ActiveBlockingState.current
                val profileName = snap.profileName?.takeUnless { it.isBlank() } ?: "Focus Session"
                val sessionStartMs = snap.sessionStartTimeMs.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                updateLiveActivityNotification(profileName, sessionStartMs)
                return START_STICKY
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received — deactivating blocking.")
                tickJob?.cancel()
                tickJob = null
                ActiveBlockingState.deactivate()
                foregroundStarted = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val snap = ActiveBlockingState.current
        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME)
            .takeUnless { it.isNullOrBlank() }
            ?: snap.profileName.takeUnless { it.isNullOrBlank() }
            ?: "Focus Session"
        val sessionStartMs = intent?.getLongExtra(EXTRA_SESSION_START_MS, 0L)?.takeIf { it > 0L }
            ?: snap.sessionStartTimeMs.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        if (!prepareLiveActivityForeground()) return START_NOT_STICKY

        Log.i(
            TAG,
            "Live Activity foreground for $profileName (start=$sessionStartMs)",
        )

        updateLiveActivityNotification(profileName, sessionStartMs)

        val needsRehydrate = intent == null && (snap.profileName.isNullOrBlank() ||
            snap.sessionStartTimeMs <= 0L)
        if (needsRehydrate) {
            hydrateFromDatabase()
        }

        startTimerTickLoop()
        loadAccentColor()

        return START_REDELIVER_INTENT
    }

    /**
     * Returns false when the lock-screen widget is off — caller must not show a notification.
     *
     * Decides solely from the in-memory [ActiveBlockingState] snapshot so we can call
     * [startForeground] without blocking on Room I/O. Any drift between in-memory
     * state and the persisted profile is reconciled asynchronously by
     * [scheduleLiveActivitySyncFromDatabase].
     */
    private fun prepareLiveActivityForeground(): Boolean {
        val snap = ActiveBlockingState.current
        if (!snap.isBlocking || !snap.enableLiveActivityNotification) {
            Log.i(TAG, "Lock-screen widget off or no active session — not running foreground service.")
            stopSelf()
            return false
        }
        scheduleLiveActivitySyncFromDatabase()
        return true
    }

    private fun hydrateFromDatabase() {
        serviceScope.launch {
            val db = TymeBoxedDatabase.getInstance(applicationContext)
            val active = withContext(Dispatchers.IO) { runCatching { db.sessionDao().findActive() }.getOrNull() }
            if (active == null) {
                Log.i(TAG, "Rehydrate: no active session — stopping stale FGS.")
                tickJob?.cancel()
                tickJob = null
                ActiveBlockingState.deactivate()
                foregroundStarted = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            val profileName = withContext(Dispatchers.IO) {
                runCatching { db.profileDao().getByIdWithApps(active.profileId)?.profile?.name }
                    .getOrNull()
            }?.takeUnless { it.isBlank() } ?: "Focus Session"
            val startMs = active.startTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            val profileRow = withContext(Dispatchers.IO) {
                runCatching { db.profileDao().getByIdWithApps(active.profileId) }.getOrNull()
            }
            if (profileRow != null) {
                val profile = profileRow.toDomain()
                val snap = ActiveBlockingState.current
                if (!snap.isBlocking ||
                    snap.profileId != profile.id ||
                    snap.enableLiveActivityNotification != profile.enableLiveActivity
                ) {
                    BlockingStateRestorer.apply(
                        profile = profile,
                        session = active.toDomain(),
                        blockedPackages = profile.blockedPackages.toSet(),
                        strictModeEnabled = snap.strictModeEnabled,
                    )
                }
            }
            if (!prepareLiveActivityForeground()) return@launch
            updateLiveActivityNotification(profileName, startMs)
        }
    }

    private fun startTimerTickLoop() {
        tickJob?.cancel()
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceBridgeEntryPoint::class.java,
        )
        val handler = entry.sessionTimerHandler()
        tickJob = serviceScope.launch {
            while (isActive) {
                delay(1_000L)
                try {
                    withContext(Dispatchers.IO) {
                        handler.onServiceTick()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Session timer tick: ${e.message}")
                }
                refreshForegroundNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        liveActivitySyncJob?.cancel()
        if (foregroundStarted) {
            foregroundStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            runCatching {
                getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
            }
        }
        serviceJob.cancel()
        super.onDestroy()
        Log.i(TAG, "SessionBlockerService destroyed.")
    }

    private fun refreshForegroundNotification() {
        val snap = ActiveBlockingState.current
        if (!snap.isBlocking || !snap.enableLiveActivityNotification) {
            stopSelf()
            return
        }

        val profileName = snap.profileName?.takeUnless { it.isBlank() } ?: "Focus Session"
        val sessionStartMs = snap.sessionStartTimeMs.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        val now = System.currentTimeMillis()
        val timerLine = notificationTimerLine(snap, sessionStartMs, now)
        val quoteLine = notificationQuoteLine(snap)
        val statusLabel = if (snap.isPauseActive) getString(R.string.session_notification_paused) else null
        val nextContent = NotificationContent(
            profileName = profileName,
            timerLine = timerLine,
            quoteLine = quoteLine,
            statusLabel = statusLabel,
        )
        if (nextContent == lastRenderedContent) return

        updateLiveActivityNotification(profileName, sessionStartMs)
        lastRenderedContent = nextContent
    }

    private fun openSessionPendingIntent(): PendingIntent {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Read the user's chosen accent colour once and cache it for [setColor]. */
    private fun loadAccentColor() {
        serviceScope.launch {
            try {
                val prefs = EntryPointAccessors.fromApplication(
                    applicationContext,
                    PermissionsEntryPoint::class.java,
                ).appPreferences()
                val name = withContext(Dispatchers.IO) { prefs.themeColorName.first() }
                notificationAccentArgb = AccentColors.byName(name).value.toArgb()
                refreshForegroundNotification()
            } catch (_: Exception) { /* keep default */ }
        }
    }

    private fun updateLiveActivityNotification(profileName: String, sessionStartTimeMs: Long) {
        val snap = ActiveBlockingState.current
        val notification = buildLiveActivityNotification(
            openPending = openSessionPendingIntent(),
            profileName = profileName,
            sessionStartTimeMs = sessionStartTimeMs,
            snap = snap,
        )
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            runCatching {
                getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildLiveActivityNotification(
        openPending: PendingIntent,
        profileName: String,
        sessionStartTimeMs: Long,
        snap: ActiveBlockingState.Snapshot,
    ): Notification {
        val now = System.currentTimeMillis()
        val timerLine = notificationTimerLine(snap, sessionStartTimeMs, now)
        val quoteLine = notificationQuoteLine(snap)
        val statusLabel = if (snap.isPauseActive) getString(R.string.session_notification_paused) else null

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_target)
            .setColor(notificationAccentArgb)
            // Tick updates every second — only alert once when first posted, so the
            // notification ranks above "Silent" without buzzing on every refresh.
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // PRIORITY_DEFAULT pairs with IMPORTANCE_DEFAULT on the channel.
            // Pre-O (Nougat & below) reads only this; O+ reads the channel importance.
            // Some OEM forks (MIUI/ColorOS/OneUI) ALSO look at priority on O+ when
            // deciding whether to ship a notification to the "Silent" group.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setLargeIcon(null as Bitmap?)
            .setContentTitle(profileName)
            .setContentText(timerLine)

        val content = RemoteViews(packageName, R.layout.notification_live).apply {
            setTextViewText(R.id.notification_profile_name, profileName)
            setViewVisibility(R.id.notification_quote, android.view.View.GONE)
            setTextViewText(R.id.notification_timer, timerLine)
            applyPausedStatusRow(statusLabel)
        }
        val expanded = RemoteViews(packageName, R.layout.notification_live_expanded).apply {
            setTextViewText(R.id.notification_profile_name, profileName)
            setTextViewText(R.id.notification_quote, quoteLine)
            setViewVisibility(R.id.notification_quote, android.view.View.VISIBLE)
            setTextViewText(R.id.notification_timer, timerLine)
            applyPausedStatusRow(statusLabel)
        }
        builder.setCustomContentView(content)
            .setCustomBigContentView(expanded)

        lastRenderedContent = NotificationContent(
            profileName = profileName,
            timerLine = timerLine,
            quoteLine = quoteLine,
            statusLabel = statusLabel,
        )
        return builder.build()
    }

    private fun RemoteViews.applyPausedStatusRow(statusLabel: String?) {
        setViewVisibility(
            R.id.notification_status_row,
            if (statusLabel != null) android.view.View.VISIBLE else android.view.View.GONE,
        )
        if (statusLabel != null) {
            setTextViewText(R.id.notification_status_label, statusLabel)
            setImageViewResource(R.id.notification_status_icon, R.drawable.ic_notification_target)
        }
    }

    /** Expanded notification quote line (focus message picked at session start). */
    private fun notificationQuoteLine(snap: ActiveBlockingState.Snapshot): String {
        if (snap.isPauseActive) {
            return getString(R.string.session_notification_body_paused)
        }
        val quote = snap.focusQuoteMessage?.trim()
        if (!quote.isNullOrEmpty()) return quote
        return getString(R.string.session_notification_quote_fallback)
    }

    /**
     * Reconciles [ActiveBlockingState] with the persisted profile off the main thread.
     *
     * Previously this was a `runBlocking(Dispatchers.IO)` invoked from
     * [onStartCommand] (main thread), which could ANR on slow devices or
     * under heavy Room load. We now launch into [serviceScope] so the I/O
     * runs on [Dispatchers.IO] and the result is applied back on the main
     * thread via [refreshForegroundNotification], which also handles
     * stopping the FGS when the user has just disabled the lock-screen widget.
     */
    private fun scheduleLiveActivitySyncFromDatabase() {
        val profileId = ActiveBlockingState.current.profileId ?: return
        liveActivitySyncJob?.cancel()
        liveActivitySyncJob = serviceScope.launch {
            val profile = withContext(Dispatchers.IO) {
                runCatching {
                    TymeBoxedDatabase.getInstance(applicationContext)
                        .profileDao()
                        .getByIdWithApps(profileId)
                        ?.toDomain()
                }.getOrNull()
            } ?: return@launch
            BlockingStateRestorer.syncLiveActivityForActiveProfile(profile)
            if (foregroundStarted) {
                refreshForegroundNotification()
            } else {
                val snap = ActiveBlockingState.current
                if (!snap.isBlocking || !snap.enableLiveActivityNotification) {
                    stopSelf()
                }
            }
        }
    }

    private fun notificationTimerLine(
        snap: ActiveBlockingState.Snapshot,
        sessionStartTimeMs: Long,
        nowMs: Long,
    ): String {
        if (snap.isPauseActive) {
            val resumeAt = snap.breakAutoResumeAtMs
            if (resumeAt != null) {
                val sec = ((resumeAt - nowMs).coerceAtLeast(0L)) / 1000L
                return formatCompactDuration(sec)
            }
            return getString(R.string.session_notification_paused)
        }
        val elapsedSec = ((nowMs - sessionStartTimeMs).coerceAtLeast(0L)) / 1000L
        return formatCompactDuration(elapsedSec)
    }

    private fun formatCompactDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0L) {
            String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
        }
    }

    companion object {
        private const val TAG = "SessionBlockerSvc"
        private const val CHANNEL_ID = TymeBoxedApplication.SESSION_CHANNEL_ID
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "dev.ambitionsoftware.tymeboxed.STOP_SESSION"
        const val ACTION_STOP_FOREGROUND_ONLY =
            "dev.ambitionsoftware.tymeboxed.STOP_SESSION_FOREGROUND_ONLY"
        const val ACTION_REFRESH = "dev.ambitionsoftware.tymeboxed.REFRESH_SESSION_NOTIFICATION"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_SESSION_START_MS = "session_start_ms"

        fun refreshIntent(context: Context): Intent =
            Intent(context, SessionBlockerService::class.java).apply {
                action = ACTION_REFRESH
            }

        fun startIntent(context: Context, profileName: String, sessionStartTimeMs: Long): Intent =
            Intent(context, SessionBlockerService::class.java).apply {
                putExtra(EXTRA_PROFILE_NAME, profileName)
                putExtra(EXTRA_SESSION_START_MS, sessionStartTimeMs)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, SessionBlockerService::class.java).apply {
                action = ACTION_STOP
            }

        /** Ends the FGS/notification without stopping the focus session. */
        fun stopForegroundOnlyIntent(context: Context): Intent =
            Intent(context, SessionBlockerService::class.java).apply {
                action = ACTION_STOP_FOREGROUND_ONLY
            }
    }
}
