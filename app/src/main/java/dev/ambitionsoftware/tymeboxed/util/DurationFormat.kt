package dev.ambitionsoftware.tymeboxed.util

import java.util.concurrent.TimeUnit

/**
 * Tiny duration formatter — turns a second count into "HH:MM:SS" for the
 * session notification and future timer UI. Matches the formatting the iOS
 * `ActiveSessionCard` uses for its elapsed-time label.
 */
object DurationFormat {

    /** "42:05" for < 1h, "01:12:30" once we cross the hour mark. */
    fun formatElapsed(seconds: Long): String {
        require(seconds >= 0) { "seconds must be non-negative, was $seconds" }
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }

    /** Human-readable: "1 hour 30 minutes", "25 minutes", "45 seconds". */
    fun formatHumanReadable(seconds: Long): String {
        if (seconds < 60) return "$seconds seconds"
        val minutes = seconds / 60
        if (minutes < 60) return "$minutes minutes"
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return if (remMinutes == 0L) {
            "$hours ${if (hours == 1L) "hour" else "hours"}"
        } else {
            "$hours ${if (hours == 1L) "hour" else "hours"} $remMinutes minutes"
        }
    }
}
