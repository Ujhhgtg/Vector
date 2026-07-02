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

package org.lsposed.manager.ui.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation-compose routes, replacing the former XML nav graphs + SafeArgs. The five
 * top-level destinations map to the bottom-bar / navigation-rail items (declared in [TopLevelRoutes]
 * in the legacy nav order); [AppList] and [RepoItem] are pushed onto the back stack with arguments.
 */
sealed interface Route

@Serializable
data object Home : Route

@Serializable
data object Logs : Route

@Serializable
data object Settings : Route

@Serializable
data object Modules : Route

@Serializable
data object Repo : Route

@Serializable
data class AppList(val modulePackageName: String, val moduleUserId: Int) : Route

@Serializable
data class RepoItem(val modulePackageName: String) : Route
