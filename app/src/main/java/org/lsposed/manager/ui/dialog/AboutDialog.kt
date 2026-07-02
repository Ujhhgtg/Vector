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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.R
import org.lsposed.manager.ui.component.DialogWindowBlur
import java.util.Locale

/**
 * "About" dialog: app icon, name, version, and the source-code/Telegram HTML links.
 * Compose replacement for the former HomeFragment.AboutDialog (DialogFragment + dialog_about.xml).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        ),
    )
    val info = AnnotatedString.fromHtml(
        stringResource(
            R.string.about_view_source_code,
            "<a href=\"https://github.com/Ujhhgtg/Vector\">GitHub</a>",
            "<a href=\"https://t.me/LSPosed\">Telegram</a>",
        ),
        linkStyles = linkStyles,
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(24.dp)) {
                // R.drawable.ic_launcher is an <adaptive-icon>, which painterResource() can't load
                // (it only supports VectorDrawable / raster). Rasterize the drawable to a bitmap so
                // any drawable type renders. See the launcher-icon crash on the About dialog.
                val context = LocalContext.current
                val iconBitmap = remember {
                    val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_launcher, context.theme)
                    drawable?.toBitmap(width = 144, height = 144)?.asImageBitmap()
                }
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        String.format(Locale.getDefault(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.padding(top = 16.dp))
                    Text(
                        info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    DialogWindowBlur()
}
