/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.arrowz.ui.IconButton
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.NavIcons
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.Tokens
import com.nomercylabs.arrowz.ui.overscan

/** One row of a library screen, whichever list it came from. */
data class LibraryRow(val title: String, val subtitle: String, val url: String)

/**
 * Bookmarks and history, which are the same screen with different rows.
 *
 * A list rather than the home screen's tile grid: these are read to find one
 * known thing, and a title with its address underneath is what a search by eye
 * needs. Tiles are for the handful of places worth recognising by colour.
 */
@Composable
fun LibraryScreen(
    title: String,
    rows: List<LibraryRow>,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onRemove: ((LibraryRow) -> Unit)? = null,
    removeDescription: String = "",
) {
    val palette: Palette = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
            modifier = Modifier.fillMaxWidth(),
        )

        // The empty state is a row rather than a line of text, and it takes
        // focus. A screen with nothing focusable strands the viewer: every
        // direction does nothing, and only someone who already knows BACK is
        // the way out gets out.
        if (rows.isEmpty()) {
            ListRow(
                title = emptyMessage,
                subtitle = "",
                selected = false,
                onClick = onClose,
                requestInitialFocus = true,
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
            // A lazy list clips at its viewport, and the viewport edge fell on
            // the first and last item's edge, so their shadow and focus growth
            // were cut while every item between them kept both.
            contentPadding = PaddingValues(Tokens.SpaceSm),
        ) {
            items(rows, key = { row -> row.url + row.subtitle }) { row ->
                ListRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    selected = false,
                    onClick = { onOpen(row.url) },
                    requestInitialFocus = row === rows.first(),
                    trailing = onRemove?.let { remove ->
                        {
                            IconButton(
                                contentDescription = removeDescription,
                                onClick = { remove(row) },
                            ) { tint -> NavIcons.Close(tint) }
                        }
                    },
                )
            }
        }
    }
}

private const val SCRIM_ALPHA: Float = 0.97f
