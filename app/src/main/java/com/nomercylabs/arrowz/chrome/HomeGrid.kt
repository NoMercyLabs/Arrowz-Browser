/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.data.Tile
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.SiteTile
import com.nomercylabs.arrowz.ui.Tokens
import com.nomercylabs.arrowz.ui.overscan

/**
 * What a new tab opens on.
 *
 * A grid rather than a search engine: a search box on a television costs a
 * dozen presses per word, and the things worth opening are nearly always ones
 * the viewer has opened before. The address field stays one press away, above.
 */
@Composable
fun HomeGrid(
    tiles: List<Tile>,
    onOpen: (String) -> Unit,
    /**
     * Named by the bar above, which sends DOWN here. A geometric search across
     * the gap between two sections finds nothing reliably — measured on the
     * 8010, where DOWN out of the address field landed on no control at all
     * until OK had been pressed first.
     */
    firstTileFocusRequester: FocusRequester,
    /** Reads a file already on disk. Deliberately not a fetch: the grid draws
     *  the moment it appears and cannot wait on the network, which is the same
     *  reason a tile drew letters in the first place. */
    iconFor: (String) -> String? = { null },
) {
    val palette: Palette = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface)
            .overscan(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        BasicText(
            text = stringResource(if (tiles.isEmpty()) R.string.home_empty else R.string.home_title),
            style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
        )

        if (tiles.isEmpty()) {
            // Nothing to draw and nothing to focus. The bar above is the only
            // way forward from here, and UP already reaches it.
            Box(modifier = Modifier.fillMaxSize())
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            // A lazy list clips at its viewport, and the viewport edge fell on
            // the first and last item's edge, so their shadow and focus growth
            // were cut while every item between them kept both.
            contentPadding = PaddingValues(Tokens.SpaceSm),
            // A group with a memory: leaving the grid and coming back restores
            // the tile that was last focused, which is what every television
            // interface does and what makes a long grid usable at all.
            modifier = Modifier
                .padding(top = Tokens.SpaceSm)
                .focusGroup()
                .focusRestorer(firstTileFocusRequester),
        ) {
            items(tiles, key = { tile -> tile.origin }) { tile ->
                SiteTile(
                    title = tile.title,
                    origin = tile.origin,
                    isFavourite = tile.isFavourite,
                    iconPath = iconFor(tile.origin),
                    // The bar above owns focus when the screen appears, so this
                    // is a target to be sent to rather than one that grabs.
                    externalFocusRequester = if (tile === tiles.first()) firstTileFocusRequester else null,
                    onClick = { onOpen(tile.url) },
                )
            }
        }
    }
}

/** Six across on a 960dp canvas leaves tiles wide enough to read a title at
 *  three metres and still shows two rows without scrolling. */
private const val COLUMNS: Int = 6
