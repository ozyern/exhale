/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs
import com.ozyern.exhale.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig
import com.ozyern.exhale.innertube.models.bestSongFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** The cases below are YouTube's real answers: a video's top card often carries no type at all. */
class BestSongTest {
    private fun song(id: String, title: String, artist: String, type: String? = null) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(artist, null)),
        thumbnail = "",
        endpoint = type?.let { WatchEndpoint(videoId = id, watchEndpointMusicSupportedConfigs = WatchEndpointMusicSupportedConfigs(WatchEndpointMusicConfig(it))) },
    )

    private val atv = WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV

    @Test
    fun theAlbumTrackBeatsAnUnmarkedLyricVideo() {
        val results = listOf(
            song("video", "cindy lou who (Lyric Video)", "Sabrina Carpenter"),
            song("track", "cindy lou who", "Sabrina Carpenter", atv),
        )
        assertEquals("track", results.bestSongFor("cindy lou who")?.id)
    }

    @Test
    fun theAlbumTrackBeatsAnUnmarkedVideoOfTheSameTitle() {
        val results = listOf(
            song("video", "Espresso", "Sabrina Carpenter"),
            song("track", "Espresso", "Sabrina Carpenter", atv),
            song("other", "Espresso", "Alec Chambers", atv),
        )
        assertEquals("track", results.bestSongFor("espresso")?.id)
    }

    @Test
    fun theSongWithItsCreditsInTheTitleLeads() {
        val results = listOf(
            song("first", "Venom (Remix)", "Eminem"),
            song("track", "Last One Standing (From Venom: Let There Be Carnage) (feat. Polo G, Mozzy & Eminem)", "Skylar Grey", atv),
            song("other", "Last One Standing", "BarrelBorn", atv),
        )
        assertEquals("track", results.bestSongFor("last one standing")?.id)
    }

    /** Asked for by name, the video is what was wanted. */
    @Test
    fun aVideoAskedForIsStillFound() {
        val results = listOf(song("video", "cindy lou who (Lyric Video)", "Sabrina Carpenter"))
        assertEquals("video", results.bestSongFor("cindy lou who lyric video")?.id)
    }
}
