/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.cursor

/**
 * Decides how far the page should scroll when the pointer is held against an
 * edge.
 *
 * Moving the pointer never dispatches ACTION_MOVE, because that would read as a
 * drag and scroll or select text on every movement. With drags off the table,
 * the edges are how a long page is read at all, and this is what keeps the
 * six-key baseline intact without borrowing CHANNEL_UP or any seventh key.
 *
 * Pure, so the ramp is testable without a page to scroll.
 */
object EdgeScroller {

    /**
     * How close to the edge counts. Generous, because landing the pointer
     * exactly on the final pixel with an accelerating cursor is a game nobody
     * should have to play.
     */
    const val EDGE_BAND_PX: Float = 48f

    private const val BASE_SPEED_PX_PER_SECOND: Float = 900f
    private const val MAX_SPEED_PX_PER_SECOND: Float = 3600f
    private const val RAMP_PER_SECOND: Float = 2200f

    data class Scroll(val dx: Int, val dy: Int)

    /**
     * @param heldMillis how long the pointer has rested against the edge, which
     *   is what the ramp is a function of.
     */
    fun scrollFor(
        position: CursorPosition,
        width: Int,
        height: Int,
        heldMillis: Long,
        frameMillis: Long,
    ): Scroll {
        if (frameMillis <= 0L) return Scroll(0, 0)

        val speed: Float = (BASE_SPEED_PX_PER_SECOND + RAMP_PER_SECOND * (heldMillis / 1000f))
            .coerceAtMost(MAX_SPEED_PX_PER_SECOND)
        val step: Int = (speed * (frameMillis / 1000f)).toInt()
        if (step == 0) return Scroll(0, 0)

        val dx: Int = when {
            position.x <= EDGE_BAND_PX -> -step
            position.x >= width - 1 - EDGE_BAND_PX -> step
            else -> 0
        }
        val dy: Int = when {
            position.y <= EDGE_BAND_PX -> -step
            position.y >= height - 1 - EDGE_BAND_PX -> step
            else -> 0
        }
        return Scroll(dx, dy)
    }
}
