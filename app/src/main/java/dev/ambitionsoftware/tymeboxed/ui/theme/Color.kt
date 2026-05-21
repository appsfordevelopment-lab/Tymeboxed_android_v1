package dev.ambitionsoftware.tymeboxed.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 15 named accent colors are ported verbatim from iOS
 * `TymeBoxed/Utils/ThemeManager.swift:7-23`. Names, order, and hex codes must
 * stay in sync across the two platforms so a user who picks "Warm Sandstone"
 * on iPad sees the same tint on their Android phone.
 */
data class AccentColor(val name: String, val value: Color)

object AccentColors {
    val Grimace = AccentColor("Grimace Purple", Color(0xFF894FA3))
    val Ocean = AccentColor("Ocean Blue", Color(0xFF007AFF))
    val Mint = AccentColor("Mint Fresh", Color(0xFF00C6BF))
    val Lime = AccentColor("Lime Zest", Color(0xFF7FD800))
    val Coral = AccentColor("Sunset Coral", Color(0xFFFF5966))
    val HotPink = AccentColor("Hot Pink", Color(0xFFFF2DA5))
    val Tangerine = AccentColor("Tangerine", Color(0xFFFF9300))
    val Lavender = AccentColor("Lavender Dream", Color(0xFFBA8EFF))
    val Merlot = AccentColor("San Diego Merlot", Color(0xFF7A1E3A))
    val Forest = AccentColor("Forest Green", Color(0xFF0B6E4F))
    val Miami = AccentColor("Miami Vice", Color(0xFFFF6EC7))
    val Lemonade = AccentColor("Electric Lemonade", Color(0xFFCCFF00))
    val NeonGrape = AccentColor("Neon Grape", Color(0xFFB026FF))
    val Slate = AccentColor("Slate Stone", Color(0xFF708090))
    val Sandstone = AccentColor("Warm Sandstone", Color(0xFFC4A77D))

    /** Ordered list — UI (settings dropdown) should iterate this. */
    val all: List<AccentColor> = listOf(
        Grimace, Ocean, Mint, Lime, Coral, HotPink, Tangerine, Lavender,
        Merlot, Forest, Miami, Lemonade, NeonGrape, Slate, Sandstone,
    )

    /** Default for new installs — matches iOS `ThemeManager.defaultColorName`. */
    val default: AccentColor = Sandstone

    fun byName(name: String?): AccentColor =
        all.firstOrNull { it.name == name } ?: default
}

/**
 * Light red used for emergency-unblock buttons, mirroring
 * `Color.emergencyLightRed` on iOS (r=1.0, g=0.45, b=0.45).
 */
val EmergencyRed = Color(red = 1.0f, green = 0.45f, blue = 0.45f)

// ---- Neutrals used by the card-based layouts on both platforms ----
// iOS Settings cards render on a grey group background with white cards.
// Android maps these onto Material 3 `surface` / `surfaceVariant` slots.
val SurfaceLight = Color(0xFFF2F2F7)   // iOS `systemGroupedBackground`
val SurfaceDark = Color(0xFF1C1C1E)    // iOS `systemGroupedBackground` dark
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF2C2C2E)
val BorderLight = Color(0x14000000)    // ~8% black — matches iOS card stroke
val BorderDark = Color(0x1FFFFFFF)
