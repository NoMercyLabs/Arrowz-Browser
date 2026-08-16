/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.input

/**
 * Decides what a keypress means. Pure: no Android types, no clock, no WebView.
 *
 * Every branch here is a table row in a unit test that runs without a device,
 * which matters because "which of BACK's four meanings fired" is close to
 * impossible to observe by looking at a television.
 */
object KeyDispatcher {

    /**
     * Returns the command a press produces, or null when the press is not ours
     * and the platform should handle it.
     */
    fun dispatch(key: RemoteKey, phase: KeyPhase, state: BrowserState): Command? {
        // A screen reader owns directional navigation entirely. Consuming even
        // one key here would fight it, and the user would feel it as focus
        // jumping to somewhere they did not ask for.
        if (state.mode == InputMode.ScreenReader && key != RemoteKey.Back) return null

        return when (key) {
            RemoteKey.Back -> dispatchBack(phase, state)
            RemoteKey.Center -> dispatchCenter(phase, state)
            RemoteKey.Up -> dispatchUp(phase, state)
            RemoteKey.Down, RemoteKey.Left, RemoteKey.Right ->
                if (phase == KeyPhase.Up) Command.Move(key) else null
        }
    }

    /**
     * BACK's four meanings, first match wins.
     *
     * The order is the whole specification. Exiting fullscreen must outrank
     * history, or leaving a video navigates the page underneath it; closing
     * chrome must outrank history for the same reason. Reaching [Command.ExitApp]
     * once history is exhausted is what guarantees no page can trap the user.
     */
    private fun dispatchBack(phase: KeyPhase, state: BrowserState): Command? = when (phase) {
        KeyPhase.LongPress -> Command.OpenMenu
        KeyPhase.Up -> when {
            state.isFullscreen -> Command.ExitFullscreen
            state.isChromeOpen -> Command.CloseChrome
            state.canGoBack -> Command.GoBack
            else -> Command.ExitApp
        }
        KeyPhase.Down -> null
    }

    private fun dispatchCenter(phase: KeyPhase, state: BrowserState): Command? = when (phase) {
        KeyPhase.LongPress -> Command.ToggleInputMode
        KeyPhase.Up -> Command.Activate
        KeyPhase.Down -> null
    }

    /**
     * UP is the only directional key with a second meaning. At the very top of
     * a page there is nothing above to move to, so the press would otherwise do
     * nothing, and a press that does nothing is the failure this interface is
     * built to avoid.
     */
    private fun dispatchUp(phase: KeyPhase, state: BrowserState): Command? {
        if (phase != KeyPhase.Up) return null
        return if (state.isPageAtTop && !state.isChromeOpen && !state.isFullscreen) {
            Command.RevealNavBar
        } else {
            Command.Move(RemoteKey.Up)
        }
    }
}
