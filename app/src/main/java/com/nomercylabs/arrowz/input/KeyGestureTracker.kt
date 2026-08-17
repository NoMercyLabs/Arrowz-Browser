/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.input

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
    fun onDown(key: RemoteKey, nowMillis: Long, repeatCount: Int, isLongPress: Boolean = false): KeyPhase? {
        val existing: Press? = presses[key]

        // The framework sets its own long-press flag on the repeat, at the same
        // threshold every other app uses. When it says so, that is the answer:
        // the timer below is for the injected and synthetic events that carry no
        // flag, not a second opinion about what a hold is.
        if (isLongPress) {
            val press: Press = existing ?: Press(downMillis = nowMillis, longPressFired = false)
            if (press.longPressFired) return null
            press.longPressFired = true
            presses[key] = press
            return KeyPhase.LongPress
        }

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
     * What a release means. The two silent cases are not the same thing and
     * cannot be collapsed into null:
     *
     * - [Release.Swallowed] follows a long press we already acted on. The app
     *   must eat it. Handing it on let the system see a plain BACK and close the
     *   browser, so holding BACK opened the menu and then quit.
     * - [Release.Unknown] is a key-up with no press behind it, which arrives
     *   during window transitions. Treating that as a real press made the
     *   browser exit two seconds after launching, for a button nobody touched.
     */
    sealed interface Release {
        data object Swallowed : Release
        data object Unknown : Release
        data class Acted(val phase: KeyPhase) : Release
    }

    fun onUp(key: RemoteKey): Release {
        val press: Press = presses.remove(key) ?: return Release.Unknown
        return if (press.longPressFired) Release.Swallowed else Release.Acted(KeyPhase.Up)
    }

    /**
     * The hold, recognised from the press itself rather than from traffic.
     *
     * [onDown] can only notice a hold two ways: the platform sets its long-press
     * flag, or it sends auto-repeats the timer above can measure. BACK gives
     * neither on every remote — the flag is only set for a key someone called
     * `startTracking` on, which the dispatchKeyEvent path never sees, and BACK
     * does not auto-repeat. One ACTION_DOWN arrives and then nothing until the
     * release, so both routes sit waiting for events that never come and a hold
     * is delivered as an ordinary press.
     *
     * A caller schedules this at the threshold instead. Still pure: whether the
     * key is still down is answered from state already here.
     */
    fun onHoldElapsed(key: RemoteKey): KeyPhase? {
        val press: Press = presses[key] ?: return null
        if (press.longPressFired) return null
        press.longPressFired = true
        return KeyPhase.LongPress
    }

    /** A key held while the app leaves the foreground never receives its up. */
    fun clear() = presses.clear()

    fun isPressed(key: RemoteKey): Boolean = presses.containsKey(key)

    companion object {
        /** The platform's own long-press timeout, so holds feel the same everywhere. */
        const val DEFAULT_LONG_PRESS_MILLIS: Long = 500L
    }
}
