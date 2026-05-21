package dev.ambitionsoftware.tymeboxed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Prominent primary-action button — Android twin of iOS
 * [`ActionButton`](Components/Common/ActionButton.swift).
 *
 * Used for "Continue" in the intro wizard, "Start Session" on the home
 * screen, "Save" in Profile Edit, etc. Defaults to the theme accent via
 * `MaterialTheme.colorScheme.primary`; callers may override `backgroundColor`
 * (e.g. the emergency-unblock flow uses [EmergencyRed]).
 */
@Composable
fun ActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val interactive = enabled && !isLoading
    Button(
        onClick = { if (interactive) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = interactive,
        shape = RoundedCornerShape(25.dp), // capsule — matches iOS Capsule clipShape
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.9f),
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
