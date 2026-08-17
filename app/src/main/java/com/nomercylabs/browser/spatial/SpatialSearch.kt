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
        val inDirection: List<Focusable> = candidates.filter { candidate ->
            isBeyond(direction, source, candidate.rect)
        }

        if (inDirection.isEmpty()) {
            return if (canScroll) scrollFor(direction, viewport) else SpatialResult.LeavePage
        }

        val winner: Focusable = inDirection.minWith(ranking(direction, source))

        // Scroll before jumping. Focus disappearing off screen is the single
        // worst thing a television browser does, and it is what a search that
        // only ranks geometry will happily produce.
        if (canScroll && isOffscreen(winner.rect, viewport)) {
            return scrollFor(direction, viewport)
        }
        return SpatialResult.Move(winner.id)
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

    private fun ranking(direction: RemoteKey, source: Rect): Comparator<Focusable> =
        compareBy<Focusable> { candidate ->
            // The beam outranks distance outright. Project the source onto the
            // axis across the travel; anything overlapping that band beats
            // everything outside it however near the outsider happens to be.
            // This one rule is what makes movement feel like movement rather
            // than teleporting.
            if (isInBeam(direction, source, candidate.rect)) 0 else 1
        }
            .thenBy { candidate -> weightedDistance(direction, source, candidate.rect) }
            .thenBy { candidate -> candidate.documentOrder }

    private fun isInBeam(direction: RemoteKey, source: Rect, candidate: Rect): Boolean =
        when (direction) {
            RemoteKey.Up, RemoteKey.Down ->
                candidate.right > source.left && candidate.left < source.right
            RemoteKey.Left, RemoteKey.Right ->
                candidate.bottom > source.top && candidate.top < source.bottom
            else -> false
        }

    /**
     * `13 × major² + minor²`, Android's own tuned weighting. Travelling far in
     * the intended direction is cheap; drifting sideways is not.
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

    /** A screenful less an overlap, so the line that was at the edge is still
     *  readable after the scroll and the viewer keeps their place. */
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
}
