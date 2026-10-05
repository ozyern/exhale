/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ozyern.exhale.utils.YTPlayerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Past this the picture is visibly ahead of or behind the singer, and it is pulled back. */
private const val RESYNC_THRESHOLD_MS = 220L

/**
 * The music video, silent, kept in step with the song.
 *
 * It is pictures only: the sound is still the song's own stream, through the app's audio chain, so
 * switching to the video costs nothing in sound quality. Every half second the picture is checked
 * against [positionMs] and nudged back if it has drifted; it plays and pauses with [isPlaying].
 * Fades in on its first frame so there is never a black flash over the cover.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MusicVideoSurface(
    stream: YTPlayerUtils.VideoStream,
    positionMs: () -> Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onFirstFrame: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    var rendered by remember(stream.url) { mutableStateOf(false) }
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val failed by rememberUpdatedState(onError)

    val player = remember(stream.url) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            val factory = DefaultHttpDataSource.Factory().setUserAgent(stream.userAgent)
            setMediaSource(
                ProgressiveMediaSource.Factory(com.ozyern.exhale.playback.ChunkedHttpDataSource.Factory(factory, stream.contentLength))
                    .createMediaSource(MediaItem.fromUri(stream.url)),
            )
            prepare()
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    rendered = true
                    firstFrame()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    failed()
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val playing by rememberUpdatedState(isPlaying)
    val position by rememberUpdatedState(positionMs)
    LaunchedEffect(player) {
        player.seekTo(position())
        while (isActive) {
            // In the background nobody sees the video: stop decoding it, and line it back up
            // with the song on return.
            if (!com.ozyern.exhale.utils.isAppVisible) {
                player.playWhenReady = false
                com.ozyern.exhale.utils.awaitAppVisible()
                player.seekTo(position())
            }
            player.playWhenReady = playing
            val target = position()
            if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                if (abs(player.currentPosition - target) > RESYNC_THRESHOLD_MS) player.seekTo(target)
            }
            delay(500)
        }
    }

    val alpha by animateFloatAsState(if (rendered) 1f else 0f, tween(350), label = "musicVideoAlpha")
    // A TextureView, not PlayerView's default SurfaceView. A SurfaceView is a hole punched through
    // the window to a layer underneath it, so anything the player draws over it — the black
    // backing, the fade at the foot — covers the picture entirely; that was the black box. A
    // TextureView is an ordinary view, composited in place with everything around it.
    val aspect = if (stream.width > 0 && stream.height > 0) stream.width.toFloat() / stream.height else 16f / 9f
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        AndroidView(
            factory = { viewContext ->
                android.view.TextureView(viewContext).also { player.setVideoTextureView(it) }
            },
            update = { player.setVideoTextureView(it) },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .graphicsLayer { this.alpha = alpha },
        )
    }
}
