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

import android.text.TextUtils
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
import org.lsposed.manager.repo.model.Release
import org.lsposed.manager.util.LocaleUtil

data class RepoItemState(
    val module: OnlineModule? = null,
    val readmeHtml: String? = null,
    val releases: List<Release> = emptyList(),
    val noMoreReleases: Boolean = false,
    val loadingMore: Boolean = false,
)

class RepoItemViewModel(private val packageName: String) : ViewModel(), RepoLoader.RepoListener {
    private val repoLoader = RepoLoader.getInstance()
    private val channels = App.getInstance().resources.getStringArray(org.lsposed.manager.R.array.update_channel_values)

    private val _state = MutableStateFlow(RepoItemState())
    val state: StateFlow<RepoItemState> = _state.asStateFlow()

    /** One-shot load-failure messages, surfaced by the route as a snackbar hint. */
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val errors: SharedFlow<Throwable> = _errors.asSharedFlow()

    private var remoteLoadRequested = false
    private var releaseLoadRequestedByUser = false

    init {
        repoLoader.addListener(this)
        refresh()
        loadRemoteIfReadmeMissing()
        reloadReleases()
    }

    override fun onCleared() {
        repoLoader.removeListener(this)
    }

    private fun currentModule(): OnlineModule? =
        repoLoader.getOnlineModule(packageName) ?: _state.value.module

    private fun hasReadme(m: OnlineModule?): Boolean =
        m != null && (!TextUtils.isEmpty(m.readmeHTML) || !TextUtils.isEmpty(m.readme))

    private fun moduleReadme(m: OnlineModule?): String? {
        m ?: return null
        return m.readmeHTML?.takeIf { it.isNotEmpty() } ?: m.readme
    }

    private fun refresh() {
        val m = currentModule()
        _state.value = _state.value.copy(module = m, readmeHtml = moduleReadme(m))
    }

    private fun loadRemoteIfReadmeMissing() {
        val m = currentModule() ?: return
        if (m.name == null) return
        if (remoteLoadRequested || m.releasesLoaded || hasReadme(m)) return
        remoteLoadRequested = true
        viewModelScope.launch(Dispatchers.IO) { repoLoader.loadRemoteReleases(m.name) }
    }

    /** User tapped "load more releases" in the list footer. */
    fun loadMoreReleases() {
        val m = currentModule() ?: return
        releaseLoadRequestedByUser = true
        _state.value = _state.value.copy(loadingMore = true)
        viewModelScope.launch(Dispatchers.IO) { repoLoader.loadRemoteReleases(m.name) }
    }

    fun reloadReleases() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { computeReleases() }
            _state.value = _state.value.copy(releases = list)
        }
    }

    private fun computeReleases(): List<Release> {
        val m = currentModule() ?: return emptyList()
        val channel = App.getPreferences().getString("update_channel", channels[0])
        val releases = repoLoader.getReleases(m.name) ?: m.releases ?: return emptyList()
        return when (channel) {
            channels[0] -> releases.filter { t ->
                if (java.lang.Boolean.TRUE == t.isPrerelease) return@filter false
                val name = t.name?.lowercase(LocaleUtil.getDefaultLocale())
                !(name != null && (name.startsWith("snapshot") || name.startsWith("nightly")))
            }
            channels[1] -> releases.filter { t ->
                val name = t.name?.lowercase(LocaleUtil.getDefaultLocale())
                !(name != null && (name.startsWith("snapshot") || name.startsWith("nightly")))
            }
            else -> releases
        }
    }

    // --- RepoListener ---
    override fun onRepoLoaded() {
        val m = currentModule()
        if (!hasReadme(m)) remoteLoadRequested = false
        refresh()
        loadRemoteIfReadmeMissing()
        reloadReleases()
    }

    override fun onModuleReleasesLoaded(module: OnlineModule?) {
        val current = _state.value.module ?: return
        if (module == null || !TextUtils.equals(current.name, module.name)) return
        refresh()
        reloadReleases()
        val count = repoLoader.getReleases(module.name)?.size ?: 1
        if (releaseLoadRequestedByUser && count == 1) {
            _state.value = _state.value.copy(noMoreReleases = true)
        }
        releaseLoadRequestedByUser = false
        // Clear the footer spinner now that a load round-trip completed.
        _state.value = _state.value.copy(loadingMore = false)
    }

    override fun onThrowable(t: Throwable) {
        remoteLoadRequested = false
        releaseLoadRequestedByUser = false
        _errors.tryEmit(t)
        _state.value = _state.value.copy(loadingMore = false)
        reloadReleases()
    }

    fun consumeNoMore() {
        _state.value = _state.value.copy(noMoreReleases = false)
    }
}
