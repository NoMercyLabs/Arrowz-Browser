/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.cursor

import com.nomercylabs.arrowz.input.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorStateTest {

    private val config = CursorConfig()

    private fun centred(): CursorState =
        CursorState(config, initialX = 960f, initialY = 540f)

    @Test
    fun speedStaysFlatUntilTheRampDelayPasses() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)

        assertEquals(config.startSpeedPxPerSecond, state.speedAt(0L), TOLERANCE)
        assertEquals(config.startSpeedPxPerSecond, state.speedAt(config.rampDelayMillis), TOLERANCE)
    }

    @Test
    fun speedAcceleratesAfterTheRampDelay() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)

        val early: Float = state.speedAt(config.rampDelayMillis + 100L)
        val later: Float = state.speedAt(config.rampDelayMillis + 400L)

        assertTrue("expected acceleration, got $early then $later", later > early)
        assertTrue(early > config.startSpeedPxPerSecond)
    }

    @Test
    fun speedIsCapped() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)

        assertEquals(config.maxSpeedPxPerSecond, state.speedAt(10_000L), TOLERANCE)
    }

    // The behaviour that makes the pointer usable: a quick tap nudges rather
    // than jumping across the screen.
    @Test
    fun aShortTapMovesASmallDistance() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)
        state.advance(nowMillis = 80L, width = 1920, height = 1080)

        val travelled: Float = state.x - 960f
        assertTrue("a tap moved $travelled px", travelled in 10f..80f)
    }

    @Test
    fun aLongHoldCrossesTheScreen() {
        val state = CursorState(config, initialX = 0f, initialY = 540f)
        state.press(RemoteKey.Right, nowMillis = 0L)
        var now = 0L
        while (now < 2_000L) {
            now += 16L
            state.advance(now, width = 1920, height = 1080)
        }
        assertEquals(1919f, state.x, TOLERANCE)
    }

    @Test
    fun releasingResetsTheRampSoTheNextPressIsPreciseAgain() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)
        state.advance(nowMillis = 1_500L, width = 1920, height = 1080)
        state.release(RemoteKey.Right)

        state.press(RemoteKey.Right, nowMillis = 2_000L)
        assertEquals(config.startSpeedPxPerSecond, state.speedAt(2_000L), TOLERANCE)
    }

    @Test
    fun releasedKeysStopMovement() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)
        state.release(RemoteKey.Right)

        val before: Float = state.x
        state.advance(nowMillis = 500L, width = 1920, height = 1080)
        assertEquals(before, state.x, TOLERANCE)
    }

    // An off-screen pointer is invisible, and an invisible pointer is
    // indistinguishable from a frozen browser.
    @Test
    fun positionIsClampedAtEveryEdge() {
        val width = 1920
        val height = 1080

        listOf(
            RemoteKey.Left to { s: CursorState -> assertEquals(0f, s.x, TOLERANCE) },
            RemoteKey.Right to { s: CursorState -> assertEquals(1919f, s.x, TOLERANCE) },
            RemoteKey.Up to { s: CursorState -> assertEquals(0f, s.y, TOLERANCE) },
            RemoteKey.Down to { s: CursorState -> assertEquals(1079f, s.y, TOLERANCE) },
        ).forEach { (key, assertion) ->
            val state = centred()
            state.press(key, nowMillis = 0L)
            var now = 0L
            while (now < 5_000L) {
                now += 16L
                state.advance(now, width, height)
            }
            assertion(state)
        }
    }

    @Test
    fun diagonalMovementIsSupported() {
        val state = centred()
        state.press(RemoteKey.Right, nowMillis = 0L)
        state.press(RemoteKey.Down, nowMillis = 0L)
        state.advance(nowMillis = 100L, width = 1920, height = 1080)

        assertTrue(state.x > 960f)
        assertTrue(state.y > 540f)
    }

    @Test
    fun centreInPlacesThePointerInTheMiddle() {
        val state = CursorState(config)
        state.centreIn(1920, 1080)
        assertEquals(960f, state.x, TOLERANCE)
        assertEquals(540f, state.y, TOLERANCE)
    }

    @Test
    fun nonDirectionalKeysDoNotStartMovement() {
        val state = centred()
        state.press(RemoteKey.Center, nowMillis = 0L)
        state.press(RemoteKey.Back, nowMillis = 0L)
        assertTrue(!state.isMoving)
    }

    private companion object {
        const val TOLERANCE: Float = 0.01f
    }

    // Measured on the 8000: five taps of UP moved the cursor from y=540 to
    // y=540. A tap is a key-down and a key-up milliseconds apart and the
    // pointer only moves on a frame in between, so no frame ever ran. A press
    // that does nothing is the failure this interface exists to avoid.
    @Test
    fun aTapMovesThePointerEvenWhenNoFrameRan() {
        val cursor = CursorState(CursorConfig(), initialX = 500f, initialY = 540f)

        cursor.press(RemoteKey.Up, nowMillis = 0)
        cursor.release(RemoteKey.Up, width = 1920, height = 1080)

        assertTrue("a tap must move the pointer", cursor.y < 540f)
    }

    @Test
    fun aHoldThatAlreadyMovedIsNotNudgedAgainOnRelease() {
        val cursor = CursorState(CursorConfig(), initialX = 500f, initialY = 540f)

        cursor.press(RemoteKey.Up, nowMillis = 0)
        cursor.advance(nowMillis = 200, width = 1920, height = 1080)
        val afterFrames: Float = cursor.y
        cursor.release(RemoteKey.Up, width = 1920, height = 1080)

        assertEquals(afterFrames, cursor.y, 0.01f)
    }

    @Test
    fun aTapCannotPushThePointerOffScreen() {
        val cursor = CursorState(CursorConfig(), initialX = 500f, initialY = 4f)

        cursor.press(RemoteKey.Up, nowMillis = 0)
        cursor.release(RemoteKey.Up, width = 1920, height = 1080)

        assertEquals(0f, cursor.y, 0.01f)
    }
}
