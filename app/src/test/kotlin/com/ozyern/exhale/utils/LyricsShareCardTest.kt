package com.ozyern.exhale.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsShareCardTest {
    @Test
    fun uploadLabelsLeaveTheCardTitle() {
        assertEquals("Act A Fool", LyricsShareCard.cardTitle("Act A Fool (Official Music Video)"))
        assertEquals("Houdini", LyricsShareCard.cardTitle("Houdini [Official Audio]"))
        assertEquals("Espresso", LyricsShareCard.cardTitle("Espresso (Lyrics)"))
        assertEquals("Golden", LyricsShareCard.cardTitle("Golden (Official Visualizer) [4K]"))
    }

    @Test
    fun partsOfTheSongNameStay() {
        // Featured artists and versions are the song's own name, not an upload label.
        assertEquals("Yeah! (feat. Lil Jon & Ludacris)", LyricsShareCard.cardTitle("Yeah! (feat. Lil Jon & Ludacris)"))
        assertEquals("Blinding Lights (Remix)", LyricsShareCard.cardTitle("Blinding Lights (Remix)"))
        assertEquals("Video Games", LyricsShareCard.cardTitle("Video Games"))
    }

    @Test
    fun aTitleThatIsOnlyALabelIsKept() {
        assertEquals("(Official Video)", LyricsShareCard.cardTitle("(Official Video)"))
    }
}
