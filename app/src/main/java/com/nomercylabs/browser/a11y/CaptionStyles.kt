/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

import java.util.Locale

/**
 * Carries the television's caption preferences into web video.
 *
 * A `<track>` is styled by the page, and the page has never heard of Android, so
 * somebody who set captions to large yellow-on-black system-wide gets whatever
 * the site felt like on every site. This is the one place those two worlds meet:
 * a `::cue` rule built from the system's own values.
 */
object CaptionStyles {

    /** `CaptioningManager.CaptionStyle` edge types, named rather than repeated
     *  as bare integers at the point of use. */
    const val EDGE_NONE: Int = 0
    const val EDGE_OUTLINE: Int = 1
    const val EDGE_DROP_SHADOW: Int = 2
    const val EDGE_RAISED: Int = 3
    const val EDGE_DEPRESSED: Int = 4

    /**
     * Null colours mean the user set no preference, and they stay unset rather
     * than being filled with a default. A site that styled its captions well
     * already beats a colour nobody chose.
     */
    fun css(
        enabled: Boolean,
        captionFontScale: Float,
        userFontScale: Float,
        foregroundArgb: Int?,
        backgroundArgb: Int?,
        edgeType: Int,
        edgeArgb: Int?,
    ): String {
        if (!enabled) return ""

        val declarations: MutableList<String> = mutableListOf()

        // Both scales are the same stated preference expressed twice, and
        // somebody who raised both meant it.
        val scale: Int = scalePercent(captionFontScale * userFontScale)
        if (scale != NO_CHANGE_PERCENT) declarations += "font-size:$scale%"

        if (foregroundArgb != null) declarations += "color:${rgba(foregroundArgb)}"
        if (backgroundArgb != null) declarations += "background-color:${rgba(backgroundArgb)}"

        edgeShadow(edgeType, edgeArgb)?.let { shadow -> declarations += "text-shadow:$shadow" }

        if (declarations.isEmpty()) return ""
        return "::cue{" + declarations.joinToString(";") + ";}"
    }

    /**
     * Clamped at both ends. A system scale set to its maximum renders a caption
     * that covers the picture it is captioning, and one set very low is
     * unreadable at three metres, which is the distance this whole app assumes.
     */
    fun scalePercent(combined: Float): Int =
        (combined * 100f).toInt().coerceIn(MIN_PERCENT, MAX_PERCENT)

    /**
     * Raised and depressed are drawn as shadows rather than as the bevels their
     * names suggest: CSS has no bevel, and a shadow on the matching side is what
     * the platform's own renderer produces for them.
     */
    private fun edgeShadow(edgeType: Int, edgeArgb: Int?): String? {
        val colour: String = rgba(edgeArgb ?: return null)
        return when (edgeType) {
            EDGE_OUTLINE ->
                "-1px -1px 0 $colour,1px -1px 0 $colour,-1px 1px 0 $colour,1px 1px 0 $colour"
            EDGE_DROP_SHADOW -> "2px 2px 2px $colour"
            EDGE_RAISED -> "1px 1px 0 $colour"
            EDGE_DEPRESSED -> "-1px -1px 0 $colour"
            else -> null
        }
    }

    /** Alpha carried through, because a caption background the user set to
     *  semi-transparent is a deliberate choice about seeing the picture. */
    private fun rgba(argb: Int): String {
        val alpha: Float = ((argb shr 24) and 0xFF) / 255f
        val red: Int = (argb shr 16) and 0xFF
        val green: Int = (argb shr 8) and 0xFF
        val blue: Int = argb and 0xFF
        // Locale.ROOT, or a Dutch television formats the alpha as "0,50" and the
        // whole rule is dropped by the parser as a syntax error.
        return "rgba($red,$green,$blue,${String.format(Locale.ROOT, "%.2f", alpha)})"
    }

    private const val NO_CHANGE_PERCENT: Int = 100
    private const val MIN_PERCENT: Int = 50
    private const val MAX_PERCENT: Int = 300
}
