package dev.ambitionsoftware.tymeboxed.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps low-level network failures to copy safe to show in the UI.
 * API / validation messages are left unchanged via [toDisplayMessage].
 */
fun Throwable.networkDisplayMessageOrNull(): String? {
    var t: Throwable? = this
    while (t != null) {
        when (t) {
            is SocketTimeoutException ->
                return "Server didn't respond in time. Please try again."
            is UnknownHostException ->
                return "Couldn't connect. Check your internet connection and try again."
            is ConnectException ->
                return "Couldn't connect to the server. Please try again."
            is SSLException ->
                return "Secure connection failed. Please try again."
        }
        t = t.cause
    }
    val msg = message?.trim().orEmpty()
    if (msg.isEmpty()) return null
    return when {
        msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("No address associated with hostname", ignoreCase = true) ->
            "Couldn't connect. Check your internet connection and try again."
        msg.contains("failed to connect", ignoreCase = true) ||
            msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ->
            "Couldn't connect to the server. Please try again."
        msg.contains("timeout", ignoreCase = true) ->
            "Server didn't respond in time. Please try again."
        else -> null
    }
}

fun Throwable.toDisplayMessage(fallback: String): String =
    networkDisplayMessageOrNull() ?: message?.takeIf { it.isNotBlank() } ?: fallback
