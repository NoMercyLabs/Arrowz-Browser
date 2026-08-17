/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

import org.json.JSONObject

/** What the page reported: its focusables and where the viewport is. */
data class PageSnapshot(
    val elements: List<PageFocusable>,
    val viewport: Rect,
    val scrollY: Int,
    val scrollHeight: Int,
    val scrollX: Int = 0,
    val scrollWidth: Int = 0,
    val focusedId: String,
    /** A dialog is in front, by the page's own declaration. Focus stays inside
     *  it and BACK dismisses it before it means anything else. */
    val hasModal: Boolean = false,
)

/** Where to press, in the page's own CSS pixels, with the viewport it was
 *  measured against so the caller can scale it to the view. */
data class TapPoint(val x: Int, val y: Int, val viewportWidth: Int, val viewportHeight: Int)

/** A focusable, plus whether it is pinned to the viewport and which row or
 *  grid it belongs to. */
data class PageFocusable(
    val focusable: Focusable,
    val isFixed: Boolean,
    val section: String = "document",
)

/**
 * Parsing kept away from both the search and the bridge.
 *
 * The page is the one part of this that cannot be trusted to be well formed —
 * a site can rename `document.querySelectorAll`, a script can throw halfway
 * through, and an injected result comes back as a JSON string inside a JSON
 * string. Failure returns an empty snapshot, which the caller reads as "no
 * candidates" and falls back to the pointer.
 */
object PageSnapshotParser {

    fun parse(json: String): PageSnapshot? {
        val root: JSONObject = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val width: Int = root.optInt("viewportWidth")
        val height: Int = root.optInt("viewportHeight")
        if (width <= 0 || height <= 0) return null

        val array = root.optJSONArray("elements")
        val elements: MutableList<PageFocusable> = mutableListOf()
        for (index in 0 until (array?.length() ?: 0)) {
            val item: JSONObject = array?.optJSONObject(index) ?: continue
            val id: String = item.optString("id")
            if (id.isEmpty()) continue
            elements += PageFocusable(
                focusable = Focusable(
                    id = id,
                    rect = Rect(
                        left = item.optInt("left"),
                        top = item.optInt("top"),
                        right = item.optInt("right"),
                        bottom = item.optInt("bottom"),
                    ),
                    documentOrder = item.optInt("order"),
                ),
                isFixed = item.optBoolean("fixed"),
                section = item.optString("section").ifEmpty { "document" },
            )
        }

        return PageSnapshot(
            elements = elements,
            viewport = Rect(0, 0, width, height),
            scrollY = root.optInt("scrollY"),
            scrollHeight = root.optInt("scrollHeight"),
            scrollX = root.optInt("scrollX"),
            scrollWidth = root.optInt("scrollWidth"),
            focusedId = root.optString("focused"),
            hasModal = root.optBoolean("modal"),
        )
    }

    fun parseNavigability(json: String): Navigability? {
        val root: JSONObject = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return Navigability(
            total = root.optInt("total"),
            visible = root.optInt("visible"),
            viewportHeight = root.optInt("viewportHeight"),
            stealsFocus = root.optBoolean("stealsFocus"),
        )
    }

    /** Empty when nothing is focused, which is a legitimate answer rather than
     *  a failure: the caller falls back to the page's own activation. */
    fun parseTapPoint(json: String): TapPoint? {
        if (json.isEmpty()) return null
        val root: JSONObject = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val width: Int = root.optInt("viewportWidth")
        if (width <= 0) return null
        return TapPoint(
            x = root.optInt("x"),
            y = root.optInt("y"),
            viewportWidth = width,
            viewportHeight = root.optInt("viewportHeight"),
        )
    }
}
