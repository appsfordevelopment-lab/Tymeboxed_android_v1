package dev.ambitionsoftware.tymeboxed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * App-level Material 3 theme wrapper. The `primary` color is driven by
 * [ThemeController], so selecting a new accent in Settings immediately
 * repaints every screen that uses `MaterialTheme.colorScheme.primary`.
 *
 * [LocalAccentColor] is provided alongside the Material scheme so any custom
 * Compose component (the home profile cards, the break-glass button, etc.)
 * can read the accent directly — handy when it needs to render tinted
 * backgrounds or gradients that Material 3's slot system doesn't cover.
 */
val LocalAccentColor = staticCompositionLocalOf { AccentColors.default }

@HiltViewModel
class ThemeViewModel @Inject constructor(
    controller: ThemeController,
) : ViewModel() {
    val accent = controller.accent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AccentColors.default,
    )
}

@Composable
fun TbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val vm: ThemeViewModel = hiltViewModel()
    val accent by vm.accent.collectAsState()

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = accent.value,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            background = SurfaceDark,
            surface = CardDark,
            onSurface = androidx.compose.ui.graphics.Color.White,
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF8E8E93),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3A3A3C),
            outline = BorderDark,
            outlineVariant = androidx.compose.ui.graphics.Color(0xFF38383A),
        )
    } else {
        lightColorScheme(
            primary = accent.value,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            background = SurfaceLight,
            surface = CardLight,
            onSurface = androidx.compose.ui.graphics.Color.Black,
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF636366),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E5EA),
            outline = BorderLight,
            outlineVariant = androidx.compose.ui.graphics.Color(0xFFC6C6C8),
        )
    }

    CompositionLocalProvider(LocalAccentColor provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TbTypography,
            shapes = TbShapes,
            content = content,
        )
    }
}
