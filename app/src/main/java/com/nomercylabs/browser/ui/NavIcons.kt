/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The icon set, drawn rather than imported.
 *
 * Every glyph shares one 24dp box, one stroke weight and round caps, so the
 * chrome reads as one hand. Drawing them keeps the app free of any icon
 * dependency, which matters for an app whose size and dependency list are part
 * of the point.
 */
object NavIcons {

    @Composable
    fun Back(tint: Color) = Glyph(tint) { stroke, size ->
        val mid: Float = size / 2f
        drawLine(tint, Offset(size * 0.78f, mid), Offset(size * 0.22f, mid), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size * 0.22f, mid), Offset(size * 0.46f, mid - size * 0.24f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size * 0.22f, mid), Offset(size * 0.46f, mid + size * 0.24f), stroke, StrokeCap.Round)
    }

    @Composable
    fun Reload(tint: Color) = Glyph(tint) { stroke, size ->
        // An open arc rather than a closed circle, so it reads as motion at
        // three metres rather than as a dot.
        drawArc(
            color = tint,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(size * 0.18f, size * 0.18f),
            size = androidx.compose.ui.geometry.Size(size * 0.64f, size * 0.64f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val head = Path().apply {
            moveTo(size * 0.80f, size * 0.30f)
            lineTo(size * 0.86f, size * 0.10f)
            lineTo(size * 0.62f, size * 0.16f)
            close()
        }
        drawPath(head, tint)
    }

    @Composable
    fun Home(tint: Color) = Glyph(tint) { stroke, size ->
        val roof = Path().apply {
            moveTo(size * 0.15f, size * 0.48f)
            lineTo(size * 0.5f, size * 0.16f)
            lineTo(size * 0.85f, size * 0.48f)
        }
        drawPath(roof, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawRect(
            color = tint,
            topLeft = Offset(size * 0.26f, size * 0.46f),
            size = androidx.compose.ui.geometry.Size(size * 0.48f, size * 0.38f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    /** Two sheets, offset. The count rides beside it rather than inside it: a
     *  number drawn into a 24dp box is unreadable across a room. */
    @Composable
    fun Tabs(tint: Color) = Glyph(tint) { stroke, size ->
        drawRect(
            color = tint,
            topLeft = Offset(size * 0.30f, size * 0.16f),
            size = androidx.compose.ui.geometry.Size(size * 0.54f, size * 0.48f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawRect(
            color = tint,
            topLeft = Offset(size * 0.16f, size * 0.36f),
            size = androidx.compose.ui.geometry.Size(size * 0.54f, size * 0.48f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    @Composable
    fun Plus(tint: Color) = Glyph(tint) { stroke, size ->
        val mid: Float = size / 2f
        drawLine(tint, Offset(size * 0.20f, mid), Offset(size * 0.80f, mid), stroke, StrokeCap.Round)
        drawLine(tint, Offset(mid, size * 0.20f), Offset(mid, size * 0.80f), stroke, StrokeCap.Round)
    }

    @Composable
    fun Close(tint: Color) = Glyph(tint) { stroke, size ->
        drawLine(tint, Offset(size * 0.24f, size * 0.24f), Offset(size * 0.76f, size * 0.76f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size * 0.76f, size * 0.24f), Offset(size * 0.24f, size * 0.76f), stroke, StrokeCap.Round)
    }

    @Composable
    private fun Glyph(
        tint: Color,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.(stroke: Float, size: Float) -> Unit,
    ) {
        Canvas(modifier = Modifier.size(GLYPH_BOX)) {
            val box: Float = this.size.minDimension
            val stroke: Float = box * STROKE_RATIO
            draw(stroke, box)
        }
    }

    private val GLYPH_BOX = 24.dp

    /** One weight across the set; a mixed-weight icon row reads as an accident. */
    private const val STROKE_RATIO: Float = 0.085f
}
