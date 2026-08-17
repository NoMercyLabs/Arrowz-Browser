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
import com.nomercylabs.browser.forms.FormBridge
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

    /** How many times the find query appears, and which one is highlighted.
     *  Counted asynchronously, so both are zero until WebView says otherwise. */
    var findMatches: Int by mutableStateOf(0)
        internal set
    var findActiveMatch: Int by mutableStateOf(0)
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
     * The tab's form bridge, attached by the activity because deciding what a
     * focused field opens is the activity's job. Held here so the registry can
     * ask one question — is this tab holding unsaved input — without knowing
     * anything about forms.
     */
    var formBridge: FormBridge? = null

    /** This tab's tracker filter. Per tab, because what counts as third-party
     *  depends on the page the requests belong to. */
    var requestFilter: com.nomercylabs.browser.privacy.RequestFilter? = null

    override val hasDirtyForm: Boolean get() = formBridge?.hasDirtyForm == true

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
    private var loadedListener: ((String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        userAgent: String,
        isDarkTheme: Boolean,
        /** The television's font scale, carried into the page. 100 is the
         *  page's own size, which is what an unchanged system setting means. */
        textZoomPercent: Int = 100,
        onEnterFullscreen: (android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit,
        onExitFullscreen: () -> Unit,
        assetLoader: androidx.webkit.WebViewAssetLoader? = null,
        scriptsAtDocumentStart: List<String> = emptyList(),
        onPermissionAsked: (String, List<String>, () -> Unit, () -> Unit) -> Unit = { _, _, _, deny -> deny() },
        onFileChooser: (
            android.webkit.ValueCallback<Array<android.net.Uri>>,
            android.webkit.WebChromeClient.FileChooserParams,
        ) -> Boolean = { _, _ -> false },
        onDownload: (url: String, userAgent: String, contentDisposition: String, mimeType: String) -> Unit =
            { _, _, _, _ -> },
        onInterceptRequest: (android.webkit.WebResourceRequest) -> android.webkit.WebResourceResponse? =
            { null },
    ) {
        val target: WebView = view ?: return
        WebSettingsFactory.apply(target, userAgent, isDarkTheme, textZoomPercent)

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
            onInterceptRequest = onInterceptRequest,
        )

        target.webChromeClient = NmWebChromeClient(
            onProgress = { progress ->
                state.progress = progress
                refreshScrollPosition()
                if (progress >= COMPLETE) loadedListener?.invoke(state.url)
            },
            onTitle = { title -> state.title = title },
            onEnterFullscreen = onEnterFullscreen,
            onExitFullscreen = onExitFullscreen,
            onPermissionAsked = onPermissionAsked,
            onFileChooser = onFileChooser,
        )

        target.setDownloadListener { url, agent, disposition, mimeType, _ ->
            onDownload(url, agent, disposition, mimeType)
        }

        target.setFindListener { activeIndex, matches, isDoneCounting ->
            if (isDoneCounting) {
                state.findMatches = matches
                state.findActiveMatch = if (matches == 0) 0 else activeIndex + 1
            }
        }

        target.setOnScrollChangeListener { _, _, _, _, _ -> refreshScrollPosition() }
    }

    /** Changing what the site is told requires asking it again, so this reloads
     *  rather than waiting for the next navigation to pick the new string up. */
    fun setUserAgent(userAgent: String) {
        val target: WebView = view ?: return
        target.settings.userAgentString = userAgent
        target.reload()
    }

    fun find(query: String) {
        if (query.isEmpty()) {
            clearFind()
            return
        }
        view?.findAllAsync(query)
    }

    /** Wraps at both ends, which is what every browser does and what stops a
     *  press at the last match from appearing to be a dead key. */
    fun findNext(forward: Boolean) {
        view?.findNext(forward)
    }

    fun clearFind() {
        view?.clearMatches()
        state.findMatches = 0
        state.findActiveMatch = 0
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

    /** Fires when the page reaches full progress, which is the moment its title
     *  is real rather than the previous document's. Can fire more than once for
     *  one document, so a listener must tolerate a repeat. */
    fun onLoaded(listener: (url: String) -> Unit) {
        loadedListener = listener
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
        const val COMPLETE: Int = 100

        /**
         * A few pixels of slack. Momentum scrolling frequently settles at 1 or 2
         * rather than exactly 0, and requiring 0 makes revealing the nav bar
         * feel intermittent for reasons the user cannot see.
         */
        const val AT_TOP_THRESHOLD_PX: Int = 4
    }
}
