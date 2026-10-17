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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
/** Drift the video is left with: below this it plays at normal speed. */
private const val IN_STEP_MS = 60L

/**
 * Drift past which the video seeks instead of catching up. It was the only correction, at 220ms, and
 * ordinary jitter between the two clocks crossed that: the video kept seeking, and every seek on a
 * video stream is a stall back to a keyframe, which was the stutter.
 */
private const val RESEEK_MS = 1_500L

/** The most the video is sped up or slowed down to close a drift, as a fraction of normal speed. */
private const val MAX_NUDGE = 0.08f

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
    /** Fit the picture inside both edges (full screen); otherwise it takes the full width. */
    fitInside: Boolean = false,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    /** Where the bottom of the picture is, in root coordinates, once laid out. */
    onPictureBottom: (Float) -> Unit = {},
    /** Drawn over the picture itself, in its own bounds: controls that belong to the video. */
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    onFirstFrame: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    var rendered by remember(stream.url) { mutableStateOf(false) }
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val failed by rememberUpdatedState(onError)

    val player = remember(stream.url) {
        ExoPlayer.Builder(context)
            // Start on half a second of picture rather than the default two and a half: the song is
            // already playing, and the video only has to join it.
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(8_000, 30_000, 500, 1_000)
                    .build(),
            )
            .build().apply {
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
                    android.util.Log.w("MusicVideo", "video failed: ${error.errorCodeName} ${stream.width}x${stream.height} len=${stream.contentLength}", error)
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
            // Only a playing picture is corrected; one still buffering is behind for a reason that a
            // seek would only make worse.
            if (player.playbackState == Player.STATE_READY && playing) {
                val drift = target - player.currentPosition
                val speed = when {
                    abs(drift) > RESEEK_MS -> {
                        player.seekTo(target)
                        1f
                    }
                    abs(drift) < IN_STEP_MS -> 1f
                    // Caught up gradually: about a second of video per twelve to close a big gap,
                    // with no visible jump at any point.
                    else -> 1f + (drift / 4_000f).coerceIn(-MAX_NUDGE, MAX_NUDGE)
                }
                if (abs(player.playbackParameters.speed - speed) > 0.005f) {
                    player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                }
            }
            delay(250)
        }
    }

    val alpha by animateFloatAsState(if (rendered) 1f else 0f, tween(350), label = "musicVideoAlpha")
    // A TextureView, not PlayerView's default SurfaceView. A SurfaceView is a hole punched through
    // the window to a layer underneath it, so anything the player draws over it — the black
    // backing, the fade at the foot — covers the picture entirely; that was the black box. A
    // TextureView is an ordinary view, composited in place with everything around it.
    val aspect = if (stream.width > 0 && stream.height > 0) stream.width.toFloat() / stream.height else 16f / 9f
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = alignment) {
        val heightLimited = fitInside && maxHeight.value > 0f && maxWidth / maxHeight > aspect
        // Inline, a picture taller than the room under the top strip used to be centred by its
        // parent and so slid up underneath the strip, its buttons and "Playing from" over the
        // picture. It still fills the width, but is now cropped evenly inside that room.
        val crop = !fitInside && maxHeight.value > 0f && maxHeight != androidx.compose.ui.unit.Dp.Infinity &&
            maxWidth / aspect > maxHeight
        val fullWidth = maxWidth
        val reportBottom by rememberUpdatedState(onPictureBottom)
        androidx.compose.foundation.layout.Box(
            (
                when {
                    heightLimited ->
                        androidx.compose.ui.Modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true)
                    crop ->
                        androidx.compose.ui.Modifier.fillMaxWidth().fillMaxHeight()
                            .then(androidx.compose.ui.Modifier.clipToBounds())
                    else -> androidx.compose.ui.Modifier.fillMaxWidth().aspectRatio(aspect)
                }
                ).onGloballyPositioned { reportBottom(it.positionInRoot().y + it.size.height) },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            AndroidView(
                factory = { viewContext ->
                    android.view.TextureView(viewContext).also { player.setVideoTextureView(it) }
                },
                update = { player.setVideoTextureView(it) },
                onRelease = { player.clearVideoTextureView(it) },
                modifier = (
                    if (crop) {
                        androidx.compose.ui.Modifier.requiredSize(fullWidth, fullWidth / aspect)
                    } else {
                        androidx.compose.ui.Modifier.matchParentSize()
                    }
                    )
                    .graphicsLayer { this.alpha = alpha },
            )
            if (rendered) overlay()
        }
    }
}
