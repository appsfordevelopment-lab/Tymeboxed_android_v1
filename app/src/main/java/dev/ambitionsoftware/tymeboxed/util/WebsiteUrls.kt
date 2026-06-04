package dev.ambitionsoftware.tymeboxed.util

import dev.ambitionsoftware.tymeboxed.BuildConfig

/** Public marketing-site URLs opened in the browser (schedule help, settings, intro, shields). */
object WebsiteUrls {
    private val base: String = BuildConfig.TYMEBOXED_WEB_BASE_URL.trimEnd('/')

    val home: String = "$base/"
    val terms: String = "$base/terms"
    val privacy: String = "$base/privacy"
    val support: String = "$base/support"

    /** Schedule step “quick video” / Shortcuts help until a dedicated video URL exists. */
    val shortcutsHelp: String = support
}
