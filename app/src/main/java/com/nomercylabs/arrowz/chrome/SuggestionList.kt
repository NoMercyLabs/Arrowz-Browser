/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.data.Suggestion
import com.nomercylabs.arrowz.data.SuggestionKind
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.NavIcons
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.Tokens

/**
 * What the address bar offers under the field while something is being typed.
 *
 * It appears below the row rather than over the page, so the D-pad reaches it by
 * moving down out of the field — no new gesture, and nothing overlaps the
 * buttons the way a floating popover would.
 *
 * It is only reachable once the keyboard is down, because the leanback IME
 * consumes every directional key while it is up. That is the intended flow: type
 * enough letters, press BACK to drop the keyboard, then walk down into the list.
 */
@Composable
fun SuggestionList(
    suggestions: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
    firstRowFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    val palette = LocalPalette.current
    val description: String = stringResource(R.string.suggest_list_description)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Tokens.SpaceSm)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            ListRow(
                externalFocusRequester = if (index == 0) firstRowFocusRequester else null,
                title = titleOf(suggestion),
                subtitle = suggestion.subtitle,
                selected = false,
                offered = true,
                onClick = { onPick(suggestion) },
                // The kind is drawn, not coloured: a star says "you kept this"
                // to someone who cannot tell the row tints apart.
                trailing = if (suggestion.kind == SuggestionKind.Favourite) {
                    { NavIcons.Star(palette.accent, filled = true) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun titleOf(suggestion: Suggestion): String = when (suggestion.kind) {
    SuggestionKind.Search -> stringResource(R.string.suggest_search, suggestion.title)
    SuggestionKind.Destination -> stringResource(R.string.suggest_open, suggestion.title)
    else -> suggestion.title
}
