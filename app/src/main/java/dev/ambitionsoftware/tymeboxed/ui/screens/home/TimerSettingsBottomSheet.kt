package dev.ambitionsoftware.tymeboxed.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.ui.components.ActionButton
import kotlin.math.roundToInt

/** Slider range for [dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId.FOCUS_TIMER]. */
val FOCUS_TIMER_SETTINGS_RANGE = TimerSettingsRange(minMinutes = 15, maxMinutes = 24 * 60)

/** Slider range for [dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId.FOCUS_TIMER_BREAK]. */
val BREAK_TIMER_SETTINGS_RANGE = TimerSettingsRange(minMinutes = 5, maxMinutes = 60)

data class TimerSettingsRange(
    val minMinutes: Int,
    val maxMinutes: Int,
    val stepMinutes: Int = 5,
) {
    fun coerce(minutes: Int): Int = minutes.coerceIn(minMinutes, maxMinutes)

    fun snap(raw: Float): Int {
        val offset = raw - minMinutes
        val steps = (offset / stepMinutes).roundToInt()
        return (minMinutes + steps * stepMinutes).coerceIn(minMinutes, maxMinutes)
    }
}

/**
 * Modal to pick session/break length before starting a timer-based strategy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSettingsBottomSheet(
    profileName: String,
    range: TimerSettingsRange,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    val startMinutes = range.coerce(initialMinutes)
    var minutes by remember(profileName, range, startMinutes) {
        mutableIntStateOf(range.snap(startMinutes.toFloat()))
    }

    BackHandler(onBack = onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.timer_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.timer_settings_subtitle, profileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            Text(
                text = formatTimerDurationLabel(minutes),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedIconButton(
                    onClick = {
                        minutes = (minutes - range.stepMinutes).coerceAtLeast(range.minMinutes)
                    },
                    enabled = minutes > range.minMinutes,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.timer_settings_decrease),
                        tint = cs.onSurface,
                    )
                }

                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = range.snap(it) },
                    valueRange = range.minMinutes.toFloat()..range.maxMinutes.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = cs.onSurface,
                        activeTrackColor = cs.onSurface.copy(alpha = 0.35f),
                        inactiveTrackColor = cs.onSurface.copy(alpha = 0.12f),
                    ),
                )

                FilledIconButton(
                    onClick = {
                        minutes = (minutes + range.stepMinutes).coerceAtMost(range.maxMinutes)
                    },
                    enabled = minutes < range.maxMinutes,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                        disabledContainerColor = cs.primary.copy(alpha = 0.4f),
                        disabledContentColor = cs.onPrimary.copy(alpha = 0.6f),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.timer_settings_increase),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTimerDurationLabel(range.minMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = formatTimerDurationLabel(range.maxMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            ActionButton(
                title = stringResource(R.string.timer_settings_confirm),
                icon = Icons.Default.Check,
                onClick = { onConfirm(minutes) },
            )
        }
    }
}

/** Compact label for the large timer readout (e.g. `15m`, `1h`, `1h 30m`). */
fun formatTimerDurationLabel(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    if (m < 60) return "${m}m"
    val hours = m / 60
    val rem = m % 60
    return if (rem == 0) "${hours}h" else "${hours}h ${rem}m"
}
