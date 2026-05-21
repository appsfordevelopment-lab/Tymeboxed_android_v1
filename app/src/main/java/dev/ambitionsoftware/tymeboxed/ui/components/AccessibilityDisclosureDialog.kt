package dev.ambitionsoftware.tymeboxed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ambitionsoftware.tymeboxed.R

/**
 * Prominent disclosure shown before the user is sent to system Accessibility settings.
 * Required by Google Play policy for apps using [android.accessibilityservice.AccessibilityService].
 */
@Composable
fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onConfirmOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.accessibility_disclosure_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.accessibility_disclosure_what_it_does),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.accessibility_disclosure_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmOpenSettings) {
                Text(stringResource(R.string.accessibility_disclosure_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.accessibility_disclosure_not_now))
            }
        },
    )
}
