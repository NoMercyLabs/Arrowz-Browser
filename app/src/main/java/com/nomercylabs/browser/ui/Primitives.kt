/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Focus is the entire visual language on a television. There is no hover and no
 * pointer state for our own chrome, so this modifier is what tells the viewer
 * where they are, and it goes on every focusable surface without exception.
 *
 * Values come from [Tokens.Focus] and the active palette, so the ring drawn here
 * and the one injected into web content in slice 14 cannot drift apart.
 */
@Composable
fun Modifier.tvFocusable(
    focused: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(Tokens.RadiusMd),
): Modifier {
    val palette: Palette = LocalPalette.current
    val scale: Float by animateFloatAsState(
        targetValue = if (focused) Tokens.Focus.Scale else 1f,
        animationSpec = tween(Tokens.Focus.TransitionMillis),
        label = "focusScale",
    )
    return this
        .scale(scale)
        .border(
            width = if (focused) Tokens.Focus.RingWidth else 0.dp,
            color = if (focused) palette.focusRing else Color.Transparent,
            shape = shape,
        )
}

/**
 * A square button carrying a drawn glyph.
 *
 * The glyph is a composable rather than an icon resource so the app keeps no
 * third-party icon dependency, which is the same reason the whole icon set is
 * authored in [NavIcons].
 */
@Composable
fun IconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable (tint: Color) -> Unit,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val description: String = contentDescription

    Box(
        modifier = modifier
            .size(BUTTON_SIZE)
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .tvFocusable(focused, RoundedCornerShape(Tokens.RadiusSm))
            .background(palette.surfaceRaised, RoundedCornerShape(Tokens.RadiusSm))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        glyph(if (focused) palette.accent else palette.onSurface)
    }
}

/**
 * A single-line field for addresses and queries.
 *
 * [requestInitialFocus] exists because a bar that opens without focus costs an
 * extra press before the keyboard appears, and on a remote every extra press is
 * felt.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val description: String = contentDescription

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(focused)
            .background(palette.surfaceRaised, RoundedCornerShape(Tokens.RadiusMd))
            .padding(horizontal = Tokens.SpaceMd, vertical = Tokens.SpaceSm),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            BasicText(
                text = placeholder,
                style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.onSurface, fontSize = Tokens.TextBody),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier
                .focusRequester(focusRequester)
                .semantics { this.contentDescription = description },
        )
    }
}

private val BUTTON_SIZE = 48.dp
