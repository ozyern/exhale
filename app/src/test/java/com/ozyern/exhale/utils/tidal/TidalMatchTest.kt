package com.ozyern.exhale.utils.tidal

import com.ozyern.exhale.utils.tidal.TidalMatch.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TidalMatchTest {
    @Test
    fun stripsVideoTagsAndTheArtistPrefixFromATitle() {
        assertEquals("Espresso", TidalMatch.songTitle("Sabrina Carpenter - Espresso (Official Video)", listOf("Sabrina Carpenter")))
        assertEquals("Espresso", TidalMatch.cleanTitle("Espresso [Official Lyric Video]"))
        assertEquals("Taste", TidalMatch.cleanTitle("Taste | Sabrina Carpenter"))
    }

    @Test
    fun keepsADashThatIsPartOfTheSongTitle() {
        assertEquals("Run - Remix", TidalMatch.songTitle("Run - Remix", listOf("Someone Else")))
    }

    @Test
    fun dropsChannelDecorationsFromArtists() {
        assertEquals("SabrinaCarpenter", TidalMatch.cleanArtist("SabrinaCarpenterVEVO"))
        assertEquals("Sabrina Carpenter", TidalMatch.cleanArtist("Sabrina Carpenter - Topic"))
    }

    @Test
    fun searchesWithoutAnArtistTidalCannotRead() {
        // An artist in Devanagari (a Hindi content language) made TIDAL's search come back empty.
        assertEquals(listOf("Espresso"), TidalMatch.queries("Espresso", listOf("सबरीना कारपेंटर")))
        assertEquals(
            listOf("Espresso Sabrina Carpenter", "Espresso"),
            TidalMatch.queries("Espresso", listOf("Sabrina Carpenter")),
        )
    }

    @Test
    fun matchesByLengthWhenTheArtistNameCannotBeCompared() {
        val espresso = candidate(1, "Espresso", "Sabrina Carpenter", 175)
        val ranked = TidalMatch.rank(listOf(espresso), "Espresso", listOf("सबरीना कारपेंटर"), durationSec = 176)

        assertEquals(listOf(espresso), ranked)
    }

    @Test
    fun rejectsATitleOnlyMatchWithTheWrongArtistAndLength() {
        val stranger = candidate(1, "Taste", "Someone Else", 240)

        assertTrue(TidalMatch.rank(listOf(stranger), "Taste", listOf("Sabrina Carpenter"), durationSec = 157).isEmpty())
    }

    @Test
    fun ranksTheLosslessVersionAboveItsLossyTwin() {
        val lossy = candidate(1, "Espresso", "Sabrina Carpenter", 175, lossless = false)
        val lossless = candidate(2, "Espresso", "Sabrina Carpenter", 175, lossless = true)

        val ranked = TidalMatch.rank(listOf(lossy, lossless), "Espresso", listOf("Sabrina Carpenter"), 175)

        assertEquals(listOf(lossless, lossy), ranked)
    }

    @Test
    fun matchesAVevoUploadToTheAlbumTrack() {
        val track = candidate(1, "Espresso", "Sabrina Carpenter", 175)

        val ranked = TidalMatch.rank(
            listOf(track),
            "Sabrina Carpenter - Espresso (Official Video)",
            listOf("SabrinaCarpenterVEVO"),
            durationSec = 201,
        )

        assertEquals(listOf(track), ranked)
    }

    @Test
    fun neverPlaysAnotherRecordingForALiveTake() {
        // TIDAL has no Coachella recording; the Mark Ronson "Espresso" was picked before.
        val ronson = Candidate(1, "Espresso", listOf("Sabrina Carpenter", "Mark Ronson"), 184, lossless = true)

        assertTrue(
            TidalMatch.rank(listOf(ronson), "Espresso - Live at Coachella 2026", listOf("Sabrina Carpenter"), 182).isEmpty(),
        )
    }

    @Test
    fun matchesALiveTakeToTheSameLiveTake() {
        val live = candidate(1, "Espresso (Live)", "Sabrina Carpenter", 182)
        val studio = candidate(2, "Espresso", "Sabrina Carpenter", 175)

        val ranked = TidalMatch.rank(listOf(studio, live), "Espresso (Live)", listOf("Sabrina Carpenter"), 182)

        assertEquals(listOf(live), ranked)
    }

    @Test
    fun keepsTheAlbumTrackFromMatchingASpedUpEdit() {
        val spedUp = candidate(1, "Espresso (Sped Up)", "Sabrina Carpenter", 150)

        assertTrue(TidalMatch.rank(listOf(spedUp), "Espresso", listOf("Sabrina Carpenter"), 175).isEmpty())
    }

    private fun candidate(id: Long, title: String, artist: String, duration: Int, lossless: Boolean = true) =
        Candidate(id, title, listOf(artist), duration, lossless)
}
