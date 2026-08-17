/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.forms

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Native form editing, page side to app side.
 *
 * The page reports that a field took focus and describes it. Whether that opens
 * anything is decided here, in Kotlin, for the same reason spatial navigation
 * is split that way: a decision made inside a document we do not control is a
 * decision the document can change.
 *
 * The name matters. `proguard-rules.pro` keeps `@JavascriptInterface` members on
 * classes ending in `Bridge`, and a bridge R8 has stripped fails as silence
 * rather than as an error.
 */
class FormBridge(
    private val webView: () -> WebView?,
    private val now: () -> Long,
    /** The field to edit, when the viewer's own press is what focused it. */
    private val onOpenField: (FormField) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    /** Whatever the page last reported focusing, whether or not it opened a
     *  sheet. BACK needs to know a field holds focus even when nothing was
     *  interrupted by it. */
    var focusedField: FormField? = null
        private set

    /**
     * Whether anything on this page has been edited.
     *
     * Pushed by the page rather than asked for, because it is read while memory
     * pressure is being answered and a round trip through the page is exactly
     * what is unavailable then. A dirty tab is never suspended: `saveState` does
     * not reliably carry unsaved input, so the exemption is the only thing
     * between the system reclaiming memory and losing what somebody typed.
     */
    @Volatile
    var hasDirtyForm: Boolean = false
        private set

    private var activatedAtMillis: Long = 0L

    /**
     * Records that the viewer pressed OK or tapped. A field focus arriving
     * shortly after this is theirs; one arriving on its own is the page's, and
     * interrupting for that is what makes a browser raise a keyboard over every
     * home page it opens.
     */
    fun noteActivation() {
        activatedAtMillis = now()
    }

    @JavascriptInterface
    fun onFieldFocused(described: String) {
        val reportedAtMillis: Long = now()
        val field: FormField = FieldParser.parse(described) ?: return
        handler.post {
            focusedField = field
            if (FormOpenPolicy.shouldOpen(activatedAtMillis, reportedAtMillis)) {
                // Spent. Closing the sheet refocuses the field, and without
                // this that report would open it again.
                activatedAtMillis = 0L
                onOpenField(field)
            }
        }
    }

    @JavascriptInterface
    fun onDirty() {
        hasDirtyForm = true
    }

    @JavascriptInterface
    fun onFieldBlurred() {
        handler.post { focusedField = null }
    }

    fun commit(id: String, value: String) {
        val committed: FormField? = focusedField
        evaluate("window.__nmForms && window.__nmForms.commit('$id', ${value.asJsString()})")
        // The page puts focus back on the element it just wrote, so navigation
        // carries on from the field rather than the top of the document. That
        // refocus is deliberately silent — it must not read as the viewer
        // choosing to edit again — so the record is restored here instead.
        handler.post { focusedField = committed }
    }

    fun select(id: String, optionIndex: Int) {
        evaluate("window.__nmForms && window.__nmForms.select('$id', $optionIndex)")
    }

    /** BACK with a field focused releases it and does nothing else, which is how
     *  every native app on the platform behaves. */
    fun blurFocusedField() {
        focusedField = null
        evaluate("window.__nmForms && window.__nmForms.blur()")
    }

    /** A navigation is a new document, and the old page's field and edits went
     *  with it. Carrying either over exempts a tab from suspension forever. */
    fun forgetPage() {
        focusedField = null
        activatedAtMillis = 0L
        hasDirtyForm = false
    }

    private fun evaluate(script: String) {
        webView()?.evaluateJavascript(script, null)
    }

    private companion object {
        /**
         * JavaScript ends a string literal on these two as well as on a newline,
         * and text copied out of a web page carries them where nothing visible
         * suggests a break at all. Built from their code points rather than
         * written out, because both are invisible in a source file and a reader
         * cannot tell a broken escape from a working one.
         */
        private val LINE_SEPARATOR: String = Char(0x2028).toString()
        private val PARAGRAPH_SEPARATOR: String = Char(0x2029).toString()

        /**
         * A value goes into a script as a literal, so anything the viewer can
         * type has to survive being one. A quote or a newline dictated into a
         * field would otherwise end the string and run whatever followed as
         * code, in the page's own origin.
         */
        fun String.asJsString(): String {
            val escaped: String = this
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace(LINE_SEPARATOR, "\\u2028")
                .replace(PARAGRAPH_SEPARATOR, "\\u2029")
            return "'$escaped'"
        }
    }
}
