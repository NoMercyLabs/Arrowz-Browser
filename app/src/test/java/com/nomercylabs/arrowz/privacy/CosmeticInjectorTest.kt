/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticInjectorTest {

    private fun css(host: String, vararg lines: String): String =
        CosmeticInjector.cssFor(FilterParser.parse(lines.asSequence()).cosmetic, host)

    @Test
    fun aGenericRuleAppliesEverywhere() {
        assertEquals(".ad{display:none !important;}", css("example.org", "##.ad"))
    }

    @Test
    fun aSiteRuleAppliesToThatSiteAndItsSubdomains() {
        assertEquals(".ad{display:none !important;}", css("news.example.com", "example.com##.ad"))
        assertEquals("", css("other.org", "example.com##.ad"))
    }

    @Test
    fun anExcludedDomainDropsTheRule() {
        assertEquals("", css("shop.example.com", "example.com,~shop.example.com##.ad"))
    }

    @Test
    fun anExceptionUnhidesWhatABroaderRuleHid() {
        assertEquals("", css("example.com", "##.ad", "example.com#@#.ad"))
    }

    // The rule that keeps somebody able to see their way around: a list selector
    // naming our reserved prefix could otherwise hide the focus ring itself.
    @Test
    fun aRuleNamingOurOwnNamespaceIsRefused() {
        val rule: String = css("example.org", "##.nm-focus-ring", "##.ad")
        assertFalse(rule, rule.contains("nm-"))
        assertTrue(rule, rule.contains(".ad"))
    }

    // One rule rather than one per selector: a page carrying a thousand separate
    // rules pays for them on every layout.
    @Test
    fun everySelectorSharesOneDeclaration() {
        assertEquals(
            ".a,.b,.c{display:none !important;}",
            css("example.org", "##.a", "##.b", "##.c"),
        )
    }

    @Test
    fun theSameSelectorTwiceIsWrittenOnce() {
        assertEquals(".a{display:none !important;}", css("example.org", "##.a", "example.org##.a"))
    }

    @Test
    fun nothingApplicableProducesNoStylesheetAtAll() {
        assertEquals("", css("example.org"))
    }
}
