/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.tabs

/**
 * The second pressure signal, and the one that still exists everywhere.
 *
 * `onTrimMemory`'s levels below UI_HIDDEN are deprecated and newer Android
 * versions stop dispatching them, so a browser watching only that callback
 * evicts nothing on the devices most likely to need it. `MemoryInfo` is read
 * when a tab is opened or activated, which is the moment the answer changes.
 */
object MemoryPressure {

    /**
     * How many tabs to release, given what `ActivityManager.MemoryInfo` reports.
     *
     * [threshold] is the system's own figure for when it begins killing
     * background processes. Being within [HEADROOM_FACTOR] of it means the next
     * page is likely to be paid for with somebody else's process, or with one of
     * our renderers.
     */
    fun releasesFor(availableBytes: Long, thresholdBytes: Long, isLowMemory: Boolean): Int = when {
        isLowMemory -> 2
        thresholdBytes <= 0L -> 0
        availableBytes < thresholdBytes * HEADROOM_FACTOR -> 1
        else -> 0
    }

    private const val HEADROOM_FACTOR: Long = 2
}
