/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.browser

/**
 * Decides whether typed text is an address or a query.
 *
 * Getting this wrong is the most visible bug a browser can have: a domain that
 * lands on a search page, or a sentence that produces a DNS error. It is pure so
 * every awkward case is a test rather than a guess.
 */
object UrlOrSearch {

    sealed interface Destination {
        data class Url(val url: String) : Destination
        data class Search(val query: String) : Destination
        /** Refused rather than navigated, and refused rather than searched. */
        data object Blocked : Destination
        data object Nothing : Destination
    }

    private val SUPPORTED_SCHEMES = setOf("http", "https", "about", "data")

    /**
     * Schemes a browser must never follow from typed input. `javascript:` typed
     * into an address bar is the classic self-XSS delivery route, and `file:`
     * would expose the device's storage to a pasted string.
     */
    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "intent")

    private val HOST_WITH_PORT = Regex("^[^\\s/:]+:\\d{1,5}(/.*)?$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?(/.*)?$")
    private val HOSTNAME = Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:\\d{1,5})?(/.*)?$")

    fun resolve(input: String, searchTemplate: String): Destination {
        val trimmed: String = input.trim()
        if (trimmed.isEmpty()) return Destination.Nothing

        val scheme: String? = trimmed.substringBefore(':', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotEmpty() && trimmed.contains(':') && !trimmed.startsWith(":") }

        if (scheme != null && scheme in BLOCKED_SCHEMES) return Destination.Blocked
        if (scheme != null && scheme in SUPPORTED_SCHEMES) return Destination.Url(trimmed)

        // A space is the strongest signal available. No hostname contains one,
        // and almost every real query does.
        if (trimmed.contains(' ')) return Destination.Search(trimmed)

        if (trimmed.equals("localhost", ignoreCase = true) ||
            trimmed.startsWith("localhost:") ||
            trimmed.startsWith("localhost/")
        ) {
            return Destination.Url("http://$trimmed")
        }

        if (IPV4.matches(trimmed)) return Destination.Url("http://$trimmed")
        if (HOSTNAME.matches(trimmed)) return Destination.Url("https://$trimmed")
        if (HOST_WITH_PORT.matches(trimmed)) return Destination.Url("http://$trimmed")

        return Destination.Search(trimmed)
    }

    fun searchUrl(query: String, searchTemplate: String): String =
        searchTemplate.replace(QUERY_PLACEHOLDER, encode(query))

    /**
     * Deliberately not `URLEncoder`, which is a JVM class this pure module would
     * otherwise depend on, and which encodes spaces as `+` in a form-specific
     * way that is wrong for a path.
     */
    private fun encode(value: String): String = buildString {
        value.forEach { character ->
            when {
                // Deliberately not isLetterOrDigit(), which is Unicode-aware and
                // reports true for characters like é. The unreserved set in a
                // URL is ASCII only, so anything else must be percent-encoded.
                character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
                    character in UNRESERVED -> append(character)
                else -> character.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
                    append('%').append("%02X".format(byte))
                }
            }
        }
    }

    const val QUERY_PLACEHOLDER: String = "{query}"
    const val DUCKDUCKGO: String = "https://duckduckgo.com/?q=$QUERY_PLACEHOLDER"

    private const val UNRESERVED: String = "-_.~"
}
