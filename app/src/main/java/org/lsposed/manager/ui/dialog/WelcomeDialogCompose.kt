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
 * Copyright (C) 2022 LSPosed Contributors
 */

package org.lsposed.manager.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.ui.component.DialogWindowBlur
import org.lsposed.manager.util.ShortcutUtil

/**
 * First-launch welcome dialog. Compose replacement for the former WelcomeDialog DialogFragment.
 * Parasitic installs get a 3-button variant (never-show / ok / create-shortcut); the standalone app
 * gets a 2-button variant. The shell decides whether to show it via [shouldShowWelcome].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WelcomeDialog(
    isParasitic: Boolean,
    shortcutSupported: Boolean,
    onNeverShow: () -> Unit,
    onCreateShortcut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (isParasitic) R.string.parasitic_welcome else R.string.app_welcome
    val message = when {
        !isParasitic -> R.string.app_welcome_summary
        shortcutSupported -> R.string.parasitic_welcome_summary
        else -> R.string.parasitic_welcome_summary_no_shortcut_support
    }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                Spacer16()
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer16()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { onNeverShow(); onDismiss() }) {
                        Text(stringResource(R.string.never_show))
                    }
                    if (isParasitic) {
                        TextButton(onClick = { onCreateShortcut(); onDismiss() }) {
                            Text(stringResource(R.string.create_shortcut))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
    DialogWindowBlur()
}

@Composable
private fun Spacer16() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))

/** Mirrors the legacy WelcomeDialog.showIfNeed guard (binder alive, not opted out, not pinned). */
fun shouldShowWelcome(): Boolean {
    if (!org.lsposed.manager.ConfigManager.isBinderAlive()) return false
    if (App.getPreferences().getBoolean("never_show_welcome", false)) return false
    if (App.isParasitic && ShortcutUtil.isLaunchShortcutPinned()) return false
    return true
}
