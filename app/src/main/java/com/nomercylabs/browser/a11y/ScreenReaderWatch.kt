/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Watches the system for a screen reader arriving or leaving.
 *
 * Both listeners are needed. Enabling TalkBack fires the accessibility-state
 * one; turning its exploration on and off inside a running session fires only
 * the touch-exploration one, and a browser that noticed the first but not the
 * second would keep a pointer on screen underneath a reader.
 */
class ScreenReaderWatch(
    private val context: Context,
    private val onChanged: (active: Boolean) -> Unit,
) {

    private val manager: AccessibilityManager? =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    private val stateListener = AccessibilityManager.AccessibilityStateChangeListener { report() }
    private val explorationListener =
        AccessibilityManager.TouchExplorationStateChangeListener { report() }

    val isActive: Boolean
        get() {
            val live: AccessibilityManager = manager ?: return false
            return A11yMode.isScreenReaderActive(
                accessibilityEnabled = live.isEnabled,
                touchExplorationEnabled = live.isTouchExplorationEnabled,
                spokenFeedbackServices = live
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
                    .size,
            )
        }

    fun start() {
        manager?.addAccessibilityStateChangeListener(stateListener)
        manager?.addTouchExplorationStateChangeListener(explorationListener)
        report()
    }

    fun stop() {
        manager?.removeAccessibilityStateChangeListener(stateListener)
        manager?.removeTouchExplorationStateChangeListener(explorationListener)
    }

    private fun report() = onChanged(isActive)
}
