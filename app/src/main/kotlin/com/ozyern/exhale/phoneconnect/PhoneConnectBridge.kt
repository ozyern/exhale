/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.phoneconnect

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ozyern.exhale.connect.ConnectAction
import com.ozyern.exhale.connect.ConnectMessage
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.playback.MusicService
import com.ozyern.exhale.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The playback service's side of Exhale Connect: tells the PC what the phone plays, and does what
 * the PC asks. Follows the service's player across DJ crossfades, which swap in a new one.
 * Everything here runs on the main thread, where the player lives.
 */
class PhoneConnectBridge(
    private val service: MusicService,
    private val scope: CoroutineScope,
) : PhoneConnect.Player, Player.Listener {
    private var listening: ExoPlayer? = null
    private var follow: Job? = null

    fun start() {
        follow = scope.launch {
            service.playerFlow.collect { next ->
                listening?.removeListener(this@PhoneConnectBridge)
                next?.addListener(this@PhoneConnectBridge)
                listening = next
                PhoneConnect.playerChanged()
            }
        }
        PhoneConnect.attach(this)
    }

    fun stop() {
        follow?.cancel()
        listening?.removeListener(this)
        listening = null
        PhoneConnect.detach(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_VOLUME_CHANGED,
            )
        ) {
            PhoneConnect.playerChanged()
        }
    }

    override fun nowPlaying(): ConnectMessage.NowPlaying {
        val player = service.player
        val current = player.currentMediaItemIndex
        // From the playing song on: what the PC needs to show it and to carry on from it.
        val queue = if (current < 0) emptyList() else (current until minOf(player.mediaItemCount, current + QUEUE_SENT))
            .mapNotNull { player.getMediaItemAt(it).metadata?.toTrack() }
        return ConnectMessage.NowPlaying(
            track = player.currentMediaItem?.metadata?.toTrack(),
            playing = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            volume = (service.playerVolume.value * 100).toInt().coerceIn(0, 100),
            queue = queue,
            index = 0,
            sentAtMs = System.currentTimeMillis(),
        )
    }

    override fun command(command: ConnectMessage.Command) {
        val player = service.player
        when (command.action) {
            ConnectAction.PLAY -> player.play()
            ConnectAction.PAUSE -> player.pause()
            ConnectAction.TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            ConnectAction.NEXT -> player.seekToNext()
            ConnectAction.PREVIOUS -> player.seekToPrevious()
            ConnectAction.SEEK -> command.value?.let { player.seekTo(it.coerceAtLeast(0)) }
            ConnectAction.VOLUME -> command.value?.let { percent ->
                val volume = (percent / 100f).coerceIn(0f, 1f)
                service.playerVolume.value = volume
                player.volume = volume
            }
            // Counted from the playing song, as the queue in [nowPlaying] is.
            ConnectAction.SKIP_TO -> command.value?.let { offset ->
                val index = player.currentMediaItemIndex + offset.toInt()
                if (index in 0 until player.mediaItemCount) player.seekTo(index, 0)
            }
        }
    }

    override fun handoff(handoff: ConnectMessage.Handoff) {
        val items = handoff.queue.map { it.toMediaMetadata().toMediaItem() }
        if (items.isEmpty()) return
        service.playQueue(
            ListQueue(
                items = items,
                startIndex = handoff.index.coerceIn(0, items.lastIndex),
                position = handoff.positionMs.coerceAtLeast(0),
            ),
            playWhenReady = handoff.play,
        )
    }

    override fun pauseForHandoff() {
        service.player.pause()
    }

    private companion object {
        const val QUEUE_SENT = 50
    }
}
