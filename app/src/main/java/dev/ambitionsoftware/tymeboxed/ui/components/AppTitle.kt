package dev.ambitionsoftware.tymeboxed.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * App title header — mirrors iOS [`AppTitle`](Components/Common/AppTitle.swift).
 *
 * Default text is "Tyme Boxed" matching the iOS main header. The home screen
 * renders it in [MaterialTheme.typography.displaySmall]; other call sites can
 * override `style` for smaller variants.
 */
@Composable
fun AppTitle(
    modifier: Modifier = Modifier,
    title: String = "Tyme Boxed",
    style: TextStyle = MaterialTheme.typography.displaySmall,
    fontWeight: FontWeight = FontWeight.Bold,
    horizontalPadding: Int = 16,
) {
    Text(
        text = title,
        style = style,
        fontWeight = fontWeight,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = horizontalPadding.dp),
    )
}
