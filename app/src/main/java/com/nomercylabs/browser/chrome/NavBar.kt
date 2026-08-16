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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.nomercylabs.browser.R
import com.nomercylabs.browser.ui.IconButton
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.NavIcons
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.TvTextField
import com.nomercylabs.browser.ui.overscan

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
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
) {
    val palette: Palette = LocalPalette.current
    var typed: String by remember(currentUrl) { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                // weight, not fillMaxWidth: inside a Row the latter takes the
                // whole width and draws over the buttons beside it.
                modifier = Modifier.weight(1f),
            )

            IconButton(
                contentDescription = stringResource(R.string.nav_tabs),
                onClick = onTabs,
            ) { tint -> NavIcons.Tabs(tint) }

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
    }
}

private const val SCRIM_ALPHA: Float = 0.96f
private val PROGRESS_HEIGHT = 3.dp
