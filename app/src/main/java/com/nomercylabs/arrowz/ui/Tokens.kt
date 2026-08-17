/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.ui

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
    /**
     * What a row showing the setting the app is currently on is filled with.
     *
     * This was the accent at 35% over whatever sat behind it, which is a lift on
     * a dark ground and a wash on a light one. In light mode it composited to
     * `#C9A1AE`: darker than the unselected rows around it, and 3.12:1 under the
     * subtitle, so the word "On" was both unreadable and dressed as "off".
     *
     * A per-mode value instead. Selection reads as lit rather than dimmed, and
     * the accent bar beside it carries the signal for anyone the tint does not
     * reach.
     */
    val surfaceSelected: Color,
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
     * Arrowz, taken from the mark rather than derived alongside it.
     *
     * `#FF0055`, `#FE2970`, `#990033`, `#1F000A` and `#090104` are the five
     * colours in `docs/store/tv-logo.svg` and `store-logo.svg`. Everything below
     * is one of those five or a measured step between two of them, so the app
     * and the icon are the same object.
     *
     * This overturns a decision taken from hardware: the accent was deliberately
     * desaturated after a violet and then a cyan both "arrived as a flare rather
     * than as emphasis" at three metres. That finding was about brightness at a
     * distance and it has not been repealed — it is now a thing to check on the
     * television rather than a reason to dilute the brand, because the mark is
     * the designer's call and legibility is measurable.
     */
    val Dark = Palette(
        surface = Color(0xFF090104),
        surfaceRaised = Color(0xFF1F000A),
        // The one value with no counterpart in the mark. Two near-blacks give
        // two levels and the interface needs three, so this is a step further
        // along the same line rather than a new colour.
        surfaceOffered = Color(0xFF2E0512),
        surfaceSelected = Color(0xFF3B0114),
        outline = Color(0x1FFFFFFF),
        onSurface = Color(0xFFF7F2F4),
        onSurfaceMuted = Color(0xFFB9A8AE),
        accent = Color(0xFFFF0055),
        accentDeep = Color(0xFF990033),
        /**
         * Amber, because the brand accent is already red.
         *
         * Danger is conventionally red and here red means "this is where focus
         * is", so a red warning would be the same signal as emphasis and would
         * read as a highlight rather than a problem. Nothing else on screen is
         * warm-yellow, so it stays distinguishable at a distance and to a viewer
         * who cannot separate red from green.
         */
        danger = Color(0xFFFFB020),
        focusRing = Color(0xFFFF0055),
        // Measured: the mark's own near-black on the accent reaches 5.07, where
        // white manages 3.90. The darker choice is the legible one here, which
        // is the opposite of what a red fill usually wants.
        onAccent = Color(0xFF1F000A),
        isLight = false,
    )

    /**
     * The same five colours, re-ranked for a bright ground.
     *
     * `#FF0055` fails against light surfaces — 2.55 against the offered step,
     * where a focus indicator needs three — so light mode takes the mark's own
     * `#990033`, which reaches 5.71 on the same surface. The deep shade was
     * already in the palette, so the ring stays a brand colour rather than a
     * darker red invented to pass a check.
     */
    val Light = Palette(
        // Inverted against the obvious arrangement: the page is the bright
        // surface and controls sit a step darker on it. White cards on a grey
        // page read as paper on a desk, which is a document, not an interface;
        // this reads as controls on a screen. The neutrals carry a red bias so
        // they belong to the accent rather than sitting under it.
        surface = Color(0xFFFAF7F8),
        surfaceRaised = Color(0xFFEFE6E9),
        surfaceOffered = Color(0xFFDCCDD3),
        surfaceSelected = Color(0xFFF9DCE5),
        outline = Color(0x1F000000),
        onSurface = Color(0xFF1F000A),
        onSurfaceMuted = Color(0xFF6B5158),
        accent = Color(0xFF990033),
        accentDeep = Color(0xFF6E0025),
        // Amber for the same reason, darkened until it survives a bright ground.
        danger = Color(0xFF8A4B00),
        focusRing = Color(0xFF990033),
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
