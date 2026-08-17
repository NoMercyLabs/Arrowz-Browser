/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.ThemeMode
import com.nomercylabs.arrowz.ui.Tokens
import com.nomercylabs.arrowz.ui.overscan

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
    /** Null while a screen reader is driving, which is not a choice to offer:
     *  the reader owns the D-pad and switching would put two focus systems on
     *  one screen. */
    inputModeIsFocus: Boolean?,
    onToggleInputMode: () -> Unit,
    isStaySignedIn: Boolean,
    onToggleStaySignedIn: () -> Unit,
    isFilteringOn: Boolean,
    blockedOnPage: Int,
    onToggleFiltering: () -> Unit,
) {
    val palette: Palette = LocalPalette.current

    // Scrollable, and it has to be. The menu outgrew 1080p when privacy landed,
    // and a row past the bottom edge of a television is a row nobody can reach
    // at all — the same failure as a press that does nothing, arrived at from
    // the other direction. Compose brings a focused child into view, so the
    // D-pad walks the whole list without anything else changing.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan()
            .verticalScroll(rememberScrollState()),
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
            // Only offered for a real page, because it is a decision about one
            // site and there is no site behind the home screen.
            ListRow(
                title = stringResource(R.string.menu_stay_signed_in),
                subtitle = stringResource(
                    if (isStaySignedIn) {
                        R.string.menu_stay_signed_in_on
                    } else {
                        R.string.menu_stay_signed_in_off
                    },
                ),
                selected = isStaySignedIn,
                onClick = onToggleStaySignedIn,
            )
        }

        // Long-pressing OK does this too, and that is the faster way once you
        // know it. It is not discoverable, and a shortcut nobody is told about
        // cannot be the only way in -- the same reason the menu has a button in
        // the address bar as well as a long press.
        ListRow(
            title = stringResource(R.string.menu_input),
            subtitle = stringResource(
                when (inputModeIsFocus) {
                    null -> R.string.menu_input_reader
                    true -> R.string.menu_input_focus
                    false -> R.string.menu_input_cursor
                },
            ),
            selected = inputModeIsFocus == true,
            onClick = onToggleInputMode,
        )

        // The count is the subtitle rather than a badge: on a television a
        // number nobody can read is decoration, and this one is the only
        // evidence the viewer ever sees that the filtering is doing anything.
        ListRow(
            title = stringResource(R.string.menu_privacy),
            subtitle = when {
                !isFilteringOn -> stringResource(R.string.menu_privacy_off)
                blockedOnPage > 0 ->
                    pluralStringResource(R.plurals.menu_privacy_blocked, blockedOnPage, blockedOnPage)
                else -> stringResource(R.string.menu_privacy_on)
            },
            selected = isFilteringOn,
            onClick = onToggleFiltering,
        )

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
