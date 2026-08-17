/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.privacy

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * The rules, shared by every tab.
 *
 * One copy, because eighty thousand rules per tab on a 2GB television is the
 * memory the tab registry spends its life trying to reclaim. Replaced wholesale
 * rather than edited, so a list update cannot be observed half-applied by a
 * request already in flight on another thread.
 */
class FilterEngine {

    @Volatile
    private var matcher: FilterMatcher = FilterMatcher(emptyList())

    @Volatile
    private var cosmetic: List<CosmeticRule> = emptyList()

    val ruleCount: Int get() = matcher.size

    fun replaceRules(filters: FilterSet) {
        matcher = FilterMatcher(filters.network)
        cosmetic = filters.cosmetic
    }

    fun blocks(url: String, pageHost: String, kind: ResourceKind, isThirdParty: Boolean): Boolean =
        matcher.blocks(url, pageHost, kind, isThirdParty)

    fun cssFor(host: String): String = CosmeticInjector.cssFor(cosmetic, host)
}

/**
 * The blocking half for one tab, sitting in `shouldInterceptRequest`.
 *
 * Per tab rather than per activity because `third-party` and `domain=` are
 * judged against the page the request belongs to, and a browser with two tabs
 * open has two of those.
 *
 * Runs on WebView's network threads, several at once, so the only state it holds
 * is written once per navigation and read many times.
 */
class RequestFilter(
    private val engine: FilterEngine,
    private val isEnabled: () -> Boolean,
    private val onBlocked: (total: Int) -> Unit,
) {

    @Volatile
    private var pageHost: String = ""

    @Volatile
    var blockedCount: Int = 0
        private set

    /** A new page starts a new count. Carrying the previous page's total over
     *  makes the number meaningless within a minute of browsing. */
    fun pageChanged(url: String) {
        pageHost = hostOf(url)
        blockedCount = 0
    }

    fun cssForCurrentPage(): String = engine.cssFor(pageHost)

    /**
     * An empty 200 rather than an error.
     *
     * A blocked request that fails loudly makes a page run its own error
     * handling — retries, a fallback tracker, "please disable your ad blocker" —
     * and an empty body it can parse is the quieter answer.
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!isEnabled()) return null

        val url: String = request.url.toString()
        val kind: ResourceKind = ResourceKind.infer(
            url = url,
            isMainFrame = request.isForMainFrame,
            accept = request.requestHeaders["Accept"],
        )

        val requestHost: String = hostOf(url)
        val host: String = pageHost
        val isThirdParty: Boolean =
            host.isNotEmpty() && requestHost.isNotEmpty() && !sameSite(requestHost, host)

        if (!engine.blocks(url, host, kind, isThirdParty)) return null

        blockedCount++
        onBlocked(blockedCount)
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun sameSite(requestHost: String, pageHost: String): Boolean =
        requestHost == pageHost || requestHost.isUnder(pageHost) || pageHost.isUnder(requestHost)

    private fun hostOf(url: String): String {
        val afterScheme: String = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return ""
        return afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringAfter('@')
            .substringBefore(':')
            .lowercase()
    }
}
