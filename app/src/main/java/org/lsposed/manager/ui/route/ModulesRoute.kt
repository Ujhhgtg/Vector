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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.adapters.AppHelper
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.ui.component.BlurAlertDialog
import org.lsposed.manager.ui.dialog.CompileDialog
import org.lsposed.manager.ui.nav.AppList
import org.lsposed.manager.ui.nav.Route
import org.lsposed.manager.ui.nav.RepoItem
import org.lsposed.manager.ui.screen.modules.ModuleMenuEntry
import org.lsposed.manager.ui.screen.modules.ModulesActions
import org.lsposed.manager.ui.screen.modules.ModulesScreen
import org.lsposed.manager.ui.screen.modules.ModulesViewModel
import org.lsposed.manager.ui.shell.LocalHintHost
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule

/**
 * Modules destination. Compose replacement for ModulesFragment: builds the long-press context menu,
 * and hosts the uninstall / install-to-user confirm dialogs and the compile-speed progress dialog
 * (all formerly DialogFragments). Navigation to the scope and repo screens is delegated to the shell.
 */
@Composable
fun ModulesRoute(
    onNavigate: (Route) -> Unit,
    viewModel: ModulesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current!!
    val hint = LocalHintHost.current
    val moduleUtil = remember { ModuleUtil.getInstance() }
    val repoLoader = remember { RepoLoader.getInstance() }

    // Side-effect dialog state (replacements for the former DialogFragments).
    var uninstallTarget by remember { mutableStateOf<InstalledModule?>(null) }
    var installTarget by remember { mutableStateOf<Pair<InstalledModule, UserInfo>?>(null) }
    var compileTarget by remember { mutableStateOf<android.content.pm.ApplicationInfo?>(null) }

    fun buildContextMenu(module: InstalledModule): List<ModuleMenuEntry> {
        val entries = mutableListOf<ModuleMenuEntry>()
        val settingsIntent = AppHelper.getSettingsIntent(module.packageName, module.userId)
        if (settingsIntent != null) {
            entries += ModuleMenuEntry(context.getString(R.string.module_settings), Icons.Outlined.Settings) {
                ConfigManager.startActivityAsUserWithFeature(settingsIntent, module.userId)
            }
        }
        entries += ModuleMenuEntry(context.getString(R.string.modules_other_app), Icons.Outlined.Apps) {
            val intent = Intent(Intent.ACTION_SHOW_APP_INFO)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, module.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ConfigManager.startActivityAsUserWithFeature(intent, module.userId)
        }
        if (repoLoader.getOnlineModule(module.packageName) != null) {
            entries += ModuleMenuEntry(context.getString(R.string.view_in_repo), Icons.Outlined.GetApp) {
                onNavigate(RepoItem(module.packageName))
            }
        }
        entries += ModuleMenuEntry(context.getString(R.string.module_app_info), Icons.Outlined.Info) {
            ConfigManager.startActivityAsUserWithFeature(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", module.packageName, null)),
                module.userId,
            )
        }
        // Legacy always offers compile-speed; it passes pkg.applicationInfo straight through. Guard
        // only the actual dialog launch so a null applicationInfo can't crash the dex-opt call.
        entries += ModuleMenuEntry(context.getString(R.string.compile_speed), Icons.Outlined.Speed) {
            module.pkg.applicationInfo?.let { appInfo -> compileTarget = appInfo }
                ?: hint.show(context.getString(R.string.compile_failed), lengthShort = true)
        }
        entries += ModuleMenuEntry(context.getString(R.string.module_uninstall), Icons.Outlined.Delete) {
            uninstallTarget = module
        }
        if (module.userId == 0) {
            ConfigManager.getUsers()?.forEach { user ->
                if (moduleUtil.getModule(module.packageName, user.id) == null) {
                    entries += ModuleMenuEntry(context.getString(R.string.install_to_user, user.name), Icons.Outlined.PersonAdd) {
                        installTarget = module to user
                    }
                }
            }
        }
        return entries
    }

    ModulesScreen(
        viewModel = viewModel,
        actions = ModulesActions(
            onModuleClick = { module -> onNavigate(AppList(module.packageName, module.userId)) },
            contextMenuEntries = ::buildContextMenu,
            onInstallToUser = { module, user -> installTarget = module to user },
        ),
    )

    uninstallTarget?.let { module ->
        BlurAlertDialog(
            onDismissRequest = { uninstallTarget = null },
            icon = {
                org.lsposed.manager.ui.component.AppIcon(
                    module.pkg,
                    sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.roundToPx() },
                    modifier = androidx.compose.ui.Modifier.size(48.dp),
                )
            },
            title = module.appName,
            text = { androidx.compose.material3.Text(stringResource(R.string.module_uninstall_message)) },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                App.getExecutorService().submit {
                    val success = ConfigManager.uninstallPackage(module.packageName, module.userId)
                    hint.show(
                        if (success) context.getString(R.string.module_uninstalled, module.appName)
                        else context.getString(R.string.module_uninstall_failed),
                        lengthShort = false,
                    )
                    if (success) moduleUtil.reloadSingleModule(module.packageName, module.userId)
                }
            },
            dismissText = stringResource(android.R.string.cancel),
        )
    }

    installTarget?.let { (module, user) ->
        BlurAlertDialog(
            onDismissRequest = { installTarget = null },
            title = stringResource(R.string.install_to_user, user.name),
            text = { androidx.compose.material3.Text(stringResource(R.string.install_to_user_message, module.appName, user.name)) },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                App.getExecutorService().submit {
                    val success = ConfigManager.installExistingPackageAsUser(module.packageName, user.id)
                    hint.show(
                        if (success) context.getString(R.string.module_installed, module.appName, user.name)
                        else context.getString(R.string.module_install_failed),
                        lengthShort = false,
                    )
                    if (success) moduleUtil.reloadSingleModule(module.packageName, user.id)
                }
            },
            dismissText = stringResource(android.R.string.cancel),
        )
    }

    compileTarget?.let { appInfo ->
        CompileDialog(
            appInfo = appInfo,
            onResult = { text -> hint.show(text, lengthShort = true) },
            onDismiss = { compileTarget = null },
        )
    }
}
