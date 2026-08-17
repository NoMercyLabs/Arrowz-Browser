/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

/**
 * Carries the system font scale into web content.
 *
 * The chrome needs nothing: its sizes are declared in `sp` and Compose applies
 * the scale by construction. A page has never seen that setting, so the same
 * intent has to be handed to WebView separately.
 *
 * Deliberately text zoom rather than page zoom. Page zoom scales images and
 * layout along with the text and produces horizontal scrolling, which is the one
 * axis a D-pad handles worst.
 */
object TextScale {

    /**
     * Clamped at both ends. A television's accessibility settings reach scales a
     * web layout was never built for, and a site rendered at three words per
     * line is less readable than the same site at its own size.
     */
    fun zoomPercent(fontScale: Float): Int =
        (fontScale * 100f).toInt().coerceIn(MIN_PERCENT, MAX_PERCENT)

    private const val MIN_PERCENT: Int = 75
    private const val MAX_PERCENT: Int = 200
}
