/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.input

/**
 * Turns a stream of raw key actions into the phases the dispatcher understands.
 *
 * This exists because key handling cannot live in `Activity.onKeyDown`. A
 * focused WebView consumes every directional key for its own focus walking, so
 * the activity callbacks never run and the browser appears to ignore the
 * remote entirely. Input has to be taken in `dispatchKeyEvent`, above the view
 * hierarchy, and that path provides no long-press callback and no tracking
 * flags, so both are computed here.
 *
 * Pure, with the clock passed in, so the long-press threshold and the
 * suppression rules are testable without a device.
 */
class KeyGestureTracker(private val longPressMillis: Long = DEFAULT_LONG_PRESS_MILLIS) {

    private data class Press(val downMillis: Long, var longPressFired: Boolean)

    private val presses: MutableMap<RemoteKey, Press> = mutableMapOf()

    /**
     * @param repeatCount the platform's auto-repeat counter, used only to tell a
     *   genuine first press from a repeat.
     */
    fun onDown(key: RemoteKey, nowMillis: Long, repeatCount: Int): KeyPhase? {
        val existing: Press? = presses[key]

        if (repeatCount == 0 || existing == null) {
            presses[key] = Press(downMillis = nowMillis, longPressFired = false)
            return KeyPhase.Down
        }

        if (!existing.longPressFired && nowMillis - existing.downMillis >= longPressMillis) {
            existing.longPressFired = true
            return KeyPhase.LongPress
        }

        // Auto-repeat between the first press and the long-press threshold. The
        // cursor accelerates on its own clock, so repeats are dropped rather
        // than restarting movement.
        return null
    }

    /**
     * Returns null when the release must not produce a command: either the long
     * press already consumed it, or no matching press was ever seen.
     *
     * That second case is not hypothetical. A stray key-up arrives during a
     * window transition, and treating it as a real press made the browser exit
     * two seconds after launching, for a button nobody touched.
     */
    fun onUp(key: RemoteKey): KeyPhase? {
        val press: Press = presses.remove(key) ?: return null
        return if (press.longPressFired) null else KeyPhase.Up
    }

    /** A key held while the app leaves the foreground never receives its up. */
    fun clear() = presses.clear()

    fun isPressed(key: RemoteKey): Boolean = presses.containsKey(key)

    companion object {
        /** The platform's own long-press timeout, so holds feel the same everywhere. */
        const val DEFAULT_LONG_PRESS_MILLIS: Long = 500L
    }
}
