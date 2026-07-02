package org.lsposed.manager.ui.screen.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.ui.component.AppIcon
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule

data class ModuleMenuEntry(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit,
)

class ModulesActions(
    val onModuleClick: (InstalledModule) -> Unit,
    /** Build the context-menu entries for a long-pressed module (intents/dialogs live in the fragment). */
    val contextMenuEntries: (InstalledModule) -> List<ModuleMenuEntry>,
    /** Install a module to a user, picked from the multi-user FAB dialog. */
    val onInstallToUser: (InstalledModule, org.lsposed.lspd.models.UserInfo) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModulesScreen(
    viewModel: ModulesViewModel,
    actions: ModulesActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    var pickerUser by remember { mutableStateOf<org.lsposed.lspd.models.UserInfo?>(null) }
    val users = state.users
    val pagerState = rememberPagerState(pageCount = { users.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    pickerUser?.let { user ->
        ModulePickerDialog(
            user = user,
            modules = viewModel.installableModules(user),
            onPick = { module ->
                pickerUser = null
                actions.onInstallToUser(module, user)
            },
            onDismiss = { pickerUser = null },
        )
    }

    val subtitle = if (state.enabledCount == -1) {
        stringResource(R.string.loading)
    } else {
        App.getInstance().resources.getQuantityString(
            R.plurals.modules_enabled_count, state.enabledCount, state.enabledCount,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = queryText,
                            onValueChange = { queryText = it; viewModel.setQuery(it) },
                            singleLine = true,
                            placeholder = { Text(stringResource(android.R.string.search_go)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text(stringResource(R.string.Modules))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) { queryText = ""; viewModel.setQuery("") }
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(android.R.string.search_go))
                    }
                },
            )
        },
        floatingActionButton = {
            // Multi-user only: install an existing module to the currently selected user's tab.
            if (users.size > 1) {
                val currentUser = users.getOrNull(pagerState.currentPage.coerceIn(0, users.size - 1))
                if (currentUser != null) {
                    FloatingActionButton(onClick = { pickerUser = currentUser }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.install_to_user, currentUser.name ?: ""))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            // Multi-user tab strip only when more than one user exists, matching the legacy behavior.
            if (users.size > 1) {
                TabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, users.size - 1)) {
                    users.forEachIndexed { index, user ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(user.name ?: user.id.toString()) },
                        )
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.reload() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (users.isEmpty()) {
                    EmptyState()
                } else {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = users.size > 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val user = users[page]
                        val modules = state.modulesByUser[user.id].orEmpty()
                        if (modules.isEmpty() && !state.loading) {
                            EmptyState()
                        } else {
                            // Match legacy: row taps are ignored until the list has finished loading.
                            ModuleList(modules, !state.loading, actions.onModuleClick, actions.contextMenuEntries)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ModulePickerDialog(
    user: org.lsposed.lspd.models.UserInfo,
    modules: List<InstalledModule>,
    onPick: (InstalledModule) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.install_to_user, user.name ?: user.id.toString())) },
        text = {
            if (modules.isEmpty()) {
                Text(
                    stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Constrain height so a long module list stays scrollable inside the dialog.
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(modules, key = { it.packageName }) { module ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(module) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(module.pkg, sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.roundToPx() }, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(module.getAppName(), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    module.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleList(
    modules: List<InstalledModule>,
    clickEnabled: Boolean,
    onClick: (InstalledModule) -> Unit,
    contextMenuEntries: (InstalledModule) -> List<ModuleMenuEntry>,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(modules, key = { (it.packageName + "!" + it.userId).hashCode() }) { module ->
            ModuleRow(module, clickEnabled, onClick, contextMenuEntries)
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleRow(
    module: InstalledModule,
    clickEnabled: Boolean,
    onClick: (InstalledModule) -> Unit,
    contextMenuEntries: (InstalledModule) -> List<ModuleMenuEntry>,
) {
    val enabled = ModuleUtil.getInstance().isModuleEnabled(module.packageName)
    val appName = if (module.userId != 0) "${module.getAppName()} (${module.userId})" else module.getAppName()
    val description = module.getDescription().ifEmpty { stringResource(R.string.module_empty_description) }
    val hint = moduleHint(module)
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (clickEnabled) onClick(module) },
                onLongClick = { menuExpanded = true },
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            contextMenuEntries(module).forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    leadingIcon = entry.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                    onClick = { menuExpanded = false; entry.onClick() },
                )
            }
        }
        AppIcon(module.pkg, sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.roundToPx() }, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(module.versionName ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun moduleHint(module: InstalledModule): androidx.compose.ui.text.AnnotatedString? {
    val errorColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    // Build warning text matching legacy precedence.
    val installedApi = org.lsposed.manager.ConfigManager.getXposedApiVersion()
    val warn: String? = when {
        module.minVersion == 0 -> stringResource(R.string.no_min_version_specified)
        installedApi > 0 && module.minVersion > installedApi -> stringResource(R.string.warning_xposed_min_version, module.minVersion)
        module.targetVersion > installedApi -> stringResource(R.string.warning_target_version_higher, module.targetVersion)
        module.minVersion < ModuleUtil.MIN_MODULE_VERSION -> stringResource(R.string.warning_min_version_too_low, module.minVersion, ModuleUtil.MIN_MODULE_VERSION)
        module.isInstalledOnExternalStorage -> stringResource(R.string.warning_installed_on_external_storage)
        else -> null
    }
    val ver = RepoLoader.getInstance().getModuleLatestVersion(module.packageName)
    val upgrade = if (ver != null && ver.upgradable(module.versionCode, module.versionName)) {
        stringResource(R.string.update_available, ver.versionName)
    } else null
    if (warn == null && upgrade == null) return null
    // Render warning in error color and the update line in primary color, each keeping its own color
    // even when both are present (legacy used two separately-colored spans).
    return androidx.compose.ui.text.buildAnnotatedString {
        if (warn != null) {
            withStyle(androidx.compose.ui.text.SpanStyle(color = errorColor)) { append(warn) }
        }
        if (upgrade != null) {
            if (warn != null) append("\n")
            withStyle(androidx.compose.ui.text.SpanStyle(color = primaryColor)) { append(upgrade) }
        }
    }
}
