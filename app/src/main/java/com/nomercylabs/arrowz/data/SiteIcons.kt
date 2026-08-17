/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.data

import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The picture a site tile draws, captured while browsing rather than fetched
 * when the grid appears.
 *
 * The tile drew two letters because fetching an icon is a network request per
 * tile at the moment the screen has to appear. That reasoning still holds, so
 * nothing here runs from the grid: a page that has been visited leaves its icon
 * on disk, and the grid only ever reads a local file. A site never visited keeps
 * its letters, which is also the honest answer — we have nothing better to draw.
 *
 * `/favicon.ico` is the last resort, not the first. It is 16 or 32 pixels square
 * on most of the web, and a tile is 180dp: stretched that far it looks worse
 * than the letters it replaced. `siteicon.js` ranks what the page declares and
 * only falls back to the small one.
 */
class SiteIcons(private val directory: File) {

    fun iconFor(origin: String): File? =
        fileFor(origin).takeIf { file -> file.isFile && file.length() > 0 }

    /**
     * Stores the icon a page declared. Runs off the main thread.
     *
     * Kept even when the bytes are small, because a site whose only icon is a
     * 16px favicon is still better identified by its own mark than by two
     * letters — the size ranking above is about preferring better, not about
     * refusing worse.
     */
    fun capture(origin: String, iconUrl: String) {
        if (origin.isEmpty() || iconUrl.isEmpty()) return
        val target: File = fileFor(origin)
        // Existence, not size. An empty file is the record that this origin was
        // tried and had nothing we can draw, which is what stops a site whose
        // only icon is a .ico from being fetched again on every single visit.
        if (target.exists()) return

        val bytes: ByteArray? = download(iconUrl)
        val usable: Boolean = bytes != null && bytes.size >= MINIMUM_BYTES && decodes(bytes)
        runCatching {
            directory.mkdirs()
            File(directory, target.name + ".part").let { part ->
                part.writeBytes(if (usable) bytes!! else ByteArray(0))
                part.renameTo(target)
            }
        }
    }

    /**
     * Whether the platform can draw these bytes at all.
     *
     * Hacker News is the case: it declares only `/favicon.ico`, which downloads
     * fine and which BitmapFactory cannot decode, so the tile went back to its
     * letters while a file sat on disk claiming otherwise. Checked before
     * storing, so a tile is never left waiting on a picture that will never
     * appear.
     */
    private fun decodes(bytes: ByteArray): Boolean = runCatching {
        // A real decode, not a bounds read. inJustDecodeBounds only parses the
        // header, so a truncated image passes it and then draws as nothing.
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
    }.getOrDefault(false)

    /** Dropped with the rest of a site's data, so clearing it clears this too. */
    fun forget(origin: String) {
        runCatching { fileFor(origin).delete() }
    }

    private fun fileFor(origin: String): File =
        File(directory, origin.replace(UNSAFE, "_") + ".img")

    private companion object {
        private val UNSAFE = Regex("[^A-Za-z0-9.-]")
        private const val TIMEOUT_MILLIS: Int = 10_000

        /** Below this it is a tracking pixel or an error page, not an icon. */
        private const val MINIMUM_BYTES: Int = 64
        private const val MAXIMUM_BYTES: Int = 512 * 1024

        fun download(url: String): ByteArray? = runCatching {
            val connection: HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                // Bounded on purpose: this is an arbitrary URL from an arbitrary
                // page, and an icon that is not an icon should cost us nothing.
                connection.inputStream.buffered().use { stream ->
                    // One byte past the cap on purpose. Reading exactly the cap
                    // cannot tell a file that ended from one that was cut, and a
                    // cut image still carries a valid header: github's share
                    // card stored at exactly 524288 bytes and passed a bounds
                    // check while being half an image.
                    val limit: Int = MAXIMUM_BYTES + 1
                    val buffer = ByteArray(limit)
                    var read = 0
                    while (read < limit) {
                        val n: Int = stream.read(buffer, read, limit - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read > MAXIMUM_BYTES) null else buffer.copyOf(read)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
