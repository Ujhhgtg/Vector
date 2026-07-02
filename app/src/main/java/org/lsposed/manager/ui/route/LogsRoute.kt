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

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.receivers.LSPManagerServiceHolder
import org.lsposed.manager.ui.screen.logs.LogsActions
import org.lsposed.manager.ui.screen.logs.LogsScreen
import org.lsposed.manager.ui.screen.logs.LogsViewModel
import org.lsposed.manager.ui.shell.LocalHintHost
import java.time.LocalDateTime
import java.util.Locale

/**
 * Logs destination: assembles [LogsScreen] with its side effects (save-to-zip via SAF launcher,
 * clear, word-wrap preference). Compose replacement for the former LogsFragment.
 */
@Composable
fun LogsRoute(viewModel: LogsViewModel = viewModel()) {
    val context = LocalContext.current
    val hint = LocalHintHost.current
    val scope = rememberCoroutineScope()

    var wordWrap by remember {
        mutableStateOf(App.getPreferences().getBoolean("enable_word_wrap", false))
    }
    var prettyPrint by remember {
        mutableStateOf(App.getPreferences().getBoolean("logs_pretty_print", false))
    }

    // Re-read both log buffers each time the screen is shown, matching the legacy onResume.
    LaunchedEffect(Unit) {
        viewModel.load(false)
        viewModel.load(true)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "wt").use { zipFd ->
                        hint.show(context.getString(R.string.logs_saving), lengthShort = false)
                        LSPManagerServiceHolder.getService().getLogs(zipFd)
                    }
                }
                hint.show(context.getString(R.string.logs_saved))
            } catch (e: Throwable) {
                val message = e.cause?.message ?: e.message
                hint.show(context.getString(R.string.logs_save_failed2, message), lengthShort = false)
                Log.w(App.TAG, "save log", e)
            }
        }
    }

    LogsScreen(
        viewModel = viewModel,
        wordWrap = wordWrap,
        onWordWrapChange = { enabled ->
            wordWrap = enabled
            App.getPreferences().edit().putBoolean("enable_word_wrap", enabled).apply()
        },
        prettyPrint = prettyPrint,
        onPrettyPrintChange = { enabled ->
            prettyPrint = enabled
            App.getPreferences().edit().putBoolean("logs_pretty_print", enabled).apply()
        },
        actions = LogsActions(
            save = {
                val now = LocalDateTime.now()
                val filename = String.format(Locale.getDefault(), "LSPosed_%s.zip", now.toString())
                try {
                    saveLauncher.launch(filename)
                } catch (e: ActivityNotFoundException) {
                    hint.show(context.getString(R.string.enable_documentui))
                }
            },
            clear = { verbose ->
                if (viewModel.clear(verbose)) {
                    hint.show(context.getString(R.string.logs_cleared))
                } else {
                    hint.show(context.getString(R.string.logs_clear_failed_2))
                }
            },
        ),
    )
}
