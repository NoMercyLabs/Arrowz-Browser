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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lifts a raised surface off the page: a small shadow, and a hairline that does
 * the work the shadow cannot. Applied before the background so the line sits on
 * the edge of the fill rather than inside it.
 */
@Composable
fun Modifier.raised(shape: RoundedCornerShape = RoundedCornerShape(Tokens.Radius)): Modifier {
    val palette: Palette = LocalPalette.current
    return this
        .shadow(Tokens.Elevation, shape)
        .border(Tokens.Hairline, palette.outline, shape)
}

/**
 * A bar that sits above the page: the address bar, and find.
 *
 * A header drawn on the page's own colour is a strip of nothing with controls
 * floating in it. Its own surface, a shadow beneath it and a line along the
 * bottom edge are what say the page passes underneath rather than around.
 *
 * Full-screen surfaces — the menu, the tab list, the library screens — take the
 * page's colour instead, because they replace the page rather than sit over it.
 */
@Composable
fun Modifier.chromeHeader(): Modifier {
    val palette: Palette = LocalPalette.current
    val hairlinePx: Float = with(LocalDensity.current) { Tokens.Hairline.toPx() }
    return this
        .fillMaxWidth()
        .shadow(Tokens.Focus.Elevation)
        .background(palette.surfaceRaised.copy(alpha = HEADER_ALPHA))
        .drawBehind {
            drawLine(
                color = palette.outline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = hairlinePx,
            )
        }
}

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
    shape: RoundedCornerShape = RoundedCornerShape(Tokens.Radius),
    /**
     * Growing on focus works for a control with room around it and is wrong for
     * one that already spans its row: the address field grew into the buttons
     * beside it and was clipped by them, and a full-width row grows past both
     * screen edges. Wide controls take the ring alone.
     */
    scaleOnFocus: Boolean = true,
    /**
     * Off where the control draws its own ring closer to the thing being
     * focused. A tile carried two: one around its artwork and one around the
     * artwork plus its label, which reads as a rendering fault rather than as
     * emphasis.
     */
    drawRing: Boolean = true,
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
            width = if (focused && drawRing) Tokens.Focus.RingWidth else 0.dp,
            color = if (focused && drawRing) palette.focusRing else Color.Transparent,
            shape = shape,
        )
}

/**
 * What a focused control is filled with.
 *
 * An outline alone is a thin line at three metres, and it competes with the
 * hairline every raised surface already carries. A focused control takes the
 * accent as its whole background instead, which is unmistakable from a sofa and
 * needs no colour vision to read as "this one".
 */
@Composable
fun focusFill(focused: Boolean, selected: Boolean = false, offered: Boolean = false): Color {
    val palette: Palette = LocalPalette.current
    return when {
        focused -> palette.accent
        selected -> palette.accentDeep.copy(alpha = SELECTED_ALPHA)
        offered -> palette.surfaceOffered
        else -> palette.surfaceRaised
    }
}

@Composable
fun contentOn(focused: Boolean): Color {
    val palette: Palette = LocalPalette.current
    return if (focused) palette.onAccent else palette.onSurface
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
            .tvFocusable(focused, RoundedCornerShape(Tokens.Radius))
            .raised(RoundedCornerShape(Tokens.Radius))
            .background(focusFill(focused), RoundedCornerShape(Tokens.Radius))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        glyph(contentOn(focused))
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
    externalFocusRequester: FocusRequester? = null,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val ownBoxFocusRequester = remember { FocusRequester() }
    val boxFocusRequester: FocusRequester = externalFocusRequester ?: ownBoxFocusRequester
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
            .height(BUTTON_SIZE)
            .raised()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onEditingChange(true) },
            )
            .background(palette.surfaceRaised, RoundedCornerShape(Tokens.Radius))
            .padding(horizontal = Tokens.SpaceMd),
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
    /** Drawn a step brighter than the surrounding chrome, for a row proposing
     *  something rather than one listing what already exists. */
    offered: Boolean = false,
    /** Supplied when something outside the row has to send focus here by name.
     *  A geometric search cannot always find a row that appeared underneath the
     *  control the viewer is standing on. */
    externalFocusRequester: FocusRequester? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val ownFocusRequester = remember { FocusRequester() }
    val focusRequester: FocusRequester = externalFocusRequester ?: ownFocusRequester

    // Owned here rather than by the caller: a requester the caller attaches from
    // outside can be asked for focus before the row exists, which throws.
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .raised()
            .background(focusFill(focused, selected, offered), RoundedCornerShape(Tokens.Radius))
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
                    RoundedCornerShape(Tokens.Radius),
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
                style = TextStyle(color = contentOn(focused), fontSize = Tokens.TextBody),
            )
            if (subtitle.isNotEmpty()) {
                BasicText(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (focused) palette.onAccent.copy(alpha = MUTED_ON_ACCENT) else palette.onSurfaceMuted,
                        fontSize = Tokens.TextSmall,
                    ),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * A site tile: a big letter on a colour derived from the origin, with the title
 * underneath.
 *
 * The letter is not a placeholder for a favicon that never arrived. Fetching one
 * is a network request per tile at exactly the moment the screen has to appear,
 * so the tile draws something instant, offline and stable across launches, and a
 * favicon captured while browsing can replace it later without the grid changing
 * shape.
 */
@Composable
fun SiteTile(
    title: String,
    origin: String,
    isFavourite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
    externalFocusRequester: FocusRequester? = null,
) {
    val palette: Palette = LocalPalette.current
    var focused: Boolean by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val ownFocusRequester = remember { FocusRequester() }
    val focusRequester: FocusRequester = externalFocusRequester ?: ownFocusRequester
    val label: String = title.ifBlank { origin }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(focused, drawRing = false)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            // Inside the focus ring, so the growth on focus has somewhere to go
            // and the title is not clipped by the tile's own edge.
            .padding(Tokens.SpaceXs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT)
                // The lift is the focus signal a coloured tile can carry: its
                // own colour is the thing identifying the site, so filling it
                // with the accent would throw that away. Height and a ring do
                // the work instead.
                .shadow(
                    if (focused) Tokens.Focus.Elevation else Tokens.Elevation,
                    RoundedCornerShape(Tokens.Radius),
                )
                .border(
                    width = if (focused) Tokens.Focus.RingWidth else Tokens.Hairline,
                    color = if (focused) palette.focusRing else palette.outline,
                    shape = RoundedCornerShape(Tokens.Radius),
                )
                // A gradient rather than a flat fill. A row of flat rectangles
                // reads as placeholders waiting for artwork; the same colour
                // with a light top and a deep bottom reads as a made thing.
                .background(
                    Brush.verticalGradient(tileColours(origin, palette)),
                    RoundedCornerShape(Tokens.Radius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = origin.take(INITIALS).uppercase(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = Tokens.TextDisplay,
                    fontWeight = FontWeight.Bold,
                    // Two letters is what tells sites apart. One initial makes
                    // every g-domain the same tile from three metres.
                    letterSpacing = TILE_LETTER_SPACING,
                ),
            )
            if (isFavourite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Tokens.SpaceSm),
                ) {
                    NavIcons.Star(Color.White, filled = true)
                }
            }
        }
        BasicText(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = if (focused) palette.accent else palette.onSurface,
                fontSize = Tokens.TextSmall,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            ),
            // The tile itself keeps its own colour on focus: filling it would
            // throw away the one thing that tells sites apart at a glance.
            modifier = Modifier.padding(top = Tokens.SpaceXs),
        )
    }
}

/**
 * Derived from the origin so a site keeps its colour between launches, and kept
 * dark enough that the letter on top stays legible.
 */
private fun tileColours(origin: String, palette: Palette): List<Color> {
    // Confined to the cool arc rather than the whole wheel. A free hue puts a
    // red tile beside a green one on a slate screen, and the grid stops looking
    // like one interface; inside this band every site still gets its own
    // recognisable colour and they all belong to the room.
    val step: Int = (origin.hashCode() % HUE_STEPS + HUE_STEPS) % HUE_STEPS
    val hue: Float = (HUE_START + step * HUE_SPAN / HUE_STEPS) / 360f
    // Low enough that a tile is a shade rather than a colour. At the previous
    // saturation the grid was the loudest thing on a deliberately quiet screen.
    val saturation: Float = if (palette.isLight) 0.30f else 0.22f
    // Dark enough in either palette to carry white letters, and never so dark
    // that the tile becomes one more black rectangle on a black screen.
    val lightness: Float = if (palette.isLight) 0.46f else 0.32f
    return listOf(
        Color.hsl(hue * 360f, saturation, lightness + GRADIENT_LIFT),
        Color.hsl(hue * 360f, saturation, lightness - GRADIENT_LIFT),
    )
}

private const val HUE_STEPS: Int = 24

/** Teal through to indigo: the arc the palette itself lives in. */
private const val HUE_START: Float = 175f
private const val HUE_SPAN: Float = 90f
private const val INITIALS: Int = 2
private const val GRADIENT_LIFT: Float = 0.08f
private val TILE_LETTER_SPACING = 2.sp
private val TILE_HEIGHT = 120.dp
private val BUTTON_SIZE = 48.dp
private val SELECTED_BAR_HEIGHT = 40.dp
private const val SELECTED_ALPHA: Float = 0.35f
private const val MUTED_ON_ACCENT: Float = 0.75f

/** Nearly opaque. A header has to be readable over whatever the page happens to
 *  be showing behind it, and only the last sliver of transparency says there is
 *  a page there at all. */
private const val HEADER_ALPHA: Float = 0.97f
