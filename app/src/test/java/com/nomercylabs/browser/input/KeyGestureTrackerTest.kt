/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyGestureTrackerTest {

    @Test
    fun aFirstPressIsADown() {
        val tracker = KeyGestureTracker()
        assertEquals(KeyPhase.Down, tracker.onDown(RemoteKey.Back, nowMillis = 0, repeatCount = 0))
    }

    @Test
    fun aHeldKeyBecomesALongPressOnceTheThresholdPasses() {
        val tracker = KeyGestureTracker(longPressMillis = 500)
        tracker.onDown(RemoteKey.Back, nowMillis = 0, repeatCount = 0)

        assertNull(tracker.onDown(RemoteKey.Back, nowMillis = 200, repeatCount = 1))
        assertEquals(
            KeyPhase.LongPress,
            tracker.onDown(RemoteKey.Back, nowMillis = 600, repeatCount = 2),
        )
    }

    // The framework flags the repeat at the same threshold every other app uses,
    // and an event that carries the flag needs no clock of ours to agree.
    @Test
    fun theFrameworksOwnLongPressFlagIsTakenAtItsWord() {
        val tracker = KeyGestureTracker(longPressMillis = 500)
        tracker.onDown(RemoteKey.Back, nowMillis = 0, repeatCount = 0)

        assertEquals(
            KeyPhase.LongPress,
            tracker.onDown(RemoteKey.Back, nowMillis = 1, repeatCount = 1, isLongPress = true),
        )
    }

    @Test
    fun aFlaggedLongPressFiresOnlyOnceForOneHold() {
        val tracker = KeyGestureTracker()
        tracker.onDown(RemoteKey.Back, nowMillis = 0, repeatCount = 0)
        tracker.onDown(RemoteKey.Back, nowMillis = 1, repeatCount = 1, isLongPress = true)

        assertNull(tracker.onDown(RemoteKey.Back, nowMillis = 2, repeatCount = 2, isLongPress = true))
    }

    // The app must eat this one. Handing it on let the system see a plain BACK,
    // so holding BACK opened the menu and then closed the browser behind it.
    @Test
    fun theReleaseAfterALongPressIsSwallowedRatherThanPassedOn() {
        val tracker = KeyGestureTracker()
        tracker.onDown(RemoteKey.Back, nowMillis = 0, repeatCount = 0)
        tracker.onDown(RemoteKey.Back, nowMillis = 1, repeatCount = 1, isLongPress = true)

        assertEquals(KeyGestureTracker.Release.Swallowed, tracker.onUp(RemoteKey.Back))
    }

    // A stray release during a window transition was read as a real press and
    // exited the browser two seconds after launch.
    @Test
    fun aReleaseWithNoMatchingPressIsUnknownRatherThanAPress() {
        assertEquals(KeyGestureTracker.Release.Unknown, KeyGestureTracker().onUp(RemoteKey.Back))
    }

    @Test
    fun anOrdinaryReleaseActs() {
        val tracker = KeyGestureTracker()
        tracker.onDown(RemoteKey.Back, nowMillis = 0, repeatCount = 0)

        assertEquals(KeyGestureTracker.Release.Acted(KeyPhase.Up), tracker.onUp(RemoteKey.Back))
    }
}
