/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.cursor

import com.nomercylabs.arrowz.input.RemoteKey

/**
 * The four numbers that decide how the cursor feels.
 *
 * Kept together and free of logic so they can be tuned from watching someone
 * use it, which is the only way this can be judged. Tests can prove the curve
 * is the curve; they cannot say whether it is the right one at three metres.
 */
data class CursorConfig(
    /** Slow enough to land on a link without overshooting. */
    val startSpeedPxPerSecond: Float = 420f,
    /** Held this long before accelerating, so a tap stays a small nudge. */
    val rampDelayMillis: Long = 220L,
    val accelerationPxPerSecondSquared: Float = 2600f,
    /** Capped, or a long hold crosses the screen faster than the eye follows. */
    val maxSpeedPxPerSecond: Float = 2400f,
    /**
     * What a tap moves, when no frame ran while the key was down.
     *
     * A tap is a key-down and a key-up milliseconds apart, and the pointer only
     * moves on a frame in between. Measured on the 8000: five taps of UP moved
     * the cursor from y=540 to y=540, and only a hold moved it at all. A press
     * that does nothing is the failure this whole interface is built to avoid,
     * and it was sitting on the most-used key in cursor mode.
     *
     * One ramp delay at the start speed, so a tap nudges by exactly what a hold
     * would have covered before it began accelerating.
     */
    val tapNudgePx: Float = 92f,
)

data class CursorPosition(val x: Float, val y: Float)

/**
 * Where the pointer is and how fast it is travelling.
 *
 * Pure: no Android types, no view, and the clock is passed in rather than read.
 * That is what makes "one tap moves a little, a two second hold crosses the
 * screen" checkable at exact millisecond offsets instead of with a stopwatch
 * pointed at a television.
 */
class CursorState(
    private val config: CursorConfig = CursorConfig(),
    initialX: Float = 0f,
    initialY: Float = 0f,
) {

    var x: Float = initialX
        private set

    var y: Float = initialY
        private set

    private val held: MutableSet<RemoteKey> = mutableSetOf()
    private var heldSinceMillis: Long = 0L
    private var lastFrameMillis: Long = 0L

    /** Whether any frame advanced the pointer while this press was down. */
    private var movedWhileHeld: Boolean = false

    val isMoving: Boolean get() = held.isNotEmpty()

    fun centreIn(width: Int, height: Int) {
        x = width / 2f
        y = height / 2f
    }

    fun press(key: RemoteKey, nowMillis: Long) {
        if (!DIRECTIONS.contains(key)) return
        if (held.isEmpty()) {
            heldSinceMillis = nowMillis
            lastFrameMillis = nowMillis
            movedWhileHeld = false
        }
        held.add(key)
    }

    /**
     * Releasing resets the ramp, so the next press starts precise again rather
     * than inheriting the speed the previous hold reached.
     */
    fun release(key: RemoteKey, width: Int = 0, height: Int = 0) {
        val wasHeld: Boolean = held.remove(key)

        // A tap that outran the frame clock still has to move something.
        if (wasHeld && !movedWhileHeld) nudge(key, width, height)

        if (held.isEmpty()) {
            heldSinceMillis = 0L
            lastFrameMillis = 0L
            movedWhileHeld = false
        }
    }

    private fun nudge(key: RemoteKey, width: Int, height: Int) {
        when (key) {
            RemoteKey.Left -> x -= config.tapNudgePx
            RemoteKey.Right -> x += config.tapNudgePx
            RemoteKey.Up -> y -= config.tapNudgePx
            RemoteKey.Down -> y += config.tapNudgePx
            else -> return
        }
        if (width > 0) x = x.coerceIn(0f, (width - 1).toFloat())
        if (height > 0) y = y.coerceIn(0f, (height - 1).toFloat())
    }

    fun releaseAll() {
        held.clear()
        heldSinceMillis = 0L
        lastFrameMillis = 0L
        movedWhileHeld = false
    }

    /**
     * Advances the pointer to [nowMillis] and clamps it inside the viewport.
     *
     * Clamping every frame rather than on demand is deliberate: a pointer that
     * leaves the screen is invisible, and an invisible pointer is
     * indistinguishable from a frozen browser.
     */
    fun advance(nowMillis: Long, width: Int, height: Int) {
        if (held.isEmpty()) return

        val deltaSeconds: Float = ((nowMillis - lastFrameMillis).coerceAtLeast(0L)) / MILLIS_PER_SECOND
        lastFrameMillis = nowMillis

        val speed: Float = speedAt(nowMillis)
        val step: Float = speed * deltaSeconds

        if (step > 0f) movedWhileHeld = true

        if (held.contains(RemoteKey.Left)) x -= step
        if (held.contains(RemoteKey.Right)) x += step
        if (held.contains(RemoteKey.Up)) y -= step
        if (held.contains(RemoteKey.Down)) y += step

        x = x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        y = y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    }

    /**
     * Start speed until the ramp delay passes, then linear acceleration to the
     * cap. The flat opening is what makes a tap a nudge rather than a jump.
     */
    fun speedAt(nowMillis: Long): Float {
        val heldMillis: Long = nowMillis - heldSinceMillis
        if (heldMillis <= config.rampDelayMillis) return config.startSpeedPxPerSecond

        val rampingSeconds: Float = (heldMillis - config.rampDelayMillis) / MILLIS_PER_SECOND
        val speed: Float = config.startSpeedPxPerSecond +
            config.accelerationPxPerSecondSquared * rampingSeconds
        return speed.coerceAtMost(config.maxSpeedPxPerSecond)
    }

    fun position(): CursorPosition = CursorPosition(x, y)

    private companion object {
        const val MILLIS_PER_SECOND: Float = 1000f
        val DIRECTIONS: Set<RemoteKey> =
            setOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right)
    }
}
