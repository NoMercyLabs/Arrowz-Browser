/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.privacy

/**
 * Builds the element-hiding CSS for one page.
 *
 * `shouldInterceptRequest` cannot hide an element nobody requested — an ad slot
 * drawn by the page's own script never crosses the network — so hiding is a
 * separate mechanism from blocking, and this is it.
 */
object CosmeticInjector {

    /**
     * Selectors from strangers are never allowed near our own overlays. Ours
     * live in their own stylesheet under a reserved prefix, and this drops any
     * rule that names it, so a list rule cannot hide the focus ring and leave
     * somebody with a browser they cannot see their way around.
     */
    const val RESERVED_PREFIX: String = "nm-"

    fun cssFor(rules: List<CosmeticRule>, host: String): String {
        val lowercaseHost: String = host.lowercase()

        val applicable: List<CosmeticRule> = rules.filter { rule -> rule.appliesTo(lowercaseHost) }
        val (exceptions, hides) = applicable.partition { rule -> rule.isException }
        val unhidden: Set<String> = exceptions.map { rule -> rule.selector }.toSet()

        val selectors: List<String> = hides
            .map { rule -> rule.selector }
            .filter { selector -> selector !in unhidden }
            .filter { selector -> !selector.contains(RESERVED_PREFIX) }
            .distinct()

        if (selectors.isEmpty()) return ""

        // One rule rather than one per selector. A page carrying a thousand
        // separate rules costs style resolution on every layout, and the browser
        // feels slow on exactly the ad-heavy pages this is meant to improve.
        return selectors.joinToString(",") + "{display:none !important;}"
    }

    private fun CosmeticRule.appliesTo(host: String): Boolean {
        if (excludeDomains.any { domain -> host.isUnder(domain) }) return false
        if (domains.isEmpty()) return true
        return domains.any { domain -> host.isUnder(domain) }
    }
}
