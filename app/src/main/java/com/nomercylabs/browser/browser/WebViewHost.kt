/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The live facts about the page that a keypress decision needs, plus the ones
 * the chrome renders. Held as Compose state so both stay in step with what is
 * on screen.
 */
class PageState {
    var url: String by mutableStateOf("")
        internal set
    var title: String by mutableStateOf("")
        internal set
    var progress: Int by mutableStateOf(0)
        internal set
    var canGoBack: Boolean by mutableStateOf(false)
        internal set
    var error: PageError? by mutableStateOf(null)
        internal set

    /**
     * Read from the view rather than tracked, because a page can scroll itself
     * at any time without telling us, and a stale value here would send UP to
     * the nav bar in the middle of an article.
     */
    var isAtTop: Boolean by mutableStateOf(true)
        internal set
}

/**
 * Owns the WebView and turns dispatcher commands into calls on it.
 *
 * Deliberately not a composable. Slice 7 gives tabs a lifetime that outlives the
 * Activity so background audio survives, and a WebView owned by a composition
 * cannot do that.
 */
class WebViewHost(private val webView: WebView) {

    val state: PageState = PageState()

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        userAgent: String,
        isDarkTheme: Boolean,
        onEnterFullscreen: (android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit,
        onExitFullscreen: () -> Unit,
        assetLoader: androidx.webkit.WebViewAssetLoader? = null,
        scriptsAtDocumentStart: List<String> = emptyList(),
    ) {
        WebSettingsFactory.apply(webView, userAgent, isDarkTheme)

        webView.webViewClient = NmWebViewClient(
            onPageStateChanged = { url, canGoBack ->
                state.url = url
                state.canGoBack = canGoBack
                state.error = null
                refreshScrollPosition()
            },
            onError = { error -> state.error = error },
            onRendererGone = { state.error = PageError(RENDERER_GONE, "The page stopped responding", state.url) },
            assetLoader = assetLoader,
            onInjectAtDocumentStart = { view ->
                scriptsAtDocumentStart.forEach { script -> view.evaluateJavascript(script, null) }
            },
        )

        webView.webChromeClient = NmWebChromeClient(
            onProgress = { progress ->
                state.progress = progress
                refreshScrollPosition()
            },
            onTitle = { title -> state.title = title },
            onEnterFullscreen = onEnterFullscreen,
            onExitFullscreen = onExitFullscreen,
        )

        webView.setOnScrollChangeListener { _, _, _, _, _ -> refreshScrollPosition() }
    }

    fun load(url: String) = webView.loadUrl(url)

    /** Ducking without pausing: the page keeps playing, quietly. */
    fun setVolume(volume: Float) = webView.evaluateJavascript(
        "document.querySelectorAll('video,audio').forEach(function(m){m.volume=$volume})",
        null,
    )

    /**
     * The concrete bridge objects annotate their methods with
     * @JavascriptInterface and proguard-rules.pro keeps them. Lint cannot see
     * that through an Any parameter, and its stated concern is visibility on
     * API 17, which is twelve releases below this app's minimum.
     */
    @SuppressLint("JavascriptInterface")
    fun addBridge(name: String, bridge: Any) = webView.addJavascriptInterface(bridge, name)

    /** Calls into the injected shim. Returns nothing; the page reports back. */
    fun sendMediaAction(action: String) =
        webView.evaluateJavascript("window.__nmMediaAction && window.__nmMediaAction('$action')", null)

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun reload() = webView.reload()

    fun applyTheme(isDarkTheme: Boolean) =
        WebSettingsFactory.applyDarkening(webView.settings, isDarkTheme)

    private fun refreshScrollPosition() {
        state.isAtTop = webView.scrollY <= AT_TOP_THRESHOLD_PX
    }

    private companion object {
        const val RENDERER_GONE: Int = -1101

        /**
         * A few pixels of slack. Momentum scrolling frequently settles at 1 or 2
         * rather than exactly 0, and requiring 0 makes revealing the nav bar
         * feel intermittent for reasons the user cannot see.
         */
        const val AT_TOP_THRESHOLD_PX: Int = 4
    }
}
