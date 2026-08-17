/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.privacy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list that ships in the APK, read from the asset itself.
 *
 * A matcher tested only against rules written in the test file proves the
 * matcher. This proves the thing a television actually loads, which is where a
 * single malformed line turns into every request on every site being blocked.
 */
class SeedListTest {

    private val seed: FilterSet = FilterParser.parse(
        File("src/main/assets/filters-seed.txt").readText().lineSequence(),
    )

    private val matcher = FilterMatcher(seed.network)

    private fun blocks(url: String, pageHost: String, thirdParty: Boolean = true): Boolean =
        matcher.blocks(url, pageHost, ResourceKind.Script, thirdParty)

    @Test
    fun theSeedParsesIntoRules() {
        assertTrue(seed.network.size > 50)
        assertTrue(seed.cosmetic.isNotEmpty())
    }

    // Every rule bucketed under a token is a rule most requests never walk. A
    // seed that lands entirely in the catch-all would be tested against every
    // request on every page.
    @Test
    fun almostEveryRuleIsIndexed() {
        val unindexed: List<NetworkRule> = seed.network.filter { rule -> rule.token.isEmpty() }
        assertTrue(unindexed.map { rule -> rule.pattern }.toString(), unindexed.size <= 2)
    }

    @Test
    fun knownTrackersAreBlocked() {
        assertTrue(blocks("https://www.google-analytics.com/analytics.js", "example.org"))
        assertTrue(blocks("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "example.org"))
        assertTrue(blocks("https://connect.facebook.net/en_US/fbevents.js", "example.org"))
    }

    /**
     * The failure this exists for, measured on the 8010: every request on every
     * site came back as an empty 200, including the page's own document, so a
     * site loaded as a blank screen with no error anywhere.
     */
    @Test
    fun anOrdinarySiteIsNotBlocked() {
        listOf(
            "https://example.org/",
            "https://example.org/index.html",
            "https://www.theguardian.com/international",
            "https://assets.guim.co.uk/style.css",
            "https://en.wikipedia.org/wiki/Television",
            "https://cdn.jsdelivr.net/npm/thing/dist/thing.min.js",
        ).forEach { url ->
            assertFalse(url, blocks(url, "example.org"))
            assertFalse(url, blocks(url, "example.org", thirdParty = false))
        }
    }

    @Test
    fun aFirstPartyRequestToASiteOfItsOwnNameIsNotBlocked() {
        assertFalse(blocks("https://www.criteo.com/careers", "www.criteo.com", thirdParty = false))
    }

    @Test
    fun everySeedCosmeticSelectorIsPlainCss() {
        seed.cosmetic.forEach { rule ->
            assertFalse(rule.selector, rule.selector.contains(":has-text("))
            assertFalse(rule.selector, rule.selector.startsWith("+js"))
        }
    }

    @Test
    fun theSeedHidesNothingOnAnOrdinarySite() {
        val css: String = CosmeticInjector.cssFor(seed.cosmetic, "example.org")
        assertEquals(
            ".adsbygoogle,.ad-container,.advertisement,div[id^=\"google_ads_iframe\"]," +
                "ins.adsbygoogle{display:none !important;}",
            css,
        )
    }
}
