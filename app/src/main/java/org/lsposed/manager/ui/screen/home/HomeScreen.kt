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

package org.lsposed.manager.ui.screen.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import org.lsposed.manager.R
import org.lsposed.lspd.ILSPManagerService

/**
 * Compose rewrite of the Home/Overview dashboard. Pure UI: it renders [HomeUiState] and emits
 * intents (about, feedback, update, copy info) through [actions]. Navigation, custom-tabs and the
 * about dialog stay in the hosting fragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.app_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feedback_or_suggestion)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                actions.onFeedback()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.About)) },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                actions.onAbout()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state)
            WarningCard(state)
            UpdateCard(state, actions)
            if (state.isDeveloper) DeveloperWarningCard()
            InfoCard(state, actions)
        }
    }
}

/** Callbacks the hosting fragment supplies. */
class HomeActions(
    val onAbout: () -> Unit,
    val onFeedback: () -> Unit,
    val onUpdate: () -> Unit,
    val onCopyInfo: (String) -> Unit,
)

private fun HomeUiState.statusAbnormal(): Boolean =
    !sepolicyLoaded || !systemServerRequested ||
        (dex2oatCompat != ILSPManagerService.DEX2OAT_OK && !dex2oatFlagsLoaded)

@Composable
private fun StatusCard(state: HomeUiState) {
    val (title, icon) = when {
        !state.binderAlive -> stringResource(R.string.not_installed) to Icons.Outlined.ErrorOutline
        state.statusAbnormal() -> stringResource(R.string.partial_activated) to Icons.Outlined.Warning
        else -> stringResource(R.string.activated) to Icons.Outlined.CheckCircle
    }
    val summary = if (state.binderAlive) {
        "${state.xposedVersionName} (${state.xposedVersionCode})"
    } else {
        stringResource(R.string.not_install_summary)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WarningCard(state: HomeUiState) {
    if (!state.binderAlive || !state.statusAbnormal()) return
    val titleRes: Int
    val bodyRes: Int
    // Match the legacy fragment's sequential-if precedence (last write wins):
    // dex2oat > systemServer > sepolicy.
    when {
        state.dex2oatCompat != ILSPManagerService.DEX2OAT_OK && !state.dex2oatFlagsLoaded -> {
            titleRes = R.string.system_prop_incorrect_summary
            bodyRes = R.string.system_prop_incorrect
        }
        !state.systemServerRequested -> {
            titleRes = R.string.system_inject_fail_summary
            bodyRes = R.string.system_inject_fail
        }
        else -> {
            titleRes = R.string.selinux_policy_not_loaded_summary
            bodyRes = R.string.selinux_policy_not_loaded
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(htmlText(bodyRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UpdateCard(state: HomeUiState, actions: HomeActions) {
    val titleRes: Int
    val summaryRes: Int
    when {
        state.binderAlive && state.needUpdate -> {
            titleRes = R.string.need_update
            summaryRes = R.string.please_update_summary
        }
        !state.binderAlive && state.magiskInstalled -> {
            titleRes = R.string.install
            summaryRes = R.string.install_summary
        }
        else -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summaryRes), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = actions.onUpdate) {
                    Text(stringResource(R.string.install))
                }
            }
        }
    }
}

@Composable
private fun DeveloperWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.developer_warning_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.developer_warning_summary), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoCard(state: HomeUiState, actions: HomeActions) {
    val notInstalled = stringResource(R.string.not_installed)
    val apiVersion = if (state.binderAlive) state.xposedApiVersion.toString() else notInstalled
    val api = if (state.binderAlive) {
        stringResource(if (state.dexObfuscateEnabled) R.string.enabled else R.string.not_enabled)
    } else notInstalled
    val frameworkVersion = if (state.binderAlive) {
        "${state.xposedVersionName} (${state.xposedVersionCode})"
    } else notInstalled
    val dex2oat = dex2oatText(state)
    val systemVersion = if (Build.VERSION.PREVIEW_SDK_INT != 0) {
        "${Build.VERSION.CODENAME} Preview (API ${Build.VERSION.SDK_INT})"
    } else {
        "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
    val device = deviceName()
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""

    val rows = listOf(
        stringResource(R.string.info_api_version) to apiVersion,
        stringResource(R.string.settings_xposed_api_call_protection) to api,
        stringResource(R.string.info_dex2oat_wrapper) to dex2oat,
        stringResource(R.string.info_framework_version) to frameworkVersion,
        stringResource(R.string.info_manager_package_name) to state.managerPackageName,
        stringResource(R.string.info_system_version) to systemVersion,
        stringResource(R.string.info_device) to device,
        stringResource(R.string.info_system_abi) to abi,
    )
    val copyText = rows.joinToString("\n\n") { (label, value) -> "$label\n$value" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            rows.forEach { (label, value) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { actions.onCopyInfo(copyText) }) {
                    Text(stringResource(android.R.string.copy))
                }
            }
        }
    }
}

@Composable
private fun dex2oatText(state: HomeUiState): String {
    if (!state.binderAlive) return stringResource(R.string.not_installed)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return "${stringResource(R.string.unsupported)} (${stringResource(R.string.android_version_unsatisfied)})"
    }
    val unsupported = stringResource(R.string.unsupported)
    return when (state.dex2oatCompat) {
        ILSPManagerService.DEX2OAT_OK -> stringResource(R.string.supported)
        ILSPManagerService.DEX2OAT_CRASHED -> "$unsupported (${stringResource(R.string.crashed)})"
        ILSPManagerService.DEX2OAT_MOUNT_FAILED -> "$unsupported (${stringResource(R.string.mount_failed)})"
        ILSPManagerService.DEX2OAT_SELINUX_PERMISSIVE -> "$unsupported (${stringResource(R.string.selinux_permissive)})"
        ILSPManagerService.DEX2OAT_SEPOLICY_INCORRECT -> "$unsupported (${stringResource(R.string.sepolicy_incorrect)})"
        else -> unsupported
    }
}

private fun deviceName(): String {
    var manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    if (Build.BRAND != Build.MANUFACTURER) {
        manufacturer += " " + Build.BRAND.replaceFirstChar { it.uppercase() }
    }
    manufacturer += " " + Build.MODEL + " "
    return manufacturer
}

/** Renders an HTML string resource (legacy mode) into an [AnnotatedString] for Compose Text. */
@Composable
private fun htmlText(resId: Int): AnnotatedString =
    AnnotatedString.fromHtml(stringResource(resId))

