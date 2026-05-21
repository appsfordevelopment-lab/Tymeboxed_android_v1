package dev.ambitionsoftware.tymeboxed.ui.screens.insights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.ambitionsoftware.tymeboxed.domain.model.Profile

private fun formatInsightDurationMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
fun ProfileInsightsScreen(
    profile: Profile,
    onDismiss: () -> Unit,
    viewModel: ProfileInsightsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onDismiss)
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(profile.id) {
        viewModel.startCollecting(profile.id)
        onDispose { viewModel.stopCollecting() }
    }

    val cs = MaterialTheme.colorScheme
    val profileTitle = profile.name.ifBlank { "Unnamed Profile" }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val chartHeight = 200.dp
    val maxDaily = state.dailyFocusMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scroll)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = cs.onSurface,
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { periodMenuExpanded = true },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = cs.surface,
                            contentColor = cs.onSurface,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.periodButtonLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    DropdownMenu(
                        expanded = periodMenuExpanded,
                        onDismissRequest = { periodMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("This week") },
                            onClick = {
                                viewModel.setPeriod(InsightsPeriod.THIS_WEEK)
                                periodMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("This month") },
                            onClick = {
                                viewModel.setPeriod(InsightsPeriod.THIS_MONTH)
                                periodMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Text(
                text = "$profileTitle Insights",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )

            Text(
                text = if (state.period == InsightsPeriod.THIS_MONTH) {
                    "Avg focus / session (this month)"
                } else {
                    "Avg focus / session (this week)"
                },
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            )
            Text(
                text = formatInsightDurationMinutes(state.avgFocusMinutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    state.dailyFocusMinutes.forEach { minutes ->
                        val fraction =
                            (minutes.toFloat() / maxDaily.toFloat()).coerceIn(0f, 1f)
                        val barFraction = if (minutes == 0) 0.06f else fraction
                        val barHeight = (chartHeight * 0.88f) * barFraction.coerceIn(0.06f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (minutes > 0) {
                                            cs.primary.copy(alpha = 0.85f)
                                        } else {
                                            cs.surfaceVariant.copy(alpha = 0.35f)
                                        },
                                    ),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.dayLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (state.period == InsightsPeriod.THIS_MONTH) {
                    Text(
                        text = "Bar height is total focus time on that weekday in the current month (all weeks combined).",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }

            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cs.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InsightSummaryRow(
                        icon = Icons.Default.Schedule,
                        label = "Total focus time",
                        value = formatInsightDurationMinutes(state.totalFocusMinutes),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = cs.outline.copy(alpha = 0.2f),
                    )
                    InsightSummaryRow(
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        label = "Completed sessions",
                        value = state.completedSessionCount.toString(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface,
        )
    }
}
