/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.LyricsShowTranslationKey
import com.ozyern.exhale.constants.LyricsTextSizeKey
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.LyricsV2
import com.ozyern.exhale.ui.component.liquid.interactiveGlass
import com.ozyern.exhale.ui.component.liquid.rememberGlassInteraction
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberPreference
import androidx.media3.common.Player as Media3Player

/** The sizes the Aa disc steps through, smallest to largest. */
private val GLASS_LYRIC_SIZES = listOf(22f, 26f, 30f, 34f)

private val Heart = Color(0xFFFF375F)

/**
 * Apple Music's full-screen lyrics, in liquid glass.
 *
 * Output on the left, a segmented pill in the middle that flips between the words and the cover,
 * the lyric settings on the right; the words themselves large and to the left, the sung line lit
 * and the rest dimmed and softened; then the song with its heart, a slim scrubber, and translate,
 * the transport and text size along the foot. Every control is a pane of glass that swells under
 * the finger, over the cover's own colours drifting behind.
 *
 * [linesReady] holds the words back until the screen has finished arriving, so the heaviest thing
 * here is never composed on the first frame of the open.
 */
@Composable
internal fun GlassLyricsView(
    mediaMetadata: MediaMetadata,
    player: Media3Player,
    isPlaying: Boolean,
    isLoading: Boolean,
    liked: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    linesReady: Boolean,
    /** The cover has finished flying into the song's thumbnail; until then the thumbnail is the cover's. */
    thumbLanded: Boolean,
    /** 0 → 1 as the view arrives; the chrome slides in from the edges with it. */
    appear: () -> Float,
    /** Where the song's thumbnail sits, in root coordinates, for the cover to land on. */
    onThumbPlaced: (androidx.compose.ui.geometry.Offset) -> Unit,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onShowArtwork: () -> Unit,
    onOutput: () -> Unit,
    onSettings: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = com.ozyern.exhale.LocalPlayerConnection.current
    val haptic = LocalHapticFeedback.current
    val (showTranslation, setShowTranslation) = rememberPreference(LyricsShowTranslationKey, false)
    val (textSize, setTextSize) = rememberPreference(LyricsTextSizeKey, 26f)

    val linesAlpha by animateFloatAsState(
        targetValue = if (linesReady) 1f else 0f,
        animationSpec = tween(if (linesReady) 260 else 90, easing = FastOutSlowInEasing),
        label = "glassLyricsLines",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            // The whole screen is this view's: nothing under it should answer a stray tap.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top: output · lyrics | cover · settings ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    val a = appear()
                    translationY = -(1f - a) * 22.dp.toPx()
                    alpha = a
                }
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp),
        ) {
            GlassButton(icon = R.drawable.airplay, contentDescription = "Output", onClick = onOutput)
            Spacer(Modifier.weight(1f))
            GlassSegmented(
                onShowArtwork = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShowArtwork()
                },
            )
            Spacer(Modifier.weight(1f))
            GlassButton(icon = R.drawable.settings, contentDescription = "Lyrics settings", onClick = onSettings)
        }

        // ── The words, fading out under the chrome at both edges ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = linesAlpha
                    // The words float up into place rather than appearing where they will be.
                    translationY = (1f - linesAlpha) * 34.dp.toPx()
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.06f to Color.Black,
                            0.90f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            if (linesReady) {
                LyricsV2(
                    sliderPositionProvider = sliderPositionProvider,
                    cornerButtons = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                )
            }
        }

        Column(
            Modifier.graphicsLayer {
                val a = appear()
                translationY = (1f - a) * 30.dp.toPx()
            },
        ) {
        // ── The song ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 6.dp),
        ) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .onGloballyPositioned { onThumbPlaced(it.boundsInRoot().topLeft) }
                    // The flying cover is the thumbnail until it lands.
                    .graphicsLayer { alpha = if (thumbLanded) 1f else 0f }
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onShowArtwork),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaMetadata.artists.joinToString(", ") { it.name },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val heartTint by animateColorAsState(if (liked) Heart else Color.White, label = "glassHeart")
            val heartScale by animateFloatAsState(
                targetValue = if (liked) 1f else 0.92f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                label = "glassHeartPop",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleLike()
                    },
            ) {
                Icon(
                    painter = painterResource(if (liked) R.drawable.favorite_filled else R.drawable.favorite_outline),
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = heartTint,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        },
                )
            }
        }

        // ── Where we are in it: a hairline that thickens under the thumb ──
        GlassScrubber(
            positionProvider = positionProvider,
            durationProvider = durationProvider,
            sliderPositionProvider = sliderPositionProvider,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp),
        )

        // ── Translate · transport · text size ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
        ) {
            GlassButton(
                icon = R.drawable.translate,
                contentDescription = "Translate",
                lit = showTranslation,
                onClick = { setShowTranslation(!showTranslation) },
            )
            Spacer(Modifier.weight(1f))
            TransportButton(R.drawable.skip_previous, "Previous", 34.dp, enabled = canSkipPrevious) {
                playerConnection?.seekToPrevious() ?: player.seekToPrevious()
            }
            Spacer(Modifier.width(14.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .clickable { player.togglePlayPause() },
            ) {
                if (isLoading) {
                    LoadingRing(modifier = Modifier.size(34.dp), color = Color.White, stroke = 3.dp)
                } else {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            TransportButton(R.drawable.skip_next, "Next", 34.dp, enabled = canSkipNext) {
                playerConnection?.seekToNext() ?: player.seekToNext()
            }
            Spacer(Modifier.weight(1f))
            GlassButton(
                contentDescription = "Text size",
                onClick = {
                    val next = GLASS_LYRIC_SIZES.firstOrNull { it > textSize + 0.5f } ?: GLASS_LYRIC_SIZES.first()
                    setTextSize(next)
                },
            ) {
                // Apple's "AA": a small A beside a large one.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("A", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}

/** A disc of the player's liquid glass: swells and glows under the finger, lit when [lit]. */
@Composable
private fun GlassButton(
    contentDescription: String,
    onClick: () -> Unit,
    icon: Int? = null,
    lit: Boolean = false,
    size: Dp = 46.dp,
    content: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .glassPane(CircleShape, lit)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        if (content != null) {
            content()
        } else if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (lit) 1f else 0.9f),
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * The lyrics | cover switch: one pane of glass with a lit bubble resting under the lyrics glyph.
 * Pressing the cover half sends the bubble across before the screen hands back to the artwork.
 */
@Composable
private fun GlassSegmented(onShowArtwork: () -> Unit) {
    var leaving by remember { mutableStateOf(false) }
    val bubble by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "glassSegmentBubble",
        finishedListener = { if (it >= 1f) onShowArtwork() },
    )
    val segment = 56.dp
    Box(
        modifier = Modifier
            .width(segment * 2 + 8.dp)
            .height(46.dp)
            .glassPane(CircleShape, lit = false)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = segment * bubble)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        )
        Row(Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(segment)
                    .fillMaxHeight()
                    .clip(CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = "Lyrics",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(segment)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { leaving = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.album),
                    contentDescription = "Artwork",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TransportButton(icon: Int, contentDescription: String, iconSize: Dp, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "glassTransportPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A slim line that swells while held; drag or tap anywhere along it to seek. */
@Composable
private fun GlassScrubber(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    val thickness by animateFloatAsState(if (held) 9f else 4f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "glassScrubThickness")
    var widthPx by remember { mutableFloatStateOf(1f) }
    Column(modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val duration = durationProvider()
                        if (duration == C.TIME_UNSET || duration <= 0) return@detectTapGestures
                        onValueChange((offset.x / size.width * duration).toLong().coerceIn(0, duration))
                        onValueChangeFinished()
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            held = true
                            val duration = durationProvider()
                            if (duration > 0) onValueChange((offset.x / size.width * duration).toLong().coerceIn(0, duration))
                        },
                        onDragEnd = {
                            held = false
                            onValueChangeFinished()
                        },
                        onDragCancel = {
                            held = false
                            onValueChangeFinished()
                        },
                    ) { change, _ ->
                        val duration = durationProvider()
                        if (duration > 0) onValueChange((change.position.x / size.width * duration).toLong().coerceIn(0, duration))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(thickness.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .drawWithContent {
                        widthPx = size.width
                        val duration = durationProvider()
                        val position = sliderPositionProvider() ?: positionProvider()
                        val fraction = if (duration > 0 && duration != C.TIME_UNSET) {
                            (position.toFloat() / duration).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        drawRect(Color.White.copy(alpha = if (held) 0.95f else 0.75f), size = size.copy(width = size.width * fraction))
                    },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScrubberTime { makeTimeString(sliderPositionProvider() ?: positionProvider()) }
            ScrubberTime {
                val duration = durationProvider()
                if (duration == C.TIME_UNSET || duration <= 0) "" else "-" + makeTimeString(
                    (duration - (sliderPositionProvider() ?: positionProvider())).coerceAtLeast(0),
                )
            }
        }
    }
}

@Composable
private fun ScrubberTime(text: () -> String) {
    Text(
        text = text(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** The player's interactive glass where there is a backdrop to refract; its flat film where not. */
@Composable
private fun Modifier.glassPane(shape: Shape, lit: Boolean): Modifier {
    val backdrop = LocalPlayerBackdrop.current ?: return this.playerGlass(shape, lit)
    val interaction = rememberGlassInteraction()
    return this.interactiveGlass(
        backdrop = backdrop,
        shape = shape,
        tint = Color.White.copy(alpha = if (lit) 0.28f else 0.10f),
        interaction = interaction,
        pressedScale = 1.1f,
    )
}
