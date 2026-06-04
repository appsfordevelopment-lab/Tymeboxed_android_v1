package dev.ambitionsoftware.tymeboxed.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import dev.ambitionsoftware.tymeboxed.ui.components.ActionButton
import dev.ambitionsoftware.tymeboxed.ui.theme.EmergencyRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val GLASS_TAPS_REQUIRED = 3

private val EmergencySheetBg = Color(0xFF1A1228)
private val EmergencyCardBg = Color(0xFF261D35)
private val EmergencyMutedText = Color(0xFFB8B0C4)
private val EmergencySettingsBtnBg = Color(0xFF342A45)
private val EmergencyPeriodPopupBg = Color(0xFF4E425F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyBottomSheet(
    remaining: Int,
    resetPeriodWeeks: Int,
    nextResetEpochMs: Long,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onResetPeriodWeeksChange: (Int) -> Unit,
    onEmergencyUnblock: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasRemaining = remaining > 0
    var glassTapCount by remember { mutableIntStateOf(0) }
    val glassRevealed = glassTapCount >= GLASS_TAPS_REQUIRED
    val haptic = LocalHapticFeedback.current
    var showPeriodPicker by remember { mutableStateOf(false) }

    val resetDateLabel = remember(nextResetEpochMs) {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        Instant.ofEpochMilli(nextResetEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    BackHandler {
        if (showPeriodPicker) showPeriodPicker = false else onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EmergencySheetBg,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .then(
                        if (showPeriodPicker) Modifier.alpha(0.42f) else Modifier,
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.emergency_access_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = EmergencyMutedText,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.emergency_resets_on, resetDateLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = EmergencyMutedText,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showPeriodPicker = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmergencySettingsBtnBg),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(
                                R.string.emergency_reset_period_settings_cd,
                            ),
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(
                        if (glassRevealed) {
                            R.string.emergency_access_hint_revealed
                        } else {
                            R.string.emergency_access_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmergencyMutedText,
                    lineHeight = 22.sp,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = EmergencyCardBg,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (hasRemaining) Color(0xFF34C759) else EmergencyRed,
                                modifier = Modifier.size(28.dp),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.emergency_unblocks_remaining_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmergencyMutedText,
                                )
                                Text(
                                    text = remaining.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasRemaining) Color.White else EmergencyRed,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.emergency_unblocks_limited),
                            style = MaterialTheme.typography.bodySmall,
                            color = EmergencyMutedText,
                        )

                        if (hasRemaining && !glassRevealed) {
                            EmergencyBreakGlassPanel(
                                tapCount = glassTapCount,
                                tapsRequired = GLASS_TAPS_REQUIRED,
                                enabled = !isLoading,
                                onTap = {
                                    if (glassTapCount < GLASS_TAPS_REQUIRED) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        glassTapCount++
                                    }
                                },
                            )
                        }

                        AnimatedVisibility(
                            visible = glassRevealed && hasRemaining,
                            enter = fadeIn(tween(280)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                            ),
                        ) {
                            ActionButton(
                                title = stringResource(R.string.emergency_unblock_button),
                                onClick = onEmergencyUnblock,
                                icon = Icons.Default.Warning,
                                backgroundColor = Color(0xFFE97A72),
                                contentColor = Color.White,
                                isLoading = isLoading,
                                enabled = !isLoading,
                            )
                        }

                        Text(
                            text = when {
                                !hasRemaining -> stringResource(R.string.emergency_unblocks_none_left)
                                glassRevealed -> stringResource(R.string.emergency_unblock_cost)
                                else -> pluralStringResource(
                                    R.plurals.emergency_glass_taps_remaining,
                                    GLASS_TAPS_REQUIRED - glassTapCount,
                                    GLASS_TAPS_REQUIRED - glassTapCount,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasRemaining) EmergencyMutedText else EmergencyRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (showPeriodPicker) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showPeriodPicker = false },
                        ),
                )
                EmergencyResetPeriodPopup(
                    selectedWeeks = resetPeriodWeeks,
                    onSelect = { weeks ->
                        onResetPeriodWeeksChange(weeks)
                        showPeriodPicker = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp),
                )
            }
        }
    }
}

@Composable
private fun EmergencyResetPeriodPopup(
    selectedWeeks: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(220.dp),
        shape = RoundedCornerShape(24.dp),
        color = EmergencyPeriodPopupBg,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppPreferences.EMERGENCY_RESET_PERIOD_WEEKS_OPTIONS.forEach { weeks ->
                val isSelected = weeks == selectedWeeks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(weeks) }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.emergency_reset_period_weeks, weeks),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyBreakGlassPanel(
    tapCount: Int,
    tapsRequired: Int,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val crackAlpha by animateFloatAsState(
        targetValue = when (tapCount) {
            0 -> 0.12f
            1 -> 0.5f
            2 -> 0.78f
            else -> 1f
        },
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "crackAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFF0B0AA),
                        Color(0xFFE97A72),
                        Color(0xFFE0665E),
                    ),
                ),
            )
            .clickable(
                enabled = enabled && tapCount < tapsRequired,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassCrackOverlay(
            crackLevel = tapCount,
            alpha = crackAlpha,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GlassCrackOverlay(
    crackLevel: Int,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (crackLevel <= 0 && alpha < 0.2f) return

    Canvas(modifier = modifier) {
        val crackColor = Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
        val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
        val origin = Offset(size.width * 0.2f, size.height * 0.52f)

        fun crackPath(builder: Path.() -> Unit) {
            val path = Path().apply(builder)
            drawPath(path, crackColor, style = stroke)
        }

        if (crackLevel >= 1) {
            crackPath {
                moveTo(origin.x, origin.y)
                lineTo(size.width * 0.38f, size.height * 0.18f)
                moveTo(origin.x, origin.y)
                lineTo(size.width * 0.32f, size.height * 0.78f)
            }
        }
        if (crackLevel >= 2) {
            crackPath {
                moveTo(origin.x, origin.y)
                lineTo(size.width * 0.52f, size.height * 0.08f)
                moveTo(origin.x, origin.y)
                lineTo(size.width * 0.58f, size.height * 0.45f)
                moveTo(origin.x, origin.y)
                lineTo(size.width * 0.48f, size.height * 0.92f)
            }
        }
        if (crackLevel >= 3) {
            crackPath {
                moveTo(size.width * 0.35f, size.height * 0.25f)
                lineTo(size.width * 0.72f, size.height * 0.12f)
                moveTo(size.width * 0.42f, size.height * 0.5f)
                lineTo(size.width * 0.88f, size.height * 0.38f)
                moveTo(size.width * 0.3f, size.height * 0.72f)
                lineTo(size.width * 0.78f, size.height * 0.82f)
                moveTo(size.width * 0.55f, size.height * 0.35f)
                lineTo(size.width * 0.55f, size.height * 0.88f)
            }
        }
    }
}
