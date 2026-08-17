/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

import com.nomercylabs.browser.input.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yModeTest {

    @Test
    fun touchExplorationAloneIsEnough() {
        assertTrue(
            A11yMode.isScreenReaderActive(
                accessibilityEnabled = true,
                touchExplorationEnabled = true,
                spokenFeedbackServices = 0,
            ),
        )
    }

    // A reader that never turns exploration on is still a reader, and the app
    // that only watched the one flag would keep a pointer under it.
    @Test
    fun aSpokenServiceAloneIsEnough() {
        assertTrue(
            A11yMode.isScreenReaderActive(
                accessibilityEnabled = true,
                touchExplorationEnabled = false,
                spokenFeedbackServices = 1,
            ),
        )
    }

    // Accessibility being switched off is the one answer that overrules both.
    @Test
    fun nothingCountsWhileAccessibilityIsOff() {
        assertFalse(
            A11yMode.isScreenReaderActive(
                accessibilityEnabled = false,
                touchExplorationEnabled = true,
                spokenFeedbackServices = 3,
            ),
        )
    }

    // A service with no spoken feedback — switch access, a magnifier — is not a
    // screen reader, and yielding the D-pad to it would leave the browser inert.
    @Test
    fun anEnabledServiceThatDoesNotSpeakIsNotAReader() {
        assertFalse(
            A11yMode.isScreenReaderActive(
                accessibilityEnabled = true,
                touchExplorationEnabled = false,
                spokenFeedbackServices = 0,
            ),
        )
    }

    @Test
    fun aReaderTakesTheModeWhateverItWas() {
        listOf(InputMode.Cursor, InputMode.Focus, InputMode.ScreenReader).forEach { current ->
            assertEquals(InputMode.ScreenReader, A11yMode.modeFor(true, current))
        }
    }

    // The failure this guards: staying in a mode that consumes no directional
    // key after the reader that owned it went away, which is a dead remote.
    @Test
    fun losingTheReaderHandsTheBrowserBackToThePointer() {
        assertEquals(InputMode.Cursor, A11yMode.modeFor(false, InputMode.ScreenReader))
    }

    @Test
    fun losingTheReaderLeavesAnyOtherModeAlone() {
        assertEquals(InputMode.Focus, A11yMode.modeFor(false, InputMode.Focus))
        assertEquals(InputMode.Cursor, A11yMode.modeFor(false, InputMode.Cursor))
    }

    // Per-site memory and the navigability probe are both conveniences. A reader
    // running right now is not, and neither may overrule it.
    @Test
    fun nothingElseMayChooseTheModeWhileAReaderIsRunning() {
        assertFalse(A11yMode.mayChooseMode(screenReaderActive = true))
        assertTrue(A11yMode.mayChooseMode(screenReaderActive = false))
    }
}
