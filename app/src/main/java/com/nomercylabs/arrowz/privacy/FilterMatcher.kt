/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.privacy

/**
 * Decides whether one request is blocked.
 *
 * The shape is the whole performance story. Roughly eighty thousand rules meet
 * roughly a hundred requests per page: testing every rule against every request
 * is eight million pattern walks on a processor that is already struggling with
 * the page. So rules are bucketed by a literal token, a request is tokenised the
 * same way, and only the buckets the request itself names are walked.
 */
class FilterMatcher(rules: List<NetworkRule>) {

    private val blockBuckets: Map<String, List<NetworkRule>>
    private val exceptionBuckets: Map<String, List<NetworkRule>>

    /** Rules with no literal run long enough to index. Every request pays for
     *  these, so the parser's job is to keep the list short. */
    private val blockCatchAll: List<NetworkRule>
    private val exceptionCatchAll: List<NetworkRule>

    init {
        val (exceptions, blocks) = rules.partition { rule -> rule.isException }
        blockBuckets = blocks.filter { rule -> rule.token.isNotEmpty() }.groupBy { rule -> rule.token }
        exceptionBuckets =
            exceptions.filter { rule -> rule.token.isNotEmpty() }.groupBy { rule -> rule.token }
        blockCatchAll = blocks.filter { rule -> rule.token.isEmpty() }
        exceptionCatchAll = exceptions.filter { rule -> rule.token.isEmpty() }
    }

    val size: Int get() = blockBuckets.values.sumOf { bucket -> bucket.size } +
        exceptionBuckets.values.sumOf { bucket -> bucket.size } +
        blockCatchAll.size + exceptionCatchAll.size

    /**
     * An exception is looked for first and it wins outright.
     *
     * That order is not an optimisation, it is the rule: a list author writing
     * `@@` is saying "whatever else matches here, do not block it", and a
     * blocker that checks blocks first and stops has broken a site the author
     * went out of their way to keep working.
     */
    fun blocks(
        url: String,
        pageHost: String,
        kind: ResourceKind,
        isThirdParty: Boolean,
    ): Boolean {
        // A page somebody navigated to is never blocked, whatever the lists say.
        // Blocking a main frame produces a blank screen with no explanation and
        // no way forward.
        if (kind == ResourceKind.Document) return false

        val tokens: List<String> = FilterParser.tokensIn(url)
        if (matches(exceptionBuckets, exceptionCatchAll, tokens, url, pageHost, kind, isThirdParty)) {
            return false
        }
        return matches(blockBuckets, blockCatchAll, tokens, url, pageHost, kind, isThirdParty)
    }

    private fun matches(
        buckets: Map<String, List<NetworkRule>>,
        catchAll: List<NetworkRule>,
        tokens: List<String>,
        url: String,
        pageHost: String,
        kind: ResourceKind,
        isThirdParty: Boolean,
    ): Boolean {
        catchAll.forEach { rule ->
            if (rule.applies(url, pageHost, kind, isThirdParty)) return true
        }
        tokens.forEach { token ->
            buckets[token]?.forEach { rule ->
                if (rule.applies(url, pageHost, kind, isThirdParty)) return true
            }
        }
        return false
    }
}

/** Whether this rule's conditions and pattern both hold for one request. */
fun NetworkRule.applies(
    url: String,
    pageHost: String,
    kind: ResourceKind,
    isThirdParty: Boolean,
): Boolean {
    if (thirdParty != null && thirdParty != isThirdParty) return false
    if (kinds.isNotEmpty() && kind !in kinds) return false

    if (excludeDomains.isNotEmpty() && excludeDomains.any { domain -> pageHost.isUnder(domain) }) {
        return false
    }
    if (includeDomains.isNotEmpty() && includeDomains.none { domain -> pageHost.isUnder(domain) }) {
        return false
    }

    return PatternWalk.matches(this, url)
}

/** `news.example.com` is under `example.com`, and `notexample.com` is not —
 *  which a plain `endsWith` gets wrong, and gets wrong in the direction that
 *  blocks somebody else's site. */
fun String.isUnder(domain: String): Boolean {
    if (this == domain) return true
    return endsWith(".$domain")
}

/**
 * The pattern language, walked rather than compiled.
 *
 * `*` is any run, `^` is a separator — anything that is not a letter, digit,
 * `_`, `-`, `.` or `%`, and the end of the URL counts as one. Everything else is
 * literal.
 */
private object PatternWalk {

    fun matches(rule: NetworkRule, url: String): Boolean {
        val starts: IntArray = when {
            rule.anchorDomain -> domainStarts(url)
            rule.anchorStart -> intArrayOf(0)
            else -> IntArray(url.length + 1) { index -> index }
        }

        starts.forEach { start ->
            val end: Int = walk(rule.pattern, url, start)
            if (end >= 0 && (!rule.anchorEnd || end == url.length)) return true
        }
        return false
    }

    /**
     * Where `||` is allowed to begin: after the scheme, and at each label
     * boundary inside the host, so `||example.com` matches
     * `https://cdn.example.com/x` as list authors expect and does not match
     * `https://notexample.com/`.
     */
    private fun domainStarts(url: String): IntArray {
        val schemeAt: Int = url.indexOf("://")
        val hostStart: Int = if (schemeAt >= 0) schemeAt + 3 else 0
        val hostEnd: Int = url.indexOfFirst(hostStart) { character ->
            character == '/' || character == '?' || character == '#'
        }

        val starts: MutableList<Int> = mutableListOf(hostStart)
        // Past a userinfo section, so `http://user@example.com` still anchors on
        // the host rather than only on the credentials.
        val atSign: Int = url.indexOf('@', hostStart)
        if (atSign in hostStart until hostEnd) starts += atSign + 1

        for (index in hostStart until hostEnd) {
            if (url[index] == '.') starts += index + 1
        }
        return starts.toIntArray()
    }

    private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (index in from until length) {
            if (predicate(this[index])) return index
        }
        return length
    }

    /** Returns where the match ended, or -1. Recursive only on `*`, which real
     *  rules carry at most a couple of. */
    private fun walk(pattern: String, url: String, start: Int): Int {
        var patternIndex = 0
        var urlIndex: Int = start

        while (patternIndex < pattern.length) {
            when (val expected: Char = pattern[patternIndex]) {
                '*' -> {
                    val rest: String = pattern.substring(patternIndex + 1)
                    if (rest.isEmpty()) return url.length
                    for (candidate in urlIndex..url.length) {
                        val end: Int = walk(rest, url, candidate)
                        if (end >= 0) return end
                    }
                    return -1
                }

                '^' -> {
                    // The end of the URL is a separator. Without this,
                    // `||ads.example.com^` never matches a request for that host
                    // with no path, which is most of them.
                    if (urlIndex == url.length) return urlIndex
                    if (!isSeparator(url[urlIndex])) return -1
                    urlIndex++
                }

                else -> {
                    if (urlIndex >= url.length || url[urlIndex] != expected) return -1
                    urlIndex++
                }
            }
            patternIndex++
        }
        return urlIndex
    }

    private fun isSeparator(character: Char): Boolean = when {
        character.isLetterOrDigit() && character.code < ASCII_CEILING -> false
        character == '_' || character == '-' || character == '.' || character == '%' -> false
        else -> true
    }

    private const val ASCII_CEILING: Int = 128
}
