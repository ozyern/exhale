/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The player's layout, motion and controls follow BitChord's Now Playing screen
 * (github.com/kushagrasinghx/BitChord, GPL-3.0), rebuilt on Exhale's playback, lyrics and queue.
 */

package com.ozyern.exhale.ui.player

import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import com.ozyern.exhale.playback.queueTier
import com.ozyern.exhale.playback.QueueTier
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sqrt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.LyricsLayout
import com.ozyern.exhale.constants.LyricsLayoutKey
import com.ozyern.exhale.constants.AutoLoadMoreKey
import com.ozyern.exhale.constants.CanvasSource
import com.ozyern.exhale.constants.CanvasSourceKey
import com.ozyern.exhale.constants.ExhaleCanvasKey
import com.ozyern.exhale.constants.ReactiveBackdropKey
import com.ozyern.exhale.playback.AudioLevels
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.extensions.move
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.extensions.toggleRepeatMode
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils.findCurrentLineIndex
import com.ozyern.exhale.lyrics.LyricsUtils.isTtml
import com.ozyern.exhale.lyrics.LyricsUtils.parseLyrics
import com.ozyern.exhale.lyrics.LyricsUtils.parseTtml
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.AudioFormatBadges
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.LyricsV2
import com.ozyern.exhale.ui.menu.LyricsMenu
import com.ozyern.exhale.ui.menu.PlayerMenu
import com.ozyern.exhale.ui.utils.ShowMediaInfo
import com.ozyern.exhale.ui.utils.highRes
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.constants.SpatialAudioKey
import com.ozyern.exhale.ui.component.LocalLyricsGlowColor
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as glassBlur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow as GlassShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class NowPlayingPanel { None, Lyrics, Queue }

/** Collapsed-header geometry, shared by the layout and its animation. */
private val THUMB_SIZE = 54.dp
private val GLASS_THUMB_SIZE = 48.dp
private val HEADER_HEIGHT = 60.dp
private val ART_TITLE_GAP = 20.dp
private val ART_BOX_TOP_PAD = 8.dp
private val TOP_STRIP_HEIGHT = 32.dp

/** The player's side margin. The two scrolling panels reach back across it. */
private val GUTTER = 30.dp

/** Past this the column stops growing and centres itself; phones never reach it. */
private val MAX_WIDTH = 560.dp

/** The whole journey between the full player and a panel's header. */
private const val COLLAPSE_MS = 420

/** Share of the full-bleed banner's height given over to its dissolve into the colours below. */
private const val HERO_FADE_FRACTION = 0.1f

/** How far up the cover has to be dragged for a release to open the queue. */
private const val QUEUE_CARRY_FRACTION = 0.3f

/** A release this fast (px/s) decides the queue on its own, however far it travelled. */
private const val QUEUE_FLICK_VELOCITY = 450f

/** How long the controls stand under the lyrics, untouched, before giving them the screen. */
private const val LYRICS_CONTROLS_IDLE_MS = 5_000L

private val SWIPE_SKIP_THRESHOLD = 72.dp

/** How far the cover sinks back while paused, as a share of its size. */
private const val PAUSE_SINK = 0.08f

private val BOTTOM_ACTION_SIZE = 44.dp
private val PILL_SEGMENT_WIDTH = 64.dp
private val PILL_SEGMENT_WIDTH_TRIPLE = 52.dp

private fun pillWidth(segments: Int): Dp = PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

private val SfPro = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))

/** The layer the player's glass refracts; null outside the Now Playing screen. */
internal val LocalPlayerBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** The cover's accent, for details deep in the controls. */
internal val LocalPlayerAccent = compositionLocalOf { DefaultAccent }

/**
 * Exhale's liquid glass for the player's controls: the colours under the pane are saturated,
 * softened and bent at the rim, then a thin film is laid over them — lighter when [lit].
 *
 * Plain translucent white where there is no recorded backdrop to refract.
 */
@Composable
internal fun Modifier.playerGlass(shape: Shape, lit: Boolean = false): Modifier {
    val film = Color.White.copy(alpha = if (lit) 0.22f else 0.07f)
    val backdrop = LocalPlayerBackdrop.current
        ?: return this.clip(shape).background(film.copy(alpha = film.alpha + 0.06f))
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            glassBlur(3f.dp.toPx())
            lens(10f.dp.toPx(), 20f.dp.toPx())
        },
        highlight = { Highlight.Default },
        shadow = { GlassShadow(radius = 10f.dp, color = Color.Black.copy(alpha = 0.22f)) },
        onDrawSurface = { drawRect(film) },
    )
}

/**
 * The Now Playing screen: one screen with three states.
 *
 * The cover runs full-bleed across the top and dissolves into a mesh of its own colours; the
 * controls sit on that calm lower half. Opening the lyrics or the queue doesn't raise another
 * sheet: the cover shrinks into a header thumbnail with the title beside it, and the panel fades
 * up underneath once it has arrived. The controls at the foot never move.
 *
 * Everything that animates with the collapse reads it in placement or draw lambdas, never in
 * composition, so the 420ms travel is a placement pass per frame rather than a recomposition.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingScreen(
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val togetherState by playerConnection.service.togetherSessionState.collectAsState()
    val togetherActive = togetherState !is com.ozyern.exhale.together.TogetherSessionState.Idle
    var infiniteQueue by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val playerVolume by playerConnection.service.playerVolume.collectAsState()

    var panel by rememberSaveable { mutableStateOf(NowPlayingPanel.None) }
    BackHandler(enabled = panel != NowPlayingPanel.None) { panel = NowPlayingPanel.None }

    // White status and navigation bar glyphs while the player is mostly up, whatever the app's
    // theme: the top of the screen is the cover, and a light theme's dark glyphs vanish into it.
    // Handed back to the theme as the player closes.
    val activity = context as? android.app.Activity
    val sheetMostlyOpen by remember { derivedStateOf { playerSheetState.progress > 0.5f } }
    DisposableEffect(activity, sheetMostlyOpen) {
        val window = activity?.window
        if (window == null || !sheetMostlyOpen) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }

    // ---- The collapse ----
    //
    // 0 is the cover filling the top of the player, 1 is the cover as a header thumbnail over a
    // panel. One value for both panels, so switching straight from lyrics to the queue moves
    // nothing but the panel.
    val collapse = remember { Animatable(if (panel == NowPlayingPanel.None) 0f else 1f) }
    // While a finger drags the queue in, it owns the value; the animation is parked.
    val dragSlide = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var queueFromDrag by remember { mutableStateOf(false) }
    var settleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(panel, settleTick, dragging) {
        if (dragging) return@LaunchedEffect
        val target = if (panel == NowPlayingPanel.None) 0f else 1f
        val from = collapse.value
        if (from == target) return@LaunchedEffect
        // Spent in proportion: a drag released four fifths of the way has a fifth of the time left.
        collapse.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = max(1, (COLLAPSE_MS * abs(target - from)).roundToInt()),
                easing = FastOutSlowInEasing,
            ),
        )
    }
    LaunchedEffect(panel) { if (panel != NowPlayingPanel.Queue) queueFromDrag = false }

    // A function, not a value: read where things are placed or drawn, so the collapse never
    // recomposes this screen. Composition only asks the thresholds below, which flip once each.
    val p: () -> Float = { if (dragging) dragSlide.floatValue else collapse.value }

    // Closing the player is the same motion as opening a panel, run by the sheet: as it is pulled
    // down the cover shrinks towards the top-left corner with the title beside it — the mini
    // player's own layout — while the sheet itself squeezes into the mini player's pill. So the
    // cover arrives at the mini player's artwork instead of dissolving into it; opening runs it
    // backwards, the artwork growing out of the pill into the full cover. Done by three tenths of
    // the way down, before the sheet hands over to the pill.
    val sheetClose: () -> Float = {
        FastOutSlowInEasing.transform(((1f - playerSheetState.progress) / 0.7f).coerceIn(0f, 1f))
    }
    // Geometry answers to whichever is further along. The panels answer to [p] alone.
    val g: () -> Float = { max(p(), sheetClose()) }
    val collapseStarted by remember { derivedStateOf { g() > 0f } }
    val collapsePastSettling by remember { derivedStateOf { g() >= 0.01f } }
    val collapsePastHalf by remember { derivedStateOf { g() >= 0.5f } }
    val collapseAlmostDone by remember { derivedStateOf { g() >= 0.999f } }
    val collapseDone by remember { derivedStateOf { p() >= 1f } }

    // The panels arrive after the cover has finished getting out of the way, not during: the
    // lyric sheet and the queue list are the heaviest things this screen composes, and composing
    // them on the first frame of the collapse is what makes an open stutter.
    val lyricsSettled = panel == NowPlayingPanel.Lyrics && collapseDone
    val queueSettled = panel == NowPlayingPanel.Queue && collapseDone
    val lyricsFade by animateFloatAsState(
        targetValue = if (lyricsSettled) 1f else 0f,
        animationSpec = tween(if (lyricsSettled) 200 else 90, easing = FastOutSlowInEasing),
        label = "npLyricsFade",
    )
    val queueFade by animateFloatAsState(
        targetValue = if (queueSettled) 1f else 0f,
        animationSpec = tween(if (queueSettled) 200 else 90, easing = FastOutSlowInEasing),
        label = "npQueueFade",
    )

    // Full-screen lyrics: the header and the controls step aside and the words take the screen,
    // until the same disc brings them back. Not undone by a scroll, as the idle hide is.
    var lyricsFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(panel) { if (panel != NowPlayingPanel.Lyrics) lyricsFullscreen = false }
    val lyricsFull = animateFloatAsState(
        targetValue = if (lyricsFullscreen && panel == NowPlayingPanel.Lyrics) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "npLyricsFull",
    )
    // ---- The controls under the lyrics stand down after a while, and come back on a scroll ----
    var lyricsControlsOpen by remember { mutableStateOf(true) }
    var lyricsNudge by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(panel) { lyricsControlsOpen = true }
    LaunchedEffect(panel, lyricsControlsOpen, lyricsNudge, scrubbing) {
        if (panel == NowPlayingPanel.Lyrics && lyricsControlsOpen && !scrubbing) {
            delay(LYRICS_CONTROLS_IDLE_MS)
            lyricsControlsOpen = false
        }
    }
    val lyricsScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !lyricsFullscreen) {
                    if (available.y > 2f) {
                        lyricsControlsOpen = true
                        lyricsNudge++
                    } else if (available.y < -2f) {
                        lyricsControlsOpen = false
                    }
                }
                return Offset.Zero
            }
        }
    }
    // ---- Apple Music's glass lyrics, when chosen: a screen of their own over the mesh ----
    val lyricsLayout by rememberEnumPreference(LyricsLayoutKey, LyricsLayout.GLASS)
    val glassLayout = lyricsLayout == LyricsLayout.GLASS
    val glassLyrics = glassLayout && panel == NowPlayingPanel.Lyrics
    val glassFade = animateFloatAsState(
        targetValue = if (glassLyrics) 1f else 0f,
        animationSpec = tween(COLLAPSE_MS, easing = LinearEasing),
        label = "npGlassLyrics",
    )
    val deckVisible = !glassLyrics && (panel != NowPlayingPanel.Lyrics || (lyricsControlsOpen && !lyricsFullscreen))
    val deckReveal = remember { Animatable(1f) }

    // ---- Artwork ----
    val artUrl = mediaMetadata.thumbnailUrl?.highRes()
    // One request object for the banner and the sleeve: one decode, one bitmap for both.
    val artRequest = remember(artUrl) {
        ImageRequest.Builder(context)
            .data(artUrl)
            .size(1200, 1200)
            .build()
    }
    var artLoaded by remember(artUrl) { mutableStateOf(false) }
    // Success from the banner's own painter: two painters sharing a request still don't enter
    // Success on the same frame, and the sleeve must not hand over to a banner still empty.
    var heroArtLoaded by remember(artUrl) { mutableStateOf(false) }
    // Sticky across skips: the banner is the shape of the player, not a property of the track.
    var heroSettled by remember { mutableStateOf(false) }
    var canvasRendered by remember { mutableStateOf(false) }
    LaunchedEffect(artLoaded, canvasRendered) { if (artLoaded || canvasRendered) heroSettled = true }
    val heroT = animateFloatAsState(
        targetValue = if (artLoaded || canvasRendered || heroSettled) 1f else 0f,
        animationSpec = tween(durationMillis = COLLAPSE_MS, easing = FastOutSlowInEasing),
        label = "npHero",
    )
    // The banner dissolves *as* the sleeve shrinks — one movement rather than two in a row.
    // The banner does not dissolve any more: it *becomes* the sleeve, shrinking into the header's
    // square, and hands over only once it is there. Two covers crossfading was one cover visibly
    // sliding over the other for the whole trip.
    val heroVisible: () -> Float = { if (g() >= 0.999f) 0f else heroT.value }
    // Where the sleeve sits once collapsed, relative to this screen: the banner's destination.
    var sleeveHome by remember { mutableStateOf(Offset.Zero) }
    // In the glass lyrics the cover lands on the song's thumbnail at the foot, as Apple's does.
    var glassThumbHome by remember { mutableStateOf<Offset?>(null) }
    // Held through the return trip, so the cover flies back out of the thumbnail it went into
    // rather than jumping to the header's corner the moment the lyrics are dismissed.
    var morphToGlass by remember { mutableStateOf(false) }
    LaunchedEffect(glassLyrics, panel) {
        if (glassLyrics) morphToGlass = true else if (panel != NowPlayingPanel.None) morphToGlass = false
    }
    LaunchedEffect(collapseStarted) { if (!collapseStarted) morphToGlass = glassLyrics }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    // The player's full height: the cover fills all of it, as Apple Music's does.
    var screenHeight by remember { mutableStateOf(0.dp) }
    val heroShowing by remember { derivedStateOf { heroVisible() > 0.001f } }
    var heroHeight by remember { mutableStateOf(0.dp) }

    // ---- The cover is never quite still: a slow zoom and pan while the song plays ----
    val coverDrift = remember { mutableFloatStateOf(0f) }
    val driftPlaying by rememberUpdatedState(isPlaying)
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        var speed = 0f
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                speed += ((if (driftPlaying) 1f else 0f) - speed) * (1f - kotlin.math.exp(-dt / 1.2f))
                coverDrift.floatValue = (coverDrift.floatValue + dt * speed) % 100_000f
            }
        }
    }
    // ---- Depth: paused, the cover sinks back into the screen and dims ----
    //
    // Keyed on "paused while ready" rather than on isPlaying, which is also false for the moment
    // a song is buffering: a cover that sank and rose on every load would read as a flicker.
    val paused = !isPlaying && (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED)
    val pauseDepth = animateFloatAsState(
        targetValue = if (paused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 170f),
        label = "npPauseDepth",
    )

    // ---- The music, as light ----
    val reactive by rememberPreference(ReactiveBackdropKey, defaultValue = true)
    val breath = remember { mutableFloatStateOf(0f) }
    val kick = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, reactive) {
        var last = System.nanoTime()
        fun follow(current: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
            val tau = if (target > current) attack else release
            return current + (target - current) * (1f - kotlin.math.exp(-dt / tau))
        }
        if (!isPlaying || !reactive) {
            // Settle rather than snap when the music stops.
            while (breath.floatValue > 0.002f || kick.floatValue > 0.002f) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                    breath.floatValue = follow(breath.floatValue, 0f, dt, 0.1f, 0.5f)
                    kick.floatValue = follow(kick.floatValue, 0f, dt, 0.05f, 0.25f)
                }
            }
            breath.floatValue = 0f
            kick.floatValue = 0f
            return@LaunchedEffect
        }
        val reader = AudioLevels.Reader()
        val levels = FloatArray(2)
        var bassAverage = 0f
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val heard = reader.sample(System.nanoTime(), playerConnection.player.currentPosition, levels)
                val loud = if (heard) levels[0] else 0f
                val low = if (heard) levels[1] else 0f
                // Loudness is a slow breath; a kick is the bass jumping above where it has been
                // sitting, which is what separates a beat from a song that is simply bass-heavy.
                breath.floatValue = follow(breath.floatValue, loud, dt, 0.09f, 0.45f)
                bassAverage += (low - bassAverage) * (1f - kotlin.math.exp(-dt / 0.8f))
                val onset = ((low - bassAverage) * 4.5f).coerceIn(0f, 1f)
                kick.floatValue = follow(kick.floatValue, onset, dt, 0.035f, 0.22f)
            }
        }
    }

    // Paused, the sleeve steps back (it only shows once the banner has handed over to it).
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "npArtScale",
    )

    // ---- Animated cover ----
    val canvasEnabled by rememberPreference(ExhaleCanvasKey, defaultValue = true)
    val canvasSource by rememberEnumPreference(CanvasSourceKey, CanvasSource.AUTO)
    var canvas by remember(mediaMetadata.id) { mutableStateOf<com.ozyern.exhale.canvas.CanvasArtwork?>(null) }
    LaunchedEffect(mediaMetadata.id, canvasEnabled, canvasSource) {
        canvas = null
        if (!canvasEnabled) return@LaunchedEffect
        CanvasArtworkPlaybackCache.get(mediaMetadata.id)?.let { cached ->
            canvas = cached.toResolverArtwork()
            return@LaunchedEffect
        }
        val storefront = Locale.getDefault().country
            .takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                fetchCanvasArtworkForPlayback(
                    songTitleRaw = mediaMetadata.title,
                    artistNameRaw = mediaMetadata.artists.firstOrNull()?.name.orEmpty(),
                    albumName = mediaMetadata.album?.title,
                    storefront = storefront,
                    source = canvasSource,
                )
            }.getOrNull()
        }
        if (fetched != null && (fetched.animated != null || fetched.videoUrl != null)) {
            CanvasArtworkPlaybackCache.put(mediaMetadata.id, fetched.toPlaybackArtwork())
            canvas = fetched
        }
    }
    val heroClip = canvas?.takeIf { !collapsePastHalf && heroHeight > 0.dp }
    // How much of the banner the clip is covering: gone by the half-way point of the collapse,
    // where the still frame takes over from it.
    val clipCover: () -> Float = {
        if (heroClip != null && canvasRendered) (1f - 2f * g()).coerceIn(0f, 1f) else 0f
    }

    val palette = rememberArtworkPalette(artUrl)
    // The cover's colour for the details: the played part of the scrubber, the liked heart, the
    // lit queue and shuffle glyphs, the glow of the lyric being sung.
    val accent by animateColorAsState(palette.accent, tween(900), label = "npAccent")
    // What the glass controls refract: the mesh and the cover, recorded as a layer the controls
    // are not part of (glass cannot sample a layer it is inside).
    val glassBackdrop = rememberLayerBackdrop()

    // ---- Skipping by swiping the cover sideways ----
    val swipeOffset = remember { Animatable(0f) }
    val swipeThresholdPx = with(density) { SWIPE_SKIP_THRESHOLD.toPx() }

    // ---- The band a vertical drag belongs to: the cover and the credits under it ----
    var bandTop by remember { mutableFloatStateOf(0f) }
    var bandBottom by remember { mutableFloatStateOf(0f) }
    var bandSpace by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var showOutputSheet by remember { mutableStateOf(false) }
    var showLyricsSources by remember { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var showLyricsSettings by remember { mutableStateOf(false) }

    // ---- The music video, over the cover, when the song has one ----
    var videoMode by rememberSaveable { mutableStateOf(false) }
    var musicVideo by remember(mediaMetadata.id) { mutableStateOf<com.ozyern.exhale.playback.MusicVideo.Found?>(null) }
    var videoLoading by remember(mediaMetadata.id) { mutableStateOf(false) }
    var videoShown by remember(mediaMetadata.id) { mutableStateOf(false) }
    LaunchedEffect(videoMode) { if (!videoMode) videoShown = false }
    var videoMissing by remember(mediaMetadata.id) { mutableStateOf(com.ozyern.exhale.playback.MusicVideo.knownMissing(mediaMetadata.id)) }
    LaunchedEffect(mediaMetadata.id, videoMode) {
        if (!videoMode || musicVideo != null || videoMissing) return@LaunchedEffect
        videoLoading = true
        val found = com.ozyern.exhale.playback.MusicVideo.find(mediaMetadata)
        videoLoading = false
        if (found == null) {
            videoMissing = true
            android.widget.Toast.makeText(context, "No music video for this song", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            musicVideo = found
        }
    }

    val openMenu: () -> Unit = {
        menuState.show {
            PlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = playerSheetState,
                onShowDetailsDialog = {
                    bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                },
                onDismiss = menuState::dismiss,
            )
        }
    }
    val toggleLyrics: () -> Unit = {
        panel = if (panel == NowPlayingPanel.Lyrics) NowPlayingPanel.None else NowPlayingPanel.Lyrics
    }
    val toggleQueue: () -> Unit = {
        panel = if (panel == NowPlayingPanel.Queue) NowPlayingPanel.None else NowPlayingPanel.Queue
    }

    Box(
        modifier = modifier.fillMaxSize().onGloballyPositioned {
            screenOrigin = it.boundsInRoot().topLeft
            val h = with(density) { it.size.height.toDp() }
            if (h != screenHeight) screenHeight = h
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop),
        ) {
        // The whole screen under everything: the cover's own colours, drifting into place.
        LiveArtworkBackdrop(
            artUrl = artUrl,
            palette = palette,
            isPlaying = isPlaying,
            breath = { breath.floatValue },
            kick = { kick.floatValue },
        )

        // The cover, edge to edge, dissolving into the mesh at its foot.
        if (!(heroClip != null && canvasRendered && !collapseStarted) && (!collapsePastHalf || heroShowing) && heroHeight > 0.dp) {
            DisposableEffect(artRequest) { onDispose { heroArtLoaded = false } }
            AsyncImage(
                model = artRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = {
                    val ok = it is AsyncImagePainter.State.Success
                    heroArtLoaded = ok
                    if (ok) artLoaded = true
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // From the full-width banner to the header's square, in one piece: size and
                    // place are read per frame in layout, never recomposing.
                    .layout { measurable, constraints ->
                        val e = FastOutSlowInEasing.transform(g())
                        val toGlass = morphToGlass && glassThumbHome != null && sheetClose() < 0.01f
                        val thumb = (if (toGlass) GLASS_THUMB_SIZE else THUMB_SIZE).toPx()
                        val home = if (toGlass) glassThumbHome!! else sleeveHome
                        val fullW = constraints.maxWidth.toFloat()
                        val fullH = heroHeight.toPx()
                        val w = (fullW + (thumb - fullW) * e).roundToInt().coerceAtLeast(1)
                        val h = (fullH + (thumb - fullH) * e).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(constraints.maxWidth, h) {
                            placeable.placeRelative(
                                (home.x * e).roundToInt(),
                                (home.y * e).roundToInt(),
                            )
                        }
                    }
                    .graphicsLayer {
                        alpha = heroVisible() * (1f - clipCover())
                        compositingStrategy = CompositingStrategy.Offscreen
                        val landing = g()
                        if (landing > 0.001f) {
                            shape = RoundedCornerShape((8f * landing).dp)
                            clip = true
                        }
                        // Paused, the picture recedes: smaller, rounded, a shade darker.
                        val depth = pauseDepth.value
                        val sink = 1f - PAUSE_SINK * depth
                        // A slow breath of zoom and a drift across the picture, gone as it
                        // shrinks into the header so it lands square.
                        val t = coverDrift.floatValue
                        val settle = 1f - g()
                        val zoom = 1f + settle * (0.05f + 0.035f * kotlin.math.sin(t * 0.21f))
                        scaleX = sink * zoom
                        scaleY = sink * zoom
                        translationX = settle * size.width * 0.018f * kotlin.math.sin(t * 0.13f)
                        translationY = settle * size.height * 0.014f * kotlin.math.cos(t * 0.17f)
                        transformOrigin = TransformOrigin(0.5f, 0.42f)
                        if (depth > 0.001f && landing <= 0.001f) {
                            shape = RoundedCornerShape((28f * depth).dp)
                            clip = true
                        } else if (landing <= 0.001f) {
                            clip = false
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val depth = pauseDepth.value
                        if (depth > 0.001f) drawRect(Color.Black.copy(alpha = 0.28f * depth))
                        // The dissolve at the foot belongs to the banner; the square it lands as
                        // has none.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Black.copy(alpha = g().coerceIn(0f, 1f))),
                                startY = size.height * (1f - HERO_FADE_FRACTION),
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        }

        heroClip?.let { clip ->
            CanvasHeroVideo(
                primaryUrl = clip.animated,
                fallbackUrl = clip.videoUrl,
                // Held still for the whole trip into a panel and back.
                isPlaying = isPlaying && !collapseStarted,
                bottomFade = HERO_FADE_FRACTION,
                alpha = {
                    if (canvasRendered) (1f - 2f * g()).coerceIn(0f, 1f) * (1f - 0.3f * pauseDepth.value) else 0f
                },
                scale = { 1f - PAUSE_SINK * pauseDepth.value },
                onRenderedChanged = { canvasRendered = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(heroHeight),
            )
        }
        if (heroClip == null && canvasRendered) {
            SideEffect { canvasRendered = false }
        }

        // The video sits where the cover is, on black, and dissolves at its foot the same way.
        val video = musicVideo
        if (videoMode && video != null && heroHeight > 0.dp && !collapsePastHalf) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        alpha = (1f - 2f * g()).coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        // Black only once the picture is there; until then the cover shows through.
                        if (videoShown) drawRect(Color.Black)
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height * (1f - HERO_FADE_FRACTION),
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                MusicVideoSurface(
                    stream = video.stream,
                    positionMs = positionProvider,
                    isPlaying = isPlaying && !collapseStarted,
                    onFirstFrame = { videoShown = true },
                    onError = {
                        musicVideo = null
                        videoMode = false
                        android.widget.Toast.makeText(context, "Couldn't play this video", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTop + TOP_STRIP_HEIGHT),
                )
            }
        }

        // Over a full-screen cover, the controls need their own contrast: clear through the upper
        // half of the picture, darkening towards the foot where the scrubber and buttons sit. It
        // goes as the cover shrinks into the header, where the square needs none.
        if (heroHeight > 0.dp && !collapsePastHalf) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer { alpha = (1f - 2f * g()).coerceIn(0f, 1f) * heroVisible() }
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.12f),
                            0.38f to Color.Transparent,
                            0.58f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
        }

        // Keeps the status bar legible over a bright cover without boxing it in.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(statusBarTop + TOP_STRIP_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                    ),
                ),
        )

        CompositionLocalProvider(
            LocalPlayerBackdrop provides glassBackdrop,
            LocalPlayerAccent provides accent,
            LocalLyricsGlowColor provides accent,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // One screen at a time: the player steps out over the first half of the switch and
                // the lyrics step in over the second, so the two never ghost through each other.
                .graphicsLayer { alpha = (1f - 2f * glassFade.value).coerceIn(0f, 1f) }
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The strip over the cover: the handle, and where the music is playing from. Drags
            // here close the player; a tap on it does the same.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOP_STRIP_HEIGHT)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { playerSheetState.collapseSoft() },
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(0, lerp(6.dp, (TOP_STRIP_HEIGHT - 5.dp) / 2, g()).roundToPx())
                        }
                        .width(38.dp)
                        .height(5.dp)
                        .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.70f)),
                )
                val origin = queueTitle?.takeIf { it.isNotBlank() }
                    ?: mediaMetadata.album?.title?.takeIf { it.isNotBlank() }
                if (origin != null && !collapseAlmostDone) {
                    Text(
                        text = "Playing from $origin",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 4f),
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = GUTTER)
                            .padding(bottom = 1.dp)
                            .graphicsLayer { alpha = 1f - g() },
                    )
                }
                // Song ⇄ Video, as Apple Music offers it: only while the cover is the screen, and
                // gone for a song we already know has no video.
                if (!videoMissing && !collapsePastSettling) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 14.dp)
                            .height(30.dp)
                            .playerGlass(CircleShape, lit = videoMode)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                videoMode = !videoMode
                            }
                            .padding(horizontal = 12.dp),
                    ) {
                        if (videoLoading) {
                            LoadingRing(modifier = Modifier.size(14.dp), color = Color.White, stroke = 2.dp)
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.music_video),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (videoMode) "Song" else "Video",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(panel, canSkipNext, canSkipPrevious) {
                        if (panel != NowPlayingPanel.None) return@pointerInput
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = {
                                scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                            onDragEnd = {
                                when {
                                    total <= -swipeThresholdPx && canSkipNext -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        playerConnection.seekToNext()
                                    }
                                    total >= swipeThresholdPx && canSkipPrevious -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        playerConnection.seekToPrevious()
                                    }
                                }
                                scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                            onHorizontalDrag = { change, delta ->
                                total += delta
                                change.consume()
                                // Damped: it's a hint, not a drag-to-position.
                                scope.launch { swipeOffset.snapTo(total * 0.35f) }
                            },
                        )
                    }
                    // Upward drags on the cover pull the queue in behind it; downward ones are left
                    // for the sheet, so the player closes from the picture. Everywhere else a
                    // vertical drag is held here, so a stray swipe on the controls can't close it.
                    .onGloballyPositioned { bandSpace = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val space = bandSpace
                            val y = space?.localToRoot(down.position)?.y ?: down.position.y
                            val panelUp = panel != NowPlayingPanel.None || collapse.value > 0.01f
                            val top: Float
                            val bottom: Float
                            if (panelUp) {
                                top = space?.positionInRoot()?.y ?: 0f
                                bottom = top + (ART_BOX_TOP_PAD + HEADER_HEIGHT).toPx()
                            } else {
                                top = bandTop
                                bottom = bandBottom
                            }
                            if (y in top..bottom) {
                                if (!panelUp) {
                                    dragQueueIn(
                                        down = down,
                                        travel = bottom - top - HEADER_HEIGHT.toPx(),
                                        onProgress = { value ->
                                            if (!dragging) {
                                                dragging = true
                                                queueFromDrag = true
                                            }
                                            dragSlide.floatValue = value
                                        },
                                        onRelease = { open ->
                                            scope.launch {
                                                collapse.snapTo(dragSlide.floatValue)
                                                dragging = false
                                                haptic.performHapticFeedback(
                                                    if (open) HapticFeedbackType.LongPress
                                                    else HapticFeedbackType.TextHandleMove,
                                                )
                                                panel = if (open) NowPlayingPanel.Queue else NowPlayingPanel.None
                                                settleTick++
                                            }
                                        },
                                    )
                                }
                                return@awaitEachGesture
                            }
                            if (down.isConsumed) return@awaitEachGesture
                            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (drag != null) verticalDrag(drag.id) { it.consume() }
                        }
                    }
                    .padding(horizontal = GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- Everything that changes between the states lives in this one box ----
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp)
                        .onGloballyPositioned { sleeveHome = it.boundsInRoot().topLeft - screenOrigin },
                ) {
                    val fullArt = minOf(maxWidth, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                        .coerceAtLeast(THUMB_SIZE)
                    // The sleeve and its credits travel as one block, centred in what's spare.
                    val groupTop = (maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT)
                        .coerceAtLeast(0.dp) / 2
                    val boxWidth = maxWidth

                    fun artSize(): Dp = lerp(fullArt, THUMB_SIZE, g())
                    fun artTop(): Dp = lerp(groupTop, 0.dp, g())
                    fun artStart(): Dp = lerp((boxWidth - fullArt) / 2, 0.dp, g())
                    fun titleTop(): Dp = lerp(groupTop + fullArt + ART_TITLE_GAP, 0.dp, g())
                    fun titleStart(): Dp = lerp(0.dp, THUMB_SIZE + 12.dp, g())

                    // Where the banner has to stop for the credits under it to stay put. Only
                    // measured on the settled player: the controls' footprint changes under the
                    // lyrics, and the banner should not breathe with it.
                    // The cover runs the full height of the player, behind the controls, rather
                    // than stopping under the credits and dissolving into the colour backdrop.
                    val bannerBottom = if (screenHeight > 0.dp) {
                        screenHeight
                    } else {
                        statusBarTop + TOP_STRIP_HEIGHT + ART_BOX_TOP_PAD + groupTop + fullArt + ART_TITLE_GAP / 2
                    }
                    if ((panel == NowPlayingPanel.None && !collapseStarted || heroHeight == 0.dp) &&
                        bannerBottom != heroHeight
                    ) {
                        SideEffect { heroHeight = bannerBottom }
                    }

                    // ---- The sleeve ----
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = 1f - lyricsFull.value }
                            .offset { IntOffset(artStart().roundToPx(), artTop().roundToPx()) }
                            .layout { measurable, _ ->
                                val side = artSize().roundToPx()
                                val placeable = measurable.measure(Constraints.fixed(side, side))
                                layout(side, side) { placeable.placeRelative(0, 0) }
                            }
                            .onGloballyPositioned { bandTop = it.boundsInRoot().top }
                            .graphicsLayer {
                                val c = g()
                                val idle = artScale + (1f - artScale) * c
                                scaleX = idle
                                scaleY = idle
                                translationX = swipeOffset.value * (1f - c)
                            }
                            .then(
                                if (panel != NowPlayingPanel.None) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { panel = NowPlayingPanel.None }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if ((heroArtLoaded || canvasRendered) && heroT.value > 0.999f) {
                                        if (g() >= 0.999f) 1f else 0f
                                    } else if (heroArtLoaded || canvasRendered) {
                                        1f - heroVisible()
                                    } else {
                                        1f
                                    }
                                }
                                .shadow(if (artLoaded) 10.dp else 0.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!artLoaded) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            AsyncImage(
                                model = artRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onState = { if (it is AsyncImagePainter.State.Success) artLoaded = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // ---- Title, artist, like and more ----
                    Row(
                        modifier = Modifier
                            .graphicsLayer { alpha = 1f - lyricsFull.value }
                            .fillMaxWidth()
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = (titleTop() - lerp(0.dp, (HEADER_HEIGHT - THUMB_SIZE) / 2, g())).roundToPx(),
                                )
                            }
                            .layout { measurable, constraints ->
                                val start = titleStart().roundToPx()
                                val placeable = measurable.measure(constraints.offset(horizontal = -start))
                                layout(
                                    constraints.constrainWidth(placeable.width + start),
                                    constraints.constrainHeight(placeable.height),
                                ) { placeable.placeRelative(start, 0) }
                            }
                            .height(HEADER_HEIGHT)
                            .onGloballyPositioned { bandBottom = it.boundsInRoot().bottom },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                // Scaling the measured type is draw-only; animating fontSize
                                // would reshape both lines every frame.
                                .graphicsLayer {
                                    val scale = 1f - 0.2f * g()
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                },
                        ) {
                            var titleOverflowing by remember { mutableStateOf(false) }
                            val artistsText = mediaMetadata.artists.joinToString(", ") { it.name }
                            // Credits only crawl while they are the screen. Over a panel they are a
                            // label, and a label that moves pulls the eye off what's being read.
                            val scrolls = !collapsePastSettling
                            Crossfade(
                                targetState = mediaMetadata.title to artistsText,
                                animationSpec = tween(durationMillis = 300),
                                label = "npCredits",
                            ) { (title, artists) ->
                                Column {
                                    MarqueeText(
                                        text = title,
                                        style = TextStyle(
                                            fontFamily = SfPro,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 21.sp,
                                            letterSpacing = (-0.2).sp,
                                        ),
                                        color = Color.White,
                                        enabled = scrolls,
                                        trailing = if (mediaMetadata.explicit) {
                                            { ExplicitBadge(color = Color.White.copy(alpha = 0.55f)) }
                                        } else {
                                            null
                                        },
                                        onOverflowChange = { titleOverflowing = it },
                                        modifier = Modifier.opensPage(mediaMetadata.album?.id) { albumId ->
                                            navController.navigate("album/$albumId")
                                            playerSheetState.collapseSoft()
                                        },
                                    )
                                    MarqueeText(
                                        text = artists,
                                        style = TextStyle(
                                            fontFamily = SfPro,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 21.sp,
                                            letterSpacing = (-0.2).sp,
                                        ),
                                        color = Color.White.copy(alpha = 0.55f),
                                        enabled = scrolls,
                                        startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                                        modifier = Modifier.opensPage(
                                            mediaMetadata.artists.firstOrNull { !it.id.isNullOrBlank() }?.id,
                                        ) { artistId ->
                                            navController.navigate("artist/$artistId")
                                            playerSheetState.collapseSoft()
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        CircleGlyph(
                            icon = if (liked) R.drawable.favorite_filled else R.drawable.favorite_outline,
                            active = liked,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.toggleLike()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        CircleGlyph(icon = R.drawable.more_horiz, onClick = openMenu)
                    }

                    // ---- Lyrics ----
                    if (!glassLayout && (lyricsSettled || lyricsFade > 0.01f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = HEADER_HEIGHT * (1f - lyricsFull.value))
                                .bleedHorizontally(GUTTER)
                                .graphicsLayer { alpha = lyricsFade }
                                .nestedScroll(lyricsScrollConnection),
                        ) {
                            LyricsV2(
                                sliderPositionProvider = sliderPositionProvider,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                            )
                            // Which source these came from, and the way to another — along the foot,
                            // between the pronunciation and translation discs.
                            LyricsSourceChip(
                                mediaId = mediaMetadata.id,
                                onClick = { showLyricsSources = true },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 20.dp, bottom = 30.dp),
                            )
                            // Full screen, and back.
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 74.dp, bottom = 30.dp)
                                    .size(44.dp)
                                    .playerGlass(CircleShape, lit = lyricsFullscreen)
                                    .clip(CircleShape)
                                    .clickable {
                                        lyricsFullscreen = !lyricsFullscreen
                                        if (!lyricsFullscreen) lyricsControlsOpen = true
                                    },
                            ) {
                                Icon(
                                    painter = painterResource(if (lyricsFullscreen) R.drawable.expand_more else R.drawable.fullscreen),
                                    contentDescription = if (lyricsFullscreen) "Exit full-screen lyrics" else "Full-screen lyrics",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            // The lyric settings, here as well as in the glass layout — without it,
                            // switching to Classic left no way back.
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 128.dp, bottom = 30.dp)
                                    .size(44.dp)
                                    .playerGlass(CircleShape)
                                    .clip(CircleShape)
                                    .clickable { showLyricsSettings = true },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.settings),
                                    contentDescription = "Lyrics settings",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // ---- Queue ----
                    val queueComposed = panel != NowPlayingPanel.Lyrics &&
                        (dragging || queueFromDrag || queueSettled || queueFade > 0.01f)
                    if (queueComposed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = HEADER_HEIGHT)
                                .bleedHorizontally(GUTTER)
                                .graphicsLayer {
                                    alpha = if (dragging || queueFromDrag) {
                                        ((p() - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                    } else {
                                        queueFade
                                    }
                                    translationY = (1f - p()) * 26.dp.toPx()
                                },
                        ) {
                            NowPlayingQueue(
                                navController = navController,
                                playerSheetState = playerSheetState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // ---- The controls: measured at their natural height and pinned to the foot ----
                SlidingDeck(visible = deckVisible, reveal = deckReveal) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = MAX_WIDTH)
                            .fillMaxWidth()
                            // Closing, the controls step out of the way first, so what reaches
                            // the mini player is the cover and its title.
                            .graphicsLayer { alpha = (1f - 1.8f * sheetClose()).coerceIn(0f, 1f) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // One slot, the same height whichever line is in it, so opening the
                        // lyrics never resizes the controls under the sleeve.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (panel == NowPlayingPanel.Lyrics) {
                                LyricsStatusRow(onChange = { showLyricsSettings = true })
                            } else {
                                CurrentLyricStrip(
                                    trackKey = mediaMetadata.id,
                                    isPlaying = isPlaying,
                                    onClick = { panel = NowPlayingPanel.Lyrics },
                                )
                            }
                        }

                        PlayerScrubber(
                            positionProvider = positionProvider,
                            durationProvider = durationProvider,
                            sliderPositionProvider = sliderPositionProvider,
                            onScrub = onSliderValueChange,
                            onScrubFinished = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSliderValueChangeFinished()
                            },
                            onScrubbingChange = { scrubbing = it },
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PlayerSoundLine(format = currentFormat)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TransportRow(
                            isPlaying = isPlaying,
                            isLoading = playbackState == Player.STATE_BUFFERING,
                            ended = playbackState == Player.STATE_ENDED,
                            previousEnabled = canSkipPrevious,
                            nextEnabled = canSkipNext,
                            onPrevious = playerConnection::seekToPrevious,
                            onPlayPause = {
                                if (playbackState == Player.STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                            onNext = playerConnection::seekToNext,
                        )

                        Spacer(Modifier.height(12.dp))

                        VolumeRow(
                            value = playerVolume,
                            onValueChange = { playerConnection.service.playerVolume.value = it },
                            onDraggingChange = { scrubbing = it },
                        )

                        Spacer(Modifier.height(6.dp))

                        ActionRow(
                            lyricsOpen = panel == NowPlayingPanel.Lyrics,
                            queueOpen = panel == NowPlayingPanel.Queue,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            infiniteQueue = infiniteQueue,
                            togetherActive = togetherActive,
                            onToggleLyrics = toggleLyrics,
                            onToggleQueue = toggleQueue,
                            onToggleShuffle = {
                                playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                            },
                            onCycleRepeat = { playerConnection.player.toggleRepeatMode() },
                            onToggleInfinite = {
                                val next = !infiniteQueue
                                infiniteQueue = next
                                if (next) playerConnection.service.onInfiniteQueueEnabled()
                                else playerConnection.service.onInfiniteQueueDisabled()
                            },
                            onOutput = { showOutputSheet = true },
                            onTogether = {
                                navController.navigate("settings/music_together")
                                playerSheetState.collapseSoft()
                            },
                        )
                        Spacer(Modifier.height(18.dp))
                        OutputCaption(onClick = { showOutputSheet = true })
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }


        if (glassLyrics || glassFade.value > 0.001f) {
            GlassLyricsView(
                mediaMetadata = mediaMetadata,
                player = playerConnection.player,
                isPlaying = isPlaying,
                isLoading = playbackState == Player.STATE_BUFFERING,
                liked = liked,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                linesReady = glassLyrics && collapseDone,
                thumbLanded = glassLyrics && collapseAlmostDone,
                appear = { FastOutSlowInEasing.transform((2f * glassFade.value - 1f).coerceIn(0f, 1f)) },
                onThumbPlaced = { glassThumbHome = it - screenOrigin },
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                sliderPositionProvider = sliderPositionProvider,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                onShowArtwork = { panel = NowPlayingPanel.None },
                onOutput = { showOutputSheet = true },
                onSettings = { showLyricsSettings = true },
                onToggleLike = { playerConnection.toggleLike() },
                modifier = Modifier.graphicsLayer { alpha = (2f * glassFade.value - 1f).coerceIn(0f, 1f) },
            )
        }
        }

        if (showLyricsSettings) {
            LyricsSettingsSheet(
                onOpenSources = {
                    showLyricsSettings = false
                    showLyricsSources = true
                },
                onDismiss = { showLyricsSettings = false },
            )
        }

        if (showLyricsSources) {
            LyricsSourceSheet(mediaMetadata = mediaMetadata, onDismiss = { showLyricsSources = false })
        }

        if (showOutputSheet) {
            val devices = rememberOutputDevices()
            DeviceSelectionBottomSheet(
                onDismiss = { showOutputSheet = false },
                availableDevices = devices.all,
                activeDevice = devices.active,
                onDeviceSelected = { showOutputSheet = false },
            )
        }

        if (showLyricsMenu) {
            val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
            Dialog(
                onDismissRequest = { showLyricsMenu = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                LyricsMenu(
                    lyricsProvider = { currentLyrics },
                    mediaMetadataProvider = { mediaMetadata },
                    onDismiss = { showLyricsMenu = false },
                )
            }
        }
    }
}

/**
 * The upward half of the cover's vertical gesture: dragged up, the cover pulls the queue in
 * behind it, following the finger, and settles to whichever end it was nearer on release.
 *
 * Downward is deliberately left alone. Whether a drag is up or down can only be known once it
 * crosses the touch slop, so that is where the decision is made: consuming the crossing event keeps
 * the sheet out of an upward drag, and leaving it hands the sheet a downward one.
 */
private suspend fun AwaitPointerEventScope.dragQueueIn(
    down: PointerInputChange,
    travel: Float,
    onProgress: (Float) -> Unit,
    onRelease: (Boolean) -> Unit,
) {
    if (travel < 1f) return
    var pulled = 0f
    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
        if (overSlop < 0f) {
            pulled = -overSlop
            change.consume()
        }
    }
    if (drag == null || pulled <= 0f) return

    val velocity = VelocityTracker()
    velocity.addPointerInputChange(drag)
    onProgress((pulled / travel).coerceIn(0f, 1f))
    verticalDrag(drag.id) { change ->
        velocity.addPointerInputChange(change)
        pulled -= change.positionChange().y
        onProgress((pulled / travel).coerceIn(0f, 1f))
        change.consume()
    }
    val flick = -velocity.calculateVelocity().y
    val progress = (pulled / travel).coerceIn(0f, 1f)
    onRelease(
        when {
            flick >= QUEUE_FLICK_VELOCITY -> true
            flick <= -QUEUE_FLICK_VELOCITY -> false
            else -> progress >= QUEUE_CARRY_FRACTION
        },
    )
}

/**
 * The lower deck, with one value owning both its pixels and the room it takes: it reports a
 * fraction of its real height, so the box above grows into the space as it slides away.
 */
@Composable
private fun SlidingDeck(
    visible: Boolean,
    reveal: Animatable<Float, AnimationVector1D>,
    content: @Composable () -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            reveal.animateTo(1f, tween(COLLAPSE_MS, easing = FastOutSlowInEasing))
        } else {
            reveal.animateTo(0f, tween(COLLAPSE_MS, easing = FastOutSlowInEasing))
            mounted = false
        }
    }
    if (mounted) {
        Box(
            modifier = Modifier
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val height = (placeable.height * reveal.value).roundToInt()
                        .coerceIn(constraints.minHeight, constraints.maxHeight)
                    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                },
        ) {
            content()
        }
    }
}

// ============================================================================================
// Controls
// ============================================================================================

/** The seek bar, elapsed and remaining under it, and [centerLabel] between the two. */
@Composable
private fun PlayerScrubber(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPositionProvider: () -> Long?,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    centerLabel: @Composable BoxScope.() -> Unit,
) {
    // Read here, so the playhead's tick recomposes the bar and its two times, not the player.
    val duration = durationProvider().takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
    val shownMs = (sliderPositionProvider() ?: positionProvider()).coerceIn(0L, max(0L, duration))
    val fraction = if (duration > 0L) shownMs.toFloat() / duration else 0f
    val latestDuration by rememberUpdatedState(duration)
    val accent = LocalPlayerAccent.current
    Column(Modifier.fillMaxWidth()) {
        ThinSlider(
            value = fraction,
            onValueChange = { f -> if (latestDuration > 0L) onScrub((f * latestDuration).toLong()) },
            onValueChangeFinished = onScrubFinished,
            onDraggingChange = onScrubbingChange,
            // Mostly white, carrying the cover's colour: a tint, not a new colour for the bar.
            activeColor = androidx.compose.ui.graphics.lerp(Color.White, accent, 0.55f).copy(alpha = 0.95f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // The slider's touch target reaches well past the drawn bar.
                .offset(y = (-9).dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = makeTimeString(shownMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = if (duration > 0L) "-" + makeTimeString(duration - shownMs) else "--:--",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            // Pinned to the centre rather than squeezed between the times, whose widths change.
            centerLabel()
        }
    }
}

/**
 * Apple's scrubber: a capsule with no thumb, which thickens under the finger and settles back when
 * it lets go. One gesture loop for taps and drags, so a tap seeks as well.
 */
@Composable
private fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    onDraggingChange: (Boolean) -> Unit = {},
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),
) {
    var dragging by remember { mutableStateOf(false) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnDragging by rememberUpdatedState(onDraggingChange)
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "npSliderHeight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(activeHeight + 22.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    latestOnDragging(true)
                    latestOnValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            latestOnValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }
                    dragging = false
                    latestOnDragging(false)
                    latestOnFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(filled.coerceAtLeast(size.height).coerceAtMost(size.width), size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Previous, play/pause, next — oversized glyphs with no containers, Apple-style. */
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    isLoading: Boolean,
    ended: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val playTouch = 92.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        // SpaceAround: the outer margins take half a gap, spreading the three a little wider.
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportGlyph(
            icon = R.drawable.ic_np_previous,
            size = 53.dp,
            touchSize = 60.dp,
            heightScale = 0.85f,
            enabled = previousEnabled,
            onClick = onPrevious,
        )
        if (isLoading) {
            // Same footprint as the play target, so nothing below shifts on every load.
            Box(Modifier.size(playTouch), contentAlignment = Alignment.Center) {
                LoadingRing(
                    modifier = Modifier.size(38.dp),
                    color = Color.White,
                    stroke = 3.dp,
                )
            }
        } else {
            TransportGlyph(
                icon = when {
                    ended -> R.drawable.replay
                    isPlaying -> R.drawable.ic_np_pause
                    else -> R.drawable.ic_np_play
                },
                size = 74.dp,
                touchSize = playTouch,
                onClick = onPlayPause,
            )
        }
        TransportGlyph(
            icon = R.drawable.ic_np_next,
            size = 53.dp,
            touchSize = 60.dp,
            heightScale = 0.85f,
            enabled = nextEnabled,
            onClick = onNext,
        )
    }
}

/**
 * One transport glyph. No ripple: what acknowledges the press is the glyph itself dipping and
 * springing back. Faded, not hidden, when there is nowhere to go — the row keeps its shape.
 */
@Composable
private fun TransportGlyph(
    icon: Int,
    size: Dp,
    touchSize: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    heightScale: Float = 1f,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "npTransportPress",
    )
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, label = "npTransportAlpha")
    Box(
        modifier = Modifier
            .size(touchSize)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = icon, animationSpec = tween(160), label = "npTransportIcon") { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale * heightScale
                    },
            )
        }
    }
}

/** The volume bar between its two speaker glyphs. */
@Composable
private fun VolumeRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_off),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        ThinSlider(
            value = value,
            onValueChange = onValueChange,
            onDraggingChange = onDraggingChange,
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Lyrics, the capsule, and the queue. Only the capsule swaps: where the sound is going normally,
 * how the queue plays while the queue is up — that is when those are what you reach for.
 */
@Composable
private fun ActionRow(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    infiniteQueue: Boolean,
    togetherActive: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleInfinite: () -> Unit,
    onOutput: () -> Unit,
    onTogether: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Sized for the wider capsule in both states, or the two glyphs would slide as they swap.
        val widestRow = BOTTOM_ACTION_SIZE * 2 + pillWidth(3)
        val edgeInset = ((maxWidth - widestRow) / 4).coerceAtLeast(0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edgeInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomGlyph(
                icon = R.drawable.lyrics_apple,
                highlighted = lyricsOpen,
                onClick = onToggleLyrics,
            )
            AnimatedContent(
                targetState = queueOpen,
                transitionSpec = {
                    (fadeIn(tween(180, delayMillis = 140)) togetherWith fadeOut(tween(140)))
                        .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                },
                label = "npBottomPill",
            ) { showQueueModes ->
                if (showQueueModes) {
                    Pill {
                        PillSegment(
                            icon = R.drawable.shuffle,
                            highlighted = shuffleEnabled,
                            width = PILL_SEGMENT_WIDTH_TRIPLE,
                            onClick = onToggleShuffle,
                        )
                        PillDivider()
                        PillSegment(
                            icon = if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                            highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                            width = PILL_SEGMENT_WIDTH_TRIPLE,
                            onClick = onCycleRepeat,
                        )
                        PillDivider()
                        PillSegment(
                            icon = R.drawable.all_inclusive,
                            highlighted = infiniteQueue,
                            width = PILL_SEGMENT_WIDTH_TRIPLE,
                            onClick = onToggleInfinite,
                        )
                    }
                } else {
                    Pill {
                        PillSegment(
                            icon = R.drawable.headphones,
                            iconSize = 23.dp,
                            onClick = onOutput,
                        )
                        PillDivider()
                        PillSegment(
                            icon = R.drawable.person,
                            iconSize = 22.dp,
                            highlighted = togetherActive,
                            onClick = onTogether,
                        )
                    }
                }
            }
            BottomGlyph(
                icon = R.drawable.queue_music,
                highlighted = queueOpen,
                onClick = onToggleQueue,
            )
        }
    }
}

/** A row of controls joined into one capsule by hairlines, so they read as a single object. */
@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .height(BOTTOM_ACTION_SIZE)
            .animateContentSize(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
            .playerGlass(CircleShape)
            .clip(CircleShape),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

@Composable
private fun PillSegment(
    icon: Int,
    onClick: () -> Unit,
    iconSize: Dp = 22.dp,
    highlighted: Boolean = false,
    width: Dp = PILL_SEGMENT_WIDTH,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .width(width)
            .height(BOTTOM_ACTION_SIZE)
            .background(if (highlighted) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val accent = LocalPlayerAccent.current
        Crossfade(targetState = icon, animationSpec = tween(200), label = "npPillIcon") { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = null,
                tint = if (highlighted) accent else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun BottomGlyph(
    icon: Int,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(BOTTOM_ACTION_SIZE)
            .playerGlass(CircleShape, lit = highlighted)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The translucent round button beside the credits — the like and the menu. */
@Composable
private fun CircleGlyph(
    icon: Int,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val accent = LocalPlayerAccent.current
    val tint by animateColorAsState(if (active) accent else Color.White, label = "npGlyphTint")
    Box(
        modifier = Modifier
            .size(34.dp)
            .playerGlass(CircleShape, lit = active)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = icon, animationSpec = tween(180), label = "npCircleGlyph") { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** The small "E" for explicit tracks, kept outside the scrolling text. */
@Composable
private fun ExplicitBadge(color: Color) {
    // Apple's mark: a small solid tile with the E cut out of it, after the title in the title's
    // own grey — not an outlined letter in front of it.
    Icon(
        painter = painterResource(R.drawable.explicit),
        contentDescription = "Explicit",
        tint = color,
        modifier = Modifier.size(15.dp),
    )
}

/** A credit that links somewhere, when there is somewhere to go. */
private fun Modifier.opensPage(id: String?, onOpen: (String) -> Unit): Modifier =
    if (id.isNullOrBlank()) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(id) }
    }

/**
 * Measures a child wider than its slot by [gutter] each side and places it back over that margin,
 * so a scrolling list reaches the screen's edges instead of leaving a strip of bare sheet there.
 */
private fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(-(placeable.width - width) / 2, 0)
    }
}

// ============================================================================================
// Output
// ============================================================================================

private data class OutputDevices(val all: List<AudioDeviceInfo>, val active: AudioDeviceInfo?)

private val OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
)

private fun AudioDeviceInfo.isBluetooth(): Boolean =
    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        type == AudioDeviceInfo.TYPE_BLE_SPEAKER

private fun readOutputDevices(context: Context): OutputDevices {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val all = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in OUTPUT_TYPES }
        .distinctBy { it.type to it.productName?.toString() }
        .sortedBy {
            when {
                it.isBluetooth() -> 0
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 2
                else -> 1
            }
        }
    // Bluetooth, then anything plugged in, then the phone's own speaker.
    return OutputDevices(all = all, active = all.firstOrNull())
}

/** The output devices, kept current as headphones connect and drop. */
@Composable
private fun rememberOutputDevices(): OutputDevices {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(readOutputDevices(context)) }
    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                devices = readOutputDevices(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                devices = readOutputDevices(context)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return devices
}

private fun outputName(context: Context, device: AudioDeviceInfo?): String {
    val product = device?.productName?.toString()?.trim().orEmpty()
    return when {
        device == null || device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: Build.MODEL
        product.isNotBlank() && product != Build.MODEL -> product
        device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphones"
        device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        else -> "Bluetooth"
    }
}

/** The line under the row: which device the sound is leaving by. Tapping it picks another. */
/**
 * Says the spatialiser is on, in the app's own words and mark. Only while it is actually shaping
 * the sound: off, or on a stream it cannot read, there is nothing to claim.
 */
@Composable
private fun PlayerSoundLine(format: com.ozyern.exhale.db.entities.FormatEntity?) {
    val (spatial) = rememberPreference(SpatialAudioKey, com.ozyern.exhale.utils.DeviceAudio.defaultSpatialAudio)
    val badges = remember(format) { com.ozyern.exhale.ui.component.audioBadgesFor(format) }
    // Apple Music's way of saying it: a glyph and a word in the scrubber's own grey, set in the
    // gap under the bar. No chip, no outline — a boxed label under a hairline scrubber is what
    // read as a sticker.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        badges.forEach { badge ->
            SoundTag(iconRes = R.drawable.waves, label = badge.label)
        }
        AnimatedVisibility(visible = spatial, enter = fadeIn(), exit = fadeOut()) {
            SoundTag(iconRes = R.drawable.ic_spatial_audio, label = "Spatial Audio")
        }
    }
}

@Composable
private fun SoundTag(iconRes: Int, label: String) {
    val ink = Color.White.copy(alpha = 0.6f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = SfPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.1.sp,
            ),
            color = ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun OutputCaption(onClick: () -> Unit) {
    val context = LocalContext.current
    val devices = rememberOutputDevices()
    val name = remember(devices.active) { outputName(context, devices.active) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}

// ============================================================================================
// Lyrics
// ============================================================================================

private sealed interface StripLyrics {
    data object Loading : StripLyrics
    data object Unavailable : StripLyrics
    data object Unsynced : StripLyrics
    class Synced(val lines: List<LyricsEntry>) : StripLyrics
}

private fun stripLyricsOf(raw: String?): StripLyrics = when {
    raw == null -> StripLyrics.Loading
    raw == LYRICS_NOT_FOUND || raw.isBlank() -> StripLyrics.Unavailable
    isTtml(raw) -> parseTtml(raw).takeIf { it.isNotEmpty() }?.let { StripLyrics.Synced(it) }
        ?: StripLyrics.Unavailable
    raw.trimStart().startsWith("[") -> parseLyrics(raw).takeIf { it.isNotEmpty() }?.let { StripLyrics.Synced(it) }
        ?: StripLyrics.Unsynced
    else -> StripLyrics.Unsynced
}

/**
 * The current lyric, one line, directly above the scrubber — or the line saying why there isn't
 * one. It opens the full lyrics.
 *
 * The line is chosen from the player's own position, read directly: it is exact at any moment, so
 * there is no polled clock to reconcile and the line can never step backwards on a late report.
 */
@Composable
private fun CurrentLyricStrip(
    trackKey: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = remember(lyricsEntity?.lyrics) { stripLyricsOf(lyricsEntity?.lyrics) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (lyrics) {
            StripLyrics.Loading -> {
                // The human lines while the lookup runs, one after another.
                val loadingLines = androidx.compose.ui.res.stringArrayResource(R.array.lyrics_loading_lines)
                var shown by remember(trackKey) { mutableIntStateOf(loadingLines.indices.random()) }
                LaunchedEffect(trackKey) {
                    while (isActive) {
                        com.ozyern.exhale.utils.awaitAppVisible()
                        delay(2_600L)
                        shown = (shown + 1) % loadingLines.size
                    }
                }
                AnimatedContent(
                    targetState = loadingLines[shown],
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "npLoadingLine",
                ) { line -> StripText("$line…", alpha = 0.55f) }
            }
            StripLyrics.Unavailable -> {
                // Registers, then gets out of the way rather than sitting there for the whole song.
                var visible by remember(trackKey) { mutableStateOf(true) }
                LaunchedEffect(trackKey) {
                    delay(4_000L)
                    visible = false
                }
                val alpha by animateFloatAsState(if (visible) 0.55f else 0f, tween(800), label = "npNoLyrics")
                StripText("Lyrics not available", alpha = alpha)
            }
            StripLyrics.Unsynced -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                StripText("Open lyrics", alpha = 1f)
                StripChevron()
            }
            is StripLyrics.Synced -> SyncedStripLine(lyrics.lines, trackKey, isPlaying)
        }
    }
}

@Composable
private fun SyncedStripLine(lines: List<LyricsEntry>, trackKey: String, isPlaying: Boolean) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var index by remember(trackKey, lines) { mutableIntStateOf(-1) }
    LaunchedEffect(trackKey, lines, isPlaying) {
        while (isActive) {
            com.ozyern.exhale.utils.awaitAppVisible()
            val position = playerConnection.player.currentPosition
            index = if (lines.isEmpty() || position < lines.first().time) -1
            else findCurrentLineIndex(lines, position, 0L)
            delay(if (isPlaying) 60L else 300L)
        }
    }
    val current = lines.getOrNull(index)
    val instrumental = current == null || current.text.isBlank()
    // Before the first sung line, say so the way a person would; later gaps stay a quiet note.
    val firstSung = remember(lines) { lines.indexOfFirst { it.text.isNotBlank() } }
    val introLines = androidx.compose.ui.res.stringArrayResource(R.array.lyrics_intro_lines)
    val introLine = remember(trackKey) { introLines.random() }
    val text = when {
        instrumental && firstSung >= 0 && index < firstSung -> introLine
        instrumental -> "♪"
        else -> current!!.text
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = index to text,
            transitionSpec = {
                (
                    fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(340, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() }
                    ).togetherWith(
                    fadeOut(tween(340, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { -(it * 0.35f).toInt() },
                ).using(SizeTransform(clip = false))
            },
            label = "npCurrentLyric",
            modifier = Modifier.weight(1f, fill = false),
        ) { (lineIndex, line) ->
            val entry = lines.getOrNull(lineIndex)?.takeIf { it.text.isNotBlank() }
            if (line == "♪" || entry == null) {
                Text(
                    text = line,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = if (line == "♪") 0.55f else 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SweptStripText(
                    entry = entry,
                    lineEndMs = lines.getOrNull(lineIndex + 1)?.time,
                    isPlaying = isPlaying,
                )
            }
        }
        StripChevron()
    }
}

/**
 * The line being sung, lit word by word: a dim copy underneath and a bright
 * one over it, revealed up to the point the singer has reached with a soft leading edge. Words
 * come from the lyrics' own timings where the source has them; otherwise the line's time is shared
 * across its words by length. The edge moves in the draw pass every frame, never recomposing.
 */
@Composable
private fun SweptStripText(entry: LyricsEntry, lineEndMs: Long?, isPlaying: Boolean) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val text = entry.text
    // Each word as (first char, end char, start ms, end ms).
    val spans = remember(entry, lineEndMs) { stripWordSpans(entry, lineEndMs) }
    val clock = remember { androidx.compose.runtime.mutableLongStateOf(playerConnection.player.currentPosition) }
    LaunchedEffect(entry, isPlaying) {
        while (isActive) {
            com.ozyern.exhale.utils.awaitAppVisible()
            androidx.compose.runtime.withFrameMillis { clock.longValue = playerConnection.player.currentPosition }
            if (!isPlaying) delay(250L)
        }
    }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val feather = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
    Box {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
        )
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val measured = layout ?: return@drawWithContent
                    val x = stripFillX(measured, spans, clock.longValue, size.width)
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color.Black, Color.Transparent),
                            startX = x - feather / 2f,
                            endX = x + feather / 2f,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
        )
    }
}

private class StripWordSpan(val from: Int, val to: Int, val startMs: Long, val endMs: Long)

private fun stripWordSpans(entry: LyricsEntry, lineEndMs: Long?): List<StripWordSpan> {
    val text = entry.text
    val timed = entry.words?.filter { !it.isBackground && it.text.isNotBlank() }.orEmpty()
    if (timed.isNotEmpty()) {
        var cursor = 0
        return timed.mapNotNull { word ->
            val found = text.indexOf(word.text.trim(), cursor)
            if (found < 0) return@mapNotNull null
            cursor = found + word.text.trim().length
            StripWordSpan(found, cursor, (word.startTime * 1000).toLong(), (word.endTime * 1000).toLong())
        }
    }
    // No word timings: no sweep. Guessing them from the line's length runs ahead of the singer.
    return emptyList()
}

private fun stripFillX(
    layout: androidx.compose.ui.text.TextLayoutResult,
    spans: List<StripWordSpan>,
    positionMs: Long,
    width: Float,
): Float {
    if (spans.isEmpty()) return width + 100f // fully lit: a line without word timings shows whole
    val length = layout.layoutInput.text.length
    fun left(offset: Int) = layout.getHorizontalPosition(offset.coerceIn(0, length), true)
    val first = spans.first()
    if (positionMs < first.startMs) return left(first.from)
    for (span in spans) {
        if (positionMs < span.endMs) {
            if (positionMs < span.startMs) return left(span.from)
            val progress = (positionMs - span.startMs).toFloat() / (span.endMs - span.startMs).coerceAtLeast(1L)
            return left(span.from) + (left(span.to) - left(span.from)) * progress
        }
    }
    return width + 100f
}

@Composable
private fun StripText(text: String, alpha: Float) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = alpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StripChevron() {
    Spacer(Modifier.width(6.dp))
    Icon(
        painter = painterResource(R.drawable.chevron_right),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.size(16.dp),
    )
}

/** Under the open lyrics, in the strip's place: what kind of lyrics these are, and a way to change them. */
@Composable
private fun LyricsStatusRow(onChange: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val status = when (val lyrics = stripLyricsOf(lyricsEntity?.lyrics)) {
        StripLyrics.Loading -> "Looking for lyrics…"
        StripLyrics.Unavailable -> "No lyrics found"
        StripLyrics.Unsynced -> "Lyrics"
        is StripLyrics.Synced ->
            if (lyrics.lines.any { !it.words.isNullOrEmpty() }) "Word-synced lyrics" else "Synced lyrics"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = status,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Change",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.72f),
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onChange,
            ),
        )
    }
}

// ============================================================================================
// Queue
// ============================================================================================

/** What a stretch of the queue is, for its heading. */
private enum class QueueSectionKind { HISTORY, NOW, QUEUED, CONTEXT, AUTOPLAY }

private sealed interface QueueEntry {
    val key: Any
}

/** A section heading. [run] ties it to the rows beneath it. */
private class QueueHeading(
    override val key: Any,
    val kind: QueueSectionKind,
    val count: Int,
    val seconds: Int,
) : QueueEntry

/** A song in the player's timeline, at [position] in play order. */
private class QueueSong(
    override val key: Any,
    val window: Timeline.Window,
    val position: Int,
    val tier: QueueTier,
    /** Which run of same-tier songs this belongs to; a drag never leaves its own. */
    val run: Int,
    val current: Boolean,
    val heard: Boolean,
) : QueueEntry

/** An Autoplay suggestion not yet in the timeline: what will follow when the queue runs out. */
private class QueueSuggestion(
    override val key: Any,
    val item: androidx.media3.common.MediaItem,
    val index: Int,
) : QueueEntry

/**
 * The queue in sections: what has played, what is playing, **Next in Queue** —
 * what you added, played first and once — then **Continue Playing** from the album or playlist,
 * then **Autoplay**, with its own switch and the songs it has lined up. A section heading carries
 * how many songs and how long; a row drags only within its own section, swipes away, and a tap
 * skips the way it reads: tap a song further down the album and the queued songs still come next.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingQueue(
    navController: NavController,
    playerSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val haptic = LocalHapticFeedback.current

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val shuffled by playerConnection.shuffleModeEnabled.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val suggestions by playerConnection.service.automixItems.collectAsState()
    val (autoplay, setAutoplay) = rememberPreference(com.ozyern.exhale.constants.AutoLoadMoreKey, true)
    val togetherState by playerConnection.service.togetherSessionState.collectAsState()
    val joined = togetherState as? com.ozyern.exhale.together.TogetherSessionState.Joined
    val guest = joined?.role is com.ozyern.exhale.together.TogetherRole.Guest

    val windows = remember { mutableStateListOf<Timeline.Window>() }
    LaunchedEffect(queueWindows) {
        windows.clear()
        windows.addAll(queueWindows)
    }
    val currentUid = queueWindows.getOrNull(currentWindowIndex)?.uid
    val activeIndex = windows.indexOfFirst { it.uid == currentUid }

    // The list, as sections. Rebuilt from [windows], which a drag reorders live.
    val entries: List<QueueEntry> = run {
        val out = ArrayList<QueueEntry>(windows.size + 8)
        fun secondsOf(from: Int, until: Int) =
            (from until until).sumOf { windows[it].mediaItem.metadata?.duration?.coerceAtLeast(0) ?: 0 }
        // No History section: the queue starts at what is playing, and so does this one.
        if (activeIndex >= 0) {
            val w = windows[activeIndex]
            out += QueueHeading("h-now", QueueSectionKind.NOW, 1, 0)
            out += QueueSong(w.uid.hashCode(), w, activeIndex, w.mediaItem.queueTier, run = -2, current = true, heard = false)
        }
        var i = activeIndex + 1
        var run = 0
        var sawAutoplay = false
        while (i < windows.size) {
            val tier = windows[i].mediaItem.queueTier
            var end = i
            while (end < windows.size && windows[end].mediaItem.queueTier == tier) end++
            val kind = when (tier) {
                QueueTier.USER -> QueueSectionKind.QUEUED
                QueueTier.CONTEXT -> QueueSectionKind.CONTEXT
                QueueTier.AUTOPLAY -> QueueSectionKind.AUTOPLAY
            }
            if (tier == QueueTier.AUTOPLAY) sawAutoplay = true
            out += QueueHeading("h-run-$run-${windows[i].uid.hashCode()}", kind, end - i, secondsOf(i, end))
            for (k in i until end) {
                val w = windows[k]
                out += QueueSong(w.uid.hashCode(), w, k, tier, run = run, current = false, heard = false)
            }
            run++
            i = end
        }
        // What Autoplay will add once the queue runs out — or that it's off.
        val pending = if (autoplay) suggestions.take(20) else emptyList()
        if (!sawAutoplay) {
            out += QueueHeading("h-autoplay", QueueSectionKind.AUTOPLAY, pending.size, 0)
        }
        pending.forEachIndexed { index, item -> out += QueueSuggestion("s-${item.mediaId}-$index", item, index) }
        out
    }
    val latestEntries by rememberUpdatedState(entries)

    val listState = rememberLazyListState()
    // Open with what is playing at the top; follow it down as the songs change, but never while
    // someone is scrolling or dragging.
    val nowKey = "h-now"
    // Keyed on the playing row existing as well: the list first draws before the queue has
    // arrived — suggestions only — and when the songs are then inserted above, a lazy list holds
    // the row it was showing, which parked the page at its foot with nothing to bring it back.
    val nowShown = activeIndex >= 0
    var placedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(currentUid, nowShown) {
        if (!nowShown) return@LaunchedEffect
        val at = latestEntries.indexOfFirst { it.key == nowKey }.coerceAtLeast(0)
        if (!placedOnce) {
            listState.scrollToItem(at)
            placedOnce = true
        } else if (!listState.isScrollInProgress) {
            listState.animateScrollToItem(at)
        }
    }

    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        val a = latestEntries.firstOrNull { it.key == from.key } as? QueueSong ?: return@rememberReorderableLazyListState
        val b = latestEntries.firstOrNull { it.key == to.key } as? QueueSong ?: return@rememberReorderableLazyListState
        // Within its own section only: a queued song dragged into the album would stop being queued.
        if (a.run < 0 || a.run != b.run) return@rememberReorderableLazyListState
        if (a.position !in windows.indices || b.position !in windows.indices) return@rememberReorderableLazyListState
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        dragInfo = dragInfo?.let { it.first to b.position } ?: (a.position to b.position)
        windows.move(a.position, b.position)
    }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (reorderState.isAnyItemDragging) return@LaunchedEffect
        val (from, to) = dragInfo ?: return@LaunchedEffect
        dragInfo = null
        if (from == to || queueWindows.isEmpty()) return@LaunchedEffect
        val safeFrom = from.coerceIn(0, queueWindows.lastIndex)
        val safeTo = to.coerceIn(0, queueWindows.lastIndex)
        if (!playerConnection.player.shuffleModeEnabled) {
            playerConnection.player.moveMediaItem(safeFrom, safeTo)
        } else {
            playerConnection.player.setShuffleOrder(
                DefaultShuffleOrder(
                    queueWindows.map { it.firstPeriodIndex }
                        .toMutableList()
                        .move(safeFrom, safeTo)
                        .toIntArray(),
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    // The last removal, for a moment, so a swipe can be taken back.
    var lastRemoved by remember { mutableStateOf<Pair<Int, androidx.media3.common.MediaItem>?>(null) }
    LaunchedEffect(lastRemoved) {
        if (lastRemoved != null) {
            delay(4_000)
            lastRemoved = null
        }
    }

    val hasQueued = entries.any { it is QueueSong && it.tier == QueueTier.USER && !it.current }
    Column(modifier) {
    // "Queue", fixed above the list, with Clear when there is anything to clear.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER, end = GUTTER - 6.dp, bottom = 2.dp),
    ) {
        Text(
            text = "Queue",
            style = TextStyle(fontFamily = SfPro, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (hasQueued && !guest) {
            Text(
                text = "Clear",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { playerConnection.service.clearQueuedTracks() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    Box(Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .queueFadingEdges(),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is QueueHeading -> item(key = entry.key) {
                        QueueSectionHeading(
                            heading = entry,
                            source = queueTitle,
                            shuffled = shuffled,
                            autoplay = autoplay,
                            canEdit = !guest,
                            onClear = { playerConnection.service.clearQueuedTracks() },
                            onAutoplayChange = setAutoplay,
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = QueueRowMotion),
                        )
                    }

                    is QueueSuggestion -> item(key = entry.key) {
                        val metadata = entry.item.metadata ?: return@item
                        NpQueueRow(
                            metadata = metadata,
                            current = false,
                            playing = false,
                            heard = false,
                            lifted = false,
                            showHandle = false,
                            handle = Modifier,
                            suggestion = true,
                            onClick = {
                                if (guest) return@NpQueueRow
                                // Play it now: in as the next song, then straight to it.
                                playerConnection.service.playNextAutomix(entry.item, entry.index)
                                playerConnection.seekToNext()
                                playerConnection.player.playWhenReady = true
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = playerSheetState,
                                        isQueueTrigger = true,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show { ShowMediaInfo(entry.item.mediaId) }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = QueueRowMotion),
                        )
                    }

                    is QueueSong -> item(key = entry.key) {
                        ReorderableItem(state = reorderState, key = entry.key) { isDragging ->
                            val window = entry.window
                            val metadata = window.mediaItem.metadata ?: return@ReorderableItem
                            val editable = !guest && !entry.current && !entry.heard

                            val onRowClick: () -> Unit = onRowClick@{
                                if (entry.current) {
                                    playerConnection.player.togglePlayPause()
                                    return@onRowClick
                                }
                                if (guest) {
                                    if (joined?.roomState?.settings?.allowGuestsToControlPlayback != true) {
                                        Toast.makeText(context, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                                        return@onRowClick
                                    }
                                    val trackId = metadata.id.trim().ifBlank { window.mediaItem.mediaId.trim() }
                                    if (trackId.isBlank()) return@onRowClick
                                    Toast.makeText(context, R.string.together_requesting_song_change, Toast.LENGTH_SHORT).show()
                                    playerConnection.service.requestTogetherControl(
                                        com.ozyern.exhale.together.ControlAction.SeekToTrack(trackId = trackId, positionMs = 0L),
                                    )
                                } else {
                                    playerConnection.service.playQueueEntry(window.firstPeriodIndex)
                                }
                            }
                            val onRowLongClick: () -> Unit = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = playerSheetState,
                                        isQueueTrigger = true,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show { ShowMediaInfo(window.mediaItem.mediaId) }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                            val row: @Composable () -> Unit = {
                                NpQueueRow(
                                    metadata = metadata,
                                    current = entry.current,
                                    playing = entry.current && isPlaying,
                                    heard = entry.heard,
                                    lifted = isDragging,
                                    showHandle = editable,
                                    handle = if (editable) Modifier.draggableHandle() else Modifier,
                                    onClick = onRowClick,
                                    onLongClick = onRowLongClick,
                                    onRemove = if (guest || entry.heard) null else {
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val index = window.firstPeriodIndex
                                            lastRemoved = index to window.mediaItem
                                            playerConnection.player.removeMediaItem(index)
                                        }
                                    },
                                )
                            }
                            if (!editable) {
                                row()
                            } else {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { total -> total * 0.5f },
                                )
                                var removed by remember { mutableStateOf(false) }
                                LaunchedEffect(dismissState.currentValue) {
                                    val value = dismissState.currentValue
                                    if (!removed && (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart)) {
                                        removed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        val index = window.firstPeriodIndex
                                        lastRemoved = index to window.mediaItem
                                        playerConnection.player.removeMediaItem(index)
                                    }
                                }
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = { QueueSwipeBackground(dismissState, GUTTER) },
                                ) {
                                    row()
                                }
                            }
                        }
                    }
                }
            }
            item(key = "npQueueFoot") { Spacer(Modifier.height(72.dp)) }
        }

        // Undo, for four seconds after a swipe: a removed song goes back where it was.
        androidx.compose.animation.AnimatedVisibility(
            visible = lastRemoved != null,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            val removedTitle = lastRemoved?.second?.metadata?.title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2A2A2E).copy(alpha = 0.96f))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = "Removed" + (removedTitle?.let { " “$it”" } ?: ""),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Undo",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalPlayerAccent.current,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            lastRemoved?.let { (index, item) ->
                                val at = index.coerceIn(0, playerConnection.player.mediaItemCount)
                                playerConnection.player.addMediaItem(at, item)
                            }
                            lastRemoved = null
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    }
}

/** Placement only: a fading copy of a removed row would print one title over another. */
private val QueueRowMotion = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)

/** Softens the list where it meets the header above it and the controls below. */
private fun Modifier.queueFadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 24.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun QueueSectionHeading(
    heading: QueueHeading,
    source: String?,
    shuffled: Boolean,
    autoplay: Boolean,
    canEdit: Boolean,
    onClear: () -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The queue headings: one quiet line each, "Now playing", "Next in queue", "Next from
    // <album>" — and AutoPlay, which says what it is doing under its own name.
    if (heading.kind == QueueSectionKind.AUTOPLAY) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .clickable(enabled = canEdit) { onAutoplayChange(!autoplay) }
                .padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.all_inclusive),
                contentDescription = if (autoplay) "Turn AutoPlay off" else "Turn AutoPlay on",
                tint = Color.White.copy(alpha = if (autoplay) 0.8f else 0.4f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "AutoPlay",
                    style = TextStyle(fontFamily = SfPro, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = Color.White.copy(alpha = if (autoplay) 1f else 0.6f),
                )
                Text(
                    text = when {
                        !autoplay -> "Off — the music stops when the queue ends"
                        heading.count > 0 -> "Similar music, selected to play next"
                        else -> "Finding similar music…"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    val title = when (heading.kind) {
        QueueSectionKind.HISTORY -> "History"
        QueueSectionKind.NOW -> "Now playing"
        QueueSectionKind.QUEUED -> "Next in queue"
        QueueSectionKind.CONTEXT -> when {
            shuffled && !source.isNullOrBlank() -> "Shuffling $source"
            !source.isNullOrBlank() -> "Next from $source"
            else -> "Next up"
        }
        QueueSectionKind.AUTOPLAY -> "AutoPlay"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = GUTTER, end = GUTTER - 6.dp, top = if (heading.kind == QueueSectionKind.NOW) 8.dp else 16.dp, bottom = 6.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontFamily = SfPro, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (heading.kind == QueueSectionKind.QUEUED && canEdit) {
            Text(
                text = "Clear",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NpQueueRow(
    metadata: MediaMetadata,
    current: Boolean,
    playing: Boolean,
    heard: Boolean,
    lifted: Boolean,
    showHandle: Boolean,
    handle: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** An Autoplay suggestion not yet in the queue: drawn a step back from the songs that are. */
    suggestion: Boolean = false,
    /** Takes the song out of the queue; null where it can't be (a guest, or a song already heard). */
    onRemove: (() -> Unit)? = null,
) {
    val liftAlpha by animateFloatAsState(if (lifted) 0.08f else 0f, label = "npQueueLift")
    // The row: handle, artwork, title and artist, the playing glyph, and an ✕.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = liftAlpha))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = if (showHandle) GUTTER - 10.dp else GUTTER, end = GUTTER - 10.dp, top = 6.dp, bottom = 6.dp)
            .graphicsLayer { alpha = if (heard) 0.45f else if (suggestion) 0.78f else 1f },
    ) {
        if (showHandle) {
            Box(modifier = handle.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.drag_handle),
                    contentDescription = "Drag to reorder",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        AsyncImage(
            model = metadata.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (metadata.explicit) {
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = "Explicit",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = metadata.title,
                    style = TextStyle(fontFamily = SfPro, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = if (current) Color.White else Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = metadata.artists.joinToString(", ") { it.name },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            Icon(
                painter = painterResource(if (playing) R.drawable.graphic_eq else R.drawable.ic_np_pause),
                contentDescription = "Now playing",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Remove from queue",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ============================================================================================
// Marquee
// ============================================================================================

private const val MARQUEE_DP_PER_SEC = 26f
private val MARQUEE_GAP = 48.dp
private const val MARQUEE_REST_MS = 5_000L
private const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

/**
 * A single line that scrolls in place only when it is too long to show in full.
 *
 * Text that fits never moves and is never faded — the fade belongs to the scroll, not the line.
 * When it does scroll, the line is drawn twice with a gap and crawled left by exactly one copy plus
 * the gap, so the reset at the end of a pass falls under a copy already in place.
 */
@Composable
private fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayMillis: Long = 0L,
    leading: (@Composable () -> Unit)? = null,
    /** Held just after the line and outside the scroll, as Apple Music sets its explicit mark. */
    trailing: (@Composable () -> Unit)? = null,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MarqueeBody(text, style, color, enabled, startDelayMillis, leading, onOverflowChange, density, textMeasurer)
        if (trailing != null) {
            Spacer(Modifier.width(7.dp))
            trailing()
        }
    }
}

@Composable
private fun RowScope.MarqueeBody(
    text: String,
    style: TextStyle,
    color: Color,
    enabled: Boolean,
    startDelayMillis: Long,
    leading: (@Composable () -> Unit)?,
    onOverflowChange: (Boolean) -> Unit,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    run {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        if (!enabled) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            return@run
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).clipToBounds()) {
            val maxWidthPx = constraints.maxWidth
            val measured = remember(text, style, maxWidthPx) {
                textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
            }
            val overflowing = measured.size.width > maxWidthPx
            val latestOnOverflow by rememberUpdatedState(onOverflowChange)
            LaunchedEffect(overflowing) { latestOnOverflow(overflowing) }

            val travelPx = if (overflowing) {
                measured.size.width + with(density) { MARQUEE_GAP.roundToPx() }
            } else {
                0
            }
            val offsetX = remember { Animatable(0f) }
            LaunchedEffect(text, travelPx, startDelayMillis) {
                offsetX.snapTo(0f)
                if (travelPx <= 0) return@LaunchedEffect
                val pxPerMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
                val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
                delay(MARQUEE_REST_MS / 2 + startDelayMillis)
                while (true) {
                    com.ozyern.exhale.utils.awaitAppVisible()
                    offsetX.animateTo(-travelPx.toFloat(), tween(scrollMs, easing = LinearEasing))
                    offsetX.snapTo(0f)
                    delay(MARQUEE_REST_MS)
                }
            }
            Row(
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .then(
                        // Soft edges only while the line is actually moving through them.
                        if (overflowing) Modifier.marqueeEdgeFade(maxWidthPx, offsetX) else Modifier,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarqueeLine(text, style, color)
                if (overflowing) {
                    Spacer(Modifier.width(MARQUEE_GAP))
                    MarqueeLine(text, style, color)
                }
            }
        }
    }
}

/** Fades the visible window's two edges on a line wider than it — the window, not the text. */
private fun Modifier.marqueeEdgeFade(windowPx: Int, offset: Animatable<Float, AnimationVector1D>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val start = -offset.value
        val edge = 18.dp.toPx().coerceAtMost(windowPx / 4f)
        // The leading edge only fades once the line has started moving off it.
        val leadAlpha = if (offset.value < -0.5f) 0f else 1f
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = leadAlpha),
                edge / size.width to Color.Black,
                (windowPx - edge) / size.width to Color.Black,
                windowPx / size.width to Color.Transparent,
                1f to Color.Transparent,
                startX = start,
                endX = start + size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun MarqueeLine(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

// ============================================================================================
// The colour backdrop
// ============================================================================================

// Neutral until the cover has been read: a coloured placeholder flashed purple on every open.
private val FallbackMesh = listOf(
    Color(0xFF2A2A2E),
    Color(0xFF3A3A40),
    Color(0xFF232327),
    Color(0xFF45454C),
)

@Immutable
internal data class MeshPalette(val colors: List<Color>, val accent: Color = DefaultAccent)

/** Until a cover has been read: the warm gold of Exhale's own mark. */
private val DefaultAccent = Color(0xFFF3CE6A)

/**
 * The cover itself as the ground of the player, alive: three copies of it, each larger than the
 * screen, turning slowly against each other and drifting on small orbits so its colours flow into
 * one another the way Apple Music's do. Slower while paused, a little quicker when the music is
 * loud, never still.
 *
 * It costs almost nothing per frame. The cover is read once at 64px, blurred once in software at
 * 160px, and the two copies that float over the first have their edges feathered away into the
 * same pixels, so no edge ever crosses the screen. Stretched across the screen that *is* the blur:
 * no blur effect runs while it moves, and each frame only draws three bitmaps.
 */
@Composable
private fun LiveArtworkBackdrop(
    artUrl: String?,
    palette: MeshPalette,
    isPlaying: Boolean,
    /** How loud the music is right now, 0..1, smoothed. Read while drawing. */
    breath: () -> Float,
    /** The kick: bass jumping above its own recent level, 0..1. Read while drawing. */
    kick: () -> Float,
) {
    val context = LocalContext.current
    // Held across a skip until the next cover is ready, so the ground never flashes empty.
    var art by remember { mutableStateOf<LiveArt?>(null) }
    LaunchedEffect(artUrl) {
        if (artUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artUrl)
            .size(64, 64)
            .allowHardware(false)
            .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@LaunchedEffect
        art = withContext(Dispatchers.Default) { liveArtOf(bitmap) }
    }

    val colors = (palette.colors.ifEmpty { FallbackMesh } + FallbackMesh).take(2).map { it.tuned() }
    val baseColor by animateColorAsState(colors.first().dimmed(), tween(1200), label = "npLiveBase")
    val glowColor by animateColorAsState(colors[1], tween(1200), label = "npLiveGlow")

    // One clock for the motion, in degrees, advanced by a speed that eases between playing and
    // paused rather than jumping — the drift settles to a crawl on pause and picks back up on play.
    val playing by rememberUpdatedState(isPlaying)
    val turn = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        var speed = 1f
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val target = if (playing) 1f else 0.3f
                speed += (target - speed) * (1f - kotlin.math.exp(-dt / 0.8f))
                turn.floatValue = (turn.floatValue + dt * LIVE_ART_DEG_PER_SEC * speed * (1f + 0.5f * breath())) % 360_000f
            }
        }
    }

    val saturate = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.3f) }) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
        Crossfade(targetState = art, animationSpec = tween(1200), label = "npLiveArt") { current ->
            if (current != null) {
                Canvas(Modifier.fillMaxSize().clipToBounds()) {
                    val t = turn.floatValue
                    val swell = 1f + 0.05f * breath() + 0.03f * kick()
                    // Sized against the long side, so the solid copy covers every corner at any angle.
                    val side = size.maxDimension
                    LIVE_ART_LAYERS.forEachIndexed { index, layer ->
                        val image = if (index == 0) current.solid else current.feathered
                        val orbit = ((t * layer.orbitSpeed + layer.phase) * PI / 180.0)
                        val cx = size.width * (0.5f + layer.x + layer.orbit * cos(orbit).toFloat())
                        val cy = size.height * (0.5f + layer.y + layer.orbit * 0.6f * sin(orbit * 1.3).toFloat())
                        val drawn = (side * layer.scale * swell).roundToInt()
                        rotate(degrees = layer.phase + t * layer.spin, pivot = Offset(cx, cy)) {
                            drawImage(
                                image = image,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(image.width, image.height),
                                dstOffset = IntOffset((cx - drawn / 2f).roundToInt(), (cy - drawn / 2f).roundToInt()),
                                dstSize = IntSize(drawn, drawn),
                                alpha = layer.alpha,
                                colorFilter = saturate,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                }
            }
        }
        // Keeps white type legible over a bright cover, deepest where the controls are.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.26f), Color.Black.copy(alpha = 0.46f)),
                    ),
                ),
        )
        // The beat itself: a soft bloom of the cover's colour rising behind the controls on every
        // kick. Plain radial light, no blur, so redrawing it each frame costs next to nothing.
        Canvas(Modifier.fillMaxSize()) {
            val strength = (0.10f * breath() + 0.30f * kick()).coerceIn(0f, 0.45f)
            if (strength <= 0.005f) return@Canvas
            val center = Offset(size.width / 2f, size.height * 0.62f)
            val radius = size.maxDimension * (0.55f + 0.08f * kick())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = strength), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/** The cover, blurred, twice: once solid for the ground, once with its edges feathered to nothing. */
private class LiveArt(val solid: ImageBitmap, val feathered: ImageBitmap)

private class LiveArtLayer(
    /** Drawn size against the screen's long side. */
    val scale: Float,
    /** Turn rate against the base clock; negative turns the other way. */
    val spin: Float,
    /** Starting angle, degrees, for the turn and the orbit. */
    val phase: Float,
    /** Resting centre, as a fraction of the screen away from its middle. */
    val x: Float,
    val y: Float,
    /** Radius of the slow orbit around that centre, as a fraction of the screen. */
    val orbit: Float,
    val orbitSpeed: Float,
    val alpha: Float,
)

/** Base period of the drift: one full turn of the slowest copy in about a minute and a half. */
private const val LIVE_ART_DEG_PER_SEC = 13f

private val LIVE_ART_LAYERS = listOf(
    LiveArtLayer(scale = 1.6f, spin = 1f, phase = 0f, x = 0f, y = 0f, orbit = 0f, orbitSpeed = 0f, alpha = 1f),
    LiveArtLayer(scale = 1.15f, spin = -1.35f, phase = 120f, x = -0.22f, y = -0.16f, orbit = 0.18f, orbitSpeed = 0.9f, alpha = 0.85f),
    LiveArtLayer(scale = 1.0f, spin = 0.8f, phase = 240f, x = 0.24f, y = 0.24f, orbit = 0.20f, orbitSpeed = -0.75f, alpha = 0.75f),
    LiveArtLayer(scale = 0.8f, spin = -0.6f, phase = 60f, x = 0.05f, y = -0.28f, orbit = 0.16f, orbitSpeed = 1.1f, alpha = 0.6f),
)

private const val LIVE_ART_SIDE = 160

/**
 * The cover as the backdrop draws it: upscaled to [LIVE_ART_SIDE], then three passes of a box blur
 * (close to a Gaussian) wide enough that nothing of the artwork's edges survives, only its colour.
 * Done at this size rather than on the 64px read so the screen-wide stretch has smooth gradients
 * to interpolate between instead of blocks.
 */
private fun liveArtOf(source: Bitmap): LiveArt {
    val side = LIVE_ART_SIDE
    val scaled = Bitmap.createScaledBitmap(source, side, side, true)
    val pixels = IntArray(side * side).also { scaled.getPixels(it, 0, side, 0, 0, side, side) }
    val r = IntArray(pixels.size) { (pixels[it] shr 16) and 0xFF }
    val g = IntArray(pixels.size) { (pixels[it] shr 8) and 0xFF }
    val b = IntArray(pixels.size) { pixels[it] and 0xFF }
    repeat(3) {
        for (channel in arrayOf(r, g, b)) {
            boxBlur(channel, side, radius = 9, horizontal = true)
            boxBlur(channel, side, radius = 9, horizontal = false)
        }
    }
    val solid = IntArray(pixels.size)
    val feathered = IntArray(pixels.size)
    val half = side / 2f
    for (y in 0 until side) {
        for (x in 0 until side) {
            val i = y * side + x
            val rgb = (r[i] shl 16) or (g[i] shl 8) or b[i]
            solid[i] = (0xFF shl 24) or rgb
            // A round, soft falloff: full in the middle third, nothing by the edge of the circle.
            val dx = (x + 0.5f - half) / half
            val dy = (y + 0.5f - half) / half
            val d = sqrt(dx * dx + dy * dy)
            val k = ((1f - d) / 0.6f).coerceIn(0f, 1f)
            val a = (k * k * (3f - 2f * k) * 255f).roundToInt()
            // Premultiplied, as Bitmap stores it.
            feathered[i] = (a shl 24) or
                ((r[i] * a / 255) shl 16) or ((g[i] * a / 255) shl 8) or (b[i] * a / 255)
        }
    }
    fun bitmapOf(data: IntArray) = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply {
        setPixels(data, 0, side, 0, 0, side, side)
    }.asImageBitmap()
    return LiveArt(solid = bitmapOf(solid), feathered = bitmapOf(feathered))
}

/** One pass of a sliding-window box blur along rows or columns of a square channel, edges clamped. */
private fun boxBlur(channel: IntArray, side: Int, radius: Int, horizontal: Boolean) {
    val line = IntArray(side)
    val window = radius * 2 + 1
    for (lane in 0 until side) {
        fun at(k: Int): Int {
            val c = k.coerceIn(0, side - 1)
            return if (horizontal) channel[lane * side + c] else channel[c * side + lane]
        }
        var sum = 0
        for (k in -radius..radius) sum += at(k)
        for (k in 0 until side) {
            line[k] = sum / window
            sum += at(k + radius + 1) - at(k - radius)
        }
        for (k in 0 until side) {
            if (horizontal) channel[lane * side + k] = line[k] else channel[k * side + lane] = line[k]
        }
    }
}

@Composable
internal fun rememberArtworkPalette(imageUrl: String?): MeshPalette {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(MeshPalette(FallbackMesh)) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128, 128)
            .allowHardware(false)
            .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@LaunchedEffect
        palette = withContext(Dispatchers.Default) { MeshPalette(paletteOf(bitmap), accentOf(bitmap)) }
    }
    return palette
}

/** Four colours from the whole swatch list, topped up from the art's own hues when it is sparse. */
private fun paletteOf(bitmap: Bitmap): List<Color> {
    fun swatchesOf(builder: Palette.Builder): List<Color> =
        builder.maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { Color(it.rgb) }

    val found = swatchesOf(Palette.from(bitmap)).ifEmpty {
        // The default filter drops near-black and near-white, which on a monochrome sleeve is everything.
        swatchesOf(Palette.from(bitmap).clearFilters())
    }
    val distinct = mutableListOf<Color>()
    found.forEach { color -> if (distinct.none { it.isCloseTo(color) }) distinct += color }
    return when {
        distinct.isEmpty() -> FallbackMesh
        distinct.size >= 4 -> distinct.take(4)
        else -> {
            val out = distinct.toMutableList()
            var step = 1
            while (out.size < 4) {
                out += distinct[(out.size - distinct.size) % distinct.size].shifted(24f * step, 0.12f * step)
                step++
            }
            out
        }
    }
}

/**
 * The one colour the details of the player take from the cover: its most vivid light swatch,
 * lifted into a pastel so it reads on the dark lower half and never shouts over the artwork.
 */
private fun accentOf(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).maximumColorCount(24).generate()
    val swatch = palette.lightVibrantSwatch
        ?: palette.vibrantSwatch
        ?: palette.lightMutedSwatch
        ?: palette.dominantSwatch
        ?: return DefaultAccent
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
    // A near-grey cover gets a near-white accent rather than an invented hue.
    hsl[1] = if (hsl[1] < 0.12f) hsl[1] else hsl[1].coerceIn(0.45f, 0.85f)
    hsl[2] = hsl[2].coerceIn(0.70f, 0.82f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Boost saturation and clamp lightness so any artwork yields a rich, non-muddy mesh. */
private fun Color.tuned(): Color {
    val hsl = hsl()
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = hsl()
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}

// ============================================================================================
// Canvas artwork: the resolver and the playback cache use two copies of the same model
// ============================================================================================

internal fun com.ozyern.exhale.canvas.CanvasArtwork.toPlaybackArtwork(): com.ozyern.exhale.canvas.models.CanvasArtwork =
    com.ozyern.exhale.canvas.models.CanvasArtwork(
        name = name,
        artist = artist,
        albumId = albumId,
        albumName = albumName,
        static = static,
        animated = animated,
        animatedVertical = animatedVertical,
        videoUrl = videoUrl,
        videoUrlVertical = videoUrlVertical,
    )

internal fun com.ozyern.exhale.canvas.models.CanvasArtwork.toResolverArtwork(): com.ozyern.exhale.canvas.CanvasArtwork =
    com.ozyern.exhale.canvas.CanvasArtwork(
        name = name,
        artist = artist,
        albumId = albumId,
        albumName = albumName,
        static = static,
        animated = animated,
        animatedVertical = animatedVertical,
        videoUrl = videoUrl,
        videoUrlVertical = videoUrlVertical,
    )

// ============================================================================================
// Landscape
// ============================================================================================

/**
 * The player on a phone turned on its side: two columns, the
 * sleeve on the left and, on the right, either the controls, the lyrics or the queue.
 *
 * The portrait player is a tall thing — a full-bleed cover over a stack of controls — and squeezed
 * into a short, wide window its controls ran off the bottom and its cover off the sides. Nothing
 * here moves between columns: opening the lyrics or the queue only turns the right-hand page.
 */
@Composable
internal fun LandscapeNowPlaying(
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
    val haptic = LocalHapticFeedback.current
    val playerVolume by playerConnection.service.playerVolume.collectAsState()
    var panel by rememberSaveable { mutableStateOf(NowPlayingPanel.None) }
    BackHandler(enabled = panel != NowPlayingPanel.None) { panel = NowPlayingPanel.None }
    val artUrl = mediaMetadata.thumbnailUrl?.highRes()

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // The cover, blurred and dimmed, is the room the two columns sit in.
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .graphicsLayer { alpha = 0.75f },
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compact = maxHeight < 440.dp
            Row(
                modifier = Modifier
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
                    .padding(top = 20.dp, bottom = if (compact) 8.dp else 20.dp),
            ) {
                // ---- The sleeve ----
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = if (compact) 20.dp else GUTTER),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(minOf(maxWidth, maxHeight))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                    )
                }

                // ---- The page: controls, lyrics or the queue ----
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = if (compact) 20.dp else GUTTER),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = panel,
                            transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(140)) },
                            label = "landscapePane",
                            modifier = Modifier.fillMaxSize(),
                        ) { pane ->
                            when (pane) {
                                NowPlayingPanel.Lyrics -> LyricsV2(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                NowPlayingPanel.Queue -> NowPlayingQueue(
                                    navController = navController,
                                    playerSheetState = playerSheetState,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                NowPlayingPanel.None -> Column(
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    MarqueeText(
                                        text = mediaMetadata.title,
                                        style = TextStyle(fontFamily = SfPro, fontWeight = FontWeight.Bold, fontSize = 21.sp),
                                        color = Color.White,
                                        trailing = if (mediaMetadata.explicit) {
                                            { ExplicitBadge(color = Color.White.copy(alpha = 0.55f)) }
                                        } else null,
                                    )
                                    Text(
                                        text = mediaMetadata.artists.joinToString { it.name },
                                        style = TextStyle(fontFamily = SfPro, fontWeight = FontWeight.Bold, fontSize = 21.sp),
                                        color = Color.White.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                                    PlayerScrubber(
                                        positionProvider = positionProvider,
                                        durationProvider = durationProvider,
                                        sliderPositionProvider = sliderPositionProvider,
                                        onScrub = onSliderValueChange,
                                        onScrubFinished = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSliderValueChangeFinished()
                                        },
                                        onScrubbingChange = {},
                                    ) {
                                        Row(Modifier.align(Alignment.Center)) { PlayerSoundLine(format = currentFormat) }
                                    }
                                    TransportRow(
                                        isPlaying = isPlaying,
                                        isLoading = playbackState == Player.STATE_BUFFERING,
                                        ended = playbackState == Player.STATE_ENDED,
                                        previousEnabled = canSkipPrevious,
                                        nextEnabled = canSkipNext,
                                        onPrevious = playerConnection::seekToPrevious,
                                        onPlayPause = {
                                            if (playbackState == Player.STATE_ENDED) {
                                                playerConnection.player.seekTo(0, 0)
                                                playerConnection.player.playWhenReady = true
                                            } else {
                                                playerConnection.player.togglePlayPause()
                                            }
                                        },
                                        onNext = playerConnection::seekToNext,
                                    )
                                    if (!compact) {
                                        Spacer(Modifier.height(8.dp))
                                        VolumeRow(
                                            value = playerVolume,
                                            onValueChange = { playerConnection.service.playerVolume.value = it },
                                            onDraggingChange = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Lyrics and the queue, always here, whichever page is showing.
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        LandscapeToggle(
                            icon = R.drawable.lyrics,
                            active = panel == NowPlayingPanel.Lyrics,
                            onClick = { panel = if (panel == NowPlayingPanel.Lyrics) NowPlayingPanel.None else NowPlayingPanel.Lyrics },
                        )
                        LandscapeToggle(
                            icon = R.drawable.queue_music,
                            active = panel == NowPlayingPanel.Queue,
                            onClick = { panel = if (panel == NowPlayingPanel.Queue) NowPlayingPanel.None else NowPlayingPanel.Queue },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeToggle(icon: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 0.22f else 0.08f))
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (active) 1f else 0.8f),
            modifier = Modifier.size(22.dp),
        )
    }
}
