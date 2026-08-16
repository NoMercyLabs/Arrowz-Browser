/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class NmWebChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String) -> Unit,
) : WebChromeClient() {

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
