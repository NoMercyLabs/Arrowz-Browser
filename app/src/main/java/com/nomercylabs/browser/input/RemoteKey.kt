/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.input

/**
 * The six keys every Android TV remote sends. Not a subset of a larger set that
 * grows later: a plain Chromecast voice remote delivers these and nothing else
 * to an app, so this is the whole vocabulary the interface may depend on.
 *
 * HOME and the assistant button are consumed by the system and never arrive.
 * Volume is unreliable on HDMI ARC setups. Media transport keys reach the app
 * through MediaSession rather than here, which is why they are absent.
 */
enum class RemoteKey {
    Up,
    Down,
    Left,
    Right,
    Center,
    Back,
}

enum class KeyPhase {
    Down,
    Up,
    LongPress,
}

/**
 * How directional keys and Center are interpreted.
 *
 * [ScreenReader] exists from the start rather than being added when
 * accessibility work lands, because it is not "cursor off". A screen reader
 * drives its own focus, so the app must consume nothing at all; treating it as
 * a variant of another mode leaves our handling still eating presses.
 */
enum class InputMode {
    Cursor,
    Focus,
    ScreenReader,
}

/**
 * Everything a keypress decision is allowed to depend on.
 *
 * Deliberately small and free of Android types. If a decision needs a fact that
 * is not here, the fact belongs here rather than the decision belonging
 * elsewhere.
 */
data class BrowserState(
    val mode: InputMode = InputMode.Cursor,
    val isFullscreen: Boolean = false,
    val isChromeOpen: Boolean = false,
    val canGoBack: Boolean = false,
    val isPageAtTop: Boolean = false,
    /**
     * Whether the pointer itself has reached the top edge of the screen.
     *
     * Needed because "page is scrolled to the top" is not enough to mean the
     * user asked for the nav bar. With a cursor, a page at scroll zero is the
     * normal state of every freshly loaded page, so keying the reveal on that
     * alone makes UP stop moving the pointer entirely.
     */
    val isCursorAtTopEdge: Boolean = false,
)

/**
 * What should happen, named rather than performed. The host turns these into
 * calls; the dispatcher never touches a WebView.
 */
sealed interface Command {
    data object ExitFullscreen : Command
    data object CloseChrome : Command
    data object OpenMenu : Command
    data object GoBack : Command
    data object ExitApp : Command
    data object RevealNavBar : Command
    data object ToggleInputMode : Command

    /**
     * Movement is a held state rather than an event, because the cursor
     * accelerates for as long as a direction is held. One command on release
     * would describe a jump, which is not what the pointer does.
     */
    data class StartMove(val key: RemoteKey) : Command
    data class StopMove(val key: RemoteKey) : Command
    data object Activate : Command
}
