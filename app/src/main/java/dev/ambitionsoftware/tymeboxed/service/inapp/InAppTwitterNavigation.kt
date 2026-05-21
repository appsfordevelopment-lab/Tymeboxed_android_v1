package dev.ambitionsoftware.tymeboxed.service.inapp

import android.view.accessibility.AccessibilityNodeInfo

/**
 * In-app navigation for X (Twitter) surfaces, adapted from
 * [Switchly](https://gitlab.com/Saltyy/switchly-public) `tryNavigateTwitterToHome` /
 * `tryNavigateTwitterToNotifications`.
 */
internal object InAppTwitterNavigation {

    fun tryNavigateToHome(root: AccessibilityNodeInfo?): Boolean {
        val r = root ?: return false
        return InAppA11yNodes.tryClickAnyLabelInPackage(
            r,
            InAppToggleKeys.X_TWITTER,
            listOf("home", "home timeline", "startseite"),
        )
    }

    fun tryNavigateToNotifications(root: AccessibilityNodeInfo?): Boolean {
        val r = root ?: return false
        return InAppA11yNodes.tryClickAnyLabelInPackage(
            r,
            InAppToggleKeys.X_TWITTER,
            listOf("notifications", "notification", "benachrichtigungen"),
        )
    }
}
