package dev.ambitionsoftware.tymeboxed.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ambitionsoftware.tymeboxed.admin.DeviceAdminSupport
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore-backed preferences — replaces iOS @AppStorage / UserDefaults. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tymeboxed_prefs",
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeColorName: Flow<String> =
        context.dataStore.data.map { it[KEY_THEME] ?: DEFAULT_THEME_NAME }

    val introCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_INTRO_COMPLETED] ?: false }

    /** When false, the home Activity heatmap is hidden. */
    val activityChartVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ACTIVITY_CHART_VISIBLE] ?: true }

    /** One of [ActivityChartType] string constants. */
    val activityChartType: Flow<String> =
        context.dataStore.data.map { it[KEY_ACTIVITY_CHART_TYPE] ?: ActivityChartType.MONTHLY }

    /**
     * Raw Strict-mode pref — what the user toggled in Settings.
     *
     * Important: this can be `true` while the OS-level Device Admin is *not* active (e.g. the
     * user disabled it from system Settings → Device admin apps). Use [effectiveStrictModeEnabled]
     * everywhere strictness actually matters; this raw flow is only useful for the Settings
     * toggle's persisted state.
     */
    val strictModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_STRICT_MODE_ENABLED] ?: false }

    /**
     * Strict mode AS THE OS SEES IT — the conjunction of [strictModeEnabled] and
     * [DeviceAdminSupport.isAdminActive].
     *
     * The OS-generated *"Can't uninstall active device admin app"* toast only fires when the
     * admin is active. If we treated the raw pref as truth, the UI would happily report
     * "Strict mode ON" while the user can in fact uninstall the app — exactly the bug shown in
     * the screenshot.
     *
     * Note: this re-reads admin state on every DataStore emission, not on every collect, so it
     * doesn't poll. The accessibility tick + [TymeBoxedDeviceAdminReceiver.onDisabled] flip
     * the pref when admin drops, which triggers a fresh emission downstream.
     */
    val effectiveStrictModeEnabled: Flow<Boolean> =
        strictModeEnabled.map { pref -> pref && DeviceAdminSupport.isAdminActive(context) }

    /** Remaining emergency session stops (Foqos [StrategyManager] parity). */
    val emergencyUnblocksRemaining: Flow<Int> =
        context.dataStore.data.map { it[KEY_EMERGENCY_UNBLOCKS_REMAINING] ?: DEFAULT_EMERGENCY_UNBLOCKS }

    suspend fun setThemeColorName(name: String) {
        context.dataStore.edit { it[KEY_THEME] = name }
    }

    suspend fun setIntroCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_INTRO_COMPLETED] = completed }
    }

    suspend fun setActivityChartVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_ACTIVITY_CHART_VISIBLE] = visible }
    }

    suspend fun setActivityChartType(type: String) {
        context.dataStore.edit { it[KEY_ACTIVITY_CHART_TYPE] = type }
    }

    suspend fun setStrictModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STRICT_MODE_ENABLED] = enabled }
    }

    /**
     * Refills emergency unblocks when the reset period has elapsed (default 4 weeks).
     * Mirrors Foqos `checkAndResetEmergencyUnblocks()`.
     */
    suspend fun checkAndResetEmergencyUnblocks() {
        context.dataStore.edit { applyEmergencyUnblockResetIfNeeded(it) }
    }

    /**
     * Consumes one emergency unblock if any remain. Returns false when exhausted.
     *
     * Reset check and decrement run in a single [edit] transaction so concurrent
     * callers cannot both read the same remaining count and overwrite each other.
     */
    suspend fun tryConsumeEmergencyUnblock(): Boolean {
        var consumed = false
        context.dataStore.edit { prefs ->
            applyEmergencyUnblockResetIfNeeded(prefs)
            val remaining = prefs[KEY_EMERGENCY_UNBLOCKS_REMAINING] ?: DEFAULT_EMERGENCY_UNBLOCKS
            if (remaining > 0) {
                prefs[KEY_EMERGENCY_UNBLOCKS_REMAINING] = remaining - 1
                consumed = true
            }
        }
        return consumed
    }

    /**
     * Period refill for emergency unblocks — must run inside a DataStore [edit] block
     * so it composes atomically with [tryConsumeEmergencyUnblock].
     */
    private fun applyEmergencyUnblockResetIfNeeded(prefs: MutablePreferences) {
        val last = prefs[KEY_LAST_EMERGENCY_RESET_MS] ?: 0L
        if (last == 0L) {
            prefs[KEY_LAST_EMERGENCY_RESET_MS] = System.currentTimeMillis()
            if (prefs[KEY_EMERGENCY_UNBLOCKS_REMAINING] == null) {
                prefs[KEY_EMERGENCY_UNBLOCKS_REMAINING] = DEFAULT_EMERGENCY_UNBLOCKS
            }
            return
        }
        val weeks = prefs[KEY_EMERGENCY_RESET_WEEKS] ?: DEFAULT_EMERGENCY_RESET_WEEKS
        val periodMs = weeks * 7L * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - last >= periodMs) {
            prefs[KEY_EMERGENCY_UNBLOCKS_REMAINING] = DEFAULT_EMERGENCY_UNBLOCKS
            prefs[KEY_LAST_EMERGENCY_RESET_MS] = System.currentTimeMillis()
        }
    }

    /**
     * One-time safety migration.
     *
     * Ensures Strict mode starts OFF by default after app updates, even if a previous build
     * accidentally left it enabled. Users can always re-enable it from Settings.
     */
    suspend fun ensureStrictModeDefaultsOffOnce() {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_STRICT_DEFAULT_OFF_MIGRATED] == true) return@edit
            prefs[KEY_STRICT_MODE_ENABLED] = false
            prefs[KEY_STRICT_DEFAULT_OFF_MIGRATED] = true
        }
    }

    /**
     * Whether this tag UID (see [dev.ambitionsoftware.tymeboxed.nfc.nfcUidHexCacheKey]) was already
     * confirmed via [dev.ambitionsoftware.tymeboxed.data.repository.AuthRepository.verifyNfcTag].
     *
     * Audit #28: the cache only needs identity (have we seen this UID before?), so
     * we store an HMAC/SHA-256 of the UID instead of the raw hex. An attacker
     * with rooted access to the DataStore file therefore cannot extract the
     * physical NFC tag identifiers — exactly the property that was missing in
     * the previous plaintext layout. Any legacy non-hash entries are dropped
     * on first read so old caches force a one-time server re-verify.
     */
    suspend fun isNfcTagVerifiedInCache(hexKey: String): Boolean {
        val needle = hashNfcCacheKey(hexKey)
        val set = context.dataStore.data
            .map { it[KEY_VERIFIED_NFC_TAG_HEX_KEYS] ?: emptySet() }
            .first()
        return needle in set
    }

    suspend fun putVerifiedNfcTagInCache(hexKey: String) {
        val hashed = hashNfcCacheKey(hexKey)
        context.dataStore.edit { prefs ->
            val cur = prefs[KEY_VERIFIED_NFC_TAG_HEX_KEYS] ?: emptySet()
            // Drop any legacy non-hash entries while we're at it (one-time migration).
            val pruned = cur.filter { isLikelyHash(it) }.toSet()
            prefs[KEY_VERIFIED_NFC_TAG_HEX_KEYS] = pruned + hashed
        }
    }

    /** Clears NFC tag verification cache (device ↔ server lookup shortcuts). */
    suspend fun clearVerifiedNfcTagCache() {
        context.dataStore.edit { it.remove(KEY_VERIFIED_NFC_TAG_HEX_KEYS) }
    }

    /**
     * Deterministic one-way derivation. We salt with a build-private constant
     * so the hashes are not cross-app-portable (i.e. you can't compare with a
     * raw SHA-256 from another app). Salt is not a secret — its purpose is to
     * keep the per-tag value app-scoped.
     */
    private fun hashNfcCacheKey(rawHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(NFC_CACHE_SALT.toByteArray(Charsets.UTF_8))
        val bytes = md.digest(rawHex.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) append("%02x".format(b))
        }
    }

    private fun isLikelyHash(s: String): Boolean =
        s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        /** Matches the iOS default in `ThemeManager.swift`. */
        const val DEFAULT_THEME_NAME = "Warm Sandstone"

        private val KEY_THEME = stringPreferencesKey("theme_color_name")
        private val KEY_INTRO_COMPLETED = booleanPreferencesKey("intro_completed")
        private val KEY_ACTIVITY_CHART_VISIBLE = booleanPreferencesKey("activity_chart_visible")
        private val KEY_ACTIVITY_CHART_TYPE = stringPreferencesKey("activity_chart_type")
        private val KEY_STRICT_MODE_ENABLED = booleanPreferencesKey("strict_mode_enabled")
        private val KEY_STRICT_DEFAULT_OFF_MIGRATED =
            booleanPreferencesKey("strict_mode_default_off_migrated")
        private val KEY_VERIFIED_NFC_TAG_HEX_KEYS =
            stringSetPreferencesKey("verified_nfc_tag_hex_keys")
        private val KEY_EMERGENCY_UNBLOCKS_REMAINING =
            intPreferencesKey("emergency_unblocks_remaining")
        private val KEY_EMERGENCY_RESET_WEEKS =
            intPreferencesKey("emergency_unblocks_reset_period_weeks")
        private val KEY_LAST_EMERGENCY_RESET_MS =
            longPreferencesKey("last_emergency_unblocks_reset_date")

        const val DEFAULT_EMERGENCY_UNBLOCKS = 3
        private const val DEFAULT_EMERGENCY_RESET_WEEKS = 4

        /**
         * Salt for the NFC cache hash. Not security-critical (lookup-only);
         * its job is to keep the hashes app-scoped so they aren't directly
         * comparable to a generic SHA-256 of the UID.
         */
        private const val NFC_CACHE_SALT = "tymeboxed.nfc.cache.v1"
    }
}

/** Persisted values for the home Activity chart (Manage sheet). */
object ActivityChartType {
    const val FOUR_WEEK = "four_week"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
}
