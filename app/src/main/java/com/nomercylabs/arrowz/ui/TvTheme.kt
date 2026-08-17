/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalPalette = staticCompositionLocalOf { Palettes.Dark }

/**
 * Resolves the palette the way a browser should: follow the system unless the
 * user has said otherwise.
 *
 * The window background is themed separately in `themes.xml` and its `-night`
 * variant. Without that, the launcher-to-app transition flashes the wrong
 * colour before the first frame composes.
 */
@Composable
fun TvTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark: Boolean = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val palette: Palette = if (dark) Palettes.Dark else Palettes.Light

    CompositionLocalProvider(LocalPalette provides palette) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface),
        ) {
            content()
        }
    }
}

/**
 * Insets a surface into the area a television will not crop.
 *
 * Applied per surface rather than by the theme, because page content is
 * full-bleed and only the app's own chrome is inset. Insetting everything would
 * letterbox every website inside a border.
 */
fun Modifier.overscan(): Modifier = padding(
    horizontal = Tokens.OverscanHorizontal,
    vertical = Tokens.OverscanVertical,
)
