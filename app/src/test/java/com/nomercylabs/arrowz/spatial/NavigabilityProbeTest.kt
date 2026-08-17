/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.spatial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigabilityProbeTest {

    private fun page(total: Int, visible: Int, stealsFocus: Boolean = false) =
        Navigability(total = total, visible = visible, viewportHeight = 1080, stealsFocus = stealsFocus)

    @Test
    fun aDenseNavigablePageStartsInFocusMode() {
        assertTrue(NavigabilityProbe.prefersFocusMode(page(total = 40, visible = 14)))
    }

    // An article with two or three links in a screenful is a page where most
    // presses would do nothing, and the pointer is the better tool.
    @Test
    fun aSparsePageStaysWithThePointer() {
        assertFalse(NavigabilityProbe.prefersFocusMode(page(total = 12, visible = 3)))
    }

    // Measured on the 8010. DuckDuckGo's home page is a header pair, two
    // toggles, a field and its submit: six reachable targets, nothing below the
    // fold. It reported 56 while a closed off-canvas drawer was still being
    // collected and cleared the old total gate; once that was fixed it reported
    // an honest 6 and fell to the pointer. The page never changed.
    @Test
    fun aShortPageThatIsEntirelyOnScreenStillWalksByFocus() {
        assertTrue(NavigabilityProbe.prefersFocusMode(page(total = 6, visible = 6)))
    }

    /** The clearest case there is for focus: two fields, a button and a link. */
    @Test
    fun aLoginFormWalksByFocus() {
        assertTrue(NavigabilityProbe.prefersFocusMode(page(total = 4, visible = 4)))
    }

    @Test
    fun aPageWithAlmostNothingFocusableStaysWithThePointer() {
        assertFalse(NavigabilityProbe.prefersFocusMode(page(total = 2, visible = 2)))
    }

    // Two systems moving focus is worse than either alone, so a page that grabs
    // focus on load keeps it.
    @Test
    fun aPageThatMovesFocusItselfIsLeftAlone() {
        assertFalse(NavigabilityProbe.prefersFocusMode(page(total = 60, visible = 20, stealsFocus = true)))
    }
}
