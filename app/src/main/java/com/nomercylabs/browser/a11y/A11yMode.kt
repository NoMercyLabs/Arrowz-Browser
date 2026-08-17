/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

import com.nomercylabs.browser.input.InputMode

/**
 * Decides whether a screen reader is driving, and what the input mode becomes
 * when the answer changes.
 *
 * Pure, because it is the rule that stops two focus systems being on screen at
 * once and that is not a rule to discover on a device.
 */
object A11yMode {

    /**
     * Two signals rather than one, because they disagree.
     *
     * Touch exploration is TalkBack's own switch and is the most reliable
     * indicator when it is on. It is also absent on readers that never enable
     * it, which is why the spoken-service count is consulted as well. Either is
     * enough; requiring both would leave us fighting a reader that is plainly
     * running.
     */
    fun isScreenReaderActive(
        accessibilityEnabled: Boolean,
        touchExplorationEnabled: Boolean,
        spokenFeedbackServices: Int,
    ): Boolean {
        if (!accessibilityEnabled) return false
        return touchExplorationEnabled || spokenFeedbackServices > 0
    }

    /**
     * A reader takes the mode whatever it was. Losing one hands the browser back
     * to the pointer rather than leaving it in a mode with nobody driving it —
     * [InputMode.ScreenReader] consumes no directional key, so staying there
     * after the reader stops is a dead remote.
     */
    fun modeFor(screenReaderActive: Boolean, current: InputMode): InputMode = when {
        screenReaderActive -> InputMode.ScreenReader
        current == InputMode.ScreenReader -> InputMode.Cursor
        else -> current
    }

    /** Whether the page may be asked to change mode at all. A remembered
     *  override and a late probe are both conveniences, and neither outranks a
     *  reader that is running right now. */
    fun mayChooseMode(screenReaderActive: Boolean): Boolean = !screenReaderActive
}
