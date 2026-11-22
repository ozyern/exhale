/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.ozyern.exhale.playback.PlaybackSleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
) : Player.Listener {
    private val policy = PlaybackSleepTimer(
        scope = scope,
        nowMillis = System::currentTimeMillis,
        pausePlayback = {
            player.pause()
            // Back to full for whenever playback is started again.
            restoreVolume()
        },
    )

    /** The volume the fade started from, while one is under way. */
    private var fadeFrom: Float? = null
    val sharedPolicy: PlaybackSleepTimer get() = policy

    var triggerTime by mutableLongStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    val isActive: Boolean
        get() = policy.state.value.isActive

    init {
        scope.launch {
            policy.state.collect { state ->
                triggerTime = state.triggerTimeMs
                pauseWhenSongEnd = state.pauseWhenTrackEnds
            }
        }
        // The last stretch of a timed sleep fades the music out rather than cutting it: the
        // volume eases down over [FADE_MS] and the pause lands on silence. Cleared or
        // restarted, the fade lets go and the volume comes back.
        scope.launch {
            policy.state.map { it.triggerTimeMs }.distinctUntilChanged().collectLatest { trigger ->
                if (trigger < 0L) {
                    restoreVolume()
                    return@collectLatest
                }
                val fadeStart = trigger - FADE_MS
                delay((fadeStart - System.currentTimeMillis()).coerceAtLeast(0L))
                val from = player.volume.takeIf { it > 0f } ?: return@collectLatest
                fadeFrom = from
                while (true) {
                    val left = trigger - System.currentTimeMillis()
                    if (left <= 0L) break
                    val t = (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
                    // Eased: loudness falls evenly to the ear rather than dropping off late.
                    player.volume = from * t * t
                    delay(FADE_STEP_MS)
                }
                player.volume = 0f
            }
        }
    }

    private fun restoreVolume() {
        fadeFrom?.let { player.volume = it }
        fadeFrom = null
    }

    fun start(minute: Int) {
        policy.start(minute)
    }

    /** Also called when crossfade swaps the session player. */
    fun notifySongTransition() = pauseAtSongEnd()

    fun clear() = policy.clear()

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        pauseAtSongEnd()
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == Player.STATE_ENDED) pauseAtSongEnd()
    }

    private fun pauseAtSongEnd() {
        policy.onTrackEnded()
    }

    private companion object {
        /** The fade before a timed sleep pauses. */
        const val FADE_MS = 30_000L
        const val FADE_STEP_MS = 50L
    }
}
