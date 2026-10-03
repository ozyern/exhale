package com.ozyern.exhale.ui.player



import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * A silent, looping ExoPlayer for one animated cover, falling back from [primary] to [fallback]
 * if the first will not play. Shared by the card-sized [CanvasArtworkPlayer] and the full-bleed
 * [CanvasHeroVideo]; only the surface each one draws onto differs.
 */
private class CanvasPlayback(
    val player: ExoPlayer,
    val ready: () -> Boolean,
)

@Composable
private fun rememberCanvasPlayback(
    primary: String?,
    fallback: String?,
    isPlaying: Boolean,
): CanvasPlayback? {
    val context = LocalContext.current
    val initial = primary ?: fallback ?: return null
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.proxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                                host.endsWith("googleusercontent.com") ||
                                host.endsWith("youtube.com") ||
                                host.endsWith("youtube-nocookie.com") ||
                                host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val clientParam = request.url.queryParameter("c")?.trim().orEmpty()
                    val isWeb =
                        clientParam.startsWith("WEB", ignoreCase = true) ||
                                clientParam.startsWith("WEB_REMIX", ignoreCase = true) ||
                                request.url.toString().contains("c=WEB", ignoreCase = true)

                    val userAgent =
                        when {
                            clientParam.startsWith("WEB", ignoreCase = true) ||
                                    clientParam.startsWith("WEB_REMIX", ignoreCase = true) -> YouTubeClient.USER_AGENT_WEB

                            clientParam.startsWith("IOS", ignoreCase = true) -> YouTubeClient.IOS.userAgent

                            clientParam.startsWith("ANDROID_VR", ignoreCase = true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent

                            clientParam.startsWith("ANDROID", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent

                            else -> YouTubeClient.USER_AGENT_WEB
                        }

                    val builder = request.newBuilder().header("User-Agent", userAgent)
                    if (isWeb) {
                        builder.header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                        builder.header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                    }

                    chain.proceed(builder.build())
                }
                .build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                ),
            )
        }
    val exoPlayer =
        remember(initial, mediaSourceFactory) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false,
                    )
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = isPlaying
                }
        }

    LaunchedEffect(isPlaying) {
        if (exoPlayer.playWhenReady != isPlaying) {
            exoPlayer.playWhenReady = isPlaying
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val next =
                        when (currentUrl) {
                            primary -> fallback
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem.Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    return remember(exoPlayer) { CanvasPlayback(exoPlayer) { isVideoReady } }
}

@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val playback = rememberCanvasPlayback(
        primary = primaryUrl?.takeIf { it.isNotBlank() },
        fallback = fallbackUrl?.takeIf { it.isNotBlank() },
        isPlaying = isPlaying,
    ) ?: return
    val exoPlayer = playback.player

    val alpha by animateFloatAsState(
        targetValue = if (playback.ready()) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                player = exoPlayer
                useController = false
                this.resizeMode = resizeMode
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) view.player = exoPlayer
            if (view.resizeMode != resizeMode) view.resizeMode = resizeMode
        },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

/**
 * An animated cover drawn full-bleed at the top of the player, dissolving into whatever is below it.
 *
 * Drawn on a TextureView rather than PlayerView's default SurfaceView. A SurfaceView is a hole
 * punched through the window, so nothing Compose does — an alpha, a mask — ever reaches its
 * pixels; the clip would sit on the screen as a hard-edged rectangle over the colours it is meant
 * to melt into. A TextureView is an ordinary view, and the fade can be applied to it directly.
 *
 * The fade itself is a RenderEffect on the view's own node, not a DstIn gradient in the caller's
 * draw scope: a TextureView's frames are composited from its own surface, and a Compose blend drawn
 * over the node lands on the layer around the video rather than on the video. (Adapted from
 * BitChord's CanvasArtworkPlayer, GPL-3.0.)
 *
 * @param bottomFade the share of the view's height, from the bottom, given to the dissolve.
 * @param alpha read while drawing, so a per-frame fade costs no recomposition.
 */
@Composable
internal fun CanvasHeroVideo(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    bottomFade: Float,
    alpha: () -> Float,
    onRenderedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Read while drawing, like [alpha]: the paused cover sinking back. */
    scale: () -> Float = { 1f },
) {
    val playback = rememberCanvasPlayback(
        primary = primaryUrl?.takeIf { it.isNotBlank() },
        fallback = fallbackUrl?.takeIf { it.isNotBlank() },
        isPlaying = isPlaying,
    )
    val latestOnRendered by rememberUpdatedState(onRenderedChanged)
    val rendered = playback?.ready?.invoke() == true
    LaunchedEffect(rendered) { latestOnRendered(rendered) }
    DisposableEffect(Unit) { onDispose { latestOnRendered(false) } }
    playback ?: return
    val exoPlayer = playback.player

    // The decoded clip's shape, for the cover-crop transform below.
    val videoAspect = remember(exoPlayer) { mutableFloatStateOf(0f) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect.floatValue =
                        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    val fadeIn by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "canvasHeroFadeIn",
    )

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                isOpaque = false
                exoPlayer.setVideoTextureView(this)
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    val texture = view as TextureView
                    texture.applyCoverCrop(videoAspect.floatValue)
                    texture.applyBottomFade(bottomFade)
                }
            }
        },
        // Snapshot-observed: the reads below re-run this block, not the composition around it.
        update = { view ->
            view.applyCoverCrop(videoAspect.floatValue)
            view.applyBottomFade(bottomFade)
            view.alpha = (alpha() * fadeIn).coerceIn(0f, 1f)
            val s = scale()
            view.pivotX = view.width / 2f
            view.pivotY = view.height * 0.42f
            view.scaleX = s
            view.scaleY = s
        },
        onRelease = { view -> exoPlayer.clearVideoTextureView(view) },
        modifier = modifier,
    )
}

/** A TextureView stretches its content to its bounds; scale it back so the clip covers them. */
private fun TextureView.applyCoverCrop(clipAspect: Float) {
    if (width <= 0 || height <= 0 || !clipAspect.isFinite() || clipAspect <= 0f) {
        setTransform(Matrix())
        return
    }
    val viewAspect = width.toFloat() / height
    val matrix = Matrix().apply {
        if (clipAspect > viewAspect) {
            setScale(clipAspect / viewAspect, 1f, width / 2f, height / 2f)
        } else {
            setScale(1f, viewAspect / clipAspect, width / 2f, height / 2f)
        }
    }
    setTransform(matrix)
}

private fun TextureView.applyBottomFade(fraction: Float) {
    // Called on every alpha frame; only rebuild the effect when its inputs actually change.
    val key = fraction to height
    if (tag == key) return
    tag = key
    if (fraction <= 0.001f || height <= 0) {
        setRenderEffect(null)
        return
    }
    val gradient = LinearGradient(
        0f,
        height * (1f - fraction.coerceAtMost(1f)),
        0f,
        height.toFloat(),
        android.graphics.Color.BLACK,
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP,
    )
    // createOffsetEffect(0, 0) is the identity over the node's own content: the only way to name
    // "what this view drew" as the destination of a blend.
    setRenderEffect(
        RenderEffect.createBlendModeEffect(
            RenderEffect.createOffsetEffect(0f, 0f),
            RenderEffect.createShaderEffect(gradient),
            BlendMode.DST_IN,
        ),
    )
}
