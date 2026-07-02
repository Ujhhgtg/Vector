package org.lsposed.manager.ui.screen.scope

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lsposed.manager.R
import org.lsposed.manager.ui.component.AppIcon

class ScopeActions(
    val onBack: () -> Unit,
    val onMasterSwitch: (Boolean) -> Unit,
    val onToggleApp: (ScopeAppInfo, Boolean) -> Unit,
    val contextMenuEntries: (ScopeAppInfo) -> List<Pair<String, () -> Unit>>,
    val menuActions: ScopeMenuActions,
    /** Launches the module's own settings activity; null hides the FAB. */
    val onOpenModuleSettings: (() -> Unit)? = null,
)

class ScopeMenuActions(
    val useRecommended: () -> Unit,
    val selectAll: () -> Unit,
    val selectNone: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScopeScreen(
    title: String,
    subtitle: String,
    viewModel: AppScopeViewModel,
    actions: ScopeActions,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var contextApp by remember { mutableStateOf<ScopeAppInfo?>(null) }

    contextApp?.let { app ->
        ScopeContextSheet(
            title = app.label.toString(),
            entries = actions.contextMenuEntries(app),
            onDismiss = { contextApp = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
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
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    ScopeOverflowMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        hasScopeList = viewModel.hasScopeList,
                        modulePackageName = viewModel.module.packageName,
                        onChanged = { viewModel.refresh(false) },
                        menuActions = actions.menuActions,
                    )
                },
            )
        },
        floatingActionButton = {
            actions.onOpenModuleSettings?.let { open ->
                FloatingActionButton(onClick = open) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.module_settings))
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            MasterSwitch(state.enabled, actions.onMasterSwitch)
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.refresh(true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!state.loading && state.apps.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.list_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.apps, key = {
                            (it.packageName + "!" + it.applicationInfo.uid / org.lsposed.manager.App.PER_USER_RANGE).hashCode()
                        }) { app ->
                            ScopeRow(
                                app = app,
                                checked = state.checked.contains(app.application),
                                recommended = state.recommended.isNotEmpty() && state.recommended.contains(app.application),
                                enabled = state.enabled,
                                onToggle = { actions.onToggleApp(app, it) },
                                onLongClick = { contextApp = app },
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    // A rounded, inset card whose color reflects the on/off state, replacing the full-width flat bar.
    val container by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "masterSwitchContainer",
    )
    val content = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier
                .clickable { onChange(!enabled) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                contentDescription = null,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                // Static label matching the legacy MainSwitchBar ("Enable module"), not a toggling caption.
                stringResource(R.string.enable_module),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScopeRow(
    app: ScopeAppInfo,
    checked: Boolean,
    recommended: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val system = app.packageName == "system"
    val name = if (system) stringResource(R.string.android_framework) else app.label.toString()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (enabled) onToggle(!checked) },
                onLongClick = onLongClick,
            )
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageInfo, sizePx = with(LocalDensity.current) { 48.dp.roundToPx() }, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (system) "system" else app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The legacy row showed the version line for every non-framework app.
            if (!system) {
                Text(
                    stringResource(R.string.app_version, app.packageInfo.versionName ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (recommended) {
                Text(
                    stringResource(R.string.requested_by_module),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = checked, onCheckedChange = { if (enabled) onToggle(it) }, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeContextSheet(
    title: String,
    entries: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        entries.forEach { (label, action) ->
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable { action(); onDismiss() }.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        Spacer(Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun ScopeOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasScopeList: Boolean,
    modulePackageName: String,
    onChanged: () -> Unit,
    menuActions: ScopeMenuActions,
) {
    val prefs = org.lsposed.manager.App.getPreferences()
    var filterSystem by remember { mutableStateOf(prefs.getBoolean("filter_system_apps", true)) }
    var filterGames by remember { mutableStateOf(prefs.getBoolean("filter_games", true)) }
    var filterModules by remember { mutableStateOf(prefs.getBoolean("filter_modules", true)) }
    var autoInclude by remember { mutableStateOf(org.lsposed.manager.ConfigManager.getAutoInclude(modulePackageName)) }
    var sort by remember { mutableStateOf(prefs.getInt("list_sort", 0)) }

    // Compose has no native cascading submenu, so the original XML's nested menus (Select / Hide /
    // Sort / Backup & restore) are reproduced by swapping the dropdown's content: tapping a group
    // header descends into it, and a back row returns to the root. Reset to root whenever reopened.
    var submenu by remember(expanded) { mutableStateOf(ScopeSubmenu.ROOT) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (submenu) {
            ScopeSubmenu.ROOT -> {
                SubmenuHeader(stringResource(R.string.menu_select)) { submenu = ScopeSubmenu.SELECT }
                SubmenuHeader(stringResource(R.string.menu_hide)) { submenu = ScopeSubmenu.HIDE }
                SubmenuHeader(stringResource(R.string.menu_sort)) { submenu = ScopeSubmenu.SORT }
                SubmenuHeader(stringResource(R.string.menu_backup_and_restore)) { submenu = ScopeSubmenu.BACKUP }
            }

            ScopeSubmenu.SELECT -> {
                SubmenuBack(stringResource(R.string.menu_select)) { submenu = ScopeSubmenu.ROOT }
                HorizontalDivider()
                if (hasScopeList) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.use_recommended)) }, onClick = { onDismiss(); menuActions.useRecommended() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_select_all)) }, onClick = { onDismiss(); menuActions.selectAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_select_none)) }, onClick = { onDismiss(); menuActions.selectNone() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_auto_include)) },
                    trailingIcon = { Checkbox(checked = autoInclude, onCheckedChange = null) },
                    onClick = {
                        autoInclude = !autoInclude
                        org.lsposed.manager.ConfigManager.setAutoInclude(modulePackageName, autoInclude)
                        onChanged()
                    },
                )
            }

            ScopeSubmenu.HIDE -> {
                SubmenuBack(stringResource(R.string.menu_hide)) { submenu = ScopeSubmenu.ROOT }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_show_games)) },
                    trailingIcon = { Checkbox(checked = filterGames, onCheckedChange = null) },
                    onClick = { filterGames = !filterGames; prefs.edit().putBoolean("filter_games", filterGames).apply(); onChanged() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_show_modules)) },
                    trailingIcon = { Checkbox(checked = filterModules, onCheckedChange = null) },
                    onClick = { filterModules = !filterModules; prefs.edit().putBoolean("filter_modules", filterModules).apply(); onChanged() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_show_system_apps)) },
                    trailingIcon = { Checkbox(checked = filterSystem, onCheckedChange = null) },
                    onClick = { filterSystem = !filterSystem; prefs.edit().putBoolean("filter_system_apps", filterSystem).apply(); onChanged() },
                )
            }

            ScopeSubmenu.SORT -> {
                SubmenuBack(stringResource(R.string.menu_sort)) { submenu = ScopeSubmenu.ROOT }
                HorizontalDivider()
                // Selecting a category preserves the current asc/desc bit (legacy: i % 2). Reverse is
                // a separate toggle that flips that bit, exactly like the old options menu.
                SortItem(stringResource(R.string.sort_by_name), sort, 0) { sort = it; prefs.edit().putInt("list_sort", it).apply(); onChanged() }
                SortItem(stringResource(R.string.sort_by_package_name), sort, 2) { sort = it; prefs.edit().putInt("list_sort", it).apply(); onChanged() }
                SortItem(stringResource(R.string.sort_by_install_time), sort, 4) { sort = it; prefs.edit().putInt("list_sort", it).apply(); onChanged() }
                SortItem(stringResource(R.string.sort_by_update_time), sort, 6) { sort = it; prefs.edit().putInt("list_sort", it).apply(); onChanged() }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_reverse)) },
                    trailingIcon = { Checkbox(checked = sort % 2 == 1, onCheckedChange = null) },
                    onClick = {
                        val next = if (sort % 2 == 0) sort + 1 else sort - 1
                        sort = next; prefs.edit().putInt("list_sort", next).apply(); onChanged()
                    },
                )
            }

            ScopeSubmenu.BACKUP -> {
                SubmenuBack(stringResource(R.string.menu_backup_and_restore)) { submenu = ScopeSubmenu.ROOT }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_backup)) }, onClick = { onDismiss(); menuActions.backup() })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_restore)) }, onClick = { onDismiss(); menuActions.restore() })
            }
        }
    }
}

private enum class ScopeSubmenu { ROOT, SELECT, HIDE, SORT, BACKUP }

@Composable
private fun SubmenuHeader(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun SubmenuBack(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.titleSmall) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun SortItem(label: String, current: Int, base: Int, onSelect: (Int) -> Unit) {
    // asc value is `base`, desc value is `base + 1`. "Selected" means the current sort is this pair.
    val selected = current == base || current == base + 1
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (selected) {
                Icon(
                    if (current == base + 1) Icons.Filled.KeyboardArrowDown
                    else Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                )
            }
        },
        onClick = {
            // Preserve the current asc/desc bit when switching category (legacy: i % 2).
            onSelect(base + (current % 2))
        },
    )
}
