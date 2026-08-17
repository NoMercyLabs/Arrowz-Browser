/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

data class PageError(val code: Int, val description: String, val url: String)

/**
 * What the page just did, because "the URL changed" is three different events
 * wearing one name.
 *
 * They were one signal, and everything that resets per document ran on every
 * load event a page produced. Measured on a Wikipedia article: the spatial
 * state was cleared several seconds into reading it, when a deferred
 * subresource finished, and focus jumped from mid-article back to the first
 * link on the page — with the visit recorded in history twice for good measure.
 */
enum class PageEvent {
    /** A document began loading. Exactly one per load, including a reload, where
     *  the address legitimately repeats and the document really is new. */
    DocumentStarted,

    /** A single-page app routed without any of the page lifecycle firing.
     *  Follows an ordinary load with the same address, so the host drops the
     *  repeat and keeps the genuine route changes. */
    RouteChanged,

    /** The same document reporting on itself. Keeps the address bar honest and
     *  resets nothing. */
    Progressed,
}

class NmWebViewClient(
    private val onPageStateChanged: (url: String, canGoBack: Boolean, event: PageEvent) -> Unit,
    private val onError: (PageError) -> Unit,
    private val onRendererGone: () -> Unit,
    private val onInjectAtDocumentStart: (WebView) -> Unit = {},
    /**
     * Debug builds only. Serves the bundled test page over a real https origin
     * so media behaviour can be driven from a page we control, rather than from
     * whichever third-party site happens to cooperate today.
     *
     * Null in release, where the interceptor does nothing at all.
     */
    private val assetLoader: WebViewAssetLoader? = null,
    /**
     * The tracker filter, asked after the asset loader so a debug test page is
     * never subject to it. Runs on WebView's network threads.
     */
    private val onInterceptRequest: (WebResourceRequest) -> WebResourceResponse? = { null },
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? =
        assetLoader?.shouldInterceptRequest(request.url) ?: onInterceptRequest(request)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Re-injected on every navigation rather than once per WebView, so a
        // single-page app that swaps its document does not lose the shim.
        onInjectAtDocumentStart(view)
        onPageStateChanged(url, view.canGoBack(), PageEvent.DocumentStarted)
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageStateChanged(url, view.canGoBack(), PageEvent.Progressed)
    }

    /**
     * How a single-page app says it navigated.
     *
     * `pushState` swaps the document's content without any of the page
     * lifecycle firing, so a site that routes in JavaScript kept the previous
     * page's focus positions, its section memory and its filtering origin.
     */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        onPageStateChanged(url, view.canGoBack(), PageEvent.RouteChanged)
    }

    /**
     * Only the main frame is surfaced. A failed tracking pixel is not a broken
     * page, and reporting subresource failures would put an error over sites
     * that are working perfectly.
     */
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        onError(PageError(error.errorCode, error.description.toString(), request.url.toString()))
    }

    /**
     * Cancelled, with no override offered.
     *
     * A browser that lets someone click through a certificate error on a
     * television, where the URL bar is barely readable at three metres, is
     * offering a decision they have no way to make well.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        onError(PageError(ERROR_SSL, "Certificate error", error.url ?: ""))
    }

    /**
     * Returning false here crashes the whole browser.
     *
     * WebView is multiprocess on these devices, so a renderer killed under
     * memory pressure takes its WebView with it, and this is routine rather than
     * exceptional on a 2GB box. Returning true keeps the app alive; slice 7
     * turns that into a real rebuild from saved tab state.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }

    private companion object {
        const val ERROR_SSL: Int = -1100
    }
}
