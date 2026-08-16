/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.cursor

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * Turns a pointer position into the same input a finger produces.
 *
 * Dispatching a real touch is what makes this work on every site without any
 * cooperation from the page: whatever tap handling the site already has applies
 * unchanged, whether it was written for a phone in 2014 or a framework from
 * this year.
 */
object TouchSynthesizer {

    /**
     * A touch of zero duration is discarded by some frameworks as a stray
     * event, and one that moves reads as a swipe. Down and up therefore share
     * coordinates and are separated by a plausible interval.
     */
    private const val TAP_DURATION_MILLIS: Long = 60L

    fun tap(target: View, position: CursorPosition) {
        val downTime: Long = SystemClock.uptimeMillis()

        dispatch(target, MotionEvent.ACTION_DOWN, downTime, downTime, position)
        dispatch(target, MotionEvent.ACTION_UP, downTime, downTime + TAP_DURATION_MILLIS, position)
    }

    private fun dispatch(
        target: View,
        action: Int,
        downTime: Long,
        eventTime: Long,
        position: CursorPosition,
    ) {
        val event: MotionEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            position.x,
            position.y,
            0,
        )
        // A touchscreen source, not a mouse: sites branch on it, and the touch
        // path is the one every page has been built and tested against.
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try {
            target.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
