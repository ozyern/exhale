/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.lyrics.Lyrics
import com.ozyern.exhale.desktop.lyrics.LyricsParser
import com.ozyern.exhale.desktop.lyrics.LyricsRepository
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.LoadingRing
import com.ozyern.exhale.desktop.ui.LocalWindowState
import com.ozyern.exhale.desktop.ui.Motion
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.formatTime
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.rememberPosition
import kotlinx.coroutines.delay
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * The controls, in a window of their own.
 *
 * mpv paints the picture into an operating-system window that sits above everything Exhale draws, so
 * a control drawn in the app is a control the picture swallows. The only thing that can be above that
 * window is another window — so this is one: borderless, transparent, owned by the main window so it
 * rides with it, and laid exactly over the picture. Inside it the player behaves the way a video
 * player should: the chrome fades in when the pointer moves and leaves again a few still seconds
 * later, the picture underneath untouched the whole time.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoControlsOverlay(bounds: State<Rectangle?>, state: PlayerState, player: DesktopPlayer) {
    // Only whether there is a surface is read here. Where it is changes as often as the window moves,
    // and reading that here would recompose the whole overlay every time it did.
    val ready = remember { derivedStateOf { bounds.value.let { it != null && it.width >= 80 && it.height >= 60 } } }
    if (!ready.value) return
    val song = state.current ?: return
    val first = bounds.value ?: return
    val scale = LocalDensity.current.density

    DialogWindow(
        onCloseRequest = {},
        state = rememberDialogState(
            position = WindowPosition.Absolute((first.x / scale).dp, (first.y / scale).dp),
            size = DpSize((first.width / scale).dp, (first.height / scale).dp),
        ),
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        // A dialog here is document-modal by default, and a modal dialog takes every click in the app
        // it belongs to. Made modeless instead, which needs a hide and a show to take effect.
        LaunchedEffect(window) {
            if (window.isModal) {
                window.isVisible = false
                window.modalityType = java.awt.Dialog.ModalityType.MODELESS
                window.isVisible = true
            }
        }

        // In the toolkit's own coordinates, so the overlay and the surface it covers are measured the
        // same way however the display is scaled. Restated on a beat rather than only when the surface
        // moves: Compose places a dialog from its own state after this, and a still surface would never
        // correct itself against that.
        LaunchedEffect(window) {
            while (true) {
                val place = bounds.value
                if (place != null) {
                    val at = window.bounds
                    if (at.x != place.x || at.y != place.y || at.width != place.width || at.height != place.height) {
                        window.setBounds(place.x, place.y, place.width, place.height)
                    }
                }
                delay(80)
            }
        }

        var lastMoved by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val chromeInteraction = remember { MutableInteractionSource() }
        val overChrome by chromeInteraction.collectIsHoveredAsState()
        var qualityOpen by remember { mutableStateOf(false) }
        var pillBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

        // A clock only while there's something to count down to.
        LaunchedEffect(lastMoved, overChrome, qualityOpen, state.isPlaying) {
            while (true) {
                now = System.currentTimeMillis()
                if (now - lastMoved > 2_800 && !overChrome && !qualityOpen && state.isPlaying) break
                delay(250)
            }
        }
        val shown = overChrome || qualityOpen || !state.isPlaying || now - lastMoved <= 2_800
        val position by player.rememberPosition()
        var captions by remember { mutableStateOf(true) }
        var menuOpen by remember { mutableStateOf(false) }

        // The same lookup the lyrics page makes, and the same cache answers it — so this costs a hit
        // rather than a second search.
        val lyrics by produceState<Lyrics?>(null, song.id, state.durationSeconds > 0) {
            value = LyricsRepository.lyricsFor(song, state.durationSeconds.toInt())
        }

        // The pointer goes with the chrome. A cursor sitting on a picture nobody is touching is the
        // one piece of furniture every video player knows to clear away.
        LaunchedEffect(shown) {
            window.cursor = if (shown) Cursor.getDefaultCursor() else BlankCursor
        }

        Box(
            Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Move) { lastMoved = System.currentTimeMillis() }
                .onPointerEvent(PointerEventType.Enter) { lastMoved = System.currentTimeMillis() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            lastMoved = System.currentTimeMillis()
                            player.togglePlayPause()
                        },
                    )
                },
        ) {
            // The picture's own name, over a scrim that only exists where the words are.
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(200)) + slideInVertically(spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness)) { -it / 2 },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.66f), Color.Transparent)))
                        .padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(
                                shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 6f),
                            ),
                        )
                        val artist = song.artists.joinToString { it.name }
                        if (artist.isNotBlank()) {
                            Text(
                                artist,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Sung words over the picture, the way subtitles sit: they stay when the chrome leaves,
            // and step out of its way when it comes back.
            val captionLift by animateDpAsState(
                if (shown) 150.dp else 42.dp,
                spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness),
                label = "caption_lift",
            )
            if (captions) {
                VideoCaptions(
                    lyrics = lyrics,
                    positionMs = (position * 1000).toLong(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = captionLift, start = 40.dp, end = 40.dp),
                )
            }

            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(200)) + slideInVertically(spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness)) { it / 2 },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                OverlayBar(
                    state = state,
                    player = player,
                    hover = chromeInteraction,
                    captions = captions,
                    hasLyrics = lyrics is Lyrics.Synced,
                    onCaptions = { captions = !captions },
                    onMore = { menuOpen = true },
                    qualityOpen = qualityOpen,
                    onQuality = { qualityOpen = !qualityOpen },
                    onPillBounds = { pillBounds = it },
                ) { lastMoved = System.currentTimeMillis() }
            }

            // Quality, in this window for the same reason as the menu: a popup would be drawn behind the picture.
            if (qualityOpen) {
                var sheetSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { qualityOpen = false } }) {
                    Box(
                        Modifier
                            .onSizeChanged { sheetSize = it }
                            .offset {
                                // Sitting on the pill it belongs to: its right edge on the pill's, a gap above it,
                                // and never further out than the window.
                                val pill = pillBounds
                                val x = ((pill?.right ?: 0f) - sheetSize.width).roundToInt()
                                val y = ((pill?.top ?: 0f) - sheetSize.height - gap).roundToInt()
                                IntOffset(x.coerceAtLeast(12), y.coerceAtLeast(12))
                            }
                            // Until it has been measured and placed it would flash in the corner.
                            .graphicsLayer { alpha = if (sheetSize == androidx.compose.ui.unit.IntSize.Zero || pillBounds == null) 0f else 1f }
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        QualitySheet(state.streamInfo, player, onDismiss = { qualityOpen = false })
                    }
                }
            }

            // The menu belongs in this window: one drawn in the app's own would be behind the picture.
            if (menuOpen) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) { detectTapGestures { menuOpen = false } },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(Modifier.padding(end = 28.dp).pointerInput(Unit) { detectTapGestures { } }) {
                        PlayerMenu(song, state, player, onDismiss = { menuOpen = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayBar(
    state: PlayerState,
    player: DesktopPlayer,
    hover: MutableInteractionSource,
    captions: Boolean,
    hasLyrics: Boolean,
    onCaptions: () -> Unit,
    onMore: () -> Unit,
    qualityOpen: Boolean,
    onQuality: () -> Unit,
    onPillBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTouched: () -> Unit,
) {
    val position by player.rememberPosition()
    val buffered by player.buffered.collectAsState()
    val duration = state.durationSeconds.coerceAtLeast(0.0)
    val progress = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                // Exhale's own glass: the same gradient, sheen and rim as every other plate in the app,
                // over a dark base, since a picture underneath is the one thing glass can't refract —
                // the video is a window of its own and there is nothing of it in this one to sample.
                .glassPlate(shape, base = Color(0xB3111014))
                .hoverable(hover)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            // One row, not three: the clock sits either side of the line it belongs to.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayTime(formatTime(position))
                Spacer(Modifier.width(11.dp))
                OverlayScrubber(
                    progress = progress,
                    buffered = if (duration > 0) (buffered / duration).toFloat().coerceIn(0f, 1f) else 0f,
                    enabled = duration > 0,
                    onSeek = {
                        onTouched()
                        player.seekTo(it * duration)
                    },
                    modifier = Modifier.weight(1f),
                    preview = { formatTime(it * duration) },
                )
                Spacer(Modifier.width(11.dp))
                OverlayTime(if (duration > 0) "−${formatTime(duration - position)}" else formatTime(0.0))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The transport sits in the middle of the bar whatever hangs off either end of it.
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayGlyph(TransportIcons.Previous, "Previous", 18.dp, hover = hover, onClick = player::previous)
                    Spacer(Modifier.width(17.dp))
                    OverlayGlyph(TransportIcons.Back10, "Back ten seconds", 21.dp, hover = hover) { player.seekBy(-10.0) }
                    Spacer(Modifier.width(18.dp))
                    if (state.isLoading) {
                        Box(Modifier.size(29.dp), contentAlignment = Alignment.Center) {
                            LoadingRing(Modifier.size(24.dp), color = Color.White, stroke = 2.dp)
                        }
                    } else {
                        OverlayGlyph(
                            if (state.isPlaying) TransportIcons.Pause else TransportIcons.Play,
                            if (state.isPlaying) "Pause" else "Play",
                            29.dp,
                            hover = hover,
                            onClick = player::togglePlayPause,
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    OverlayGlyph(TransportIcons.Forward10, "Forward ten seconds", 21.dp, hover = hover) { player.seekBy(10.0) }
                    Spacer(Modifier.width(17.dp))
                    OverlayGlyph(TransportIcons.Next, "Next", 18.dp, enabled = state.hasNext, hover = hover, onClick = player::next)
                }
                VolumeControl(state, player, hover)
                Spacer(Modifier.width(10.dp))
                if (hasLyrics) {
                    OverlayGlyph(
                        TransportIcons.Lyrics,
                        if (captions) "Hide the words" else "Show the words",
                        18.dp,
                        lit = captions,
                        hover = hover,
                        onClick = onCaptions,
                    )
                    Spacer(Modifier.width(9.dp))
                }
                state.streamInfo?.let {
                    QualityPill(it, open = qualityOpen, onBounds = onPillBounds, onClick = onQuality)
                    Spacer(Modifier.width(10.dp))
                }
                // The window itself goes full screen, so the picture gets the display rather than a
                // rectangle inside a player inside a window.
                val windowState = LocalWindowState.current
                if (windowState != null) {
                    val full = windowState.placement == WindowPlacement.Fullscreen
                    OverlayGlyph(
                        if (full) NavIcons.FullscreenExit else NavIcons.Fullscreen,
                        if (full) "Leave full screen" else "Full screen",
                        19.dp,
                        hover = hover,
                    ) {
                        windowState.placement = if (full) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    }
                    Spacer(Modifier.width(10.dp))
                }
                OverlayGlyph(NavIcons.New, "Switch to audio", 18.dp, hover = hover) { player.setVideoMode(false) }
                Spacer(Modifier.width(10.dp))
                OverlayGlyph(NavIcons.More, "More", 18.dp, hover = hover, onClick = onMore)
            }
        }
    }
}

/** A bare white glyph that lifts under the pointer. No plate, no ring. */
@Composable
private fun OverlayGlyph(
    icon: ImageVector,
    description: String,
    size: Dp,
    enabled: Boolean = true,
    lit: Boolean = false,
    hover: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val own = remember { MutableInteractionSource() }
    val hovered by own.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = 820f), label = "overlay_glyph")
    val alpha by animateFloatAsState(
        when {
            !enabled -> 0.32f
            hovered || lit -> 1f
            else -> 0.62f
        },
        tween(140),
        label = "overlay_glyph_alpha",
    )
    Icon(
        icon,
        contentDescription = description,
        tint = Color.White.copy(alpha = alpha),
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .hoverable(own)
            .hoverable(hover)
            .clickable(interactionSource = own, indication = null, enabled = enabled, onClick = onClick),
    )
}

/** A hairline until the pointer nears it, then it thickens and grows a knob. Seeks on release. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OverlayScrubber(
    progress: Float,
    enabled: Boolean,
    /** Only the timeline has one; the volume slider leaves it at nothing. */
    buffered: Float = 0f,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    preview: ((Float) -> String)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val seek by rememberUpdatedState(onSeek)
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var held by remember { mutableFloatStateOf(0f) }

    val shown = if (dragging) held else progress
    val thickness by animateDpAsState(if (hovered || dragging) 7.dp else 4.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "overlay_thickness")
    var pointerAt by remember { mutableFloatStateOf(-1f) }
    val knob by animateDpAsState(if (hovered || dragging) 13.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "overlay_knob")

    Box(
        modifier
            .height(22.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .hoverable(interaction, enabled = enabled)
            .onPointerEvent(PointerEventType.Move) { pointerAt = it.changes.first().position.x / width }
            .onPointerEvent(PointerEventType.Exit) { pointerAt = -1f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        dragging = true
                        held = (start.x / width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        held = (change.position.x / width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        seek(held)
                    },
                    onDragCancel = { dragging = false },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> seek((offset.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(Color.White.copy(alpha = 0.26f)))
        // How far the picture has actually been fetched. These streams are served in order, so this is
        // also how far a seek can land and still play — worth seeing before dragging past it.
        val reached by animateFloatAsState(buffered.coerceIn(shown, 1f), tween(320), label = "overlay_buffered")
        if (reached > shown) {
            Box(Modifier.fillMaxWidth(reached).height(thickness).clip(CircleShape).background(Color.White.copy(alpha = 0.34f)))
        }
        Box(Modifier.fillMaxWidth(shown).height(thickness).clip(CircleShape).background(Color.White))
        if (knob > 0.dp) {
            Box(
                Modifier
                    .graphicsLayer { translationX = (width * shown) - (knob.toPx() / 2f) }
                    .size(knob)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        // Where a click would land, before committing to it.
        if (preview != null && pointerAt in 0f..1f) {
            val at = pointerAt
            Text(
                preview(at),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .graphicsLayer { translationX = (width * at) - 26.dp.toPx(); translationY = -26.dp.toPx() }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun OverlayTime(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.62f),
        maxLines = 1,
    )
}


/** A cursor with nothing in it, for when the chrome has gone and the picture should be alone. */
private val BlankCursor: Cursor by lazy {
    Toolkit.getDefaultToolkit().createCustomCursor(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        Point(0, 0),
        "blank",
    )
}

/**
 * The words as they're sung, over the picture.
 *
 * A lyrics page can scroll a whole song past; a picture can spare one line. So this shows the line
 * being sung and lights each word as it arrives — the same timings the lyrics page animates, read at
 * subtitle size and weighted to stay legible over whatever happens to be on screen behind it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoCaptions(lyrics: Lyrics?, positionMs: Long, modifier: Modifier = Modifier) {
    val synced = lyrics as? Lyrics.Synced ?: return
    val index = LyricsParser.currentIndex(synced.lines, positionMs)
    val line = synced.lines.getOrNull(index)?.takeIf { it.text.isNotBlank() } ?: return

    val glow = Shadow(Color.Black.copy(alpha = 0.72f), Offset(0f, 2f), 12f)
    // Each line arrives rather than replacing the last one in place.
    androidx.compose.animation.AnimatedContent(
        targetState = line,
        transitionSpec = {
            (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(140))
        },
        modifier = modifier,
        label = "caption",
    ) { shown ->
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center,
        ) {
            val words = shown.words.filter { it.text.isNotBlank() }
            if (words.isEmpty()) {
                Text(
                    shown.text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(shadow = glow),
                )
            } else {
                words.forEach { word ->
                    val sung = when {
                        positionMs >= word.endMs -> 1f
                        positionMs <= word.startMs -> 0f
                        else -> (positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)
                    }
                    // Unsung words are present but recessed, so the line reads whole and the sung part
                    // is unmistakable — the same idea as the lyrics page, at a size that suits a film.
                    val alpha by animateFloatAsState(0.45f + 0.55f * sung, tween(120), label = "caption_word")
                    Text(
                        word.text,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = alpha),
                        style = LocalTextStyle.current.copy(shadow = glow),
                    )
                }
            }
        }
    }
}

/** What is playing, as a pill on the bar: "1080p" with a tag for anything sharper than SD. Opens [QualitySheet]. */
@Composable
private fun QualityPill(streamInfo: String, open: Boolean, onBounds: (androidx.compose.ui.geometry.Rect) -> Unit, onClick: () -> Unit) {
    val label = streamInfo.substringAfter("Video · ", streamInfo)
    val height = label.filter { it.isDigit() }.toIntOrNull()
    Row(
        Modifier
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = if (open) 0.24f else 0.12f))
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        heightTag(height)?.let { tag ->
            Spacer(Modifier.width(5.dp))
            Text(
                tag,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        Icon(
            TransportIcons.Collapse,
            contentDescription = "Change quality",
            tint = Color.White.copy(alpha = if (open) 1f else 0.6f),
            modifier = Modifier.padding(start = 3.dp).size(13.dp).graphicsLayer { rotationZ = if (open) 0f else 180f },
        )
    }
}

private fun heightTag(height: Int?): String? = when {
    height == null -> null
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 720 -> "HD"
    else -> null
}

/**
 * Picture quality, and the ceiling it is allowed to reach.
 *
 * Only what this video actually has is offered. The tick follows what is really playing rather than
 * what was last asked for — a video that tops out at 1080p ticks 1080p under a 4K ceiling — and the
 * sharpest sizes say what they cost, since the client that serves them serves them in order only.
 */
@Composable
private fun QualitySheet(streamInfo: String?, player: DesktopPlayer, onDismiss: () -> Unit) {
    val available by player.videoQualities.collectAsState()
    val chosen = AppSettings.current.videoQuality
    val playing = streamInfo?.substringAfter("Video · ", "")?.filter { it.isDigit() }?.toIntOrNull()
    val options = qualityOptions(available)
    val ticked = tickedOption(chosen, available)

    Column(
        Modifier
            .width(236.dp)
            .glassPlate(RoundedCornerShape(18.dp), base = Color(0xF2121014))
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            playing?.let { Text("Playing ${it}p", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.5f)) }
        }
        options.forEach { option ->
            QualityRow(option, selected = option.value == ticked) {
                onDismiss()
                if (option.value != chosen) {
                    AppSettings.update { it.copy(videoQuality = option.value) }
                    player.refreshVideo()
                }
            }
        }
    }
}

private data class QualityOption(val value: String, val name: String, val note: String?, val tag: String?)

@Composable
private fun QualityRow(option: QualityOption, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (hovered) 0.12f else 0f))
            .hoverable(source)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(NavIcons.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                option.name,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White.copy(alpha = if (selected) 1f else 0.85f),
            )
            option.note?.let { Text(it, fontSize = 10.sp, color = Color.White.copy(alpha = 0.42f)) }
        }
        option.tag?.let {
            Text(
                it,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Auto, then every height worth choosing that this video has, sharpest first. */
private fun qualityOptions(available: List<Int>): List<QualityOption> {
    val best = available.maxOrNull()
    val heights = available.filter { it >= 360 }.sortedDescending().ifEmpty { listOfNotNull(best) }
    return buildList {
        add(QualityOption("AUTO", "Auto", best?.let { "Best available · ${it}p" }, null))
        heights.forEach { height ->
            add(QualityOption(height.toString(), "${height}p", if (height >= 1440) "Slower to seek" else null, heightTag(height)))
        }
    }
}

/** Which row is ticked: Auto, or the sharpest size at or under the ceiling — the one that actually plays. */
private fun tickedOption(chosen: String, available: List<Int>): String {
    if (chosen == "AUTO") return "AUTO"
    val ceiling = chosen.toIntOrNull() ?: return "AUTO"
    val offered = available.filter { it >= 360 }
    val fit = offered.filter { it <= ceiling }.maxOrNull() ?: offered.minOrNull() ?: return "AUTO"
    return fit.toString()
}

/** Loudness, on the picture rather than behind it — the dock is not reachable while watching. */
@Composable
private fun VolumeControl(state: PlayerState, player: DesktopPlayer, hover: MutableInteractionSource) {
    val own = remember { MutableInteractionSource() }
    val hovered by own.collectIsHoveredAsState()
    val width by animateDpAsState(if (hovered) 62.dp else 0.dp, spring(dampingRatio = 0.8f, stiffness = 320f), label = "volume_width")
    val muted = state.volume == 0

    Row(
        Modifier.hoverable(own).hoverable(hover),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayGlyph(
            if (muted) TransportIcons.VolumeOff else TransportIcons.VolumeUp,
            if (muted) "Unmute" else "Mute",
            18.dp,
            hover = hover,
        ) {
            player.setVolume(if (muted) 100 else 0)
        }
        if (width > 0.dp) {
            Spacer(Modifier.width(8.dp))
            OverlayScrubber(
                progress = state.volume / 100f,
                enabled = true,
                onSeek = { player.setVolume((it * 100).roundToInt()) },
                modifier = Modifier.width(width),
            )
        }
    }
}
