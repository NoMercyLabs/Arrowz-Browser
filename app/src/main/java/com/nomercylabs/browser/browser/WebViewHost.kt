/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nomercylabs.browser.tabs.TabPage

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
 * Owns one tab's WebView and turns commands into calls on it.
 *
 * Deliberately not a composable: a tab outlives the composition that draws it,
 * because background audio has to survive switching to another tab.
 */
class WebViewHost(initialView: WebView) : TabPage {

    val state: PageState = PageState()

    /**
     * Null while the tab is suspended, and between a renderer death and the
     * rebuild. Nothing draws a tab in that window, so every call below is a
     * no-op rather than a crash.
     */
    var view: WebView? = initialView
        private set

    override var isSuspended: Boolean by mutableStateOf(false)
        private set

    override val pageUrl: String get() = state.url
    override val pageTitle: String get() = state.title

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

    /** Where to return to when the saved state carries no usable history. */
    private var lastUrl: String = ""

    private var rebuild: ((Bundle, String) -> WebView)? = null
    private var rendererDeathListener: (() -> Unit)? = null
    private var navigationListener: ((String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        userAgent: String,
        isDarkTheme: Boolean,
        onEnterFullscreen: (android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit,
        onExitFullscreen: () -> Unit,
        assetLoader: androidx.webkit.WebViewAssetLoader? = null,
        scriptsAtDocumentStart: List<String> = emptyList(),
    ) {
        val target: WebView = view ?: return
        WebSettingsFactory.apply(target, userAgent, isDarkTheme)

        target.webViewClient = NmWebViewClient(
            onPageStateChanged = { url, canGoBack ->
                state.url = url
                state.canGoBack = canGoBack
                state.error = null
                lastUrl = url
                navigationListener?.invoke(url)
                captureState()
                refreshScrollPosition()
            },
            onError = { error -> state.error = error },
            onRendererGone = { recoverFromDeadRenderer() },
            assetLoader = assetLoader,
            onInjectAtDocumentStart = { injected ->
                scriptsAtDocumentStart.forEach { script -> injected.evaluateJavascript(script, null) }
            },
        )

        target.webChromeClient = NmWebChromeClient(
            onProgress = { progress ->
                state.progress = progress
                refreshScrollPosition()
            },
            onTitle = { title -> state.title = title },
            onEnterFullscreen = onEnterFullscreen,
            onExitFullscreen = onExitFullscreen,
        )

        target.setOnScrollChangeListener { _, _, _, _, _ -> refreshScrollPosition() }
    }

    fun load(url: String) {
        lastUrl = url
        state.url = url
        view?.loadUrl(url)
    }

    /**
     * Supplies the factory used to build this tab's WebView again. Called for
     * both a renderer death and a resume from suspension, which end in the same
     * state: no live view, a saved bundle, a URL.
     */
    fun onRebuildRequired(factory: (savedState: Bundle, url: String) -> WebView) {
        rebuild = factory
    }

    /** Every committed navigation, which is what a visit count is counting. */
    fun onNavigated(listener: (url: String) -> Unit) {
        navigationListener = listener
    }

    /**
     * A renderer death is evidence the system is reclaiming memory, so the
     * registry treats it as a pressure signal rather than only as an error.
     */
    fun onRendererDeath(listener: () -> Unit) {
        rendererDeathListener = listener
    }

    /**
     * Points the host at a replacement view. Called before configure() during a
     * rebuild, because configure() acts on the adopted view and configuring the
     * dead one would throw.
     */
    fun adopt(replacement: WebView) {
        view = replacement
        isSuspended = false
    }

    /**
     * Releases the renderer, keeping enough to come back. The view is detached
     * before it is destroyed: destroying one still in a hierarchy takes the
     * hierarchy with it.
     */
    override fun suspendPage() {
        val current: WebView = view ?: return
        captureState()
        current.stopLoading()
        (current.parent as? ViewGroup)?.removeView(current)
        current.destroy()
        view = null
        isSuspended = true
    }

    /** Rebuilds through the same path a dead renderer uses. */
    override fun resumePage() {
        if (!isSuspended) return
        rebuild?.invoke(savedState, lastUrl)
    }

    fun captureState() {
        val current: WebView = view ?: return
        val bundle = Bundle()
        // Returns null when there is nothing worth saving, in which case the
        // previous capture is still the better one.
        if (current.saveState(bundle) != null) savedState = bundle
    }

    /**
     * Slice 2 returned true from onRenderProcessGone so the app survived. That
     * is not recovery: the tab was left showing a dead view. This rebuilds it.
     *
     * On a 2GB box with multiprocess WebView this is routine rather than
     * exceptional, so it is a normal path, not an error path.
     */
    private fun recoverFromDeadRenderer() {
        view = null
        rendererDeathListener?.invoke()

        val factory = rebuild
        if (factory == null) {
            isSuspended = true
            state.error = PageError(RENDERER_GONE, "The page stopped responding", state.url)
            return
        }
        factory(savedState, lastUrl)
    }

    /** Ducking without pausing: the page keeps playing, quietly. */
    fun setVolume(volume: Float) = view?.evaluateJavascript(
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
    fun addBridge(name: String, bridge: Any) {
        view?.addJavascriptInterface(bridge, name)
    }

    /** Calls into the injected shim. Returns nothing; the page reports back. */
    fun sendMediaAction(action: String) {
        view?.evaluateJavascript("window.__nmMediaAction && window.__nmMediaAction('$action')", null)
    }

    fun goBack() {
        val current: WebView = view ?: return
        if (current.canGoBack()) current.goBack()
    }

    fun reload() {
        view?.reload()
    }

    fun applyTheme(isDarkTheme: Boolean) {
        val current: WebView = view ?: return
        WebSettingsFactory.applyDarkening(current.settings, isDarkTheme)
    }

    /** Nothing comes back from here: the tab is being thrown away. */
    override fun destroyPage() {
        rebuild = null
        rendererDeathListener = null
        suspendPage()
    }

    private fun refreshScrollPosition() {
        val current: WebView = view ?: return
        state.isAtTop = current.scrollY <= AT_TOP_THRESHOLD_PX
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
