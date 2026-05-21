package dev.ambitionsoftware.tymeboxed.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.net.toUri
import java.util.ArrayDeque
import java.util.Locale

/**
 * Browser detection + URL-bar scraping for per-website blocking, adapted from
 * Switchly's [at.saltyy.switchly.blocking.SwitchlyAccessibilityService].
 */
object WebsiteBlockingSupport {

    private const val MAX_NODE_SCAN_COUNT = 120
    private const val MAX_NODE_SCAN_DEPTH = 12

    fun isBrowserPackage(pkg: String): Boolean {
        return pkg == "com.android.chrome" ||
            pkg == "com.google.android.chrome" ||
            pkg == "com.brave.browser" ||
            pkg == "com.microsoft.emmx" ||
            pkg == "com.opera.browser" ||
            pkg == "com.opera.browser.beta" ||
            pkg == "com.opera.mini.native" ||
            pkg == "com.sec.android.app.sbrowser" ||
            pkg == "com.sec.android.app.sbrowser.beta" ||
            pkg == "org.mozilla.firefox" ||
            pkg == "org.mozilla.firefox_beta" ||
            pkg == "org.mozilla.fennec_fdroid" ||
            pkg == "org.mozilla.focus" ||
            pkg == "org.mozilla.fenix" ||
            pkg == "com.kiwibrowser.browser" ||
            pkg == "com.vivaldi.browser" ||
            pkg == "com.duckduckgo.mobile.android" ||
            pkg == "com.google.android.apps.chrome" ||
            pkg == "com.chrome.beta" ||
            pkg == "com.chrome.dev"
    }

    private fun isFirefoxFamily(pkg: String): Boolean = pkg.startsWith("org.mozilla.")

    /** Chromium browsers need a short confirmation window so we do not block on autocomplete flicker. */
    fun requiresStableDomainConfirmation(pkg: String): Boolean = !isFirefoxFamily(pkg)

    private fun browserUrlViewIds(pkg: String): List<String> {
        return when (pkg) {
            "com.android.chrome", "com.google.android.chrome" -> listOf(
                "com.android.chrome:id/url_bar",
                "com.google.android.chrome:id/url_bar",
            )
            "com.brave.browser" -> listOf(
                "com.brave.browser:id/url_bar",
                "com.android.chrome:id/url_bar",
            )
            "com.microsoft.emmx" -> listOf(
                "com.microsoft.emmx:id/url_bar",
                "com.android.chrome:id/url_bar",
            )
            "com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native" -> listOf(
                "com.opera.browser:id/url_field",
                "com.opera.browser:id/url_bar",
                "com.opera.browser:id/address_bar",
            )
            "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta" -> listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/location_bar",
            )
            "org.mozilla.firefox" -> listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view",
                "org.mozilla.firefox:id/mozac_browser_toolbar_display_url_view",
                "org.mozilla.firefox:id/mozac_browser_toolbar_origin_view",
            )
            "org.mozilla.firefox_beta" -> listOf(
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_edit_url_view",
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_display_url_view",
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_origin_view",
            )
            "org.mozilla.fennec_fdroid" -> listOf(
                "org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_url_view",
                "org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_edit_url_view",
                "org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_display_url_view",
                "org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_origin_view",
            )
            "org.mozilla.focus" -> listOf("org.mozilla.focus:id/urlInputView")
            "org.mozilla.fenix" -> listOf(
                "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
                "org.mozilla.fenix:id/mozac_browser_toolbar_edit_url_view",
                "org.mozilla.fenix:id/mozac_browser_toolbar_display_url_view",
                "org.mozilla.fenix:id/mozac_browser_toolbar_origin_view",
            )
            "com.kiwibrowser.browser" -> listOf(
                "com.kiwibrowser.browser:id/url_bar",
                "com.android.chrome:id/url_bar",
            )
            "com.vivaldi.browser" -> listOf(
                "com.vivaldi.browser:id/url_bar",
                "com.android.chrome:id/url_bar",
            )
            "com.duckduckgo.mobile.android" -> listOf(
                "com.duckduckgo.mobile.android:id/omnibarTextInput",
            )
            "com.google.android.apps.chrome" -> listOf("com.android.chrome:id/url_bar")
            "com.chrome.beta" -> listOf(
                "com.chrome.beta:id/url_bar",
                "com.android.chrome:id/url_bar",
            )
            "com.chrome.dev" -> listOf(
                "com.chrome.dev:id/url_bar",
                "com.android.chrome:id/url_bar",
            )
            else -> emptyList()
        }
    }

    private fun findBrowserUrlNode(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = browserUrlViewIds(pkg)
        for (id in ids) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull() ?: emptyList()
            val copy = try {
                val best = nodes.firstOrNull {
                    !it.text?.toString().isNullOrBlank() ||
                        !it.contentDescription?.toString().isNullOrBlank()
                } ?: nodes.firstOrNull()
                best?.let { AccessibilityNodeInfo.obtain(it) }
            } finally {
                nodes.forEach { runCatching { it.recycle() } }
            }
            if (copy != null) return copy
        }

        if (pkg.startsWith("org.mozilla.")) {
            return findAnyNode(root) { node ->
                val vid = node.viewIdResourceName?.lowercase(Locale.getDefault()).orEmpty()
                if (!(vid.contains("mozac") || vid.contains("toolbar") || vid.contains("url") || vid.contains("origin"))) {
                    return@findAnyNode false
                }
                val t = node.text?.toString().orEmpty()
                val cd = node.contentDescription?.toString().orEmpty()
                val c = (t + " " + cd).trim()
                c.contains(".") || c.contains("http", ignoreCase = true)
            }
        }

        // Chromium / WebView shells: view IDs change between releases; scan the toolbar.
        if (isBrowserPackage(pkg)) {
            return findAnyNode(root) { node ->
                val vid = node.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
                val t = node.text?.toString().orEmpty()
                val cd = node.contentDescription?.toString().orEmpty()
                val c = "$t $cd".trim()
                if (c.isBlank()) return@findAnyNode false
                val idHints = vid.contains("url_bar") || vid.contains("location_bar") ||
                    vid.contains("omnibox") || vid.contains("address_bar") ||
                    vid.contains("url_field") ||
                    (vid.contains("toolbar") && vid.contains("url"))
                val looksLikeHost = c.contains(".") || c.contains("http", ignoreCase = true) ||
                    c.contains("localhost", ignoreCase = true)
                idHints && looksLikeHost
            }
        }

        return null
    }

    private data class WorkItem(val node: AccessibilityNodeInfo, val depth: Int, val owned: Boolean)

    private fun findAnyNode(root: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val stack = ArrayDeque<WorkItem>()
        stack.addLast(WorkItem(root, 0, false))
        var visited = 0

        while (stack.isNotEmpty() && visited < MAX_NODE_SCAN_COUNT) {
            val item = stack.removeLast()
            val current = item.node
            try {
                visited++
                if (pred(current)) {
                    return AccessibilityNodeInfo.obtain(current)
                }

                if (item.depth >= MAX_NODE_SCAN_DEPTH) continue

                val childCount = runCatching { current.childCount }.getOrDefault(0)
                for (i in childCount - 1 downTo 0) {
                    if (visited + stack.size >= MAX_NODE_SCAN_COUNT) break
                    val child = runCatching { current.getChild(i) }.getOrNull() ?: continue
                    stack.addLast(WorkItem(child, item.depth + 1, true))
                }
            } finally {
                if (item.owned) runCatching { current.recycle() }
            }
        }
        return null
    }

    fun domainFromText(raw: String): String? {
        val s0 = raw.trim()
        if (s0.isBlank()) return null

        val token = s0.split(" ", "›", "·", "|", "—", " ")
            .firstOrNull { it.contains(".") } ?: s0
        val s = token.trim()

        val rx = Regex("""(?i)(?:https?://)?([a-z0-9.-]+\.[a-z]{2,})(?::\d+)?""")
        val m = rx.find(s)
        val host = m?.groupValues?.getOrNull(1)
        if (!host.isNullOrBlank()) return DomainBlocking.normalize(host)

        val withScheme = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
        val parsed = runCatching { withScheme.toUri().host }.getOrNull() ?: return null
        return DomainBlocking.normalize(parsed)
    }

    private fun firefoxEventDomainSignal(event: AccessibilityEvent?): String? {
        if (event == null) return null

        event.text?.forEach { part ->
            val raw = part?.toString()?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                domainFromText(raw)?.let { return it }
            }
        }

        val sourceCopy = runCatching { event.source?.let { AccessibilityNodeInfo.obtain(it) } }.getOrNull()
        try {
            if (sourceCopy != null) {
                val direct = sequenceOf(
                    sourceCopy.text?.toString(),
                    sourceCopy.contentDescription?.toString(),
                )
                for (raw in direct) {
                    val value = raw?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        domainFromText(value)?.let { return it }
                    }
                }

                val parent = runCatching { sourceCopy.parent?.let { AccessibilityNodeInfo.obtain(it) } }.getOrNull()
                try {
                    if (parent != null) {
                        val parentDirect = sequenceOf(
                            parent.text?.toString(),
                            parent.contentDescription?.toString(),
                        )
                        for (raw in parentDirect) {
                            val value = raw?.trim().orEmpty()
                            if (value.isNotEmpty()) {
                                domainFromText(value)?.let { return it }
                            }
                        }

                        val childCount = runCatching { parent.childCount }.getOrDefault(0)
                        for (i in 0 until childCount) {
                            val child = runCatching { parent.getChild(i) }.getOrNull() ?: continue
                            try {
                                val childDirect = sequenceOf(
                                    child.text?.toString(),
                                    child.contentDescription?.toString(),
                                )
                                for (raw in childDirect) {
                                    val value = raw?.trim().orEmpty()
                                    if (value.isNotEmpty()) {
                                        domainFromText(value)?.let { return it }
                                    }
                                }
                            } finally {
                                runCatching { child.recycle() }
                            }
                        }
                    }
                } finally {
                    if (parent != null) runCatching { parent.recycle() }
                }
            }
        } finally {
            if (sourceCopy != null) runCatching { sourceCopy.recycle() }
        }

        return null
    }

    private fun findEditableUrlText(node: AccessibilityNodeInfo): String? {
        val stack = ArrayDeque<WorkItem>()
        stack.addLast(WorkItem(node, 0, false))
        var visited = 0

        while (stack.isNotEmpty() && visited < MAX_NODE_SCAN_COUNT) {
            val item = stack.removeLast()
            val current = item.node
            try {
                visited++

                val t = current.text?.toString()?.trim()
                val cd = current.contentDescription?.toString()?.trim()
                val vid = current.viewIdResourceName?.lowercase(Locale.getDefault()).orEmpty()
                val cls = current.className?.toString().orEmpty()

                val candidate = when {
                    !t.isNullOrBlank() -> t
                    !cd.isNullOrBlank() -> cd
                    else -> null
                }

                val looksLikeUrl = candidate != null && candidate.length in 4..300 && candidate.contains(".")
                val idHints = vid.contains("url") || vid.contains("address") || vid.contains("omnibox") ||
                    vid.contains("location") || vid.contains("toolbar")
                val isEdit = current.isEditable || cls.contains("EditText", ignoreCase = true)
                val editingNow = current.isFocused || current.isAccessibilityFocused

                if (candidate != null && looksLikeUrl && (idHints || isEdit) && !editingNow) {
                    return candidate
                }

                if (item.depth >= MAX_NODE_SCAN_DEPTH) continue

                val childCount = runCatching { current.childCount }.getOrDefault(0)
                for (i in childCount - 1 downTo 0) {
                    if (visited + stack.size >= MAX_NODE_SCAN_COUNT) break
                    val child = runCatching { current.getChild(i) }.getOrNull() ?: continue
                    stack.addLast(WorkItem(child, item.depth + 1, true))
                }
            } finally {
                if (item.owned) runCatching { current.recycle() }
            }
        }
        return null
    }

    private fun isBrowserAddressEditingImpl(
        root: AccessibilityNodeInfo,
        pkg: String,
        event: AccessibilityEvent?,
    ): Boolean {
        val node = findBrowserUrlNode(root, pkg) ?: return false
        try {
            val cls = node.className?.toString().orEmpty()
            val isEdit = node.isEditable || cls.contains("EditText", ignoreCase = true)
            val focused = node.isFocused || node.isAccessibilityFocused

            val type = event?.eventType ?: 0
            val inputEvent = type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_VIEW_CLICKED

            if (isFirefoxFamily(pkg)) {
                val hasUrlSignal = firefoxEventDomainSignal(event) != null ||
                    domainFromText(node.text?.toString().orEmpty()) != null ||
                    domainFromText(node.contentDescription?.toString().orEmpty()) != null
                return isEdit && (focused || inputEvent) && !hasUrlSignal
            }

            if (focused) return true
            return isEdit && inputEvent
        } finally {
            runCatching { node.recycle() }
        }
    }

    fun tryExtractDomainFromBrowser(
        root: AccessibilityNodeInfo?,
        pkg: String,
        event: AccessibilityEvent? = null,
    ): String? {
        if (root == null) return null

        if (isFirefoxFamily(pkg)) {
            firefoxEventDomainSignal(event)?.let { return it }
        }

        val urlNode = findBrowserUrlNode(root, pkg)
        try {
            val t = urlNode?.text?.toString()?.trim().orEmpty()
            if (t.isNotBlank()) {
                domainFromText(t)?.let { return it }
            }

            val cd = urlNode?.contentDescription?.toString()?.trim().orEmpty()
            if (cd.isNotBlank()) {
                domainFromText(cd)?.let { return it }
            }
        } finally {
            if (urlNode != null) runCatching { urlNode.recycle() }
        }

        val candidate = findEditableUrlText(root)
        return candidate?.let { domainFromText(it) }
    }

    fun shouldIgnoreEventForChromiumUrlField(pkg: String, event: AccessibilityEvent?): Boolean {
        if (isFirefoxFamily(pkg)) return false
        val et = event?.eventType ?: 0
        return et == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || et == AccessibilityEvent.TYPE_VIEW_FOCUSED
    }

    fun isBrowserAddressEditing(root: AccessibilityNodeInfo?, pkg: String, event: AccessibilityEvent?): Boolean {
        if (root == null) return false
        return isBrowserAddressEditingImpl(root, pkg, event)
    }
}
