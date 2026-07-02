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

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.lsposed.manager.R
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.ui.screen.repo.RepoScreen
import org.lsposed.manager.ui.screen.repo.RepoViewModel
import org.lsposed.manager.ui.shell.LocalHintHost

/**
 * Repo list destination: assembles [RepoScreen] and warms up a [WebView] (used by the detail
 * screen's README) shortly after first composition, mirroring the legacy RepoFragment.onResume.
 */
@Composable
fun RepoRoute(
    onModuleClick: (OnlineModule) -> Unit,
    viewModel: RepoViewModel = viewModel(),
) {
    val context = LocalContext.current
    val hint = LocalHintHost.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
        // Preload the WebView engine once so the README renders without a cold-start stall.
        delay(500)
        runCatching { WebView(context) }
    }

    // Surface repo load failures as a snackbar hint (legacy RepoFragment.onThrowable).
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { t ->
            hint.show(
                context.getString(R.string.repo_load_failed, t.localizedMessage ?: ""),
                lengthShort = false,
            )
        }
    }

    RepoScreen(viewModel = viewModel, onModuleClick = onModuleClick)
}
