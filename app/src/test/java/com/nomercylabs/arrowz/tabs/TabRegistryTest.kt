/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePage(override val pageUrl: String = "", override val pageTitle: String = "") : TabPage {
    override var isSuspended: Boolean = false
    var destroyed: Boolean = false
    var resumeCount: Int = 0

    override fun suspendPage() { isSuspended = true }
    override fun resumePage() { isSuspended = false; resumeCount++ }
    override fun destroyPage() { destroyed = true }
}

class TabRegistryTest {

    private val pages = mutableMapOf<String, FakePage>()
    private val opened = mutableListOf<String>()
    private var playingTabId: String = ""
    private var clock: Long = 0L

    private fun registry() = TabRegistry(
        createPage = { id -> FakePage().also { page -> pages[id] = page } },
        isPlayingMedia = { id -> id == playingTabId },
        now = { ++clock },
        onOpened = { tab -> opened += tab.id },
    )

    @Test
    fun openingATabMakesItActive() {
        val registry = registry()
        val first = registry.open()
        val second = registry.open()

        assertEquals(2, registry.tabs.size)
        assertEquals(second.id, registry.activeId)
        assertNotEquals(first.id, second.id)
    }

    // A tab that appears before its page comes back is a black rectangle, which
    // the viewer reads as a crash.
    @Test
    fun activatingASuspendedTabResumesIt() {
        val registry = registry()
        val first = registry.open()
        registry.open()
        registry.release(1)
        assertTrue(pages.getValue(first.id).isSuspended)

        registry.activate(first.id)

        assertFalse(pages.getValue(first.id).isSuspended)
        assertEquals(1, pages.getValue(first.id).resumeCount)
    }

    @Test
    fun closingATabDestroysItsPageAndActivatesTheNeighbour() {
        val registry = registry()
        val first = registry.open()
        val second = registry.open()

        registry.close(second.id)

        assertTrue(pages.getValue(second.id).destroyed)
        assertEquals(listOf(first.id), registry.tabs.map { tab -> tab.id })
        assertEquals(first.id, registry.activeId)
    }

    // An accidental exit from a remote is indistinguishable from a crash, which
    // is the complaint this browser exists to answer.
    @Test
    fun closingTheLastTabLeavesAFreshOne() {
        val registry = registry()
        val only = registry.open()

        registry.close(only.id)

        assertEquals(1, registry.tabs.size)
        assertNotEquals(only.id, registry.tabs.single().id)
        assertEquals(registry.tabs.single().id, registry.activeId)
        // Nobody asked for the replacement, so nothing else would give it a
        // page to show, and the viewer would be looking at a blank rectangle.
        assertEquals(listOf(only.id, registry.tabs.single().id), opened)
    }

    @Test
    fun releaseSuspendsTheLeastRecentlyUsedTabAndNeverTheForegroundOne() {
        val registry = registry()
        val oldest = registry.open()
        val middle = registry.open()
        val active = registry.open()

        val released: List<String> = registry.release(5)

        assertEquals(listOf(oldest.id, middle.id), released)
        assertTrue(pages.getValue(oldest.id).isSuspended)
        assertFalse(pages.getValue(active.id).isSuspended)
    }

    // Audio stopping because a newer tab was opened is indistinguishable from a
    // crash, so ownership comes from the media session rather than from a flag.
    @Test
    fun theTabHoldingTheMediaSessionIsNeverReleased() {
        val registry = registry()
        val playing = registry.open()
        val idle = registry.open()
        registry.open()
        playingTabId = playing.id

        val released: List<String> = registry.release(5)

        assertEquals(listOf(idle.id), released)
        assertFalse(pages.getValue(playing.id).isSuspended)
    }

    @Test
    fun aTrimLevelReleasesWhatThePolicySays() {
        val registry = registry()
        repeat(4) { registry.open() }

        assertTrue(registry.applyTrim(TabPressureManager.TRIM_RUNNING_LOW).size == 1)
        assertTrue(registry.applyTrim(TabPressureManager.TRIM_MODERATE).size == 2)
        // Nothing left but the foreground tab, which is exempt.
        assertTrue(registry.applyTrim(TabPressureManager.TRIM_COMPLETE).isEmpty())
    }

    @Test
    fun anAlreadySuspendedTabIsNotReleasedTwice() {
        val registry = registry()
        val first = registry.open()
        registry.open()

        assertEquals(listOf(first.id), registry.release(1))
        assertTrue(registry.release(1).isEmpty())
    }

    @Test
    fun snapshotsReportForegroundAndSuspension() {
        val registry = registry()
        val background = registry.open()
        val active = registry.open()
        registry.release(1)

        val snapshots: Map<String, TabSnapshot> =
            registry.snapshots().associateBy { snapshot -> snapshot.id }

        assertTrue(snapshots.getValue(active.id).isForeground)
        assertTrue(snapshots.getValue(background.id).isSuspended)
        assertFalse(snapshots.getValue(background.id).isForeground)
    }
}
