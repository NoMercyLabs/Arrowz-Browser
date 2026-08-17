/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionMemoryTest {

    private fun element(id: String, section: String, order: Int = 0) = PageFocusable(
        focusable = Focusable(id, Rect(0, 0, 100, 40), order),
        isFixed = false,
        section = section,
    )

    private val row = listOf(
        element("first", "rowA", 1),
        element("third", "rowA", 3),
        element("belowFirst", "rowB", 4),
    )

    // The fixture the plan names: leave a grid and come back, and focus must
    // return to the item that was left rather than to the first one.
    @Test
    fun reEnteringASectionReturnsToTheItemItWasLeftOn() {
        val memory = SectionMemory()
        memory.remember("rowA", "third")

        assertEquals(
            "third",
            memory.resolve(fromSection = "rowB", winner = element("first", "rowA"), available = row),
        )
    }

    // Redirecting a move that stays inside one section would pin focus to the
    // remembered item and make the row impossible to walk at all.
    @Test
    fun movingWithinASectionIsOrdinaryGeometry() {
        val memory = SectionMemory()
        memory.remember("rowA", "third")

        assertEquals(
            "first",
            memory.resolve(fromSection = "rowA", winner = element("first", "rowA"), available = row),
        )
    }

    @Test
    fun aSectionWithNoMemoryTakesTheSearchesWinner() {
        assertEquals(
            "first",
            SectionMemory().resolve("rowB", element("first", "rowA"), row),
        )
    }

    // A remembered id that has since been removed from the page would send
    // focus nowhere, which is indistinguishable from the browser hanging.
    @Test
    fun aRememberedItemThatIsGoneFallsBackToTheWinner() {
        val memory = SectionMemory()
        memory.remember("rowA", "elementThatWasRemoved")

        assertEquals(
            "first",
            memory.resolve("rowB", element("first", "rowA"), row),
        )
    }

    @Test
    fun aNewPageForgetsEverySection() {
        val memory = SectionMemory()
        memory.remember("rowA", "third")
        memory.forget()

        assertEquals(
            "first",
            memory.resolve("rowB", element("first", "rowA"), row),
        )
    }
}
