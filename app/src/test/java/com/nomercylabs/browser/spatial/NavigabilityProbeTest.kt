/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

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

    // An article with four links in a screenful is a page where most presses
    // would do nothing, and the pointer is the better tool.
    @Test
    fun aSparsePageStaysWithThePointer() {
        assertFalse(NavigabilityProbe.prefersFocusMode(page(total = 12, visible = 3)))
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
