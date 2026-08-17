/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.a11y

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncerTest {

    private val spoken: MutableList<String> = mutableListOf()
    private var active: Boolean = true

    private fun announcer(): Announcer =
        Announcer(isActive = { active }, speak = { text -> spoken += text.toString() })

    @Test
    fun anAnnouncementReachesTheReader() {
        announcer().announce("Example Domain loaded")
        assertEquals(listOf("Example Domain loaded"), spoken)
    }

    // Speaking with no reader running does nothing visible in testing and costs
    // work on every page load.
    @Test
    fun nothingIsSaidWhileNoReaderIsListening() {
        active = false
        announcer().announce("Example Domain loaded")
        assertEquals(emptyList<String>(), spoken)
    }

    // The defect this guards: progress arrives several times per load and every
    // report carries the same title, so one page load said the same sentence
    // four times.
    @Test
    fun theSameSentenceIsNotRepeated() {
        val announcer: Announcer = announcer()
        repeat(4) { announcer.announce("Example Domain loaded") }
        assertEquals(listOf("Example Domain loaded"), spoken)
    }

    // The guard is against a repeat, not against ever saying it again: two tabs
    // on the same page are two different events.
    @Test
    fun theSameSentenceIsSaidAgainAfterANewDocument() {
        val announcer: Announcer = announcer()
        announcer.announce("Example Domain loaded")
        announcer.forgetLast()
        announcer.announce("Example Domain loaded")
        assertEquals(listOf("Example Domain loaded", "Example Domain loaded"), spoken)
    }

    @Test
    fun anEmptyAnnouncementIsNotSpokenAsSilence() {
        val announcer: Announcer = announcer()
        announcer.announce("")
        announcer.announce("   ")
        assertEquals(emptyList<String>(), spoken)
    }

    @Test
    fun aTitledPageIsNamedByItsTitle() {
        assertEquals("Example Domain", Announcer.nameFor("Example Domain", "example.org"))
    }

    // A page that failed to load usually has no title, which is exactly when the
    // announcement carries the most.
    @Test
    fun anUntitledPageIsNamedByItsHost() {
        assertEquals("example.org", Announcer.nameFor("", "example.org"))
        assertEquals("example.org", Announcer.nameFor("   ", "example.org"))
    }

    @Test
    fun anAddressWithNeitherIsNamedAsNothing() {
        assertEquals("", Announcer.nameFor("", ""))
    }
}
