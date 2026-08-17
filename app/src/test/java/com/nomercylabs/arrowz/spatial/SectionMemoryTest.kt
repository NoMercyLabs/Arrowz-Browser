/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.spatial

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

    /**
     * The article fixture: everything outside a row, grid or list reports the
     * same sentinel, so treating it as a section makes the whole body of a page
     * one row with one remembered child.
     *
     * Measured on a Wikipedia article before this: entering the header nav
     * remembered nothing, but the first link on the page had already been
     * remembered for the body, so DOWN out of the header resolved back to the
     * top of the document and the article was unreachable in either direction.
     */
    @Test
    fun theBodyOfAPageIsNotASectionAndRemembersNoChild() {
        val memory = SectionMemory()
        memory.remember("document", "firstLinkOnThePage")

        val body = listOf(
            element("firstLinkOnThePage", "document", 1),
            element("linkInTheArticle", "document", 40),
        )
        assertEquals(
            "linkInTheArticle",
            memory.resolve("headerNav", element("linkInTheArticle", "document", 40), body),
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
