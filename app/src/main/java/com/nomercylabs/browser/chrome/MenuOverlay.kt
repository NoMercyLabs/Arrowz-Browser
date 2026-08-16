/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.browser.R
import com.nomercylabs.browser.ui.ListRow
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.ThemeMode
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.overscan

/**
 * What a long press on BACK opens.
 *
 * Long-press BACK was consuming the press and falling back to a plain BACK,
 * because leaving it unhandled made the framework fire a second command and one
 * hold quit the browser. This is what it was always meant to do.
 *
 * Every entry acts immediately. There is no submenu: a second level on a
 * television costs two presses to reach and two to leave, and nothing here needs
 * one.
 */
@Composable
fun MenuOverlay(
    canKeepPage: Boolean,
    isFavourite: Boolean,
    isDesktopSite: Boolean,
    themeMode: ThemeMode,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onToggleFavourite: () -> Unit,
    onCycleTheme: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktopSite: () -> Unit,
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
            text = stringResource(R.string.menu_title),
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
            modifier = Modifier.fillMaxWidth(),
        )

        ListRow(
            title = stringResource(R.string.tabs_new),
            subtitle = "",
            selected = false,
            onClick = onNewTab,
            requestInitialFocus = true,
        )
        ListRow(
            title = stringResource(R.string.nav_tabs),
            subtitle = "",
            selected = false,
            onClick = onTabs,
        )
        ListRow(
            title = stringResource(R.string.menu_home),
            subtitle = "",
            selected = false,
            onClick = onHome,
        )

        // Both hidden rather than disabled while the home screen is showing:
        // a row that cannot act is a press that does nothing, which is the
        // failure this interface is built to avoid.
        if (canKeepPage) {
            ListRow(
                title = stringResource(
                    if (isFavourite) R.string.nav_favourite_remove else R.string.nav_favourite_add,
                ),
                subtitle = "",
                selected = isFavourite,
                onClick = onToggleFavourite,
            )
            ListRow(
                title = stringResource(R.string.menu_find),
                subtitle = "",
                selected = false,
                onClick = onFind,
            )
            ListRow(
                title = stringResource(
                    if (isDesktopSite) R.string.menu_tv_site else R.string.menu_desktop_site,
                ),
                subtitle = "",
                selected = isDesktopSite,
                onClick = onToggleDesktopSite,
            )
            ListRow(
                title = stringResource(R.string.nav_reload),
                subtitle = "",
                selected = false,
                onClick = onReload,
            )
        }

        ListRow(
            title = stringResource(R.string.menu_bookmarks),
            subtitle = "",
            selected = false,
            onClick = onBookmarks,
        )
        ListRow(
            title = stringResource(R.string.menu_history),
            subtitle = "",
            selected = false,
            onClick = onHistory,
        )

        ListRow(
            title = stringResource(R.string.menu_theme),
            subtitle = stringResource(
                when (themeMode) {
                    ThemeMode.System -> R.string.menu_theme_system
                    ThemeMode.Light -> R.string.menu_theme_light
                    ThemeMode.Dark -> R.string.menu_theme_dark
                },
            ),
            selected = false,
            onClick = onCycleTheme,
        )
    }
}

private const val SCRIM_ALPHA: Float = 0.97f
