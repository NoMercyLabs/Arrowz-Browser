/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.tabs.Tab
import com.nomercylabs.arrowz.ui.IconButton
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.NavIcons
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.Tokens
import com.nomercylabs.arrowz.ui.overscan

/**
 * The open tabs, as a list rather than a thumbnail grid.
 *
 * A grid arrives with the rest of the tile screens. A list is what the six-key
 * baseline reads best anyway: one axis, one press per tab, and a title that is
 * legible from a sofa where a scaled-down screenshot is not.
 */
@Composable
fun TabList(
    tabs: List<Tab>,
    activeId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    val palette: Palette = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = stringResource(R.string.tabs_title, tabs.size),
                style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
                modifier = Modifier.padding(end = Tokens.SpaceMd),
            )
            IconButton(
                contentDescription = stringResource(R.string.tabs_new),
                onClick = onNewTab,
            ) { tint -> NavIcons.Plus(tint) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
            items(tabs, key = { tab -> tab.id }) { tab ->
                ListRow(
                    title = tab.page.pageTitle.ifEmpty { stringResource(R.string.tabs_untitled) },
                    subtitle = tab.page.pageUrl,
                    selected = tab.id == activeId,
                    onClick = { onSelect(tab.id) },
                    // The list opens standing on the tab you are already
                    // looking at, so OK is always a return rather than a jump.
                    requestInitialFocus = tab.id == activeId,
                    // Which tab is on screen is drawn as a coloured bar and read
                    // as nothing, so a reader hears a list of pages with no way
                    // to tell which one is behind the list.
                    roleDescription = stringResource(R.string.a11y_role_tab),
                    selectedDescription = stringResource(R.string.a11y_selected),
                ) {
                    IconButton(
                        contentDescription = stringResource(R.string.tabs_close),
                        onClick = { onClose(tab.id) },
                    ) { tint -> NavIcons.Close(tint) }
                }
            }
        }
    }
}

private const val SCRIM_ALPHA: Float = 0.97f
