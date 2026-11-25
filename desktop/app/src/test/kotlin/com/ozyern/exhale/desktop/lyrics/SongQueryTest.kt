/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What gets sent to the lyrics sites. A song uploaded as a video is titled like a video, and the
 * difference between searching its name and searching its credits is the difference between lyrics
 * and none.
 */
class SongQueryTest {
    @Test
    fun aVideoTitleIsCutBackToTheSong() {
        assertEquals(
            "Re Bawri",
            SongQuery.cleanTitle("Re Bawri (Song) | Alia Bhatt | Don't Be Shy | Sona Mohapatra | Ram Sampath | T-Series"),
        )
        assertEquals("Kesariya", SongQuery.cleanTitle("Kesariya – Full Video | Brahmāstra | Arijit Singh"))
    }

    @Test
    fun ordinaryTitlesSurviveUntouched() {
        assertEquals("Espresso", SongQuery.cleanTitle("Espresso"))
        assertEquals("Stan", SongQuery.cleanTitle("Stan (feat. Dido)"))
        assertEquals("Blinding Lights", SongQuery.cleanTitle("Blinding Lights (Official Video)"))
        // A live or acoustic take is a different recording, and saying so is how the right one is found.
        assertEquals("Why Why Why (Acoustic)", SongQuery.cleanTitle("Why Why Why (Acoustic)"))
    }

    @Test
    fun theCreditedSingerBeatsTheChannel() {
        val title = "Re Bawri (Song) | Alia Bhatt | Don't Be Shy | Sona Mohapatra | Ram Sampath | T-Series"
        assertEquals("Alia Bhatt", SongQuery.creditedArtist(title, listOf("T-Series")))
    }

    @Test
    fun aPlainTitleKeepsItsOwnArtist() {
        assertEquals("Sabrina Carpenter", SongQuery.creditedArtist("Espresso", listOf("Sabrina Carpenter")))
        assertEquals(null, SongQuery.creditedArtist("Espresso", emptyList()))
    }
}
