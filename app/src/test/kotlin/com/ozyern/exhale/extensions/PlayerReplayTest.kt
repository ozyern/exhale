package com.ozyern.exhale.extensions

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerReplayTest {
    @Test
    fun endedTrackRestartsOnFirstTapForEitherPlayWhenReadyValue() {
        for (initialReady in listOf(false, true)) {
            val state = StubPlayer(Player.STATE_ENDED, initialReady)
            state.player.togglePlayPause()
            assertEquals(listOf(2 to 0L), state.seeks)
            assertTrue(state.ready)
        }
    }

    @Test
    fun playingTrackPausesAndPausedTrackResumesWithoutSeeking() {
        val state = StubPlayer(Player.STATE_READY, true)
        state.player.togglePlayPause()
        assertEquals(false, state.ready)
        state.player.togglePlayPause()
        assertEquals(true, state.ready)
        assertTrue(state.seeks.isEmpty())
    }

    private class StubPlayer(private val playbackState: Int, var ready: Boolean) {
        val seeks = mutableListOf<Pair<Int, Long>>()
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getPlaybackState" -> playbackState
                "getPlayWhenReady" -> ready
                "setPlayWhenReady" -> { ready = args!![0] as Boolean; null }
                "getCurrentMediaItemIndex" -> 2
                "seekTo" -> { seeks += (args!![0] as Int) to (args[1] as Long); null }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player
    }
}
