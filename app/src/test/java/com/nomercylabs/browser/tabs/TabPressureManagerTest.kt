/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPressureManagerTest {

    private fun tab(
        id: String,
        lastUsed: Long,
        playing: Boolean = false,
        dirty: Boolean = false,
        foreground: Boolean = false,
        suspended: Boolean = false,
    ) = TabSnapshot(id, lastUsed, playing, dirty, foreground, suspended)

    @Test
    fun evictsLeastRecentlyUsedFirst() {
        val tabs = listOf(
            tab("newest", lastUsed = 300),
            tab("oldest", lastUsed = 100),
            tab("middle", lastUsed = 200),
        )
        val order = TabPressureManager.evictionOrder(tabs, needed = 2).map { it.id }
        assertEquals(listOf("oldest", "middle"), order)
    }

    // Audio stopping because a newer tab was opened is indistinguishable from a
    // crash, so this exemption outranks age entirely.
    @Test
    fun aPlayingTabIsNeverEvictedEvenWhenItIsTheOldest() {
        val tabs = listOf(
            tab("playing", lastUsed = 1, playing = true),
            tab("idle", lastUsed = 500),
        )
        val order = TabPressureManager.evictionOrder(tabs, needed = 5).map { it.id }
        assertEquals(listOf("idle"), order)
    }

    // saveState does not reliably carry unsaved input, and losing what someone
    // typed on a television keyboard is not recoverable by them.
    @Test
    fun aTabWithADirtyFormIsNeverEvicted() {
        val tabs = listOf(
            tab("form", lastUsed = 1, dirty = true),
            tab("idle", lastUsed = 500),
        )
        val order = TabPressureManager.evictionOrder(tabs, needed = 5).map { it.id }
        assertEquals(listOf("idle"), order)
    }

    @Test
    fun theForegroundTabIsNeverEvicted() {
        val tabs = listOf(
            tab("front", lastUsed = 1, foreground = true),
            tab("idle", lastUsed = 500),
        )
        val order = TabPressureManager.evictionOrder(tabs, needed = 5).map { it.id }
        assertEquals(listOf("idle"), order)
    }

    @Test
    fun alreadySuspendedTabsAreNotEvictedAgain() {
        val tabs = listOf(
            tab("gone", lastUsed = 1, suspended = true),
            tab("idle", lastUsed = 500),
        )
        val order = TabPressureManager.evictionOrder(tabs, needed = 5).map { it.id }
        assertEquals(listOf("idle"), order)
    }

    // A legitimate outcome rather than a failure. Releasing nothing is correct
    // when everything left is either playing, holding input, or on screen.
    @Test
    fun everythingExemptReleasesNothing() {
        val tabs = listOf(
            tab("a", lastUsed = 1, playing = true),
            tab("b", lastUsed = 2, dirty = true),
            tab("c", lastUsed = 3, foreground = true),
        )
        assertTrue(TabPressureManager.evictionOrder(tabs, needed = 3).isEmpty())
    }

    @Test
    fun askingForNothingReleasesNothing() {
        val tabs = listOf(tab("a", lastUsed = 1), tab("b", lastUsed = 2))
        assertTrue(TabPressureManager.evictionOrder(tabs, needed = 0).isEmpty())
        assertTrue(TabPressureManager.evictionOrder(tabs, needed = -1).isEmpty())
    }

    @Test
    fun requestingMoreThanAvailableReturnsWhatThereIs() {
        val tabs = listOf(tab("a", lastUsed = 1), tab("b", lastUsed = 2))
        assertEquals(2, TabPressureManager.evictionOrder(tabs, needed = 10).size)
    }

    // Treating every trim level the same either releases too little to help or
    // throws away tabs the user still wanted.
    @Test
    fun trimLevelsScaleWhatIsReleased() {
        assertEquals(0, TabPressureManager.releasesFor(5))
        assertEquals(1, TabPressureManager.releasesFor(TabPressureManager.TRIM_RUNNING_LOW))
        assertEquals(2, TabPressureManager.releasesFor(TabPressureManager.TRIM_RUNNING_CRITICAL))
        assertEquals(1, TabPressureManager.releasesFor(TabPressureManager.TRIM_BACKGROUND))
        assertEquals(2, TabPressureManager.releasesFor(TabPressureManager.TRIM_MODERATE))
        assertEquals(Int.MAX_VALUE, TabPressureManager.releasesFor(TabPressureManager.TRIM_COMPLETE))
    }
}
