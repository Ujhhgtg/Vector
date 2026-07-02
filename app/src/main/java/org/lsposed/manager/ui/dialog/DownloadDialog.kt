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

package org.lsposed.manager.ui.dialog

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lsposed.manager.R
import org.lsposed.manager.repo.model.ReleaseAsset
import org.lsposed.manager.ui.component.DialogWindowBlur

/**
 * Asset picker for a release. Compose replacement for RepoItemFragment.DownloadDialog: lists each
 * asset's name with its size and download count, and opens the chosen asset's URL via [onPick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDialog(
    assets: List<ReleaseAsset>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(vertical = 24.dp)) {
                Text(
                    stringResource(R.string.module_release_view_assets),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer8()
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(assets) { asset ->
                        val count = asset.downloadCount
                        val countStr = pluralStringResource(
                            R.plurals.module_release_assets_download_count, count, count,
                        )
                        val sizeStr = Formatter.formatShortFileSize(context, asset.size.toLong())
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    asset.downloadUrl?.let { onPick(it) }
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(asset.name ?: "", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "$sizeStr/$countStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    DialogWindowBlur()
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
