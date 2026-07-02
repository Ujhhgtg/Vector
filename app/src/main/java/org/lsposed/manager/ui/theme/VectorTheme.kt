/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme
import org.lsposed.manager.App
import org.lsposed.manager.util.ThemeUtil

/**
 * The 18 built-in accent seed colors, keyed by the `theme_color` preference value. These are the
 * same seeds the legacy `materialThemeBuilder` Gradle plugin used to generate the View-system
 * ThemeOverlays; here they feed [rememberDynamicColorScheme] so Compose generates the full Material 3
 * tonal palette natively, with no XML overlays.
 */
private val AccentSeeds: Map<String, Color> = mapOf(
    "SAKURA" to Color(0xFFFF9CA8),
    "MATERIAL_RED" to Color(0xFFF44336),
    "MATERIAL_PINK" to Color(0xFFE91E63),
    "MATERIAL_PURPLE" to Color(0xFF9C27B0),
    "MATERIAL_DEEP_PURPLE" to Color(0xFF673AB7),
    "MATERIAL_INDIGO" to Color(0xFF3F51B5),
    "MATERIAL_BLUE" to Color(0xFF2196F3),
    "MATERIAL_LIGHT_BLUE" to Color(0xFF03A9F4),
    "MATERIAL_CYAN" to Color(0xFF00BCD4),
    "MATERIAL_TEAL" to Color(0xFF009688),
    "MATERIAL_GREEN" to Color(0xFF4FAF50),
    "MATERIAL_LIGHT_GREEN" to Color(0xFF8BC3A4),
    "MATERIAL_LIME" to Color(0xFFCDDC39),
    "MATERIAL_YELLOW" to Color(0xFFFFEB3B),
    "MATERIAL_AMBER" to Color(0xFFFFC107),
    "MATERIAL_ORANGE" to Color(0xFFFF9800),
    "MATERIAL_DEEP_ORANGE" to Color(0xFFFF5722),
    "MATERIAL_BROWN" to Color(0xFF795548),
    "MATERIAL_BLUE_GREY" to Color(0xFF607D8F),
)

private val DefaultSeed = AccentSeeds.getValue("MATERIAL_BLUE")

/** Pushes the dark scheme's surfaces to pure black for the AMOLED "black dark theme" option. */
private fun ColorScheme.toBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    surfaceDim = Color.Black,
)

/**
 * Resolves the active Compose [ColorScheme] from the same preferences the legacy theme system read
 * (`theme_color`, `follow_system_accent`, `dark_theme`, `black_dark_theme`). Dynamic (Monet) color is
 * used on Android 12+ when "follow system accent" is on; otherwise one of the 18 seeded schemes.
 */
@Composable
fun rememberVectorColorScheme(): ColorScheme {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val mode = remember { App.getPreferences().getString("dark_theme", ThemeUtil.MODE_NIGHT_FOLLOW_SYSTEM) }
    val isDark = when (mode) {
        ThemeUtil.MODE_NIGHT_YES -> true
        ThemeUtil.MODE_NIGHT_NO -> false
        else -> systemDark
    }
    val systemAccent = ThemeUtil.isSystemAccent()
    val blackDark = App.getPreferences().getBoolean("black_dark_theme", false)

    val scheme = if (systemAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = AccentSeeds[ThemeUtil.getColorTheme()] ?: DefaultSeed
        rememberDynamicColorScheme(seed, isDark = isDark, isAmoled = false)
    }
    return if (isDark && blackDark) scheme.toBlack() else scheme
}

@Composable
fun VectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = rememberVectorColorScheme(), content = content)
}
