/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.spatial

/**
 * Which item a row or grid returns to when focus comes back into it.
 *
 * Android TV's remembered-child behaviour, and without it a long grid is
 * unusable: leaving to check something and coming back starts at the first item
 * again, so every return costs as many presses as the original journey.
 *
 * Only applies when focus *enters* a section it was not already in. Moving
 * inside a section is ordinary geometry, and redirecting those presses would
 * pin focus to the remembered item and make the row impossible to walk.
 */
class SectionMemory {

    private val lastFocused = mutableMapOf<String, String>()

    fun remember(section: String, id: String) {
        if (section.isEmpty()) return
        lastFocused[section] = id
    }

    /**
     * The id focus should actually land on, given where the search wanted to
     * go. Returns the winner unchanged when the move stays inside one section,
     * when the section has no memory, or when what it remembers is no longer on
     * the page.
     */
    fun resolve(
        fromSection: String,
        winner: PageFocusable,
        available: List<PageFocusable>,
    ): String {
        if (winner.section == fromSection) return winner.focusable.id

        val remembered: String = lastFocused[winner.section] ?: return winner.focusable.id
        val stillPresent: Boolean = available.any { element -> element.focusable.id == remembered }
        return if (stillPresent) remembered else winner.focusable.id
    }

    fun forget() = lastFocused.clear()
}
