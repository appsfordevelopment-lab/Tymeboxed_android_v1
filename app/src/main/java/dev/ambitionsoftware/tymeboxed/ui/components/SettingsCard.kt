package dev.ambitionsoftware.tymeboxed.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The grouped-section card every Settings / Home card is wrapped in.
 *
 * Mirrors iOS `settingsCard` helper in `SettingsView.swift:80-106`:
 *   - 22dp rounded-rectangle clip
 *   - Subtle 1dp border in `outline`
 *   - Optional title rendered outside and above the card, subheadline weight,
 *     secondary text colour — matches iOS "Appearance" / "About" section
 *     labels above each card.
 *
 * Children are placed inside a `Column(spacing = 0)`. Use [SettingsCardDivider]
 * between rows to get the iOS inset-divider look.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    elevation: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * Inset divider that mirrors the iOS `settingsInsetDivider` (1px hairline with
 * 16dp left inset). Use between rows inside a [SettingsCard].
 */
@Composable
fun SettingsCardDivider(
    modifier: Modifier = Modifier,
    startInset: Int = 16,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = startInset.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * Convenience pass-through for row content inside a [SettingsCard]. Mirrors
 * the 16dp horizontal / 14dp vertical padding iOS uses inside its grouped
 * rows so content lines up across cards.
 */
@Composable
fun SettingsCardRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(0.dp)) // keeps column spacing at 0
}
