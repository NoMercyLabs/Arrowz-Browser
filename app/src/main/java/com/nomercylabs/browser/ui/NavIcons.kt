/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nomercylabs.browser.R

/**
 * The icon set, from the NoMercy design system.
 *
 * These are the designer's Moooom icons, copied unchanged as vector drawables:
 * one 24dp grid, one 1.5 stroke weight, round caps. They are the same glyphs the
 * rest of the ecosystem uses, so this browser reads as part of it rather than as
 * a lookalike drawn by hand.
 *
 * Tinting happens at draw time, so one drawable serves both palettes and the
 * focused and unfocused states.
 */
object NavIcons {

    @Composable
    fun Back(tint: Color) = Glyph(R.drawable.ic_back, tint)

    @Composable
    fun Reload(tint: Color) = Glyph(R.drawable.ic_reload, tint)

    @Composable
    fun Home(tint: Color) = Glyph(R.drawable.ic_home, tint)

    @Composable
    fun Tabs(tint: Color) = Glyph(R.drawable.ic_tabs, tint)

    @Composable
    fun Plus(tint: Color) = Glyph(R.drawable.ic_add, tint)

    @Composable
    fun Close(tint: Color) = Glyph(R.drawable.ic_close, tint)

    /** Filled when the page is kept: at three metres a fill and an outline read
     *  apart far better than two weights of the same outline. */
    @Composable
    fun Star(tint: Color, filled: Boolean) =
        Glyph(if (filled) R.drawable.ic_star_filled else R.drawable.ic_star, tint)

    @Composable
    private fun Glyph(id: Int, tint: Color) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(GLYPH_BOX),
        )
    }

    private val GLYPH_BOX = 24.dp
}
