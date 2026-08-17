/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.chrome

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
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.browser.PermissionKind
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.Tokens
import com.nomercylabs.arrowz.ui.overscan

/** What a site asked for, and the two callbacks that answer it. */
data class PermissionAsk(
    val origin: String,
    val kinds: List<String>,
    val grant: () -> Unit,
    val deny: () -> Unit,
)

/**
 * The prompt for camera, microphone and location.
 *
 * Blocking is the first row and the one focus lands on. This device sits in a
 * living room and the person holding the remote may not have asked for the page
 * that is asking, so the safe answer must be the one a stray OK produces.
 */
@Composable
fun PermissionPrompt(
    ask: PermissionAsk,
    onAnswer: (allow: Boolean, remember: Boolean) -> Unit,
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
            text = stringResource(R.string.permission_title, ask.origin),
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
            modifier = Modifier.fillMaxWidth(),
        )
        val wanted: String = ask.kinds.map { kind -> labelFor(kind) }.joinToString(", ")
        BasicText(
            text = wanted,
            style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
        )

        ListRow(
            title = stringResource(R.string.permission_block),
            subtitle = "",
            selected = false,
            onClick = { onAnswer(false, true) },
            requestInitialFocus = true,
        )
        ListRow(
            title = stringResource(R.string.permission_allow_once),
            subtitle = "",
            selected = false,
            onClick = { onAnswer(true, false) },
        )
        ListRow(
            title = stringResource(R.string.permission_allow_always),
            subtitle = "",
            selected = false,
            onClick = { onAnswer(true, true) },
        )
    }
}

@Composable
private fun labelFor(kind: String): String = stringResource(
    when (kind) {
        PermissionKind.CAMERA -> R.string.permission_camera
        PermissionKind.MICROPHONE -> R.string.permission_microphone
        else -> R.string.permission_location
    },
)

private const val SCRIM_ALPHA: Float = 0.97f
