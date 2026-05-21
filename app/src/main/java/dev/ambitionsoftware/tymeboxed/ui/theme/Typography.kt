package dev.ambitionsoftware.tymeboxed.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scale chosen to echo iOS defaults. The iOS app leans on SF Pro's
 * `.largeTitle` / `.title2` / `.headline` / `.body` / `.subheadline` / `.caption`
 * slots. Material 3 provides a close-enough analogue via `displaySmall`,
 * `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `labelSmall`.
 *
 * We stick with the platform default font family so Android users see their
 * system font (Roboto / OEM-specific) rather than a downloaded SF clone.
 */
private val Default = FontFamily.Default

val TbTypography = Typography(
    // Used for the big "Tyme Boxed" title on Home (see iOS `AppTitle`).
    displaySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    // Screen titles in navigation bars.
    titleLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Card / section headers (e.g. "Appearance", "About").
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    // Primary row text inside settings cards.
    bodyLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    // Secondary descriptive text.
    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    // Tiny "Made in Hyderabad India" footers, permission captions.
    labelSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
