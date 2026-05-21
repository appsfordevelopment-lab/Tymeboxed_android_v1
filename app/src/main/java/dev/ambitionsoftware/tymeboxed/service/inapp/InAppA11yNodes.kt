package dev.ambitionsoftware.tymeboxed.service.inapp

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

/**
 * View-tree walks adapted from Switchly
 * [SwitchlyAccessibilityService](https://gitlab.com/Saltyy/switchly-public).
 */
internal object InAppA11yNodes {
    /** YouTube/Compose trees can be deep; too-small scans miss the search field entirely. */
    private const val MAX_NODE_SCAN_COUNT = 280
    private const val MAX_NODE_SCAN_DEPTH = 14

    private data class WorkItem(val node: AccessibilityNodeInfo, val depth: Int, val owned: Boolean)

    fun findAnyNode(
        root: AccessibilityNodeInfo,
        maxNodes: Int = MAX_NODE_SCAN_COUNT,
        maxDepth: Int = MAX_NODE_SCAN_DEPTH,
        pred: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<WorkItem>()
        stack.addLast(WorkItem(root, 0, false))
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val item = stack.removeLast()
            val current = item.node
            try {
                visited++
                if (pred(current)) return AccessibilityNodeInfo.obtain(current)
                if (item.depth >= maxDepth) continue
                val childCount = runCatching { current.childCount }.getOrDefault(0)
                for (i in childCount - 1 downTo 0) {
                    if (visited + stack.size >= maxNodes) break
                    val child = runCatching { current.getChild(i) }.getOrNull() ?: continue
                    stack.addLast(WorkItem(child, item.depth + 1, true))
                }
            } finally {
                if (item.owned) runCatching { current.recycle() }
            }
        }
        return null
    }

    fun nodeTextOrDesc(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val t = node.text?.toString().orEmpty()
        val cd = node.contentDescription?.toString().orEmpty()
        return listOf(t, cd).filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase(Locale.getDefault())
    }

    fun nodeTextMatches(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val found = findAnyNode(root) { node ->
            val t = node.text?.toString()?.lowercase(Locale.getDefault())
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault())
            (t != null && n.any { t.contains(it) }) || (cd != null && n.any { cd.contains(it) })
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * Like [nodeTextMatches] but includes [AccessibilityNodeInfo.getHintText] so Compose /
     * Material placeholders (e.g. YouTube search) are visible before typed text exists.
     */
    fun nodeTextDescHintMatches(
        root: AccessibilityNodeInfo,
        needles: List<String>,
        maxNodes: Int = MAX_NODE_SCAN_COUNT,
        maxDepth: Int = MAX_NODE_SCAN_DEPTH,
    ): Boolean {
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val found = findAnyNode(
            root,
            maxNodes = maxNodes,
            maxDepth = maxDepth,
        ) { node ->
            val t = node.text?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val hint = runCatching { node.hintText?.toString()?.lowercase(Locale.getDefault()) }.getOrNull().orEmpty()
            val hay = listOf(t, cd, hint).filter { it.isNotBlank() }.joinToString(" ")
            hay.isNotBlank() && n.any { hay.contains(it) }
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * True when any node in [root] has a [AccessibilityNodeInfo.getViewIdResourceName]
     * containing one of [tokens] (case-insensitive substring).
     */
    fun nodeViewIdMatches(
        root: AccessibilityNodeInfo,
        tokens: List<String>,
        maxNodes: Int = MAX_NODE_SCAN_COUNT,
        maxDepth: Int = MAX_NODE_SCAN_DEPTH,
    ): Boolean {
        if (tokens.isEmpty()) return false
        val lc = tokens.map { it.lowercase(Locale.getDefault()) }.filter { it.isNotBlank() }
        if (lc.isEmpty()) return false
        val found = findAnyNode(
            root,
            maxNodes = maxNodes,
            maxDepth = maxDepth,
        ) { node ->
            val raw = runCatching { node.viewIdResourceName?.toString() }.getOrNull() ?: return@findAnyNode false
            val id = raw.lowercase(Locale.getDefault())
            lc.any { id.contains(it) }
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    fun nodeSelfTextDescHintMatches(node: AccessibilityNodeInfo?, needles: List<String>): Boolean {
        if (node == null) return false
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val t = node.text?.toString()?.lowercase(Locale.getDefault()).orEmpty()
        val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault()).orEmpty()
        val hint = runCatching { node.hintText?.toString()?.lowercase(Locale.getDefault()) }.getOrNull().orEmpty()
        val hay = listOf(t, cd, hint).filter { it.isNotBlank() }.joinToString(" ")
        return hay.isNotBlank() && n.any { hay.contains(it) }
    }

    /**
     * Focused node matching needles — use with a **cold-start guard** so the home toolbar cannot
     * fire on launch when it briefly mirrors search strings.
     */
    fun hasFocusedSearchLikeNode(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        val found = findAnyNode(root) { node ->
            node.isFocused && nodeSelfTextDescHintMatches(node, needles)
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    fun isEditableLike(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString()?.lowercase(Locale.getDefault()).orEmpty()
        return node.isEditable ||
            cls.contains("edittext") ||
            cls.contains("edits") ||
            cls.contains("textfield") ||
            cls.contains("compose.ui.text")
    }

    fun hasFocusedEditableNode(root: AccessibilityNodeInfo): Boolean {
        val found = findAnyNode(root) { node -> node.isFocused && isEditableLike(node) }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * True when [node] or any ancestor (up to [maxDepth]) has a view id containing one of [tokens].
     */
    fun nodeOrAncestorViewIdMatches(
        node: AccessibilityNodeInfo,
        tokens: List<String>,
        maxDepth: Int = 14,
    ): Boolean {
        if (tokens.isEmpty()) return false
        val idLc = tokens.map { it.lowercase(Locale.getDefault()) }.filter { it.isNotBlank() }
        if (idLc.isEmpty()) return false
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        var depth = 0
        try {
            while (current != null && depth < maxDepth) {
                val viewId = runCatching { current.viewIdResourceName?.toString()?.lowercase(Locale.getDefault()) }
                    .getOrNull()
                if (viewId != null && idLc.any { viewId.contains(it) }) return true
                val parent = current.parent
                runCatching { current.recycle() }
                current = parent
                depth++
            }
            return false
        } finally {
            runCatching { current?.recycle() }
        }
    }

    /**
     * Comment composer on a YouTube watch page: focused field with comment view IDs (self or ancestor),
     * strong placeholder on focus (Compose often omits [AccessibilityNodeInfo.isEditable]), or editable
     * field with comment hints — avoids matching the passive "Comments" section title alone.
     */
    fun hasFocusedYouTubeCommentField(
        root: AccessibilityNodeInfo,
        strongPhrases: List<String>,
        viewIdTokens: List<String>,
        weakPhrases: List<String> = emptyList(),
    ): Boolean {
        val strongLc = strongPhrases.map { it.lowercase(Locale.getDefault()) }
        val weakLc = weakPhrases.map { it.lowercase(Locale.getDefault()) }
        val found = findAnyNode(root) { node ->
            if (!node.isFocused) return@findAnyNode false
            if (nodeOrAncestorViewIdMatches(node, viewIdTokens)) return@findAnyNode true
            val t = node.text?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val hint = runCatching { node.hintText?.toString()?.lowercase(Locale.getDefault()) }.getOrNull().orEmpty()
            val hay = listOf(t, cd, hint).filter { it.isNotBlank() }.joinToString(" ")
            if (hay.isNotBlank() && strongLc.any { hay.contains(it) }) return@findAnyNode true
            if (!isEditableLike(node)) return@findAnyNode false
            if (hay.isBlank()) return@findAnyNode false
            strongLc.any { hay.contains(it) } ||
                (weakLc.isNotEmpty() && weakLc.any { hay.contains(it) })
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * YouTube query field: focused + editable when the framework reports it; also strong phrase on
     * focus when Compose omits [AccessibilityNodeInfo.isEditable].
     */
    fun hasFocusedYouTubeSearchField(
        root: AccessibilityNodeInfo,
        needles: List<String>,
        strongPhrasesOnly: List<String>,
    ): Boolean {
        val needlesLc = needles.map { it.lowercase(Locale.getDefault()) }
        val strongLc = strongPhrasesOnly.map { it.lowercase(Locale.getDefault()) }
        val found = findAnyNode(root) { node ->
            if (!node.isFocused) return@findAnyNode false
            val t = node.text?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val hint = runCatching { node.hintText?.toString()?.lowercase(Locale.getDefault()) }.getOrNull().orEmpty()
            val hay = listOf(t, cd, hint).filter { it.isNotBlank() }.joinToString(" ")
            if (hay.isBlank()) return@findAnyNode false
            val matchesStrong = strongLc.any { hay.contains(it) }
            val matchesAny = needlesLc.any { hay.contains(it) }
            (node.isEditable && matchesAny) || matchesStrong
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    fun eventTextMatches(event: AccessibilityEvent?, needles: List<String>): Boolean {
        if (event == null) return false
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val cd = event.contentDescription?.toString()?.lowercase(Locale.getDefault())
        if (cd != null && n.any { cd.contains(it) }) return true
        for (cs in event.text.orEmpty()) {
            val t = cs?.toString()?.lowercase(Locale.getDefault()) ?: continue
            if (n.any { t.contains(it) }) return true
        }
        return false
    }

    /**
     * True if the event's source view has a [AccessibilityNodeInfo.getViewIdResourceName]
     * containing one of [tokens] (case-insensitive substring). The full ID looks like
     * `com.snapchat.android:id/map_tab`, so a token of `"map"` reliably matches the
     * Map nav button even when its contentDescription is null/empty.
     *
     * View IDs survive across locales and across most R8/minify configurations.
     */
    fun eventSourceIdMatches(event: AccessibilityEvent?, tokens: List<String>): Boolean {
        if (event == null || tokens.isEmpty()) return false
        val lc = tokens.map { it.lowercase(Locale.getDefault()) }.filter { it.isNotBlank() }
        if (lc.isEmpty()) return false
        val src = runCatching { event.source }.getOrNull() ?: return false
        return try {
            val raw = runCatching { src.viewIdResourceName?.toString() }.getOrNull()
            val id = raw?.lowercase(Locale.getDefault()) ?: return false
            lc.any { id.contains(it) }
        } finally {
            runCatching { src.recycle() }
        }
    }

    fun hasSelectedLabel(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val found = findAnyNode(root) { node ->
            if (!node.isSelected) return@findAnyNode false
            val t = node.text?.toString()?.lowercase(Locale.getDefault())
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault())
            (t != null && n.any { t.contains(it) }) || (cd != null && n.any { cd.contains(it) })
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * Like [hasSelectedLabel] but also matches checked nav items (some apps use
     * [AccessibilityNodeInfo.isChecked] for the active bottom tab, not isSelected).
     */
    fun hasSelectedOrCheckedLabel(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val found = findAnyNode(root) { node ->
            if (!node.isSelected && !node.isChecked) return@findAnyNode false
            val t = node.text?.toString()?.lowercase(Locale.getDefault())
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault())
            (t != null && n.any { t.contains(it) }) || (cd != null && n.any { cd.contains(it) })
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    fun hasSelectedLabelInPackage(
        root: AccessibilityNodeInfo,
        needles: List<String>,
        pkg: String,
    ): Boolean {
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val targetPkg = pkg.lowercase(Locale.getDefault())
        val found = findAnyNode(root) { node ->
            if (!node.isSelected) return@findAnyNode false
            val nodePkg = node.packageName?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            if (nodePkg != targetPkg) return@findAnyNode false
            val t = node.text?.toString()?.lowercase(Locale.getDefault())
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault())
            (t != null && n.any { t.contains(it) }) || (cd != null && n.any { cd.contains(it) })
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * Like [hasSelectedLabelInPackage] but treats [AccessibilityNodeInfo.isChecked] as active
     * (Instagram bottom nav often uses checked, not selected).
     */
    fun hasSelectedOrCheckedLabelInPackage(
        root: AccessibilityNodeInfo,
        needles: List<String>,
        pkg: String,
    ): Boolean {
        val n = needles.map { it.lowercase(Locale.getDefault()) }
        val targetPkg = pkg.lowercase(Locale.getDefault())
        val found = findAnyNode(root) { node ->
            if (!node.isSelected && !node.isChecked) return@findAnyNode false
            val nodePkg = node.packageName?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            if (nodePkg != targetPkg) return@findAnyNode false
            val t = node.text?.toString()?.lowercase(Locale.getDefault())
            val cd = node.contentDescription?.toString()?.lowercase(Locale.getDefault())
            (t != null && n.any { t.contains(it) }) || (cd != null && n.any { cd.contains(it) })
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    /**
     * True if any node in [root]'s subtree has [AccessibilityNodeInfo.getClassName]
     * containing one of [tokens] (case-insensitive substring match).
     *
     * Used to identify a screen by the framework widget class it renders — e.g.
     * `MapView` / `GoogleMap` only exists on the actual Snap Map screen, not on
     * the small Map button in the persistent bottom nav strip. This is the
     * cross-locale, fragment-swap-resilient signal that pure text scanning
     * misses on apps like Snapchat which use a single host Activity for all tabs.
     */
    fun hasNodeWithClassNameContaining(root: AccessibilityNodeInfo, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val lc = tokens.map { it.lowercase(Locale.getDefault()) }.filter { it.isNotBlank() }
        if (lc.isEmpty()) return false
        val found = findAnyNode(root) { node ->
            val cls = node.className?.toString()?.lowercase(Locale.getDefault())
                ?: return@findAnyNode false
            lc.any { cls.contains(it) }
        }
        return try {
            found != null
        } finally {
            if (found != null) runCatching { found.recycle() }
        }
    }

    fun findNodeWithLabelInPackage(
        root: AccessibilityNodeInfo,
        labels: List<String>,
        pkg: String,
        requireUnselected: Boolean = false,
    ): AccessibilityNodeInfo? {
        val lowered = labels.map { it.lowercase(Locale.getDefault()) }
        val targetPkg = pkg.lowercase(Locale.getDefault())
        return findAnyNode(root) { node ->
            val nodePkg = node.packageName?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            if (nodePkg != targetPkg) return@findAnyNode false
            if (requireUnselected && node.isSelected) return@findAnyNode false
            val text = node.text?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()).orEmpty()
            lowered.any { text.contains(it) || desc.contains(it) }
        }
    }

    fun tryClickAnyLabelInPackage(
        root: AccessibilityNodeInfo?,
        pkg: String,
        labels: List<String>,
    ): Boolean {
        val r = root ?: return false
        val preferred = findNodeWithLabelInPackage(r, labels, pkg, requireUnselected = true)
        if (clickNodeOrClickableParent(preferred)) return true
        val fallback = findNodeWithLabelInPackage(r, labels, pkg, requireUnselected = false)
        return clickNodeOrClickableParent(fallback)
    }

    fun clickNodeOrClickableParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var hops = 0
        while (current != null && hops < 6) {
            val next = runCatching { current.parent }.getOrNull()
            val canClick =
                current.isClickable ||
                    (current.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } == true)
            if (canClick) {
                val clicked = runCatching { current.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    .getOrDefault(false)
                if (clicked) return true
            }
            current = next
            hops++
        }
        return false
    }
}
