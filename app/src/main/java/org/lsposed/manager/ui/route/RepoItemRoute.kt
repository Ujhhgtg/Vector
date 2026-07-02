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

package org.lsposed.manager.ui.route

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.lsposed.manager.R
import org.lsposed.manager.repo.model.ReleaseAsset
import org.lsposed.manager.ui.dialog.DownloadDialog
import org.lsposed.manager.ui.screen.repo.RepoItemActions
import org.lsposed.manager.ui.screen.repo.RepoItemScreen
import org.lsposed.manager.ui.screen.repo.RepoItemViewModel
import org.lsposed.manager.ui.shell.LocalHintHost
import org.lsposed.manager.util.NavUtil

/**
 * Repo detail destination. Compose replacement for RepoItemFragment: surfaces the README/releases/
 * info tabs, opens asset/browser URLs, shows the asset picker dialog, and emits the "no more
 * releases" hint once. The view model is keyed to [packageName] via a factory.
 */
@Composable
fun RepoItemRoute(packageName: String) {
    val activity = LocalActivity.current!!
    val context = LocalContext.current
    val hint = LocalHintHost.current
    val viewModel: RepoItemViewModel = viewModel(
        key = "repo_item_$packageName",
        factory = viewModelFactory { initializer { RepoItemViewModel(packageName) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var assets by remember { mutableStateOf<List<ReleaseAsset>?>(null) }

    val noMoreMsg = stringResource(R.string.module_release_no_more)
    LaunchedEffect(state.noMoreReleases) {
        if (state.noMoreReleases) {
            hint.show(noMoreMsg, lengthShort = true)
            viewModel.consumeNoMore()
        }
    }

    RepoItemScreen(
        viewModel = viewModel,
        actions = RepoItemActions(
            openUrl = { url -> url?.let { NavUtil.startURL(activity, it) } },
            openInBrowser = {
                NavUtil.startURL(activity, "https://modules.lsposed.org/module/${state.module?.name}")
            },
            showAssets = { list -> assets = list },
        ),
    )

    assets?.let { list ->
        DownloadDialog(
            assets = list,
            onPick = { url -> NavUtil.startURL(activity, url) },
            onDismiss = { assets = null },
        )
    }
}
