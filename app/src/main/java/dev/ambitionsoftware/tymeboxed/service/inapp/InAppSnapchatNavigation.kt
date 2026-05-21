package dev.ambitionsoftware.tymeboxed.service.inapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs

/**
 * In-app navigation for Snapchat surfaces (Map, Stories, etc.), adapted from
 * [Switchly](https://gitlab.com/Saltyy/switchly-public) `tryNavigateSnapchatToCamera`.
 *
 * Taps the Camera tab (or Chat fallback) so dismissing the block overlay lands on
 * Snapchat home instead of the blocked surface.
 */
internal object InAppSnapchatNavigation {

  private const val MAX_NODE_SCAN_COUNT = 280
  private const val MAX_NODE_SCAN_DEPTH = 14

  fun tryNavigateToCamera(service: AccessibilityService, root: AccessibilityNodeInfo?): Boolean {
    val r = root ?: return tapScreenAtRatio(service, 0.50f, 0.90f)
    val pkg = InAppToggleKeys.SNAPCHAT

    if (InAppA11yNodes.tryClickAnyLabelInPackage(r, pkg, listOf("camera", "capture"))) return true

    val cameraNode = findBestSnapchatBottomTabNode(service, r, 0.50f)
    try {
      if (InAppA11yNodes.clickNodeOrClickableParent(cameraNode)) return true
    } finally {
      runCatching { cameraNode?.recycle() }
    }

    if (tapScreenAtRatio(service, 0.50f, 0.90f) || tapScreenAtRatio(service, 0.50f, 0.86f)) return true

    if (InAppA11yNodes.tryClickAnyLabelInPackage(r, pkg, listOf("chat", "chats"))) return true

    val chatNode = findBestSnapchatBottomTabNode(service, r, 0.30f)
    try {
      if (InAppA11yNodes.clickNodeOrClickableParent(chatNode)) return true
    } finally {
      runCatching { chatNode?.recycle() }
    }

    return tapScreenAtRatio(service, 0.30f, 0.90f)
  }

  private fun findBestSnapchatBottomTabNode(
    service: AccessibilityService,
    root: AccessibilityNodeInfo,
    targetCenterRatio: Float,
  ): AccessibilityNodeInfo? {
    val generic = findBestBottomTabNodeInPackage(service, root, InAppToggleKeys.SNAPCHAT, targetCenterRatio)
      ?: return null
    val bounds = Rect()
    runCatching { generic.getBoundsInScreen(bounds) }
    if (bounds.isEmpty) return generic

    val width = service.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val height = service.resources.displayMetrics.heightPixels.coerceAtLeast(1)
    val widthRatio = bounds.width() / width.toFloat()
    val heightRatio = bounds.height() / height.toFloat()
    val likelyOversized = widthRatio > 0.42f || heightRatio > 0.20f
    if (likelyOversized) {
      runCatching { generic.recycle() }
      return null
    }
    return generic
  }

  /**
   * Returns an owned [AccessibilityNodeInfo] copy of the best bottom-tab match; caller must recycle.
   */
  private fun findBestBottomTabNodeInPackage(
    service: AccessibilityService,
    root: AccessibilityNodeInfo,
    pkg: String,
    targetCenterRatio: Float,
  ): AccessibilityNodeInfo? {
    data class WorkItem(val node: AccessibilityNodeInfo, val depth: Int, val owned: Boolean)

    val stack = ArrayDeque<WorkItem>()
    stack.addLast(WorkItem(root, 0, false))
    val width = service.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val height = service.resources.displayMetrics.heightPixels.coerceAtLeast(1)
    val targetPkg = pkg.lowercase(Locale.getDefault())
    var visited = 0
    var bestNode: AccessibilityNodeInfo? = null
    var bestScore = Float.MAX_VALUE

    while (stack.isNotEmpty() && visited < MAX_NODE_SCAN_COUNT) {
      val item = stack.removeLast()
      val current = item.node
      try {
        visited++

        val nodePkg = current.packageName?.toString()?.lowercase(Locale.getDefault()).orEmpty()
        val canClick =
          current.isClickable ||
            (current.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } == true)
        if (nodePkg == targetPkg && canClick) {
          val bounds = Rect()
          runCatching { current.getBoundsInScreen(bounds) }
          if (!bounds.isEmpty) {
            val centerX = bounds.exactCenterX() / width.toFloat()
            val centerY = bounds.exactCenterY() / height.toFloat()
            if (centerY >= 0.72f) {
              val score = abs(centerX - targetCenterRatio) + abs(centerY - 0.90f) * 0.35f
              if (score < bestScore) {
                runCatching { bestNode?.recycle() }
                bestNode = runCatching { AccessibilityNodeInfo.obtain(current) }.getOrNull()
                bestScore = score
              }
            }
          }
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

    return bestNode
  }

  private fun tapScreenAtRatio(
    service: AccessibilityService,
    centerXRatio: Float,
    centerYRatio: Float = 0.90f,
  ): Boolean {
    val width = service.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val height = service.resources.displayMetrics.heightPixels.coerceAtLeast(1)
    val x = (width * centerXRatio).coerceIn(1f, width.toFloat() - 1f)
    val y = (height * centerYRatio).coerceIn(1f, height.toFloat() - 1f)
    val path = Path().apply { moveTo(x, y) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
  }
}
