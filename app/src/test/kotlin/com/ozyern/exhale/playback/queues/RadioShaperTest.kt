package com.ozyern.exhale.playback.queues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioShaperTest {
    private data class Song(val id: String, val title: String, val artist: String)

    private fun shaper() = RadioShaper<Song>(
        idOf = { it.id },
        songOf = { "${it.title}|${it.artist}" },
        artistOf = { it.artist },
    )

    private fun songs(vararg artists: String) =
        artists.mapIndexed { i, artist -> Song("id$i", "song$i", artist) }

    @Test
    fun anArtistWaitsForOthersWhenThereAreOthers() {
        val shaped = shaper().shape(songs("A", "A", "A", "B", "C", "D", "E"))
        assertEquals(listOf("A", "B", "C", "D", "A", "E", "A"), shaped.map { it.artist })
    }

    @Test
    fun anArtistIsCappedAtAShareOfTheQueue() {
        val many = List(40) { Song("a$it", "a$it", "A") } + List(60) { Song("o$it", "o$it", "other$it") }
        val shaped = shaper().shape(many)
        assertEquals(15, shaped.count { it.artist == "A" })
        assertEquals(60, shaped.count { it.artist != "A" })
    }

    @Test
    fun aShortRadioOfOneArtistStillHasSomethingToPlay() {
        val shaped = shaper().shape(songs("A", "A", "A", "A", "A", "A"))
        assertEquals(4, shaped.size)
    }

    @Test
    fun theSameSongOffAnotherAlbumAndRepeatsAcrossPagesGo() {
        val shaper = shaper()
        val first = shaper.shape(listOf(Song("1", "one", "A"), Song("2", "one", "A"), Song("3", "two", "B")))
        assertEquals(listOf("1", "3"), first.map { it.id })
        val second = shaper.shape(listOf(Song("3", "two", "B"), Song("4", "three", "C")))
        assertEquals(listOf("4"), second.map { it.id })
    }

    @Test
    fun keptSongsCountWithoutBeingOfferedAgain() {
        val shaper = shaper()
        shaper.keep(listOf(Song("0", "playing", "A")))
        val shaped = shaper.shape(listOf(Song("0", "playing", "A"), Song("1", "next", "A"), Song("2", "other", "B")))
        assertEquals(listOf("2", "1"), shaped.map { it.id })
        assertTrue(shaped.none { it.id == "0" })
    }
}
