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

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.manager.R
import org.lsposed.manager.receivers.LSPManagerServiceHolder
import org.lsposed.manager.ui.component.DialogWindowBlur

/**
 * Modal "compile speed" progress dialog. Compose replacement for CompileDialogFragment: shows an
 * indeterminate spinner while the dex-opt binder call runs off the main thread, then dismisses and
 * reports the outcome through [onResult] (the shell turns it into a snackbar hint). Non-cancelable,
 * matching the legacy `setCancelable(false)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompileDialog(
    appInfo: ApplicationInfo,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val label = remember(appInfo) { appInfo.loadLabel(pm).toString() }
    val iconBitmap = remember(appInfo) {
        val drawable = appInfo.loadIcon(pm)
        val bmp = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }

    LaunchedEffect(appInfo.packageName) {
        val text = withContext(Dispatchers.IO) {
            try {
                LSPManagerServiceHolder.getService().clearApplicationProfileData(appInfo.packageName)
                if (LSPManagerServiceHolder.getService().performDexOptMode(appInfo.packageName)) {
                    context.getString(R.string.compile_done)
                } else {
                    context.getString(R.string.compile_failed)
                }
            } catch (e: Throwable) {
                context.getString(R.string.compile_failed_with_info) + e
            }
        }
        onResult(text)
        onDismiss()
    }

    BasicAlertDialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(iconBitmap, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(24.dp))
                    Text(
                        stringResource(R.string.compile_speed_msg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    DialogWindowBlur()
}
