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

package org.lsposed.manager.ui.screen.repo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.repo.model.OnlineModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(
    viewModel: RepoViewModel,
    onModuleClick: (OnlineModule) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    val subtitle = when {
        state.upgradableCount > 0 ->
            App.getInstance().resources.getQuantityString(
                R.plurals.module_repo_upgradable, state.upgradableCount, state.upgradableCount,
            )
        state.upgradableCount == 0 -> stringResource(R.string.module_repo_up_to_date)
        else -> stringResource(R.string.loading)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = queryText,
                            onValueChange = {
                                queryText = it
                                viewModel.setQuery(it)
                            },
                            singleLine = true,
                            placeholder = { Text(stringResource(android.R.string.search_go)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column {
                            Text(stringResource(R.string.module_repo))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) {
                            queryText = ""
                            viewModel.setQuery("")
                        }
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(android.R.string.search_go))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.menu_sort))
                    }
                    RepoSortMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onChanged = { viewModel.refresh() },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            RepoList(viewModel = viewModel, onModuleClick = onModuleClick)
        }
    }
}

@Composable
private fun RepoSortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val prefs = App.getPreferences()
    var sort by remember { mutableStateOf(prefs.getInt("repo_sort", 0)) }
    var upgradableFirst by remember { mutableStateOf(prefs.getBoolean("upgradable_first", true)) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_by_name)) },
            leadingIcon = { RadioButton(selected = sort == 0, onClick = null) },
            onClick = {
                sort = 0
                prefs.edit().putInt("repo_sort", 0).apply()
                onChanged()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_by_update_time)) },
            leadingIcon = { RadioButton(selected = sort == 1, onClick = null) },
            onClick = {
                sort = 1
                prefs.edit().putInt("repo_sort", 1).apply()
                onChanged()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_upgradable_first)) },
            trailingIcon = { Checkbox(checked = upgradableFirst, onCheckedChange = null) },
            onClick = {
                upgradableFirst = !upgradableFirst
                prefs.edit().putBoolean("upgradable_first", upgradableFirst).apply()
                onChanged()
            },
        )
    }
}
