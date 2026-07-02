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

import java.util.Locale;

/**
 * Minimal locale holder replacing the former {@code rikka.material.app.LocaleDelegate}. The system
 * locale is captured once when this class is first loaded (which happens during {@code App.onCreate}
 * before any user override is applied), so it survives later {@link #setDefaultLocale} calls.
 */
public final class LocaleUtil {
    private static final Locale systemLocale = Locale.getDefault();
    private static Locale defaultLocale = Locale.getDefault();

    private LocaleUtil() {}

    public static Locale getSystemLocale() {
        return systemLocale;
    }

    public static Locale getDefaultLocale() {
        return defaultLocale;
    }

    public static void setDefaultLocale(Locale locale) {
        defaultLocale = locale;
        Locale.setDefault(locale);
    }
}
