package org.lsposed.manager.ui.component

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader
import org.lsposed.manager.App

private val iconCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun cacheKey(info: PackageInfo) =
    "${info.packageName}:${(info.applicationInfo?.uid ?: 0) / App.PER_USER_RANGE}:${info.getLongVersionCode()}"

@Composable
fun AppIcon(info: PackageInfo, sizePx: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = cacheKey(info)
    val bitmap = produceState<Bitmap?>(initialValue = iconCache[key], info, sizePx) {
        value = iconCache[key] ?: withContext(Dispatchers.IO) {
            runCatching {
                val appInfo = info.applicationInfo ?: return@runCatching null
                AppIconLoader(sizePx, true, context).loadIcon(appInfo).also {
                    iconCache.put(key, it)
                }
            }.getOrNull()
        }
    }.value
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
    }
}
