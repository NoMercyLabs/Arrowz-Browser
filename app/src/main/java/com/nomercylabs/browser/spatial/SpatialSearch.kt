/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

import com.nomercylabs.browser.input.RemoteKey

/** A candidate's box, in the coordinate space the caller is working in. */
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Something focus can land on, with the order it appears in the document so
 * identical geometry resolves the same way twice.
 */
data class Focusable(val id: String, val rect: Rect, val documentOrder: Int)

/**
 * Where a press should send focus.
 *
 * [Move] names the winner. [ScrollThenRetry] means the best candidate is far
 * enough outside the viewport that throwing focus there would put it somewhere
 * the viewer cannot see; scroll first and search again. [LeavePage] means there
 * is nothing in that direction and the page cannot scroll further, so the
 * chrome takes over — a press that does nothing at all is a defect.
 */
sealed interface SpatialResult {
    data class Move(val id: String) : SpatialResult
    data class ScrollThenRetry(val dx: Int, val dy: Int) : SpatialResult
    data object LeavePage : SpatialResult
}

/**
 * D-pad navigation over a page's focusable elements.
 *
 * Deliberately not the WICG polyfill's model. Nearest-rectangle is what produces
 * the failure everyone recognises: DOWN jumping hundreds of pixels sideways
 * because that candidate's centre happened to be marginally closer than the one
 * just below and slightly left. Android solved this in `FocusFinder` years ago,
 * and these are its rules, so the chrome and the page feel like one interface.
 *
 * Pure. Geometry is testable without a device, and a regression here is
 * invisible in a screenshot but obvious in the hand, which is exactly the kind
 * of defect that needs a mechanical ruler.
 */
object SpatialSearch {

    fun search(
        direction: RemoteKey,
        source: Rect,
        candidates: List<Focusable>,
        viewport: Rect,
        canScroll: Boolean = true,
    ): SpatialResult {
        val inDirection: List<Focusable> = candidates
            .filter { candidate -> isBeyond(direction, source, candidate.rect) }

        if (inDirection.isEmpty()) {
            return if (canScroll) scrollFor(direction, viewport) else SpatialResult.LeavePage
        }

        /**
         * What is on screen is ranked first, and separately.
         *
         * Ranking everything together let a candidate far below the fold win on
         * the beam and turned an ordinary press into a screenful of scrolling
         * that skipped every link in between. The viewer's own screen is the
         * thing they are navigating, so a press moves within it while it can,
         * and scrolling is what happens when it cannot.
         *
         * This also retires the old "drop off-screen candidates when the page
         * cannot scroll" filter. With nowhere to scroll, an off-screen candidate
         * can never be brought into view — a closed off-canvas drawer past the
         * right edge, measured on DuckDuckGo on the 8010 — and now falls out of
         * the same rule rather than a second one.
         */
        val onScreen: List<Focusable> = inDirection
            .filter { candidate -> !isOffscreen(candidate.rect, viewport) }
        if (onScreen.isNotEmpty()) return SpatialResult.Move(best(direction, source, onScreen).id)

        if (!canScroll) return SpatialResult.LeavePage
        return revealScroll(direction, best(direction, source, inDirection).rect, viewport)
    }

    private fun best(direction: RemoteKey, source: Rect, candidates: List<Focusable>): Focusable =
        candidates.reduce { best, next ->
            if (isBetter(direction, source, next.rect, best.rect)) next
            else if (isBetter(direction, source, best.rect, next.rect)) best
            else if (next.documentOrder < best.documentOrder) next
            else best
        }

    /**
     * Strictly beyond, on both edges. A candidate that merely overlaps the
     * source is not in the direction of travel, whatever its centre says.
     */
    private fun isBeyond(direction: RemoteKey, source: Rect, candidate: Rect): Boolean =
        when (direction) {
            RemoteKey.Up -> candidate.bottom <= source.top
            RemoteKey.Down -> candidate.top >= source.bottom
            RemoteKey.Left -> candidate.right <= source.left
            RemoteKey.Right -> candidate.left >= source.right
            else -> false
        }

    private fun isBetter(direction: RemoteKey, source: Rect, candidate: Rect, rival: Rect): Boolean =
        when {
            beamBeats(direction, source, candidate, rival) -> true
            beamBeats(direction, source, rival, candidate) -> false
            else -> weightedDistance(direction, source, candidate) <
                weightedDistance(direction, source, rival)
        }

    /**
     * Whether being in the beam is enough for [candidate] to win outright.
     *
     * The beam is what makes movement feel like movement: project the source
     * across the travel axis, and something overlapping that band beats
     * something outside it however near the outsider is. But it is not
     * unconditional, and the qualification is the part that is easy to miss.
     *
     * Measured on DuckDuckGo's home page: from the button in the top right,
     * DOWN reached the search field's submit icon — in beam, but most of the
     * page further down — instead of the pair of toggles sitting well above it
     * and slightly left. Vertically, an in-beam candidate only wins when it is
     * genuinely nearer than the far edge of what it is beating; without that
     * test, one narrow column of the page swallows every downward press.
     */
    private fun beamBeats(direction: RemoteKey, source: Rect, candidate: Rect, rival: Rect): Boolean {
        val candidateInBeam: Boolean = isInBeam(direction, source, candidate)
        val rivalInBeam: Boolean = isInBeam(direction, source, rival)

        if (rivalInBeam || !candidateInBeam) return false
        if (!isBeyond(direction, source, rival)) return true

        // Horizontally the beam is unqualified: rows are the unit of meaning,
        // and staying in one is what a viewer expects from LEFT and RIGHT.
        if (direction == RemoteKey.Left || direction == RemoteKey.Right) return true

        return majorAxisDistance(direction, source, candidate) <
            majorAxisDistanceToFarEdge(direction, source, rival)
    }

    /** To the far side of the rival, which is what makes the comparison a test
     *  of "clearly nearer" rather than of "nearer by a pixel". */
    private fun majorAxisDistanceToFarEdge(direction: RemoteKey, source: Rect, candidate: Rect): Int =
        when (direction) {
            RemoteKey.Up -> source.top - candidate.top
            RemoteKey.Down -> candidate.bottom - source.bottom
            RemoteKey.Left -> source.left - candidate.left
            RemoteKey.Right -> candidate.right - source.right
            else -> 0
        }.coerceAtLeast(1)

    private fun isInBeam(direction: RemoteKey, source: Rect, candidate: Rect): Boolean =
        when (direction) {
            RemoteKey.Up, RemoteKey.Down ->
                candidate.right > source.left && candidate.left < source.right
            RemoteKey.Left, RemoteKey.Right ->
                candidate.bottom > source.top && candidate.top < source.bottom
            else -> false
        }

    /**
     * `13 × major² + minor²`, Android's own tuned weighting, used only when
     * neither candidate wins on the beam.
     *
     * Major distance is the expensive term, so a candidate barely past the
     * source wins over one much further along even when the near one sits well
     * off to the side. Keeping movement in a straight line is the beam's job,
     * not this one's.
     */
    private fun weightedDistance(direction: RemoteKey, source: Rect, candidate: Rect): Long {
        val major: Long = majorAxisDistance(direction, source, candidate).toLong()
        val minor: Long = minorAxisDistance(direction, source, candidate).toLong()
        return MAJOR_WEIGHT * major * major + minor * minor
    }

    /** Along the travel axis, measured edge to edge and never negative. */
    private fun majorAxisDistance(direction: RemoteKey, source: Rect, candidate: Rect): Int =
        when (direction) {
            RemoteKey.Up -> source.top - candidate.bottom
            RemoteKey.Down -> candidate.top - source.bottom
            RemoteKey.Left -> source.left - candidate.right
            RemoteKey.Right -> candidate.left - source.right
            else -> 0
        }.coerceAtLeast(0)

    /** Across the travel axis, centre to centre. */
    private fun minorAxisDistance(direction: RemoteKey, source: Rect, candidate: Rect): Int =
        when (direction) {
            RemoteKey.Up, RemoteKey.Down ->
                Math.abs((source.left + source.width / 2) - (candidate.left + candidate.width / 2))
            RemoteKey.Left, RemoteKey.Right ->
                Math.abs((source.top + source.height / 2) - (candidate.top + candidate.height / 2))
            else -> 0
        }

    private fun isOffscreen(rect: Rect, viewport: Rect): Boolean =
        rect.bottom > viewport.bottom + OFFSCREEN_TOLERANCE ||
            rect.top < viewport.top - OFFSCREEN_TOLERANCE ||
            rect.right > viewport.right + OFFSCREEN_TOLERANCE ||
            rect.left < viewport.left - OFFSCREEN_TOLERANCE

    /**
     * Scrolls exactly far enough to bring [target] inside the viewport, never
     * further than a screenful.
     *
     * The blind screenful this replaces is what makes a television browser feel
     * like it is throwing the page around: one press past the last visible link
     * moved the article a whole screen, so everything between the fold and the
     * next candidate went by unread and unreachable. Moving by what the next
     * candidate needs means the page travels the least that makes the press
     * mean something, which is what scrolling is for.
     *
     * Always at least one pixel. A candidate that is off screen on the other
     * axis produces no travel on this one, and returning a scroll of zero would
     * leave the retry reading the same geometry forever.
     */
    private fun revealScroll(direction: RemoteKey, target: Rect, viewport: Rect): SpatialResult {
        val vertical: Int = (viewport.height - SCROLL_OVERLAP).coerceAtLeast(1)
        val horizontal: Int = (viewport.width - SCROLL_OVERLAP).coerceAtLeast(1)
        return when (direction) {
            RemoteKey.Down ->
                SpatialResult.ScrollThenRetry(
                    0,
                    (target.bottom - viewport.bottom + REVEAL_MARGIN).coerceIn(1, vertical),
                )
            RemoteKey.Up ->
                SpatialResult.ScrollThenRetry(
                    0,
                    -(viewport.top - target.top + REVEAL_MARGIN).coerceIn(1, vertical),
                )
            RemoteKey.Right ->
                SpatialResult.ScrollThenRetry(
                    (target.right - viewport.right + REVEAL_MARGIN).coerceIn(1, horizontal),
                    0,
                )
            RemoteKey.Left ->
                SpatialResult.ScrollThenRetry(
                    -(viewport.left - target.left + REVEAL_MARGIN).coerceIn(1, horizontal),
                    0,
                )
            else -> SpatialResult.LeavePage
        }
    }

    /** A screenful less an overlap, so the line that was at the edge is still
     *  readable after the scroll and the viewer keeps their place. Used only
     *  when the direction holds no candidate at all, so there is nothing to
     *  measure a smaller move against. */
    private fun scrollFor(direction: RemoteKey, viewport: Rect): SpatialResult {
        val vertical: Int = viewport.height - SCROLL_OVERLAP
        val horizontal: Int = viewport.width - SCROLL_OVERLAP
        return when (direction) {
            RemoteKey.Up -> SpatialResult.ScrollThenRetry(0, -vertical)
            RemoteKey.Down -> SpatialResult.ScrollThenRetry(0, vertical)
            RemoteKey.Left -> SpatialResult.ScrollThenRetry(-horizontal, 0)
            RemoteKey.Right -> SpatialResult.ScrollThenRetry(horizontal, 0)
            else -> SpatialResult.LeavePage
        }
    }

    private const val MAJOR_WEIGHT: Long = 13
    private const val OFFSCREEN_TOLERANCE: Int = 8
    private const val SCROLL_OVERLAP: Int = 64

    /** Landing flush against the edge of the screen reads as half cut off on a
     *  television, so a revealed candidate is brought a little further in. */
    private const val REVEAL_MARGIN: Int = 24
}
