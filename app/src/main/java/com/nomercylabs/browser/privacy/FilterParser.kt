/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.privacy

/**
 * Reads Adblock Plus filter syntax into [NetworkRule] and [CosmeticRule].
 *
 * Unsupported constructs are skipped rather than approximated. A rule we half
 * understand is worse than a rule we ignore: it blocks something the list author
 * did not ask us to block, on a page nobody is watching us break.
 */
object FilterParser {

    /** Rules we will not attempt. Regex rules are the big one — `/pattern/` is
     *  a per-rule regex, which is exactly the cost the token index exists to
     *  avoid — and procedural cosmetic rules need a query engine we do not have. */
    private val UNSUPPORTED_OPTIONS = setOf(
        "csp", "redirect", "redirect-rule", "removeparam", "replace", "urltransform",
    )

    fun parse(lines: Sequence<String>): FilterSet {
        val network: MutableList<NetworkRule> = mutableListOf()
        val cosmetic: MutableList<CosmeticRule> = mutableListOf()

        lines.forEach { raw ->
            val line: String = raw.trim()
            // `!` is a comment and `[` opens a list header. An empty line is
            // neither, and reading either as a rule matches everything.
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[')) return@forEach

            val cosmeticRule: CosmeticRule? = parseCosmetic(line)
            if (cosmeticRule != null) {
                cosmetic += cosmeticRule
                return@forEach
            }
            parseNetwork(line)?.let { rule -> network += rule }
        }

        return FilterSet(network, cosmetic)
    }

    private fun parseCosmetic(line: String): CosmeticRule? {
        val exceptionAt: Int = line.indexOf("#@#")
        val hideAt: Int = line.indexOf("##")
        val isException: Boolean = exceptionAt >= 0 && (hideAt < 0 || exceptionAt < hideAt)
        val separatorAt: Int = if (isException) exceptionAt else hideAt
        if (separatorAt < 0) return null

        val separatorLength: Int = if (isException) 3 else 2
        val selector: String = line.substring(separatorAt + separatorLength).trim()
        if (selector.isEmpty()) return null

        // Procedural rules — :has-text(), :xpath(), :style() — need an engine we
        // do not have, and a selector the browser cannot parse silently drops
        // every rule after it in the same sheet.
        if (selector.startsWith("+js") || selector.contains(":has-text(") ||
            selector.contains(":xpath(") || selector.contains(":style(") ||
            selector.contains(":matches-css")
        ) {
            return null
        }

        val domainField: String = line.substring(0, separatorAt)
        val included: MutableSet<String> = mutableSetOf()
        val excluded: MutableSet<String> = mutableSetOf()
        domainField.split(',')
            .map { domain -> domain.trim().lowercase() }
            .filter { domain -> domain.isNotEmpty() }
            .forEach { domain ->
                if (domain.startsWith('~')) excluded += domain.substring(1) else included += domain
            }

        return CosmeticRule(
            selector = selector,
            domains = included,
            excludeDomains = excluded,
            isException = isException,
        )
    }

    private fun parseNetwork(line: String): NetworkRule? {
        var body: String = line
        val isException: Boolean = body.startsWith("@@")
        if (isException) body = body.substring(2)

        // `/pattern/` is a regex rule, and supporting it would put a compiled
        // regex back in the hot path for a small minority of rules.
        //
        // The delimiters alone are not enough to recognise one: `/analytics/` is
        // an ordinary path substring and by far the more common shape. A regex
        // metacharacter has to be present as well, which is the same
        // disambiguation the lists themselves rely on.
        if (isRegexRule(body.substringBefore('$'))) return null

        var thirdParty: Boolean? = null
        val kinds: MutableSet<ResourceKind> = mutableSetOf()
        val includeDomains: MutableSet<String> = mutableSetOf()
        val excludeDomains: MutableSet<String> = mutableSetOf()

        val optionsAt: Int = body.lastIndexOf('$')
        if (optionsAt > 0) {
            val options: String = body.substring(optionsAt + 1)
            body = body.substring(0, optionsAt)

            for (option in options.split(',')) {
                val trimmed: String = option.trim().lowercase()
                if (trimmed.isEmpty()) continue

                val negated: Boolean = trimmed.startsWith('~')
                val name: String = if (negated) trimmed.substring(1) else trimmed

                when {
                    name == "third-party" || name == "3p" -> thirdParty = !negated
                    name == "first-party" || name == "1p" -> thirdParty = negated
                    name.startsWith("domain=") -> {
                        name.substringAfter('=').split('|').forEach { domain ->
                            if (domain.isEmpty()) return@forEach
                            if (domain.startsWith('~')) {
                                excludeDomains += domain.substring(1)
                            } else {
                                includeDomains += domain
                            }
                        }
                    }
                    name.substringBefore('=') in UNSUPPORTED_OPTIONS -> return null
                    else -> {
                        val kind: ResourceKind = ResourceKind.forOption(name) ?: continue
                        // A negated type means every other type, which this
                        // representation cannot say. Skipped rather than
                        // inverted wrongly.
                        if (negated) return null
                        kinds += kind
                    }
                }
            }
        }

        var anchorDomain = false
        var anchorStart = false
        var anchorEnd = false

        if (body.startsWith("||")) {
            anchorDomain = true
            body = body.substring(2)
        } else if (body.startsWith("|")) {
            anchorStart = true
            body = body.substring(1)
        }
        if (body.endsWith("|")) {
            anchorEnd = true
            body = body.dropLast(1)
        }

        if (body.isEmpty()) return null

        return NetworkRule(
            pattern = body,
            isException = isException,
            anchorDomain = anchorDomain,
            anchorStart = anchorStart,
            anchorEnd = anchorEnd,
            thirdParty = thirdParty,
            kinds = kinds,
            includeDomains = includeDomains,
            excludeDomains = excludeDomains,
            token = tokenOf(body),
        )
    }

    private fun isRegexRule(body: String): Boolean {
        if (body.length <= 2 || !body.startsWith('/') || !body.endsWith('/')) return false
        return body.any { character -> character in REGEX_METACHARACTERS }
    }

    private const val REGEX_METACHARACTERS: String = "\\[]()+?{}"

    /**
     * The literal run a rule is indexed under.
     *
     * The longest one wins rather than the first, because the longest is the
     * rarest, and a rule bucketed under `com` is a rule tested against every
     * request ever made. Runs shorter than four characters are not worth a
     * bucket and fall to the catch-all.
     */
    fun tokenOf(pattern: String): String {
        var best = ""
        var current = StringBuilder()
        pattern.forEach { character ->
            if (character.isLetterOrDigit() && character.code < ASCII_CEILING) {
                current.append(character)
            } else {
                if (current.length > best.length) best = current.toString()
                current = StringBuilder()
            }
        }
        if (current.length > best.length) best = current.toString()
        return if (best.length >= MINIMUM_TOKEN) best.lowercase() else ""
    }

    /** Tokens a URL offers, which is what decides which buckets get tested. */
    fun tokensIn(url: String): List<String> {
        val tokens: MutableList<String> = mutableListOf()
        var current = StringBuilder()
        url.forEach { character ->
            if (character.isLetterOrDigit() && character.code < ASCII_CEILING) {
                current.append(character)
            } else {
                if (current.length >= MINIMUM_TOKEN) tokens += current.toString().lowercase()
                current = StringBuilder()
            }
        }
        if (current.length >= MINIMUM_TOKEN) tokens += current.toString().lowercase()
        return tokens
    }

    /** Four characters. Three buckets `ads` with `api` and `www`, and the
     *  catch-all stops being small. */
    private const val MINIMUM_TOKEN: Int = 4

    /** `isLetterOrDigit` is Unicode-aware and reports true for characters a
     *  hostname cannot contain, which would produce a token no URL can offer. */
    private const val ASCII_CEILING: Int = 128
}

/** Everything one or more lists parsed into. */
data class FilterSet(
    val network: List<NetworkRule>,
    val cosmetic: List<CosmeticRule>,
) {
    operator fun plus(other: FilterSet): FilterSet =
        FilterSet(network + other.network, cosmetic + other.cosmetic)

    companion object {
        val EMPTY = FilterSet(emptyList(), emptyList())
    }
}
