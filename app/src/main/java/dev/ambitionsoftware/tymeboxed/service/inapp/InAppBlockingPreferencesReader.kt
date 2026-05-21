package dev.ambitionsoftware.tymeboxed.service.inapp

import android.content.Context

/**
 * Synchronous read of in-app toggles (AccessibilityService has no coroutines).
 * Stored in a dedicated [SharedPreferences] file edited from [InAppBlockingViewModel] / system UI.
 */
object InAppBlockingPreferencesReader {
    private const val PREFS = "tymeboxed_inapp_toggles"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context, key: String, default: Boolean = false): Boolean =
        prefs(c).getBoolean(key, default)
}
