/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.browser.UrlOrSearch
import com.nomercylabs.arrowz.data.HomeContent
import com.nomercylabs.arrowz.data.SitePermission
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.ThemeMode
import com.nomercylabs.arrowz.ui.Tokens

/**
 * Where the menu is. One level deep by design, so this is a place rather than a
 * stack: BACK from anywhere below the root returns to it, never deeper.
 */
enum class MenuSection(val titleRes: Int) {
    Root(R.string.menu_title),
    Site(R.string.menu_site),
    Library(R.string.menu_library),
    Settings(R.string.menu_settings),
    About(R.string.menu_about_section),
    SearchEngine(R.string.menu_search_engine),
    Permissions(R.string.menu_permissions),
    ClearData(R.string.menu_clear_data),
    Downloads(R.string.menu_downloads),
}

/**
 * What a long press on BACK opens.
 *
 * One level of submenus, and only one. The list was flat because a second level
 * on a television costs two presses to reach and two to leave, and at eight rows
 * nothing needed one. It reached thirteen with more owed, and at that length the
 * flat list costs more than the second level saves: the bottom rows sit off the
 * screen behind a scroll, and finding anything means reading every label from
 * three metres. That rule was right for the menu it was written for and has
 * outlived it.
 *
 * What stays on the top level is what you reach for mid-page. Find and closing a
 * tab are worth no detour; a per-site toggle you set once is.
 *
 * Nothing here duplicates the address bar. Open tabs, the home screen, reload
 * and keeping a page each have a button up there already, and a second door to
 * every one of them was most of the length this menu was drowning in.
 */
@Composable
fun MenuOverlay(
    canKeepPage: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    isDesktopSite: Boolean,
    themeMode: ThemeMode,
    section: MenuSection,
    onSection: (MenuSection) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
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
    versionName: String,
    searchEngineId: String,
    onPickSearchEngine: (String) -> Unit,
    permissions: List<SitePermission>,
    onForgetPermission: (SitePermission) -> Unit,
    /** Title and state, already read from the system downloader. The menu does
     *  no querying of its own. */
    downloads: List<Pair<String, String>>,
    onClearHistory: () -> Unit,
    onClearCookies: () -> Unit,
    onClearIcons: () -> Unit,
    onClearPermissions: () -> Unit,
) {
    val palette: Palette = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA)),
    ) {
        // Outside the scroll. A title that scrolls away takes with it the only
        // thing naming the list, and inside the column it read as another row.
        BasicText(
            text = stringResource(section.titleRes),
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.OverscanHorizontal,
                    end = Tokens.OverscanHorizontal,
                    top = Tokens.OverscanVertical,
                    bottom = if (section == MenuSection.Root) Tokens.SpaceMd else Tokens.SpaceXs,
                ),
        )

        // Said rather than assumed. BACK closing the whole menu from a submenu
        // is what makes a second level feel like a trapdoor, so it pops one
        // level and the screen says so while you are down there.
        if (section != MenuSection.Root) {
            BasicText(
                text = stringResource(R.string.menu_back_hint),
                style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Tokens.OverscanHorizontal,
                        end = Tokens.OverscanHorizontal,
                        bottom = Tokens.SpaceMd,
                    ),
            )
        }

        // The line is what separates the heading from the list. A gap alone
        // reads as loose spacing once a row is scrolled up beneath the title.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Hairline)
                .background(palette.outline),
        )

        // The inset sits inside the scroll rather than around it. Outside, the
        // viewport edge fell on the last row's edge and clipped the shadow every
        // raised surface carries, so the bottom row came out flat against the
        // screen edge while every row above it was lifted.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Tokens.OverscanHorizontal,
                    end = Tokens.OverscanHorizontal,
                    top = Tokens.SpaceMd,
                    bottom = Tokens.OverscanVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        ) {
            when (section) {
                MenuSection.Root -> {
                    if (canKeepPage) {
                        ListRow(
                            title = stringResource(R.string.menu_find),
                            subtitle = "",
                            selected = false,
                            onClick = onFind,
                            requestInitialFocus = true,
                        )
                    }
                    ListRow(
                        title = stringResource(R.string.tabs_new),
                        subtitle = "",
                        selected = false,
                        onClick = onNewTab,
                        requestInitialFocus = !canKeepPage,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_close_tab),
                        subtitle = "",
                        selected = false,
                        onClick = onCloseTab,
                    )
                    if (canKeepPage) {
                        ListRow(
                            title = stringResource(R.string.menu_site),
                            subtitle = stringResource(R.string.menu_site_summary),
                            selected = false,
                            onClick = { onSection(MenuSection.Site) },
                        )
                    }
                    ListRow(
                        title = stringResource(R.string.menu_library),
                        subtitle = stringResource(R.string.menu_library_summary),
                        selected = false,
                        onClick = { onSection(MenuSection.Library) },
                    )
                    ListRow(
                        title = stringResource(R.string.menu_settings),
                        subtitle = stringResource(R.string.menu_settings_summary),
                        selected = false,
                        onClick = { onSection(MenuSection.Settings) },
                    )
                    ListRow(
                        title = stringResource(R.string.menu_about),
                        subtitle = "",
                        selected = false,
                        onClick = { onSection(MenuSection.About) },
                    )
                }

                MenuSection.Site -> {
                    // Back, after being cut as an address-bar duplicate. The bar
                    // carries a star and that was the argument, but the bar is
                    // revealed by pressing UP with the pointer against the top
                    // edge of a page that is already at its top - and
                    // app.nomercy.tv never satisfies it, so on that page the
                    // star does not exist and keeping the page became
                    // impossible. A second door is only redundant while the
                    // first one opens.
                    ListRow(
                        title = stringResource(
                            if (isFavourite) R.string.nav_favourite_remove else R.string.nav_favourite_add,
                        ),
                        subtitle = "",
                        selected = isFavourite,
                        onClick = onToggleFavourite,
                        requestInitialFocus = true,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_privacy),
                        subtitle = if (isFilteringOn) {
                            if (blockedOnPage > 0) {
                                pluralStringResource(
                                    R.plurals.menu_privacy_blocked,
                                    blockedOnPage,
                                    blockedOnPage,
                                )
                            } else {
                                stringResource(R.string.menu_privacy_on)
                            }
                        } else {
                            stringResource(R.string.menu_privacy_off)
                        },
                        selected = isFilteringOn,
                        onClick = onToggleFiltering,
                    )
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
                    ListRow(
                        title = stringResource(
                            if (isDesktopSite) R.string.menu_tv_site else R.string.menu_desktop_site,
                        ),
                        subtitle = "",
                        selected = isDesktopSite,
                        onClick = onToggleDesktopSite,
                    )
                    // Absent while a reader is driving rather than disabled. A
                    // row that cannot act is a press that does nothing, which is
                    // the failure this interface is built to avoid.
                    if (inputModeIsFocus != null) {
                        ListRow(
                            title = stringResource(R.string.menu_input),
                            subtitle = stringResource(
                                if (inputModeIsFocus) {
                                    R.string.menu_input_focus
                                } else {
                                    R.string.menu_input_cursor
                                },
                            ),
                            selected = inputModeIsFocus,
                            onClick = onToggleInputMode,
                        )
                    }
                }

                MenuSection.Library -> {
                    ListRow(
                        title = stringResource(R.string.menu_bookmarks),
                        subtitle = "",
                        selected = false,
                        onClick = onBookmarks,
                        requestInitialFocus = true,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_history),
                        subtitle = "",
                        selected = false,
                        onClick = onHistory,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_downloads),
                        subtitle = "",
                        selected = false,
                        onClick = { onSection(MenuSection.Downloads) },
                    )
                }

                MenuSection.Settings -> {
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
                        requestInitialFocus = true,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_search_engine),
                        subtitle = UrlOrSearch.engineById(searchEngineId).label,
                        selected = false,
                        onClick = { onSection(MenuSection.SearchEngine) },
                    )
                    ListRow(
                        title = stringResource(R.string.menu_permissions),
                        subtitle = "",
                        selected = false,
                        onClick = { onSection(MenuSection.Permissions) },
                    )
                    ListRow(
                        title = stringResource(R.string.menu_clear_data),
                        subtitle = "",
                        selected = false,
                        onClick = { onSection(MenuSection.ClearData) },
                    )
                }

                MenuSection.SearchEngine -> {
                    UrlOrSearch.ENGINES.forEachIndexed { index, engine ->
                        ListRow(
                            title = engine.label,
                            subtitle = HomeContent.originOf(engine.home),
                            selected = engine.id == searchEngineId,
                            onClick = { onPickSearchEngine(engine.id) },
                            requestInitialFocus = index == 0,
                        )
                    }
                }

                MenuSection.Permissions -> {
                    if (permissions.isEmpty()) {
                        ListRow(
                            title = stringResource(R.string.menu_permissions_empty),
                            subtitle = "",
                            selected = false,
                            onClick = {},
                            requestInitialFocus = true,
                        )
                    }
                    permissions.forEachIndexed { index, permission ->
                        ListRow(
                            title = permission.origin,
                            subtitle = permissionSubtitle(permission),
                            selected = permission.decision == ALLOW,
                            onClick = { onForgetPermission(permission) },
                            requestInitialFocus = index == 0,
                        )
                    }
                }

                MenuSection.ClearData -> {
                    // Four rows rather than one button. "Clear everything" on a
                    // television is one press away from someone who meant to
                    // close the menu, and these are genuinely different losses.
                    ListRow(
                        title = stringResource(R.string.menu_clear_history),
                        subtitle = "",
                        selected = false,
                        onClick = onClearHistory,
                        requestInitialFocus = true,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_clear_cookies),
                        subtitle = "",
                        selected = false,
                        onClick = onClearCookies,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_clear_icons),
                        subtitle = "",
                        selected = false,
                        onClick = onClearIcons,
                    )
                    ListRow(
                        title = stringResource(R.string.menu_clear_permissions),
                        subtitle = "",
                        selected = false,
                        onClick = onClearPermissions,
                    )
                }

                MenuSection.Downloads -> {
                    if (downloads.isEmpty()) {
                        ListRow(
                            title = stringResource(R.string.menu_downloads_empty),
                            subtitle = "",
                            selected = false,
                            onClick = {},
                            requestInitialFocus = true,
                        )
                    }
                    downloads.forEachIndexed { index, download ->
                        ListRow(
                            title = download.first,
                            subtitle = download.second,
                            selected = false,
                            onClick = {},
                            requestInitialFocus = index == 0,
                        )
                    }
                }

                // Not a row. A row is something to press, and this one did
                // nothing when pressed, which is the exact failure the rest of
                // this interface is built to avoid. The mark says which app this
                // is better than its name repeated in a list would, so the
                // section is a page about the product rather than a menu of one
                // entry that goes nowhere.
                MenuSection.About -> AboutPanel(versionName)
            }
        }
    }
}

/** Which permission, and what was answered, as one line a reader speaks once. */
@Composable
private fun permissionSubtitle(permission: SitePermission): String {
    val kind: String = stringResource(
        when (permission.kind) {
            "camera" -> R.string.permission_camera
            "microphone" -> R.string.permission_microphone
            else -> R.string.permission_location
        },
    )
    val decision: String = stringResource(
        if (permission.decision == ALLOW) {
            R.string.permission_decision_allow
        } else {
            R.string.permission_decision_block
        },
    )
    return "$kind, $decision"
}

private const val ALLOW: String = "allow"

/**
 * What the About section draws.
 *
 * Deliberately holds no focus. There is nothing here to activate, and a stop the
 * ring can sit on while doing nothing is worse than no stop at all; BACK is the
 * way out and the heading already says so.
 */
@Composable
private fun AboutPanel(versionName: String) {
    val palette: Palette = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Tokens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
    ) {
        BasicText(
            text = stringResource(R.string.app_name),
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextDisplay),
        )
        BasicText(
            text = stringResource(R.string.menu_about_version, versionName),
            style = TextStyle(color = palette.accent, fontSize = Tokens.TextBody),
        )
        BasicText(
            text = stringResource(R.string.menu_about_what),
            style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
        )
        BasicText(
            text = stringResource(R.string.menu_about_privacy_line),
            style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
        )
        BasicText(
            text = stringResource(R.string.menu_about_licence),
            style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextSmall),
        )
    }
}

private const val SCRIM_ALPHA: Float = 0.97f
