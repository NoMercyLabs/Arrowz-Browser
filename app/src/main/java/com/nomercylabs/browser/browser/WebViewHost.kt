/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import android.annotation.SuppressLint
import android.os.Bundle
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
class WebViewHost(private var webView: WebView) {

    val state: PageState = PageState()

    /**
     * The most recent saved state, captured continuously rather than when a tab
     * is suspended.
     *
     * A renderer killed under memory pressure gives no warning, so a
     * suspend-time capture is worthless to exactly the case it exists for.
     * Captured on every navigation, which is when history changes and therefore
     * when there is something new worth keeping.
     */
    private var savedState: Bundle = Bundle()

    /** Set when the last WebView died, so recovery reloads rather than restores
     *  into a view that never had the page. */
    private var lastUrl: String = ""

    private var rebuild: ((Bundle, String) -> WebView)? = null

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
                lastUrl = url
                captureState()
                refreshScrollPosition()
            },
            onError = { error -> state.error = error },
            onRendererGone = { recoverFromDeadRenderer() },
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

    fun load(url: String) {
        lastUrl = url
        webView.loadUrl(url)
    }

    /**
     * Supplies the factory used to replace a WebView whose renderer died. The
     * old view cannot be reused: it is permanently dead once its process is
     * gone, and touching it throws.
     */
    fun onRebuildRequired(factory: (savedState: Bundle, url: String) -> WebView) {
        rebuild = factory
    }

    /**
     * Points the host at a replacement view. Called before configure() during
     * recovery, because configure() acts on the adopted view and configuring
     * the dead one would throw.
     */
    fun adopt(replacement: WebView) {
        webView = replacement
    }

    fun captureState() {
        val bundle = Bundle()
        // Returns null when there is nothing worth saving, in which case the
        // previous capture is still the better one.
        if (webView.saveState(bundle) != null) savedState = bundle
    }

    /**
     * Slice 2 returned true from onRenderProcessGone so the app survived. That
     * is not recovery: the tab was left showing a dead view. This rebuilds it.
     *
     * On a 2GB box with multiprocess WebView this is routine rather than
     * exceptional, so it is a normal path, not an error path.
     */
    private fun recoverFromDeadRenderer() {
        val factory = rebuild
        if (factory == null) {
            state.error = PageError(RENDERER_GONE, "The page stopped responding", state.url)
            return
        }
        factory(savedState, lastUrl)
    }

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
