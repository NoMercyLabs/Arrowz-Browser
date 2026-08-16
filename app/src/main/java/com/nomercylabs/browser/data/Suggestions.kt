/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.data

import com.nomercylabs.browser.browser.UrlOrSearch

enum class SuggestionKind { Destination, Favourite, History, Search }

data class Suggestion(
    val kind: SuggestionKind,
    val title: String,
    val subtitle: String,
    val url: String,
)

/**
 * What the address bar offers while a query is being typed.
 *
 * The point is not autocomplete for its own sake. Entering a URL on a remote is
 * the worst thing a television browser asks of anyone, so the goal is that four
 * or five presses reach a site that has been visited before, and the typing is
 * abandoned rather than finished.
 *
 * Pure, and separate from the drop-down that draws it: the ordering rules are
 * where this succeeds or fails, and they are testable without a device.
 */
object Suggestions {

    fun forQuery(
        query: String,
        bookmarks: List<Bookmark>,
        visits: List<Visit>,
        searchTemplate: String = UrlOrSearch.DUCKDUCKGO,
        limit: Int = DEFAULT_LIMIT,
    ): List<Suggestion> {
        val trimmed: String = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val needle: String = trimmed.lowercase()

        val favourites: List<Ranked> = bookmarks
            .filter { bookmark -> bookmark.deletedAt == null }
            .mapNotNull { bookmark ->
                rank(needle, bookmark.origin, bookmark.title)?.let { score ->
                    Ranked(
                        score,
                        Suggestion(
                            kind = SuggestionKind.Favourite,
                            title = bookmark.title.ifBlank { bookmark.origin },
                            subtitle = bookmark.url,
                            url = bookmark.url,
                        ),
                    )
                }
            }

        val history: List<Ranked> = visits.mapNotNull { visit ->
            rank(needle, visit.origin, visit.origin)?.let { score ->
                Ranked(
                    score,
                    Suggestion(
                        kind = SuggestionKind.History,
                        title = visit.origin,
                        subtitle = visit.origin,
                        url = "https://${visit.origin}/",
                    ),
                )
            }
        }

        // Favourites outrank history at equal match quality: a site kept on
        // purpose is a stronger statement of intent than one landed on twice.
        val remembered: List<Suggestion> = (favourites + history)
            .sortedByDescending { ranked -> ranked.score }
            .map { ranked -> ranked.suggestion }

        val head: List<Suggestion> =
            when (val destination = UrlOrSearch.resolve(trimmed, searchTemplate)) {
                // Typing an address that is already a favourite must not offer
                // the same row twice, so the literal destination is only shown
                // when nothing remembered already leads there.
                is UrlOrSearch.Destination.Url ->
                    if (remembered.any { same(it.url, destination.url) }) emptyList()
                    else listOf(
                        Suggestion(
                            kind = SuggestionKind.Destination,
                            title = trimmed,
                            subtitle = destination.url,
                            url = destination.url,
                        ),
                    )

                else -> emptyList()
            }

        // Always last, never absent: whatever was typed has to stay reachable
        // as a search even when the list above it is full of near misses.
        val search = Suggestion(
            kind = SuggestionKind.Search,
            title = trimmed,
            subtitle = HomeContent.originOf(searchTemplate),
            url = UrlOrSearch.searchUrl(trimmed, searchTemplate),
        )

        return (head + remembered)
            .distinctBy { suggestion -> key(suggestion.url) }
            .take((limit - 1).coerceAtLeast(0)) + search
    }

    /**
     * `https://a.example` and `https://a.example/` are one place, and a stored
     * favourite carries the slash while typed text does not, so comparing the
     * strings as written offers the same site twice.
     */
    private fun key(url: String): String = url.trimEnd('/')

    private fun same(one: String, other: String): Boolean = key(one) == key(other)

    /**
     * Higher is better, null is no match. A prefix beats an interior hit
     * because that is what someone typing an address is doing; matching the
     * origin beats matching the title because the origin is what they typed.
     */
    private fun rank(needle: String, origin: String, title: String): Int? {
        val host: String = origin.lowercase()
        val name: String = title.lowercase()
        return when {
            host.startsWith(needle) -> ORIGIN_PREFIX
            name.startsWith(needle) -> TITLE_PREFIX
            host.contains(needle) -> ORIGIN_CONTAINS
            name.contains(needle) -> TITLE_CONTAINS
            else -> null
        }
    }

    private data class Ranked(val score: Int, val suggestion: Suggestion)

    private const val ORIGIN_PREFIX: Int = 4
    private const val TITLE_PREFIX: Int = 3
    private const val ORIGIN_CONTAINS: Int = 2
    private const val TITLE_CONTAINS: Int = 1

    private const val DEFAULT_LIMIT: Int = 6
}
