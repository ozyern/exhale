/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ozyern.exhale.playback.MusicVideo
import com.ozyern.exhale.utils.YTPlayerUtils

/**
 * The one video player every music video surface draws from.
 *
 * Each surface used to build its own ExoPlayer. Turning the video on, going full screen and coming
 * back out each started a player from cold: a new connection, half a second of buffer, a seek to a
 * keyframe. That was the lag switching between video and audio. Now the player outlives the
 * surfaces: the inline video, full screen and the dock's mini player each attach a TextureView to
 * the same warm player, and the newest one shown gets the picture. With nothing attached it is
 * paused and kept for [KeepWarmMs], so turning the video straight back on doesn't start over.
 */
@OptIn(UnstableApi::class)
internal object SharedMusicVideo {
    private const val KeepWarmMs = 15_000L

    /** How long the video may run with no surface while one surface hands over to the next. */
    private const val HandOverMs = 600L

    /** How long a video got ready in advance waits to be turned on before it is let go. */
    private const val PrewarmHoldMs = 60_000L

    /** Re-seek the warm video when the song is within this much of the end of what it holds. */
    private const val PrewarmMarginMs = 4_000L

    /** Buffering this soon after a seek is the seek's, not a stall. */
    private const val SeekGraceMs = 3_000L

    /** The size of each request for the video's file. */
    private const val VideoChunkBytes = 4L * 1024 * 1024

    private var player: ExoPlayer? = null
    private var url: String? = null
    /** Every surface on screen that wants the picture, with its rank (see [attach]). */
    private val surfaces = ArrayList<Pair<TextureView, Int>>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val release = Runnable { releaseNow() }
    private val pause = Runnable { if (surfaces.isEmpty()) player?.playWhenReady = false }

    /** Whether a frame has reached [activeSurface] since it took the picture over. */
    var activeHasPicture by mutableStateOf(false)
        private set

    /** How many times the picture has run dry mid-play: watched, so Auto can step down (see MusicVideo.stepDownAuto). */
    var stalls by mutableIntStateOf(0)
        private set

    private var lastSeekAt = 0L

    /** The surface currently showing the picture. */
    var activeSurface by mutableStateOf<TextureView?>(null)
        private set

    private var errorListener: (() -> Unit)? = null

    /**
     * The video the person was last watching, kept when the player closes so the dock's mini player
     * can carry on showing it. Cleared when the song changes or the video is put away.
     */
    var docked by mutableStateOf<DockedVideo?>(null)

    data class DockedVideo(val songId: String, val found: MusicVideo.Found)

    /** The player for [stream], the warm one when it's the same video. */
    fun acquire(context: Context, stream: YTPlayerUtils.VideoStream, onError: () -> Unit): ExoPlayer {
        handler.removeCallbacks(release)
        errorListener = onError
        player?.let { if (url == stream.url) return it }
        releaseNow()
        return ExoPlayer.Builder(context.applicationContext)
            // Start on half a second of picture rather than the default two and a half: the song is
            // already playing, and the video only has to join it.
            // After a stall, two seconds before going again rather than one: resuming on a second of
            // picture on a slow connection ran straight into the next stall, which was the stutter.
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(8_000, 30_000, 500, 2_000)
                    .build(),
            )
            .build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                val factory = DefaultHttpDataSource.Factory()
                    .setUserAgent(stream.userAgent)
                    .setConnectTimeoutMs(6_000)
                    .setReadTimeoutMs(8_000)
                setMediaSource(
                    // 4 MB pieces, as the audio path asks for: half as many requests, each a gap.
                    ProgressiveMediaSource.Factory(
                        com.ozyern.exhale.playback.ChunkedHttpDataSource.Factory(factory, stream.contentLength, VideoChunkBytes),
                    ).createMediaSource(MediaItem.fromUri(stream.url)),
                )
                prepare()
                addListener(object : Player.Listener {
                    private var wasReady = false

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // A stall: it was playing, on screen, and ran out of picture. Not the first
                        // load, and not a seek's own buffering, which the surface asked for.
                        if (playbackState == Player.STATE_BUFFERING && wasReady && playWhenReady && activeSurface != null &&
                            android.os.SystemClock.uptimeMillis() - lastSeekAt > SeekGraceMs
                        ) {
                            stalls++
                        }
                        wasReady = playbackState == Player.STATE_READY
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) lastSeekAt = android.os.SystemClock.uptimeMillis()
                    }

                    override fun onRenderedFirstFrame() {
                        // Fired again after every surface change: this is the new surface's first frame.
                        activeHasPicture = true
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.w("MusicVideo", "video failed: ${error.errorCodeName} ${stream.width}x${stream.height} len=${stream.contentLength}", error)
                        errorListener?.invoke()
                    }
                })
                player = this
                url = stream.url
            }
    }

    /**
     * Gets the song's video ready before anyone asks for it: the player built, the file's header read
     * and up to half a minute from [positionMs] on downloaded, all with no surface and nothing
     * drawn. Tapping Video then only has to jump inside what is already here, instead of starting
     * a player from cold, which was most of the wait. Only on an unmetered connection, and a video
     * nobody turns on is let go after [PrewarmHoldMs].
     */
    fun prewarm(context: Context, stream: YTPlayerUtils.VideoStream, positionMs: Long) {
        // Already on screen: the surface keeps it in step itself.
        if (surfaces.isNotEmpty()) return
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivity == null || connectivity.isActiveNetworkMetered) return
        val warm = acquire(context, stream) {}
        warm.playWhenReady = false
        // Only when what it holds no longer covers where the song is: a seek that lands outside the
        // buffer throws it away and downloads again.
        val buffered = warm.bufferedPosition
        if (warm.playbackState == Player.STATE_IDLE || positionMs < warm.currentPosition || positionMs > buffered - PrewarmMarginMs) {
            warm.seekTo(positionMs)
        }
        handler.removeCallbacks(release)
        handler.postDelayed(release, PrewarmHoldMs)
    }

    /**
     * Shows the picture on [view], or on whichever attached surface outranks it: full screen over
     * the player, the player over the dock ([priority]), and among equals the newest, the way a
     * picture-in-picture hands over.
     *
     * Ranked, not newest-wins. A new stream (a quality change) re-attaches every surface showing
     * the video at once, in an order nobody controls, and the inline video still mounted under the
     * full-screen one could end up with the picture: full screen went black or froze.
     */
    fun attach(view: TextureView, priority: Int = 0) {
        // A surface is taking it, so it is not left alone. Closing the player detaches the player's
        // own surface a moment before the dock's attaches, and that detach queued the release: the
        // video carried on in the dock for fifteen seconds and then died.
        handler.removeCallbacks(release)
        handler.removeCallbacks(pause)
        surfaces.removeAll { it.first === view }
        surfaces.add(view to priority)
        pointAtBest()
    }

    fun detach(view: TextureView) {
        val wasActive = activeSurface === view
        surfaces.removeAll { it.first === view }
        if (wasActive) {
            if (surfaces.isNotEmpty()) {
                pointAtBest()
            } else {
                player?.clearVideoTextureView(view)
                activeSurface = null
            }
        }
        if (surfaces.isEmpty()) {
            // Paused only if nothing takes it within a moment: a surface handing over to the next
            // one (the player's to the dock's) passes through empty, and pausing there at once was
            // a visible stop in the video.
            handler.removeCallbacks(pause)
            handler.postDelayed(pause, HandOverMs)
            handler.removeCallbacks(release)
            handler.postDelayed(release, KeepWarmMs)
        }
    }

    /** The highest-ranked surface, the newest among equals. */
    private fun pointAtBest() {
        val best = surfaces.withIndex().maxWithOrNull(compareBy({ it.value.second }, { it.index }))?.value?.first ?: return
        point(best)
    }

    private fun point(view: TextureView) {
        if (activeSurface === view) return
        activeHasPicture = false
        player?.setVideoTextureView(view)
        activeSurface = view
    }

    private fun releaseNow() {
        handler.removeCallbacks(release)
        handler.removeCallbacks(pause)
        player?.release()
        player = null
        url = null
        activeSurface = null
        surfaces.clear()
    }
}
