package dev.ambitionsoftware.tymeboxed.nfc

import android.nfc.Tag
import java.util.LinkedHashSet
import java.util.Locale

fun normalizeNfcUid(bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    return bytes.joinToString(":") { b -> "%02x".format(b) }.lowercase(Locale.ROOT)
}

fun normalizeNfcUid(raw: String): String = raw.trim().lowercase(Locale.ROOT)

fun Tag.normalizedUid(): String? = normalizeNfcUid(id)

/**
 * Lowercase continuous hex digits from a scanned UID — stable key for local "already verified" cache.
 * Matches the hex core of [nfcTagIdLookupCandidates] without allocating the full candidate list.
 */
fun nfcUidHexCacheKey(uidFromReader: String): String? {
    val raw = uidFromReader.trim()
    if (raw.isEmpty() || raw.equals("unknown", ignoreCase = true)) return null
    val lower = raw.lowercase(Locale.ROOT)
    val hexOnly = buildString {
        for (c in lower) {
            when (c) {
                in '0'..'9', in 'a'..'f' -> append(c)
                ':', '-', ' ' -> Unit
                else -> Unit
            }
        }
    }
    return hexOnly.takeIf { it.isNotEmpty() }
}

/**
 * Possible `tagId` strings the API / admin tools may have stored, while the reader gives us one form
 * (usually colon-separated lower hex on Android). The DB is often seeded with **continuous** hex, or
 * upper case, or dash-separated — we try all until `/api/nfc/verify` returns `valid: true`.
 */
fun nfcTagIdLookupCandidates(uidFromReader: String): List<String> {
    val raw = uidFromReader.trim()
    if (raw.isEmpty() || raw.equals("unknown", ignoreCase = true)) return emptyList()
    val out = LinkedHashSet<String>()

    val lower = raw.lowercase(Locale.ROOT)
    val hexOnly = buildString {
        for (c in lower) {
            when (c) {
                in '0'..'9', in 'a'..'f' -> append(c)
                ':', '-', ' ' -> Unit
                else -> Unit
            }
        }
    }
    val withColons = when {
        lower.contains(':') ->
            lower.split(':', '-', ' ')
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(":")
        hexOnly.isNotEmpty() && hexOnly.length % 2 == 0 -> hexOnly.chunked(2).joinToString(":")
        else -> lower
    }
    val withDashes = withColons.replace(':', '-')
    val noSeparators = hexOnly

    out.add(raw)
    out.add(lower)
    out.add(withColons)
    if (noSeparators.isNotEmpty()) {
        out.add(noSeparators)
    }
    out.add(withDashes)
    if (noSeparators.isNotEmpty()) {
        out.add(noSeparators.uppercase(Locale.ROOT))
        out.add(withColons.uppercase(Locale.ROOT))
    }
    // Some deployments store LSB-first UID (rare) — only after normal forms
    if (withColons.contains(':')) {
        val parts = withColons.split(':').filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            out.add(parts.asReversed().joinToString(":"))
            if (noSeparators.length % 2 == 0) {
                out.add(
                    noSeparators.chunked(2).reversed().joinToString(""),
                )
            }
        }
    }
    return out.toList()
}
