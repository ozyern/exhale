package com.ozyern.exhale.artistvideo

import android.content.Context
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import okhttp3.OkHttpClient

/**
 * A looping, muted motion-video player that can outlive the view showing it: hoist it above a
 * lazy list and the hero can scroll out of composition and back without rebuffering.
 * [active] pauses decoding (off screen, app in background) without dropping the buffer.
 */
@Stable
class ArtistVideoPlayer internal constructor(context: Context, videoUrl: String) {
    internal val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(sharedClient)),
            ),
        )
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false,
            )
            // The hero is screen-wide and partly blurred, so higher resolution is unnecessary.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1080, 1080)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(videoUrl.trim()))
            prepare()
            playWhenReady = true
        }

    internal var isReady by mutableStateOf(false)

    var active: Boolean
        get() = exoPlayer.playWhenReady
        set(value) {
            exoPlayer.playWhenReady = value
        }

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isReady = true
                }
            },
        )
    }

    internal fun release() = exoPlayer.release()

    private companion object {
        val sharedClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    }
}

@Composable
fun rememberArtistVideoPlayer(videoUrl: String): ArtistVideoPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(videoUrl) { ArtistVideoPlayer(context, videoUrl) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

@Composable
fun ArtistVideo(
    videoUrl: String,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    onClick: () -> Unit,
) = ArtistVideo(rememberArtistVideoPlayer(videoUrl), modifier, resizeMode, onClick = onClick)

@Composable
fun ArtistVideo(
    player: ArtistVideoPlayer,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    onFrame: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val videoAlpha by animateFloatAsState(
        targetValue = if (player.isReady) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "videoAlpha"
    )

    // Match the still image's edges so the hero blur does not show a seam.
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick() },
    ) {
        AndroidView(
            factory = { viewContext ->
                AspectRatioFrameLayout(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    this.resizeMode = resizeMode

                    val textureView = VideoFrameTextureView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        this.onFrame = onFrame
                    }
                    addView(textureView)
                    player.exoPlayer.setVideoTextureView(textureView)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { view ->
                (view.getChildAt(0) as? VideoFrameTextureView)?.onFrame = onFrame
                if (view.resizeMode != resizeMode) {
                    view.resizeMode = resizeMode
                }
            },
            onRelease = { view ->
                (view.getChildAt(0) as? TextureView)?.let(player.exoPlayer::clearVideoTextureView)
            },
            modifier = Modifier
                .matchParentSize()
                .alpha(videoAlpha),
        )
    }
}
