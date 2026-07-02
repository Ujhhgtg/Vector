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

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ShortcutUtil
import org.lsposed.manager.util.UpdateUtil

/**
 * Backs the Compose shell's navigation badges, replacing the listener/badge bookkeeping that lived
 * in the legacy `MainActivity`. Listens to [RepoLoader] and [ModuleUtil] and exposes three counts:
 *  - [enabledModules]  → number badge on the Modules item
 *  - [upgradableModules] → number badge on the Repo item
 *  - [managerUpdate]   → dot badge on the Home item (a manager self-update is available)
 *
 * The shell also reads [binderAlive] / [magiskInstalled] to decide which destinations are reachable,
 * exactly as the legacy activity removed unavailable bottom-nav items.
 */
class ShellViewModel : ViewModel(), RepoLoader.RepoListener, ModuleUtil.ModuleListener {
    private val repoLoader = RepoLoader.getInstance()
    private val moduleUtil = ModuleUtil.getInstance()

    data class Badges(
        val enabledModules: Int = 0,
        val upgradableModules: Int = 0,
        val managerUpdate: Boolean = false,
    )

    private val _badges = MutableStateFlow(Badges())
    val badges: StateFlow<Badges> = _badges.asStateFlow()

    val binderAlive: Boolean get() = ConfigManager.isBinderAlive()
    val magiskInstalled: Boolean get() = ConfigManager.isMagiskInstalled()

    init {
        repoLoader.addListener(this)
        moduleUtil.addListener(this)
        recomputeModules()
        onRepoLoaded()
    }

    /** Recomputes module-derived badges; call from the shell's onResume to mirror the legacy flow. */
    fun refresh() {
        recomputeModules()
        _badges.value = _badges.value.copy(managerUpdate = UpdateUtil.needUpdate())
        // Parasitic installs refresh the launcher shortcut each resume, matching legacy MainActivity.
        if (App.isParasitic) {
            val updated = ShortcutUtil.updateShortcut()
            Log.d(App.TAG, "update shortcut success = $updated")
        }
    }

    private fun recomputeModules() {
        val count = if (binderAlive) moduleUtil.enabledModulesCount else 0
        _badges.value = _badges.value.copy(enabledModules = count.coerceAtLeast(0))
    }

    override fun onRepoLoaded() {
        val modules = moduleUtil.modules ?: run {
            _badges.value = _badges.value.copy(upgradableModules = 0)
            return
        }
        val processed = HashSet<String>()
        var count = 0
        modules.forEach { (key, value) ->
            val pkg = key.first
            if (processed.add(pkg)) {
                val ver = repoLoader.getModuleLatestVersion(pkg)
                if (ver != null && ver.upgradable(value.versionCode, value.versionName)) count++
            }
        }
        _badges.value = _badges.value.copy(upgradableModules = count)
    }

    override fun onThrowable(t: Throwable?) {
        _badges.value = _badges.value.copy(upgradableModules = 0)
    }

    override fun onModuleReleasesLoaded(module: OnlineModule?) {
        onRepoLoaded()
    }

    override fun onModulesReloaded() {
        onRepoLoaded()
        recomputeModules()
    }

    override fun onSingleModuleReloaded(module: ModuleUtil.InstalledModule?) {
        recomputeModules()
    }

    override fun onCleared() {
        repoLoader.removeListener(this)
        moduleUtil.removeListener(this)
    }
}
