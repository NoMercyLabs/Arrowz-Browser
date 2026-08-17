/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.tabs

/**
 * What eviction needs to know about a tab. Deliberately not the tab itself, so
 * the decision is testable without a WebView, a renderer or a device.
 */
data class TabSnapshot(
    val id: String,
    val lastUsedMillis: Long,
    /** Holding a media session. Audio stopping because a newer tab opened is
     *  indistinguishable from a crash. */
    val isPlayingMedia: Boolean = false,
    /** Has typed input that `WebView.saveState` will not reliably carry. */
    val hasDirtyForm: Boolean = false,
    /** The one the user is looking at. */
    val isForeground: Boolean = false,
    /** Already evicted; nothing to reclaim. */
    val isSuspended: Boolean = false,
)

/**
 * Chooses which tabs to release when memory gets tight.
 *
 * Pure, because the interesting part is the ordering and the exemptions, and
 * that is impossible to observe reliably on a device: memory pressure arrives
 * when the system decides, not when a test asks.
 */
object TabPressureManager {

    /**
     * Returns the tabs to evict, least recently used first, honouring the
     * exemptions. Empty when nothing may be released, which is a legitimate
     * outcome rather than a failure: the alternative is breaking a promise to
     * reclaim memory the app does not own anyway.
     */
    fun evictionOrder(tabs: List<TabSnapshot>, needed: Int): List<TabSnapshot> {
        if (needed <= 0) return emptyList()

        return tabs
            .filter { tab -> isEvictable(tab) }
            .sortedBy { tab -> tab.lastUsedMillis }
            .take(needed)
    }

    fun isEvictable(tab: TabSnapshot): Boolean =
        !tab.isForeground &&
            !tab.isSuspended &&
            !tab.isPlayingMedia &&
            !tab.hasDirtyForm

    /**
     * How many tabs to release for a given trim level.
     *
     * The levels are the system's own vocabulary for how much trouble it is in,
     * and treating them all the same either releases too little to help or
     * throws away tabs the user still wanted.
     */
    fun releasesFor(trimLevel: Int): Int = when {
        // Not a severity at all: it says the UI is no longer visible, and it
        // arrives every single time somebody presses HOME. Its numeric value
        // sits between two of the real levels, so an ordered table reads it as
        // pressure and throws away tabs on the way out of the app. Measured on
        // the 8000: pressing HOME released a tab with memory to spare.
        trimLevel == TRIM_UI_HIDDEN -> 0

        trimLevel >= TRIM_COMPLETE -> Int.MAX_VALUE
        trimLevel >= TRIM_MODERATE -> 2
        trimLevel >= TRIM_BACKGROUND -> 1
        trimLevel >= TRIM_RUNNING_CRITICAL -> 2
        trimLevel >= TRIM_RUNNING_LOW -> 1
        else -> 0
    }

    // ComponentCallbacks2 constants, named here so the pure module needs no
    // Android import and the table above reads as the policy it is.
    const val TRIM_RUNNING_LOW: Int = 10
    const val TRIM_RUNNING_CRITICAL: Int = 15
    const val TRIM_UI_HIDDEN: Int = 20
    const val TRIM_BACKGROUND: Int = 40
    const val TRIM_MODERATE: Int = 60
    const val TRIM_COMPLETE: Int = 80
}
