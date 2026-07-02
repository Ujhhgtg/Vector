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
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.ui.screen.settings.SettingsCallbacks
import org.lsposed.manager.ui.screen.settings.SettingsScreen
import org.lsposed.manager.ui.screen.settings.SettingsViewModel
import org.lsposed.manager.ui.shell.LocalHintHost
import org.lsposed.manager.ui.shell.LocalRestart
import org.lsposed.manager.util.BackupUtils
import org.lsposed.manager.util.NavUtil
import org.lsposed.manager.util.LocaleUtil
import org.lsposed.manager.util.ShortcutUtil
import java.time.LocalDateTime
import java.util.Locale

/**
 * Settings destination. Compose replacement for SettingsFragment: hosts the backup/restore document
 * launchers, theme/locale application, restart, shortcut pinning, and reboot hint. Heavy backup work
 * runs on the app executor (matching the legacy `runAsync`), with results surfaced as snackbar hints.
 */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current!!
    val hint = LocalHintHost.current
    val restart = LocalRestart.current

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        App.getExecutorService().submit {
            try {
                BackupUtils.backup(uri)
            } catch (e: Exception) {
                hint.show(context.getString(R.string.settings_backup_failed2, e.message), lengthShort = false)
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        App.getExecutorService().submit {
            try {
                BackupUtils.restore(uri)
            } catch (e: Exception) {
                hint.show(context.getString(R.string.settings_restore_failed2, e.message), lengthShort = false)
            }
        }
    }

    SettingsScreen(
        viewModel = viewModel,
        callbacks = SettingsCallbacks(
            restart = restart,
            setDefaultNightMode = {
                // Night mode is resolved inside VectorTheme from the persisted `dark_theme`
                // preference, so recreating the activity is enough to apply the new mode.
                restart()
            },
            applyLocale = { tag ->
                val app = App.getInstance()
                val locale = App.getLocale(tag)
                val res = app.resources
                val config = res.configuration
                config.setLocale(locale)
                LocaleUtil.setDefaultLocale(locale)
                @Suppress("DEPRECATION")
                res.updateConfiguration(config, res.displayMetrics)
            },
            backup = {
                val now = LocalDateTime.now()
                try {
                    backupLauncher.launch(String.format(Locale.getDefault(), "LSPosed_%s.lsp", now.toString()))
                } catch (e: ActivityNotFoundException) {
                    hint.show(context.getString(R.string.enable_documentui), lengthShort = true)
                }
            },
            restore = {
                try {
                    restoreLauncher.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    hint.show(context.getString(R.string.enable_documentui), lengthShort = true)
                }
            },
            addShortcut = {
                val pinned = ShortcutUtil.requestPinLaunchShortcut {
                    App.getPreferences().edit().putBoolean("never_show_welcome", true).apply()
                    hint.show(context.getString(R.string.settings_shortcut_pinned_hint), lengthShort = false)
                }
                if (!pinned) {
                    hint.show(context.getString(R.string.settings_unsupported_pin_shortcut_summary), lengthShort = true)
                }
            },
            openTranslation = {
                NavUtil.startURL(activity, "https://crowdin.com/project/lsposed_jingmatrix")
            },
            rebootHint = {
                hint.show(
                    context.getString(R.string.reboot_required),
                    lengthShort = true,
                    actionLabel = context.getString(R.string.reboot),
                ) { viewModel.reboot() }
            },
            shortcutSupported = runCatching {
                ShortcutUtil.isRequestPinShortcutSupported(context)
            }.getOrDefault(false),
        ),
    )
}
