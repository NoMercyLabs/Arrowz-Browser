/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMatcherTest {

    private fun matcher(vararg lines: String): FilterMatcher =
        FilterMatcher(FilterParser.parse(lines.asSequence()).network)

    private fun FilterMatcher.blocksScript(
        url: String,
        pageHost: String = "news.example.com",
        isThirdParty: Boolean = true,
    ): Boolean = blocks(url, pageHost, ResourceKind.Script, isThirdParty)

    @Test
    fun aDomainAnchorMatchesTheHostAndItsSubdomains() {
        val filter: FilterMatcher = matcher("||doubleclick.net^")
        assertTrue(filter.blocksScript("https://doubleclick.net/tag.js"))
        assertTrue(filter.blocksScript("https://stats.g.doubleclick.net/tag.js"))
    }

    // The failure a plain endsWith produces, and it fails in the direction that
    // blocks a site belonging to somebody else entirely.
    @Test
    fun aDomainAnchorDoesNotMatchASuffixOfAnotherHost() {
        assertFalse(matcher("||example.com^").blocksScript("https://notexample.com/a.js"))
    }

    // The end of a URL is a separator. Without that, a rule ending in ^ never
    // matches a request for a bare host, which is most of them.
    @Test
    fun theEndOfTheUrlCountsAsASeparator() {
        assertTrue(matcher("||ads.example.com^").blocksScript("https://ads.example.com"))
    }

    @Test
    fun aSeparatorDoesNotMatchAnOrdinaryHostCharacter() {
        assertFalse(matcher("||ads^").blocksScript("https://adservice.example.com/x.js"))
    }

    @Test
    fun aStartAnchorOnlyMatchesFromTheBeginning() {
        val filter: FilterMatcher = matcher("|https://tracker.example.com/beacon")
        assertTrue(filter.blocksScript("https://tracker.example.com/beacon.js"))
        assertFalse(filter.blocksScript("https://cdn.example.org/?u=https://tracker.example.com/beacon"))
    }

    @Test
    fun anEndAnchorRequiresTheUrlToStopThere() {
        val filter: FilterMatcher = matcher("/beacon.js|")
        assertTrue(filter.blocksScript("https://example.org/beacon.js"))
        assertFalse(filter.blocksScript("https://example.org/beacon.js?v=2"))
    }

    @Test
    fun aWildcardSpansAnything() {
        assertTrue(matcher("/tracking*/pixel").blocksScript("https://example.org/tracking-v2/pixel"))
    }

    @Test
    fun aPlainSubstringMatchesAnywhere() {
        assertTrue(matcher("/analytics/").blocksScript("https://example.org/js/analytics/main.js"))
    }

    // The rule that keeps well-known sites working: a list author writing @@ is
    // saying "whatever else matches, do not block this".
    @Test
    fun anExceptionBeatsABlockWhicheverOrderTheyWereRead() {
        val blockFirst: FilterMatcher = matcher("||example.org^", "@@||example.org/needed.js")
        val exceptionFirst: FilterMatcher = matcher("@@||example.org/needed.js", "||example.org^")
        listOf(blockFirst, exceptionFirst).forEach { filter ->
            assertFalse(filter.blocksScript("https://example.org/needed.js"))
            assertTrue(filter.blocksScript("https://example.org/tracker.js"))
        }
    }

    // Blocking a page somebody navigated to produces a blank screen with no
    // explanation and no way forward.
    @Test
    fun theMainFrameIsNeverBlocked() {
        val filter: FilterMatcher = matcher("||example.org^")
        assertFalse(filter.blocks("https://example.org/", "example.org", ResourceKind.Document, false))
    }

    @Test
    fun aThirdPartyRuleIgnoresAFirstPartyRequest() {
        val filter: FilterMatcher = matcher("||example.org^\$third-party")
        assertTrue(filter.blocksScript("https://example.org/t.js", isThirdParty = true))
        assertFalse(filter.blocksScript("https://example.org/t.js", isThirdParty = false))
    }

    @Test
    fun aTypeOptionOnlyAppliesToThatType() {
        val filter: FilterMatcher = matcher("||example.org/x\$script")
        assertTrue(filter.blocks("https://example.org/x", "a.com", ResourceKind.Script, true))
        assertFalse(filter.blocks("https://example.org/x", "a.com", ResourceKind.Image, true))
    }

    @Test
    fun aDomainOptionLimitsTheRuleToThosePages() {
        val filter: FilterMatcher = matcher("||example.org^\$domain=news.example.com")
        assertTrue(filter.blocksScript("https://example.org/t.js", pageHost = "news.example.com"))
        assertTrue(filter.blocksScript("https://example.org/t.js", pageHost = "a.news.example.com"))
        assertFalse(filter.blocksScript("https://example.org/t.js", pageHost = "other.com"))
    }

    @Test
    fun anExcludedDomainIsNotBlockedEvenThoughTheRuleOtherwiseMatches() {
        val filter: FilterMatcher = matcher("||example.org^\$domain=~safe.com")
        assertTrue(filter.blocksScript("https://example.org/t.js", pageHost = "other.com"))
        assertFalse(filter.blocksScript("https://example.org/t.js", pageHost = "safe.com"))
    }

    @Test
    fun commentsAndHeadersAreNotRules() {
        val filter: FilterMatcher = matcher("! a comment", "[Adblock Plus 2.0]", "")
        assertEquals(0, filter.size)
        assertFalse(filter.blocksScript("https://example.org/anything.js"))
    }

    /**
     * The index has to be invisible.
     *
     * Bucketing by token is the one optimisation in this slice, and the way it
     * fails is by quietly not testing a rule that would have matched — which
     * looks like the list being wrong rather than the matcher being wrong. So
     * the same rules are also walked exhaustively, and the two must agree on
     * every URL.
     */
    @Test
    fun theTokenIndexAgreesWithABruteForceScan() {
        val lines: List<String> = listOf(
            "||doubleclick.net^",
            "||scorecardresearch.com^\$third-party",
            "/analytics/",
            "|https://tracker.example.com/beacon",
            "/pixel.gif|",
            "||example.org^\$domain=news.example.com",
            "@@||doubleclick.net/allowed.js",
            "ads",
            "/a*/b",
        )
        val rules: List<NetworkRule> = FilterParser.parse(lines.asSequence()).network
        val indexed = FilterMatcher(rules)

        val urls: List<String> = listOf(
            "https://doubleclick.net/tag.js",
            "https://doubleclick.net/allowed.js",
            "https://cdn.scorecardresearch.com/beacon.js",
            "https://example.org/js/analytics/main.js",
            "https://tracker.example.com/beacon.js",
            "https://example.org/pixel.gif",
            "https://example.org/pixel.gif?v=1",
            "https://example.org/t.js",
            "https://example.net/ads/banner",
            "https://example.net/a-thing/b",
            "https://example.net/nothing/at/all.js",
        )

        urls.forEach { url ->
            val brute: Boolean = bruteForce(rules, url, "news.example.com", ResourceKind.Script, true)
            assertEquals(url, brute, indexed.blocksScript(url))
        }
    }

    private fun bruteForce(
        rules: List<NetworkRule>,
        url: String,
        pageHost: String,
        kind: ResourceKind,
        isThirdParty: Boolean,
    ): Boolean {
        if (kind == ResourceKind.Document) return false
        val applying: List<NetworkRule> =
            rules.filter { rule -> rule.applies(url, pageHost, kind, isThirdParty) }
        if (applying.any { rule -> rule.isException }) return false
        return applying.isNotEmpty()
    }
}
