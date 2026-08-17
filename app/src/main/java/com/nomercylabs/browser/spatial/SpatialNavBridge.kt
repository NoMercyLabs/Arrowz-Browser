/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

import android.webkit.WebView
import com.nomercylabs.browser.input.RemoteKey

/**
 * Drives [SpatialSearch] against a live page.
 *
 * Every decision stays in Kotlin. The page reports geometry and applies focus,
 * and nothing about where a press goes is computed inside a document we do not
 * control.
 *
 * The asynchrony is the awkward part: reading geometry is a round trip through
 * `evaluateJavascript`, so a press cannot be answered synchronously. It is
 * answered on the next callback instead, which on a page of any size is a
 * frame or two — and holding a direction is a repeat of presses rather than
 * one long one, so the delay does not compound.
 */
class SpatialNavBridge(private val webView: () -> WebView?) {

    /**
     * The last element focused inside each section, so leaving a row or grid
     * and coming back returns to where the viewer was. Without it a long grid
     * is unusable: every re-entry starts at the first item again.
     */
    private val sectionMemory = mutableMapOf<String, String>()

    private var lastFocusedId: String = ""

    fun applyRingStyle(colorCss: String, widthPx: Int, radiusPx: Int) {
        evaluate("window.__nmSpatial && window.__nmSpatial.style('$colorCss', $widthPx, $radiusPx)")
    }

    /**
     * Answers one directional press. [onScroll] runs when the winner is off
     * screen, [onLeavePage] when the page has nothing left in that direction —
     * the chrome takes the key rather than the press doing nothing.
     */
    fun move(
        direction: RemoteKey,
        onScroll: (dx: Int, dy: Int) -> Unit,
        onLeavePage: () -> Unit,
    ) {
        readSnapshot { snapshot ->
            if (snapshot == null || snapshot.elements.isEmpty()) {
                onLeavePage()
                return@readSnapshot
            }

            val source: Rect = sourceRect(snapshot)
            val fixedSource: Boolean = snapshot.elements
                .firstOrNull { element -> element.focusable.id == snapshot.focusedId }
                ?.isFixed ?: false

            // A sticky header and the body under it are separate groups. Mixing
            // them makes focus ping-pong between a nav bar and the article on
            // every press, which is the second most common complaint about
            // spatial navigation after the sideways jump.
            val candidates: List<Focusable> = snapshot.elements
                .filter { element -> element.isFixed == fixedSource }
                .map { element -> element.focusable }

            val canScroll: Boolean = when (direction) {
                RemoteKey.Down -> snapshot.scrollY + snapshot.viewport.height < snapshot.scrollHeight
                RemoteKey.Up -> snapshot.scrollY > 0
                else -> false
            }

            when (
                val result = SpatialSearch.search(
                    direction = direction,
                    source = source,
                    candidates = candidates,
                    viewport = snapshot.viewport,
                    canScroll = canScroll,
                )
            ) {
                is SpatialResult.Move -> focus(result.id)
                is SpatialResult.ScrollThenRetry -> onScroll(result.dx, result.dy)
                SpatialResult.LeavePage -> onLeavePage()
            }
        }
    }

    fun activate() {
        evaluate("window.__nmSpatial && window.__nmSpatial.activate()")
    }

    fun clear() {
        evaluate("window.__nmSpatial && window.__nmSpatial.clear()")
        lastFocusedId = ""
    }

    /** Entering a page with nothing focused: start where the viewer left off in
     *  this section if we know, and at the top of the document otherwise. */
    fun focusFirst(sectionKey: String) {
        readSnapshot { snapshot ->
            if (snapshot == null) return@readSnapshot
            val remembered: String? = sectionMemory[sectionKey]
            val target: String? = remembered?.takeIf { id ->
                snapshot.elements.any { element -> element.focusable.id == id }
            } ?: snapshot.elements.minByOrNull { it.focusable.documentOrder }?.focusable?.id
            if (target != null) focus(target, sectionKey)
        }
    }

    fun probe(onResult: (Navigability?) -> Unit) {
        evaluateForResult("window.__nmSpatial && window.__nmSpatial.probe()") { raw ->
            onResult(raw?.let(PageSnapshotParser::parseNavigability))
        }
    }

    private fun focus(id: String, sectionKey: String = "") {
        lastFocusedId = id
        if (sectionKey.isNotEmpty()) sectionMemory[sectionKey] = id
        evaluate("window.__nmSpatial && window.__nmSpatial.focus('$id')")
    }

    /**
     * Where the search starts. With nothing focused the top-left corner of the
     * viewport stands in, so the first press walks in from the edge rather than
     * being refused.
     */
    private fun sourceRect(snapshot: PageSnapshot): Rect =
        snapshot.elements
            .firstOrNull { element -> element.focusable.id == snapshot.focusedId.ifEmpty { lastFocusedId } }
            ?.focusable
            ?.rect
            ?: Rect(0, 0, 1, 1)

    private fun readSnapshot(onResult: (PageSnapshot?) -> Unit) {
        evaluateForResult("window.__nmSpatial && window.__nmSpatial.collect()") { raw ->
            onResult(raw?.let(PageSnapshotParser::parse))
        }
    }

    private fun evaluate(script: String) {
        webView()?.evaluateJavascript(script, null)
    }

    /**
     * `evaluateJavascript` hands back a JSON *value*, so a string result
     * arrives quoted and escaped. Unwrapping it here keeps every caller from
     * discovering that separately.
     */
    private fun evaluateForResult(script: String, onResult: (String?) -> Unit) {
        val view: WebView = webView() ?: run {
            onResult(null)
            return
        }
        view.evaluateJavascript(script) { value ->
            onResult(unwrap(value))
        }
    }

    private fun unwrap(value: String?): String? {
        if (value == null || value == "null" || value == "false") return null
        if (!value.startsWith("\"")) return value
        return value
            .trim('"')
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
    }
}
