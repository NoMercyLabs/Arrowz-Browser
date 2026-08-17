/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.input

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

        // Chrome open means chrome owns the D-pad. Two focus systems exist here
        // — the pointer for page content, Compose focus for our own surfaces —
        // and exactly one may consume a key. Returning null hands directional
        // keys and Center to Compose, which moves focus between the bar's
        // controls; BACK still resolves here, because closing the chrome is this
        // dispatcher's decision to make.
        if (state.isChromeOpen && key != RemoteKey.Back) return null

        return when (key) {
            RemoteKey.Back -> dispatchBack(phase, state)
            RemoteKey.Center -> dispatchCenter(phase, state)
            RemoteKey.Up -> dispatchUp(phase, state)
            RemoteKey.Down, RemoteKey.Left, RemoteKey.Right -> dispatchDirection(key, phase, state)
        }
    }

    /**
     * Focus mode moves in steps; cursor mode moves continuously.
     *
     * A pointer is a held state, so it starts on press and stops on release. A
     * focus step is a discrete event, so it happens once per press and a
     * release means nothing — repeating it on the way up would move focus twice
     * for one press.
     */
    private fun dispatchDirection(key: RemoteKey, phase: KeyPhase, state: BrowserState): Command? =
        if (state.mode == InputMode.Focus) {
            when (phase) {
                KeyPhase.Down -> Command.MoveFocus(key)
                KeyPhase.Up, KeyPhase.LongPress -> null
            }
        } else {
            when (phase) {
                KeyPhase.Down -> Command.StartMove(key)
                KeyPhase.Up -> Command.StopMove(key)
                KeyPhase.LongPress -> null
            }
        }

    /**
     * BACK's seven meanings, first match wins.
     *
     * The order is the whole specification. Exiting fullscreen must outrank
     * history, or leaving a video navigates the page underneath it; closing the
     * keyboard must outrank closing the bar that raised it, or one press throws
     * away what was being typed; closing a surface must outrank history for the
     * same reason. Releasing a focused page field sits below surfaces and above
     * history, so leaving a form is one press and leaving the page is the next,
     * rather than one press doing both.
     *
     * The home screen is the last rung before the door. Measured on the 8010:
     * open the first page from the home grid and BACK left the browser
     * altogether, because that page has no history behind it — so one press
     * went from a site to the launcher, with the grid the viewer came from
     * never appearing. An accidental exit is indistinguishable from a crash,
     * which is the complaint this browser exists to answer. Home first, then
     * the door, and reaching [Command.ExitApp] from there is what still
     * guarantees no page can trap anybody.
     */
    private fun dispatchBack(phase: KeyPhase, state: BrowserState): Command? = when (phase) {
        KeyPhase.LongPress -> Command.OpenMenu
        KeyPhase.Up -> when {
            state.isFullscreen -> Command.ExitFullscreen
            state.isEditingText -> Command.StopEditing
            state.isSurfaceOpen -> Command.CloseChrome
            // The page's own dialog is closer to the viewer than its history is.
            state.isPageModalOpen -> Command.DismissPageModal
            state.isPageFieldFocused -> Command.ReleasePageFocus
            state.canGoBack -> Command.GoBack
            !state.isShowingHome -> Command.GoHome
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
    private fun dispatchUp(phase: KeyPhase, state: BrowserState): Command? = if (
        // In focus mode UP is always a focus step. Reaching the bar is not
        // decided here but by the search: when nothing lies above and the page
        // cannot scroll further, it reports that it has run out and the chrome
        // takes over. One rule covers both, instead of two that can disagree.
        state.mode == InputMode.Focus
    ) {
        dispatchDirection(RemoteKey.Up, phase, state)
    } else when (phase) {
        // Decided on press, not release: the alternative is starting to move the
        // pointer and then revealing the bar when the key comes up, which reads
        // as the interface changing its mind.
        // Both conditions matter. The page must have nothing left to scroll
        // AND the pointer must already be against the top edge, so the reveal
        // is a deliberate "one more UP" rather than the first UP on a page
        // that simply has not been scrolled yet.
        KeyPhase.Down ->
            if (state.isPageAtTop && state.isCursorAtTopEdge &&
                !state.isChromeOpen && !state.isFullscreen
            ) {
                Command.RevealNavBar
            } else {
                Command.StartMove(RemoteKey.Up)
            }
        KeyPhase.Up -> Command.StopMove(RemoteKey.Up)
        KeyPhase.LongPress -> null
    }
}
