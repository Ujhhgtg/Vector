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

package org.lsposed.manager.ui.screen.repo

import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.Headers
import okhttp3.Request
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.util.NavUtil
import rikka.core.util.ResourceUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * GitHub-flavoured markdown README rendered in a [WebView] via AndroidView interop. The HTML
 * pipeline (template injection, OkHttp-backed image loading, external-link handling) is preserved
 * verbatim from the legacy RepoItemFragment.renderGithubMarkdown — replicating it in pure Compose
 * markdown would lose GitHub's CSS and relative-image resolution.
 */
@Composable
fun ReadmeWebView(html: String?, modifier: Modifier = Modifier, nestedInScrollable: Boolean = false) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // When embedded inside a scrollable parent (e.g. the releases LazyColumn), the parent
                // would otherwise steal vertical drags and the WebView's own content could never
                // scroll. Ask the parent not to intercept touches while a finger is down so the
                // WebView handles its own scrolling; release the lock when the gesture ends.
                if (nestedInScrollable) {
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN,
                            android.view.MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)

                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        v.performClick()
                        false
                    }
                }
            }
        },
        update = { view -> renderGithubMarkdown(view, html) },
    )
}

private fun renderGithubMarkdown(view: WebView, text: String?) {
    try {
        view.setBackgroundColor(Color.TRANSPARENT)
        val setting = view.settings
        setting.offscreenPreRaster = true
        setting.domStorageEnabled = true
        setting.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        setting.allowContentAccess = false
        @Suppress("DEPRECATION")
        setting.allowFileAccessFromFileURLs = true
        setting.allowFileAccess = false
        setting.setGeolocationEnabled(false)
        setting.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        setting.textZoom = 80

        val direction = if (view.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr"
        var body = text
        if (TextUtils.isEmpty(body)) {
            body = "<center>" + App.getInstance().getString(R.string.list_empty) + "</center>"
        }
        val html = if (ResourceUtils.isNightMode(view.resources.configuration)) {
            App.HTML_TEMPLATE_DARK.get().replace("@dir@", direction).replace("@body@", body!!)
        } else {
            App.HTML_TEMPLATE.get().replace("@dir@", direction).replace("@body@", body!!)
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                NavUtil.startURL(view.context as android.app.Activity, request.url)
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.scheme?.startsWith("http") != true) return null
                val client = App.getOkHttpClient()
                val headersBuilder = Headers.Builder()
                request.requestHeaders.forEach { (k, v) -> headersBuilder.add(k, v) }
                val call = client.newCall(
                    Request.Builder()
                        .url(request.url.toString())
                        .method(request.method, null)
                        .headers(headersBuilder.build())
                        .build(),
                )
                return try {
                    val reply = call.execute()
                    val header = reply.header("content-type", "image/*;charset=utf-8")
                    val contentTypes = header?.split(";\\s*".toRegex())?.toTypedArray() ?: arrayOf()
                    val mimeType = contentTypes.getOrNull(0) ?: "image/*"
                    val charset = if (contentTypes.size > 1) {
                        contentTypes[1].split("=\\s*".toRegex())[1]
                    } else {
                        "utf-8"
                    }
                    val responseBody = reply.body
                    WebResourceResponse(mimeType, charset, responseBody.byteStream())
                } catch (e: Throwable) {
                    WebResourceResponse(
                        "text/html", "utf-8",
                        ByteArrayInputStream(Log.getStackTraceString(e).toByteArray(StandardCharsets.UTF_8)),
                    )
                }
            }
        }
        view.loadDataWithBaseURL("https://github.com", html, "text/html", StandardCharsets.UTF_8.name(), null)
    } catch (e: Throwable) {
        Log.e(App.TAG, "render readme", e)
    }
}
