/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.browser

import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * What a site is allowed to ask for.
 *
 * A string rather than an enum because it is also the primary key of the row
 * that remembers the answer, and a stored decision has to survive a rename of
 * whatever we happen to call it in code.
 */
object PermissionKind {
    const val CAMERA: String = "camera"
    const val MICROPHONE: String = "microphone"
    const val LOCATION: String = "location"
}

class NmWebChromeClient(
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String) -> Unit,
    private val onEnterFullscreen: (View, CustomViewCallback) -> Unit,
    private val onExitFullscreen: () -> Unit,
    /**
     * Asked with the origin, what is wanted, and the two answers. Nothing is
     * granted here: the decision belongs to whoever is holding the remote, and
     * this class has no way to reach them.
     */
    private val onPermissionAsked: (origin: String, kinds: List<String>, grant: () -> Unit, deny: () -> Unit) -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean,
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
     * Never granted silently. This is a device sitting in a living room, and a
     * page taking its camera without anyone told is the failure that cannot be
     * undone after the fact.
     */
    override fun onPermissionRequest(request: PermissionRequest) {
        val kinds: List<String> = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> PermissionKind.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> PermissionKind.MICROPHONE
                else -> null
            }
        }
        if (kinds.isEmpty()) {
            request.deny()
            return
        }
        onPermissionAsked(
            request.origin.toString(),
            kinds,
            { request.grant(request.resources) },
            { request.deny() },
        )
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        onPermissionAsked(
            origin,
            listOf(PermissionKind.LOCATION),
            { callback.invoke(origin, true, true) },
            { callback.invoke(origin, false, false) },
        )
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)
}
