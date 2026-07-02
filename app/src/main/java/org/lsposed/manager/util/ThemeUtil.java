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

package org.lsposed.manager.util;

import android.content.SharedPreferences;
import android.os.Build;

import org.lsposed.manager.App;

/**
 * Theme preference accessors. Since the UI is fully Jetpack Compose (see {@code VectorTheme}), the
 * Compose layer derives the active {@code ColorScheme} directly from these preferences — there are
 * no View-system theme overlays or {@code AppCompatDelegate} night-mode hooks any more.
 */
public class ThemeUtil {
    private static final SharedPreferences preferences = App.getPreferences();

    public static final String MODE_NIGHT_FOLLOW_SYSTEM = "MODE_NIGHT_FOLLOW_SYSTEM";
    public static final String MODE_NIGHT_NO = "MODE_NIGHT_NO";
    public static final String MODE_NIGHT_YES = "MODE_NIGHT_YES";

    /**
     * Dynamic (Monet) color is available from Android 12 (S). The system-accent preference can only
     * take effect on such devices.
     */
    public static boolean isDynamicColorAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean isSystemAccent() {
        return isDynamicColorAvailable() && preferences.getBoolean("follow_system_accent", true);
    }

    /** The selected accent key (e.g. {@code MATERIAL_BLUE}), or {@code SYSTEM} when following the wallpaper. */
    public static String getColorTheme() {
        if (isSystemAccent()) {
            return "SYSTEM";
        }
        return preferences.getString("theme_color", "MATERIAL_BLUE");
    }

    /** The chosen night mode (follow-system / always / never), used by the Compose theme. */
    public static String getDarkThemeMode() {
        return preferences.getString("dark_theme", MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
