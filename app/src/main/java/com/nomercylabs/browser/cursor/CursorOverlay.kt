/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.cursor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nomercylabs.browser.ui.LocalPalette

/**
 * Draws the pointer above the page.
 *
 * A filled dot with a contrasting ring rather than an arrow: at three metres an
 * arrow's point is the part that matters and the part that disappears, while a
 * ring survives both a white page and a black video frame. The ring is what
 * makes it visible on any background, so it is not decoration.
 */
@Composable
fun CursorOverlay(position: CursorPosition, visible: Boolean) {
    if (!visible) return

    val accent: Color = LocalPalette.current.accent

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centre = Offset(position.x, position.y)

        // Outer contrast ring first, so it sits behind the fill and reads as an
        // outline on light and dark alike.
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = OUTER_RADIUS_DP.dp.toPx(),
            center = centre,
            style = Stroke(width = OUTLINE_WIDTH_DP.dp.toPx()),
        )
        drawCircle(
            color = Color.White,
            radius = INNER_RADIUS_DP.dp.toPx(),
            center = centre,
            style = Stroke(width = OUTLINE_WIDTH_DP.dp.toPx()),
        )
        drawCircle(
            color = accent,
            radius = FILL_RADIUS_DP.dp.toPx(),
            center = centre,
        )
    }
}

private const val OUTER_RADIUS_DP: Float = 11f
private const val INNER_RADIUS_DP: Float = 9f
private const val FILL_RADIUS_DP: Float = 6f
private const val OUTLINE_WIDTH_DP: Float = 2f
