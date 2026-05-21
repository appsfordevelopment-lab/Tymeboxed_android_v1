package dev.ambitionsoftware.tymeboxed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Section header with an optional trailing action, matching iOS
 * [`SectionTitle`](Components/Common/SectionTitle.swift). Typical usage:
 *
 * ```
 * SectionTitle("Recent Activity", buttonText = "See All", onButtonClick = {...})
 * ```
 *
 * Used on Home above the profile list and on Settings above each card group.
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    buttonText: String? = null,
    buttonIcon: ImageVector? = null,
    onButtonClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (buttonText != null && onButtonClick != null) {
            RoundedButton(
                text = buttonText,
                onClick = onButtonClick,
                icon = buttonIcon,
            )
        }
    }
}
