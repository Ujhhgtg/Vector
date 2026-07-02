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

package org.lsposed.manager.ui.screen.home

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.util.UpdateUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Raw state of the Home dashboard, read from [ConfigManager]/[UpdateUtil]. All values are primitive
 * facts; the mapping to string/drawable resources is done in the composable so that resource
 * resolution stays in the UI layer.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val binderAlive: Boolean = false,
    val magiskInstalled: Boolean = false,
    val needUpdate: Boolean = false,
    val isDeveloper: Boolean = false,
    val sepolicyLoaded: Boolean = true,
    val systemServerRequested: Boolean = true,
    val dex2oatFlagsLoaded: Boolean = true,
    val dex2oatCompat: Int = 0,
    val xposedVersionName: String = "",
    val xposedVersionCode: Long = 0,
    val xposedApiVersion: Int = 0,
    val dexObfuscateEnabled: Boolean = false,
    val managerPackageName: String = "",
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh(managerPackageName: String) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val binderAlive = ConfigManager.isBinderAlive()
                HomeUiState(
                    loading = false,
                    binderAlive = binderAlive,
                    magiskInstalled = ConfigManager.isMagiskInstalled(),
                    needUpdate = UpdateUtil.needUpdate(),
                    isDeveloper = if (binderAlive) isDeveloper() else false,
                    sepolicyLoaded = ConfigManager.isSepolicyLoaded(),
                    systemServerRequested = ConfigManager.systemServerRequested(),
                    dex2oatFlagsLoaded = ConfigManager.dex2oatFlagsLoaded(),
                    dex2oatCompat = ConfigManager.getDex2OatWrapperCompatibility(),
                    xposedVersionName = ConfigManager.getXposedVersionName() ?: "",
                    xposedVersionCode = ConfigManager.getXposedVersionCode(),
                    xposedApiVersion = ConfigManager.getXposedApiVersion(),
                    dexObfuscateEnabled = ConfigManager.isDexObfuscateEnabled(),
                    managerPackageName = managerPackageName,
                )
            }
            _uiState.value = state
        }
    }

    /**
     * Mirrors the legacy detection in HomeFragment: a live Android Studio attach leaves a pid file
     * under /data/local/tmp/.studio/ipids. Performs filesystem I/O, hence off the main thread.
     */
    private fun isDeveloper(): Boolean {
        val pids = Paths.get("/data/local/tmp/.studio/ipids")
        var developer = false
        try {
            Files.list(pids).use { dir ->
                dir.findFirst().ifPresent { name ->
                    val pid = name.fileName.toString().toInt()
                    try {
                        Os.kill(pid, 0)
                        developer = true
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.ESRCH) {
                            try {
                                Files.delete(name)
                            } catch (ignored: IOException) {
                            }
                        } else {
                            developer = true
                        }
                    }
                }
            }
        } catch (e: IOException) {
            return false
        }
        return developer
    }
}
