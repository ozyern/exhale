/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import com.ozyern.exhale.ui.component.liquid.interactiveGlass
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.extensions.toggleRepeatMode
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.LyricsV2
import com.ozyern.exhale.ui.menu.PlayerMenu
import com.ozyern.exhale.ui.utils.ShowMediaInfo
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val GlassTitleFont = FontFamily(Font(R.font.unbounded_semibold, FontWeight.SemiBold))
private val GlassLabelFont = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))

private enum class GlassPanel { Artwork, Lyrics, Queue }

/**
 * The Liquid Glass player: the cover large and square in the middle of its own colour, and every
 * control a piece of glass over it — round discs for the small things, a wide pill for Play, pills
 * for shuffle, the stream and repeat. The glass is real: everything under it is recorded and each
 * control blurs, saturates and bends that at its rim, tinted in the cover's colour.
 *
 * The heading's "Now Playing · source" opens the queue in the cover's place; the quote opens the
 * lyrics there; the stream pill shows what is playing and how.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiquidGlassPlayerScreen(
    playerSheetState: BottomSheetState,
    navController: NavController,
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentFormat: FormatEntity?,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val haptic = LocalHapticFeedback.current
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val shuffle by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val artUrl = mediaMetadata.thumbnailUrl
    val palette = rememberArtworkPalette(artUrl)
    val accent by animateColorAsState(palette.accent, tween(900), label = "glassAccent")
    // The ground: the cover's colour, deep, falling to black — the tint the glass is cut from.
    val ground by animateColorAsState(
        lerp(palette.colors.firstOrNull() ?: palette.accent, Color.Black, 0.55f),
        tween(900),
        label = "glassGround",
    )
    var panel by remember { mutableStateOf(GlassPanel.Artwork) }
    val backdrop = rememberLayerBackdrop()

    val openMenu: () -> Unit = {
        menuState.show {
            PlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = playerSheetState,
                onShowDetailsDialog = { bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) } },
                onDismiss = menuState::dismiss,
            )
        }
    }

    CompositionLocalProvider(LocalPlayerAccent provides accent) {
        Box(modifier.fillMaxSize()) {
            // ---- Everything the glass sees: recorded, and drawn beneath the controls ----
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(Color.Black),
            ) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.55f }
                        .blur(90.dp),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to ground.copy(alpha = 0.55f),
                                0.55f to ground.copy(alpha = 0.78f),
                                1f to Color.Black.copy(alpha = 0.92f),
                            ),
                        ),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 22.dp),
            ) {
                // ---- Heading ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    GlassDisc(
                        backdrop = backdrop,
                        tint = accent,
                        size = 52.dp,
                        onClick = { playerSheetState.collapseSoft() },
                    ) {
                        Icon(painterResource(R.drawable.expand_more), null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                panel = if (panel == GlassPanel.Queue) GlassPanel.Artwork else GlassPanel.Queue
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = if (panel == GlassPanel.Queue) "UP NEXT" else "NOW PLAYING",
                            style = TextStyle(fontFamily = GlassLabelFont, fontSize = 14.sp, letterSpacing = 2.6.sp),
                            color = Color.White,
                        )
                        Text(
                            text = queueTitle?.takeIf { it.isNotBlank() } ?: mediaMetadata.album?.title ?: "Exhale",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlassDisc(backdrop = backdrop, tint = accent, size = 52.dp, onClick = openMenu) {
                        Icon(painterResource(R.drawable.more_vert), null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                // ---- The cover, or what took its place ----
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                ) {
                    AnimatedContent(
                        targetState = panel,
                        transitionSpec = {
                            (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(180)) + scaleOut(targetScale = 0.96f))
                        },
                        label = "glassPanel",
                    ) { shown ->
                        when (shown) {
                            GlassPanel.Artwork -> GlassCover(
                                artUrl = artUrl,
                                isPlaying = isPlaying,
                                onSwipe = { forward ->
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    if (forward) playerConnection.seekToNext() else playerConnection.seekToPrevious()
                                },
                            )
                            GlassPanel.Lyrics -> LyricsV2(
                                sliderPositionProvider = sliderPositionProvider,
                                modifier = Modifier.fillMaxSize(),
                            )
                            GlassPanel.Queue -> NowPlayingQueue(
                                navController = navController,
                                playerSheetState = playerSheetState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // ---- Title, and the two things done to it ----
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata.title,
                            style = TextStyle(fontFamily = GlassTitleFont, fontSize = 27.sp, letterSpacing = (-0.2).sp),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_500),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = mediaMetadata.artists.joinToString { it.name },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassDisc(
                        backdrop = backdrop,
                        tint = accent,
                        size = 52.dp,
                        lit = liked,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playerConnection.toggleLike()
                        },
                    ) {
                        Icon(
                            painterResource(if (liked) R.drawable.favorite_filled else R.drawable.favorite_outline),
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassDisc(
                        backdrop = backdrop,
                        tint = accent,
                        size = 52.dp,
                        lit = panel == GlassPanel.Lyrics,
                        onClick = { panel = if (panel == GlassPanel.Lyrics) GlassPanel.Artwork else GlassPanel.Lyrics },
                    ) {
                        Icon(painterResource(R.drawable.ic_quote), null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))

                GlassScrubber(
                    accent = accent,
                    positionProvider = positionProvider,
                    durationProvider = durationProvider,
                    sliderPositionProvider = sliderPositionProvider,
                    onScrub = onSliderValueChange,
                    onScrubFinished = onSliderValueChangeFinished,
                )

                Spacer(Modifier.height(18.dp))

                // ---- Transport: two discs and the Play pill between them ----
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    GlassDisc(
                        backdrop = backdrop,
                        tint = accent,
                        size = 64.dp,
                        enabled = canSkipPrevious,
                        onClick = playerConnection::seekToPrevious,
                    ) {
                        Icon(painterResource(R.drawable.ic_np_previous), null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    GlassPill(
                        backdrop = backdrop,
                        tint = accent,
                        lit = true,
                        height = 64.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (playbackState == Player.STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                    ) {
                        AnimatedContent(targetState = isPlaying, label = "glassPlay") { playing ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(if (playing) R.drawable.ic_np_pause else R.drawable.ic_np_play),
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (playing) "Pause" else "Play",
                                    style = TextStyle(fontFamily = GlassLabelFont, fontSize = 22.sp),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    GlassDisc(
                        backdrop = backdrop,
                        tint = accent,
                        size = 64.dp,
                        lit = true,
                        enabled = canSkipNext,
                        onClick = playerConnection::seekToNext,
                    ) {
                        Icon(painterResource(R.drawable.ic_np_next), null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ---- Shuffle, the stream, repeat ----
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                ) {
                    GlassPill(
                        backdrop = backdrop,
                        tint = accent,
                        lit = shuffle,
                        height = 54.dp,
                        modifier = Modifier.weight(0.85f),
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffle },
                    ) {
                        Icon(painterResource(R.drawable.shuffle), null, tint = Color.White.copy(alpha = if (shuffle) 1f else 0.8f), modifier = Modifier.size(22.dp))
                    }
                    GlassPill(
                        backdrop = backdrop,
                        tint = accent,
                        height = 54.dp,
                        modifier = Modifier.weight(1.5f),
                        onClick = { bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) } },
                    ) {
                        Icon(painterResource(R.drawable.music_note), null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = streamLabel(currentFormat),
                            style = TextStyle(fontFamily = GlassLabelFont, fontSize = 15.sp, letterSpacing = 0.8.sp),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    GlassPill(
                        backdrop = backdrop,
                        tint = accent,
                        lit = repeatMode != Player.REPEAT_MODE_OFF,
                        height = 54.dp,
                        modifier = Modifier.weight(0.85f),
                        onClick = { playerConnection.player.toggleRepeatMode() },
                    ) {
                        Icon(
                            painterResource(if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat),
                            null,
                            tint = Color.White.copy(alpha = if (repeatMode != Player.REPEAT_MODE_OFF) 1f else 0.8f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "OPUS 158 kbps": the codec and the rate, as the stream pill says them. */
private fun streamLabel(format: FormatEntity?): String {
    if (format == null) return "Streaming"
    val codec = when {
        format.codecs.contains("opus", ignoreCase = true) -> "OPUS"
        format.codecs.contains("flac", ignoreCase = true) || format.mimeType.contains("flac", ignoreCase = true) -> "FLAC"
        format.codecs.startsWith("mp4a", ignoreCase = true) -> "AAC"
        else -> format.mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }
    return if (format.bitrate > 0) "$codec ${format.bitrate / 1000} kbps" else codec
}

/** The cover: square, rounded, lifted, and a swipe across it changes the song. */
@Composable
private fun GlassCover(artUrl: String?, isPlaying: Boolean, onSwipe: (forward: Boolean) -> Unit) {
    // Playing, it sits forward; paused, it settles back a little, as a record set down does.
    val scale by animateFloatAsState(if (isPlaying) 1f else 0.9f, spring(dampingRatio = 0.72f, stiffness = 260f), label = "glassCover")
    val onSwipeLatest by rememberUpdatedState(onSwipe)
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight)
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = drag * 0.35f
                    rotationZ = drag / 90f
                }
                .shadow(30.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = size.width * 0.22f
                            if (drag > threshold) onSwipeLatest(false) else if (drag < -threshold) onSwipeLatest(true)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { change, amount ->
                        change.consume()
                        drag += amount
                    }
                },
        )
    }
}

@Composable
private fun GlassDisc(
    backdrop: LayerBackdrop,
    tint: Color,
    size: Dp,
    onClick: () -> Unit,
    lit: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    // The glass: it swells under the finger and lights where it was touched.
    val interaction = com.ozyern.exhale.ui.component.liquid.rememberGlassInteraction()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .interactiveGlass(
                backdrop = backdrop,
                shape = CircleShape,
                tint = tint.copy(alpha = if (lit) 0.46f else 0.20f),
                interaction = if (enabled) interaction else null,
            )
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
    ) { content() }
}

@Composable
private fun GlassPill(
    backdrop: LayerBackdrop,
    tint: Color,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val interaction = com.ozyern.exhale.ui.component.liquid.rememberGlassInteraction()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(height)
            .interactiveGlass(
                backdrop = backdrop,
                shape = CircleShape,
                tint = tint.copy(alpha = if (lit) 0.46f else 0.20f),
                interaction = interaction,
                // A wide pill swells less than a disc.
                pressedScale = 1.04f,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        content = content,
    )
}

/**
 * A thin track with a round, lit thumb, and the elapsed and remaining time under its two ends.
 * The position is read here, so the playhead's tick redraws only this.
 */
@Composable
private fun GlassScrubber(
    accent: Color,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
) {
    val duration = durationProvider().takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
    val shown = (sliderPositionProvider() ?: positionProvider()).coerceIn(0L, max(0L, duration))
    val fraction = if (duration > 0L) shown.toFloat() / duration else 0f
    val latestDuration by rememberUpdatedState(duration)
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (dragging) 1.25f else 1f, label = "glassThumb")
    val thumbColor = lerp(Color.White, accent, 0.18f)
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset ->
                        if (latestDuration > 0L) {
                            onScrub(((offset.x / size.width).coerceIn(0f, 1f) * latestDuration).toLong())
                            onScrubFinished()
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            if (latestDuration > 0L) onScrub(((offset.x / size.width).coerceIn(0f, 1f) * latestDuration).toLong())
                        },
                        onDragEnd = {
                            dragging = false
                            onScrubFinished()
                        },
                        onDragCancel = {
                            dragging = false
                            onScrubFinished()
                        },
                    ) { change, _ ->
                        change.consume()
                        if (latestDuration > 0L) onScrub(((change.position.x / size.width).coerceIn(0f, 1f) * latestDuration).toLong())
                    }
                },
        ) {
            val track = 5.dp.toPx()
            val y = size.height / 2f
            val r = 11.dp.toPx() * thumb
            val x = (size.width * fraction).coerceIn(r, size.width - r)
            drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(0f, y - track / 2), Size(size.width, track), CornerRadius(track / 2))
            drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(0f, y - track / 2), Size(x, track), CornerRadius(track / 2))
            drawCircle(Color.Black.copy(alpha = 0.25f), r + 2f, Offset(x, y + 2f))
            drawCircle(thumbColor, r, Offset(x, y))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                text = clock(shown),
                style = TextStyle(fontFamily = GlassLabelFont, fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "−" + clock((duration - shown).coerceAtLeast(0L)),
                style = TextStyle(fontFamily = GlassLabelFont, fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000.0).roundToInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
