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
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.lsposed.manager.App
import org.lsposed.manager.ui.theme.VectorTheme

/**
 * The single Compose entry point. Replaces the legacy fragment-hosting MainActivity (NavHostFragment
 * + bottom nav + nav XML); all UI now lives in [VectorApp]. Kept as a [ComponentActivity] since the
 * theme, locale and dark mode are resolved inside Compose ([VectorTheme]) rather than by the View
 * system. Deep-link / shortcut intents are forwarded to the shell as a one-shot `pendingIntent`.
 */
class MainActivity : ComponentActivity() {

    private var pendingIntent by mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        // Apply the user's chosen locale to the activity context, matching the legacy MaterialActivity.
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(App.getLocale())
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingIntent = intent
        setContent {
            VectorTheme {
                org.lsposed.manager.ui.shell.VectorApp(
                    restart = ::restart,
                    pendingIntent = pendingIntent,
                    onIntentConsumed = { pendingIntent = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }

    /** Recreate so [VectorTheme] re-reads the theme/locale preferences (accent, dark mode, language). */
    fun restart() {
        recreate()
    }

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }
}
