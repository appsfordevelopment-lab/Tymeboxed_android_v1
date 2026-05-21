package dev.ambitionsoftware.tymeboxed.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii match iOS:
 *  - `SettingsCard` / profile cards use a 22dp radius (see iOS `settingsCard`
 *    helper in `SettingsView.swift:76-102`).
 *  - Primary CTA buttons use 16dp ("rounded rectangle").
 *  - Small chips/toggles use 12dp.
 *
 * Material 3 maps these to `small` / `medium` / `large` so normal Material
 * components pick the right curvature automatically.
 */
val TbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
