package dev.ambitionsoftware.tymeboxed.service

import java.net.IDN
import java.util.Locale

/**
 * Normalizes hosts and tests subdomain matches — same rules as Switchly's
 * [DomainBlockStore], without SharedPreferences.
 */
object DomainBlocking {

    fun normalize(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.isBlank()) return null

        s = s.lowercase(Locale.ROOT)

        if (s.startsWith("*.")) s = s.removePrefix("*.")

        val schemeIdx = s.indexOf("://")
        if (schemeIdx >= 0) s = s.substring(schemeIdx + 3)

        val endIdx = listOf(
            s.indexOf('/'),
            s.indexOf('?'),
            s.indexOf('#'),
            s.indexOf(' '),
        ).filter { it >= 0 }.minOrNull() ?: -1
        if (endIdx >= 0) s = s.substring(0, endIdx)

        val at = s.lastIndexOf('@')
        if (at >= 0 && at < s.length - 1) s = s.substring(at + 1)

        s = s.trim().trimEnd('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")

        val colon = s.lastIndexOf(':')
        if (colon > 0) {
            val tail = s.substring(colon + 1)
            if (tail.all { it.isDigit() }) s = s.substring(0, colon)
        }

        while (".." in s) s = s.replace("..", ".")

        if (s.isBlank() || s.startsWith(".") || s.endsWith(".")) return null

        val ascii = runCatching { IDN.toASCII(s, IDN.ALLOW_UNASSIGNED) }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?: return null

        if (!ascii.contains('.')) return null
        if (ascii.length !in 3..253) return null
        if (!ascii.matches(Regex("^[a-z0-9][a-z0-9.-]*[a-z0-9]$"))) return null

        return ascii
    }

    fun matches(host: String, domain: String): Boolean {
        val h = normalize(host) ?: return false
        val d = normalize(domain) ?: return false
        return h == d || h.endsWith(".$d")
    }
}
