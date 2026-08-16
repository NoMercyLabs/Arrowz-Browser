/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

import com.nomercylabs.browser.browser.UrlOrSearch.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlOrSearchTest {

    private fun resolve(input: String): Destination =
        UrlOrSearch.resolve(input, UrlOrSearch.DUCKDUCKGO)

    @Test
    fun aBareDomainIsAnAddress() {
        assertEquals(Destination.Url("https://example.com"), resolve("example.com"))
        assertEquals(Destination.Url("https://sub.example.co.uk/path"), resolve("sub.example.co.uk/path"))
    }

    @Test
    fun anExplicitSchemeIsKept() {
        assertEquals(Destination.Url("http://example.com"), resolve("http://example.com"))
        assertEquals(Destination.Url("https://example.com"), resolve("https://example.com"))
    }

    @Test
    fun localhostAndPortsAreAddresses() {
        assertEquals(Destination.Url("http://localhost"), resolve("localhost"))
        assertEquals(Destination.Url("http://localhost:8080"), resolve("localhost:8080"))
        assertEquals(Destination.Url("http://192.168.2.21"), resolve("192.168.2.21"))
        assertEquals(Destination.Url("http://192.168.2.21:5555"), resolve("192.168.2.21:5555"))
    }

    // A space is the strongest available signal: no hostname contains one.
    @Test
    fun textWithSpacesIsAQuery() {
        assertEquals(Destination.Search("how tall is the eiffel tower"), resolve("how tall is the eiffel tower"))
    }

    // The case that catches naive "contains a dot" implementations.
    @Test
    fun aSentenceContainingADotIsStillAQuery() {
        assertEquals(Destination.Search("what is a .com domain"), resolve("what is a .com domain"))
    }

    @Test
    fun aSingleWordWithNoDotIsAQuery() {
        assertEquals(Destination.Search("weather"), resolve("weather"))
    }

    // Typing javascript: into an address bar is the classic self-XSS route, and
    // file: would expose device storage to a pasted string.
    @Test
    fun dangerousSchemesAreRefusedRatherThanNavigatedOrSearched() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/",
            "content://media/external/images",
            "intent://scan/#Intent;scheme=zxing;end",
        ).forEach { input ->
            assertEquals("$input should be blocked", Destination.Blocked, resolve(input))
        }
    }

    @Test
    fun emptyInputDoesNothing() {
        assertEquals(Destination.Nothing, resolve(""))
        assertEquals(Destination.Nothing, resolve("   "))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(Destination.Url("https://example.com"), resolve("  example.com  "))
    }

    @Test
    fun queriesAreEncodedIntoTheSearchTemplate() {
        val url: String = UrlOrSearch.searchUrl("hello world & more", UrlOrSearch.DUCKDUCKGO)
        assertEquals("https://duckduckgo.com/?q=hello%20world%20%26%20more", url)
    }

    @Test
    fun nonAsciiQueriesAreEncodedAsUtf8() {
        val url: String = UrlOrSearch.searchUrl("café", UrlOrSearch.DUCKDUCKGO)
        assertTrue(url.endsWith("caf%C3%A9"))
    }
}
