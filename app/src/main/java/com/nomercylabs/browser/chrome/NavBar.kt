/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.nomercylabs.browser.R
import com.nomercylabs.browser.data.Suggestion
import com.nomercylabs.browser.ui.IconButton
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.NavIcons
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.TvTextField
import com.nomercylabs.browser.ui.overscan
import androidx.compose.foundation.focusGroup
import com.nomercylabs.browser.ui.chromeHeader

/**
 * The address bar.
 *
 * Sits inside the overscan inset while page content stays full bleed, which is
 * why the inset is a modifier applied per surface rather than something the
 * theme does to everything.
 */
@Composable
fun NavBar(
    currentUrl: String,
    canGoBack: Boolean,
    progress: Int,
    tabCount: Int,
    isFavourite: Boolean,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onToggleFavourite: () -> Unit,
    suggestionsFor: (String) -> List<Suggestion>,
    onPickSuggestion: (Suggestion) -> Unit,
    onVoice: () -> Unit,
    onMenu: () -> Unit,
    /**
     * Where DOWN goes when there is nothing to suggest: the first tile of the
     * home grid. Named rather than searched for, because a geometric search
     * across the gap between two sections found nothing at all — DOWN out of
     * the field left no control focused anywhere on screen.
     */
    downTarget: FocusRequester? = null,
    /** Held by whoever needs to send focus back here after a surface closes. */
    fieldFocusRequester: FocusRequester? = null,
) {
    val palette: Palette = LocalPalette.current
    var typed: String by remember(currentUrl) { mutableStateOf(currentUrl) }

    // Nothing is offered until the address has actually been touched: opening
    // the bar to check where you are should not bury the page under a list.
    val suggestions: List<Suggestion> =
        if (typed == currentUrl) emptyList() else suggestionsFor(typed)
    val firstSuggestion = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .chromeHeader()
            .overscan(),
    ) {
        Row(
            // One group, one exit. Every control in the bar hands DOWN to the
            // same place, rather than each button searching for whatever
            // happens to sit under it.
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusProperties {
                    val target: FocusRequester? =
                        if (suggestions.isNotEmpty()) firstSuggestion else downTarget
                    if (target != null) down = target
                },
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                IconButton(
                    contentDescription = stringResource(R.string.nav_back),
                    onClick = onBack,
                ) { tint -> NavIcons.Back(tint) }
            }
            IconButton(
                contentDescription = stringResource(R.string.nav_reload),
                onClick = onReload,
            ) { tint -> NavIcons.Reload(tint) }
            IconButton(
                contentDescription = stringResource(R.string.nav_home),
                onClick = onHome,
            ) { tint -> NavIcons.Home(tint) }

            TvTextField(
                value = typed,
                onValueChange = { typed = it },
                onSubmit = { onNavigate(typed) },
                placeholder = stringResource(R.string.nav_placeholder),
                contentDescription = stringResource(R.string.nav_field_description),
                editing = editing,
                onEditingChange = onEditingChange,
                requestInitialFocus = true,
                externalFocusRequester = fieldFocusRequester,
                // weight, not fillMaxWidth: inside a Row the latter takes the
                // whole width and draws over the buttons beside it.
                //
                // DOWN is named rather than searched for. Measured on the 8010:
                // the geometric search left the field and landed on nothing at
                // all — no ring anywhere and the next press lost — because the
                // list appears underneath the control the viewer is standing on
                // rather than being there when focus arrived.
                modifier = Modifier.weight(1f),
            )

            // Beside the field, because speaking is the alternative to typing
            // into it and a remote's mic is the fastest way in on a television.
            IconButton(
                contentDescription = stringResource(R.string.nav_voice),
                onClick = onVoice,
            ) { tint -> NavIcons.Mic(tint) }

            IconButton(
                contentDescription = stringResource(
                    if (isFavourite) R.string.nav_favourite_remove else R.string.nav_favourite_add,
                ),
                onClick = onToggleFavourite,
            ) { tint -> NavIcons.Star(tint, filled = isFavourite) }

            IconButton(
                contentDescription = stringResource(R.string.nav_tabs),
                onClick = onTabs,
            ) { tint -> NavIcons.Tabs(tint) }

            // Everything else lives behind this, at the end of the bar where a
            // browser's menu has always been. Long-press BACK still opens it,
            // but a shortcut nobody is told about cannot be the only way in.
            IconButton(
                contentDescription = stringResource(R.string.nav_menu),
                onClick = onMenu,
            ) { tint -> NavIcons.Profile(tint) }

            // Beside the glyph rather than inside it: a numeral drawn into a
            // 24dp box cannot be read from a sofa.
            if (tabCount > 1) {
                BasicText(
                    text = tabCount.toString(),
                    style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
                )
            }
        }

        // Only while loading. A progress bar that is always present is a
        // permanent line of chrome that tells the viewer nothing.
        if (progress in 1..99) {
            Box(
                modifier = Modifier
                    .padding(top = Tokens.SpaceSm)
                    .fillMaxWidth(progress / 100f)
                    .height(PROGRESS_HEIGHT)
                    .background(palette.accent, RoundedCornerShape(PROGRESS_HEIGHT / 2)),
            )
        }

        SuggestionList(
            suggestions = suggestions,
            onPick = onPickSuggestion,
            firstRowFocusRequester = firstSuggestion,
        )
    }
}

private const val SCRIM_ALPHA: Float = 0.96f
private val PROGRESS_HEIGHT = 3.dp
