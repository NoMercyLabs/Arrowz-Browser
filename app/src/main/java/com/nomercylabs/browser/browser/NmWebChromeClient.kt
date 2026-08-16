/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class NmWebChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String) -> Unit,
    private val onEnterFullscreen: (View, CustomViewCallback) -> Unit,
    private val onExitFullscreen: () -> Unit,
) : WebChromeClient() {

    /**
     * WebView hands over a view already containing the video plus a callback for
     * when the user leaves. Ignoring this is why a webview-based browser's
     * fullscreen button appears to do nothing.
     */
    override fun onShowCustomView(view: View, callback: CustomViewCallback) =
        onEnterFullscreen(view, callback)

    override fun onHideCustomView() = onExitFullscreen()

    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

    override fun onReceivedTitle(view: WebView, title: String) = onTitle(title)

    /**
     * Denied outright until slice 10 builds the prompt and the per-site store.
     *
     * The alternative is granting silently, which would hand a page the camera
     * or microphone of a device sitting in a living room with nobody told.
     */
    override fun onPermissionRequest(request: PermissionRequest) = request.deny()
}
