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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.adapters.AppHelper
import org.lsposed.manager.ui.component.BlurAlertDialog
import org.lsposed.manager.ui.dialog.CompileDialog
import org.lsposed.manager.ui.screen.scope.AppScopeScreen
import org.lsposed.manager.ui.screen.scope.AppScopeViewModel
import org.lsposed.manager.ui.screen.scope.ScopeActions
import org.lsposed.manager.ui.screen.scope.ScopeAppInfo
import org.lsposed.manager.ui.screen.scope.ScopeMenuActions
import org.lsposed.manager.ui.shell.LocalHintHost
import org.lsposed.manager.util.BackupUtils
import org.lsposed.manager.util.ModuleUtil
import java.time.LocalDateTime
import java.util.Locale

/**
 * App-scope destination. Compose replacement for AppListFragment: hosts the backup/restore launchers,
 * the per-app context-menu confirm dialogs (reboot / force-stop), the compile-speed dialog, the
 * "use recommended" confirm, and the back-press guard that warns when no scope is selected.
 *
 * If the module can't be resolved (e.g. uninstalled while navigating) the route immediately pops back.
 */
@Composable
fun AppListRoute(
    modulePackageName: String,
    moduleUserId: Int,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current!!
    val hint = LocalHintHost.current

    val module = remember(modulePackageName, moduleUserId) {
        ModuleUtil.getInstance().getModule(modulePackageName, moduleUserId)
    }
    if (module == null) {
        // Mirrors the legacy fragment redirecting back to the module list when the module is gone.
        BackHandler(enabled = false) {}
        onNavigateUp()
        return
    }

    val viewModel: AppScopeViewModel = viewModel(
        key = "scope:$modulePackageName:$moduleUserId",
        factory = viewModelFactory { initializer { AppScopeViewModel(module) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val title = if (module.userId != 0) "${module.getAppName()} (${module.userId})" else module.getAppName()
    val settingsIntent = remember { AppHelper.getSettingsIntent(module.packageName, module.userId) }

    // Mirror the legacy AppListFragment.onResume: re-read scope/enabled state when returning to the
    // foreground (e.g. after force-stop, changing settings, or a restore).
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.refresh(false)
        onPauseOrDispose { }
    }

    // Sealed UI dialog state for confirms triggered from this screen.
    var dialog by remember { mutableStateOf<ScopeDialog?>(null) }
    var compileInfo by remember { mutableStateOf<android.content.pm.ApplicationInfo?>(null) }

    val rebootHint: () -> Unit = {
        hint.show(
            context.getString(R.string.reboot_required),
            lengthShort = true,
            actionLabel = context.getString(R.string.reboot),
        ) { ConfigManager.reboot() }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        App.getExecutorService().submit {
            try {
                BackupUtils.backup(uri, module.packageName)
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
                BackupUtils.restore(uri, module.packageName)
            } catch (e: Exception) {
                hint.show(context.getString(R.string.settings_restore_failed2, e.message), lengthShort = false)
            }
        }
    }

    // The legacy back-press guard: if the module is enabled, loaded, and nothing is selected, warn.
    val handleBack: () -> Unit = {
        if (state.enabled && !state.loading && state.checked.isEmpty()) {
            dialog = ScopeDialog.NoSelection(hasRecommended = state.recommended.isNotEmpty())
        } else {
            onNavigateUp()
        }
    }
    BackHandler { handleBack() }

    AppScopeScreen(
        title = title,
        subtitle = module.packageName,
        viewModel = viewModel,
        actions = ScopeActions(
            onBack = handleBack,
            onMasterSwitch = { checked -> viewModel.setMasterSwitch(checked) },
            onToggleApp = { app, checked ->
                if (!viewModel.toggleApp(app, checked)) {
                    hint.show(context.getString(R.string.failed_to_save_scope_list))
                } else if (app.packageName == "system") {
                    rebootHint()
                }
            },
            contextMenuEntries = { app ->
                buildContextMenu(
                    app, module, context,
                    showDialog = { dialog = it },
                    showCompile = { compileInfo = it },
                )
            },
            menuActions = ScopeMenuActions(
                useRecommended = {
                    if (!viewModel.state.value.enabled) {
                        hint.show(context.getString(R.string.module_is_not_activated_yet), lengthShort = false)
                    } else if (viewModel.state.value.checked.isNotEmpty()) {
                        dialog = ScopeDialog.UseRecommended
                    } else {
                        viewModel.checkRecommended()
                    }
                },
                selectAll = { if (viewModel.selectAll()) rebootHint() },
                selectNone = { if (viewModel.selectNone()) rebootHint() },
                backup = {
                    try {
                        backupLauncher.launch(
                            String.format(Locale.getDefault(), "%s_%s.lsp", module.getAppName(), LocalDateTime.now().toString()),
                        )
                    } catch (e: ActivityNotFoundException) {
                        hint.show(context.getString(R.string.enable_documentui))
                    }
                },
                restore = {
                    try {
                        restoreLauncher.launch(arrayOf("*/*"))
                    } catch (e: ActivityNotFoundException) {
                        hint.show(context.getString(R.string.enable_documentui))
                    }
                },
            ),
            onOpenModuleSettings = settingsIntent?.let {
                { ConfigManager.startActivityAsUserWithFeature(it, module.userId) }
            },
        ),
    )

    when (val d = dialog) {
        is ScopeDialog.NoSelection -> {
            BlurAlertDialog(
                onDismissRequest = { dialog = null },
                text = {
                    androidx.compose.material3.Text(
                        stringResource(
                            if (d.hasRecommended) R.string.no_scope_selected_has_recommended
                            else R.string.no_scope_selected,
                        ),
                    )
                },
                confirmText = if (d.hasRecommended) stringResource(android.R.string.ok) else null,
                onConfirm = if (d.hasRecommended) {
                    { viewModel.checkRecommended() }
                } else null,
                // The "disable module & leave" action is the destructive choice.
                neutralText = if (d.hasRecommended) stringResource(android.R.string.cancel) else stringResource(android.R.string.ok),
                onNeutral = {
                    ModuleUtil.getInstance().setModuleEnabled(module.packageName, false)
                    hint.show(
                        context.getString(R.string.module_disabled_no_selection, module.getAppName()),
                        lengthShort = false,
                    )
                    onNavigateUp()
                },
            )
        }

        ScopeDialog.UseRecommended -> {
            BlurAlertDialog(
                onDismissRequest = { dialog = null },
                text = { androidx.compose.material3.Text(stringResource(R.string.use_recommended_message)) },
                confirmText = stringResource(android.R.string.ok),
                onConfirm = { viewModel.checkRecommended() },
                dismissText = stringResource(android.R.string.cancel),
            )
        }

        is ScopeDialog.Reboot -> {
            BlurAlertDialog(
                onDismissRequest = { dialog = null },
                title = stringResource(R.string.reboot),
                confirmText = stringResource(android.R.string.ok),
                onConfirm = { ConfigManager.reboot() },
                dismissText = stringResource(android.R.string.cancel),
            )
        }

        is ScopeDialog.ForceStop -> {
            BlurAlertDialog(
                onDismissRequest = { dialog = null },
                title = stringResource(R.string.force_stop_dlg_title),
                text = { androidx.compose.material3.Text(stringResource(R.string.force_stop_dlg_text)) },
                confirmText = stringResource(android.R.string.ok),
                onConfirm = { ConfigManager.forceStopPackage(d.packageName, d.uid / App.PER_USER_RANGE) },
                dismissText = stringResource(android.R.string.cancel),
            )
        }

        null -> {}
    }

    compileInfo?.let { info ->
        CompileDialog(
            appInfo = info,
            onResult = { hint.show(it) },
            onDismiss = { compileInfo = null },
        )
    }
}

/** Confirm dialogs that the app-scope screen can raise. */
private sealed interface ScopeDialog {
    data class NoSelection(val hasRecommended: Boolean) : ScopeDialog
    data object UseRecommended : ScopeDialog
    data object Reboot : ScopeDialog
    data class ForceStop(val packageName: String, val uid: Int) : ScopeDialog
}

/**
 * Builds the per-app long-press menu. Intents run immediately; reboot/force-stop/compile defer to the
 * caller's dialog state since they need confirmation or a progress dialog.
 */
private fun buildContextMenu(
    app: ScopeAppInfo,
    module: ModuleUtil.InstalledModule,
    context: android.content.Context,
    showDialog: (ScopeDialog) -> Unit,
    showCompile: (android.content.pm.ApplicationInfo) -> Unit,
): List<Pair<String, () -> Unit>> {
    val system = app.packageName == "system"
    val userId = app.applicationInfo.uid / App.PER_USER_RANGE
    val entries = mutableListOf<Pair<String, () -> Unit>>()
    val launchIntent = AppHelper.getLaunchIntentForPackage(app.packageName, userId)
    if (launchIntent != null) {
        entries += context.getString(R.string.app_launch) to {
            ConfigManager.startActivityAsUserWithFeature(launchIntent, module.userId)
        }
    }
    if (system) {
        entries += context.getString(R.string.reboot) to { showDialog(ScopeDialog.Reboot) }
    } else {
        entries += context.getString(R.string.force_stop) to {
            showDialog(ScopeDialog.ForceStop(app.packageName, app.applicationInfo.uid))
        }
        entries += context.getString(R.string.compile_speed) to { showCompile(app.applicationInfo) }
        entries += context.getString(R.string.modules_other_app) to {
            val intent = Intent(Intent.ACTION_SHOW_APP_INFO)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, module.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ConfigManager.startActivityAsUserWithFeature(intent, module.userId)
        }
        entries += context.getString(R.string.module_app_info) to {
            ConfigManager.startActivityAsUserWithFeature(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
                module.userId,
            )
        }
    }
    return entries
}
