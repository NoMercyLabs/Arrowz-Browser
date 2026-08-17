/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.data

import android.webkit.JavascriptInterface

/**
 * What the page reports about its own icon.
 *
 * Nothing is decided here. The page says which URL it declares and this hands
 * that to a background thread to fetch, exactly as the form and media bridges
 * do: a decision made inside a document we do not control is a decision the
 * document can change.
 *
 * The name matters. `proguard-rules.pro` keeps `@JavascriptInterface` members on
 * classes ending in `Bridge`, and a bridge R8 has stripped fails as silence
 * rather than as an error.
 */
class SiteIconBridge(private val onIcon: (origin: String, iconUrl: String) -> Unit) {

    @JavascriptInterface
    fun onIconFound(origin: String, iconUrl: String) {
        if (origin.isEmpty() || iconUrl.isEmpty()) return
        onIcon(HomeContent.originOf(origin), iconUrl)
    }
}
