/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.privacy

/**
 * What kind of thing was requested.
 *
 * WebView never says, so these are inferred rather than given. See
 * [ResourceKind.infer] for the guesswork and its limits.
 */
enum class ResourceKind {
    Document,
    Script,
    Stylesheet,
    Image,
    Font,
    Media,
    XmlHttpRequest,
    Other,
    ;

    companion object {
        /**
         * The `Accept` header first, because a browser states what it wants
         * there and a URL frequently does not: a script served from a path with
         * no extension is ordinary now.
         */
        fun infer(url: String, isMainFrame: Boolean, accept: String?): ResourceKind {
            if (isMainFrame) return Document

            val header: String = accept.orEmpty().lowercase()
            when {
                header.contains("text/css") -> return Stylesheet
                header.contains("image/") -> return Image
                header.contains("font/") || header.contains("application/font") -> return Font
                header.contains("video/") || header.contains("audio/") -> return Media
                header.startsWith("text/html") -> return Document
            }

            return fromExtension(url)
        }

        private fun fromExtension(url: String): ResourceKind {
            val path: String = url.substringBefore('?').substringBefore('#').lowercase()
            val extension: String = path.substringAfterLast('.', missingDelimiterValue = "")
            return when (extension) {
                "js", "mjs" -> Script
                "css" -> Stylesheet
                "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif" -> Image
                "woff", "woff2", "ttf", "otf", "eot" -> Font
                "mp4", "webm", "m4s", "ts", "mp3", "m4a", "ogg" -> Media
                "json" -> XmlHttpRequest
                else -> Other
            }
        }

        /** Adblock option names, which are not our enum names. */
        fun forOption(option: String): ResourceKind? = when (option) {
            "script" -> Script
            "stylesheet", "css" -> Stylesheet
            "image" -> Image
            "font" -> Font
            "media" -> Media
            "xmlhttprequest", "xhr" -> XmlHttpRequest
            "document", "main_frame" -> Document
            "other" -> Other
            else -> null
        }
    }
}

/**
 * One network rule, in the Adblock Plus subset the lists actually lean on.
 *
 * Deliberately data rather than a compiled pattern. Eighty thousand compiled
 * regexes cost real memory on a 2GB television, and the token index means only a
 * handful of these are ever walked per request.
 */
data class NetworkRule(
    val pattern: String,
    /** `@@`. An exception outranks every block, which is what keeps well-known
     *  sites working when a broad rule catches something they need. */
    val isException: Boolean = false,
    /** `||`, which matches at a domain boundary rather than anywhere. */
    val anchorDomain: Boolean = false,
    /** A leading `|`. */
    val anchorStart: Boolean = false,
    /** A trailing `|`. */
    val anchorEnd: Boolean = false,
    /** `third-party` or `~third-party`; null when the rule does not care. */
    val thirdParty: Boolean? = null,
    /** Empty means every kind. */
    val kinds: Set<ResourceKind> = emptySet(),
    /** `domain=a.com|b.com`. Empty means every page. */
    val includeDomains: Set<String> = emptySet(),
    /** `domain=~a.com`. */
    val excludeDomains: Set<String> = emptySet(),
    /** The literal run this rule is indexed under, or empty for the catch-all
     *  bucket that every request has to test. */
    val token: String = "",
)

/** One element-hiding rule: `example.com##.ad-banner`, or `##.ad` for every
 *  site. */
data class CosmeticRule(
    val selector: String,
    val domains: Set<String> = emptySet(),
    val excludeDomains: Set<String> = emptySet(),
    /** `#@#`, which un-hides a selector a broader rule hid. */
    val isException: Boolean = false,
)
