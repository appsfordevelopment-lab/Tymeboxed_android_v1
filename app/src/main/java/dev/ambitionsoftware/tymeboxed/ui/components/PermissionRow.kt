package dev.ambitionsoftware.tymeboxed.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambitionsoftware.tymeboxed.permissions.TymePermission

/**
 * A single row in the permissions list — used by both the Intro wizard and
 * the Settings → Permissions card so the two stay visually consistent.
 *
 * - Shows the human-readable title + description from [TymePermission].
 * - Left side has a status icon (green check when granted, amber warning when
 *   not) to mirror the iOS `PermissionsScreen` row affordance.
 * - Right side is a [Switch] reflecting granted state; toggling calls
 *   [onGrantClick] to open the system screen / settings for that permission.
 *
 * Required rows use an amber warning icon when not granted. Unavailable rows
 * (e.g. NFC on hardware-less devices) are muted.
 */
@Composable
fun PermissionRow(
    permission: TymePermission,
    granted: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
) {
    val rowClickable = !unavailable && !granted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (rowClickable) {
                    Modifier.clickable(onClick = onGrantClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconTint = when {
            unavailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            granted -> Color(0xFF2E7D32) // emerald-ish green
            permission.required -> Color(0xFFE08E00) // amber
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(fraction = 0.7f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = permission.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!permission.required) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(optional)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (unavailable) "NFC hardware not available on this device."
                       else permission.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (unavailable) {
            Text(
                text = "N/A",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val cs = MaterialTheme.colorScheme
            Switch(
                checked = granted,
                onCheckedChange = { newChecked ->
                    if (newChecked != granted) onGrantClick()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = cs.primary,
                    checkedTrackColor = cs.primary.copy(alpha = 0.45f),
                ),
            )
        }
    }
}
