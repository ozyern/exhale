/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The receiver side follows BitChord's CastPlayback (github.com/kushagrasinghx/BitChord, GPL-3.0).
 */

package com.ozyern.exhale.playback.cast

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.Result
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.abs

/** A stream the receiver can open by itself: a URL and what it holds. */
class CastStream(val url: String, val mimeType: String)

/**
 * What plays on a Cast receiver while a session is up.
 *
 * The phone stays the player of record. It keeps playing the queue — silently, its volume held at
 * zero for as long as the receiver is the speaker — and the receiver plays the same song in step
 * with it. That way everything in the app that reads the phone's player (the play button, the
 * scrubber, the word-by-word lyrics, Music Together, the notification, the widget) just keeps
 * working while casting, instead of each needing to learn that the clock is somewhere else.
 *
 * Kept in step, in both directions:
 *  - the phone moving to another song loads it on the receiver, at the phone's position;
 *  - play, pause and seeks on the phone are made on the receiver;
 *  - pause or play from the receiver's own remote is made on the phone;
 *  - a drift of more than [DRIFT_MS] between the two is closed by moving the receiver.
 *
 * The receiver cannot ask the service to resolve a song, so the stream URL is resolved on the
 * phone and handed over finished. A file on the phone can't be reached by it at all; those are
 * refused with a message rather than loaded and left silent. Everything runs on the main thread.
 */
class CastMirror(
    /** The service rebuilds its scope after it has been stopped, so it is asked for each time. */
    private val scope: () -> CoroutineScope,
    private val localPlayer: () -> ExoPlayer?,
    private val resolve: suspend (MediaItem) -> CastStream?,
    private val say: (String) -> Unit,
) {
    private val _active = MutableStateFlow(false)

    /** Whether the receiver is the speaker; the phone's own output is muted while it is. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Set by [CastController.disconnect]: the listener chose this phone, so keep playing here. */
    var resumeHereOnEnd = false

    private var client: RemoteMediaClient? = null
    private var loading = false
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var syncJob: Job? = null
    private var loadedMediaId: String? = null
    private var lastRemoteState = MediaStatus.PLAYER_STATE_UNKNOWN
    private var endWasPlaying = false

    private val statusCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onRemoteStatus()
    }

    private val localListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null || mediaItem.mediaId == loadedMediaId) return
            val player = localPlayer() ?: return
            load(mediaItem, player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (loading) return
            val remote = client ?: return
            val remotePlaying = remote.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING
            if (playWhenReady && !remotePlaying) remote.play()
            if (!playWhenReady && remotePlaying) remote.pause()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (newPosition.mediaItemIndex != oldPosition.mediaItemIndex) return
            seekRemote(newPosition.positionMs)
        }
    }

    // ---- session lifecycle --------------------------------------------------

    fun onSessionBegan(session: CastSession, resumed: Boolean) {
        val remote = session.remoteMediaClient ?: return
        if (_active.value && remote === client) return
        release()
        client = remote
        remote.registerCallback(statusCallback)
        _active.value = true

        val player = localPlayer() ?: return
        player.addListener(localListener)
        val item = player.currentMediaItem
        val onReceiver = remote.mediaStatus?.mediaInfo?.customData?.optString(KEY_MEDIA_ID)
        if (resumed && item != null && item.mediaId == onReceiver) {
            // A session that outlived the app, already on this song: it keeps going.
            loadedMediaId = item.mediaId
        } else if (item != null) {
            load(item, player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
        }
        startSync()
    }

    fun onSessionEnding(session: CastSession) {
        val remote = session.remoteMediaClient ?: client ?: return
        endWasPlaying = remote.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING
    }

    /**
     * The receiver let go. The phone's player never stopped, so the song and the second in it
     * are already right here; only whether it is heard is in question. A listener who chose "this
     * phone" hears it carry on; a TV switched off or a Wi-Fi that dropped does not get the phone
     * playing out loud unprompted, so it is paused instead.
     */
    fun onSessionEnded() {
        if (!_active.value) return
        val keepPlaying = resumeHereOnEnd && endWasPlaying
        resumeHereOnEnd = false
        release()
        val player = localPlayer() ?: return
        if (!keepPlaying) player.pause()
    }

    fun onConnectFailed() = say("Couldn't connect to that device")

    fun release() {
        loadJob?.cancel()
        syncJob?.cancel()
        client?.unregisterCallback(statusCallback)
        client = null
        localPlayer()?.removeListener(localListener)
        loading = false
        loadedMediaId = null
        lastRemoteState = MediaStatus.PLAYER_STATE_UNKNOWN
        _active.value = false
    }

    // ---- the receiver moved -------------------------------------------------

    private fun onRemoteStatus() {
        val status = client?.mediaStatus ?: return
        val state = status.playerState
        val previous = lastRemoteState
        lastRemoteState = state
        if (loading) return
        val player = localPlayer() ?: return
        when {
            // Its own remote, or the TV's: answered on the phone, which owns the queue.
            previous == MediaStatus.PLAYER_STATE_PLAYING && state == MediaStatus.PLAYER_STATE_PAUSED &&
                player.playWhenReady -> player.pause()
            previous == MediaStatus.PLAYER_STATE_PAUSED && state == MediaStatus.PLAYER_STATE_PLAYING &&
                !player.playWhenReady -> player.play()
            state == MediaStatus.PLAYER_STATE_IDLE && status.idleReason == MediaStatus.IDLE_REASON_ERROR -> {
                say("The TV couldn't play this song")
            }
        }
    }

    // ---- loading and keeping in step ----------------------------------------

    private fun load(item: MediaItem, positionMs: Long, play: Boolean) {
        val remote = client ?: return
        loadJob?.cancel()
        val generation = ++loadGeneration
        loading = true
        loadedMediaId = item.mediaId
        loadJob = scope().launch {
            try {
                val info = buildMediaInfo(item)
                if (generation != loadGeneration || !_active.value) return@launch
                if (info == null) {
                    say("This song can't be cast")
                    return@launch
                }
                // Resolving took time, and the phone kept playing meanwhile: start from where it is now.
                val start = localPlayer()?.takeIf { it.currentMediaItem?.mediaId == item.mediaId }
                    ?.currentPosition?.coerceAtLeast(0L) ?: positionMs
                val request = MediaLoadRequestData.Builder()
                    .setMediaInfo(info)
                    .setAutoplay(localPlayer()?.playWhenReady ?: play)
                    .setCurrentTime(start)
                    .build()
                val result = remote.load(request).await()
                if (!result.status.isSuccess && generation == loadGeneration) {
                    Log.w(TAG, "load failed: ${result.status.statusCode}")
                    say("The TV couldn't play this song")
                }
            } finally {
                if (generation == loadGeneration) loading = false
            }
        }
    }

    private fun seekRemote(positionMs: Long) {
        if (loading) return
        client?.seek(MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0L)).build())
    }

    /** The phone is the clock; the receiver is moved to it whenever the two have drifted apart. */
    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope().launch {
            while (isActive) {
                delay(SYNC_EVERY_MS)
                if (loading) continue
                val remote = client ?: continue
                val player = localPlayer() ?: continue
                val status = remote.mediaStatus ?: continue
                if (status.mediaInfo?.customData?.optString(KEY_MEDIA_ID) != player.currentMediaItem?.mediaId) continue
                val playing = status.playerState == MediaStatus.PLAYER_STATE_PLAYING
                if (player.playWhenReady != playing && status.playerState != MediaStatus.PLAYER_STATE_BUFFERING) {
                    if (player.playWhenReady) remote.play() else remote.pause()
                    continue
                }
                if (playing && player.isPlaying &&
                    abs(remote.approximateStreamPosition - player.currentPosition) > DRIFT_MS
                ) {
                    seekRemote(player.currentPosition)
                }
            }
        }
    }

    private suspend fun buildMediaInfo(item: MediaItem): MediaInfo? {
        val stream = try {
            resolve(item)
        } catch (e: Exception) {
            Log.w(TAG, "could not resolve ${item.mediaId} for casting", e)
            null
        } ?: return null
        val metadata = item.mediaMetadata
        val music = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            metadata.title?.let { putString(MediaMetadata.KEY_TITLE, it.toString()) }
            metadata.artist?.let { putString(MediaMetadata.KEY_ARTIST, it.toString()) }
            metadata.albumTitle?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
            metadata.artworkUri?.takeIf { it.scheme == "http" || it.scheme == "https" }
                ?.let { addImage(WebImage(it)) }
        }
        return MediaInfo.Builder(stream.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(stream.mimeType)
            .setMetadata(music)
            .setCustomData(JSONObject().put(KEY_MEDIA_ID, item.mediaId))
            .build()
    }

    private suspend fun <R : Result> PendingResult<R>.await(): R =
        suspendCancellableCoroutine { continuation ->
            setResultCallback { continuation.resume(it) }
            continuation.invokeOnCancellation { cancel() }
        }

    companion object {
        private const val TAG = "ExhaleCast"
        private const val KEY_MEDIA_ID = "mediaId"
        private const val SYNC_EVERY_MS = 2_000L

        /** Further apart than this and the receiver is moved; closer is inaudible over a TV. */
        private const val DRIFT_MS = 1_500L

        /** What a stream holds, for a receiver that is told rather than left to guess. */
        fun mimeTypeOf(uri: Uri): String {
            uri.getQueryParameter("mime")?.takeIf { it.isNotBlank() }?.let { return it }
            val path = uri.lastPathSegment?.lowercase().orEmpty()
            return when {
                path.endsWith(".flac") -> "audio/flac"
                path.endsWith(".mp3") -> "audio/mpeg"
                path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
                path.endsWith(".webm") -> "audio/webm"
                path.endsWith(".m3u8") -> "application/x-mpegurl"
                else -> "audio/mp4"
            }
        }
    }
}
