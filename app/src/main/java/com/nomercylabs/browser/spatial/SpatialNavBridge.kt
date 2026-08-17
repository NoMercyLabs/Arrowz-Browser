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
     * Where focus was standing, in page coordinates, for when the element it
     * was standing on stops existing.
     *
     * A framework that re-renders replaces the node and takes our marker with
     * it, so the id we remember matches nothing in the next snapshot. Looking it
     * up and finding nothing used to fall back to a 1x1 box in the top-left
     * corner, which is not "no source" — it is a source, at the top of the
     * document, so the next press moved focus to the first link on the page.
     * Measured mid-article on Wikipedia: a walk eleven controls deep jumped back
     * to the header without anybody pressing anything that means "go up".
     */
    private var lastFocusedRect: Rect? = null
    private var lastScrollX: Int = 0
    private var lastScrollY: Int = 0

    /**
     * The last place focus stood that was part of the document, as opposed to
     * pinned over it.
     *
     * A pinned element has no position in the page's vertical order — a bar
     * glued to the bottom of the screen has nothing below it, ever, however far
     * the page scrolls — so a press leaving one cannot be measured from where it
     * sits. Measured on developer.android.com: focus entered the cookie bar on
     * the second press and every press after it moved nothing while the page
     * scrolled to its end underneath. This is where a press out of the pinned
     * group resumes from instead.
     */
    private var lastContentRect: Rect? = null
    private var lastContentScrollX: Int = 0
    private var lastContentScrollY: Int = 0

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
    ) = move(direction, onScroll, onLeavePage, attempt = 0)

    private fun move(
        direction: RemoteKey,
        onScroll: (dx: Int, dy: Int) -> Unit,
        onLeavePage: () -> Unit,
        attempt: Int,
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

            // Separate, but not sealed. A group with nothing in the direction of
            // travel has to hand the press to the other one, or a pinned header
            // is somewhere focus can enter and never leave: every press down
            // finds nothing among its own, scrolls the body underneath it
            // instead, and eventually gives the key to the chrome. Ordering is
            // what prevents the ping-pong — a group always answers for itself
            // first — so the two are not in tension.
            val otherGroup: List<Focusable> = snapshot.elements
                .filter { element -> element.isFixed != fixedSource }
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

            val ownGroup: SpatialResult = SpatialSearch.search(
                direction = direction,
                source = source,
                candidates = candidates,
                viewport = snapshot.viewport,
                canScroll = canScroll,
            )

            // Only a Move from the other group is taken. Its scroll and its
            // handover belong to a set of elements focus is not standing in, so
            // whether the page should scroll stays the source group's answer.
            /**
             * When a press crosses out of its own group, and why the two
             * directions are not symmetric.
             *
             * Scrolling never changes what a pinned group contains — that is
             * what pinned means — so a scroll asked for from a pinned element is
             * not an answer to anything, and the press has to cross. From the
             * content it is the opposite: a scroll is exactly the answer, and
             * crossing instead would put focus in the cookie bar on every
             * second press all the way down the page, which is measurably what
             * it did.
             *
             * So the content walks and scrolls among its own, and reaches the
             * pinned bar only where the page genuinely has nothing left that
             * way — the top for a header, the end for a footer, which is where
             * somebody reaching for either would press.
             */
            val crossable: Boolean = when {
                ownGroup is SpatialResult.Move -> false
                fixedSource -> true
                else -> ownGroup is SpatialResult.LeavePage
            }

            val result: SpatialResult = if (!crossable) {
                ownGroup
            } else {
                SpatialSearch.search(
                    direction = direction,
                    // From a pinned element the walk resumes where it left the
                    // document, because a bar glued to an edge has no place in
                    // the page's own vertical order.
                    source = if (fixedSource) contentSource(snapshot) ?: source else source,
                    candidates = otherGroup,
                    viewport = snapshot.viewport,
                    canScroll = canScroll,
                ).takeIf { crossed -> crossed is SpatialResult.Move } ?: ownGroup
            }

            when (result) {
                is SpatialResult.Move -> {
                    val winner: PageFocusable = snapshot.elements
                        .first { element -> element.focusable.id == result.id }
                    val landing: String = sections.resolve(sourceSection, winner, snapshot.elements)
                    focus(
                        landed = snapshot.elements.firstOrNull { element ->
                            element.focusable.id == landing
                        } ?: winner,
                        id = landing,
                        snapshot = snapshot,
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
                 * So it scrolls and looks again. If the looks run out without
                 * finding anything reachable, the page has nothing left in this
                 * direction whatever it claims about its own scroll extent, and
                 * the chrome takes the key rather than the press vanishing.
                 *
                 * Several looks rather than one, because the scroll is now
                 * measured against the next candidate rather than being a
                 * screenful: a gap wider than the screen — a full-bleed image,
                 * an advert, a long block of prose — takes more than one move to
                 * cross, and giving up after the first left focus stranded in
                 * the middle of a page that plainly had more below it.
                 */
                is SpatialResult.ScrollThenRetry -> {
                    onScroll(result.dx, result.dy)
                    if (attempt >= MAX_SCROLL_STEPS) {
                        onLeavePage()
                    } else {
                        /**
                         * After the scroll has actually happened, and asking the
                         * page rather than guessing at a timer.
                         *
                         * `scrollBy` is applied on the view's own next frame and
                         * reaches the page later still, so a retry that reads
                         * geometry immediately gets the position from before the
                         * scroll, asks for the same scroll again, and burns an
                         * attempt on nothing. A fixed delay only moves the guess:
                         * two frames is plenty on a small page and not nearly
                         * enough on an article of sixteen hundred controls, which
                         * is where the presses that appear to do nothing were.
                         */
                        awaitScroll(
                            vertical = result.dy != 0,
                            before = if (result.dy != 0) snapshot.scrollY else snapshot.scrollX,
                            waits = 0,
                        ) {
                            move(direction, onScroll, onLeavePage, attempt + 1)
                        }
                    }
                }
                SpatialResult.LeavePage -> onLeavePage()
            }
        }
    }

    /**
     * Presses the focused element with a real touch, falling back to the page's
     * own activation when the position cannot be read.
     *
     * A synthetic event is untrusted and a control is allowed to ignore it.
     * Measured on DuckDuckGo: ordinary links activated from this path while the
     * menu — a `display:none` checkbox driven from its label's pointer events —
     * never moved, whatever sequence was dispatched at it. A touch injected
     * into the view is trusted, and it is the same mechanism the pointer has
     * used since slice 3.
     */
    fun activate(onTap: (x: Float, y: Float) -> Unit) {
        evaluateForResult("window.__nmSpatial && window.__nmSpatial.focusedRect()") { raw ->
            val where: TapPoint? = raw?.let(PageSnapshotParser::parseTapPoint)
            val view: WebView? = webView()
            if (where == null || view == null || where.viewportWidth <= 0) {
                evaluate("window.__nmSpatial && window.__nmSpatial.activate()")
                return@evaluateForResult
            }

            // The page reports CSS pixels; the view is in device pixels, and on
            // this platform the two differ by the whole device scale.
            val scale: Float = view.width.toFloat() / where.viewportWidth
            onTap(where.x * scale, where.y * scale)
        }
    }

    /** A new page is a new set of sections. Carrying the old ones over sends
     *  focus at an element that no longer exists. */
    fun clear() {
        evaluate("window.__nmSpatial && window.__nmSpatial.clear()")
        lastFocusedId = ""
        lastSection = "document"
        lastFocusedRect = null
        lastScrollX = 0
        lastScrollY = 0
        lastContentRect = null
        lastContentScrollX = 0
        lastContentScrollY = 0
        hasModal = false
        sections.forget()
    }

    /** Entering a page with nothing focused: the first element in document
     *  order, which is where a reader would start. */
    fun focusFirst() {
        // Whatever the page focused for itself goes first. A search page that
        // focuses its own box also opens the suggestion list hanging off it,
        // and that list covers the results with something no press dismisses.
        evaluate("window.__nmSpatial && window.__nmSpatial.releasePageFocus()")
        readSnapshot { snapshot ->
            /**
             * Only when nothing of ours is standing anywhere, which is what the
             * name has always claimed and what the code did not check.
             *
             * The input-mode probe asks the page what shape it is for three
             * seconds after it loads, and settling on focus mode applies focus
             * mode — including when focus mode was already running and somebody
             * was four controls into the page. Measured on a Wikipedia article:
             * the walk reached the fourth link and was thrown back to the first
             * one, then walked the same four again, which reads exactly like the
             * page refusing to go anywhere.
             */
            val standing: String = lastFocusedId
            if (
                standing.isNotEmpty() &&
                snapshot?.elements?.any { element -> element.focusable.id == standing } == true
            ) {
                return@readSnapshot
            }

            val elements: List<PageFocusable> = snapshot?.elements.orEmpty()

            /**
             * The first thing in the content, which is not the first thing in
             * the document.
             *
             * Document order puts a consent bar, a skip link or a pinned
             * toolbar first on a great many sites, and a pinned bar is the one
             * place focus can enter and never leave: it sits at the bottom of
             * the screen by definition, so nothing is ever below it, and every
             * press down finds nothing however far the page scrolls. Measured on
             * developer.android.com: the walk began in the cookie bar and
             * fourteen presses moved nothing at all while the page scrolled to
             * its end underneath.
             *
             * So the pinned elements are passed over on the way in. They are
             * still reachable — they are on screen and the search crosses into
             * them — but they are not where somebody is put when a page opens.
             */
            val first: PageFocusable = elements
                .filter { element -> !element.isFixed && isOnScreen(element.focusable.rect, snapshot) }
                .minByOrNull { element -> element.focusable.documentOrder }
                ?: elements.filter { element -> !element.isFixed }
                    .minByOrNull { element -> element.focusable.documentOrder }
                ?: elements.minByOrNull { element -> element.focusable.documentOrder }
                ?: return@readSnapshot
            focus(first, first.focusable.id, snapshot)
        }
    }

    fun probe(onResult: (Navigability?) -> Unit) {
        evaluateForResult("window.__nmSpatial && window.__nmSpatial.probe()") { raw ->
            onResult(raw?.let(PageSnapshotParser::parseNavigability))
        }
    }

    /**
     * Waits for the page to report the scroll it was asked for, then continues.
     *
     * Polling `window.scrollY` rather than `collect()` on purpose: the position
     * is one number, where a snapshot walks every focusable on the page and
     * computes a style for each. Asking sixteen hundred times to find out
     * whether the view has moved yet is how a retry becomes slower than the
     * press that triggered it.
     *
     * Gives up waiting after a fixed number of looks and continues anyway, so a
     * page that refuses to scroll still reaches the search, finds nothing new,
     * and hands the key to the chrome.
     */
    private fun awaitScroll(vertical: Boolean, before: Int, waits: Int, onSettled: () -> Unit) {
        val view: WebView = webView() ?: return onSettled()
        val axis: String = if (vertical) "window.scrollY" else "window.scrollX"
        evaluateForResult("Math.round($axis)") { raw ->
            val now: Int? = raw?.trim()?.toIntOrNull()
            if (now == null || now != before || waits >= MAX_SETTLE_POLLS) {
                onSettled()
            } else {
                view.postDelayed({ awaitScroll(vertical, before, waits + 1, onSettled) }, SCROLL_SETTLE_MILLIS)
            }
        }
    }

    private fun focus(landed: PageFocusable, id: String, snapshot: PageSnapshot?) {
        lastFocusedId = id
        lastSection = landed.section
        lastFocusedRect = landed.focusable.rect
        lastScrollX = snapshot?.scrollX ?: lastScrollX
        lastScrollY = snapshot?.scrollY ?: lastScrollY

        if (!landed.isFixed) {
            lastContentRect = landed.focusable.rect
            lastContentScrollX = lastScrollX
            lastContentScrollY = lastScrollY
        }

        sections.remember(landed.section, id)
        evaluate("window.__nmSpatial && window.__nmSpatial.focus('$id')")
    }

    /**
     * Where the search starts. With nothing focused the top-left corner of the
     * viewport stands in, so the first press walks in from the edge rather than
     * being refused.
     */
    /** Where the content walk was standing, in this snapshot's coordinates. */
    private fun contentSource(snapshot: PageSnapshot): Rect? {
        val remembered: Rect = lastContentRect ?: return null
        val dx: Int = lastContentScrollX - snapshot.scrollX
        val dy: Int = lastContentScrollY - snapshot.scrollY
        return Rect(
            left = remembered.left + dx,
            top = remembered.top + dy,
            right = remembered.right + dx,
            bottom = remembered.bottom + dy,
        )
    }

    private fun isOnScreen(rect: Rect, snapshot: PageSnapshot?): Boolean {
        val viewport: Rect = snapshot?.viewport ?: return false
        return rect.bottom > viewport.top && rect.top < viewport.bottom &&
            rect.right > viewport.left && rect.left < viewport.right
    }

    private fun sourceRect(snapshot: PageSnapshot): Rect {
        val standingOn: String = snapshot.focusedId.ifEmpty { lastFocusedId }
        snapshot.elements
            .firstOrNull { element -> element.focusable.id == standingOn }
            ?.let { element -> return element.focusable.rect }

        // The element focus was on no longer exists. Its last known box, moved
        // by however far the page has scrolled since, is still where the viewer
        // is looking — and it is the difference between the next press
        // continuing the walk and the next press starting the page over.
        val remembered: Rect = lastFocusedRect ?: return Rect(0, 0, 1, 1)
        val dx: Int = lastScrollX - snapshot.scrollX
        val dy: Int = lastScrollY - snapshot.scrollY
        return Rect(
            left = remembered.left + dx,
            top = remembered.top + dy,
            right = remembered.right + dx,
            bottom = remembered.bottom + dy,
        )
    }

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

    private companion object {
        /** Two frames at 60Hz, which is what a `scrollBy` needs before the
         *  geometry it produced can be read back. */
        const val SCROLL_SETTLE_MILLIS: Long = 32

        /** Six screenfuls, past which a direction genuinely has nothing in it
         *  and the chrome should have the key. */
        const val MAX_SCROLL_STEPS: Int = 6

        /** Roughly a quarter second of waiting for a scroll to be reported,
         *  after which the page is not going to move and the search should find
         *  that out rather than this waiting forever. */
        const val MAX_SETTLE_POLLS: Int = 8
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
