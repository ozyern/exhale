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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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

/** Ahead of the song by more than this, the picture holds still until the song catches it up. */
private const val HOLD_AHEAD_MS = 200L

/**
 * How far past the song a seek lands, so the picture is there and waiting when the song arrives:
 * about what fetching from a new place in the file takes.
 */
private const val CATCH_UP_LEAD_MS = 900L

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
    /** Fill the whole box and crop the overflow evenly, as a square thumbnail does with a wide picture. */
    cropToFill: Boolean = false,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    /** Where the bottom of the picture is, in root coordinates, once laid out. */
    onPictureBottom: (Float) -> Unit = {},
    /** Drawn over the picture itself, in its own bounds: controls that belong to the video. */
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    onFirstFrame: () -> Unit = {},
    onError: () -> Unit = {},
    /** Which surface gets the picture when several are up: full screen 2, the player 1, the dock 0. */
    priority: Int = 1,
) {
    val context = LocalContext.current
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val failed by rememberUpdatedState(onError)

    // Shared and kept warm across surfaces: see SharedMusicVideo. This surface only borrows it.
    val player = remember(stream.url) { SharedMusicVideo.acquire(context, stream) { failed() } }
    var textureView by remember { mutableStateOf<android.view.TextureView?>(null) }
    DisposableEffect(player, textureView) {
        val view = textureView
        if (view != null) SharedMusicVideo.attach(view, priority)
        onDispose { if (view != null) SharedMusicVideo.detach(view) }
    }
    val active = textureView != null && SharedMusicVideo.activeSurface === textureView
    // The picture is up once a frame has reached this surface since it took the player over.
    val rendered = active && SharedMusicVideo.activeHasPicture
    LaunchedEffect(rendered) { if (rendered) firstFrame() }

    val playing by rememberUpdatedState(isPlaying)
    val position by rememberUpdatedState(positionMs)
    LaunchedEffect(player, active) {
        // Only the surface showing the picture drives the player; another one behind it (the inline
        // video under full screen) would otherwise fight it.
        if (!active) return@LaunchedEffect
        // Warm and already in step: no seek, which on a video stream is a stall back to a keyframe.
        // Otherwise to where the song is, or a little past it when the picture has to be fetched:
        // it holds there until the song arrives (below) instead of starting late and chasing it.
        val start = position()
        val inBuffer = start >= player.currentPosition && start < player.bufferedPosition - 500
        if (abs(player.currentPosition - start) > RESEEK_MS || player.playbackState != Player.STATE_READY) {
            player.seekTo(if (inBuffer) start else start + CATCH_UP_LEAD_MS)
        }
        while (isActive) {
            // In the background nobody sees the video: stop decoding it, and line it back up
            // with the song on return.
            if (!com.ozyern.exhale.utils.isAppVisible) {
                player.playWhenReady = false
                com.ozyern.exhale.utils.awaitAppVisible()
                player.seekTo(position() + CATCH_UP_LEAD_MS)
            }
            val target = position()
            val drift = target - player.currentPosition
            when {
                !playing -> player.playWhenReady = false
                // Still fetching: it starts the moment it can, unless it will be ahead of the song
                // then (a seek just past it), in which case it waits there with its first frame up.
                player.playbackState != Player.STATE_READY -> player.playWhenReady = drift >= -HOLD_AHEAD_MS
                // Far out (a stall it fell behind in, a seek in the song): to just past the song,
                // to hold there. Seeking straight to the song's position landed behind it again by
                // the time the seek was done, and the next correction seeked again: one hiccup
                // became a run of stalls.
                drift > RESEEK_MS -> player.seekTo(target + CATCH_UP_LEAD_MS)
                drift < -RESEEK_MS -> player.seekTo(target)
                // Ahead: the frame holds still until the song reaches it. Slowing to 92% instead
                // took a quarter of a minute to line up a second's lead, all of it visibly out.
                drift < -HOLD_AHEAD_MS -> player.playWhenReady = false
                else -> {
                    player.playWhenReady = true
                    val speed = if (abs(drift) < IN_STEP_MS) 1f else 1f + (drift / 4_000f).coerceIn(-MAX_NUDGE, MAX_NUDGE)
                    if (abs(player.playbackParameters.speed - speed) > 0.005f) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                    }
                }
            }
            delay(100)
        }
    }

    // Faded in on its first picture, and not out again while it keeps the picture. A new stream (a
    // quality change) starts its player cold, and fading to black until that first frame was half a
    // second of nothing between two good pictures; the last frame now stays up until the new one lands.
    var hadPicture by remember { mutableStateOf(false) }
    LaunchedEffect(rendered) { if (rendered) hadPicture = true }
    val alpha by animateFloatAsState(if (rendered || hadPicture && active) 1f else 0f, tween(350), label = "musicVideoAlpha")
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
        val crop = cropToFill || !fitInside && maxHeight.value > 0f && maxHeight != androidx.compose.ui.unit.Dp.Infinity &&
            maxWidth / aspect > maxHeight
        // Cropping to fill a box wider than tall cuts top and bottom; one taller than wide cuts the sides.
        val cropSides = cropToFill && maxWidth / aspect < maxHeight
        val fullWidth = if (cropSides) maxHeight * aspect else maxWidth
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
                factory = { viewContext -> android.view.TextureView(viewContext).also { textureView = it } },
                onRelease = { if (textureView === it) textureView = null },
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
