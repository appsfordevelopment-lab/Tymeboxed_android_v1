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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.ambitionsoftware.tymeboxed.R
import dev.ambitionsoftware.tymeboxed.ui.components.ActionButton
import dev.ambitionsoftware.tymeboxed.ui.theme.EmergencyRed

private const val GLASS_TAPS_REQUIRED = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyBottomSheet(
    remaining: Int,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onEmergencyUnblock: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasRemaining = remaining > 0
    val cs = MaterialTheme.colorScheme
    var glassTapCount by remember { mutableIntStateOf(0) }
    val glassRevealed = glassTapCount >= GLASS_TAPS_REQUIRED
    val haptic = LocalHapticFeedback.current

    BackHandler(onBack = onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.emergency_access_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    if (glassRevealed) {
                        R.string.emergency_access_hint_revealed
                    } else {
                        R.string.emergency_access_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                                color = cs.onSurfaceVariant,
                            )
                            Text(
                                text = remaining.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (hasRemaining) cs.onSurface else EmergencyRed,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.emergency_unblocks_limited),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
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
                        color = if (hasRemaining) cs.onSurfaceVariant else EmergencyRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
