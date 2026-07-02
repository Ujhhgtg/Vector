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

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import java.util.function.Consumer

/**
 * Cross-window blur behind a Compose dialog, ported from the legacy `BlurBehindDialogBuilder`
 * (S+ `setBlurBehindRadius(20)` with dim 0.1/0.32). Attach inside any dialog's content.
 */
@Composable
fun DialogWindowBlur() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val listener = Consumer<Boolean> { enabled ->
            window.setDimAmount(if (enabled) 0.1f else 0.32f)
            window.attributes = window.attributes.apply { blurBehindRadius = 20 }
        }
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                window.windowManager.addCrossWindowBlurEnabledListener(listener)
            }
            override fun onViewDetachedFromWindow(v: View) {
                window.windowManager.removeCrossWindowBlurEnabledListener(listener)
            }
        }
        window.decorView.addOnAttachStateChangeListener(attach)
        listener.accept(window.windowManager.isCrossWindowBlurEnabled)
        onDispose {
            runCatching { window.windowManager.removeCrossWindowBlurEnabledListener(listener) }
            window.decorView.removeOnAttachStateChangeListener(attach)
        }
    }
}

/**
 * A Material3 [AlertDialog] with the blur-behind effect applied. Mirrors the legacy
 * BlurBehindDialogBuilder confirm dialogs (title + message + positive/negative/neutral buttons).
 */
@Composable
fun BlurAlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: (@Composable () -> Unit)? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onNeutral: (() -> Unit)? = null,
    neutralText: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = icon,
        title = title?.let { { Text(it) } },
        text = text,
        confirmButton = {
            if (confirmText != null) {
                TextButton(onClick = { onConfirm?.invoke(); onDismissRequest() }) { Text(confirmText) }
            }
        },
        dismissButton = {
            // The neutral action is rendered alongside dismiss to approximate the 3-button layout.
            Row {
                if (neutralText != null) {
                    TextButton(onClick = { onNeutral?.invoke(); onDismissRequest() }) { Text(neutralText) }
                }
                if (dismissText != null) {
                    TextButton(onClick = onDismissRequest) { Text(dismissText) }
                }
            }
        },
        containerColor = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    )
    DialogWindowBlur()
}
