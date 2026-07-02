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

import androidx.annotation.Nullable;

import org.lsposed.lspd.models.Application;

import java.util.Objects;

/**
 * An {@link Application} (package name + user id) with value-based equality, used as the key type
 * for module scope sets. Extracted from the former ScopeAdapter so the scope model survives the
 * Compose migration independently of any adapter/UI class.
 */
public class ApplicationWithEquals extends Application {
    public ApplicationWithEquals(String packageName, int userId) {
        this.packageName = packageName;
        this.userId = userId;
    }

    public ApplicationWithEquals(Application application) {
        packageName = application.packageName;
        userId = application.userId;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof Application)) {
            return false;
        }
        return packageName.equals(((Application) obj).packageName) && userId == ((Application) obj).userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, userId);
    }
}
