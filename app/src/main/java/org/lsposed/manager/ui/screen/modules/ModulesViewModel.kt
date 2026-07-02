package org.lsposed.manager.ui.screen.modules

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.adapters.AppHelper
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule

data class ModuleListState(
    val loading: Boolean = true,
    val users: List<UserInfo> = emptyList(),
    /** Modules per user id, sorted and filtered. */
    val modulesByUser: Map<Int, List<InstalledModule>> = emptyMap(),
    val enabledCount: Int = -1,
)

class ModulesViewModel : ViewModel(), ModuleUtil.ModuleListener, RepoLoader.RepoListener {
    private val moduleUtil = ModuleUtil.getInstance()
    private val repoLoader = RepoLoader.getInstance()
    private val pm: PackageManager = App.getInstance().packageManager

    private val _state = MutableStateFlow(ModuleListState())
    val state: StateFlow<ModuleListState> = _state.asStateFlow()

    var query = ""
        private set

    init {
        moduleUtil.addListener(this)
        repoLoader.addListener(this)
        refresh()
    }

    override fun onCleared() {
        moduleUtil.removeListener(this)
        repoLoader.removeListener(this)
    }

    override fun onModulesReloaded() = refresh()
    override fun onSingleModuleReloaded(module: InstalledModule?) = refresh()
    override fun onRepoLoaded() = refresh()

    fun setQuery(q: String) {
        query = q
        refresh()
    }

    fun reload() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            moduleUtil.reloadInstalledModules()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { compute() }
            _state.value = result
        }
    }

    private fun compute(): ModuleListState {
        val users = moduleUtil.users?.sortedBy { it.id } ?: return ModuleListState(loading = false)
        val modules = moduleUtil.modules ?: return ModuleListState(loading = false, users = users)
        val cmp = AppHelper.getAppListComparator(0, pm)
        val q = query.lowercase()
        val byUser = mutableMapOf<Int, MutableList<InstalledModule>>()
        users.forEach { byUser[it.id] = mutableListOf() }
        modules.values
            .sortedWith { a, b ->
                val aOn = moduleUtil.isModuleEnabled(a.packageName)
                val bOn = moduleUtil.isModuleEnabled(b.packageName)
                if (aOn == bOn) cmp.compare(a.pkg, b.pkg) else if (aOn) -1 else 1
            }
            .forEach { m ->
                val list = byUser[m.userId] ?: return@forEach
                if (q.isEmpty() ||
                    m.getAppName().lowercase().contains(q) ||
                    m.packageName.lowercase().contains(q) ||
                    m.getDescription().lowercase().contains(q)
                ) list.add(m)
            }
        return ModuleListState(
            loading = false,
            users = users,
            modulesByUser = byUser,
            enabledCount = moduleUtil.enabledModulesCount,
        )
    }

    /**
     * Distinct modules (by package) that are installed for some user but NOT for [user] — i.e. the
     * candidates the multi-user install picker offers for that tab. Mirrors the legacy pick adapter.
     */
    fun installableModules(user: UserInfo): List<InstalledModule> {
        val modules = moduleUtil.modules ?: return emptyList()
        val cmp = AppHelper.getAppListComparator(0, pm)
        val seen = HashSet<String>()
        val result = ArrayList<InstalledModule>()
        modules.values
            // Same enabled-first ordering the list tab uses, so the picker matches the legacy adapter.
            .sortedWith { a, b ->
                val aOn = moduleUtil.isModuleEnabled(a.packageName)
                val bOn = moduleUtil.isModuleEnabled(b.packageName)
                if (aOn == bOn) cmp.compare(a.pkg, b.pkg) else if (aOn) -1 else 1
            }
            .forEach { m ->
                if (m.userId == user.id) return@forEach
                if (moduleUtil.getModule(m.packageName, user.id) != null) return@forEach
                if (seen.add(m.packageName)) result.add(m)
            }
        return result
    }
}
