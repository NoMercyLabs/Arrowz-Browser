/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.browser

/**
 * Decides what a link handed to us by another app is allowed to become.
 *
 * This is not [UrlOrSearch]. Typed text comes from the person holding the
 * remote and a wrong guess costs them a search; an intent comes from software
 * that may be hostile, so nothing here falls back to a search and nothing is
 * repaired. A link either names a page over http or https, or it is refused.
 *
 * The refusals are the point. `javascript:` delivered through an intent is
 * self-XSS with no typing involved, `file:` and `content:` reach the device's
 * own storage, and `data:` renders attacker-authored markup that inherits the
 * address bar. Chrome refuses all four from external callers and so do we.
 */
object ExternalLink {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** The page to open, or null when the caller handed us something a browser
     *  must not follow on another app's say-so. */
    fun resolve(uri: String?): String? {
        val trimmed: String = uri?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val scheme: String = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme !in ALLOWED_SCHEMES) return null

        // A scheme with no host behind it is not a page. `http:` and `https://`
        // both parse far enough to look like links and load nothing.
        val rest: String = trimmed.substringAfter(':').removePrefix("//")
        if (rest.isEmpty() || rest.startsWith("/")) return null

        return trimmed
    }
}
