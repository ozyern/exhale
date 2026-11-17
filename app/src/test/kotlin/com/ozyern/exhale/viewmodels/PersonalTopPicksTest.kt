package com.ozyern.exhale.viewmodels

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.ozyern.exhale.db.entities.ArtistEntity
import com.ozyern.exhale.models.SimilarRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ozyern.exhale.db.entities.Artist as LocalArtist

class PersonalTopPicksTest {
    @Test
    fun isEmptyWithoutPicksOrHistory() {
        assertTrue(personalTopPicks(null, null).isEmpty())
        assertTrue(personalTopPicks(feed(), emptyList()).isEmpty())
    }

    @Test
    fun dealsPickedAndHistorySongsInTurn() {
        val result = personalTopPicks(
            feed(song("p1"), song("p2"), song("p3")),
            listOf(recommendation(song("h1"), song("h2"))),
        )

        assertEquals(listOf("p1", "h1", "p2", "h2", "p3"), result.map { it.id })
    }

    @Test
    fun skipsRepeatsNonSongsAndStopsAtTheLimit() {
        val album = AlbumItem(browseId = "a1", playlistId = "pl", title = "Record", artists = null, thumbnail = "")
        val result = personalTopPicks(
            feed(song("same"), song("p2"), song("p3")),
            listOf(recommendation(album, song("same"), song("h2"))),
            limit = 3,
        )

        assertEquals(listOf("same", "p2", "h2"), result.map { it.id })
    }

    private fun feed(vararg mix: SongItem) = PickedArtistFeed(
        mix = mix.toList(),
        shelves = emptyList(),
        releases = emptyList(),
        relatedArtists = emptyList(),
        leading = true,
    )

    private fun recommendation(vararg items: com.music.innertube.models.YTItem) = SimilarRecommendation(
        title = LocalArtist(ArtistEntity(id = "artist", name = "Artist"), songCount = 0),
        items = items.toList(),
    )

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = listOf(Artist("Artist", "artist")),
        thumbnail = "",
    )
}
