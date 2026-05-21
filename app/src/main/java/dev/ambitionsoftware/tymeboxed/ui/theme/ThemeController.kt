package dev.ambitionsoftware.tymeboxed.ui.theme

import androidx.compose.ui.graphics.Color
import dev.ambitionsoftware.tymeboxed.data.prefs.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Thin adapter between [AppPreferences] (DataStore) and the theme layer.
 * Exposes the currently-selected [AccentColor] as a [Flow] so [TbTheme] can
 * react to changes from the Settings dropdown without an app restart.
 *
 * This is the Android analogue of iOS `ThemeManager.themeColor`, but lives
 * outside any `@Composable` so ViewModels can write to it too.
 */
@Singleton
class ThemeController @Inject constructor(
    private val prefs: AppPreferences,
) {
    /** Emits the current accent every time the user changes it. */
    val accent: Flow<AccentColor> = prefs.themeColorName.map { AccentColors.byName(it) }

    /** Convenience: just the `Color` without the name. */
    val primary: Flow<Color> = accent.map { it.value }

    suspend fun select(name: String) {
        prefs.setThemeColorName(name)
    }

    suspend fun select(accent: AccentColor) = select(accent.name)
}
