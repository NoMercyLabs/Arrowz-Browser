/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.browser.R
import com.nomercylabs.browser.ui.ListRow
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.overscan

/**
 * A `<select>` as a list.
 *
 * This removes the worst class of television browser defect outright — the
 * dropdown or date picker that simply cannot be reached — because there is no
 * page-drawn widget left to reach. The options arrive as data and render in the
 * same primitive the menu and settings use, so it is operated exactly like
 * every other list in the app.
 */
@Composable
fun SelectListSheet(
    field: FormField,
    onChoose: (optionIndex: Int) -> Unit,
    onClose: () -> Unit,
) {
    val palette: Palette = LocalPalette.current
    val label: String = field.label.ifBlank { stringResource(R.string.form_field_generic) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
            modifier = Modifier.fillMaxWidth(),
        )

        // A select with no options is a page still filling it in. The row takes
        // focus so the viewer is not stranded on a screen where every direction
        // does nothing.
        if (field.options.isEmpty()) {
            ListRow(
                title = stringResource(R.string.form_no_options),
                subtitle = "",
                selected = false,
                onClick = onClose,
                requestInitialFocus = true,
            )
            return@Column
        }

        // Focus opens on what is already chosen rather than at the top. A long
        // country list otherwise costs a hundred presses to get back to where
        // the page already was.
        val opensAt: Int = field.selectedIndex.coerceAtLeast(0)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            itemsIndexed(field.options) { index, option ->
                ListRow(
                    title = option.label.ifBlank { option.value },
                    subtitle = "",
                    selected = option.isSelected,
                    onClick = { onChoose(index) },
                    requestInitialFocus = index == opensAt,
                )
            }
        }
    }
}

private const val SCRIM_ALPHA: Float = 0.97f
