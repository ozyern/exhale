/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A-B repeat: loop one stretch of the song playing, for learning a line or practising a part.
 *
 * [mark] steps through the three states — set A here, set B here (and start looping), clear —
 * so the whole feature is one control. The loop belongs to one song: moving to another clears it.
 * It runs in the service, so it keeps looping with the screen off.
 */
class AbRepeat(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    /** A stretch of [mediaId]: from [startMs], to [endMs] once B has been set. */
    data class Loop(val mediaId: String, val startMs: Long, val endMs: Long? = null) {
        val looping: Boolean get() = endMs != null
    }

    private val _loop = MutableStateFlow<Loop?>(null)
    val loop: StateFlow<Loop?> = _loop

    private var watcher: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Same song looping round (repeat-one) keeps it; any other song ends it.
                if (mediaItem?.mediaId != _loop.value?.mediaId) clear()
            }
        })
    }

    /** Set A, then B, then clear. */
    fun mark() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition
        val current = _loop.value
        when {
            current == null || current.mediaId != mediaId -> _loop.value = Loop(mediaId, position)
            !current.looping -> {
                // B before A is taken as the user meaning the stretch between the two.
                val start = minOf(current.startMs, position)
                val end = maxOf(current.startMs, position)
                if (end - start < MinLoopMs) return
                _loop.value = Loop(mediaId, start, end)
                player.seekTo(start)
                watch()
            }
            else -> clear()
        }
    }

    fun clear() {
        watcher?.cancel()
        watcher = null
        _loop.value = null
    }

    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch {
            while (isActive) {
                val loop = _loop.value ?: break
                val end = loop.endMs ?: break
                val playing = player.isPlaying
                val position = player.currentPosition
                if (playing && position >= end) {
                    player.seekTo(loop.startMs)
                    continue
                }
                // Sleep until B is due rather than waking 25 times a second for the whole loop:
                // the jump still lands on B, since the wait shrinks as B comes near. Capped so a
                // seek made meanwhile is still noticed soon, and slow while paused.
                val speed = player.playbackParameters.speed.takeIf { it > 0.05f } ?: 1f
                delay(
                    if (!playing) PausedPollMs
                    else ((end - position) / speed).toLong().coerceIn(MinWaitMs, MaxWaitMs),
                )
            }
        }
    }

    private companion object {
        const val MinWaitMs = 10L
        const val MaxWaitMs = 250L
        const val PausedPollMs = 250L

        /** A loop shorter than this is almost certainly a double tap, not a phrase. */
        const val MinLoopMs = 500L
    }
}
