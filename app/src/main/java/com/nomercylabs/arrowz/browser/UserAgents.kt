/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.browser

import android.content.Context
import android.webkit.WebSettings

/**
 * Builds the user agent from the one the system reports, rather than shipping a
 * literal.
 *
 * A hardcoded Chrome version is frozen the day it is typed while the engine
 * underneath keeps updating, so sites are told a lie that grows. Deriving keeps
 * the version honest for the life of the app and costs a string operation.
 */
object UserAgents {

    /**
     * The `wv` token is how sites detect an embedded WebView, and many answer by
     * serving a degraded page or refusing outright. `Version/x.y` is the same
     * marker in a second form.
     */
    private val WEBVIEW_MARKERS = listOf("; wv", "Version/4.0 ")

    /**
     * No standard signal exists for "this is a television". The `TV` form factor
     * was proposed for user-agent client hints and removed, and WebView offers
     * no hook to add client-hint headers to subresource requests, so a token
     * sites already sniff for is the only mechanism available.
     */
    private const val TV_TOKEN = "SMART-TV Android TV"

    fun tenFoot(context: Context, appVersion: String): String =
        "${stripWebViewMarkers(WebSettings.getDefaultUserAgent(context))} $TV_TOKEN ArrowzBrowser/$appVersion"

    /**
     * For sites whose desktop build is too heavy for a weak television SoC. The
     * markers still go: the point is a phone layout, not an embedded one.
     */
    fun mobile(context: Context, appVersion: String): String =
        "${stripWebViewMarkers(WebSettings.getDefaultUserAgent(context))} ArrowzBrowser/$appVersion"

    private fun stripWebViewMarkers(userAgent: String): String {
        var result: String = userAgent
        WEBVIEW_MARKERS.forEach { marker -> result = result.replace(marker, "") }
        return result.replace("  ", " ").trim()
    }
}
