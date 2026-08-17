/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page is the one input here that cannot be trusted to be well formed, so
 * every malformed shape has to end as "no candidates" rather than as a crash
 * in the middle of a keypress.
 */
class PageSnapshotParserTest {

    @Test
    fun aWellFormedSnapshotParses() {
        val json = """
            {"elements":[{"id":"nm1","left":10,"top":20,"right":110,"bottom":60,"order":3,"fixed":true}],
             "viewportWidth":1920,"viewportHeight":1080,"scrollY":40,"scrollHeight":5000,"focused":"nm1"}
        """.trimIndent()

        val snapshot = PageSnapshotParser.parse(json)!!
        assertEquals(1, snapshot.elements.size)
        assertEquals(Rect(10, 20, 110, 60), snapshot.elements.first().focusable.rect)
        assertEquals(3, snapshot.elements.first().focusable.documentOrder)
        assertTrue(snapshot.elements.first().isFixed)
        assertEquals(Rect(0, 0, 1920, 1080), snapshot.viewport)
        assertEquals("nm1", snapshot.focusedId)
    }

    @Test
    fun textThatIsNotJsonIsRefusedRatherThanThrowing() {
        assertNull(PageSnapshotParser.parse("<html>not json</html>"))
    }

    // A viewport of zero comes back from a page that has not laid out yet, and
    // searching against it would divide the screen into nothing.
    @Test
    fun aSnapshotWithNoViewportIsRefused() {
        assertNull(PageSnapshotParser.parse("""{"elements":[],"viewportWidth":0,"viewportHeight":0}"""))
    }

    @Test
    fun anElementWithoutAnIdIsSkippedRatherThanInvented() {
        val json = """
            {"elements":[{"left":0,"top":0,"right":10,"bottom":10},
                         {"id":"nm2","left":0,"top":0,"right":10,"bottom":10,"order":1}],
             "viewportWidth":1280,"viewportHeight":720}
        """.trimIndent()

        val snapshot = PageSnapshotParser.parse(json)!!
        assertEquals(listOf("nm2"), snapshot.elements.map { it.focusable.id })
    }

    @Test
    fun navigabilityParses() {
        val page = PageSnapshotParser.parseNavigability(
            """{"total":42,"visible":11,"viewportHeight":1080,"stealsFocus":true}""",
        )!!
        assertEquals(42, page.total)
        assertEquals(11, page.visible)
        assertTrue(page.stealsFocus)
    }
}
