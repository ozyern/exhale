/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import com.ozyern.exhale.desktop.ui.search.songTitleAnswers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    /** The Songs search's extras join only when the song's own title was typed, not its artist's name. */
    @Test
    fun anArtistsNameDoesNotNameASong() {
        // "fruitcake" found every song by a band called Fruitcake, ahead of Sabrina Carpenter's.
        assertFalse(songTitleAnswers("fruitcake", "Cool And Gentle"))
        assertFalse(songTitleAnswers("fruitcake", "Keep It Up"))
        assertTrue(songTitleAnswers("fruitcake", "Fruitcake"))
        assertTrue(songTitleAnswers("Please don't send", "Please Don't Send Me Fruitcake"))
    }

    @Test
    fun everyTypedWordMustBeInTheTitle() {
        assertTrue(songTitleAnswers("good luck babe", "Good Luck, Babe!"))
        assertFalse(songTitleAnswers("good luck babe sabrina", "Good Luck, Babe!"))
        assertFalse(songTitleAnswers("", "Anything"))
        // Whole words: a fragment doesn't count.
        assertFalse(songTitleAnswers("fruit", "Fruitcake"))
    }
}
