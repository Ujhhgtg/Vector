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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.materialkolor.rememberDynamicColorScheme

/**
 * Parity gate for the native color generation (Phase 6a): renders every built-in accent in
 * light / dark / black so the migration can be visually compared against the legacy
 * materialThemeBuilder output before the XML overlays are deleted in Phase 6c. Tooling-only.
 */
private val PreviewSeeds = listOf(
    "Sakura" to Color(0xFFFF9CA8),
    "Red" to Color(0xFFF44336),
    "Pink" to Color(0xFFE91E63),
    "Purple" to Color(0xFF9C27B0),
    "DeepPurple" to Color(0xFF673AB7),
    "Indigo" to Color(0xFF3F51B5),
    "Blue" to Color(0xFF2196F3),
    "LightBlue" to Color(0xFF03A9F4),
    "Cyan" to Color(0xFF00BCD4),
    "Teal" to Color(0xFF009688),
    "Green" to Color(0xFF4FAF50),
    "LightGreen" to Color(0xFF8BC3A4),
    "Lime" to Color(0xFFCDDC39),
    "Yellow" to Color(0xFFFFEB3B),
    "Amber" to Color(0xFFFFC107),
    "Orange" to Color(0xFFFF9800),
    "DeepOrange" to Color(0xFFFF5722),
    "Brown" to Color(0xFF795548),
    "BlueGrey" to Color(0xFF607D8F),
)

@Composable
private fun Swatch(scheme: ColorScheme) {
    Surface(color = scheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                scheme.primary, scheme.onPrimary, scheme.primaryContainer,
                scheme.secondary, scheme.tertiary, scheme.surfaceVariant, scheme.error,
            ).forEach {
                Box(it)
            }
        }
    }
}

@Composable
private fun Box(c: Color) {
    Surface(color = c, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(22.dp)) {}
}

@Preview(name = "Accents · Light", heightDp = 900)
@Composable
private fun AccentMatrixLight() {
    Column {
        PreviewSeeds.forEach { (name, seed) ->
            MaterialTheme(colorScheme = rememberDynamicColorScheme(seed, isDark = false, isAmoled = false)) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 1.dp)) {
                        Text(name, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp))
                        Swatch(MaterialTheme.colorScheme)
                    }
                }
            }
        }
    }
}

@Preview(name = "Accents · Dark", heightDp = 900)
@Composable
private fun AccentMatrixDark() {
    Column {
        PreviewSeeds.forEach { (name, seed) ->
            MaterialTheme(colorScheme = rememberDynamicColorScheme(seed, isDark = true, isAmoled = false)) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 1.dp)) {
                        Text(name, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp))
                        Swatch(MaterialTheme.colorScheme)
                    }
                }
            }
        }
    }
}

@Preview(name = "Accents · Black", heightDp = 900)
@Composable
private fun AccentMatrixBlack() {
    Column {
        PreviewSeeds.forEach { (name, seed) ->
            MaterialTheme(colorScheme = rememberDynamicColorScheme(seed, isDark = true, isAmoled = true)) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 1.dp)) {
                        Text(name, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp))
                        Swatch(MaterialTheme.colorScheme)
                    }
                }
            }
        }
    }
}
