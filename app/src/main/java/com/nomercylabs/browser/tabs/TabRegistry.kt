/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the registry needs a tab's page to do. An interface rather than the
 * WebView host itself, so open / close / evict / resume can be tested without a
 * device: memory pressure arrives when the system decides, never when a test
 * asks.
 */
interface TabPage {
    val pageUrl: String
    val pageTitle: String
    val isSuspended: Boolean

    /** Release the renderer, keeping enough to come back. */
    fun suspendPage()

    /** Rebuild from the state captured before the release. */
    fun resumePage()

    /** Nothing comes back; the tab is being thrown away. */
    fun destroyPage()
}

class Tab(val id: String, val page: TabPage) {
    var lastUsedMillis: Long by mutableStateOf(0L)
        internal set
}

/**
 * Owns the open tabs and decides which of them get to keep a renderer.
 *
 * [isPlayingMedia] is asked rather than tracked, because the media session is
 * the only thing that knows which tab is playing, and two answers to that
 * question is how audio gets stopped by a tab the viewer never touched.
 */
class TabRegistry(
    private val createPage: (tabId: String) -> TabPage,
    private val isPlayingMedia: (tabId: String) -> Boolean,
    private val now: () -> Long,
    /**
     * Called for every tab the registry opens, including the one that replaces
     * the last closed tab. That replacement is why this is a hook rather than
     * something the caller does after `open()`: nobody asked for it, so nobody
     * would have given it a page to show.
     */
    private val onOpened: (Tab) -> Unit = {},
) {

    private val entries = mutableStateListOf<Tab>()
    private var nextId: Int = 1

    val tabs: List<Tab> get() = entries

    var activeId: String by mutableStateOf("")
        private set

    val active: Tab? get() = entries.firstOrNull { tab -> tab.id == activeId }

    fun open(): Tab {
        val id = "tab-${nextId++}"
        val tab = Tab(id, createPage(id))
        entries.add(tab)
        activate(id)
        onOpened(tab)
        return tab
    }

    fun activate(id: String) {
        val tab: Tab = entries.firstOrNull { candidate -> candidate.id == id } ?: return
        activeId = id
        tab.lastUsedMillis = now()
        // Resuming here rather than lazily at draw time: a tab that appears
        // before its page comes back is a black rectangle the viewer reads as a
        // crash.
        if (tab.page.isSuspended) tab.page.resumePage()
    }

    /**
     * The registry never empties. Closing the last tab opens a fresh one rather
     * than finishing the activity: an accidental exit from a remote is
     * indistinguishable from a crash, and that is the complaint this browser
     * exists to answer.
     */
    fun close(id: String) {
        val index: Int = entries.indexOfFirst { tab -> tab.id == id }
        if (index < 0) return

        val closing: Tab = entries.removeAt(index)
        closing.page.destroyPage()

        if (entries.isEmpty()) {
            open()
            return
        }
        if (activeId == id) {
            // The neighbour to the left, which is where the eye already is.
            activate(entries[(index - 1).coerceAtLeast(0)].id)
        }
    }

    fun snapshots(): List<TabSnapshot> = entries.map { tab ->
        TabSnapshot(
            id = tab.id,
            lastUsedMillis = tab.lastUsedMillis,
            isPlayingMedia = isPlayingMedia(tab.id),
            // Set in slice 11, when form introspection can tell us. Declared
            // now because the exemption it feeds is already tested, and finding
            // it missing costs somebody what they typed.
            hasDirtyForm = false,
            isForeground = tab.id == activeId,
            isSuspended = tab.page.isSuspended,
        )
    }

    /** Releases what the system's own trim level asks for. */
    fun applyTrim(trimLevel: Int): List<String> =
        release(TabPressureManager.releasesFor(trimLevel))

    /**
     * Releases [count] tabs, honouring the exemptions, and returns what was
     * released. Empty is a legitimate outcome: everything left may be playing,
     * holding input, or on screen.
     */
    fun release(count: Int): List<String> {
        val order: List<TabSnapshot> = TabPressureManager.evictionOrder(snapshots(), count)
        order.forEach { snapshot ->
            entries.firstOrNull { tab -> tab.id == snapshot.id }?.page?.suspendPage()
        }
        return order.map { snapshot -> snapshot.id }
    }
}
