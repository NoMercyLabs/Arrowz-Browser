/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterParserTest {

    private fun parse(vararg lines: String): FilterSet = FilterParser.parse(lines.asSequence())

    @Test
    fun anchorsAreReadOffThePattern() {
        val rule: NetworkRule = parse("||example.org^").network.single()
        assertEquals("example.org^", rule.pattern)
        assertTrue(rule.anchorDomain)
    }

    @Test
    fun optionsAreReadAndRemovedFromThePattern() {
        val rule: NetworkRule = parse("||example.org/x\$third-party,script").network.single()
        assertEquals("example.org/x", rule.pattern)
        assertEquals(true, rule.thirdParty)
        assertEquals(setOf(ResourceKind.Script), rule.kinds)
    }

    @Test
    fun domainListsSplitIntoIncludesAndExcludes() {
        val rule: NetworkRule = parse("||a.org^\$domain=b.com|~c.com").network.single()
        assertEquals(setOf("b.com"), rule.includeDomains)
        assertEquals(setOf("c.com"), rule.excludeDomains)
    }

    // A rule we half understand blocks something the author did not ask us to
    // block, on a page nobody is watching us break.
    @Test
    fun rulesWeCannotHonourAreSkippedRatherThanApproximated() {
        assertEquals(0, parse("/banner\\d+/").network.size)
        assertEquals(0, parse("||example.org^\$removeparam=fbclid").network.size)
        assertEquals(0, parse("||example.org^\$~script").network.size)
    }

    @Test
    fun aCosmeticRuleCarriesItsDomains() {
        val rule: CosmeticRule = parse("example.com,~shop.example.com##.ad").cosmetic.single()
        assertEquals(".ad", rule.selector)
        assertEquals(setOf("example.com"), rule.domains)
        assertEquals(setOf("shop.example.com"), rule.excludeDomains)
    }

    @Test
    fun aGenericCosmeticRuleHasNoDomains() {
        assertEquals(emptySet<String>(), parse("##.advertisement").cosmetic.single().domains)
    }

    @Test
    fun aCosmeticExceptionIsMarkedAsOne() {
        assertTrue(parse("example.com#@#.ad").cosmetic.single().isException)
    }

    // A selector the browser cannot parse drops every rule after it in the same
    // stylesheet, so procedural syntax is refused rather than passed through.
    @Test
    fun proceduralCosmeticSyntaxIsRefused() {
        assertEquals(0, parse("example.com##.a:has-text(Ad)").cosmetic.size)
        assertEquals(0, parse("example.com##+js(nowebrtc)").cosmetic.size)
    }

    // The longest run, not the first: a rule bucketed under a common word is a
    // rule tested against every request ever made.
    @Test
    fun theLongestLiteralRunBecomesTheToken() {
        assertEquals("doubleclick", FilterParser.tokenOf("||doubleclick.net^"))
        assertEquals("analytics", FilterParser.tokenOf("/js/analytics/main"))
    }

    @Test
    fun aPatternWithNoUsableRunHasNoToken() {
        assertEquals("", FilterParser.tokenOf("/a/b^"))
        assertEquals("", FilterParser.tokenOf("ads"))
    }

    @Test
    fun urlTokensAreLowercasedAndShortRunsDropped() {
        assertEquals(
            listOf("https", "doubleclick", "tracker"),
            FilterParser.tokensIn("https://DoubleClick.net/a/tracker"),
        )
    }
}
