/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentTest {

    private fun bookmark(origin: String, updatedAt: Long, deletedAt: Long? = null) = Bookmark(
        id = origin,
        url = "https://$origin/",
        title = origin,
        origin = origin,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun originDropsSchemeWwwPathAndCase() {
        assertEquals("nomercy.tv", HomeContent.originOf("https://WWW.NoMercy.tv/movies?q=1"))
        assertEquals("192.168.2.80", HomeContent.originOf("http://192.168.2.80/admin"))
    }

    // A tombstone is how a delete survives sync. Showing one would be the delete
    // silently undoing itself.
    @Test
    fun tombstonedFavouritesAreNotShown() {
        val tiles = HomeContent.tiles(
            bookmarks = listOf(bookmark("kept.example", 2), bookmark("gone.example", 1, deletedAt = 9)),
            visits = emptyList(),
        )
        assertEquals(listOf("kept.example"), tiles.map { tile -> tile.origin })
    }

    @Test
    fun favouritesComeFirstAndNewestFirst() {
        val tiles = HomeContent.tiles(
            bookmarks = listOf(bookmark("older.example", 1), bookmark("newer.example", 5)),
            visits = listOf(Visit("visited.example", count = 99, lastVisitedAt = 1)),
        )
        assertEquals(
            listOf("newer.example", "older.example", "visited.example"),
            tiles.map { tile -> tile.origin },
        )
    }

    // The same tile twice on one screen is what makes a grid look unconsidered.
    @Test
    fun aFavouritedOriginIsNotRepeatedUnderMostVisited() {
        val tiles = HomeContent.tiles(
            bookmarks = listOf(bookmark("both.example", 1)),
            visits = listOf(Visit("both.example", count = 40, lastVisitedAt = 3)),
        )
        assertEquals(1, tiles.size)
        assertTrue(tiles.single().isFavourite)
    }

    @Test
    fun mostVisitedIsOrderedByCountAndCapped() {
        val visits = (1..12).map { index ->
            Visit("site$index.example", count = index, lastVisitedAt = index.toLong())
        }
        val tiles = HomeContent.tiles(bookmarks = emptyList(), visits = visits, mostVisitedLimit = 3)
        assertEquals(listOf("site12.example", "site11.example", "site10.example"), tiles.map { it.origin })
    }

    @Test
    fun nothingKeptAndNothingVisitedIsAnEmptyGridRatherThanAnError() {
        assertTrue(HomeContent.tiles(emptyList(), emptyList()).isEmpty())
    }
}
