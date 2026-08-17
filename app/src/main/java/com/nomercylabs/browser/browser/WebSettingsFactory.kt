/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Turns WebView into a browser.
 *
 * The defaults are tuned for an app embedding a page it controls, not for the
 * open web, and several of the gaps are silent: DOM storage is off, so any site
 * using localStorage half-works with no error anywhere.
 */
object WebSettingsFactory {

    fun apply(webView: WebView, userAgent: String, isDarkTheme: Boolean, textZoomPercent: Int = 100) {
        val settings: WebSettings = webView.settings

        // The system font scale, which a page cannot see for itself. Text rather
        // than page zoom: page zoom scales layout with it and produces sideways
        // scrolling, and sideways is the axis a D-pad handles worst.
        settings.textZoom = textZoomPercent

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true

        settings.userAgentString = userAgent

        // Popups and target=_blank. Without both, links that open a window do
        // nothing at all and the page looks broken rather than restricted.
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // A television is a wide screen viewed from three metres. Overview mode
        // fits the page to it instead of rendering at phone width and cropping.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        // Cleartext pages are allowed at the manifest level so http:// sites
        // load, but an https page still may not pull http subresources: that is
        // the case where the user believes they are on a secure page.
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Autofill would store form data on a device several people share.
        settings.saveFormData = false

        applyDarkening(settings, isDarkTheme)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        // Never LAYER_TYPE_SOFTWARE. It disables hardware and secure video
        // decode, which cannot be noticed until protected playback is attempted.
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
    }

    /**
     * Makes `prefers-color-scheme` report the television's setting to the page.
     *
     * Algorithmic darkening only takes effect in combination with the theme's
     * `isLightTheme` attribute, which is why that is declared in the `-v29`
     * resource variants. A site with its own dark stylesheet then follows the
     * system; a site without one is darkened by Chromium rather than being a
     * white rectangle in a dark room.
     */
    fun applyDarkening(settings: WebSettings, isDarkTheme: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDarkTheme)
        }
    }
}
