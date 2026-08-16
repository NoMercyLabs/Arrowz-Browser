/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.browser.R
import com.nomercylabs.browser.data.Tile
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.SiteTile
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.overscan

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
            modifier = Modifier.padding(top = Tokens.SpaceSm),
        ) {
            items(tiles, key = { tile -> tile.origin }) { tile ->
                SiteTile(
                    title = tile.title,
                    origin = tile.origin,
                    isFavourite = tile.isFavourite,
                    // The first tile holds focus when the screen appears, so
                    // the D-pad is never pointing at nothing.
                    requestInitialFocus = tile === tiles.first(),
                    onClick = { onOpen(tile.url) },
                )
            }
        }
    }
}

/** Six across on a 960dp canvas leaves tiles wide enough to read a title at
 *  three metres and still shows two rows without scrolling. */
private const val COLUMNS: Int = 6
