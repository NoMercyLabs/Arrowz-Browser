/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.a11y

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {

    @Test
    fun anUnchangedSettingLeavesThePageAtItsOwnSize() {
        assertEquals(100, TextScale.zoomPercent(1f))
    }

    @Test
    fun aRaisedSettingIsCarriedIntoThePage() {
        assertEquals(130, TextScale.zoomPercent(1.3f))
    }

    // A television's accessibility settings reach scales a web layout was never
    // built for, and a site rendered at three words per line is less readable
    // than the same site at its own size.
    @Test
    fun theExtremesAreClamped() {
        assertEquals(200, TextScale.zoomPercent(3f))
        assertEquals(75, TextScale.zoomPercent(0.5f))
    }
}
