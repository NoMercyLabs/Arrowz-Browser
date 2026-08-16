/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.data

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

    private val helper = object : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE bookmarks (
                    id TEXT PRIMARY KEY NOT NULL,
                    url TEXT NOT NULL,
                    title TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX bookmarks_origin ON bookmarks(origin)")
            db.execSQL(
                """
                CREATE TABLE prefs (
                    key TEXT PRIMARY KEY NOT NULL,
                    value TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE visits (
                    origin TEXT PRIMARY KEY NOT NULL,
                    count INTEGER NOT NULL,
                    lastVisitedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
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

    override fun recordVisit(url: String) {
        val origin: String = HomeContent.originOf(url)
        if (origin.isEmpty()) return

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

    private companion object {
        const val DATABASE_NAME: String = "browser.db"
        const val VERSION: Int = 1
    }
}
