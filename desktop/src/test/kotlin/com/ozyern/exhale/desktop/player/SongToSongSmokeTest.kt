/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * One song running into the next, through the real player with the settings it finds: the first is
 * sent to its last seconds, and the second must load and play with no error. Run it against a copy
 * of a settings folder (-PexhaleAppData=…) — the player saves its queue and history as it goes.
 * EXHALE_SMOKE=1 to run.
 */
class SongToSongSmokeTest {
    @Test
    fun nextSongPlaysAfterTheFirstEnds() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val songs = YouTube.search("Sabrina Carpenter", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().take(3)
        assumeTrue("search found too few songs", songs.size == 3)

        val player = DesktopPlayer(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        try {
            player.setVolume(0)
            player.play(songs, 0)
            for (step in 0 until 2) {
                val expected = songs[step + 1].id
                waitFor(30_000) { player.state.value.isPlaying && player.state.value.durationSeconds > 0 }
                val duration = player.state.value.durationSeconds
                println("SONG ${step + 1}: ${player.state.value.current?.title} playing, ${duration.toInt()}s; sending to its end")
                player.seekTo(duration - 4.0)
                waitFor(30_000) {
                    val s = player.state.value
                    s.error != null || (s.current?.id == expected && s.isPlaying && player.position.value > 1.5)
                }
                val s = player.state.value
                println("SONG ${step + 2}: ${s.current?.title} playing=${s.isPlaying} at ${"%.1f".format(player.position.value)}s error=${s.error}")
                assertTrue("next song failed: ${s.error}", s.error == null && s.current?.id == expected && s.isPlaying)
            }
        } finally {
            player.close()
        }
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !condition()) Thread.sleep(200)
    }
}
