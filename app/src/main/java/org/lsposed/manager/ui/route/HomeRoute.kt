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
import org.lsposed.manager.R
import org.lsposed.manager.ui.dialog.AboutDialog
import org.lsposed.manager.ui.dialog.WelcomeDialog
import org.lsposed.manager.ui.dialog.shouldShowWelcome
import org.lsposed.manager.ui.screen.home.HomeActions
import org.lsposed.manager.ui.screen.home.HomeScreen
import org.lsposed.manager.ui.screen.home.HomeViewModel
import org.lsposed.manager.ui.shell.LocalHintHost
import org.lsposed.manager.util.NavUtil
import org.lsposed.manager.util.ShortcutUtil
import rikka.core.util.ClipboardUtils

/**
 * Home destination: assembles [HomeScreen] with its side effects (about/welcome dialogs, feedback &
 * update links, copy-to-clipboard hint). Compose replacement for the former HomeFragment.
 */
@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val activity = LocalActivity.current!!
    val context = LocalContext.current
    val hint = LocalHintHost.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showAbout by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(shouldShowWelcome()) }

    LaunchedEffect(Unit) { viewModel.refresh(context.packageName) }

    HomeScreen(
        state = state,
        actions = HomeActions(
            onAbout = { showAbout = true },
            onFeedback = {
                NavUtil.startURL(activity, "https://github.com/Ujhhgtg/Vector/issues/new/choose")
            },
            onUpdate = {
                val url = if (state.binderAlive) R.string.latest_url else R.string.install_url
                NavUtil.startURL(activity, context.getString(url))
            },
            onCopyInfo = { info ->
                ClipboardUtils.put(activity, info)
                hint.show(context.getString(R.string.info_copied), lengthShort = false)
            },
        ),
    )

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showWelcome) {
        val shortcutSupported = remember {
            runCatching { ShortcutUtil.isRequestPinShortcutSupported(context) }.getOrDefault(false)
        }
        WelcomeDialog(
            isParasitic = org.lsposed.manager.App.isParasitic,
            shortcutSupported = shortcutSupported,
            onNeverShow = {
                org.lsposed.manager.App.getPreferences().edit().putBoolean("never_show_welcome", true).apply()
            },
            onCreateShortcut = {
                val pinned = ShortcutUtil.requestPinLaunchShortcut {
                    org.lsposed.manager.App.getPreferences().edit().putBoolean("never_show_welcome", true).apply()
                    hint.show(context.getString(R.string.settings_shortcut_pinned_hint), lengthShort = false)
                }
                if (!pinned) {
                    hint.show(context.getString(R.string.settings_unsupported_pin_shortcut_summary), lengthShort = false)
                }
            },
            onDismiss = { showWelcome = false },
        )
    }
}
