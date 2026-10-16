/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The control bar follows the Windows app's video overlay (desktop/ui/player/VideoOverlay.kt).
 */

package com.ozyern.exhale.ui.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.utils.YTPlayerUtils
import kotlinx.coroutines.delay

/**
 * The music video on the whole screen, as a video player: landscape, edge to edge, system bars
 * away, and controls that come up on a tap and leave on their own.
 *
 * Only this is a player. In the Now Playing screen the video is the cover — silent pictures over
 * the song with nothing to operate — and full screen is where the transport belongs. The sound is
 * still the song's own stream: every control here drives the song, and the picture follows it.
 */
/**
 * Whether a full-screen video is up. The player turns the phone to landscape for it, and a player
 * in landscape normally switches to its landscape design — which took the Now Playing screen, and
 * with it the video and this very dialog, away the moment the screen turned.
 */
object FullscreenVideoState {
    var active by androidx.compose.runtime.mutableStateOf(false)

    /**
     * Whether the Now Playing screen is showing a song's video over its cover. Turning the device
     * on its side then goes to the full-screen video (issue #6) instead of the landscape player,
     * which has no video in it and left the person watching with the cover.
     */
    var watching by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
internal fun FullscreenMusicVideo(
    stream: YTPlayerUtils.VideoStream,
    onExit: () -> Unit,
) {
    val activity = LocalContext.current as? android.app.Activity

    HoldLandscape(activity)

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        FullscreenWindow()
        BackHandler(onBack = onExit)
        FullscreenVideoBody(stream, onExit)
    }
}

/**
 * Landscape while the composable that calls this is up, whichever way the phone was held; upright
 * again on the way out. Shared by the full-screen video and the horizontal lyrics.
 */
@Composable
internal fun HoldLandscape(activity: android.app.Activity?) {
    DisposableEffect(activity) {
        FullscreenVideoState.active = true
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (activity != null) {
                // Back upright first: "unspecified" alone leaves the screen however the full-screen
                // turned it on a device that isn't being rotated, and the player came back sideways.
                // Then the system's own rotation again, a moment later.
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                // Held until the screen is upright again: let go while it was still sideways, and
                // for a moment the player switched to its landscape design, dropping the Now Playing
                // screen and with it the video that was meant to carry on there.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    FullscreenVideoState.active = false
                    activity.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }, 900)
            } else {
                FullscreenVideoState.active = false
            }
        }
    }
}

/** Inside a full-screen Dialog: the whole display, edge to edge, system bars away. */
@Composable
internal fun FullscreenWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val view = LocalView.current
    DisposableEffect(window) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            WindowInsetsControllerCompat(window, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose { }
    }
}

@Composable
private fun FullscreenVideoBody(stream: YTPlayerUtils.VideoStream, onExit: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    run {
        // Shown on arrival and on any tap; gone three seconds after the last one while it plays.
        var controls by remember { mutableStateOf(true) }
        var touched by remember { mutableIntStateOf(0) }
        LaunchedEffect(controls, touched, isPlaying) {
            if (controls && isPlaying) {
                delay(3_000)
                controls = false
            }
        }

        // The video is recorded as the backdrop the controls are cut from, so every control is a
        // piece of glass over the picture — it bends the video at its rim — as Apple's player is.
        val videoBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls; touched++ },
                        // Double-tap either half to skip ten seconds, as video players do.
                        onDoubleTap = { at ->
                            val back = at.x < size.width / 2f
                            val target = player.currentPosition + if (back) -10_000L else 10_000L
                            player.seekTo(target.coerceIn(0L, player.duration.coerceAtLeast(0L)))
                            controls = true
                            touched++
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(videoBackdrop), contentAlignment = Alignment.Center) {
                MusicVideoSurface(
                    stream = stream,
                    positionMs = { player.currentPosition },
                    isPlaying = isPlaying,
                    fitInside = true,
                    onError = onExit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = controls,
                enter = fadeIn(tween(180)) + androidx.compose.animation.scaleIn(tween(240), initialScale = 0.96f),
                exit = fadeOut(tween(260)) + androidx.compose.animation.scaleOut(tween(260), targetScale = 0.98f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // A soft dim so white glyphs hold on a bright frame; the glass does the rest.
                        .background(Color.Black.copy(alpha = 0.18f))
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    // Top left: the way out.
                    VideoGlassButton(
                        icon = R.drawable.close,
                        diameter = 44.dp,
                        iconSize = 20.dp,
                        backdrop = videoBackdrop,
                        modifier = Modifier.align(Alignment.TopStart),
                        onClick = onExit,
                    )

                    // Centre: ten seconds back, play or pause, ten seconds on — each a clear disc.
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoGlassButton(R.drawable.replay, 58.dp, 26.dp, videoBackdrop, badge = "10") {
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); touched++
                        }
                        VideoGlassButton(if (isPlaying) R.drawable.pause else R.drawable.play, 78.dp, 36.dp, videoBackdrop) {
                            if (player.isPlaying) player.pause() else player.play(); touched++
                        }
                        VideoGlassButton(R.drawable.replay, 58.dp, 26.dp, videoBackdrop, badge = "10", mirrored = true) {
                            val end = player.duration.coerceAtLeast(0L)
                            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end)); touched++
                        }
                    }

                    // Bottom: one glass capsule — what is playing, the track buttons, and the line.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                            .then(
                                com.ozyern.exhale.ui.component.rememberChromeGlassModifier(
                                    shape = RoundedCornerShape(28.dp),
                                    dark = true,
                                    tintAlpha = 0.22f,
                                    blurRadius = 24.dp,
                                    backdrop = videoBackdrop,
                                ),
                            )
                            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = metadata?.title.orEmpty(),
                                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                                    style = TextStyle(fontSize = 13.sp),
                                    color = Color.White.copy(alpha = 0.66f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            VideoGlyph(R.drawable.skip_previous, 22.dp) { player.seekToPrevious(); touched++ }
                            VideoGlyph(R.drawable.skip_next, 22.dp, enabled = canSkipNext) { player.seekToNext(); touched++ }
                        }
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.padding(end = 8.dp)) {
                            VideoScrubber(player = player, onTouched = { touched++ })
                        }
                    }
                }
            }
        }
    }
}

/**
 * A round control cut from clear glass over the video: it bends the picture at its rim, and
 * squeezes a little under the finger.
 */
@Composable
private fun VideoGlassButton(
    icon: Int,
    diameter: Dp,
    iconSize: Dp,
    backdrop: com.kyant.backdrop.Backdrop,
    modifier: Modifier = Modifier,
    badge: String? = null,
    mirrored: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squeeze by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "videoGlassPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .graphicsLayer { scaleX = squeeze; scaleY = squeeze }
            .then(
                com.ozyern.exhale.ui.component.rememberChromeGlassModifier(
                    shape = CircleShape,
                    dark = true,
                    tintAlpha = 0.14f,
                    blurRadius = 16.dp,
                    backdrop = backdrop,
                ),
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { if (mirrored) scaleX = -1f },
        )
        if (badge != null) {
            Text(
                text = badge,
                style = TextStyle(fontSize = (iconSize.value * 0.3f).sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(top = (iconSize.value * 0.12f).dp),
            )
        }
    }
}
/**
 * Apple's scrubber: a thin line that thickens under the finger, the elapsed time under its left end
 * and what is left under its right. Tap anywhere on it to jump, or drag.
 */
@Composable
private fun VideoScrubber(player: androidx.media3.common.Player, onTouched: () -> Unit) {
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    val fraction = when {
        dragging -> dragValue
        duration > 0 -> (position.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val shown = if (dragging) (dragValue * duration).toLong() else position
    val thickness by androidx.compose.animation.core.animateDpAsState(
        if (dragging) 10.dp else 5.dp,
        androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "scrubThickness",
    )
    Column {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .size(height = 24.dp, width = 0.dp)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { at ->
                        player.seekTo(((at.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                        onTouched()
                    }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { at -> dragging = true; dragValue = (at.x / size.width).coerceIn(0f, 1f); onTouched() },
                        onHorizontalDrag = { change, _ ->
                            dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                            onTouched()
                        },
                        onDragEnd = { player.seekTo((dragValue * duration).toLong()); dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .size(height = thickness, width = 0.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .size(height = thickness, width = 0.dp)
                        .background(Color.White),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            VideoTime(formatVideoTime(shown))
            Spacer(Modifier.weight(1f))
            VideoTime(if (duration > 0) "−" + formatVideoTime(duration - shown) else formatVideoTime(0L))
        }
    }
}
@Composable
private fun VideoTime(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        color = Color.White.copy(alpha = 0.8f),
    )
}

/** A bare white glyph; [badge] is drawn small in its middle (the "10" of a ten-second skip). */
@Composable
private fun VideoGlyph(
    icon: Int,
    size: Dp,
    enabled: Boolean = true,
    badge: String? = null,
    mirrored: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = false, color = Color.White),
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.32f),
            modifier = Modifier
                .size(size)
                .graphicsLayer { if (mirrored) scaleX = -1f },
        )
        if (badge != null) {
            Text(
                text = badge,
                style = TextStyle(fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(top = (size.value * 0.12f).dp),
            )
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
