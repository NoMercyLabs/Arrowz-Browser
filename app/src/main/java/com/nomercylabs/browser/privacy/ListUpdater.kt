/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.privacy

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the filter lists current, from their public upstreams and nowhere else.
 *
 * The absence of a NoMercyLabs endpoint here is the load-bearing part. A proxy
 * would be the most convenient way to accumulate a browsing-history dataset by
 * accident, and its absence is what makes "no data collected by the developer"
 * a checkable claim rather than a promise.
 */
class ListUpdater(
    private val cacheDirectory: File,
    private val now: () -> Long,
    private val readSeed: () -> String,
    private val fetch: (String) -> String? = ListUpdater::download,
) {

    /**
     * What is on disk, falling back to the seed that ships in the APK.
     *
     * The fallback is why a television is protected on its first page load
     * rather than after its first successful fetch — which on a box that is
     * switched off more than it is on could be days.
     */
    fun load(): FilterSet {
        val cached: List<File> = SOURCES
            .map { source -> File(cacheDirectory, source.fileName) }
            .filter { file -> file.isFile && file.length() > 0 }

        if (cached.isEmpty()) return FilterParser.parse(readSeed().lineSequence())

        return cached.fold(FilterParser.parse(readSeed().lineSequence())) { total, file ->
            total + FilterParser.parse(file.readText().lineSequence())
        }
    }

    /** Whether enough time has passed to be worth the traffic. Lists change
     *  daily and a television is not a browser somebody leaves open. */
    fun isDue(): Boolean {
        val stamp = File(cacheDirectory, STAMP_NAME)
        if (!stamp.isFile) return true
        return now() - stamp.lastModified() > UPDATE_INTERVAL_MILLIS
    }

    /**
     * A failed fetch leaves the previous list in place. The alternative —
     * truncating what is on disk and then failing — is a browser that quietly
     * stops protecting somebody because their network was down for a minute.
     */
    fun update(): Boolean {
        cacheDirectory.mkdirs()
        var anySucceeded = false

        SOURCES.forEach { source ->
            val body: String = fetch(source.url) ?: return@forEach
            if (body.isBlank()) return@forEach
            File(cacheDirectory, source.fileName).writeText(body)
            anySucceeded = true
        }

        if (anySucceeded) File(cacheDirectory, STAMP_NAME).writeText(now().toString())
        return anySucceeded
    }

    /** Where the lists come from, named here so the answer to "what does this
     *  app talk to" is one list in one file. */
    data class Source(val fileName: String, val url: String)

    companion object {
        val SOURCES: List<Source> = listOf(
            Source(
                fileName = "easylist.txt",
                url = "https://easylist.to/easylist/easylist.txt",
            ),
            Source(
                fileName = "easyprivacy.txt",
                url = "https://easylist.to/easylist/easyprivacy.txt",
            ),
            Source(
                fileName = "ublock-filters.txt",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            ),
            Source(
                fileName = "ublock-privacy.txt",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
            ),
        )

        private const val STAMP_NAME: String = "updated.at"
        private const val UPDATE_INTERVAL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
        private const val TIMEOUT_MILLIS: Int = 20_000

        /** Deliberately plain. A dependency here would be a dependency in the
         *  one part of the app whose whole claim is that it talks to nobody we
         *  control. */
        fun download(url: String): String? = runCatching {
            val connection: HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
