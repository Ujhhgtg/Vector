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

package org.lsposed.manager.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Lightweight Compose replacements for the androidx.preference widgets the app used (Category,
 * SwitchPreference, ListPreference via dialog, plain clickable Preference). Designed to be reused by
 * every migrated settings-like screen. State is hoisted: each component is told its current value
 * and reports changes; persistence lives in the caller's ViewModel/repository.
 */

private val PrefMinHeight = 56.dp
private val PrefHPadding = 24.dp
private val PrefVPadding = 16.dp

@Composable
fun PreferenceCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PrefHPadding, end = PrefHPadding, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PreferenceRow(
    title: String,
    summary: CharSequence?,
    icon: Any?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = PrefMinHeight)
        .let { if (onClick != null && enabled) it.clickable(onClick = onClick) else it }
        .padding(horizontal = PrefHPadding, vertical = PrefVPadding)
    val alpha = if (enabled) 1f else 0.38f
    CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = alpha)) {
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            PrefIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current)
                if (summary != null && summary.isNotEmpty()) {
                    PrefSummary(summary)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun PrefIcon(icon: Any?) {
    if (icon == null) return
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LocalContentColor.current.alpha)
    Box(Modifier.width(PrefHPadding + 24.dp), contentAlignment = Alignment.CenterStart) {
        when (icon) {
            is ImageVector -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            is Painter -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PrefSummary(summary: CharSequence) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LocalContentColor.current.alpha)
    when (summary) {
        is AnnotatedString -> Text(summary, style = style, color = color)
        else -> Text(summary.toString(), style = style, color = color)
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: CharSequence? = null,
    icon: Any? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Optimistic local state: the switch flips immediately on tap and stays in sync when the
    // caller's `checked` changes (e.g. the backing pref/binder write completes). This avoids
    // depending on the parent recomposing — toggles were previously only visible after leaving
    // and returning to the screen. We only resync when the *incoming* `checked` actually changes
    // (tracked via lastChecked); otherwise an optimistic flip would be reverted on the next
    // recomposition when the parent hasn't recomputed its value yet.
    var localChecked by remember { mutableStateOf(checked) }
    var lastChecked by remember { mutableStateOf(checked) }
    if (lastChecked != checked) {
        lastChecked = checked
        localChecked = checked
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = {
            localChecked = !localChecked
            onCheckedChange(localChecked)
        },
        trailing = {
            Switch(
                checked = localChecked,
                onCheckedChange = {
                    localChecked = it
                    onCheckedChange(it)
                },
                enabled = enabled,
            )
        },
    )
}

@Composable
fun Preference(
    title: String,
    summary: CharSequence? = null,
    icon: Any? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    PreferenceRow(title, summary, icon, enabled, onClick)
}

@Composable
fun ListPreference(
    title: String,
    icon: Any? = null,
    enabled: Boolean = true,
    entries: List<CharSequence>,
    entryValues: List<String>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
    val summary = entries.getOrNull(selectedIndex)
    PreferenceRow(title, summary, icon, enabled, onClick = { dialogOpen = true })
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEachIndexed { index, entry ->
                        val value = entryValues[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == selectedValue,
                                    onClick = {
                                        dialogOpen = false
                                        if (value != selectedValue) onValueChange(value)
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selectedValue, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            when (entry) {
                                is AnnotatedString -> Text(entry)
                                else -> Text(entry.toString())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResourceCancel())
                }
            },
        )
    }
}

@Composable
private fun stringResourceCancel(): String =
    androidx.compose.ui.res.stringResource(android.R.string.cancel)
