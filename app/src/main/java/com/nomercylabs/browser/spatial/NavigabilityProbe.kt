/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

/** What the page reported about itself when it finished loading. */
data class Navigability(
    val total: Int,
    val visible: Int,
    val viewportHeight: Int,
    val stealsFocus: Boolean,
)

/**
 * Which input mode a page starts in.
 *
 * Choosing this automatically is the difference between a browser that works
 * and one that asks the viewer to know things. A page dense with links wants
 * focus; a canvas, a map or an article with four links in a screenful wants the
 * pointer, and forcing focus on it means most presses do nothing.
 *
 * Pure, so the thresholds are a test rather than a feeling. The viewer can
 * always override with a long press on OK, and the override is remembered per
 * site.
 */
object NavigabilityProbe {

    fun prefersFocusMode(page: Navigability): Boolean {
        // A page that moved focus itself on load will keep doing it, and two
        // systems moving focus is worse than either one alone.
        if (page.stealsFocus) return false

        if (page.visible < MINIMUM_VISIBLE) return false

        // Density, not raw count: twelve links spread over ten screens is a
        // long article, and a press that scrolls a screenful to reach the next
        // one is not navigation.
        return page.total >= MINIMUM_TOTAL
    }

    /** Six on screen is roughly a navigation bar plus a couple of cards, which
     *  is where walking focus starts to beat aiming a pointer. */
    private const val MINIMUM_VISIBLE: Int = 6
    private const val MINIMUM_TOTAL: Int = 8
}
