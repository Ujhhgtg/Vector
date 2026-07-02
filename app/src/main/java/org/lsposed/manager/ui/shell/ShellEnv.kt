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

package org.lsposed.manager.ui.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Snackbar-backed equivalent of the legacy `BaseFragment.showHint`. Provided once by the shell so
 * any destination can post a message with an optional action button (e.g. "reboot").
 */
class HintHost(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(
        message: String,
        lengthShort: Boolean = true,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
    ) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (lengthShort) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) action?.invoke()
        }
    }
}

/** App-wide hint host + the activity's restart() entry point, provided by the shell. */
val LocalHintHost = staticCompositionLocalOf<HintHost> { error("No HintHost provided") }
val LocalRestart = staticCompositionLocalOf<() -> Unit> { error("No restart provided") }
