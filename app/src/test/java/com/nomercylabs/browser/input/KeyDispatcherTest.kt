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

    @Test
    fun backClosesChromeBeforeWalkingHistory() {
        val state = BrowserState(isChromeOpen = true, canGoBack = true)
        assertEquals(Command.CloseChrome, back(state))
    }

    @Test
    fun backWalksHistoryWhenNothingIsOverlaid() {
        assertEquals(Command.GoBack, back(BrowserState(canGoBack = true)))
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
    @Test
    fun keyDownProducesNothing() {
        assertNull(back(BrowserState(canGoBack = true), KeyPhase.Down))
        assertNull(KeyDispatcher.dispatch(RemoteKey.Center, KeyPhase.Down, BrowserState()))
        assertNull(KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Down, BrowserState()))
    }

    @Test
    fun upRevealsTheNavBarOnlyAtTheTopOfThePage() {
        assertEquals(
            Command.RevealNavBar,
            KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Up, BrowserState(isPageAtTop = true)),
        )
        assertEquals(
            Command.Move(RemoteKey.Up),
            KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Up, BrowserState(isPageAtTop = false)),
        )
    }

    @Test
    fun upDoesNotRevealTheNavBarWhileChromeOrFullscreenIsShowing() {
        val chromeOpen = BrowserState(isPageAtTop = true, isChromeOpen = true)
        assertEquals(Command.Move(RemoteKey.Up), KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Up, chromeOpen))

        val fullscreen = BrowserState(isPageAtTop = true, isFullscreen = true)
        assertEquals(Command.Move(RemoteKey.Up), KeyDispatcher.dispatch(RemoteKey.Up, KeyPhase.Up, fullscreen))
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
        val state = BrowserState(mode = InputMode.ScreenReader, isPageAtTop = true)
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

    @Test
    fun remainingDirectionsMoveOnKeyUp() {
        listOf(RemoteKey.Down, RemoteKey.Left, RemoteKey.Right).forEach { key ->
            assertEquals(Command.Move(key), KeyDispatcher.dispatch(key, KeyPhase.Up, BrowserState()))
            assertNull(KeyDispatcher.dispatch(key, KeyPhase.Down, BrowserState()))
        }
    }
}
