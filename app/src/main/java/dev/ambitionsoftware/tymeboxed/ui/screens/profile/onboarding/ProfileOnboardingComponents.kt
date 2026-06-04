package dev.ambitionsoftware.tymeboxed.ui.screens.profile.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambitionsoftware.tymeboxed.ui.components.ActionButton
import dev.ambitionsoftware.tymeboxed.ui.theme.CardDark
import dev.ambitionsoftware.tymeboxed.ui.theme.CardLight
import dev.ambitionsoftware.tymeboxed.ui.theme.SurfaceDark
import dev.ambitionsoftware.tymeboxed.ui.theme.SurfaceLight

/** Foqos-style sheet chrome: grouped background + elevated white cards. */
@Composable
fun ProfileOnboardingScaffold(
    /** 1-based position among steps shown for this profile (skips hidden steps). */
    displayStep: Int,
    displayTotalSteps: Int,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    nextLabel: String = "Next",
    nextEnabled: Boolean = true,
    allowNextWhenDisabled: Boolean = false,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val sheetBg = if (isDark) SurfaceDark else SurfaceLight
    val cardBg = if (isDark) CardDark else CardLight
    val navBtnBg = if (isDark) Color(0xFF2C2C2E) else Color.White
    val navIcon = cs.onSurface.copy(alpha = 0.75f)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x66000000)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            shape = sheetShape,
            color = sheetBg,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (onBack != null) {
                        OnboardingNavButton(
                            onClick = onBack,
                            backgroundColor = navBtnBg,
                            contentColor = navIcon,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                    OnboardingNavButton(
                        onClick = onClose,
                        backgroundColor = navBtnBg,
                        contentColor = navIcon,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = "Step $displayStep of $displayTotalSteps",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                        lineHeight = 32.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                        lineHeight = 22.sp,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    content()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp, top = 8.dp),
                ) {
                    ActionButton(
                        title = nextLabel,
                        onClick = onNext,
                        enabled = nextEnabled,
                        allowClickWhenDisabled = allowNextWhenDisabled,
                        backgroundColor = if (nextEnabled) {
                            cs.primary
                        } else {
                            cs.outline.copy(alpha = 0.35f)
                        },
                        contentColor = cs.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingNavButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(backgroundColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun OnboardingSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) CardDark else CardLight
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.22f else 0.12f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun OnboardingNavRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showDividerBelow: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
        }
        if (showDividerBelow) {
            OnboardingCardDivider()
        }
    }
}

@Composable
fun OnboardingCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 18.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

@Composable
fun OnboardingReviewRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                modifier = Modifier.weight(0.45f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(0.55f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        if (showDivider) OnboardingCardDivider()
    }
}
