/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import com.ozyern.exhale.desktop.ui.search.queryNamesASong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShortcutsTest {
    private var presses = 0
    private fun press(down: Boolean) = AppShortcuts.onSpace(down) { presses++ }

    /** Taken before a focused button: both halves, since a button clicks on the release. */
    @Test
    fun spaceIsTakenBeforeAnyButtonSeesIt() {
        assertTrue(press(true))
        assertEquals(1, presses)
        assertTrue(press(false))
    }

    @Test
    fun holdingSpaceIsOnePress() {
        repeat(5) { press(true) }
        press(false)
        assertEquals(1, presses)
        press(true)
        press(false)
        assertEquals(2, presses)
    }

    @Test
    fun aSpaceInATextFieldIsJustASpace() {
        val field = Any()
        AppShortcuts.setTyping(field, true)
        assertFalse(press(true))
        assertFalse(press(false))
        assertEquals(0, presses)
        // Focus moving to another field, gained before lost, is still typing.
        val next = Any()
        AppShortcuts.setTyping(next, true)
        AppShortcuts.setTyping(field, false)
        assertTrue(AppShortcuts.typing)
        AppShortcuts.setTyping(next, false)
        assertFalse(AppShortcuts.typing)
    }

    /** A song's own title is a search for that song, not a line to look up in Genius. */
    @Test
    fun aTitleIsNotALyric() {
        val titles = listOf("Last One Standing (From Venom: Let There Be Carnage) (feat. Polo G, Mozzy & Eminem)", "Dynamite")
        assertTrue(queryNamesASong("last one standing", titles))
        assertTrue(queryNamesASong("Last One Standing!", titles))
        assertFalse(queryNamesASong("his palms are sweaty", titles))
        // Whole words only, and a real line of a song stays a lyric search.
        assertFalse(queryNamesASong("dyna", titles))
        assertFalse(queryNamesASong("", titles))
    }
}
