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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.ui.component.ListPreference
import org.lsposed.manager.ui.component.Preference
import org.lsposed.manager.ui.component.PreferenceCategory
import org.lsposed.manager.ui.component.SwitchPreference
import org.lsposed.manager.util.LangList
import org.lsposed.manager.util.ThemeUtil
import java.util.Locale

/** Callbacks the hosting fragment provides for side effects that need Activity/Fragment scope. */
class SettingsCallbacks(
    val restart: () -> Unit,
    val setDefaultNightMode: (String) -> Unit,
    val applyLocale: (String) -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
    val addShortcut: () -> Unit,
    val openTranslation: () -> Unit,
    val rebootHint: () -> Unit,
    val shortcutSupported: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    callbacks: SettingsCallbacks,
) {
    val installed = viewModel.installed
    // A monotonically increasing tick lets imperative pref writes trigger recomposition so summaries
    // and dependent visibility (e.g. accent picker vs. follow-system) refresh immediately.
    var tick by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { tick++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.Settings))
                        Text(
                            text = viewModel.subtitle,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        @Suppress("UNUSED_EXPRESSION") tick // read so the column recomposes on refresh()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            NetworkSection(viewModel, tick, refresh)
            LanguageSection(viewModel, callbacks, tick, refresh)
            ThemeSection(viewModel, callbacks, tick, refresh)
            FrameworkSection(viewModel, callbacks, tick, refresh)
            RepoSection(viewModel, tick, refresh)
            BackupSection(viewModel, callbacks)
            SystemSection(viewModel, tick, refresh)
        }
    }
}

@Composable
private fun NetworkSection(viewModel: SettingsViewModel, @Suppress("UNUSED_PARAMETER") tick: Int, refresh: () -> Unit) {
    if (!viewModel.dohVisible) return
    PreferenceCategory(stringResource(R.string.group_network))
    SwitchPreference(
        title = stringResource(R.string.dns_over_http),
        summary = stringResource(R.string.dns_over_http_summary),
        icon = painterResource(R.drawable.ic_outline_dns_24),
        // App.onCreate always persists "doh" before any UI shows, so this default is never hit;
        // kept as false to match prefs.xml/CloudflareDNS literal defaults.
        checked = viewModel.getBoolean("doh", false),
        onCheckedChange = {
            viewModel.setDoH(it)
            refresh()
        },
    )
}

@Composable
private fun LanguageSection(
    viewModel: SettingsViewModel,
    callbacks: SettingsCallbacks,
    @Suppress("UNUSED_PARAMETER") tick: Int,
    refresh: () -> Unit,
) {
    PreferenceCategory(stringResource(R.string.settings_language))

    val followSystem = stringResource(rikka.core.R.string.follow_system)
    val values = LangList.LOCALES.toList()
    val entries: List<CharSequence> = values.map { lang ->
        if (lang == "SYSTEM") {
            followSystem
        } else {
            val locale = Locale.forLanguageTag(lang)
            AnnotatedString.fromHtml(locale.getDisplayName(locale))
        }
    }
    val current = viewModel.getString("language", "SYSTEM")
    ListPreference(
        title = stringResource(R.string.settings_language),
        icon = painterResource(R.drawable.ic_outline_language_24),
        entries = entries,
        entryValues = values,
        selectedValue = current,
        onValueChange = { newValue ->
            viewModel.putString("language", newValue)
            callbacks.applyLocale(newValue)
            callbacks.restart()
        },
    )

    val translators = remember {
        AnnotatedString.fromHtml(App.getInstance().getString(R.string.translators))
    }
    if (translators.text != "null") {
        Preference(
            title = stringResource(R.string.settings_translation_contributors),
            summary = translators,
            icon = painterResource(R.drawable.ic_outline_groups_24),
        )
    }
    Preference(
        title = stringResource(R.string.settings_translation),
        summary = stringResource(R.string.settings_translation_summary, stringResource(R.string.app_name)),
        icon = painterResource(R.drawable.ic_outline_translate_24),
        onClick = callbacks.openTranslation,
    )
}

@Composable
private fun ThemeSection(
    viewModel: SettingsViewModel,
    callbacks: SettingsCallbacks,
    @Suppress("UNUSED_PARAMETER") tick: Int,
    refresh: () -> Unit,
) {
    PreferenceCategory(stringResource(R.string.settings_group_theme))

    val followAccent = viewModel.getBoolean("follow_system_accent", true)
    if (viewModel.dynamicColorAvailable) {
        SwitchPreference(
            title = stringResource(R.string.theme_color_system),
            icon = painterResource(R.drawable.ic_outline_palette_24),
            checked = followAccent,
            onCheckedChange = {
                viewModel.putBoolean("follow_system_accent", it)
                refresh()
                callbacks.restart()
            },
        )
    }

    // The accent picker is hidden while the system accent is being followed (dynamic color on).
    val accentVisible = !(viewModel.dynamicColorAvailable && followAccent)
    if (accentVisible) {
        val context = LocalContext.current
        val colorTexts = remember { context.resources.getStringArray(R.array.color_texts).toList() }
        val colorValues = remember { context.resources.getStringArray(R.array.color_values).toList() }
        ListPreference(
            title = stringResource(R.string.theme_color),
            icon = painterResource(R.drawable.ic_outline_format_color_fill_24),
            entries = colorTexts,
            entryValues = colorValues,
            selectedValue = viewModel.getString("theme_color", "MATERIAL_BLUE"),
            onValueChange = {
                viewModel.putString("theme_color", it)
                callbacks.restart()
            },
        )
    }

    val context = LocalContext.current
    val themeTexts = remember { context.resources.getStringArray(R.array.theme_texts).toList() }
    val themeValues = remember { context.resources.getStringArray(R.array.theme_values).toList() }
    ListPreference(
        title = stringResource(rikka.core.R.string.dark_theme),
        icon = painterResource(R.drawable.ic_outline_dark_mode_24),
        entries = themeTexts,
        entryValues = themeValues,
        selectedValue = viewModel.getString("dark_theme", ThemeUtil.MODE_NIGHT_FOLLOW_SYSTEM),
        onValueChange = { newValue ->
            if (viewModel.darkThemeMode() != newValue) {
                viewModel.putString("dark_theme", newValue)
                callbacks.setDefaultNightMode(newValue)
            } else {
                viewModel.putString("dark_theme", newValue)
            }
            refresh()
        },
    )

    val isNight = rikka.core.util.ResourceUtils.isNightMode(LocalConfiguration.current)
    SwitchPreference(
        title = stringResource(R.string.pure_black_dark_theme),
        summary = stringResource(R.string.pure_black_dark_theme_summary),
        icon = painterResource(R.drawable.ic_outline_invert_colors_24),
        checked = viewModel.getBoolean("black_dark_theme", false),
        onCheckedChange = {
            viewModel.putBoolean("black_dark_theme", it)
            // Only an immediate restart matters when we are currently in night mode.
            if (isNight) callbacks.restart()
        },
    )
}

@Composable
private fun FrameworkSection(
    viewModel: SettingsViewModel,
    callbacks: SettingsCallbacks,
    @Suppress("UNUSED_PARAMETER") tick: Int,
    refresh: () -> Unit,
) {
    PreferenceCategory(stringResource(R.string.settings_group_framework))

    // "disable_verbose_log": checked == verbose disabled. Disabled in debug builds.
    SwitchPreference(
        title = stringResource(R.string.settings_disable_verbose_log),
        summary = stringResource(R.string.settings_disable_verbose_log_summary),
        icon = painterResource(R.drawable.ic_outline_assignment_24),
        enabled = viewModel.verboseLogEnabledState,
        checked = viewModel.verboseLogPrefChecked,
        onCheckedChange = {
            viewModel.setDisableVerboseLog(it)
            refresh()
        },
    )

    SwitchPreference(
        title = stringResource(R.string.settings_xposed_api_call_protection),
        summary = stringResource(R.string.settings_xposed_api_call_protection_summary),
        icon = painterResource(R.drawable.ic_outline_shield_24),
        enabled = viewModel.installed,
        checked = viewModel.dexObfuscateChecked,
        onCheckedChange = {
            viewModel.setDexObfuscate(it)
            callbacks.rebootHint()
            refresh()
        },
    )

    if (viewModel.isParasitic) {
        Preference(
            title = stringResource(R.string.create_shortcut),
            summary = if (callbacks.shortcutSupported) {
                stringResource(R.string.settings_create_shortcut_summary)
            } else {
                stringResource(R.string.settings_unsupported_pin_shortcut_summary)
            },
            icon = painterResource(R.drawable.ic_outline_app_shortcut_24),
            enabled = callbacks.shortcutSupported,
            onClick = callbacks.addShortcut,
        )
    }

    if (viewModel.notificationVisible) {
        val notifChecked = viewModel.notificationChecked
        SwitchPreference(
            title = stringResource(R.string.settings_enable_status_notification),
            // Legacy used summaryOn: the summary is shown only while enabled.
            summary = if (notifChecked) stringResource(R.string.settings_enable_status_notification_summary) else null,
            icon = painterResource(R.drawable.ic_outline_speaker_notes_24),
            checked = notifChecked,
            onCheckedChange = {
                viewModel.setNotification(it)
                refresh()
            },
        )
    }
}

@Composable
private fun RepoSection(viewModel: SettingsViewModel, @Suppress("UNUSED_PARAMETER") tick: Int, refresh: () -> Unit) {
    PreferenceCategory(stringResource(R.string.settings_group_repo))
    val context = LocalContext.current
    val texts = remember { context.resources.getStringArray(R.array.update_channel_texts).toList() }
    val values = remember { context.resources.getStringArray(R.array.update_channel_values).toList() }
    ListPreference(
        title = stringResource(R.string.settings_update_channel),
        icon = painterResource(R.drawable.ic_outline_merge_type_24),
        entries = texts,
        entryValues = values,
        selectedValue = viewModel.getString("update_channel", "CHANNEL_STABLE"),
        onValueChange = {
            viewModel.setUpdateChannel(it)
            refresh()
        },
    )
}

@Composable
private fun BackupSection(viewModel: SettingsViewModel, callbacks: SettingsCallbacks) {
    PreferenceCategory(stringResource(R.string.settings_backup_and_restore))
    Preference(
        title = stringResource(R.string.settings_backup),
        summary = stringResource(R.string.settings_backup_summery),
        icon = painterResource(R.drawable.ic_baseline_settings_backup_restore_24),
        enabled = viewModel.installed,
        onClick = callbacks.backup,
    )
    Preference(
        title = stringResource(R.string.settings_restore),
        summary = stringResource(R.string.settings_restore_summery),
        icon = painterResource(R.drawable.ic_outline_restore_24),
        enabled = viewModel.installed,
        onClick = callbacks.restore,
    )
}

@Composable
private fun SystemSection(viewModel: SettingsViewModel, @Suppress("UNUSED_PARAMETER") tick: Int, refresh: () -> Unit) {
    // Mirrors @bool/show_system_settings (false) AND the Q+ guard in the legacy fragment.
    if (!viewModel.showSystemGroup) return
    val context = LocalContext.current
    val showGroup = remember { context.resources.getBoolean(R.bool.show_system_settings) }
    if (!showGroup) return
    PreferenceCategory(stringResource(R.string.settings_group_system))
    SwitchPreference(
        title = stringResource(R.string.settings_show_hidden_icon_apps_enabled),
        summary = stringResource(R.string.settings_show_hidden_icon_apps_enabled_summary),
        icon = painterResource(R.drawable.ic_outline_android_24),
        enabled = viewModel.installed,
        checked = viewModel.hiddenIconsChecked(context),
        onCheckedChange = {
            viewModel.setHiddenIcons(it)
            refresh()
        },
    )
}
