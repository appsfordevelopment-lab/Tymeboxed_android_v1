package dev.ambitionsoftware.tymeboxed.util

import android.content.Context
import android.os.Build
import android.util.Log
import dev.ambitionsoftware.tymeboxed.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lightweight on-device crash recorder.
 *
 * Until a hosted crash backend (Crashlytics / Sentry) is wired up, this at
 * least gives support engineers something to ask the user to share. Crashes
 * are written to:
 *   `<filesDir>/crash_logs/crash-<timestamp>.log`
 *
 * The handler:
 *   - Chains to the previous [Thread.UncaughtExceptionHandler] so it does not
 *     suppress other instrumentation (LeakCanary, StrictMode, etc.).
 *   - Caps the directory at [MAX_FILES] entries so we never grow unbounded.
 *   - NEVER serializes JWTs, OAuth tokens, or anything in headers — only the
 *     stack trace + build metadata. Exception messages may still contain
 *     user-supplied strings; callers must avoid putting secrets in them.
 *
 * TODO(prod): replace with Firebase Crashlytics / Sentry once chosen.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR_NAME = "crash_logs"
    private const val MAX_FILES = 20

    fun install(appContext: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let the crash reporter mask the real crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeReport(appContext: Context, thread: Thread, throwable: Throwable) {
        val dir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
        pruneOldFiles(dir)

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val file = File(dir, "crash-$ts.log")
        PrintWriter(file.outputStream().bufferedWriter()).use { w ->
            w.println("Tyme Boxed crash report")
            w.println("Time (UTC): ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).run {
                timeZone = TimeZone.getTimeZone("UTC"); format(Date())
            }}")
            w.println("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            w.println("Build type: ${BuildConfig.BUILD_TYPE}, debug=${BuildConfig.DEBUG}")
            w.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            w.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            w.println("Thread: ${thread.name}")
            w.println()
            w.println("--- Stack trace ---")
            throwable.printStackTrace(w)
        }
        Log.e(TAG, "Crash report written to ${file.absolutePath}", throwable)
    }

    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") }
            ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach { runCatching { it.delete() } }
    }
}
