/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

import com.nomercylabs.browser.input.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic layouts, each asserting the exact element a press must reach.
 *
 * A regression in this search is invisible in a screenshot and unmistakable in
 * the hand, so it is held to a mechanical ruler rather than to how it feels.
 */
class SpatialSearchTest {

    private val viewport = Rect(0, 0, 1920, 1080)

    private fun at(id: String, left: Int, top: Int, order: Int = 0, width: Int = 100, height: Int = 40) =
        Focusable(id, Rect(left, top, left + width, top + height), order)

    private fun move(result: SpatialResult): String =
        (result as SpatialResult.Move).id

    // The failure everyone recognises: DOWN jumping hundreds of pixels sideways
    // because that candidate's centre happened to be marginally nearer.
    @Test
    fun aCandidateInTheBeamBeatsANearerOneOutsideIt() {
        val source = Rect(500, 100, 600, 140)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("inBeamButLeft", left = 480, top = 300, order = 1),
                at("outOfBeamButNearer", left = 1000, top = 200, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("inBeamButLeft", move(result))
    }

    @Test
    fun aCandidateBesideTheSourceIsNotInTheDirectionOfTravelAtAll() {
        val source = Rect(500, 100, 600, 140)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("levelWithSource", left = 700, top = 100, order = 1),
                at("genuinelyBelow", left = 500, top = 400, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("genuinelyBelow", move(result))
    }

    // Staggered cards: rows that do not line up must still walk downward rather
    // than drifting to whichever card happens to be closest.
    @Test
    fun aStaggeredGridWalksDownItsOwnColumn() {
        val source = Rect(0, 0, 200, 100)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("nextRowSameColumn", left = 20, top = 160, order = 1, width = 200, height = 100),
                at("nextRowOver", left = 260, top = 130, order = 2, width = 200, height = 100),
            ),
            viewport = viewport,
        )
        assertEquals("nextRowSameColumn", move(result))
    }

    // Focus disappearing off screen is the worst thing a TV browser does, and a
    // search that only ranks geometry produces it happily.
    @Test
    fun aWinnerBelowTheFoldIsScrolledToRatherThanJumpedTo() {
        val source = Rect(0, 900, 200, 940)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(at("wayBelow", left = 0, top = 2000, order = 1)),
            viewport = viewport,
        )
        assertTrue(result is SpatialResult.ScrollThenRetry)
        assertTrue((result as SpatialResult.ScrollThenRetry).dy > 0)
    }

    @Test
    fun nothingInThatDirectionScrollsThePageThatWay() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(0, 0, 200, 40),
            candidates = emptyList(),
            viewport = viewport,
        )
        assertTrue(result is SpatialResult.ScrollThenRetry)
    }

    // A press that does nothing is a defect, so the last rung hands over to the
    // chrome rather than being swallowed.
    @Test
    fun aPageThatCannotScrollFurtherHandsTheKeyToTheChrome() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(0, 0, 200, 40),
            candidates = emptyList(),
            viewport = viewport,
            canScroll = false,
        )
        assertEquals(SpatialResult.LeavePage, result)
    }

    // Identical geometry must resolve the same way every time, or focus order
    // changes between loads of the same page.
    @Test
    fun identicalGeometryIsBrokenByDocumentOrder() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(0, 0, 100, 40),
            candidates = listOf(
                at("second", left = 0, top = 200, order = 7),
                at("first", left = 0, top = 200, order = 3),
            ),
            viewport = viewport,
        )
        assertEquals("first", move(result))
    }

    @Test
    fun travellingFarInTheIntendedDirectionBeatsDriftingSideways() {
        val source = Rect(500, 0, 600, 40)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("straightDownFar", left = 500, top = 600, order = 1),
                at("closeButSideways", left = 900, top = 100, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("straightDownFar", move(result))
    }

    @Test
    fun leftAndRightUseTheHorizontalBeam() {
        val source = Rect(500, 100, 600, 140)
        val candidates = listOf(
            at("sameRow", left = 700, top = 105, order = 1),
            at("nearerButAnotherRow", left = 620, top = 500, order = 2),
        )
        assertEquals(
            "sameRow",
            move(SpatialSearch.search(RemoteKey.Right, source, candidates, viewport)),
        )
    }

    @Test
    fun upIsTheMirrorOfDown() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Up,
            source = Rect(500, 500, 600, 540),
            candidates = listOf(
                at("above", left = 500, top = 300, order = 1),
                at("below", left = 500, top = 700, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("above", move(result))
    }
}
