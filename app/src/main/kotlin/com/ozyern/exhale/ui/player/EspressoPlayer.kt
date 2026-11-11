/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The half dial the song is read from, the ring of keys round it and the way a drag round the arc
 * seeks follow AETHER's player (github.com/GHOSTxASR/AETHER, GPL-3.0: ClockDial and RingControls).
 * The cluster it is dressed as — the numbered tach, the swept arc, the redline, the mesh — is
 * Exhale's.
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
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
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val EspressoTitleFont = FontFamily(Font(R.font.unbounded_semibold, FontWeight.SemiBold))
private val EspressoDigitFont = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))

/** The swept arc and everything lit: the cluster's cold blue. */
private val Sweep = Color(0xFF3FE3F2)

/** The needle, the redline and a key that is on. */
private val Redline = Color(0xFFFF3B30)

private enum class EspressoPanel { Cover, Lyrics, Queue }

/** Marks round the half dial: one every three degrees, a long numbered one every sixth. */
private const val Marks = 60
private const val MarksPerNumber = 6

/** The last stretch of the song is the redline. */
private const val RedlineFrom = 0.88f

/**
 * The Espresso player: the song on an instrument cluster.
 *
 * The cover runs edge to edge up top and falls away into black; under it the title, and at the foot
 * a half dial read like a rev counter. The song's length is its scale, numbered nought to ten; a
 * blue arc sweeps round as far as the song has got, a red needle stands at the moment being heard,
 * and the last of the scale is the redline. The time gone sits at the dial's heart where a gear
 * would, with the previous and next songs either side of it, and every other control is a key on
 * the ring round the dial, Play at its crown.
 *
 * Drag round the arc to seek: the drag has to start on the scale and travel along it, so a pull
 * down to close the player never moves the song.
 */
@Composable
internal fun EspressoPlayerScreen(
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
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val shuffle by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    var panel by remember { mutableStateOf(EspressoPanel.Cover) }

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

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // ---- The cover, edge to edge, falling away into the cluster's black ----
        val coverDim by animateFloatAsState(if (panel == EspressoPanel.Cover) 1f else 0.22f, tween(320), label = "espressoCover")
        AsyncImage(
            model = mediaMetadata.thumbnailUrl?.let { com.ozyern.exhale.ui.component.playerArtworkRequest(androidx.compose.ui.platform.LocalContext.current, it) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .graphicsLayer { alpha = coverDim },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        0.22f to Color.Transparent,
                        0.62f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // ---- Heading ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                EspressoDisc(R.drawable.expand_more, 26.dp) { playerSheetState.collapseSoft() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = when (panel) {
                            EspressoPanel.Queue -> "UP NEXT"
                            EspressoPanel.Lyrics -> "LYRICS"
                            EspressoPanel.Cover -> "NOW PLAYING"
                        },
                        style = TextStyle(fontFamily = EspressoDigitFont, fontSize = 13.sp, letterSpacing = 3.sp),
                        color = Color.White,
                    )
                    Text(
                        text = queueTitle?.takeIf { it.isNotBlank() } ?: mediaMetadata.album?.title ?: "Exhale",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EspressoDisc(R.drawable.more_vert, 22.dp, onClick = openMenu)
            }

            // ---- The cover's room, or what took its place ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = panel,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                    label = "espressoPanel",
                ) { shown ->
                    when (shown) {
                        EspressoPanel.Cover -> Box(
                            Modifier
                                .fillMaxSize()
                                .espressoSwipe { forward ->
                                    if (forward) playerConnection.seekToNext() else playerConnection.seekToPrevious()
                                },
                        )
                        EspressoPanel.Lyrics -> LyricsV2(
                            sliderPositionProvider = sliderPositionProvider,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                        )
                        EspressoPanel.Queue -> NowPlayingQueue(
                            navController = navController,
                            playerSheetState = playerSheetState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // ---- The song ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = 10.dp, bottom = 6.dp),
            ) {
                Text(
                    text = mediaMetadata.title.uppercase(Locale.getDefault()),
                    style = TextStyle(fontFamily = EspressoTitleFont, fontSize = 19.sp, letterSpacing = 1.4.sp),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = espressoStream(currentFormat),
                    style = TextStyle(fontFamily = EspressoDigitFont, fontSize = 11.sp, letterSpacing = 2.6.sp),
                    color = Sweep.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ---- The cluster ----
            val buffering = playbackState == Player.STATE_BUFFERING
            EspressoCluster(
                isPlaying = isPlaying,
                buffering = buffering,
                durationProvider = durationProvider,
                positionProvider = { sliderPositionProvider() ?: playerConnection.player.currentPosition },
                onScrub = onSliderValueChange,
                onScrubEnd = onSliderValueChangeFinished,
                keys = listOf(
                    EspressoKey(R.drawable.shuffle, on = shuffle) { playerConnection.player.shuffleModeEnabled = !shuffle },
                    EspressoKey(if (liked) R.drawable.favorite_filled else R.drawable.favorite_outline, on = liked) { playerConnection.toggleLike() },
                    EspressoKey(R.drawable.ic_quote, on = panel == EspressoPanel.Lyrics) {
                        panel = if (panel == EspressoPanel.Lyrics) EspressoPanel.Cover else EspressoPanel.Lyrics
                    },
                    EspressoKey(if (isPlaying || buffering) R.drawable.ic_np_pause else R.drawable.ic_np_play, primary = true) {
                        playerConnection.player.togglePlayPause()
                    },
                    EspressoKey(R.drawable.queue_music, on = panel == EspressoPanel.Queue) {
                        panel = if (panel == EspressoPanel.Queue) EspressoPanel.Cover else EspressoPanel.Queue
                    },
                    EspressoKey(
                        if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                        on = repeatMode != Player.REPEAT_MODE_OFF,
                    ) { playerConnection.player.toggleRepeatMode() },
                    EspressoKey(R.drawable.more_horiz, onClick = openMenu),
                ),
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPrevious = playerConnection::seekToPrevious,
                onNext = playerConnection::seekToNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

/** One key on the ring round the dial. */
private class EspressoKey(val icon: Int, val on: Boolean = false, val primary: Boolean = false, val onClick: () -> Unit)

/** "FLAC · 1411 KBPS": what is playing, in the cluster's small capitals. */
private fun espressoStream(format: FormatEntity?): String {
    if (format == null) return "STREAMING"
    val codec = when {
        format.codecs.contains("opus", ignoreCase = true) -> "OPUS"
        format.codecs.contains("flac", ignoreCase = true) || format.mimeType.contains("flac", ignoreCase = true) -> "FLAC"
        format.codecs.startsWith("mp4a", ignoreCase = true) -> "AAC"
        else -> format.mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }
    val rate = format.sampleRate?.takeIf { it > 0 }?.let { "  ·  ${"%.1f".format(Locale.ROOT, it / 1000f).removeSuffix(".0")} KHZ" }.orEmpty()
    val bits = if (format.bitrate > 0) "  ·  ${format.bitrate / 1000} KBPS" else ""
    return codec + rate + bits
}

/** A round key off the dial: dark, with a hairline rim, as the cluster's own switches are. */
@Composable
private fun EspressoDisc(icon: Int, glyph: Dp, enabled: Boolean = true, size: Dp = 46.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f))),
            )
            .drawWithCache {
                val rim = Stroke(1.dp.toPx())
                onDrawBehind { drawCircle(Color.White.copy(alpha = 0.16f), style = rim) }
            }
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(glyph),
        )
    }
}

/** A sideways flick across the cover changes the song. */
private fun Modifier.espressoSwipe(onSwipe: (forward: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dx = 0f
        var dy = 0f
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            dx = change.position.x - down.position.x
            dy = change.position.y - down.position.y
        }
        if (abs(dx) > 72.dp.toPx() && abs(dx) > abs(dy) * 1.6f) onSwipe(dx < 0f)
    }
}

/**
 * The dial and its ring. Everything is fitted to the width it is given: the ring of keys outermost,
 * the scale inside it, the time at the heart.
 */
@Composable
private fun EspressoCluster(
    isPlaying: Boolean,
    buffering: Boolean,
    durationProvider: () -> Long,
    positionProvider: () -> Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    keys: List<EspressoKey>,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val position by rememberUpdatedState(positionProvider)
    val duration by rememberUpdatedState(durationProvider)
    val scrubTo by rememberUpdatedState(onScrub)
    val scrubDone by rememberUpdatedState(onScrubEnd)

    // How far the song has got, 0 to 1: read fifteen times a second while it plays (the needle
    // moves a fraction of a pixel a step), four times a second otherwise, and only ever drawn.
    val head = remember { mutableFloatStateOf(0f) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shownMs = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        while (true) {
            com.ozyern.exhale.utils.awaitAppVisible()
            val d = duration()
            val p = position()
            head.floatValue = if (d > 0) (p.toFloat() / d).coerceIn(0f, 1f) else 0f
            shownMs.longValue = p
            delay(if (isPlaying) 66L else 250L)
        }
    }
    // The needle swings to where it is going rather than jumping there: a seek, a new song.
    val needle by animateFloatAsState(
        targetValue = scrub ?: head.floatValue,
        animationSpec = if (scrub != null) spring(dampingRatio = 1f, stiffness = 900f) else spring(dampingRatio = 0.86f, stiffness = 120f),
        label = "espressoNeedle",
    )
    val second by remember {
        derivedStateOf {
            val d = duration()
            (scrub?.let { (it * d).toLong() } ?: shownMs.longValue) / 1000
        }
    }
    val measurer = rememberTextMeasurer()
    val numberStyle = remember { TextStyle(fontFamily = EspressoDigitFont, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Color.White) }
    val keyGlow by animateColorAsState(if (isPlaying || buffering) Sweep else Color.White, tween(400), label = "espressoCrown")

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val foot = 8.dp
        val height = (w * 0.6f).coerceIn(190.dp, 330.dp)
        val ringOuter = min((w / 2 - 4.dp).value, (height - foot - 4.dp).value).dp
        val ringInner = ringOuter - 46.dp
        val scale = ringInner - 12.dp
        val reach = 0.10f // radians left clear at either end of the ring
        val slot = (PI.toFloat() - 2f * reach) / keys.size
        fun angleOf(i: Int) = PI.toFloat() - reach - (i + 0.5f) * slot

        Box(Modifier.fillMaxWidth().height(height)) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d0 = duration()
                            if (d0 <= 0) return@awaitEachGesture
                            val r = scale.toPx()
                            val pivot = Offset(size.width / 2f, size.height - foot.toPx())
                            val from = down.position - pivot
                            val dist = from.getDistance()
                            // On the scale, give or take a fingertip, and above the dial's foot.
                            if (dist < r * 0.56f || dist > r + 10.dp.toPx() || from.y > 12.dp.toPx()) return@awaitEachGesture
                            val a0 = atan2(-from.y, from.x).coerceIn(0f, PI.toFloat())
                            val along = Offset(sin(a0), cos(a0))
                            val out = Offset(cos(a0), -sin(a0))
                            var scrubbing = false
                            var mark = -1
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val moved = change.position - down.position
                                if (!scrubbing) {
                                    if (moved.getDistance() < viewConfiguration.touchSlop) continue
                                    // Travelling round the arc seeks; anything else (a pull down) is the sheet's.
                                    val round = abs(moved.x * along.x + moved.y * along.y)
                                    val across = abs(moved.x * out.x + moved.y * out.y)
                                    if (round < across * 0.9f) return@awaitEachGesture
                                    scrubbing = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                change.consume()
                                val p = change.position - pivot
                                var a = atan2(-p.y, p.x)
                                if (a < 0f) a = if (p.x < 0f) PI.toFloat() else 0f
                                val t = (1f - a / PI.toFloat()).coerceIn(0f, 1f)
                                scrub = t
                                scrubTo((t * d0).toLong())
                                val m = (t * Marks / MarksPerNumber).toInt()
                                if (m != mark) {
                                    if (mark >= 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    mark = m
                                }
                            }
                            if (scrubbing) scrubDone()
                            scrub = null
                        }
                    }
                    .drawWithCache {
                        val pivot = Offset(size.width / 2f, size.height - foot.toPx())
                        val ro = ringOuter.toPx()
                        val ri = ringInner.toPx()
                        val r = scale.toPx()
                        val long = 14.dp.toPx()
                        val short = 6.dp.toPx()
                        // The scale, made once: short marks, long marks, and the redline's own.
                        val minor = Path()
                        val major = Path()
                        val redMarks = Path()
                        for (i in 0..Marks) {
                            val a = PI.toFloat() * (1f - i / Marks.toFloat())
                            val dir = Offset(cos(a), -sin(a))
                            val isMajor = i % MarksPerNumber == 0
                            val from = pivot + dir * (r - if (isMajor) long else short)
                            val to = pivot + dir * r
                            val path = when {
                                i / Marks.toFloat() >= RedlineFrom -> redMarks
                                isMajor -> major
                                else -> minor
                            }
                            path.moveTo(from.x, from.y)
                            path.lineTo(to.x, to.y)
                        }
                        // The mesh behind the dial: a honeycomb, faint, under everything.
                        val mesh = Path()
                        val cell = 9.dp.toPx()
                        val rowH = cell * 0.866f
                        var row = 0
                        var y = pivot.y - r * 0.86f
                        while (y < pivot.y) {
                            var x = pivot.x - r + if (row % 2 == 0) 0f else cell / 2f
                            while (x < pivot.x + r) {
                                val dx = x - pivot.x
                                val dy = y - pivot.y
                                if (dx * dx + dy * dy < (r * 0.84f) * (r * 0.84f)) {
                                    for (k in 0 until 6) {
                                        val t = (PI / 3 * k + PI / 6).toFloat()
                                        val px = x + cos(t) * cell * 0.36f
                                        val py = y + sin(t) * cell * 0.36f
                                        if (k == 0) mesh.moveTo(px, py) else mesh.lineTo(px, py)
                                    }
                                    mesh.close()
                                }
                                x += cell
                            }
                            y += rowH
                            row++
                        }
                        val numbers = (0..Marks / MarksPerNumber).map { measurer.measure(it.toString(), numberStyle) }
                        val minorStroke = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
                        val majorStroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
                        val meshStroke = Stroke(0.7.dp.toPx())
                        val sweepR = r + 6.dp.toPx()
                        val sweepStroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                        val wedge = Path()
                        val crown = Path()
                        val startDeg = -(180f - reach * 180f / PI.toFloat())
                        val sweepDeg = 180f - 2f * reach * 180f / PI.toFloat()
                        val p = keys.indexOfFirst { it.primary }
                        onDrawBehind {
                            val shown = needle.coerceIn(0f, 1f)
                            // Glow rising from the dial's foot, in the sweep's colour.
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Sweep.copy(alpha = 0.10f), Color.Transparent),
                                    center = pivot,
                                    radius = r * 0.9f,
                                ),
                                radius = r * 0.9f,
                                center = pivot,
                            )
                            drawPath(mesh, Color.White.copy(alpha = 0.07f), style = meshStroke)

                            // ---- The ring of keys: two hairline arcs, a hairline between each key ----
                            if (p >= 0) {
                                val a0 = PI.toFloat() - reach - p * slot
                                val a1 = a0 - slot
                                crown.reset()
                                crown.arcTo(Rect(pivot, ro), -a0 * 180f / PI.toFloat(), (a0 - a1) * 180f / PI.toFloat(), true)
                                crown.arcTo(Rect(pivot, ri), -a1 * 180f / PI.toFloat(), -(a0 - a1) * 180f / PI.toFloat(), false)
                                crown.close()
                                drawPath(crown, keyGlow.copy(alpha = 0.10f))
                                drawArc(
                                    keyGlow.copy(alpha = 0.85f),
                                    -a0 * 180f / PI.toFloat(),
                                    (a0 - a1) * 180f / PI.toFloat(),
                                    false,
                                    Offset(pivot.x - ro, pivot.y - ro),
                                    Size(ro * 2, ro * 2),
                                    style = Stroke(2.dp.toPx()),
                                )
                            }
                            drawArc(Color.White.copy(alpha = 0.20f), startDeg, sweepDeg, false, Offset(pivot.x - ro, pivot.y - ro), Size(ro * 2, ro * 2), style = Stroke(1.dp.toPx()))
                            drawArc(Color.White.copy(alpha = 0.12f), startDeg, sweepDeg, false, Offset(pivot.x - ri, pivot.y - ri), Size(ri * 2, ri * 2), style = Stroke(1.dp.toPx()))
                            for (b in 0..keys.size) {
                                val a = PI.toFloat() - reach - b * slot
                                val dir = Offset(cos(a), -sin(a))
                                val end = b == 0 || b == keys.size
                                val inset = if (end) 0f else 8.dp.toPx()
                                drawLine(Color.White.copy(alpha = if (end) 0.26f else 0.16f), pivot + dir * (ri + inset), pivot + dir * (ro - inset), strokeWidth = 1.dp.toPx())
                            }
                            keys.forEachIndexed { i, key ->
                                if (key.on) {
                                    val a = angleOf(i)
                                    drawCircle(Redline, radius = 2.dp.toPx(), center = pivot + Offset(cos(a), -sin(a)) * (ri + 7.dp.toPx()))
                                }
                            }

                            // ---- The scale ----
                            drawPath(minor, Color.White.copy(alpha = 0.22f), style = minorStroke)
                            drawPath(major, Color.White.copy(alpha = 0.5f), style = majorStroke)
                            drawPath(redMarks, Redline.copy(alpha = 0.55f), style = majorStroke)
                            // The redline's band, just outside its marks.
                            drawArc(
                                Redline.copy(alpha = 0.5f),
                                startAngle = 180f + 180f * RedlineFrom,
                                sweepAngle = 180f * (1f - RedlineFrom),
                                useCenter = false,
                                topLeft = Offset(pivot.x - sweepR, pivot.y - sweepR),
                                size = Size(sweepR * 2, sweepR * 2),
                                style = Stroke(4.dp.toPx()),
                            )
                            if (shown > 0f) {
                                // What the needle has passed: the same marks, lit, through a wedge to the needle.
                                wedge.reset()
                                wedge.moveTo(pivot.x, pivot.y)
                                wedge.arcTo(Rect(pivot, ro), 180f, 180f * shown + 0.5f, false)
                                wedge.close()
                                clipPath(wedge) {
                                    drawPath(minor, Color.White.copy(alpha = 0.7f), style = minorStroke)
                                    drawPath(major, Color.White, style = majorStroke)
                                    drawPath(redMarks, Redline, style = majorStroke)
                                }
                                // The sweep: a soft wide arc under a bright narrow one.
                                drawArc(
                                    Sweep.copy(alpha = 0.22f),
                                    180f,
                                    180f * shown,
                                    false,
                                    Offset(pivot.x - sweepR, pivot.y - sweepR),
                                    Size(sweepR * 2, sweepR * 2),
                                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                                )
                                drawArc(
                                    if (shown >= RedlineFrom) Redline else Sweep,
                                    180f,
                                    180f * shown,
                                    false,
                                    Offset(pivot.x - sweepR, pivot.y - sweepR),
                                    Size(sweepR * 2, sweepR * 2),
                                    style = sweepStroke,
                                )
                            }
                            // The numbers, nought to ten, inside their marks.
                            numbers.forEachIndexed { i, layout ->
                                val f = i * MarksPerNumber / Marks.toFloat()
                                val a = PI.toFloat() * (1f - f)
                                val at = pivot + Offset(cos(a), -sin(a)) * (r - long - 12.dp.toPx())
                                drawText(
                                    layout,
                                    color = when {
                                        f >= RedlineFrom -> Redline
                                        f <= shown -> Color.White
                                        else -> Color.White.copy(alpha = 0.45f)
                                    },
                                    topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
                                )
                            }
                            // The needle: red, from inside the numbers to just past the marks, with a bead at its foot.
                            val a = PI.toFloat() * (1f - shown)
                            val dir = Offset(cos(a), -sin(a))
                            val base = pivot + dir * (r * 0.5f)
                            val tip = pivot + dir * (r + 3.dp.toPx())
                            drawLine(Redline.copy(alpha = 0.3f), base, tip, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Redline, base, tip, strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(Redline, radius = 3.dp.toPx(), center = base)
                        }
                    },
            )

            // ---- The keys on the ring ----
            val mid = (ringInner + ringOuter) / 2
            keys.forEachIndexed { i, key ->
                val a = angleOf(i)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(
                            x = w / 2 + mid * cos(a) - 23.dp,
                            y = height - foot - mid * sin(a) - 23.dp,
                        )
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            key.onClick()
                        },
                ) {
                    Icon(
                        painterResource(key.icon),
                        contentDescription = null,
                        tint = when {
                            key.primary -> Color.White
                            key.on -> Redline
                            else -> Color.White.copy(alpha = 0.8f)
                        },
                        modifier = Modifier.size(if (key.primary) 26.dp else 20.dp),
                    )
                }
            }

            // ---- The time, where a gear would be, between the previous and the next song ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = foot + 6.dp),
            ) {
                EspressoDisc(R.drawable.ic_np_previous, 22.dp, enabled = canSkipPrevious, size = 50.dp, onClick = onPrevious)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = espressoClock(second * 1000),
                        style = TextStyle(fontFamily = EspressoDigitFont, fontSize = 40.sp, fontStyle = FontStyle.Italic, letterSpacing = (-1).sp),
                        color = if (scrub != null) Sweep else Color.White,
                        textAlign = TextAlign.Center,
                    )
                    val left = (duration() - second * 1000).coerceAtLeast(0)
                    Text(
                        text = if (duration() > 0) "−" + espressoClock(left) else "",
                        style = TextStyle(fontFamily = EspressoDigitFont, fontSize = 13.sp, letterSpacing = 1.sp),
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
                EspressoDisc(R.drawable.ic_np_next, 22.dp, enabled = canSkipNext, size = 50.dp, onClick = onNext)
            }
        }
    }
}

private fun espressoClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}
