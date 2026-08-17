/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.privacy

import android.webkit.CookieManager

/**
 * What survives the end of a session.
 *
 * A television is shared. A browser that silently keeps every session hands the
 * next person in the room somebody else's mail; one that keeps none is a browser
 * nobody can stay signed in to. So the choice is per site, and it is theirs.
 */
class CookiePolicy(
    private val allowedOrigins: () -> Set<String>,
    private val cookies: CookieManager = CookieManager.getInstance(),
) {

    /**
     * Cookies for allowed origins are read back and rewritten after the wipe,
     * because `CookieManager` offers no way to remove all but a few. Only the
     * name and value survive that round trip; a session cookie that depended on
     * an exotic attribute is one somebody will have to sign in for again, which
     * is the safe direction to be wrong in.
     */
    fun wipeSession(onDone: (kept: Int) -> Unit = {}) {
        val allowed: Set<String> = allowedOrigins()
        val kept: Map<String, String> = allowed
            .mapNotNull { origin ->
                val value: String? = cookies.getCookie(urlFor(origin))
                if (value.isNullOrEmpty()) null else origin to value
            }
            .toMap()

        cookies.removeAllCookies {
            kept.forEach { (origin, header) ->
                header.split(';')
                    .map { pair -> pair.trim() }
                    .filter { pair -> pair.isNotEmpty() }
                    .forEach { pair -> cookies.setCookie(urlFor(origin), pair) }
            }
            cookies.flush()
            onDone(kept.size)
        }
    }

    /** https, always. Restoring a cookie over http would strip it of the one
     *  protection it had. */
    private fun urlFor(origin: String): String = "https://$origin/"
}
