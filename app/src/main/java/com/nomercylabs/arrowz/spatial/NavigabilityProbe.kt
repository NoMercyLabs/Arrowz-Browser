/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.spatial

/** What the page reported about itself when it finished loading. */
data class Navigability(
    val total: Int,
    val visible: Int,
    val viewportHeight: Int,
    val stealsFocus: Boolean,
    /** A cross-origin frame large enough to be the page: a consent wall. */
    val blockingFrame: Boolean = false,
    /** Focus is inside a frame, so the D-pad has nowhere left to go. */
    val focusInFrame: Boolean = false,
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

        // A frame we cannot see into, covering the page. Focus can reach the
        // frame and cannot go inside it, so every press after that one does
        // nothing; the pointer taps into it the way a finger would.
        if (page.blockingFrame) return false

        // Focus is already inside a frame, so the D-pad is inert right now.
        // Stated here as well as at the call site: the ladder acts on it
        // immediately, and this keeps the rule testable without a device.
        if (page.focusInFrame) return false

        // What is on screen, and nothing else. There was a second gate on the
        // page's total count, and removing the phantom elements from the
        // collector proved it was measuring the phantoms: DuckDuckGo's home page
        // reported 56 with 46 of them a closed off-canvas drawer, cleared that
        // gate comfortably, and then reported an honest 6 and failed it. The
        // page had not changed. The ruler had.
        //
        // Total was there to catch "twelve links spread over ten screens", and
        // visible already says that better — twelve links over ten screens is
        // one or two per screenful. A page with six reachable targets on screen
        // and nothing below the fold is exactly the page focus is for.
        return page.visible >= MINIMUM_VISIBLE
    }

    /**
     * Four on screen: a login form is two fields, a button and a link, and that
     * is the clearest case in the world for walking focus rather than aiming a
     * pointer at it. Below that — a video page with two controls, a map, a
     * canvas — the pointer wins.
     *
     * Was six, against counts inflated by elements nobody could reach. Six
     * honest targets is a busy screen, not a threshold.
     */
    private const val MINIMUM_VISIBLE: Int = 4
}
