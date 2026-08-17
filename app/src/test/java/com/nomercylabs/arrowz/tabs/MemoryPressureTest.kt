/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryPressureTest {

    private val threshold: Long = 200L * 1024 * 1024

    @Test
    fun plentyOfHeadroomReleasesNothing() {
        assertEquals(0, MemoryPressure.releasesFor(threshold * 6, threshold, isLowMemory = false))
    }

    @Test
    fun approachingTheSystemThresholdReleasesOne() {
        assertEquals(1, MemoryPressure.releasesFor(threshold + 1, threshold, isLowMemory = false))
    }

    // The system saying it is in trouble outranks the arithmetic.
    @Test
    fun lowMemoryReleasesTwoEvenWithHeadroomReported() {
        assertEquals(2, MemoryPressure.releasesFor(threshold * 6, threshold, isLowMemory = true))
    }

    // A device reporting no threshold gives us nothing to compare against, and
    // guessing there would evict tabs on a machine that never asked.
    @Test
    fun aMissingThresholdReleasesNothing() {
        assertEquals(0, MemoryPressure.releasesFor(1L, 0L, isLowMemory = false))
    }
}
