package dev.ambitionsoftware.tymeboxed.service

/**
 * Heuristic adult-site detection for browser URL blocking when a profile has
 * [blockAdultWebsites] enabled. Android has no system-wide adult filter like
 * iOS Screen Time; this mirrors the intent of Foqos's "Block Adult Websites"
 * toggle using hostname rules on-device.
 */
object AdultContentBlocking {

    private val labelKeywords = setOf(
        "porn",
        "xxx",
        "adult",
        "hentai",
        "nsfw",
        "xnxx",
        "redtube",
        "youporn",
        "chaturbate",
        "onlyfans",
        "camgirl",
        "sexcam",
    )

    private val knownRoots = setOf(
        "pornhub.com",
        "xvideos.com",
        "xhamster.com",
        "xnxx.com",
        "redtube.com",
        "youporn.com",
        "spankbang.com",
        "eporner.com",
        "chaturbate.com",
        "onlyfans.com",
        "brazzers.com",
        "bangbros.com",
    )

    fun matches(rawHost: String): Boolean {
        val host = DomainBlocking.normalize(rawHost) ?: return false
        if (host == "xxx" || host.endsWith(".xxx")) return true
        if (knownRoots.any { DomainBlocking.matches(host, it) }) return true
        val labels = host.split('.')
        return labels.any { label ->
            labelKeywords.any { kw -> label == kw || label.contains(kw) }
        }
    }
}
