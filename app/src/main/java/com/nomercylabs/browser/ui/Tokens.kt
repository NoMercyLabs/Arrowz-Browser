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
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val accentDeep: Color,
    val danger: Color,
    val focusRing: Color,
    val isLight: Boolean,
)

object Palettes {

    val Dark = Palette(
        surface = Color(0xFF0A0A0C),
        surfaceRaised = Color(0xFF141419),
        onSurface = Color(0xFFF2F2F5),
        onSurfaceMuted = Color(0xFF9A9AA6),
        accent = Color(0xFFA78BFA),
        accentDeep = Color(0xFF7C3AED),
        danger = Color(0xFFF87171),
        focusRing = Color(0xFFA78BFA),
        isLight = false,
    )

    // The focus ring darkens in light mode: the pale violet that reads clearly
    // against near-black disappears against white at three metres.
    val Light = Palette(
        surface = Color(0xFFFAFAFC),
        surfaceRaised = Color(0xFFFFFFFF),
        onSurface = Color(0xFF14141A),
        onSurfaceMuted = Color(0xFF5B5B66),
        accent = Color(0xFF6D28D9),
        accentDeep = Color(0xFF4C1D95),
        danger = Color(0xFFB91C1C),
        focusRing = Color(0xFF6D28D9),
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

    val RadiusSm: Dp = 6.dp
    val RadiusMd: Dp = 12.dp

    val TextBody = 18.sp
    val TextTitle = 28.sp

    /**
     * The focus ring geometry is defined here exactly once. Slice 14 injects
     * these same values into web content as CSS, taking the colour from the
     * active [Palette], so a second definition anywhere makes native and web
     * focus drift apart.
     */
    object Focus {
        val RingWidth: Dp = 3.dp
        val RingGap: Dp = 2.dp
        val Scale: Float = 1.06f
        const val TransitionMillis: Int = 150
    }
}
