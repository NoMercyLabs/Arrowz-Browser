/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {

    private fun bookmark(origin: String, title: String) = Bookmark(
        id = origin,
        url = "https://$origin/",
        title = title,
        origin = origin,
        updatedAt = 0,
    )

    @Test
    fun anEmptyQueryOffersNothingAtAll() {
        assertEquals(
            emptyList<Suggestion>(),
            Suggestions.forQuery("  ", listOf(bookmark("wikipedia.org", "Wikipedia")), emptyList()),
        )
    }

    // The guarantee: whatever was typed stays reachable as a search even when
    // the rows above it are all near misses.
    @Test
    fun aSearchIsAlwaysTheLastOffer() {
        val results = Suggestions.forQuery("wiki", listOf(bookmark("wikipedia.org", "Wikipedia")), emptyList())
        assertEquals(SuggestionKind.Search, results.last().kind)
        assertTrue(results.last().url.endsWith("q=wiki"))
    }

    @Test
    fun aFavouriteOutranksAVisitThatMatchesEquallyWell() {
        val results = Suggestions.forQuery(
            query = "news",
            bookmarks = listOf(bookmark("news.example", "News")),
            visits = listOf(Visit("newsroom.example", count = 99, lastVisitedAt = 1)),
        )
        assertEquals(SuggestionKind.Favourite, results.first().kind)
    }

    // A host the query is a prefix of is what someone typing an address means,
    // so it must beat a title that merely contains the same letters.
    @Test
    fun anOriginPrefixBeatsATitleThatOnlyContainsTheQuery() {
        val results = Suggestions.forQuery(
            query = "arch",
            bookmarks = listOf(
                bookmark("example.org", "Search the archive"),
                bookmark("archlinux.org", "Arch Linux"),
            ),
            visits = emptyList(),
        )
        assertEquals("archlinux.org", results.first().subtitle.let(HomeContent::originOf))
    }

    @Test
    fun typingAnAddressOffersItAsADestinationFirst() {
        val results = Suggestions.forQuery("example.org/path", emptyList(), emptyList())
        assertEquals(SuggestionKind.Destination, results.first().kind)
        assertEquals("https://example.org/path", results.first().url)
    }

    // Otherwise typing a favourite's address shows that site twice, once as a
    // literal destination and once as the favourite it already is.
    @Test
    fun aDestinationAlreadyRememberedIsNotOfferedTwice() {
        val results = Suggestions.forQuery(
            query = "example.org",
            bookmarks = listOf(bookmark("example.org", "Example")),
            visits = emptyList(),
        )
        assertEquals(listOf(SuggestionKind.Favourite, SuggestionKind.Search), results.map { it.kind })
    }

    @Test
    fun theSameSiteFavouritedAndVisitedIsOneRow() {
        val results = Suggestions.forQuery(
            query = "example",
            bookmarks = listOf(bookmark("example.org", "Example")),
            visits = listOf(Visit("example.org", count = 4, lastVisitedAt = 1)),
        )
        assertEquals(listOf(SuggestionKind.Favourite, SuggestionKind.Search), results.map { it.kind })
    }

    // A list longer than the screen is a list nobody reads, and the search row
    // is part of the budget rather than an extra beyond it.
    @Test
    fun theListNeverGrowsPastItsLimit() {
        val visits = (1..20).map { index -> Visit("site$index.example", count = index, lastVisitedAt = 1) }
        val results = Suggestions.forQuery("site", emptyList(), visits, limit = 5)
        assertEquals(5, results.size)
        assertEquals(SuggestionKind.Search, results.last().kind)
    }

    @Test
    fun matchingIgnoresCase() {
        val results = Suggestions.forQuery("WIKI", listOf(bookmark("wikipedia.org", "Wikipedia")), emptyList())
        assertEquals(SuggestionKind.Favourite, results.first().kind)
    }
}
