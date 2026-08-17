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

    private val sections = SectionMemory()

    private var lastFocusedId: String = ""
    private var lastSection: String = "document"

    /**
     * Whether the page had a dialog in front the last time it was asked.
     *
     * Cached because BACK has to decide in the moment and asking the page costs
     * a round trip. Refreshed on every move, which is once per press.
     */
    var hasModal: Boolean = false
        private set

    /** Escape, which is the one thing every dialog implementation listens for,
     *  including `<dialog>`'s own. Returns nothing; the next press finds out. */
    fun dismissModal() {
        evaluate("window.__nmSpatial && window.__nmSpatial.dismissModal()")
        hasModal = false
    }

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
    ) = move(direction, onScroll, onLeavePage, isRetry = false)

    private fun move(
        direction: RemoteKey,
        onScroll: (dx: Int, dy: Int) -> Unit,
        onLeavePage: () -> Unit,
        isRetry: Boolean,
    ) {
        readSnapshot { snapshot ->
            hasModal = snapshot?.hasModal == true
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

            // Horizontal too. A page that scrolls sideways is rare, but
            // treating LEFT and RIGHT as unscrollable meant a winner past the
            // edge was focused where nobody could see it.
            val canScroll: Boolean = when (direction) {
                RemoteKey.Down -> snapshot.scrollY + snapshot.viewport.height < snapshot.scrollHeight
                RemoteKey.Up -> snapshot.scrollY > 0
                RemoteKey.Right -> snapshot.scrollX + snapshot.viewport.width < snapshot.scrollWidth
                RemoteKey.Left -> snapshot.scrollX > 0
                else -> false
            }

            val sourceSection: String = snapshot.elements
                .firstOrNull { element -> element.focusable.id == snapshot.focusedId }
                ?.section ?: lastSection

            when (
                val result = SpatialSearch.search(
                    direction = direction,
                    source = source,
                    candidates = candidates,
                    viewport = snapshot.viewport,
                    canScroll = canScroll,
                )
            ) {
                is SpatialResult.Move -> {
                    val winner: PageFocusable = snapshot.elements
                        .first { element -> element.focusable.id == result.id }
                    focus(
                        id = sections.resolve(sourceSection, winner, snapshot.elements),
                        section = winner.section,
                    )
                }
                /**
                 * The name promised a retry and the code never made one.
                 *
                 * Measured on the 8010: focus sat on DuckDuckGo's search field
                 * and three presses in a row moved nothing at all — the search
                 * asked for a scroll every time, the scroll changed nothing,
                 * and no second look was ever taken. From the outside that is a
                 * dead remote, which is the one failure this whole input model
                 * exists to prevent.
                 *
                 * So it scrolls and looks again, once. If that second look also
                 * finds nothing reachable, the page has nothing left in this
                 * direction whatever it claims about its own scroll extent, and
                 * the chrome takes the key rather than the press vanishing.
                 */
                is SpatialResult.ScrollThenRetry -> {
                    onScroll(result.dx, result.dy)
                    if (isRetry) {
                        onLeavePage()
                    } else {
                        move(direction, onScroll, onLeavePage, isRetry = true)
                    }
                }
                SpatialResult.LeavePage -> onLeavePage()
            }
        }
    }

    fun activate() {
        evaluate("window.__nmSpatial && window.__nmSpatial.activate()")
    }

    /** A new page is a new set of sections. Carrying the old ones over sends
     *  focus at an element that no longer exists. */
    fun clear() {
        evaluate("window.__nmSpatial && window.__nmSpatial.clear()")
        lastFocusedId = ""
        lastSection = "document"
        hasModal = false
        sections.forget()
    }

    /** Entering a page with nothing focused: the first element in document
     *  order, which is where a reader would start. */
    fun focusFirst() {
        readSnapshot { snapshot ->
            val first: PageFocusable = snapshot?.elements
                ?.minByOrNull { element -> element.focusable.documentOrder }
                ?: return@readSnapshot
            focus(first.focusable.id, first.section)
        }
    }

    fun probe(onResult: (Navigability?) -> Unit) {
        evaluateForResult("window.__nmSpatial && window.__nmSpatial.probe()") { raw ->
            onResult(raw?.let(PageSnapshotParser::parseNavigability))
        }
    }

    private fun focus(id: String, section: String) {
        lastFocusedId = id
        lastSection = section
        sections.remember(section, id)
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
