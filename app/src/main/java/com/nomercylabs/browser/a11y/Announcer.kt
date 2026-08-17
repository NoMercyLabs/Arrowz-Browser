/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

/**
 * Says out loud what the screen changed to.
 *
 * A page finishing, a navigation failing, a tab switching: all four are visual
 * events with no announcement of their own, which makes them invisible to
 * somebody listening rather than watching.
 *
 * This holds the policy, never the words. The words are in `strings.xml` like
 * every other user-facing string.
 */
class Announcer(
    private val isActive: () -> Boolean,
    private val speak: (CharSequence) -> Unit,
) {

    private var lastSpoken: String = ""

    /**
     * The repeat guard is not politeness. Progress arrives several times per
     * load and every report carries the same title, so one page load announced
     * the same sentence four times without it.
     */
    fun announce(text: String) {
        if (!isActive() || text.isBlank() || text == lastSpoken) return
        lastSpoken = text
        speak(text)
    }

    /** A new document, so the last thing said is no longer what is on screen and
     *  must not suppress an identical announcement about a different page. */
    fun forgetLast() {
        lastSpoken = ""
    }

    companion object {
        /**
         * What a page is called out loud.
         *
         * A page that failed to load usually has no title at all, which is
         * exactly when an announcement matters most — so the host stands in, and
         * an address with neither is announced as nothing rather than as an
         * empty string the reader would say as silence.
         */
        fun nameFor(title: String, host: String): String =
            title.trim().ifEmpty { host.trim() }
    }
}
