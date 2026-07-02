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

package org.lsposed.manager.ui.screen.logs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

/** UI state for a single log tab (module or verbose). */
data class LogTabState(
    val loading: Boolean = true,
    val lines: List<String> = emptyList(),
)

class LogsViewModel : ViewModel() {
    private val _module = MutableStateFlow(LogTabState())
    val module: StateFlow<LogTabState> = _module.asStateFlow()

    private val _verbose = MutableStateFlow(LogTabState())
    val verbose: StateFlow<LogTabState> = _verbose.asStateFlow()

    val verboseLogEnabled: Boolean get() = ConfigManager.isVerboseLogEnabled()

    private fun flow(verbose: Boolean) = if (verbose) _verbose else _module

    fun load(verbose: Boolean) {
        val target = flow(verbose)
        target.value = target.value.copy(loading = true)
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) { readLog(verbose) }
            target.value = LogTabState(loading = false, lines = lines)
        }
    }

    /** Clears the log via the binder and reloads. Returns success. */
    fun clear(verbose: Boolean): Boolean {
        val ok = ConfigManager.clearLogs(verbose)
        if (ok) load(verbose)
        return ok
    }

    private fun readLog(verbose: Boolean): List<String> {
        return try {
            ConfigManager.getLog(verbose).use { pfd ->
                BufferedReader(InputStreamReader(FileInputStream(pfd?.fileDescriptor))).use { br ->
                    br.lineSequence().toList()
                }
            }
        } catch (e: Throwable) {
            Log.w(App.TAG, "read log", e)
            Log.getStackTraceString(e).split("\n")
        }
    }
}
