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
    /**
     * A step further from the page than [surfaceRaised], for something offered
     * on top of a control rather than sitting beside it.
     *
     * The address bar's suggestions were drawn on the same value as the field
     * above them and read as more chrome rather than as an answer to what was
     * typed. A surface that is proposing something has to look different from
     * the one that asked.
     */
    val surfaceOffered: Color,
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

    /**
     * Slate, not neutral grey, and a cool accent that belongs to it.
     *
     * The violet came from the NoMercy media ecosystem, where it sits on
     * near-black. On slate it reads as a second, unrelated colour fighting the
     * surfaces around it. Everything here is drawn from one cool family, so the
     * accent looks like the brightest part of the same room rather than a
     * sticker applied to it.
     */
    val Dark = Palette(
        surface = Color(0xFF0B0F14),
        surfaceRaised = Color(0xFF151C24),
        surfaceOffered = Color(0xFF1F2933),
        outline = Color(0x1FFFFFFF),
        onSurface = Color(0xFFE8EEF4),
        onSurfaceMuted = Color(0xFF8B9AA8),
        accent = Color(0xFF38BDF8),
        accentDeep = Color(0xFF0369A1),
        danger = Color(0xFFF87171),
        focusRing = Color(0xFF38BDF8),
        onAccent = Color(0xFF06121C),
        isLight = false,
    )

    // The focus ring darkens in light mode: the pale violet that reads clearly
    // against near-black disappears against white at three metres.
    val Light = Palette(
        // Inverted against the obvious arrangement: the page is the bright
        // surface and controls sit a step darker on it. White cards on a grey
        // page read as paper on a desk, which is a document, not an interface;
        // this reads as controls on a screen.
        surface = Color(0xFFF4F6F8),
        surfaceRaised = Color(0xFFDCE3EA),
        surfaceOffered = Color(0xFFC6D1DC),
        outline = Color(0x1F000000),
        onSurface = Color(0xFF0F1720),
        onSurfaceMuted = Color(0xFF52616F),
        accent = Color(0xFF0369A1),
        accentDeep = Color(0xFF075985),
        danger = Color(0xFFB91C1C),
        focusRing = Color(0xFF0369A1),
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

    /** For a tile's initials, which are the only thing identifying a site
     *  before a favicon exists, and have a whole tile to fill. */
    val TextDisplay = 44.sp

    /**
     * The focus ring geometry is defined here exactly once. Slice 14 injects
     * these same values into web content as CSS, taking the colour from the
     * active [Palette], so a second definition anywhere makes native and web
     * focus drift apart.
     */
    object Focus {
        /** Thicker than it looks right at arm's length. At three metres a 2dp
         *  ring is a hairline, and focus is the only thing telling the viewer
         *  where they are. */
        val RingWidth: Dp = 3.dp

        /** A focused surface is genuinely lifted, not just outlined. This is
         *  what a coloured tile uses instead of a fill it cannot afford. */
        val Elevation: Dp = 12.dp
        val RingGap: Dp = 2.dp
        val Scale: Float = 1.06f
        const val TransitionMillis: Int = 150
    }
}
