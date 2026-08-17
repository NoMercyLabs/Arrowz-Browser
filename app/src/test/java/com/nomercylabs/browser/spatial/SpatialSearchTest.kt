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
                at("inBeamButLeft", left = 480, top = 200, order = 1),
                at("outOfBeamButNearer", left = 1000, top = 190, order = 2),
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

    // The 13:1 weighting, tested where it actually decides: both candidates out
    // of the beam, so nothing beam-beats and only the weights are left. Major
    // distance is the expensive one, which is why a candidate a long way down
    // loses to one barely below and far to the side.
    @Test
    fun nearnessAlongTheTravelAxisIsWeightedThirteenTimesASidewaysOffset() {
        val source = Rect(500, 0, 600, 40)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("barelyBelowButFarSideways", left = 1400, top = 100, order = 1),
                at("wellBelowAndSlightlySideways", left = 620, top = 500, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("barelyBelowButFarSideways", move(result))
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

    // A sticky header floats over the body it scrolls above. Searching them as
    // one set makes focus ping-pong between the header and the article on every
    // press, so the caller filters by fixedness and this asserts the geometry
    // that filtering produces.
    @Test
    fun aStickyHeaderAndTheBodyBelowItAreSearchedApart() {
        val header = listOf(
            at("headerLeft", left = 0, top = 0, order = 1),
            at("headerRight", left = 300, top = 0, order = 2),
        )
        val body = listOf(
            at("article", left = 0, top = 400, order = 3),
            at("articleNext", left = 0, top = 600, order = 4),
        )

        // Standing in the header, searching only the header: RIGHT stays in it.
        assertEquals(
            "headerRight",
            move(SpatialSearch.search(RemoteKey.Right, Rect(0, 0, 100, 40), header, viewport)),
        )
        // Standing in the body, searching only the body: DOWN never climbs back
        // into the header however near it is.
        assertEquals(
            "articleNext",
            move(SpatialSearch.search(RemoteKey.Down, Rect(0, 400, 100, 440), body, viewport)),
        )
    }

    // Nested focusables are ordinary geometry, but a child inside the source's
    // own box is not beyond it in any direction and must never win.
    @Test
    fun aFocusableNestedInsideTheSourceIsNeverACandidate() {
        val source = Rect(0, 0, 400, 200)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("childInsideSource", left = 20, top = 20, order = 1),
                at("realNeighbour", left = 0, top = 300, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("realNeighbour", move(result))
    }

    // Overlapping cards: two elements sharing a boundary. The one that starts
    // exactly where the source ends is beyond it; the one that straddles is not.
    @Test
    fun anOverlappingCandidateIsNotBeyondTheSourceButAnAbuttingOneIs() {
        val source = Rect(0, 100, 200, 200)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = source,
            candidates = listOf(
                at("straddlesTheEdge", left = 0, top = 180, order = 1, height = 100),
                at("startsWhereSourceEnds", left = 0, top = 200, order = 2, height = 100),
            ),
            viewport = viewport,
        )
        assertEquals("startsWhereSourceEnds", move(result))
    }

    // Measured on DuckDuckGo's home page on the 8010, and wrong: from the
    // button in the top right, DOWN reached the search field's submit icon
    // rather than the pair of toggles sitting well above it and slightly left.
    // The submit is in the beam, but most of the page further down. An in-beam
    // candidate only wins vertically when it is genuinely nearer than the far
    // edge of what it beats; without that test, one narrow column of a page
    // swallows every downward press.
    @Test
    fun anInBeamCandidateFarBelowLosesToACloserGroupBesideIt() {
        val topRightButton = Rect(1530, 88, 1735, 146)
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = topRightButton,
            candidates = listOf(
                Focusable("toggles", Rect(750, 495, 1170, 560), 2),
                Focusable("submitIcon", Rect(1490, 620, 1560, 680), 3),
            ),
            viewport = viewport,
        )
        assertEquals("toggles", move(result))
    }

    // The qualification must not undo the beam where the beam is right: a
    // candidate directly below and close still beats one off to the side.
    @Test
    fun anInBeamCandidateThatIsGenuinelyNearerStillWins() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(500, 100, 600, 140),
            candidates = listOf(
                at("directlyBelow", left = 500, top = 200, order = 1),
                at("offToTheSide", left = 1200, top = 180, order = 2),
            ),
            viewport = viewport,
        )
        assertEquals("directlyBelow", move(result))
    }

    // Measured on DuckDuckGo on the 8010: 56 collected elements, 10 of them on
    // screen. The rest were a closed off-canvas drawer parked past the right
    // edge of a page that cannot scroll sideways. RIGHT from the header landed
    // in it, focus went where nobody could see it, and the remote appeared to
    // stop working.
    @Test
    fun anUnreachableCandidateIsNotFocusedOnAPageThatCannotScrollToIt() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Right,
            source = Rect(768, 45, 864, 71),
            candidates = listOf(
                at("closedDrawer", left = 982, top = 54, order = 1),
                at("theMenuButton", left = 900, top = 45, order = 2, width = 32, height = 32),
            ),
            viewport = Rect(0, 0, 960, 540),
            canScroll = false,
        )
        assertEquals("theMenuButton", move(result))
    }

    @Test
    fun withNothingReachableTheChromeTakesTheKeyRatherThanFocusVanishing() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Right,
            source = Rect(768, 45, 864, 71),
            candidates = listOf(at("closedDrawer", left = 982, top = 54, order = 1)),
            viewport = Rect(0, 0, 960, 540),
            canScroll = false,
        )
        assertEquals(SpatialResult.LeavePage, result)
    }

    /** The exclusion must not fire where scrolling can still bring the winner
     *  into view, or every page longer than a screen stops walking downward. */
    @Test
    fun anOffscreenCandidateOnAScrollablePageIsStillScrolledTo() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(0, 400, 200, 440),
            candidates = listOf(at("belowTheFold", left = 0, top = 900, order = 1)),
            viewport = Rect(0, 0, 960, 540),
            canScroll = true,
        )
        assertTrue(result is SpatialResult.ScrollThenRetry)
    }

    /**
     * What is on the viewer's screen is ranked before what is not.
     *
     * The beam is right about direction and says nothing about whether the
     * winner can be seen, so a candidate a few pixels past the fold could beat
     * a card filling the screen and turn one press into a scroll that went
     * straight past it. The screen somebody is looking at is the thing they are
     * navigating; scrolling is what happens when it runs out.
     */
    @Test
    fun aCandidateOnScreenIsPreferredToOneJustPastTheFoldThatWouldBeatItOnTheBeam() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(400, 20, 600, 60),
            candidates = listOf(
                Focusable("aCardFillingTheScreen", Rect(50, 100, 250, 548), 1),
                Focusable("justPastTheFold", Rect(400, 509, 600, 549), 2),
            ),
            viewport = Rect(0, 0, 960, 540),
        )
        assertEquals("aCardFillingTheScreen", move(result))
    }

    /**
     * The scroll is measured against the candidate it is going to, not against
     * the height of the screen.
     *
     * A blind screenful is what makes a television browser feel like it throws
     * the page around: one press past the last visible link moved the article a
     * whole screen, and everything between the fold and the next candidate went
     * by unread.
     */
    @Test
    fun theScrollIsOnlyAsFarAsItTakesToRevealTheNextCandidate() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(0, 400, 200, 440),
            candidates = listOf(at("justBelowTheFold", left = 0, top = 700, order = 1)),
            viewport = Rect(0, 0, 960, 540),
        )
        // Its bottom edge, plus the margin that keeps it off the edge of the
        // screen -- a quarter of the page rather than all of it.
        assertEquals(224, (result as SpatialResult.ScrollThenRetry).dy)
    }

    /** And never more than a screenful, however far away the candidate is, or
     *  the page teleports and the viewer loses their place entirely. */
    @Test
    fun revealingSomethingVeryFarAwayStillMovesAtMostAScreenful() {
        val result = SpatialSearch.search(
            direction = RemoteKey.Down,
            source = Rect(0, 400, 200, 440),
            candidates = listOf(at("milesBelow", left = 0, top = 9000, order = 1)),
            viewport = Rect(0, 0, 960, 540),
        )
        assertEquals(476, (result as SpatialResult.ScrollThenRetry).dy)
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
