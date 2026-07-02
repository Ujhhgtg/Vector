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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.model.OnlineModule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoList(
    viewModel: RepoViewModel,
    onModuleClick: (OnlineModule) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.reload() },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!state.loading && state.modules.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.modules, key = { it.name ?: it.hashCode().toString() }) { module ->
                    RepoRow(
                        module = module,
                        upgradable = viewModel.upgradable(module),
                        installed = viewModel.isInstalled(module),
                        onClick = { onModuleClick(module) },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun RepoRow(
    module: OnlineModule,
    upgradable: RepoLoader.ModuleVersion?,
    installed: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = module.description ?: module.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = module.name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        module.summary?.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = stringResource(R.string.module_repo_updated_time, formatReleaseTime(module)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        when {
            upgradable != null -> Text(
                text = stringResource(R.string.update_available, upgradable.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            installed -> Text(
                text = stringResource(R.string.installed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatReleaseTime(module: OnlineModule): String {
    return try {
        val channels = App.getInstance().resources.getStringArray(R.array.update_channel_values)
        val channel = App.getPreferences().getString("update_channel", channels[0])
        val latest = RepoLoader.getInstance().getLatestReleaseTime(module.name, channel)
            ?: module.latestReleaseTime
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(App.getLocale()).withZone(ZoneId.systemDefault())
        formatter.format(Instant.parse(latest))
    } catch (e: Exception) {
        ""
    }
}
