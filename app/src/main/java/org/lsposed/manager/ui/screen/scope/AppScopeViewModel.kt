package org.lsposed.manager.ui.screen.scope

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lspd.models.Application
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.adapters.AppHelper
import org.lsposed.manager.util.ApplicationWithEquals
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule

data class ScopeAppInfo(
    val packageInfo: PackageInfo,
    val application: ApplicationWithEquals,
    val applicationInfo: ApplicationInfo,
    val packageName: String,
    val label: CharSequence,
)

data class ScopeState(
    val loading: Boolean = true,
    val enabled: Boolean = true,
    val apps: List<ScopeAppInfo> = emptyList(),
    val checked: Set<ApplicationWithEquals> = emptySet(),
    val recommended: Set<ApplicationWithEquals> = emptySet(),
)

class AppScopeViewModel(val module: InstalledModule) : ViewModel() {
    private val moduleUtil = ModuleUtil.getInstance()
    private val pm: PackageManager = App.getInstance().packageManager
    private val prefs = App.getPreferences()

    private val _state = MutableStateFlow(ScopeState())
    val state: StateFlow<ScopeState> = _state.asStateFlow()

    var query = ""
        private set

    init { refresh(false) }

    fun setQuery(q: String) { query = q; refresh(false) }

    fun refresh(force: Boolean) {
        _state.value = _state.value.copy(loading = true, enabled = moduleUtil.isModuleEnabled(module.packageName))
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { load(force) }
            _state.value = result
        }
    }

    private fun shouldHide(info: PackageInfo, app: ApplicationWithEquals, checked: Set<ApplicationWithEquals>): Boolean {
        if (info.packageName == "system") return false
        if (checked.contains(app)) return false
        val ai = info.applicationInfo ?: return false
        if (prefs.getBoolean("filter_modules", true)) {
            if (moduleUtil.getModule(info.packageName, ai.uid / App.PER_USER_RANGE) != null) return true
        }
        if (prefs.getBoolean("filter_games", true)) {
            if (ai.category == ApplicationInfo.CATEGORY_GAME) return true
            @Suppress("DEPRECATION")
            if (ai.flags and ApplicationInfo.FLAG_IS_GAME != 0) return true
        }
        return prefs.getBoolean("filter_system_apps", true) && (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0)
    }

    private fun sortComparator(checked: Set<ApplicationWithEquals>, recommended: Set<ApplicationWithEquals>): Comparator<ScopeAppInfo> {
        val cmp = AppHelper.getAppListComparator(prefs.getInt("list_sort", 0), pm)
        val framework = Comparator<ScopeAppInfo> { a, b ->
            val aSys = a.packageName == "system"
            val bSys = b.packageName == "system"
            if (aSys == bSys) cmp.compare(a.packageInfo, b.packageInfo) else if (aSys) -1 else 1
        }
        val rec = Comparator<ScopeAppInfo> { a, b ->
            val ar = recommended.isNotEmpty() && recommended.contains(a.application)
            val br = recommended.isNotEmpty() && recommended.contains(b.application)
            if (ar == br) framework.compare(a, b) else if (ar) -1 else 1
        }
        return Comparator { a, b ->
            val ac = checked.contains(a.application)
            val bc = checked.contains(b.application)
            if (ac == bc) rec.compare(a, b) else if (ac) -1 else 1
        }
    }

    private fun load(force: Boolean): ScopeState {
        val appList = AppHelper.getAppList(force)
        val tmpRec = HashSet<ApplicationWithEquals>()
        var tmpChk = HashSet(ConfigManager.getModuleScope(module.packageName))
        val installed = HashSet<ApplicationWithEquals>()
        val scopeList = module.scopeList
        val list = ArrayList<ScopeAppInfo>()
        for (info in appList) {
            val ai = info.applicationInfo ?: continue
            val userId = ai.uid / App.PER_USER_RANGE
            val packageName = info.packageName
            if (packageName == "system" && userId != 0 ||
                packageName == module.packageName ||
                packageName == BuildConfig.APPLICATION_ID
            ) continue
            val application = ApplicationWithEquals(packageName, userId)
            installed.add(application)
            if (userId != module.userId) continue
            if (scopeList != null && scopeList.contains(packageName)) {
                tmpRec.add(application)
            } else if (shouldHide(info, application, tmpChk)) {
                continue
            }
            list.add(
                ScopeAppInfo(
                    packageInfo = info,
                    application = application,
                    applicationInfo = ai,
                    packageName = packageName,
                    label = AppHelper.getAppLabel(info, pm),
                ),
            )
        }
        tmpChk.retainAll(installed)
        val sorted = list.sortedWith(sortComparator(tmpChk, tmpRec))
        val q = query.lowercase()
        val filtered = if (q.isEmpty()) sorted else sorted.filter {
            it.label.toString().lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
        return ScopeState(
            loading = false,
            enabled = moduleUtil.isModuleEnabled(module.packageName),
            apps = filtered,
            checked = tmpChk,
            recommended = tmpRec,
        )
    }

    /** Toggle a single app's scope; returns true on success. */
    fun toggleApp(app: ScopeAppInfo, checked: Boolean): Boolean {
        val tmp = HashSet(_state.value.checked)
        if (checked) tmp.add(app.application) else tmp.remove(app.application)
        return if (ConfigManager.setModuleScope(module.packageName, module.legacy, tmp)) {
            _state.value = _state.value.copy(checked = tmp)
            true
        } else {
            false
        }
    }

    fun setMasterSwitch(isChecked: Boolean): Boolean {
        var ok = moduleUtil.setModuleEnabled(module.packageName, isChecked)
        if (ok) _state.value = _state.value.copy(enabled = isChecked)
        val tmpChk = HashSet(_state.value.checked)
        if (isChecked && tmpChk.isNotEmpty() &&
            !ConfigManager.setModuleScope(module.packageName, module.legacy, tmpChk)
        ) {
            moduleUtil.setModuleEnabled(module.packageName, false)
            _state.value = _state.value.copy(enabled = false)
            ok = false
        }
        return ok
    }

    fun checkRecommended() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tmp = HashSet(_state.value.checked)
                tmp.removeIf { it.userId == module.userId }
                tmp.addAll(_state.value.recommended)
                ConfigManager.setModuleScope(module.packageName, module.legacy, tmp)
                _state.value = _state.value.copy(checked = tmp)
            }
            refresh(false)
        }
    }

    fun selectAll(): Boolean {
        var rebootNeeded = false
        val tmp = HashSet(ConfigManager.getModuleScope(module.packageName))
        for (info in _state.value.apps) {
            if (info.packageName == "android") rebootNeeded = true
            tmp.add(info.application)
        }
        ConfigManager.setModuleScope(module.packageName, module.legacy, tmp)
        refresh(false)
        return rebootNeeded
    }

    fun selectNone(): Boolean {
        var rebootNeeded = false
        val tmp = HashSet(ConfigManager.getModuleScope(module.packageName))
        for (info in _state.value.apps) {
            if (tmp.remove(info.application) && info.packageName == "android") rebootNeeded = true
        }
        ConfigManager.setModuleScope(module.packageName, module.legacy, tmp)
        refresh(false)
        return rebootNeeded
    }

    val hasScopeList: Boolean get() = !module.scopeList.isNullOrEmpty()
    fun isLoaded(): Boolean = !_state.value.loading
}
