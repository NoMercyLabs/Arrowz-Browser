/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.browser

/**
 * A value goes into an injected script as a literal, so anything it can contain
 * has to survive being one. A quote or a newline dictated into a field would
 * otherwise end the string and run whatever followed as code, in the page's own
 * origin.
 *
 * Shared rather than owned by whichever module needed it first: a second copy of
 * an escaping rule is a second chance to get it wrong, and the one that is wrong
 * is the one nobody remembers exists.
 */
private val LINE_SEPARATOR: String = Char(0x2028).toString()
private val PARAGRAPH_SEPARATOR: String = Char(0x2029).toString()

/**
 * JavaScript ends a string literal on U+2028 and U+2029 as well as on a newline,
 * and text copied out of a web page carries them where nothing visible suggests
 * a break at all. Built from their code points rather than written out, because
 * both are invisible in a source file and a reader cannot tell a broken escape
 * from a working one.
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
