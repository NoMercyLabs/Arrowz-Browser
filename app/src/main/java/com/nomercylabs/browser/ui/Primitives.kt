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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * Every control below takes its focus from `clickable` alone, never from an
 * extra `focusable()` beside it: `clickable` is already a focus target, and the
 * pair puts two of them in one chain.
 */
@Composable
fun Modifier.tvFocusable(
    focused: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(Tokens.RadiusMd),
    /**
     * Growing on focus works for a control with room around it and is wrong for
     * one that already spans its row: the address field grew into the buttons
     * beside it and was clipped by them, and a full-width row grows past both
     * screen edges. Wide controls take the ring alone.
     */
    scaleOnFocus: Boolean = true,
): Modifier {
    val palette: Palette = LocalPalette.current
    val scale: Float by animateFloatAsState(
        targetValue = if (focused && scaleOnFocus) Tokens.Focus.Scale else 1f,
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
 * A single-line field for addresses and queries, in two states.
 *
 * Not editing, it is a button: it takes focus, and the D-pad still belongs to
 * the row it sits in. OK starts editing and raises the system keyboard.
 *
 * The two states are the whole point. A field that raises the keyboard the
 * moment the bar opens makes every other control in that bar unreachable,
 * because the leanback IME consumes every directional key while it is up —
 * RIGHT walks its suggestion strip and OK inserts a word. That was measured on
 * the 8010: pressing toward the tabs button typed "van" into the address.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    contentDescription: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val boxFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val description: String = contentDescription

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) boxFocusRequester.requestFocus()
    }

    LaunchedEffect(editing) {
        if (editing) fieldFocusRequester.requestFocus() else boxFocusRequester.requestFocus()
    }

    // The keyboard can also be dismissed by the system, and a field still
    // believing it is being edited would swallow the next BACK.
    val imeVisible: Boolean = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (editing && !imeVisible) onEditingChange(false)
    }

    Box(
        modifier = modifier
            .focusRequester(boxFocusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(focused || editing, scaleOnFocus = false)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onEditingChange(true) },
            )
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
        if (editing) {
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
                    .focusRequester(fieldFocusRequester)
                    .semantics { this.contentDescription = description },
            )
        } else {
            BasicText(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextBody),
                modifier = Modifier.semantics { this.contentDescription = description },
            )
        }
    }
}

/**
 * A full-width row: a title, a quieter second line, and an optional action at
 * the end.
 *
 * The row is two focus stops, not one. A single clickable spanning the whole
 * width leaves anything inside it unreachable — the tab list's close button
 * could not be focused at all — so the label is its own target and LEFT/RIGHT
 * moves between it and the action.
 *
 * [selected] is not focus. Focus says where the remote is; selection says which
 * tab is on screen behind the list, and the two are frequently different rows.
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }

    // Owned here rather than by the caller: a requester the caller attaches from
    // outside can be asked for focus before the row exists, which throws.
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .background(
                if (selected) palette.accentDeep.copy(alpha = SELECTED_ALPHA) else palette.surfaceRaised,
                RoundedCornerShape(Tokens.RadiusMd),
            )
            .padding(Tokens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        // The selected row carries a bar rather than only a tint: at three
        // metres a background shade this quiet is not a reliable signal.
        Box(
            modifier = Modifier
                .width(Tokens.SpaceXs)
                .height(SELECTED_BAR_HEIGHT)
                .background(
                    if (selected) palette.accent else Color.Transparent,
                    RoundedCornerShape(Tokens.RadiusSm),
                ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .tvFocusable(focused, scaleOnFocus = false)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = Tokens.SpaceMd, vertical = Tokens.SpaceSm),
        ) {
            BasicText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = if (focused) palette.accent else palette.onSurface,
                    fontSize = Tokens.TextBody,
                ),
            )
            if (subtitle.isNotEmpty()) {
                BasicText(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextSmall),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

private val BUTTON_SIZE = 48.dp
private val SELECTED_BAR_HEIGHT = 40.dp
private const val SELECTED_ALPHA: Float = 0.35f
