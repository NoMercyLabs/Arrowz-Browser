/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.forms

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the sheet needs to draw. [Select] renders as a list rather than as
 * anything resembling a dropdown, because a page-drawn dropdown is the widget a
 * D-pad most often cannot reach at all.
 */
enum class FieldKind { Text, Select, Editable }

/** Which leanback keyboard to raise. The page's `type` decides, so a `tel`
 *  field offers digits instead of a full alphabet at three metres. */
enum class FieldKeyboard { Text, Email, Number, Decimal, Phone, Url, Password }

data class FieldOption(val label: String, val value: String, val isSelected: Boolean)

data class FormField(
    val id: String,
    val kind: FieldKind,
    val label: String,
    val value: String,
    val keyboard: FieldKeyboard,
    val isRequired: Boolean,
    val isInvalid: Boolean,
    val maxLength: Int,
    val isMultiline: Boolean,
    val options: List<FieldOption>,
) {
    val selectedIndex: Int get() = options.indexOfFirst { option -> option.isSelected }
}

/**
 * Turns the page's description into a field, refusing malformed input rather
 * than throwing.
 *
 * The page is the one input here that cannot be trusted to be well formed — a
 * script can rename `JSON.stringify`, another can throw halfway through — and a
 * failure in the middle of a focus has to end as "no field", which leaves the
 * page's own editing in place rather than taking the browser down.
 */
object FieldParser {

    fun parse(raw: String?): FormField? {
        val json: JSONObject = runCatching { JSONObject(raw ?: return null) }.getOrNull() ?: return null

        val id: String = json.optString("id")
        if (id.isEmpty()) return null

        val kind: FieldKind = when (json.optString("kind")) {
            "text" -> FieldKind.Text
            "select" -> FieldKind.Select
            "editable" -> FieldKind.Editable
            else -> return null
        }

        return FormField(
            id = id,
            kind = kind,
            label = json.optString("label"),
            value = json.optString("value"),
            keyboard = keyboardFor(json.optString("inputType")),
            isRequired = json.optBoolean("required"),
            isInvalid = json.optBoolean("invalid"),
            maxLength = json.optInt("maxLength"),
            isMultiline = json.optBoolean("multiline"),
            options = optionsIn(json.optJSONArray("options")),
        )
    }

    /**
     * The page's `type` mapped to a keyboard. Anything unrecognised is text:
     * a keyboard that offers too much is usable, and one that offers only
     * digits for a field wanting a name is not.
     */
    fun keyboardFor(inputType: String): FieldKeyboard = when (inputType.lowercase()) {
        "email" -> FieldKeyboard.Email
        "tel" -> FieldKeyboard.Phone
        "url" -> FieldKeyboard.Url
        "password" -> FieldKeyboard.Password
        "number" -> FieldKeyboard.Decimal
        "date", "time", "datetime-local", "month", "week" -> FieldKeyboard.Number
        else -> FieldKeyboard.Text
    }

    private fun optionsIn(array: JSONArray?): List<FieldOption> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val option: JSONObject = array.optJSONObject(index) ?: return@mapNotNull null
            FieldOption(
                label = option.optString("label"),
                value = option.optString("value"),
                isSelected = option.optBoolean("selected"),
            )
        }
    }
}

/**
 * Whether a field taking focus should interrupt with a sheet.
 *
 * Only when the viewer caused it. A page that focuses its search box on load —
 * Google, DuckDuckGo, most of the web — would otherwise raise a keyboard over
 * itself on every visit, which is the behaviour that makes the existing
 * television browsers unpleasant to use.
 *
 * Focus arriving on its own is still worth recording: the page has a focused
 * field, which is what BACK needs to know. It just does not interrupt.
 */
object FormOpenPolicy {

    fun shouldOpen(activatedAtMillis: Long, reportedAtMillis: Long): Boolean {
        if (activatedAtMillis <= 0L) return false
        val elapsed: Long = reportedAtMillis - activatedAtMillis
        return elapsed in 0..WINDOW_MILLIS
    }

    /**
     * Long enough for a page to run its own focus handling between the press and
     * the report, short enough that a later autofocus is not mistaken for it.
     * Measured against pages that move focus through a framework, which is the
     * slow end of this.
     */
    const val WINDOW_MILLIS: Long = 900
}
