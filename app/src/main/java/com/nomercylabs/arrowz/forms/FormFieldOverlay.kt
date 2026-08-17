/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import com.nomercylabs.arrowz.R
import com.nomercylabs.arrowz.ui.IconButton
import com.nomercylabs.arrowz.ui.ListRow
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.NavIcons
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.Tokens
import com.nomercylabs.arrowz.ui.TvTextField
import com.nomercylabs.arrowz.ui.overscan

/**
 * Editing one web field in a native control.
 *
 * A web input on a television fails three ways at once: the leanback keyboard
 * covers the field it is editing so the viewer types blind, the caret is a few
 * pixels tall at three metres, and the page's own key handling assumes a mouse.
 * So the field is not edited in place. The page receives an ordinary `input`
 * and `change` pair afterwards and cannot tell the difference.
 */
@Composable
fun FormFieldOverlay(
    field: FormField,
    value: String,
    onValueChange: (String) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onCommit: () -> Unit,
    onVoice: () -> Unit,
) {
    val palette: Palette = LocalPalette.current
    val label: String = field.label.ifBlank { stringResource(R.string.form_field_generic) }

    val doneFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface.copy(alpha = SCRIM_ALPHA))
            .overscan()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = palette.onSurface, fontSize = Tokens.TextTitle),
            modifier = Modifier.fillMaxWidth(),
        )

        // Required and invalid are the page's own judgement, shown here rather
        // than discovered through a submit whose result the viewer cannot see.
        //
        // An empty field is not invalid, it is empty — but the page says
        // otherwise: `validity.valid` is false for any untouched required
        // field, so DuckDuckGo's search box opened reading "this page says that
        // is not valid yet" before a single character had been typed. Told off
        // for nothing is a bad way to meet a form.
        val note: String = when {
            field.isInvalid && value.isNotEmpty() -> stringResource(R.string.form_invalid)
            field.isRequired -> stringResource(R.string.form_required)
            else -> ""
        }
        if (note.isNotEmpty()) {
            BasicText(
                text = note,
                style = TextStyle(color = palette.onSurfaceMuted, fontSize = Tokens.TextBody),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            /**
             * DOWN out of the field is routed by name rather than by geometry.
             *
             * Measured on the 8000: after typing and pressing BACK to put the
             * keyboard away, DOWN did not reach Done and OK went back into the
             * field. There was no way to commit with the D-pad at all -- the only
             * press that left the sheet was BACK, which discards. The field and
             * the microphone sit in a Row inside a Column, and the search across
             * that boundary is ambiguous enough to find nothing.
             */
            TvTextField(
                value = value,
                onValueChange = onValueChange,
                onSubmit = onCommit,
                placeholder = label,
                contentDescription = label,
                editing = editing,
                onEditingChange = onEditingChange,
                requestInitialFocus = true,
                keyboardType = keyboardTypeFor(field.keyboard),
                isSecret = field.keyboard == FieldKeyboard.Password,
                modifier = Modifier
                    .weight(1f)
                    .focusProperties { down = doneFocus },
            )
            IconButton(
                contentDescription = stringResource(R.string.form_voice),
                onClick = onVoice,
                modifier = Modifier.focusProperties { down = doneFocus },
            ) { tint -> NavIcons.Mic(tint) }
        }

        // A row rather than a hint, because it has to be reachable: OK on a
        // field raises the keyboard, so there must be somewhere else to press
        // OK that means "finished".
        ListRow(
            title = stringResource(R.string.form_done),
            subtitle = "",
            selected = false,
            offered = true,
            onClick = onCommit,
            externalFocusRequester = doneFocus,
        )
    }
}

/**
 * The page's field type mapped onto a keyboard. Kept here rather than in the
 * model so the model stays free of Compose and can be tested without it.
 */
private fun keyboardTypeFor(keyboard: FieldKeyboard): KeyboardType = when (keyboard) {
    FieldKeyboard.Email -> KeyboardType.Email
    FieldKeyboard.Number -> KeyboardType.Number
    FieldKeyboard.Decimal -> KeyboardType.Decimal
    FieldKeyboard.Phone -> KeyboardType.Phone
    FieldKeyboard.Url -> KeyboardType.Uri
    FieldKeyboard.Password -> KeyboardType.Password
    FieldKeyboard.Text -> KeyboardType.Text
}

private const val SCRIM_ALPHA: Float = 0.97f
