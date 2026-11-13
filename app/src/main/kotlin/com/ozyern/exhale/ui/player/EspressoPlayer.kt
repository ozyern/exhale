/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The ring of keys round the record and the way a drag round its rim seeks follow AETHER's player
 * (github.com/GHOSTxASR/AETHER, GPL-3.0: ClockDial and RingControls). The record, the turntable it
 * sits on and everything written round it are Exhale's.
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val RecordScript = FontFamily(Font(R.font.pinyon_script))
private val RecordCaps = FontFamily(Font(R.font.poppins, FontWeight.Medium))
private val RecordDigits = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))

/** Old paper: every word and hairline on the sleeve. */
private val Cream = Color(0xFFF3E7CF)

/** The turntable's metal: the rim that fills as the song plays, the label's rings, a key that is on. */
private val Brass = Color(0xFFD2AA5E)

/** The room the record plays in: black with a little warmth in it. */
private val Lounge = Color(0xFF0D0A08)

private enum class EspressoPanel { Cover, Lyrics, Queue }

/** How much of the screen the cover runs down, and how much of that dissolves into the room. */
private const val HeroFraction = 0.58f
private const val HeroFade = 0.5f

/** A long-player turns 33⅓ times a minute. */
private const val DegreesPerSecond = 200f

/**
 * The Espresso player: the song as a record on a turntable.
 *
 * The cover runs edge to edge up top and falls away into a dark, warm room. Under it the title is
 * signed in script, as a label's would be, with the artist and the pressing (the stream) in small
 * capitals and the line being sung beneath. At the foot half a record turns, pressed in the cover's
 * own colour with the cover as its label: it spins up when the song plays and runs down when it
 * stops. A brass rim fills round its edge as far as the song has got, with the stylus standing at
 * the moment being heard; the time is written across the vinyl, the previous and next songs sit on
 * it either side, and every other control is a key on the ring round the record, Play at its crown.
 *
 * Drag round the record's rim to seek: the drag has to start on the rim and travel along it, so a
 * pull down to close the player never moves the song.
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
    val palette = rememberArtworkPalette(mediaMetadata.thumbnailUrl)
    val vinyl by animateColorAsState(palette.accent, tween(900), label = "espressoVinyl")
    val flight = LocalCoverFlight.current
    val artRequest = mediaMetadata.thumbnailUrl?.let {
        com.ozyern.exhale.ui.component.playerArtworkRequest(androidx.compose.ui.platform.LocalContext.current, it)
    }
    // The player arriving: 0 on the dock, 1 open. Everything that rises or fades in reads it where
    // it is drawn, so the trip is a draw pass per frame and never a recomposition.
    val arrive: () -> Float = { playerSheetState.progress.coerceIn(0f, 1f) }

    val playerShown = com.ozyern.exhale.ui.component.LocalPlayerShown.current
    // The room moves with the music. The bass, as it is heard (the meter in the audio chain,
    // read a frame at a time and timed to the speaker): up at once on a beat and down slowly
    // after it, so the light under the record, its rim and the cover all take the kick together.
    val pulse = remember { mutableFloatStateOf(0f) }
    val listening by rememberUpdatedState(isPlaying && playerShown)
    LaunchedEffect(Unit) {
        val reader = com.ozyern.exhale.playback.AudioLevels.Reader()
        val levels = FloatArray(2)
        var last = 0L
        while (true) {
            if (!listening && pulse.floatValue < 0.004f) {
                pulse.floatValue = 0f
                androidx.compose.runtime.snapshotFlow { listening }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val heard = listening && reader.sample(System.nanoTime(), playerConnection.player.currentPosition, levels)
                val bass = if (heard) (levels[1] * 0.75f + levels[0] * 0.25f).coerceIn(0f, 1f) else 0f
                val fallen = pulse.floatValue - dt * 1.9f
                // Up three quarters of the way in one frame, never a hard step; down on its own time.
                pulse.floatValue = if (bass > fallen) fallen.coerceAtLeast(0f) + (bass - fallen.coerceAtLeast(0f)) * 0.75f else fallen.coerceAtLeast(0f)
            }
        }
    }
    // The cover is never quite still while the song plays: a slow breath of zoom and a drift
    // across the picture, on the shared ambient clock, eased out when the song is paused.
    val drift by com.ozyern.exhale.ui.component.rememberAmbientSeconds(running = isPlaying && playerShown)
    val liveness by animateFloatAsState(if (isPlaying) 1f else 0f, tween(900), label = "espressoLive")

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

    Box(
        modifier
            .fillMaxSize()
            .background(Lounge)
            .then(flight?.run { Modifier.fullSpace() } ?: Modifier),
    ) {
        // ---- The room, and the cover dissolving into it ----
        //
        // The same ground the Now Playing player stands on: the cover itself continued down the
        // screen, its last rows stretched to the foot and blurred, over a slow field of its own
        // colours. The cover above is the same picture, so where it lets go there is nothing to
        // see but the picture going soft. It used to fade to a flat dark instead, over a field of
        // different colours, and the line where the one stopped and the other began showed
        // straight across the screen.
        val coverDim by animateFloatAsState(if (panel == EspressoPanel.Cover) 1f else 0.2f, tween(320), label = "espressoCover")
        // Never under 1, so the picture's edges are never seen: the breath, the drift it leaves
        // room for, and a nudge on the beat.
        val coverScale: () -> Float = { 1f + liveness * (0.045f + 0.03f * sin(drift * 0.21f)) + 0.014f * pulse.floatValue }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroHeight = maxHeight * HeroFraction
            LiveArtworkBackdrop(artUrl = mediaMetadata.thumbnailUrl, palette = palette, heroHeight = heroHeight)
            // Deeper only where the record sits, and only gradually: the room darkens toward the
            // turntable without a band of its own.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.8f to Lounge.copy(alpha = 0.34f),
                            1f to Lounge.copy(alpha = 0.62f),
                        ),
                    ),
            )

            // ---- The cover, edge to edge. The dock's cover flies to it. ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .then(
                        flight?.run {
                            Modifier.reportFull { look ->
                                // The travelling cover lands as this one is drawn: at its zoom, dissolving at its foot.
                                look.scale = coverScale()
                                look.dim = 0.24f * (1f - liveness)
                                look.footFade = HeroFade
                                look.footFloor = 0f
                            }
                        } ?: Modifier,
                    )
                    .graphicsLayer {
                        alpha = coverDim
                        clip = true
                        // Its own layer, for the dissolve below to cut into.
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        // Let go of on a smootherstep, the way the other player's cover is: flat
                        // where it starts and flat where it ends, so no line marks either.
                        val dissolve = Brush.verticalGradient(
                            *heroDissolveStops(0f),
                            startY = size.height * (1f - HeroFade),
                            endY = size.height,
                        )
                        // Just enough at the top for the heading to read over a bright cover.
                        val scrim = Brush.verticalGradient(
                            0f to Lounge.copy(alpha = 0.40f),
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = size.height * 0.22f,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(scrim)
                            drawRect(dissolve, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                        }
                    },
            ) {
                AsyncImage(
                    model = artRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val k = coverScale()
                            scaleX = k
                            scaleY = k
                            translationX = liveness * size.width * 0.016f * sin(drift * 0.13f)
                            translationY = liveness * size.height * 0.012f * cos(drift * 0.17f)
                            transformOrigin = TransformOrigin(0.5f, 0.42f)
                        }
                        .drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                // Paused, the picture steps back a shade.
                                val dim = 0.24f * (1f - liveness)
                                if (dim > 0.004f) drawRect(Color.Black.copy(alpha = dim))
                            }
                        },
                )
            }
        }

        // ---- The air in the room: grain, a warm leak of light, dust, and the corners in shadow ----
        EspressoAtmosphere(
            tint = vinyl,
            drift = { drift },
            live = { liveness },
            beat = { pulse.floatValue },
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
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = ((arrive() - 0.45f) / 0.4f).coerceIn(0f, 1f) },
            ) {
                StampButton(R.drawable.expand_more, 24.dp) { playerSheetState.collapseSoft() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = when (panel) {
                            EspressoPanel.Queue -> "SIDE B  ·  UP NEXT"
                            EspressoPanel.Lyrics -> "LINER NOTES"
                            EspressoPanel.Cover -> "NOW SPINNING"
                        },
                        style = TextStyle(fontFamily = RecordCaps, fontSize = 11.sp, letterSpacing = 3.4.sp),
                        color = Cream,
                    )
                    Text(
                        text = queueTitle?.takeIf { it.isNotBlank() } ?: mediaMetadata.album?.title ?: "Exhale",
                        fontSize = 13.sp,
                        color = Cream.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StampButton(R.drawable.more_horiz, 22.dp, onClick = openMenu)
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

            // ---- The song, written as a label writes it ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        // Up into place a beat behind the sheet that carries it.
                        val e = ((arrive() - 0.3f) / 0.6f).coerceIn(0f, 1f)
                        alpha = e
                        translationY = 22.dp.toPx() * (1f - e) * (1f - e)
                    },
            ) {
                Text(
                    text = mediaMetadata.title,
                    style = TextStyle(
                        fontFamily = RecordScript,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        // The record's own colour, as light behind the signature.
                        shadow = androidx.compose.ui.graphics.Shadow(vinyl.copy(alpha = 0.75f), Offset.Zero, 28f),
                    ),
                    color = Cream,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                // The artist opens their page, and the player steps down for it. With several, the
                // first that has a page.
                val artistPage = mediaMetadata.artists.firstOrNull { !it.id.isNullOrBlank() }?.id
                Text(
                    text = mediaMetadata.artists.joinToString { it.name }.uppercase(Locale.getDefault()),
                    style = TextStyle(fontFamily = RecordCaps, fontSize = 12.sp, letterSpacing = 3.sp),
                    color = Cream.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = artistPage != null) {
                            artistPage?.let {
                                navController.navigate("artist/$it")
                                playerSheetState.collapseSoft()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Text(
                    text = espressoStream(currentFormat),
                    style = TextStyle(fontFamily = RecordCaps, fontSize = 10.sp, letterSpacing = 2.4.sp),
                    color = Brass.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                // The line being sung, as the other player shows it; a tap opens the lyrics.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(42.dp),
                ) {
                    if (panel != EspressoPanel.Lyrics) {
                        // The line being sung, written as the label writes the title: in script, in
                        // cream, the sung part lit in the record's own colour.
                        val stripLook = remember(vinyl) {
                            StripLook(
                                fontFamily = RecordScript,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Normal,
                                dim = Cream.copy(alpha = 0.34f),
                                lit = Cream,
                                glow = vinyl.copy(alpha = 0.9f),
                                chevron = Brass.copy(alpha = 0.6f),
                            )
                        }
                        androidx.compose.runtime.CompositionLocalProvider(LocalStripLook provides stripLook) {
                            CurrentLyricStrip(
                                trackKey = mediaMetadata.id,
                                isPlaying = isPlaying,
                                onClick = { panel = EspressoPanel.Lyrics },
                            )
                        }
                    }
                }
            }

            // ---- The turntable ----
            val buffering = playbackState == Player.STATE_BUFFERING
            EspressoTurntable(
                artRequest = artRequest,
                pressing = pressingFor(mediaMetadata),
                vinyl = vinyl,
                isPlaying = isPlaying,
                buffering = buffering,
                arrive = arrive,
                pulse = { pulse.floatValue },
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
                    .padding(horizontal = 10.dp),
            )
        }
    }
}

/**
 * What makes the room feel like a room rather than a screen: a fine film grain that shimmers while
 * the song plays, a warm leak of light in the record's colour drifting across the top and swelling
 * on the beat, a few motes of dust turning slowly in it, and the corners falling into shadow.
 * Everything here is made once and only moved or re-tinted as it is drawn.
 */
@Composable
private fun EspressoAtmosphere(tint: Color, drift: () -> Float, live: () -> Float, beat: () -> Float) {
    val grain = remember { grainTile() }
    val grainPaint = remember { androidx.compose.ui.graphics.Paint() }
    val shaderMatrix = remember { android.graphics.Matrix() }
    val grainShader = remember(grain) {
        android.graphics.BitmapShader(
            grain.asAndroidBitmap(),
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT,
        )
    }
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val vignette = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                    center = Offset(size.width / 2f, size.height * 0.45f),
                    radius = size.maxDimension * 0.72f,
                )
                val leak = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.9f),
                    0.4f to Color.White.copy(alpha = 0.25f),
                    1f to Color.Transparent,
                    center = Offset.Zero,
                    radius = size.width * 0.75f,
                )
                val mote = Brush.radialGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    center = Offset.Zero,
                    radius = 1f,
                )
                val tintFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint, androidx.compose.ui.graphics.BlendMode.SrcIn)
                onDrawBehind {
                    val t = drift()
                    val l = live()
                    val b = beat()
                    // The leak: from the top right, wandering, brighter on the beat.
                    val at = Offset(
                        size.width * (0.82f + 0.10f * sin(t * 0.07f)),
                        size.height * (0.06f + 0.05f * cos(t * 0.05f)),
                    )
                    withTransform({ translate(at.x, at.y) }) {
                        drawRect(
                            leak,
                            topLeft = Offset(-size.width, -size.width),
                            size = Size(size.width * 2f, size.width * 2f),
                            alpha = 0.10f + 0.10f * l + 0.10f * b,
                            colorFilter = tintFilter,
                            blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
                        )
                    }
                    // Dust, turning slowly in the light.
                    for (i in 0 until 14) {
                        val seed = i * 97.31f
                        val x = ((seed * 13.7f + t * (3f + i % 4)) % size.width + size.width) % size.width
                        val y = ((seed * 7.3f + t * (1.6f + i % 3) * if (i % 2 == 0) 1f else -1f) % (size.height * 0.7f) + size.height * 0.7f) % (size.height * 0.7f)
                        val r = (1.2f + (i % 3) * 0.8f) * density
                        val twinkle = 0.5f + 0.5f * sin(t * 0.9f + i)
                        withTransform({
                            translate(x, y)
                            scale(r * 3f, r * 3f, Offset.Zero)
                        }) {
                            drawCircle(mote, radius = 1f, center = Offset.Zero, alpha = (0.05f + 0.10f * twinkle) * (0.4f + 0.6f * l))
                        }
                    }
                    drawRect(vignette)
                    // Grain over everything, moved a little twelve times a second while it plays,
                    // as film's does.
                    val frame = if (l > 0.5f) (t * 12f).toInt() else 0
                    shaderMatrix.setTranslate(((frame * 37) % 128).toFloat(), ((frame * 71) % 128).toFloat())
                    grainShader.setLocalMatrix(shaderMatrix)
                    grainPaint.asFrameworkPaint().apply {
                        shader = grainShader
                        alpha = 22
                        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY)
                    }
                    drawContext.canvas.drawRect(0f, 0f, size.width, size.height, grainPaint)
                }
            },
    )
}

/** A tile of film grain: grey noise, made once, repeated across the screen. */
private fun grainTile(): androidx.compose.ui.graphics.ImageBitmap {
    val n = 128
    val pixels = IntArray(n * n)
    var seed = 0x2F6E2B1L
    for (i in pixels.indices) {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        val v = (((seed ushr 33) and 0xFF).toInt() + ((seed ushr 45) and 0xFF).toInt()) / 2
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    return bitmap.asImageBitmap()
}

/** One key on the ring round the record. */
private class EspressoKey(val icon: Int, val on: Boolean = false, val primary: Boolean = false, val onClick: () -> Unit)

/** "FLAC  ·  44.1 KHZ  ·  1411 KBPS": the pressing, in the label's small capitals. */
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

/**
 * A button off the record, struck like a stamp on a sleeve: no plate, two fine rings in the paper's
 * colour and the glyph inside them.
 */
@Composable
private fun StampButton(icon: Int, glyph: Dp, enabled: Boolean = true, size: Dp = 46.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Lounge.copy(alpha = 0.28f))
            .drawWithCache {
                val outer = Stroke(1.2.dp.toPx())
                val inner = Stroke(0.6.dp.toPx())
                onDrawBehind {
                    val ink = Cream.copy(alpha = if (enabled) 0.72f else 0.25f)
                    drawCircle(ink, radius = this.size.minDimension / 2f - outer.width, style = outer)
                    drawCircle(ink.copy(alpha = ink.alpha * 0.5f), radius = this.size.minDimension / 2f - 4.dp.toPx(), style = inner)
                }
            }
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Cream.copy(alpha = if (enabled) 1f else 0.3f),
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
 * Half a record on its platter, and the ring of keys round it. Everything is fitted to the width
 * it is given: the keys outermost, the record inside them, its label at the centre on the foot of
 * the screen.
 *
 * The record is one picture that only turns: its vinyl, grooves and label are drawn once into a
 * layer and the layer is rotated, so a frame of it spinning costs a transform, not a drawing. What
 * does not turn with it (the light across it, the brass rim, the stylus) is drawn over it.
 */
@Composable
private fun EspressoTurntable(
    artRequest: Any?,
    /** A photograph of the song's real record, when there is one; null draws one in the cover's colours. */
    pressing: Int?,
    vinyl: Color,
    isPlaying: Boolean,
    buffering: Boolean,
    arrive: () -> Float,
    /** The bass as it is heard, 0 to 1: read only where it is drawn. */
    pulse: () -> Float,
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
    val shown = com.ozyern.exhale.ui.component.LocalPlayerShown.current

    // How far the song has got, 0 to 1: read fifteen times a second while it plays, and only drawn.
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
    val stylus by animateFloatAsState(
        targetValue = scrub ?: head.floatValue,
        animationSpec = if (scrub != null) spring(dampingRatio = 1f, stiffness = 900f) else spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "espressoStylus",
    )
    val second by remember {
        derivedStateOf {
            val d = duration()
            (scrub?.let { (it * d).toLong() } ?: shownMs.longValue) / 1000
        }
    }

    // The platter: it runs up to speed when the song plays and runs down when it stops, as one
    // with a motor does. Asleep while the player is out of sight or at rest.
    val angle = remember { mutableFloatStateOf(0f) }
    val spinning by rememberUpdatedState((isPlaying || buffering) && shown)
    LaunchedEffect(Unit) {
        var speed = 0f
        var last = 0L
        while (true) {
            if (!spinning && speed < 0.004f) {
                speed = 0f
                androidx.compose.runtime.snapshotFlow { spinning }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                speed += ((if (spinning) 1f else 0f) - speed) * (1f - kotlin.math.exp(-dt / 0.55f))
                angle.floatValue = (angle.floatValue + DegreesPerSecond * speed * dt) % 360f
            }
        }
    }
    val crown by animateColorAsState(if (isPlaying || buffering) Brass else Cream, tween(400), label = "espressoCrown")
    // The two skips are cut into the vinyl itself: a band of the wax either side of the label,
    // pressed in as a finger lands on it.
    val previousIcon = painterResource(R.drawable.ic_np_previous)
    val nextIcon = painterResource(R.drawable.ic_np_next)
    var pressedSide by remember { mutableStateOf(0) }
    val previousPress by animateFloatAsState(if (pressedSide < 0) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "espressoPrev")
    val nextPress by animateFloatAsState(if (pressedSide > 0) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "espressoNext")
    val canPrevious by rememberUpdatedState(canSkipPrevious)
    val canNext by rememberUpdatedState(canSkipNext)
    val goPrevious by rememberUpdatedState(onPrevious)
    val goNext by rememberUpdatedState(onNext)

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val height = (w * 0.58f).coerceIn(180.dp, 320.dp)
        val ringOuter = min((w / 2 - 4.dp).value, (height - 4.dp).value).dp
        val ringInner = ringOuter - 44.dp
        val record = ringInner - 12.dp
        val reach = 0.10f // radians left clear at either end of the ring
        val slot = (PI.toFloat() - 2f * reach) / keys.size
        fun angleOf(i: Int) = PI.toFloat() - reach - (i + 0.5f) * slot

        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    // The turntable comes up from under the screen's foot as the player opens.
                    val e = ((arrive() - 0.15f) / 0.75f).coerceIn(0f, 1f)
                    val eased = 1f - (1f - e) * (1f - e) * (1f - e)
                    translationY = size.height * 0.55f * (1f - eased)
                    alpha = (e * 2f).coerceIn(0f, 1f)
                },
        ) {
            // ---- The light the record sits in: its own colour, rising from under it, on the beat ----
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val pivot = Offset(size.width / 2f, size.height)
                        val reach = record.toPx() * 1.55f
                        val halo = Brush.radialGradient(
                            0f to Color.White,
                            0.55f to Color.White.copy(alpha = 0.55f),
                            0.78f to Color.White.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                            center = pivot,
                            radius = reach,
                        )
                        val r = record.toPx()
                        val platterR = r + 9.dp.toPx()
                        // The record lies on its platter and lifts it a hair off: a soft shadow
                        // round its edge, darkest right under it.
                        val shadow = Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            (r / (platterR + 22.dp.toPx())) to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Transparent,
                            center = pivot,
                            radius = platterR + 22.dp.toPx(),
                        )
                        // The platter: brushed dark metal, its edge catching the lamp.
                        val platter = Brush.radialGradient(
                            0f to Color(0xFF1A1714),
                            0.92f to Color(0xFF26221D),
                            0.975f to Color(0xFF3A342C),
                            1f to Color(0xFF14110E),
                            center = pivot,
                            radius = platterR,
                        )
                        val platterEdge = Brush.sweepGradient(
                            0.50f to Color.White.copy(alpha = 0.05f),
                            0.62f to Color.White.copy(alpha = 0.30f),
                            0.75f to Color.White.copy(alpha = 0.08f),
                            0.88f to Color.White.copy(alpha = 0.24f),
                            1.00f to Color.White.copy(alpha = 0.05f),
                            center = pivot,
                        )
                        onDrawBehind {
                            val beat = pulse()
                            // One gradient made once, tinted and scaled where it is drawn: the halo
                            // swells and brightens with the bass without a new brush a frame.
                            val k = 1f + 0.10f * beat
                            withTransform({ scale(k, k, pivot) }) {
                                drawCircle(
                                    halo,
                                    radius = reach,
                                    center = pivot,
                                    alpha = 0.20f + 0.34f * beat,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(vinyl, androidx.compose.ui.graphics.BlendMode.SrcIn),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
                                )
                            }
                            drawCircle(shadow, radius = platterR + 22.dp.toPx(), center = pivot)
                            drawCircle(platter, radius = platterR, center = pivot)
                            drawCircle(platterEdge, radius = platterR - 0.6.dp.toPx(), center = pivot, style = Stroke(1.2.dp.toPx()))
                        }
                    },
            )

            // ---- The record: the whole disc, its lower half under the screen's foot, turning ----
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val d = (record * 2).roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(d, d))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place((constraints.maxWidth - d) / 2, constraints.maxHeight - d / 2)
                        }
                    }
                    .graphicsLayer {
                        rotationZ = angle.floatValue
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                if (pressing != null) {
                    // The real record, label and all, turning as it is.
                    androidx.compose.foundation.Image(
                        painter = painterResource(pressing),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Canvas(Modifier.fillMaxSize()) { drawRecord(vinyl) }
                    AsyncImage(
                        model = artRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(LabelFraction)
                            .clip(CircleShape),
                    )
                    Canvas(Modifier.fillMaxSize()) { drawLabelRings() }
                }
            }

            // ---- What does not turn: the light, the rim, the stylus, the ring of keys ----
            Spacer(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d0 = duration()
                            if (d0 <= 0) return@awaitEachGesture
                            val r = record.toPx()
                            val pivot = Offset(size.width / 2f, size.height.toFloat())
                            val from = down.position - pivot
                            val dist = from.getDistance()
                            // On one of the skips cut into the vinyl: a tap.
                            val side = skipBandAt(from, r)
                            if (side != 0) {
                                if ((side < 0 && !canPrevious) || (side > 0 && !canNext)) return@awaitEachGesture
                                down.consume()
                                pressedSide = side
                                var tapped = true
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) tapped = false
                                    if (!change.pressed) break
                                }
                                pressedSide = 0
                                if (tapped) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (side < 0) goPrevious() else goNext()
                                }
                                return@awaitEachGesture
                            }
                            // On the record's rim, give or take a fingertip.
                            if (dist < r * 0.74f || dist > r + 12.dp.toPx()) return@awaitEachGesture
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
                                    // Travelling round the rim seeks; anything else (a pull down) is the sheet's.
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
                                val m = (t * 20f).toInt()
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
                        val pivot = Offset(size.width / 2f, size.height)
                        val ro = ringOuter.toPx()
                        val ri = ringInner.toPx()
                        val r = record.toPx()
                        val rimR = r + 5.dp.toPx()
                        val crownPath = Path()
                        val startDeg = -(180f - reach * 180f / PI.toFloat())
                        val sweepDeg = 180f - 2f * reach * 180f / PI.toFloat()
                        val p = keys.indexOfFirst { it.primary }
                        // The light across the vinyl, from a lamp that stays where it is: the grooves
                        // catch it in two opposite fans, as a record's do, each with a soft halo
                        // either side of a bright core. Stands still while the record turns under it.
                        val sheen = Brush.sweepGradient(
                            0.00f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.57f to Color.White.copy(alpha = 0.06f),
                            0.605f to Color.White.copy(alpha = 0.26f),
                            0.64f to Color.White.copy(alpha = 0.06f),
                            0.71f to Color.Transparent,
                            0.79f to Color.Transparent,
                            0.85f to Color.White.copy(alpha = 0.05f),
                            0.885f to Color.White.copy(alpha = 0.20f),
                            0.92f to Color.White.copy(alpha = 0.05f),
                            0.98f to Color.Transparent,
                            1.00f to Color.Transparent,
                            center = pivot,
                        )
                        // Brighter mid-groove than at the run-in and the lip.
                        val sheenFall = Brush.radialGradient(
                            0f to Color.Transparent,
                            LabelFraction to Color.Transparent,
                            (LabelFraction + 0.02f) to Color.Black,
                            0.7f to Color.Black,
                            1f to Color.Black.copy(alpha = 0.55f),
                            center = pivot,
                            radius = r,
                        )
                        // The raised lip at the edge, lit from the top left.
                        val lip = Brush.sweepGradient(
                            0.50f to Color.White.copy(alpha = 0.10f),
                            0.64f to Color.White.copy(alpha = 0.55f),
                            0.80f to Color.White.copy(alpha = 0.12f),
                            1.00f to Color.White.copy(alpha = 0.04f),
                            center = pivot,
                        )
                        val discClip = Path().apply { addOval(Rect(pivot, r)) }
                        val sheenLayer = Rect(pivot, r)
                        onDrawBehind {
                            val at = stylus.coerceIn(0f, 1f)
                            clipPath(discClip) {
                                drawContext.canvas.saveLayer(sheenLayer, androidx.compose.ui.graphics.Paint())
                                drawCircle(sheen, radius = r, center = pivot)
                                drawCircle(sheenFall, radius = r, center = pivot, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                                drawContext.canvas.restore()
                            }
                            drawCircle(lip, radius = r - 0.8.dp.toPx(), center = pivot, style = Stroke(1.4.dp.toPx()))

                            // ---- The skips, cut into the wax either side of the label ----
                            drawSkipBand(pivot, r, -1, previousPress, canSkipPrevious, previousIcon)
                            drawSkipBand(pivot, r, 1, nextPress, canSkipNext, nextIcon)

                            // ---- The ring of keys: two hairline arcs, a hairline between each key ----
                            if (p >= 0) {
                                val a0 = PI.toFloat() - reach - p * slot
                                val a1 = a0 - slot
                                crownPath.reset()
                                crownPath.arcTo(Rect(pivot, ro), -a0 * 180f / PI.toFloat(), (a0 - a1) * 180f / PI.toFloat(), true)
                                crownPath.arcTo(Rect(pivot, ri), -a1 * 180f / PI.toFloat(), -(a0 - a1) * 180f / PI.toFloat(), false)
                                crownPath.close()
                                drawPath(crownPath, crown.copy(alpha = 0.10f))
                                drawArc(
                                    crown.copy(alpha = 0.9f),
                                    -a0 * 180f / PI.toFloat(),
                                    (a0 - a1) * 180f / PI.toFloat(),
                                    false,
                                    Offset(pivot.x - ro, pivot.y - ro),
                                    Size(ro * 2, ro * 2),
                                    style = Stroke(1.8.dp.toPx()),
                                )
                            }
                            drawArc(Cream.copy(alpha = 0.26f), startDeg, sweepDeg, false, Offset(pivot.x - ro, pivot.y - ro), Size(ro * 2, ro * 2), style = Stroke(1.dp.toPx()))
                            drawArc(Cream.copy(alpha = 0.14f), startDeg, sweepDeg, false, Offset(pivot.x - ri, pivot.y - ri), Size(ri * 2, ri * 2), style = Stroke(1.dp.toPx()))
                            for (b in 0..keys.size) {
                                val a = PI.toFloat() - reach - b * slot
                                val dir = Offset(cos(a), -sin(a))
                                val end = b == 0 || b == keys.size
                                val inset = if (end) 0f else 8.dp.toPx()
                                drawLine(Cream.copy(alpha = if (end) 0.3f else 0.16f), pivot + dir * (ri + inset), pivot + dir * (ro - inset), strokeWidth = 1.dp.toPx())
                            }
                            keys.forEachIndexed { i, key ->
                                if (key.on) {
                                    val a = angleOf(i)
                                    drawCircle(Brass, radius = 2.dp.toPx(), center = pivot + Offset(cos(a), -sin(a)) * (ri + 7.dp.toPx()))
                                }
                            }

                            // ---- The rim: a fine track all the way round, brass as far as the song has got ----
                            drawArc(Cream.copy(alpha = 0.16f), 180f, 180f, false, Offset(pivot.x - rimR, pivot.y - rimR), Size(rimR * 2, rimR * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                            val beat = pulse()
                            if (at > 0f) {
                                // The brass catches the light on the beat.
                                drawArc(Brass.copy(alpha = 0.22f + 0.38f * beat), 180f, 180f * at, false, Offset(pivot.x - rimR, pivot.y - rimR), Size(rimR * 2, rimR * 2), style = Stroke((8f + 6f * beat).dp.toPx(), cap = StrokeCap.Round))
                                drawArc(Brass, 180f, 180f * at, false, Offset(pivot.x - rimR, pivot.y - rimR), Size(rimR * 2, rimR * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                            }
                            // ---- The stylus: a cream head riding the groove at the moment being heard ----
                            val a = PI.toFloat() * (1f - at)
                            val dir = Offset(cos(a), -sin(a))
                            val tip = pivot + dir * (r - 20.dp.toPx())
                            val tail = pivot + dir * (rimR + 3.dp.toPx())
                            val drop = Offset(0f, 3.dp.toPx())
                            // Its shadow on the vinyl, then the arm, then the headshell over the tip.
                            drawLine(Color.Black.copy(alpha = 0.45f), tip + drop, tail + drop, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(
                                Brush.linearGradient(listOf(Cream, Brass), start = tail, end = tip),
                                tip, tail, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                            )
                            val shellAt = tip + dir * 7.dp.toPx()
                            drawLine(Color.Black.copy(alpha = 0.45f), shellAt - dir * 6.dp.toPx() + drop, shellAt + dir * 6.dp.toPx() + drop, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Brass, shellAt - dir * 6.dp.toPx(), shellAt + dir * 6.dp.toPx(), strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Cream.copy(alpha = 0.7f), shellAt - dir * 5.dp.toPx() - Offset(0f, 1.5.dp.toPx()), shellAt + dir * 5.dp.toPx() - Offset(0f, 1.5.dp.toPx()), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(Brass.copy(alpha = 0.35f * beat), radius = (6f + 8f * beat).dp.toPx(), center = tip)
                            drawCircle(Cream, radius = 1.6.dp.toPx(), center = tip)
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
                            x = w / 2 + mid * cos(a) - 22.dp,
                            y = height - mid * sin(a) - 22.dp,
                        )
                        .size(44.dp)
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
                            key.primary -> Cream
                            key.on -> Brass
                            else -> Cream.copy(alpha = 0.78f)
                        },
                        modifier = Modifier.size(if (key.primary) 26.dp else 20.dp),
                    )
                }
            }

            // ---- On the vinyl: the time across it, the previous and the next song either side ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = record * (LabelFraction + 0.06f)),
            ) {
                Text(
                    text = espressoClock(second * 1000),
                    style = TextStyle(
                        fontFamily = RecordDigits,
                        fontSize = 30.sp,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.5).sp,
                        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 10f),
                    ),
                    color = if (scrub != null) Brass else Cream,
                    textAlign = TextAlign.Center,
                )
                val left = (duration() - second * 1000).coerceAtLeast(0)
                Text(
                    text = if (duration() > 0) "−" + espressoClock(left) else "",
                    style = TextStyle(
                        fontFamily = RecordCaps,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 6f),
                    ),
                    color = Cream.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * The photograph of a song's actual pressing, where there is one to play instead of a drawn record.
 * Sabrina Carpenter's: from Short n' Sweet, Bed Chem's silver 7", Please Please Please's cream one,
 * Taste's pink one and Espresso's coffee-splash 12"; and from Man's Best Friend, Manchild's clear 7",
 * Tears' red 7", House Tour's pink one, and the album's picture disc for every other song on it.
 * Matched on the song, wherever it is playing from.
 */
private fun pressingFor(song: MediaMetadata): Int? {
    val bySabrina = song.artists.any { it.name.contains("sabrina carpenter", ignoreCase = true) }
    if (!bySabrina) return null
    // The title without what streaming adds to it: "(Official Video)", "- Remastered" and so on.
    val title = song.title.lowercase(Locale.ROOT).substringBefore(" (").substringBefore(" [").substringBefore(" - ")
        .replace(Regex("[^a-z]"), "")
    return when {
        title.startsWith("bedchem") -> R.drawable.bed_chem_vinyl
        title == "pleasepleaseplease" -> R.drawable.please_please_please_vinyl
        title == "taste" -> R.drawable.taste_vinyl
        title == "espresso" -> R.drawable.espresso_vinyl
        title == "manchild" -> R.drawable.manchild_vinyl
        title == "tears" -> R.drawable.tears_vinyl
        title == "housetour" -> R.drawable.house_tour_vinyl
        title in MansBestFriend -> R.drawable.mans_best_friend_vinyl
        song.album?.title?.lowercase(Locale.ROOT)?.replace(Regex("[^a-z]"), "")?.startsWith("mansbestfriend") == true ->
            R.drawable.mans_best_friend_vinyl
        else -> null
    }
}

/** Man's Best Friend's songs, as [pressingFor] writes a title. */
private val MansBestFriend = setOf(
    "mymanonwillpower",
    "sugartalking",
    "wealmostbrokeupagainlastnight",
    "nobodysson",
    "nevergettinglaid",
    "whendidyougethot",
    "amen",
    "goodbye",
    "dontworryillmakeyouworry",
)

/** The bands of the record the skips are cut into: radii as fractions of the record's, and angles. */
private const val SkipInner = 0.46f
private const val SkipOuter = 0.72f
private const val SkipFromDeg = 10f
private const val SkipToDeg = 44f

/**
 * Which skip a touch at [from] (relative to the record's centre, y down) lands on: -1 previous,
 * on the left; 1 next, on the right; 0 neither. A little generous, for a fingertip.
 */
private fun skipBandAt(from: Offset, r: Float): Int {
    val dist = from.getDistance()
    if (dist < r * (SkipInner - 0.06f) || dist > r * (SkipOuter + 0.04f)) return 0
    val deg = Math.toDegrees(atan2(-from.y, from.x).toDouble()).toFloat()
    return when {
        deg in (SkipFromDeg - 8f)..(SkipToDeg + 8f) -> 1
        deg in (180f - SkipToDeg - 8f)..(180f - SkipFromDeg + 8f) -> -1
        else -> 0
    }
}

/**
 * One skip, as a band of the record pressed a little deeper than the wax round it: darker, with
 * its upper edge in shadow and its lower edge catching the lamp, a few grooves running through it
 * and the arrow struck in cream. Pressed, it sinks further and the arrow turns brass.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkipBand(
    pivot: Offset,
    r: Float,
    side: Int,
    press: Float,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.painter.Painter,
) {
    val ri = r * SkipInner
    val ro = r * SkipOuter
    // Compose's arcs run clockwise from 3 o'clock; the band sits above the centre.
    val start = if (side > 0) -SkipToDeg else -(180f - SkipFromDeg)
    val sweep = SkipToDeg - SkipFromDeg
    val band = Path().apply {
        arcTo(Rect(pivot, ro), start, sweep, true)
        arcTo(Rect(pivot, ri), start + sweep, -sweep, false)
        close()
    }
    drawPath(band, Color.Black.copy(alpha = 0.30f + 0.18f * press))
    // Grooves running on through the cut.
    for (k in 1..3) {
        val rr = ri + (ro - ri) * k / 4f
        drawArc(Color.White.copy(alpha = 0.05f), start, sweep, false, Offset(pivot.x - rr, pivot.y - rr), Size(rr * 2, rr * 2), style = Stroke(0.6.dp.toPx()))
    }
    // The cut's walls: the far one in shadow, the near one lit.
    drawArc(Color.Black.copy(alpha = 0.55f), start, sweep, false, Offset(pivot.x - ro, pivot.y - ro), Size(ro * 2, ro * 2), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    drawArc(Cream.copy(alpha = 0.30f - 0.12f * press), start, sweep, false, Offset(pivot.x - ri, pivot.y - ri), Size(ri * 2, ri * 2), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
    for (edge in listOf(start, start + sweep)) {
        val a = Math.toRadians(edge.toDouble())
        val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
        drawLine(Color.Black.copy(alpha = 0.45f), pivot + dir * ri, pivot + dir * ro, strokeWidth = 1.dp.toPx())
    }
    // The arrow, at the band's middle, sunk a hair when pressed.
    val mid = Math.toRadians((start + sweep / 2f).toDouble())
    val at = pivot + Offset(kotlin.math.cos(mid).toFloat(), kotlin.math.sin(mid).toFloat()) * ((ri + ro) / 2f)
    val iconSize = 20.dp.toPx() * (1f - 0.08f * press)
    val tint = when {
        !enabled -> Cream.copy(alpha = 0.28f)
        else -> androidx.compose.ui.graphics.lerp(Cream, Brass, press)
    }
    val sink = 1.dp.toPx() * press
    withTransform({ translate(at.x - iconSize / 2f, at.y - iconSize / 2f + sink) }) {
        with(icon) {
            draw(Size(iconSize, iconSize), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
        }
    }
}

/** How much of the record's width its label takes. */
private const val LabelFraction = 0.36f

/**
 * The vinyl: pressed in [vinyl], lighter where the wax marbled, darker in the run-in and run-out,
 * with its grooves and the gaps between songs. Drawn once; the layer it is on does the turning.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRecord(vinyl: Color) {
    val c = center
    val r = size.minDimension / 2f
    val deep = lerp(vinyl, Color.Black, 0.5f)
    val pale = lerp(vinyl, Color.White, 0.28f)
    drawCircle(
        Brush.radialGradient(
            0f to deep,
            0.36f to deep,
            0.42f to lerp(vinyl, Color.Black, 0.22f),
            0.86f to vinyl,
            0.97f to lerp(vinyl, Color.Black, 0.3f),
            1f to lerp(vinyl, Color.Black, 0.55f),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
    // Marbling: where two colours of wax met in the press. The same record every time.
    var seed = 0x5EED1234L
    fun next(): Float {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return ((seed ushr 40) and 0xFFFF).toFloat() / 65535f
    }
    repeat(26) {
        val radius = r * (0.42f + 0.55f * next())
        val start = 360f * next()
        val sweep = 18f + 70f * next()
        val light = next() > 0.45f
        drawArc(
            color = (if (light) pale else deep).copy(alpha = 0.10f + 0.16f * next()),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(c.x - radius, c.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(r * (0.03f + 0.07f * next()), cap = StrokeCap.Round),
        )
    }
    // Grooves, and the wider gaps between the songs.
    var g = 0.40f
    var n = 0
    while (g < 0.965f) {
        val gap = n % 13 == 12
        drawCircle(
            Color.Black.copy(alpha = if (gap) 0.30f else 0.10f),
            radius = r * g,
            center = c,
            style = Stroke(if (gap) 1.6.dp.toPx() else 0.7.dp.toPx()),
        )
        g += if (gap) 0.022f else 0.011f
        n++
    }
    // Microgrooves between the bands: too fine to count, there to give the wax its grain.
    var fine = 0.405f
    var k = 0
    while (fine < 0.962f) {
        drawCircle(
            Color.White.copy(alpha = if (k % 3 == 0) 0.035f else 0.018f),
            radius = r * fine,
            center = c,
            style = Stroke(0.4.dp.toPx()),
        )
        fine += 0.0037f
        k++
    }
    // The run-out: the smooth band before the label, with its locked groove.
    drawCircle(Color.Black.copy(alpha = 0.25f), radius = r * 0.385f, center = c, style = Stroke(r * 0.03f))
    drawCircle(Color.White.copy(alpha = 0.08f), radius = r * 0.39f, center = c, style = Stroke(0.6.dp.toPx()))
}

/** The label's edge, in brass, and the spindle through it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabelRings() {
    val c = center
    val r = size.minDimension / 2f
    val label = r * LabelFraction
    drawCircle(Color.Black.copy(alpha = 0.22f), radius = label, center = c)
    drawCircle(Brass, radius = label, center = c, style = Stroke(1.8.dp.toPx()))
    drawCircle(Brass.copy(alpha = 0.6f), radius = label - 5.dp.toPx(), center = c, style = Stroke(0.8.dp.toPx()))
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = label + 3.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
    // The spindle: polished metal through the hole, lit from the top left.
    val pin = 5.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = pin + 1.5.dp.toPx(), center = c + Offset(0f, 1.dp.toPx()))
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFF6E4),
            0.45f to Color(0xFFBFB3A0),
            1f to Color(0xFF4A433A),
            center = c - Offset(pin * 0.35f, pin * 0.35f),
            radius = pin * 1.4f,
        ),
        radius = pin,
        center = c,
    )
}

private fun espressoClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}
