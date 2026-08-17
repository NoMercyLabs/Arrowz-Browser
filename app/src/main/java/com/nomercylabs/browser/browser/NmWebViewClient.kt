/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

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

class NmWebViewClient(
    private val onPageStateChanged: (url: String, canGoBack: Boolean) -> Unit,
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
        onPageStateChanged(url, view.canGoBack())
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageStateChanged(url, view.canGoBack())
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
