package dev.ambitionsoftware.tymeboxed.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Android counterpart to iOS [ManagedSettingsStore.application.denyAppRemoval] in
 * [AppBlockerUtil.swift] — there is no supported OS API for third‑party “deny uninstall”, so while
 * strict mode is on we scan accessibility trees from Settings / installers / Play **and** launcher &
 * System UI sheets (many OEMs surface uninstall there). For **system Settings** we only intercept when
 * copy suggests uninstall/removal (not every screen that merely shows the app name). Best‑effort only:
 * disabling accessibility,
 * ADB, safe mode, or a ROM without readable labels can still remove the app.
 */
object StrictUninstallInterception {

    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile
    private var lastInterceptAt: Long = 0L
    private const val COOLDOWN_MS = 1_200L

    /**
     * Launchers / System UI often host the short-press uninstall sheet while the accessibility
     * package name is **not** Settings or PackageInstaller — previously we missed those OEM flows.
     * For these packages we only act when both our app appears in the tree and copy suggests an
     * uninstall / removal flow (reduces false positives from home-screen icon labels).
     */
    private val LAUNCHER_OR_SYSTEM_CHROME_PACKAGES: Set<String> = setOf(
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.realme.launcher",
        "com.vivo.launcher",
        "com.oneplus.launcher",
        "com.nothing.launcher",
        "com.teslacoilsw.launcher",
        "com.microsoft.launcher",
        "com.motorola.launcher3",                // Motorola launcher
        "com.sonymobile.home",                   // Sony launcher
        "com.hihonor.android.launcher",          // Honor launcher
        "com.tcl.android.launcher",              // TCL / Alcatel launcher
        "com.transsion.hilauncher",              // Tecno / Infinix / Itel
        "com.android.systemui",
    )

    /**
     * System Settings / app-info hosts: the tree often includes the focused app’s name (notifications,
     * storage, etc.) even when the user is not in an uninstall flow — match launcher tier and require
     * uninstall‑ish copy before intercepting.
     */
    private val SETTINGS_APP_PACKAGES: Set<String> = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.google.android.settings",
        "com.google.android.apps.wellbeing",
        "com.samsung.android.settings",
        "com.samsung.android.app.settings",
        "com.transsion.settings",
    )

    private val WATCHED_PACKAGES: Set<String> = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.google.android.settings",
        "com.google.android.apps.wellbeing",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.samsung.android.settings",
        "com.samsung.android.app.settings",
        "com.miui.global.packageinstaller",
        "com.miui.packageinstaller",
        "com.miui.securitycenter",
        "com.android.vending",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.oplus.appdetail",
        "com.oneplus.security",
        "com.huawei.systemmanager",
        "com.hihonor.devicemanager",
        // Vivo / iQOO
        "com.iqoo.secure",
        "com.vivo.permissionmanager",
        // Realme / additional Oplus
        "com.realme.securitycheck",
        "com.oplus.securitypermission",
        // Transsion (Tecno / Infinix / Itel)
        "com.transsion.settings",
        "com.transsion.plat.appupdate",
        // Motorola / Lenovo
        "com.lenovo.security",
        "com.motorola.ccc.ota",
        // Sony (some builds use dedicated app management)
        "com.sonymobile.cta",
    )

    fun interceptIfNeeded(
        service: AccessibilityService,
        foregroundPackage: String,
    ): Boolean {
        val appContext: Context = service.applicationContext
        val myPkg = appContext.packageName
        val snap = ActiveBlockingState.current
        if (!snap.isBlocking || !snap.strictModeEnabled) return false
        if (foregroundPackage == myPkg) return false

        val tier = strictInterceptTier(foregroundPackage) ?: return false

        // Important: only read text from the candidate package's windows. If we flatten *all* interactive
        // windows, we can pick up "Tyme Boxed" from unrelated overlays/background UI while the user is
        // uninstalling some other app, causing false blocks.
        val combinedText = collectVisibleTextForPackage(service, foregroundPackage) ?: return false
        val flat = combinedText.lowercase(Locale.ROOT)
        // SystemUI hosts the notification shade, quick settings, recent apps,
        // and (on some OEMs) uninstall confirmation sheets. Our ongoing session
        // notification always contains "Tyme Boxed" plus tokens like "Remove"
        // that false-triggered on OxygenOS, ColorOS, and HyperOS. Require the
        // word "uninstall" (or translations) — the shade never contains it,
        // but genuine uninstall dialogs always do.
        if (foregroundPackage == "com.android.systemui" &&
            !hasExplicitUninstallLanguage(flat)
        ) {
            return false
        }
        // Critical: allow uninstalling OTHER apps during a session.
        // Tighten the heuristic: it’s not enough that "Tyme Boxed" appears somewhere (e.g. app lists);
        // we require that uninstall/removal copy is present AND it is *near* our app identifier in the
        // visible UI text.
        val suggestsUninstall = textSuggestsUninstallOrAppRemoval(flat, tier)
        val targetsTymeBoxed = suggestsUninstall && textClearlyTargetsTymeBoxed(appContext, flat, tier)
        if (!targetsTymeBoxed) {
            // Fallback for Settings: detect the Tyme Boxed app-info page even when
            // "Uninstall" is hidden — Samsung OneUI, MIUI/HyperOS, and others grey
            // out or remove the button while Device Admin is active, so the primary
            // heuristic misses it. The app-info page still has "Force stop" and
            // data/permission controls the user could tamper with.
            if (tier != StrictTier.SETTINGS_APP || !isAppInfoPageForTymeBoxed(appContext, flat)) {
                return false
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastInterceptAt < COOLDOWN_MS) return true
        lastInterceptAt = now

        return runCatching {
            // GLOBAL_ACTION_HOME is the strongest redirect we can issue without owning the
            // window — on Pixel/Samsung/Xiaomi it dismisses Settings instantly, where
            // GLOBAL_ACTION_BACK would just go one screen up (often still inside the
            // uninstall flow). For launcher / SystemUI we keep BACK because HOME there
            // would be a no-op or pop the user into a weird state.
            runCatching {
                if (tier == StrictTier.LAUNCHER_OR_SYSTEM_CHROME) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            }
            main.postDelayed(
                {
                    BlockerActivity.show(
                        context = appContext,
                        pkg = foregroundPackage,
                        label = "System",
                        headline = "Uninstall blocked",
                        body = "Strict mode is on. You can’t remove Tyme Boxed while a focus session " +
                            "is active. End the session in Tyme Boxed first.",
                    )
                },
                40L,
            )
            true
        }.getOrDefault(false)
    }

    private enum class StrictTier {
        /** Play Store, PackageInstaller, OEM security — usually an app-removal context. */
        STORE_OR_INSTALLER,
        /** System Settings — require uninstall/removal wording (avoid blocking all of Settings). */
        SETTINGS_APP,
        /** Launcher / SysUI — require uninstall/removal wording to avoid home-screen false trips. */
        LAUNCHER_OR_SYSTEM_CHROME,
    }

    private fun strictInterceptTier(pkg: String): StrictTier? {
        if (pkg in LAUNCHER_OR_SYSTEM_CHROME_PACKAGES) return StrictTier.LAUNCHER_OR_SYSTEM_CHROME
        if (isSettingsAppPackage(pkg)) return StrictTier.SETTINGS_APP
        if (pkg in WATCHED_PACKAGES || isInstallerOrStoreLike(pkg)) return StrictTier.STORE_OR_INSTALLER
        return null
    }

    private fun isSettingsAppPackage(pkg: String): Boolean {
        if (pkg in SETTINGS_APP_PACKAGES) return true
        return isOemSettingsHostPackage(pkg)
    }

    /**
     * OEM builds that use `*.settings` host packages not listed in [SETTINGS_APP_PACKAGES].
     * Mirrors the `.settings` branch previously inside [isInstallerOrStoreLike].
     */
    private fun isOemSettingsHostPackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        if (!p.endsWith(".settings")) return false
        return p.startsWith("com.google.") || p.startsWith("com.samsung.") ||
            p.startsWith("com.miui.") || p.startsWith("com.oplus.") ||
            p.startsWith("com.android.") || p.startsWith("com.oneplus.") ||
            p.startsWith("com.hihonor.") || p.startsWith("com.huawei") ||
            p.startsWith("com.vivo.") || p.startsWith("com.realme.") ||
            p.startsWith("com.transsion.") || p.startsWith("com.lenovo.") ||
            p.startsWith("com.motorola.") || p.startsWith("com.sonymobile.") ||
            p.startsWith("com.iqoo.") || p.startsWith("com.heytap.") ||
            p.startsWith("com.nothing.") || p.startsWith("com.tcl.")
    }

    /** Installers, Play Store, OEM “app management” apps — not plain system Settings. */
    private fun isInstallerOrStoreLike(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        if (p.contains("packageinstaller")) return true
        if (p.contains("vending") && p.contains("android")) return true
        if (p.contains("safecenter")) return true
        if (p.contains("securitycenter")) return true
        if (p.contains("devicemanager")) return true
        if (p.contains("systemmanager")) return true
        if (p.contains("appdetail")) return true
        return false
    }

    /**
     * Notification shade / quick settings — not an app-removal sheet. Used to ignore System UI
     * false positives while our session notification is visible in the panel.
     */
    private fun isLikelyNotificationShadeContext(flat: String): Boolean {
        val shadeMarkers = listOf(
            "notifications",
            "notification settings",
            "manage notifications",
            "quick settings",
            "quick setting",
            "clear all",
            "no notifications",
            "notification history",
            "silent",
            "priority",
            "heads up",
            "do not disturb",
        )
        return shadeMarkers.any { flat.contains(it) }
    }

    /**
     * Detect whether the user is on Tyme Boxed's app-info page in system Settings.
     *
     * Samsung OneUI, MIUI/HyperOS, ColorOS, and others grey out or hide the
     * "Uninstall" button when Device Admin is active, so the primary
     * [textSuggestsUninstallOrAppRemoval] check fails. This fallback recognizes
     * the app-info layout by its distinctive section markers ("Force stop",
     * "Storage", "Permissions", etc.) combined with our app name.
     */
    private fun isAppInfoPageForTymeBoxed(appContext: Context, flat: String): Boolean {
        // Must contain our app name or package name.
        val myPkg = appContext.packageName.lowercase(Locale.ROOT)
        val label = runCatching {
            appContext.applicationInfo.loadLabel(appContext.packageManager)
                .toString().lowercase(Locale.ROOT)
        }.getOrNull().orEmpty()
        val appNeedles = buildList {
            add("tyme boxed")
            add("tymeboxed")
            add("tyme-boxed")
            if (label.isNotBlank()) add(label)
            if (myPkg.isNotBlank()) add(myPkg)
        }
        if (appNeedles.none { flat.contains(it) }) return false

        // "Force stop" is the most distinctive app-info marker — every OEM
        // shows it regardless of whether "Uninstall" is available.
        val forceStopMarkers = listOf(
            "force stop", "force-stop",
            // French
            "forcer l'arrêt", "arrêt forcé",
            // German
            "beenden erzwingen", "stoppen erzwingen",
            // Spanish
            "forzar detención", "forzar cierre",
            // Portuguese
            "forçar parada", "forçar interrupção",
            // Italian
            "arresto forzato", "forza interruzione",
            // Chinese (Simplified & Traditional)
            "强行停止", "强制停止", "強制停止",
            // Japanese
            "強制停止",
            // Korean
            "강제 중지",
            // Russian
            "остановить принудительно", "принудительно остановить",
            // Hindi
            "ज़बरदस्ती रोकें", "बलपूर्वक रोकें",
            // Arabic
            "فرض الإيقاف",
            // Turkish
            "durmaya zorla",
        )
        if (forceStopMarkers.any { flat.contains(it) }) return true

        // Broader fallback: at least 2 of these section headers that appear
        // on every OEM's app-info page.
        val sectionMarkers = listOf(
            "storage", "permissions", "notifications", "battery",
            "clear data", "clear cache", "mobile data", "open by default",
            // German
            "speicher", "berechtigungen", "benachrichtigungen",
            // French
            "stockage", "autorisations",
            // Spanish
            "almacenamiento", "permisos",
            // Chinese
            "存储", "儲存空間", "权限", "權限", "通知",
            // Japanese
            "ストレージ", "許可",
            // Korean
            "저장공간", "저장 공간", "권한", "알림",
            // Russian
            "хранилище", "разрешения", "уведомления",
        )
        return sectionMarkers.count { flat.contains(it) } >= 2
    }

    /** Unambiguous uninstall copy (never matches generic notification "remove" hints). */
    private fun hasExplicitUninstallLanguage(flat: String): Boolean =
        explicitUninstallNeedles.any { flat.contains(it) }

    private val explicitUninstallNeedles = listOf(
        "uninstall",
        "deinstall",
        "désinstaller",
        "désinstallation",
        "desinstalar",
        "deinstallieren",
        "disinstall",
        "désinstalle",
        "卸载",
        "解除安裝",
        "삭제",
        "アンインストール",
        "удалить",
        "удален",
        "deleting",
    )

    /**
     * Cheap, multilingual hints for “this UI is about removing the app”. Not exhaustive — pairs with
     * [textClearlyTargetsTymeBoxed] so we rarely mis-fire on unrelated launcher / shade text.
     *
     * Launcher & System UI require explicit uninstall wording — the shade often has "remove" and
     * "app" near our notification without any uninstall dialog.
     */
    private fun textSuggestsUninstallOrAppRemoval(flat: String, tier: StrictTier): Boolean {
        if (hasExplicitUninstallLanguage(flat)) return true
        if (tier == StrictTier.LAUNCHER_OR_SYSTEM_CHROME) {
            // Phrase-only: the shade often contains separate "remove" and "app" tokens.
            return flat.contains("remove app")
        }
        return flat.contains("remove") && flat.contains("app")
    }

    private fun textClearlyTargetsTymeBoxed(
        context: Context,
        flat: String,
        tier: StrictTier,
    ): Boolean {
        val myPkg = context.packageName.lowercase(Locale.ROOT)

        // Strong signal: package name showing in the UI (common in some installers / Settings screens).
        if (myPkg.isNotBlank() && flat.contains(myPkg)) return true

        // Otherwise we rely on proximity between uninstall/removal copy and our visible app label.
        // This avoids blocking when "Tyme Boxed" appears somewhere in long app lists.
        val label = runCatching {
            context.applicationInfo.loadLabel(context.packageManager).toString().lowercase(Locale.ROOT)
        }.getOrNull().orEmpty()

        val appNeedles = buildList {
            add("tyme boxed")
            add("tymeboxed")
            add("tyme-boxed")
            if (label.isNotBlank()) add(label)
            // Some OEM surfaces show developer + app name.
            add("ambitionsoftware")
        }.distinct()

        val uninstallNeedles = buildList {
            addAll(explicitUninstallNeedles)
            add("remove app")
            if (tier != StrictTier.LAUNCHER_OR_SYSTEM_CHROME) {
                add("remove")
            }
        }

        // Require our app identifier to be *near* uninstall/removal wording.
        // Settings / installer pages place more content between the app name
        // and action buttons (version, size, description, sections) than
        // launcher context menus. Use a wider window so Samsung OneUI,
        // MIUI/HyperOS, and other OEM layouts still match.
        val proximityWindow = when (tier) {
            StrictTier.SETTINGS_APP, StrictTier.STORE_OR_INSTALLER -> 200
            StrictTier.LAUNCHER_OR_SYSTEM_CHROME -> 50
        }
        return uninstallNeedles.any { u ->
            appNeedles.any { a ->
                containsWithinWindow(flat, u, a, window = proximityWindow)
            }
        }
    }

    private fun containsWithinWindow(
        haystack: String,
        a: String,
        b: String,
        window: Int,
    ): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        var ai = haystack.indexOf(a)
        while (ai >= 0) {
            var bi = haystack.indexOf(b)
            while (bi >= 0) {
                if (kotlin.math.abs(ai - bi) <= window) return true
                bi = haystack.indexOf(b, startIndex = bi + 1)
            }
            ai = haystack.indexOf(a, startIndex = ai + 1)
        }
        return false
    }

    private fun collectVisibleTextForPackage(service: AccessibilityService, targetPkg: String): String? {
        val myPkg = service.applicationContext.packageName
        return try {
            val sb = StringBuilder(1500)
            val list = runCatching { service.windows }.getOrNull()
            if (!list.isNullOrEmpty()) {
                for (win in list) {
                    val r = win.root ?: continue
                    try {
                        val rp = r.packageName?.toString()
                        if (rp == myPkg) {
                            // Skip [BlockerActivity] full-screen layers so "Tyme Boxed" in the
                            // overlay does not false-trigger while the user is in Settings.
                            continue
                        }
                        if (rp != targetPkg) continue
                        sb.append(' ').append(flattenNodeTreeToString(r))
                    } finally {
                        runCatching { r.recycle() }
                    }
                }
            }
            if (sb.isBlank()) {
                val root = service.rootInActiveWindow ?: return null
                val rp = root.packageName?.toString()
                if (rp == myPkg) {
                    runCatching { root.recycle() }
                    return null
                }
                if (rp != targetPkg) {
                    runCatching { root.recycle() }
                    return null
                }
                try {
                    sb.append(' ').append(flattenNodeTreeToString(root))
                } finally {
                    runCatching { root.recycle() }
                }
            }
            sb.toString()
        } catch (_: Throwable) {
            null
        }
    }

    /** DFS; does not recycle [node]. Only reads visible nodes (API 16+). */
    private fun flattenNodeTreeToString(node: AccessibilityNodeInfo): String {
        val out = StringBuilder(512)
        fun walk(n: AccessibilityNodeInfo) {
            // Skip nodes not currently visible on screen. OEM launchers
            // (OxygenOS, ColorOS, HyperOS) pre-load context-menu items
            // ("Uninstall", "Remove", "App info") in the tree before the
            // user long-presses; reading those caused false-positive
            // overlays on the home screen and app drawer.
            if (!n.isVisibleToUser) return
            n.text?.let { out.append(' ').append(it) }
            n.contentDescription?.let { out.append(' ').append(it) }
            repeat(n.childCount) { i ->
                val c = n.getChild(i) ?: return@repeat
                try {
                    walk(c)
                } finally {
                    runCatching { c.recycle() }
                }
            }
        }
        walk(node)
        return out.toString()
    }
}
