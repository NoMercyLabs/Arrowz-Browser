/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.browser.R
import com.nomercylabs.browser.ui.IconButton
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.NavIcons
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.TvTextField
import com.nomercylabs.browser.ui.overscan

/**
 * Find in page.
 *
 * The count is not decoration: on a television the highlighted match is often
 * off screen when the search starts, and "3 of 12" is the only thing telling
 * the viewer that pressing next is worth doing.
 */
@Composable
fun FindBar(
    matches: Int,
    activeMatch: Int,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val palette: Palette = LocalPalette.current
    var query: String by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvTextField(
            value = query,
            onValueChange = { value ->
                query = value
                onQueryChange(value)
            },
            onSubmit = onNext,
            placeholder = stringResource(R.string.find_placeholder),
            contentDescription = stringResource(R.string.find_placeholder),
            editing = editing,
            onEditingChange = onEditingChange,
            requestInitialFocus = true,
            modifier = Modifier.weight(1f),
        )

        BasicText(
            text = if (matches == 0) "" else "$activeMatch / $matches",
            style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
        )

        IconButton(
            contentDescription = stringResource(R.string.find_previous),
            onClick = onPrevious,
        ) { tint -> NavIcons.Previous(tint) }

        IconButton(
            contentDescription = stringResource(R.string.find_next),
            onClick = onNext,
        ) { tint -> NavIcons.Next(tint) }
    }
}

private const val SCRIM_ALPHA: Float = 0.96f
