package dev.ambitionsoftware.tymeboxed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.permissions.PermissionsCoordinator
import dev.ambitionsoftware.tymeboxed.service.ActiveBlockingState
import dev.ambitionsoftware.tymeboxed.service.ProfileScheduleAlarmScheduler
import dev.ambitionsoftware.tymeboxed.ui.navigation.TymeBoxedNavHost
import dev.ambitionsoftware.tymeboxed.ui.theme.TbTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Single-activity host. Everything is Compose-based; the activity exists
 * only to own the NavHost, the theme wrapper, and NFC intent delivery.
 *
 * NFC is declared in the manifest with `ACTION_NDEF_DISCOVERED` /
 * `ACTION_TAG_DISCOVERED`, which means the OS will launch this activity
 * (or deliver to `onNewIntent` if already foreground) whenever a tag is
 * scanned while Tyme Boxed is in the foreground. Phase 4 wires the NFC
 * read into `StrategyCoordinator.handleNfcTag(tagId)`; Phase 1 just logs.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var permissionsCoordinator: PermissionsCoordinator
    @Inject lateinit var scheduleAlarmScheduler: ProfileScheduleAlarmScheduler

    private val permissionRecheckHandler = Handler(Looper.getMainLooper())
    private var nfcAdapter: NfcAdapter? = null

    /**
     * Runtime POST_NOTIFICATIONS launcher (Android 13+, API 33+).
     *
     * Without this, the OS *never* shows the runtime permission dialog and the
     * Live-Activity / reminder notifications silently fail to post on fresh
     * installs — which is one half of the "notification not appearing on some
     * devices" bug. The result is fed back into [PermissionsCoordinator] so the
     * permissions card reflects the new state without waiting for ON_RESUME.
     */
    private val postNotificationsLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
            permissionsCoordinator.refresh()
        }

    // True while a Compose feature (e.g. NfcIosStyleScanSheet) has temporarily taken over reader
    // mode with its own callback. While set, onResume / restoreSwallowingNfcReaderMode() will not
    // stomp on it. The sheet calls [restoreSwallowingNfcReaderMode] from its onDispose to release.
    @Volatile
    private var nfcReaderHandedOff: Boolean = false

    // Tracks the last scanned UID so a single physical tap that the OS may surface twice (once to
    // the in-app reader, once after the sheet tears down) is only acted on once at the toast layer.
    @Volatile
    private var lastSwallowedTagUid: String? = null
    private val lastSwallowedTagClearer = Runnable { lastSwallowedTagUid = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // If the OS launched us due to an NFC scan while no session is running, immediately
        // consume the intent and exit. This prevents the "blank/white page" UX and stops
        // NFC tags (including URL tags) from navigating somewhere unexpected.
        if (handleNfcIntent(intent, isColdStart = true)) {
            return
        }

        enableEdgeToEdge()
        setContent {
            TbTheme {
                TymeBoxedNavHost(prefs = appPreferences)
            }
        }

        maybeRequestPostNotificationsPermission()
    }

    /**
     * Shows the runtime POST_NOTIFICATIONS dialog on Android 13+ (API 33+) when
     * the user has not yet granted or permanently denied it.
     *
     * Pre-Tiramisu the permission is install-time and granted automatically, so
     * this is a no-op there. Already-granted is a no-op as well.
     */
    private fun maybeRequestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val already = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (already) return
        runCatching {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }.onFailure { Log.w(TAG, "Failed to request POST_NOTIFICATIONS: ${it.message}") }
    }

    /**
     * Exposed so the in-app Permissions screen can re-trigger the runtime dialog
     * (e.g. after the user re-enables notifications in a profile) instead of
     * always deep-linking to system settings.
     */
    fun requestPostNotificationsPermission() {
        maybeRequestPostNotificationsPermission()
    }

    override fun onResume() {
        super.onResume()
        permissionsCoordinator.refresh()
        // System sometimes updates Secure settings / AppOps slightly after resume.
        permissionRecheckHandler.postDelayed({ permissionsCoordinator.refresh() }, 400L)

        // If the user just granted "Alarms & reminders", immediately register schedule alarms.
        lifecycleScope.launch {
            runCatching { scheduleAlarmScheduler.rescheduleAll() }
        }

        // Brick-style: Reader Mode prevents NFC URL tags from launching the browser while the app
        // is foreground. We always enable it here so scans never navigate anywhere unexpectedly.
        // Skipped while a sheet is actively scanning (it owns the callback for that window).
        if (!nfcReaderHandedOff) {
            enableSwallowingNfcReaderModeInternal()
        }
    }

    override fun onPause() {
        runCatching { nfcAdapter?.disableReaderMode(this) }
        permissionRecheckHandler.removeCallbacks(lastSwallowedTagClearer)
        lastSwallowedTagUid = null
        super.onPause()
    }

    /**
     * Called by an in-app NFC scanner (e.g. `NfcIosStyleScanSheet`) when it is about to take
     * over the reader-mode callback. Prevents [onResume] from racing the sheet on subsequent
     * recompositions.
     */
    fun markNfcReaderHandedOff() {
        nfcReaderHandedOff = true
    }

    /**
     * Restore the activity-wide "swallowing" reader-mode callback after an in-app scanner finishes.
     *
     * IMPORTANT: Without this, when the sheet calls `disableReaderMode(...)` after a successful
     * read, the next physical tap of the still-nearby tag falls through to the OS, which on some
     * OEMs (e.g. HyperOS / MIUI) surfaces "New tag collected — Empty tag" or briefly opens a blank
     * page.
     */
    fun restoreSwallowingNfcReaderMode() {
        nfcReaderHandedOff = false
        enableSwallowingNfcReaderModeInternal()
    }

    /**
     * Lets an in-app scanner tell the activity which UID was just consumed, so that the inevitable
     * follow-up read (user still holding the phone near the tag) is silently swallowed instead of
     * surfacing a redundant toast.
     */
    fun noteRecentlyConsumedNfcUid(uid: String) {
        if (uid.isBlank() || uid == "unknown") return
        lastSwallowedTagUid = uid
        permissionRecheckHandler.removeCallbacks(lastSwallowedTagClearer)
        permissionRecheckHandler.postDelayed(lastSwallowedTagClearer, DUPLICATE_TAG_WINDOW_MS)
    }

    private fun enableSwallowingNfcReaderModeInternal() {
        val adapter = nfcAdapter ?: return
        runCatching {
            adapter.enableReaderMode(
                this,
                { tag -> handleNfcTagFromReaderMode(tag) },
                NFC_READER_FLAGS,
                null,
            )
        }
    }

    override fun onDestroy() {
        permissionRecheckHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent, isColdStart = false)
    }

    /**
     * @return true if the intent was handled in a way that should abort further startup work.
     */
    private fun handleNfcIntent(intent: Intent?, isColdStart: Boolean): Boolean {
        val action = intent?.action ?: return false

        // Some NFC tags (notably URL/URI records on some OEM ROMs) arrive as a plain VIEW intent
        // rather than an NFC action. Treat these the same: never navigate anywhere from a scan.
        if (action == Intent.ACTION_VIEW) {
            val data = intent.dataString.orEmpty()
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Intercepted VIEW intent (likely NFC URL): data=$data")
            }
            Toast.makeText(this, "NFC link ignored.", Toast.LENGTH_SHORT).show()
            if (isColdStart) {
                finishAndRemoveTaskCompat()
                return true
            }
            return false
        }

        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return false

        // Only allow NFC to affect the app while a focus session is active.
        // When no session is active, we swallow the NFC intent so the user doesn't end up
        // in a random deep-link (e.g. URL NDEF records opening a browser) or a blank activity.
        if (!ActiveBlockingState.current.isBlocking) {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Ignoring NFC intent (no active session): action=$action")
            }
            Toast.makeText(this, "No active session. NFC scan ignored.", Toast.LENGTH_SHORT).show()
            if (isColdStart) {
                finishAndRemoveTaskCompat()
                return true
            }
            return false
        }

        // Phase 1: just log the tag UID. Phase 4 routes this into the
        // strategy coordinator so scanning a tag starts / stops a session.
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val uid = tag?.id?.joinToString(":") { "%02x".format(it) } ?: "unknown"
        Log.i(TAG, "NFC tag scanned (Phase 1 stub): uid=$uid, action=$action")
        return false
    }

    private fun handleNfcTagFromReaderMode(tag: Tag) {
        val uid = tag.id?.joinToString(":") { "%02x".format(it) } ?: "unknown"
        runOnUiThread {
            // After an in-app sheet successfully reads a tag, the user often keeps the phone
            // near it for another moment, which surfaces a duplicate scan to this callback.
            // Suppress the second toast within a short window.
            if (uid != "unknown" && uid == lastSwallowedTagUid) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Suppressing duplicate ReaderMode scan: uid=$uid")
                }
                return@runOnUiThread
            }
            lastSwallowedTagUid = uid
            permissionRecheckHandler.removeCallbacks(lastSwallowedTagClearer)
            permissionRecheckHandler.postDelayed(lastSwallowedTagClearer, DUPLICATE_TAG_WINDOW_MS)

            if (!ActiveBlockingState.current.isBlocking) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Ignoring NFC scan (no active session) via ReaderMode: uid=$uid")
                }
                Toast.makeText(this, "No active session. NFC scan ignored.", Toast.LENGTH_SHORT).show()
            } else {
                Log.i(TAG, "NFC tag scanned (ReaderMode): uid=$uid")
            }
        }
    }

    private fun finishAndRemoveTaskCompat() {
        finishAndRemoveTask()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DUPLICATE_TAG_WINDOW_MS = 2_500L

        /**
         * Reader-mode flags shared with [dev.ambitionsoftware.tymeboxed.ui.screens.home.NfcIosStyleScanSheet]
         * so every reader-mode entry point claims the tag identically.
         *
         * - `FLAG_READER_NFC_*`: enable all tag technologies our hardware supports.
         * - **`FLAG_READER_SKIP_NDEF_CHECK`** — critical: stops the Android NFC
         *   service from doing its own NDEF parse in parallel with our callback,
         *   which is what made HyperOS / MIUI / Realme / some OxygenOS builds
         *   pop the system "New tag collected — Empty tag" overlay on empty or
         *   unrecognised tags.
         * - `FLAG_READER_NO_PLATFORM_SOUNDS`: suppress the system NFC beep so
         *   the only feedback the user gets is the in-app confirmation.
         */
        const val NFC_READER_FLAGS: Int =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
