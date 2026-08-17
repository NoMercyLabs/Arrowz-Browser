/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionStylesTest {

    private fun css(
        enabled: Boolean = true,
        captionFontScale: Float = 1f,
        userFontScale: Float = 1f,
        foregroundArgb: Int? = null,
        backgroundArgb: Int? = null,
        edgeType: Int = CaptionStyles.EDGE_NONE,
        edgeArgb: Int? = null,
    ): String = CaptionStyles.css(
        enabled = enabled,
        captionFontScale = captionFontScale,
        userFontScale = userFontScale,
        foregroundArgb = foregroundArgb,
        backgroundArgb = backgroundArgb,
        edgeType = edgeType,
        edgeArgb = edgeArgb,
    )

    @Test
    fun captionsTurnedOffProduceNoRuleAtAll() {
        assertEquals("", css(enabled = false, captionFontScale = 2f, foregroundArgb = WHITE))
    }

    // A default caption style is the page's own, and a colour nobody chose makes
    // captions worse on every site that styled them properly.
    @Test
    fun anUntouchedPreferenceProducesNoRule() {
        assertEquals("", css())
    }

    @Test
    fun aRaisedSizeIsCarriedAsAPercentage() {
        assertEquals("::cue{font-size:150%;}", css(captionFontScale = 1.5f))
    }

    // Both scales are the same stated preference expressed twice, and somebody
    // who raised both meant it.
    @Test
    fun theSystemFontScaleMultipliesTheCaptionScale() {
        assertEquals("::cue{font-size:200%;}", css(captionFontScale = 1.6f, userFontScale = 1.25f))
    }

    // The failure this guards: a caption large enough to cover the picture it is
    // captioning, from two scales that each looked reasonable alone.
    @Test
    fun anExtremeCombinedScaleIsClamped() {
        assertEquals("::cue{font-size:300%;}", css(captionFontScale = 3f, userFontScale = 2f))
        assertEquals("::cue{font-size:50%;}", css(captionFontScale = 0.2f))
    }

    @Test
    fun coloursCarryTheirAlpha() {
        val rule: String = css(foregroundArgb = WHITE, backgroundArgb = HALF_BLACK)
        assertTrue(rule, rule.contains("color:rgba(255,255,255,1.00)"))
        assertTrue(rule, rule.contains("background-color:rgba(0,0,0,0.50)"))
    }

    // Locale.ROOT, or a Dutch television formats the alpha as "0,50" and the
    // whole rule is dropped by the parser.
    @Test
    fun theAlphaIsFormattedWithADecimalPoint() {
        assertFalse(css(foregroundArgb = HALF_BLACK).contains(","+"50"))
        assertTrue(css(foregroundArgb = HALF_BLACK).contains("0.50"))
    }

    @Test
    fun anOutlineEdgeIsDrawnOnAllFourSides() {
        val rule: String = css(edgeType = CaptionStyles.EDGE_OUTLINE, edgeArgb = BLACK)
        assertTrue(rule, rule.contains("text-shadow:-1px -1px 0 rgba(0,0,0,1.00),1px -1px"))
    }

    @Test
    fun aDropShadowEdgeIsDrawnOnOneSide() {
        assertEquals(
            "::cue{text-shadow:2px 2px 2px rgba(0,0,0,1.00);}",
            css(edgeType = CaptionStyles.EDGE_DROP_SHADOW, edgeArgb = BLACK),
        )
    }

    // An edge type with no colour behind it has nothing to draw, and emitting a
    // shadow in a guessed colour is the same mistake as guessing the text colour.
    @Test
    fun anEdgeWithNoColourIsNotDrawn() {
        assertEquals("", css(edgeType = CaptionStyles.EDGE_OUTLINE, edgeArgb = null))
    }

    @Test
    fun anEdgeTypeOfNoneIsNotDrawnEvenWithAColour() {
        assertEquals("", css(edgeType = CaptionStyles.EDGE_NONE, edgeArgb = BLACK))
    }

    private companion object {
        const val WHITE: Int = 0xFFFFFFFF.toInt()
        const val BLACK: Int = 0xFF000000.toInt()
        const val HALF_BLACK: Int = 0x80000000.toInt()
    }
}
