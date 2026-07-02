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

package org.lsposed.manager.ui.screen.settings

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.util.CloudflareDNS
import org.lsposed.manager.util.ThemeUtil

/**
 * Backs the Compose settings screen. Reads/writes the same SharedPreferences keys the legacy
 * PreferenceFragment used (so persisted state is fully compatible), and forwards the binder-direct
 * toggles (verbose log, dex obfuscate, notifications, hidden icons) to [ConfigManager] exactly as
 * before. UI-only reactivity is provided by reading the prefs on demand; the few preferences that
 * require a full theme/locale reload signal through [SettingsCallbacks.restart].
 */
class SettingsViewModel : ViewModel() {
    private val prefs = App.getPreferences()

    val installed: Boolean get() = ConfigManager.isBinderAlive()
    val isParasitic: Boolean get() = App.isParasitic
    val dynamicColorAvailable: Boolean get() = ThemeUtil.isDynamicColorAvailable()

    val subtitle: String
        get() = if (installed) {
            "${ConfigManager.getXposedVersionName()} (${ConfigManager.getXposedVersionCode()})"
        } else {
            val notInstalled = App.getInstance().getString(org.lsposed.manager.R.string.not_installed)
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) - $notInstalled"
        }

    // --- generic pref access (mirrors PreferenceManager default SharedPreferences) ---
    fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    // --- network ---
    /** The DoH preference group is hidden entirely when a system proxy is configured. */
    val dohVisible: Boolean
        get() = (App.getOkHttpClient().dns as? CloudflareDNS)?.noProxy ?: true

    fun setDoH(enabled: Boolean) {
        (App.getOkHttpClient().dns as? CloudflareDNS)?.DoH = enabled
        putBoolean("doh", enabled)
    }

    // --- framework toggles (binder-direct, fire and write-through) ---
    val verboseLogPrefChecked: Boolean get() = !installed || !ConfigManager.isVerboseLogEnabled()
    val verboseLogEnabledState: Boolean get() = !BuildConfig.DEBUG && installed

    init {
        if (BuildConfig.DEBUG) ConfigManager.setVerboseLogEnabled(false)
    }

    /** Pref is "disable verbose log", so checked == verbose disabled. Returns success. */
    fun setDisableVerboseLog(checked: Boolean): Boolean =
        ConfigManager.setVerboseLogEnabled(!checked)

    val dexObfuscateChecked: Boolean get() = !installed || ConfigManager.isDexObfuscateEnabled()
    fun setDexObfuscate(checked: Boolean): Boolean = ConfigManager.setDexObfuscateEnabled(checked)

    val notificationVisible: Boolean get() = installed
    val notificationChecked: Boolean get() = ConfigManager.enableStatusNotification()
    fun setNotification(checked: Boolean): Boolean = ConfigManager.setEnableStatusNotification(checked)

    // --- repo ---
    fun setUpdateChannel(channel: String) {
        putString("update_channel", channel)
        RepoLoader.getInstance().updateLatestVersion(channel)
    }

    // --- system (hidden icons) ---
    val showSystemGroup: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    fun hiddenIconsChecked(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "show_hidden_icon_apps_enabled", 1) != 0
    fun setHiddenIcons(checked: Boolean): Boolean = ConfigManager.setHiddenIcon(!checked)

    // --- theme helpers ---
    fun reboot(): Boolean = ConfigManager.reboot()
    fun darkThemeMode(): String = getString("dark_theme", ThemeUtil.MODE_NIGHT_FOLLOW_SYSTEM)
}
