/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyDispatcherTest {

    private fun back(state: BrowserState, phase: KeyPhase = KeyPhase.Up): Command? =
        KeyDispatcher.dispatch(RemoteKey.Back, phase, state)

    @Test
    fun backExitsFullscreenBeforeAnythingElse() {
        val state = BrowserState(isFullscreen = true, isChromeOpen = true, canGoBack = true)
        assertEquals(Command.ExitFullscreen, back(state))
    }

    // Measured on the 8010: with the keyboard up, one BACK that closed the bar
    // threw away what was being typed, because the IME and the bar are two
    // surfaces and BACK only ever dismissed the outer one.
    @Test
    fun backClosesTheKeyboardBeforeTheBarThatRaisedIt() {
        val state = BrowserState(isChromeOpen = true, isEditingText = true, canGoBack = true)
        assertEquals(Command.StopEditing, back(state))
    }

    @Test
    fun backExitsFullscreenEvenWhileEditing() {
        val state = BrowserState(isFullscreen = true, isEditingText = true, isChromeOpen = true)
        assertEquals(Command.ExitFullscreen, back(state))
    }

    @Test
    fun backClosesChromeBeforeWalkingHistory() {
        val state = BrowserState(isChromeOpen = true, canGoBack = true)
        assertEquals(Command.CloseChrome, back(state))
    }

    @Test
    fun backWalksHistoryWhenNothingIsOverlaid() {
        assertEquals(Command.GoBack, back(BrowserState(canGoBack = true)))
    }

    // A field on the page holding focus is its own rung. Walking history or
    // exiting the browser while the caret sits in a login box is the most
    // alarming thing an input can do, and it is what every other rung would
    // have done here.
    @Test
    fun backReleasesAFocusedPageFieldBeforeWalkingHistory() {
        val state = BrowserState(isPageFieldFocused = true, canGoBack = true)
        assertEquals(Command.ReleasePageFocus, back(state))
    }

    @Test
    fun backReleasesAFocusedPageFieldRatherThanExitingTheApp() {
        val state = BrowserState(isPageFieldFocused = true, canGoBack = false)
        assertEquals(Command.ReleasePageFocus, back(state))
    }

    // Our own sheet sits above the page's field: one press leaves the sheet,
    // the next releases the field, the one after that leaves the page.
    @Test
    fun aChromeSurfaceOverAFocusedFieldClosesFirst() {
        val state = BrowserState(isChromeOpen = true, isPageFieldFocused = true, canGoBack = true)
        assertEquals(Command.CloseChrome, back(state))
    }

    // The guarantee that no page can trap the user: once history is exhausted,
    // BACK always reaches the app exit rather than becoming a no-op.
    @Test
    fun backExitsTheAppWhenHistoryIsExhausted() {
        assertEquals(Command.ExitApp, back(BrowserState(canGoBack = false)))
    }

    @Test
    fun longBackOpensTheMenuFromEveryState() {
        val states = listOf(
            BrowserState(),
            BrowserState(isFullscreen = true),
            BrowserState(isChromeOpen = true),
            BrowserState(canGoBack = true),
        )
        states.forEach { state ->
            assertEquals(Command.OpenMenu, back(state, KeyPhase.LongPress))
        }
    }

    // Acting on key-down would fire the command twice, once on the way down and
    // again on the way up.
    // BACK and Center act on release. Directional keys are the exception:
    // movement is a held state, so it must begin on press.
    @Test
    fun backAndCentreProduceNothingOnKeyDown() {
        assertNull(back(BrowserState(canGoBack = true), KeyPhase.Down))
        assertNull(KeyDispatcher.dispatch(RemoteKey.Center, KeyPhase.Down, BrowserState()))
    }

    @Test
    fun upRevealsTheNavBarOnlyAtTheTopOfThePage() {
        assertEquals(
            Command.RevealNavBar,
            KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Down, BrowserState(isPageAtTop = true, isCursorAtTopEdge = true)),
        )
        assertEquals(
            Command.StartMove(RemoteKey.Up),
            KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Down, BrowserState(isPageAtTop = false)),
        )
    }

    @Test
    fun upDoesNotRevealTheNavBarWhileFullscreenIsShowing() {
        val fullscreen = BrowserState(isPageAtTop = true, isCursorAtTopEdge = true, isFullscreen = true)
        assertEquals(
            Command.StartMove(RemoteKey.Up),
            KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Down, fullscreen),
        )
    }

    // Exactly one focus system may consume a key. With chrome open, directional
    // keys and Center belong to Compose focus, so the pointer must not move.
    @Test
    fun chromeOpenMeansChromeOwnsTheDpad() {
        val open = BrowserState(isChromeOpen = true, isPageAtTop = true, isCursorAtTopEdge = true)
        listOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right, RemoteKey.Center)
            .forEach { key ->
                assertNull(KeyDispatcher.dispatch(key, KeyPhase.Down, open))
                assertNull(KeyDispatcher.dispatch(key, KeyPhase.Up, open))
            }
    }

    // BACK is the exception: closing the chrome is the dispatcher's decision.
    @Test
    fun backStillClosesChromeWhileItOwnsTheDpad() {
        assertEquals(
            Command.CloseChrome,
            KeyDispatcher.dispatch(
                RemoteKey.Back,
                KeyPhase.Up,
                BrowserState(isChromeOpen = true, canGoBack = true),
            ),
        )
    }

    @Test
    fun centreActivatesAndLongCentreTogglesMode() {
        assertEquals(Command.Activate, KeyDispatcher.dispatch(RemoteKey.Center, KeyPhase.Up, BrowserState()))
        assertEquals(
            Command.ToggleInputMode,
            KeyDispatcher.dispatch(RemoteKey.Center, KeyPhase.LongPress, BrowserState()),
        )
    }

    // A screen reader drives its own focus. Consuming a directional key here
    // would fight it, and the user would feel focus jumping somewhere they did
    // not ask for.
    @Test
    fun screenReaderModeConsumesNoDirectionalKeys() {
        val state = BrowserState(mode = InputMode.ScreenReader, isPageAtTop = true, isCursorAtTopEdge = true)
        listOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right, RemoteKey.Center)
            .forEach { key ->
                assertNull(KeyDispatcher.dispatch(key, KeyPhase.Up, state))
                assertNull(KeyDispatcher.dispatch(key, KeyPhase.LongPress, state))
            }
    }

    // BACK is the exception: exiting the app must work with a screen reader on.
    @Test
    fun screenReaderModeStillHandlesBack() {
        val state = BrowserState(mode = InputMode.ScreenReader, canGoBack = true)
        assertEquals(Command.GoBack, back(state))
        assertEquals(Command.ExitApp, back(state.copy(canGoBack = false)))
    }

    // Held movement: press starts it, release stops it. A single command on
    // release would describe a jump, which is not what an accelerating pointer
    // does.
    @Test
    fun directionsStartMovingOnPressAndStopOnRelease() {
        listOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right).forEach { key ->
            val moving = BrowserState(isPageAtTop = false)
            assertEquals(Command.StartMove(key), KeyDispatcher.dispatch(key, KeyPhase.Down, moving))
            assertEquals(Command.StopMove(key), KeyDispatcher.dispatch(key, KeyPhase.Up, moving))
        }
    }

    // Releasing UP must still stop the pointer even when the press revealed the
    // nav bar instead of starting movement, or a stuck direction is possible.
    @Test
    fun releasingUpAlwaysStopsMovement() {
        assertEquals(
            Command.StopMove(RemoteKey.Up),
            KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Up, BrowserState(isPageAtTop = true, isCursorAtTopEdge = true)),
        )
    }

    // The defect this guards: on a freshly loaded page the pointer could not be
    // moved upward at all, because every UP was read as a request for the nav
    // bar.
    @Test
    fun upMovesThePointerWhenThePageIsAtTopButTheCursorIsNot() {
        assertEquals(
            Command.StartMove(RemoteKey.Up),
            KeyDispatcher.dispatch(
                RemoteKey.Up,
                KeyPhase.Down,
                BrowserState(isPageAtTop = true, isCursorAtTopEdge = false),
            ),
        )
    }
}
