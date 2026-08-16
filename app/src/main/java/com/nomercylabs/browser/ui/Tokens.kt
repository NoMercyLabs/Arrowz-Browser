/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How the browser decides which palette to draw. [System] is the default and is
 * what a real browser does; the other two exist because a user watching in a
 * dark room does not want the system's daytime choice imposed on them.
 */
enum class ThemeMode { System, Light, Dark }

/**
 * Colours that change with the theme. Dimensions do not, so they live in
 * [Tokens] rather than here.
 */
data class Palette(
    val surface: Color,
    val surfaceRaised: Color,
    /**
     * A hairline around every raised surface.
     *
     * In light mode the raised colour is white on an almost-white page, and a
     * shadow alone disappears on a television whose contrast is turned up. The
     * line is what actually separates a control from the screen behind it.
     */
    val outline: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val accentDeep: Color,
    val danger: Color,
    val focusRing: Color,
    /** Text and glyphs drawn on top of [accent], which a focused control fills
     *  with. Contrast is the whole job; nothing subtle belongs here. */
    val onAccent: Color,
    val isLight: Boolean,
)

object Palettes {

    val Dark = Palette(
        surface = Color(0xFF0A0A0C),
        surfaceRaised = Color(0xFF16161C),
        outline = Color(0x1FFFFFFF),
        onSurface = Color(0xFFF2F2F5),
        onSurfaceMuted = Color(0xFF9A9AA6),
        accent = Color(0xFFA78BFA),
        accentDeep = Color(0xFF7C3AED),
        danger = Color(0xFFF87171),
        focusRing = Color(0xFFA78BFA),
        onAccent = Color(0xFF0A0A0C),
        isLight = false,
    )

    // The focus ring darkens in light mode: the pale violet that reads clearly
    // against near-black disappears against white at three metres.
    val Light = Palette(
        // Inverted against the obvious arrangement: the page is the bright
        // surface and controls sit a step darker on it. White cards on a grey
        // page read as paper on a desk, which is a document, not an interface;
        // this reads as controls on a screen.
        surface = Color(0xFFF7F7FA),
        surfaceRaised = Color(0xFFE2E2EA),
        outline = Color(0x1F000000),
        onSurface = Color(0xFF14141A),
        onSurfaceMuted = Color(0xFF5B5B66),
        accent = Color(0xFF6D28D9),
        accentDeep = Color(0xFF4C1D95),
        danger = Color(0xFFB91C1C),
        focusRing = Color(0xFF6D28D9),
        onAccent = Color(0xFFFFFFFF),
        isLight = true,
    )
}

object Tokens {

    // Overscan is not decoration. TV panels crop the outer edge, so the safe
    // area is inset before anything is laid out.
    val OverscanHorizontal: Dp = 48.dp
    val OverscanVertical: Dp = 27.dp

    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 16.dp
    val SpaceLg: Dp = 24.dp
    val SpaceXl: Dp = 40.dp

    /**
     * One radius for every chrome surface: buttons, the address field, list
     * rows, tiles. Two radii in one row is the kind of mismatch that reads as
     * carelessness long before anyone can name it.
     */
    val Radius: Dp = 10.dp

    /** Lifts a control off the page. Kept small: a television is viewed flat and
     *  a deep shadow reads as blur rather than as height. */
    val Elevation: Dp = 3.dp
    val Hairline: Dp = 1.dp

    // Two sizes and a quiet one for secondary lines. Anything below this is
    // unreadable from a sofa, so there is no smaller step to reach for.
    val TextSmall = 14.sp
    val TextBody = 18.sp
    val TextTitle = 28.sp

    /**
     * The focus ring geometry is defined here exactly once. Slice 14 injects
     * these same values into web content as CSS, taking the colour from the
     * active [Palette], so a second definition anywhere makes native and web
     * focus drift apart.
     */
    object Focus {
        val RingWidth: Dp = 2.dp
        val RingGap: Dp = 2.dp
        val Scale: Float = 1.06f
        const val TransitionMillis: Int = 150
    }
}
