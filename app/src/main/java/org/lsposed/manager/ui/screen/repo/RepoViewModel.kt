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

package org.lsposed.manager.ui.screen.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.App
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.util.ModuleUtil
import rikka.core.util.LabelComparator
import java.time.Instant

data class RepoListState(
    val loading: Boolean = true,
    val modules: List<OnlineModule> = emptyList(),
    val upgradableCount: Int = -1,
)

class RepoViewModel : ViewModel(), RepoLoader.RepoListener, ModuleUtil.ModuleListener {
    private val repoLoader = RepoLoader.getInstance()
    private val moduleUtil = ModuleUtil.getInstance()
    private val labelComparator = LabelComparator()
    private val channels = App.getInstance().resources.getStringArray(org.lsposed.manager.R.array.update_channel_values)

    private val _state = MutableStateFlow(RepoListState())
    val state: StateFlow<RepoListState> = _state.asStateFlow()

    /** One-shot load-failure messages, surfaced by the route as a snackbar hint. */
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val errors: SharedFlow<Throwable> = _errors.asSharedFlow()

    var query: String = ""
        private set

    init {
        repoLoader.addListener(this)
        moduleUtil.addListener(this)
        refresh()
    }

    override fun onCleared() {
        repoLoader.removeListener(this)
        moduleUtil.removeListener(this)
    }

    // --- RepoListener / ModuleListener ---
    override fun onRepoLoaded() = refresh()
    override fun onModulesReloaded() = refresh()
    override fun onThrowable(t: Throwable) {
        // Surface the load failure (legacy RepoFragment.onThrowable) and clear the refresh spinner.
        _errors.tryEmit(t)
        _state.value = _state.value.copy(loading = false)
        refresh()
    }

    fun setQuery(q: String) {
        query = q
        // Local re-filter only: must not toggle the pull-to-refresh spinner (legacy used Filter).
        recompute()
    }

    /** Pull-to-refresh: reload remote data then recompute. This is the only path that spins. */
    fun reload() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repoLoader.loadRemoteData() }
            recompute()
        }
    }

    /**
     * Recomputes the list off the persisted prefs/query without forcing the refresh spinner. The
     * spinner is only shown while the repo has never finished loading (compute() returns loading).
     */
    fun refresh() = recompute()

    private fun recompute() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { compute() }
            _state.value = result
        }
    }

    private fun upgradableVer(module: OnlineModule): RepoLoader.ModuleVersion? {
        val installed = moduleUtil.getModule(module.name) ?: return null
        val ver = repoLoader.getModuleLatestVersion(installed.packageName)
        return if (ver != null && ver.upgradable(installed.versionCode, installed.versionName)) ver else null
    }

    private fun compute(): RepoListState {
        if (!repoLoader.isRepoLoaded) {
            return RepoListState(loading = true, modules = emptyList(), upgradableCount = -1)
        }
        val channel = App.getPreferences().getString("update_channel", channels[0])
        val sort = App.getPreferences().getInt("repo_sort", 0)
        val upgradableFirst = App.getPreferences().getBoolean("upgradable_first", true)

        val online = repoLoader.onlineModules ?: emptyList()
        val full = online
            .filter { m ->
                !m.isHide && !(repoLoader.getReleases(m.name)?.isEmpty() == true)
            }
            .sortedWith(Comparator { a, b ->
                if (upgradableFirst) {
                    val au = upgradableVer(a) != null
                    val bu = upgradableVer(b) != null
                    if (au && !bu) return@Comparator -1
                    if (!au && bu) return@Comparator 1
                }
                if (sort == 0) {
                    labelComparator.compare(a.description, b.description)
                } else {
                    Instant.parse(repoLoader.getLatestReleaseTime(b.name, channel))
                        .compareTo(Instant.parse(repoLoader.getLatestReleaseTime(a.name, channel)))
                }
            })

        val filtered = if (query.isBlank()) full else full.filter { m ->
            val f = query.lowercase()
            m.description?.lowercase()?.contains(f) == true ||
                m.name?.lowercase()?.contains(f) == true ||
                m.summary?.lowercase()?.contains(f) == true
        }

        return RepoListState(loading = false, modules = filtered, upgradableCount = computeUpgradableCount())
    }

    private fun computeUpgradableCount(): Int {
        val modules = moduleUtil.modules ?: return -1
        if (!repoLoader.isRepoLoaded) return -1
        val processed = HashSet<String>()
        var count = 0
        modules.forEach { (key, v) ->
            if (!processed.contains(key.first)) {
                val ver = repoLoader.getModuleLatestVersion(key.first)
                if (ver != null && ver.upgradable(v.versionCode, v.versionName)) count++
                processed.add(key.first)
            }
        }
        return count
    }

    /** Returns the upgradable version for a module, or null. Used by the row UI. */
    fun upgradable(module: OnlineModule): RepoLoader.ModuleVersion? = upgradableVer(module)

    fun isInstalled(module: OnlineModule): Boolean = moduleUtil.getModule(module.name) != null
}
