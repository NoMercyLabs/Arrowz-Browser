/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.ThemeMode
import com.nomercylabs.browser.ui.Tokens
import com.nomercylabs.browser.ui.TvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme(mode = ThemeMode.System) {
                ShellPlaceholder(palette = LocalPalette.current)
            }
        }
    }
}

@Composable
private fun ShellPlaceholder(palette: Palette) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.shell_placeholder_title),
            style = TextStyle(
                color = palette.accent,
                fontSize = Tokens.TextTitle,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        BasicText(
            text = stringResource(R.string.shell_placeholder_body),
            style = TextStyle(
                color = palette.onSurfaceMuted,
                fontSize = Tokens.TextBody,
            ),
        )
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ShellPreviewDark() {
    TvTheme(mode = ThemeMode.Dark) { ShellPlaceholder(palette = LocalPalette.current) }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ShellPreviewLight() {
    TvTheme(mode = ThemeMode.Light) { ShellPlaceholder(palette = LocalPalette.current) }
}
