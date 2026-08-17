/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * What the chrome needs from storage. An interface so the home screen and the
 * favourite toggle can be exercised without a device, and so the SQLite details
 * stay in one file.
 */
interface BrowserStore {
    fun bookmarks(): List<Bookmark>
    fun visits(): List<Visit>
    fun addBookmark(url: String, title: String)
    fun removeBookmark(origin: String)
    fun recordVisit(url: String)

    /** The title arrives after the navigation does, so the two-argument form is
     *  what the browser actually calls once a page has named itself. */
    fun recordVisit(url: String, title: String)

    /** Individual pages, newest first, as opposed to the per-origin counters
     *  [visits] keeps for the home grid. */
    fun history(limit: Int = 200): List<HistoryEntry>
    fun clearHistory()

    /** A site's answer to one question, remembered so it is asked once. Null
     *  means never asked, which is not the same as denied. */
    fun sitePermission(origin: String, kind: String): String?
    fun setSitePermission(origin: String, kind: String, decision: String)

    /** Settings live in the same store as everything else, so they carry the
     *  same identity fields and travel with a sync when one arrives. */
    fun preference(key: String): String?
    fun setPreference(key: String, value: String)
}

/**
 * SQLite through the framework helper rather than Room.
 *
 * The plan named Room. Two tables and five statements do not need an annotation
 * processor, and the property the plan actually wanted — rows that can describe
 * themselves to a sync that arrives later — is a matter of columns, which this
 * has: stable ids, `updatedAt`, and tombstones instead of deletes.
 */
class SqliteBrowserStore(context: Context) : BrowserStore {

    /**
     * One schema, applied identically on a first install and on an upgrade.
     *
     * The two used to be separate lists, and the failure that produced was
     * silent and total: `prefs` was added to the create path without a version
     * bump, so every device that had ever run the browser before kept a database
     * without it, `onUpgrade` never ran, and the app crashed on launch reading a
     * table that existed only for people installing it for the first time.
     * Measured on the 8000, which had the older database.
     *
     * `IF NOT EXISTS` on every statement is what makes the two paths the same
     * one. Adding a table is now a single edit that reaches an existing
     * television as well as a new one, and forgetting the version bump costs
     * nothing.
     */
    private val helper = object : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) = applySchema(db)

        // Added, never rebuilt. Somebody's television already holds their
        // favourites and their most-visited counts, and a drop-and-recreate
        // upgrade would take both away to add a table beside them.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            applySchema(db)

        // A database from a build newer than this one is missing nothing we
        // need, and refusing to open it — the platform default — is a crash
        // loop after a downgrade.
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            applySchema(db)

        private fun applySchema(db: SQLiteDatabase) =
            SCHEMA.forEach { statement -> db.execSQL(statement) }
    }

    override fun bookmarks(): List<Bookmark> {
        val rows = mutableListOf<Bookmark>()
        helper.readableDatabase.rawQuery(
            "SELECT id, url, title, origin, updatedAt, deletedAt FROM bookmarks WHERE deletedAt IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += Bookmark(
                    id = cursor.getString(0),
                    url = cursor.getString(1),
                    title = cursor.getString(2),
                    origin = cursor.getString(3),
                    updatedAt = cursor.getLong(4),
                    deletedAt = if (cursor.isNull(5)) null else cursor.getLong(5),
                )
            }
        }
        return rows
    }

    override fun visits(): List<Visit> {
        val rows = mutableListOf<Visit>()
        helper.readableDatabase.rawQuery(
            "SELECT origin, count, lastVisitedAt FROM visits ORDER BY count DESC",
            null,
        ).use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                rows += Visit(cursor.getString(0), cursor.getInt(1), cursor.getLong(2))
            }
        }
        return rows
    }

    /** Re-favouriting an origin revives its tombstone rather than inserting a
     *  second row, so the unique index stays true and sync sees one identity. */
    override fun addBookmark(url: String, title: String) {
        val origin: String = HomeContent.originOf(url)
        val values = ContentValues().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("url", url)
            put("title", title)
            put("origin", origin)
            put("updatedAt", System.currentTimeMillis())
            putNull("deletedAt")
        }
        val database: SQLiteDatabase = helper.writableDatabase
        val updated: Int = database.update(
            "bookmarks",
            ContentValues().apply {
                put("url", url)
                put("title", title)
                put("updatedAt", System.currentTimeMillis())
                putNull("deletedAt")
            },
            "origin = ?",
            arrayOf(origin),
        )
        if (updated == 0) database.insert("bookmarks", null, values)
    }

    override fun removeBookmark(origin: String) {
        helper.writableDatabase.update(
            "bookmarks",
            ContentValues().apply {
                put("deletedAt", System.currentTimeMillis())
                put("updatedAt", System.currentTimeMillis())
            },
            "origin = ?",
            arrayOf(origin),
        )
    }

    override fun recordVisit(url: String) = recordVisit(url, title = "")

    override fun recordVisit(url: String, title: String) {
        val origin: String = HomeContent.originOf(url)
        if (origin.isEmpty()) return

        helper.writableDatabase.execSQL(
            "INSERT INTO history(id, url, title, origin, visitedAt) VALUES(?, ?, ?, ?, ?)",
            arrayOf<Any>(
                java.util.UUID.randomUUID().toString(),
                url,
                title,
                origin,
                System.currentTimeMillis(),
            ),
        )

        helper.writableDatabase.execSQL(
            """
            INSERT INTO visits(origin, count, lastVisitedAt) VALUES(?, 1, ?)
            ON CONFLICT(origin) DO UPDATE SET count = count + 1, lastVisitedAt = excluded.lastVisitedAt
            """.trimIndent(),
            arrayOf<Any>(origin, System.currentTimeMillis()),
        )
    }

    override fun preference(key: String): String? {
        helper.readableDatabase.rawQuery("SELECT value FROM prefs WHERE key = ?", arrayOf(key))
            .use { cursor ->
                return if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    override fun setPreference(key: String, value: String) {
        helper.writableDatabase.execSQL(
            """
            INSERT INTO prefs(key, value, updatedAt) VALUES(?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value, updatedAt = excluded.updatedAt
            """.trimIndent(),
            arrayOf<Any>(key, value, System.currentTimeMillis()),
        )
    }

    override fun history(limit: Int): List<HistoryEntry> {
        val rows = mutableListOf<HistoryEntry>()
        helper.readableDatabase.rawQuery(
            "SELECT id, url, title, origin, visitedAt FROM history ORDER BY visitedAt DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += HistoryEntry(
                    id = cursor.getString(0),
                    url = cursor.getString(1),
                    title = cursor.getString(2),
                    origin = cursor.getString(3),
                    visitedAt = cursor.getLong(4),
                )
            }
        }
        return rows
    }

    override fun clearHistory() {
        helper.writableDatabase.delete("history", null, null)
        helper.writableDatabase.delete("visits", null, null)
    }

    override fun sitePermission(origin: String, kind: String): String? {
        helper.readableDatabase.rawQuery(
            "SELECT decision FROM site_permissions WHERE origin = ? AND kind = ?",
            arrayOf(origin, kind),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    override fun setSitePermission(origin: String, kind: String, decision: String) {
        helper.writableDatabase.execSQL(
            """
            INSERT INTO site_permissions(origin, kind, decision, updatedAt) VALUES(?, ?, ?, ?)
            ON CONFLICT(origin, kind) DO UPDATE SET
                decision = excluded.decision, updatedAt = excluded.updatedAt
            """.trimIndent(),
            arrayOf<Any>(origin, kind, decision, System.currentTimeMillis()),
        )
    }

    private companion object {
        const val DATABASE_NAME: String = "browser.db"

        /** Bumped so a device that already holds a database is offered the
         *  schema at all. Without a bump the platform never calls us. */
        const val VERSION: Int = 3

        val SCHEMA: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id TEXT PRIMARY KEY NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                origin TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER
            )
            """.trimIndent(),
            "CREATE UNIQUE INDEX IF NOT EXISTS bookmarks_origin ON bookmarks(origin)",
            """
            CREATE TABLE IF NOT EXISTS prefs (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS visits (
                origin TEXT PRIMARY KEY NOT NULL,
                count INTEGER NOT NULL,
                lastVisitedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS history (
                id TEXT PRIMARY KEY NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                origin TEXT NOT NULL,
                visitedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS site_permissions (
                origin TEXT NOT NULL,
                kind TEXT NOT NULL,
                decision TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY (origin, kind)
            )
            """.trimIndent(),
        )
    }
}
