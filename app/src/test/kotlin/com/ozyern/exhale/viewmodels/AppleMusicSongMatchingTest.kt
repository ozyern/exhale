package com.ozyern.exhale.viewmodels

import com.ozyern.exhale.canvas.ApplePlaylistTrack
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import org.junit.Assert.assertSame
import org.junit.Test

class AppleMusicSongMatchingTest {
    @Test
    fun choosesHighestScoringUniqueExactTitleAndArtistMatch() {
        val track = ApplePlaylistTrack("Quiet Song", "The Artist", 200_000)
        val firstDuplicate = song("duplicate", durationSeconds = 60)
        val betterDuplicate = song("duplicate", durationSeconds = 200)
        val bestUnique = song("best", durationSeconds = 200)
        val wrongArtist = song("wrong-artist", artist = "Someone Else", durationSeconds = 200)

        val result = chooseBestMatch(
            track,
            listOf(firstDuplicate, betterDuplicate, wrongArtist, bestUnique),
        )

        assertSame(bestUnique, result)
    }

    private fun song(
        id: String,
        artist: String = "The Artist",
        durationSeconds: Int,
    ) = SongItem(
        id = id,
        title = "Quiet Song",
        artists = listOf(Artist(artist, null)),
        thumbnail = "",
        duration = durationSeconds,
    )
}
