package dev.ambitionsoftware.tymeboxed.util

import android.util.Log
import dev.ambitionsoftware.tymeboxed.BuildConfig

/**
 * Paper-thin wrapper around `android.util.Log`. Keeps a consistent TAG
 * prefix across the app and silences `d()` / `v()` outside debug builds.
 * Swap in Timber or a structured logger later without touching call sites.
 */
object Logger {

    private const val PREFIX = "TymeBoxed"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d("$PREFIX/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w("$PREFIX/$tag", message, throwable)
        else Log.w("$PREFIX/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e("$PREFIX/$tag", message, throwable)
        else Log.e("$PREFIX/$tag", message)
    }
}
