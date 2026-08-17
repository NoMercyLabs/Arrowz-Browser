/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.data

/**
 * A row the store keeps.
 *
 * The identity fields are here from the first version rather than added when
 * sync arrives: retrofitting stable ids onto rows that only ever had
 * autoincrement keys means a migration on somebody's television.
 */
data class Bookmark(
    /** Stable across devices, generated here. */
    val id: String,
    val url: String,
    val title: String,
    val origin: String,
    val updatedAt: Long,
    /** Set instead of deleting the row, so a delete can propagate rather than
     *  being undone by an older copy of the same row. */
    val deletedAt: Long? = null,
)

data class Visit(val origin: String, val count: Int, val lastVisitedAt: Long)

/** One page, as opposed to [Visit], which is one site. */
data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val origin: String,
    val visitedAt: Long,
)

/** What a tile draws. The letter and colour come from the origin, so a tile
 *  needs no network before it can appear. */
data class Tile(val title: String, val url: String, val origin: String, val isFavourite: Boolean)

/**
 * Turns what the store holds into what the home screen shows.
 *
 * Pure, because every interesting decision here is a rule about duplication and
 * ordering rather than a question about storage, and those are the ones that
 * make a grid feel considered or careless.
 */
object HomeContent {

    fun tiles(bookmarks: List<Bookmark>, visits: List<Visit>, mostVisitedLimit: Int = 8): List<Tile> {
        val live: List<Bookmark> = bookmarks.filter { bookmark -> bookmark.deletedAt == null }

        val favourites: List<Tile> = live
            .sortedByDescending { bookmark -> bookmark.updatedAt }
            .map { bookmark ->
                Tile(
                    title = bookmark.title.ifBlank { bookmark.origin },
                    url = bookmark.url,
                    origin = bookmark.origin,
                    isFavourite = true,
                )
            }

        val favouriteOrigins: Set<String> = favourites.map { tile -> tile.origin }.toSet()

        // A favourited origin appearing again under most visited is the same
        // tile twice on one screen, which is what makes these grids look
        // unconsidered.
        val mostVisited: List<Tile> = visits
            .filter { visit -> visit.origin !in favouriteOrigins }
            .sortedWith(compareByDescending<Visit> { visit -> visit.count }.thenByDescending { it.lastVisitedAt })
            .take(mostVisitedLimit)
            .map { visit ->
                Tile(
                    title = visit.origin,
                    url = "https://${visit.origin}/",
                    origin = visit.origin,
                    isFavourite = false,
                )
            }

        return favourites + mostVisited
    }

    /**
     * The host, without a leading `www.`, which is what a viewer recognises and
     * what makes twenty article pages on one site a single tile.
     */
    fun originOf(url: String): String {
        val withoutScheme: String = url.substringAfter("://", url)
        val host: String = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        // Lowercased before the prefix is stripped: a site linked as WWW. is the
        // same site, and comparing origins is how tiles are deduplicated.
        return host.lowercase().removePrefix("www.")
    }
}
