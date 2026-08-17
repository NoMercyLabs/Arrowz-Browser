/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.forms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormOpenPolicyTest {

    @Test
    fun aFieldFocusedRightAfterAPressBelongsToTheViewer() {
        assertTrue(FormOpenPolicy.shouldOpen(activatedAtMillis = 1_000, reportedAtMillis = 1_040))
    }

    // The failure this policy exists for. Google, DuckDuckGo and most of the web
    // focus their own search box on load; interrupting for that raises a
    // keyboard over every home page the viewer opens.
    @Test
    fun aPageFocusingItsOwnSearchBoxOnLoadDoesNotInterrupt() {
        assertFalse(FormOpenPolicy.shouldOpen(activatedAtMillis = 0, reportedAtMillis = 4_000))
    }

    @Test
    fun aFocusLongAfterThePressIsThePagesRatherThanTheViewers() {
        assertFalse(
            FormOpenPolicy.shouldOpen(
                activatedAtMillis = 1_000,
                reportedAtMillis = 1_000 + FormOpenPolicy.WINDOW_MILLIS + 1,
            ),
        )
    }

    @Test
    fun theEdgeOfTheWindowStillCounts() {
        assertTrue(
            FormOpenPolicy.shouldOpen(
                activatedAtMillis = 1_000,
                reportedAtMillis = 1_000 + FormOpenPolicy.WINDOW_MILLIS,
            ),
        )
    }

    /** A report timed before the press it supposedly followed is a clock that
     *  disagrees with itself, and opening on it would be arbitrary. */
    @Test
    fun aReportFromBeforeThePressIsRefused() {
        assertFalse(FormOpenPolicy.shouldOpen(activatedAtMillis = 2_000, reportedAtMillis = 1_900))
    }
}
