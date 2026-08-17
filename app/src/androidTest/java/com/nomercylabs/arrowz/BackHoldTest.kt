/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hold a real remote sends, delivered on real hardware.
 *
 * This exists because the defect it covers is invisible to every other tool.
 * `adb shell input keyevent --longpress` sets the framework's long-press flag
 * outright, which takes a path that already worked, and `sendevent` needs root
 * that retail Android TV hardware does not give. So a hold that the remote
 * produces and adb cannot could sit broken behind a green suite and a passing
 * manual check, which is exactly what happened.
 *
 * What a remote actually sends for BACK is one ACTION_DOWN with `repeatCount`
 * zero and no long-press flag, silence for as long as the key is held, then one
 * ACTION_UP. BACK does not auto-repeat and nothing calls `startTracking` on the
 * dispatchKeyEvent path, so both routes the tracker could recognise a hold by
 * are absent. That sequence is what these tests deliver, verbatim.
 */
@RunWith(AndroidJUnit4::class)
class BackHoldTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun holdingBackOpensTheMenu() {
        press(KeyEvent.ACTION_DOWN)
        rule.mainClock.advanceTimeBy(HELD_MILLIS)
        rule.waitForIdle()
        Thread.sleep(HELD_MILLIS)
        rule.waitForIdle()
        press(KeyEvent.ACTION_UP)
        rule.waitForIdle()

        rule.onNodeWithText(menuTitle()).assertIsDisplayed()
    }

    /**
     * A press shorter than the threshold must not reach the menu, or every
     * ordinary BACK opens it.
     *
     * Asserted as the activity finishing rather than as the menu being absent.
     * On the home screen a short BACK is the last rung of the ladder and leaves
     * the browser, so the activity going away is the positive evidence that the
     * press took that rung instead of the hold's.
     */
    @Test
    fun aShortPressLeavesRatherThanOpeningTheMenu() {
        val activity: MainActivity = rule.activity
        press(KeyEvent.ACTION_DOWN)
        press(KeyEvent.ACTION_UP)
        Thread.sleep(HELD_MILLIS)

        assertTrue(
            "A short BACK should have left the browser, not opened the menu.",
            activity.isFinishing || activity.isDestroyed,
        )
    }

    private fun menuTitle(): String =
        rule.activity.getString(R.string.menu_title)

    /**
     * Straight into the activity's own dispatch, which is where all key input is
     * taken. Injecting through the instrumentation would be routed by the
     * platform and would not exercise the path a remote reaches.
     */
    private fun press(action: Int) {
        rule.activity.runOnUiThread {
            rule.activity.dispatchKeyEvent(
                KeyEvent(
                    /* downTime = */ 0L,
                    /* eventTime = */ 0L,
                    action,
                    KeyEvent.KEYCODE_BACK,
                    /* repeat = */ 0,
                ),
            )
        }
        rule.waitForIdle()
    }

    private companion object {
        /** Comfortably past the 500ms threshold, and past it in wall-clock time:
         *  the hold is scheduled on the view's own handler rather than on the
         *  composition clock, so advancing the test clock alone never fires it. */
        const val HELD_MILLIS: Long = 900L
    }
}
