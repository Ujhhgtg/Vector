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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.repo.model.Release
import org.lsposed.manager.repo.model.ReleaseAsset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class RepoItemActions(
    val openUrl: (String?) -> Unit,
    val openInBrowser: () -> Unit,
    val showAssets: (List<ReleaseAsset>) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoItemScreen(
    viewModel: RepoItemViewModel,
    actions: RepoItemActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val module = state.module
    val titles = listOf(
        stringResource(R.string.module_readme),
        stringResource(R.string.module_releases),
        stringResource(R.string.module_information),
    )
    val pagerState = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(module?.description ?: "")
                        Text(
                            module?.name ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.openInBrowser) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.menu_open_in_browser),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ReadmeWebView(state.readmeHtml)
                    1 -> ReleasesTab(state.releases, module?.releasesLoaded == true, state.loadingMore, viewModel, actions)
                    else -> InformationTab(module, actions)
                }
            }
        }
    }
}

@Composable
private fun ReleasesTab(
    releases: List<Release>,
    releasesLoaded: Boolean,
    loadingMore: Boolean,
    viewModel: RepoItemViewModel,
    actions: RepoItemActions,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(releases, key = { it.url ?: it.hashCode().toString() }) { release ->
            ReleaseRow(release, actions)
            HorizontalDivider(thickness = 0.5.dp)
        }
        if (!releasesLoaded) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator()
                    } else {
                        TextButton(onClick = { viewModel.loadMoreReleases() }) {
                            Text(stringResource(R.string.module_release_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseRow(release: Release, actions: RepoItemActions) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(release.name ?: "", style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.module_repo_published_time, formatDateTime(release.publishedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReadmeWebView(
            release.descriptionHTML,
            modifier = Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 400.dp).padding(top = 8.dp),
            nestedInScrollable = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { actions.openUrl(release.url) }) {
                Text(stringResource(R.string.menu_open_in_browser))
            }
            val assets = release.releaseAssets
            if (!assets.isNullOrEmpty()) {
                OutlinedButton(onClick = { actions.showAssets(assets) }) {
                    Text(stringResource(R.string.module_release_view_assets))
                }
            }
        }
    }
}

@Composable
private fun InformationTab(
    module: org.lsposed.manager.repo.model.OnlineModule?,
    actions: RepoItemActions,
) {
    module ?: return
    LazyColumn(Modifier.fillMaxSize()) {
        if (!module.homepageUrl.isNullOrEmpty()) {
            item {
                InfoRow(
                    title = stringResource(R.string.module_information_homepage),
                    value = module.homepageUrl.orEmpty(),
                    onClick = { actions.openUrl(module.homepageUrl) },
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
        val collaborators = module.collaborators
        if (!collaborators.isNullOrEmpty()) {
            item {
                CollaboratorsRow(collaborators, onOpenUrl = actions.openUrl)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
        if (!module.sourceUrl.isNullOrEmpty()) {
            item {
                InfoRow(
                    title = stringResource(R.string.module_information_source_url),
                    value = module.sourceUrl.orEmpty(),
                    onClick = { actions.openUrl(module.sourceUrl) },
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Collaborators row: each name is an individual link to the collaborator's GitHub profile, opened
 * through [onOpenUrl] (custom tabs), mirroring the legacy per-collaborator CustomTabsURLSpan.
 */
@Composable
private fun CollaboratorsRow(
    collaborators: List<org.lsposed.manager.repo.model.Collaborator>,
    onOpenUrl: (String?) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(collaborators, linkColor) {
        buildAnnotatedString {
            val visible = collaborators.filter { it.login != null }
            visible.forEachIndexed { index, collaborator ->
                val login = collaborator.login!!
                val name = collaborator.name ?: login
                val link = LinkAnnotation.Url(
                    "https://github.com/$login",
                    TextLinkStyles(SpanStyle(color = linkColor)),
                ) { _ -> onOpenUrl("https://github.com/$login") }
                withLink(link) { append(name) }
                if (index != visible.lastIndex) append(", ")
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.module_information_collaborators), style = MaterialTheme.typography.titleSmall)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun formatDateTime(raw: String?): String {
    return try {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(App.getLocale()).withZone(ZoneId.systemDefault())
        formatter.format(Instant.parse(raw))
    } catch (e: Exception) {
        ""
    }
}
