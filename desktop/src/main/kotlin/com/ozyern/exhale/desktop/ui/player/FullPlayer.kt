/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import androidx.compose.runtime.key
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import com.ozyern.exhale.desktop.player.MotionCovers
import com.ozyern.exhale.desktop.player.MotionCoverRenderer
import androidx.compose.runtime.DisposableEffect
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.data.Settings
import com.ozyern.exhale.desktop.lyrics.Lyrics
import com.ozyern.exhale.desktop.lyrics.LyricsRepository
import com.ozyern.exhale.desktop.lyrics.LyricsSync
import com.ozyern.exhale.desktop.player.AudioOutputStatus
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.DolbyAccess
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.player.RepeatMode
import com.ozyern.exhale.desktop.ui.AmbientGlow
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.FluidArtwork
import com.ozyern.exhale.desktop.ui.LoadingRing
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.PlayPauseIcon
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.fluidBackgroundAvailable
import com.ozyern.exhale.desktop.ui.formatTime
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.rememberPosition
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.desktop.ui.shell.LocalTitleBarHeight
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt

enum class PlayerPanel { None, Lyrics, Queue }

/** The player's open/close progress, so its sections can arrive one after another as it grows out of the dock. */
val LocalPlayerReveal = compositionLocalOf<() -> Float> { { 1f } }

/**
 * False while the player is kept ready but out of sight. Anything that would tick on its own — a
 * marquee, a spinner, the moving background, the time — holds still, so a hidden player costs nothing.
 */
val LocalPlayerActive = compositionLocalOf { true }

/** A marquee only while the player is on screen: a running one asks for frames even when unseen. */
@Composable
private fun Modifier.playerMarquee(): Modifier = if (LocalPlayerActive.current) basicMarquee() else this

/** Fades and rises a section in once the player is [start] of the way open; reversed on the way out. */
@Composable
private fun Modifier.reveal(start: Float, rise: Dp): Modifier {
    val progress = LocalPlayerReveal.current
    return graphicsLayer {
        val t = FastOutSlowInEasing.transform(((progress() - start) / (1f - start)).coerceIn(0f, 1f))
        alpha = t
        translationY = (1f - t) * rise.toPx()
    }
}

/**
 * The full-screen player: the cover's own colours moving slowly behind everything, a
 * large cover that sits back when paused, the title, a hairline progress bar that thickens under
 * the pointer, bare white transport glyphs, and volume. Lyrics and Up Next open beside it.
 * Drag the top bar or the cover down to put it back in the dock.
 *
 * Esc closes (the open panel first), Space plays/pauses, L lyrics, Q up next, ←/→ seek.
 */
@Composable
fun FullPlayer(
    state: PlayerState,
    player: DesktopPlayer,
    onClose: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    initialPanel: PlayerPanel = PlayerPanel.None,
    onArtworkBounds: (Rect) -> Unit = {},
    artworkAlpha: () -> Float = { 1f },
    active: Boolean = true,
) {
    val song = state.current ?: return
    val settings = LocalSettings.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val colors by rememberArtworkColors(song.thumbnail.resizedThumbnail(120))
    val videoMode by player.videoMode.collectAsState()
    var panel by remember { mutableStateOf(initialPanel) }
    // The frame-by-frame lyrics clock runs only while lyrics are showing; otherwise it redrew the whole
    // player at the display's full rate for nothing.
    val smoothPosition = rememberSmoothPositionMs(state, player.position, active = active && panel == PlayerPanel.Lyrics)
    var menuOpen by remember { mutableStateOf(false) }
    // Settings → Lyrics → Open lyrics as: full screen starts lyrics that way when opened from the dock.
    var fullLyrics by remember { mutableStateOf(initialPanel == PlayerPanel.Lyrics && settings.lyricsView == "FULL") }
    // The player stays composed between opens (building it at the start of the morph was the hitch), so
    // each open starts it fresh here, and each close puts it away: panels shut, keyboard handed back.
    LaunchedEffect(active, initialPanel) {
        if (active) {
            panel = initialPanel
            fullLyrics = initialPanel == PlayerPanel.Lyrics && settings.lyricsView == "FULL"
            runCatching { focus.requestFocus() }
        } else {
            panel = PlayerPanel.None
            fullLyrics = false
            menuOpen = false
            if (focused) focusManager.clearFocus()
        }
    }
    var syncShownAt by remember { mutableStateOf(0L) }
    LaunchedEffect(syncShownAt) {
        if (syncShownAt > 0L) {
            delay(1_600)
            syncShownAt = 0L
        }
    }
    val songNudge = LyricsSync.offsetFor(song.id)
    val lyricOffset = settings.lyricsOffsetMs + songNudge

    // Asked again once the song's length is known. Plenty of songs arrive without one — anything off
    // Home or a radio — and the sources that match on runtime have nothing to match against until
    // then, so the first answer is the poor one and this is where the good one arrives.
    val durationKnown = state.durationSeconds > 0
    val lyrics by produceState<Lyrics?>(initialValue = null, song.id, durationKnown) {
        if (!durationKnown) value = null
        value = LyricsRepository.lyricsFor(song, state.durationSeconds.toInt())
    }

    fun toggle(target: PlayerPanel) {
        panel = if (panel == target) PlayerPanel.None else target
    }

    fun toggleFull() {
        if (panel != PlayerPanel.Lyrics) {
            panel = PlayerPanel.Lyrics
            fullLyrics = true
        } else {
            fullLyrics = !fullLyrics
        }
    }

    fun nudge(deltaMs: Long) {
        LyricsSync.nudge(song.id, deltaMs)
        syncShownAt = System.nanoTime()
    }

    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val dismissGesture = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(onDragEnd = { latestDragEnd() }, onDragCancel = { latestDragEnd() }) { change, dy ->
            change.consume()
            latestDrag(dy)
        }
    }

    CompositionLocalProvider(LocalPlayerActive provides active) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF110E12))
            .onFocusChanged { focused = it.hasFocus }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                if (!active || it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.Escape -> {
                        when {
                            menuOpen -> menuOpen = false
                            fullLyrics && panel == PlayerPanel.Lyrics -> fullLyrics = false
                            panel != PlayerPanel.None -> panel = PlayerPanel.None
                            else -> onClose()
                        }
                        true
                    }
                    Key.Spacebar -> { player.togglePlayPause(); true }
                    Key.L -> { toggle(PlayerPanel.Lyrics); true }
                    Key.Q -> { toggle(PlayerPanel.Queue); true }
                    Key.F -> { toggleFull(); true }
                    Key.LeftBracket -> { nudge(100); true }
                    Key.RightBracket -> { nudge(-100); true }
                    Key.Backslash -> {
                        LyricsSync.reset(song.id)
                        syncShownAt = System.nanoTime()
                        true
                    }
                    Key.DirectionRight -> { player.seekBy(AppSettings.current.seekStepSeconds.toDouble()); true }
                    Key.DirectionLeft -> { player.seekBy(-AppSettings.current.seekStepSeconds.toDouble()); true }
                    else -> false
                }
            }
            // Swallow clicks so nothing underneath reacts.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Watching: nothing is drawn behind the picture. The artwork background is a canvas redrawn
        // frame by frame under a 96dp blur, and every one of those frames would be spent underneath an
        // opaque picture, taking the graphics card away from the thing actually being watched.
        if (videoMode) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            PlayerBackground(song.thumbnail, colors, settings, paused = !active)
        }

        Column(Modifier.fillMaxSize().padding(top = LocalTitleBarHeight.current)) {
            PlayerTopBar(
                panel = panel,
                fullLyrics = fullLyrics && panel == PlayerPanel.Lyrics,
                video = videoMode,
                onClose = onClose,
                onToggle = ::toggle,
                onToggleFull = ::toggleFull,
                onMore = { menuOpen = true },
                onLeaveVideo = { player.setVideoMode(false) },
                modifier = dismissGesture.reveal(0.35f, (-10).dp),
            )

            // Watching takes the whole player: the picture, and the chrome that floats over it.
            if (videoMode) {
                VideoStage(state, player, Modifier.weight(1f).fillMaxWidth())
            } else {
            AnimatedContent(
                targetState = fullLyrics && panel == PlayerPanel.Lyrics,
                transitionSpec = {
                    (fadeIn(tween(340)) + scaleIn(spring(dampingRatio = 0.86f, stiffness = 240f), initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 1.03f))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "lyrics_full",
            ) { full ->
                if (full) {
                    FullScreenLyrics(
                        state = state,
                        player = player,
                        lyrics = lyrics,
                        position = smoothPosition,
                        lyricOffset = lyricOffset,
                        nudged = songNudge != 0L,
                        onNudge = ::nudge,
                        onResetNudge = { LyricsSync.reset(song.id) },
                        onArtworkBounds = onArtworkBounds,
                        artworkAlpha = artworkAlpha,
                        onExit = { fullLyrics = false },
                    )
                } else {
                    SplitPlayer(
                        state = state,
                        player = player,
                        panel = panel,
                        lyrics = lyrics,
                        position = smoothPosition,
                        lyricOffset = lyricOffset,
                        nudged = songNudge != 0L,
                        onNudge = ::nudge,
                        onResetNudge = { LyricsSync.reset(song.id) },
                        lyricsLeft = settings.lyricsSide == "LEFT",
                        dismissGesture = dismissGesture,
                        onArtworkBounds = onArtworkBounds,
                        artworkAlpha = artworkAlpha,
                    )
                }
            }
            }
        }

        AnimatedVisibility(visible = menuOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { menuOpen = false } })
        }
        AnimatedVisibility(
            visible = menuOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp + LocalTitleBarHeight.current, end = 28.dp),
            enter = fadeIn(tween(160)) + scaleIn(spring(0.72f, 420f), initialScale = 0.86f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            PlayerMenu(song, state, player, onDismiss = { menuOpen = false })
        }
        AnimatedVisibility(
            visible = syncShownAt > 0L,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 84.dp + LocalTitleBarHeight.current),
            enter = fadeIn(tween(140)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -it / 2 },
            exit = fadeOut(tween(220)),
        ) {
            SyncToast(lyricOffset)
        }
    }
    }
}

/**
 * The player with a side panel. The panel opens on whichever side Settings → Lyrics → Lyrics
 * position says, sliding in from that edge while the now-playing column makes room.
 */
@Composable
private fun SplitPlayer(
    state: PlayerState,
    player: DesktopPlayer,
    panel: PlayerPanel,
    lyrics: Lyrics?,
    position: State<Long>,
    lyricOffset: Long,
    nudged: Boolean,
    onNudge: (Long) -> Unit,
    onResetNudge: () -> Unit,
    lyricsLeft: Boolean,
    dismissGesture: Modifier,
    onArtworkBounds: (Rect) -> Unit,
    artworkAlpha: () -> Float,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, bottom = 36.dp)) {
        val panelOpen = panel != PlayerPanel.None
        val panelFraction by animateFloatAsState(
            if (panelOpen) 1f else 0f,
            spring(dampingRatio = 0.86f, stiffness = 240f),
            label = "player_panel",
        )
        var shownPanel by remember { mutableStateOf(PlayerPanel.Lyrics) }
        if (panelOpen && shownPanel != panel) shownPanel = panel

        val columnWidth = (maxWidth * (1f / (1f + 1.15f * panelFraction)) - 24.dp).coerceIn(300.dp, 520.dp)
        val art = minOf(columnWidth, maxHeight - 290.dp).coerceIn(160.dp, 520.dp)

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (lyricsLeft) PanelSlot(panelFraction, shownPanel, state, player, lyrics, position, lyricOffset, nudged, onNudge, onResetNudge, leading = true)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                NowPlayingColumn(state, player, art, columnWidth, dismissGesture, onArtworkBounds, artworkAlpha)
            }
            if (!lyricsLeft) PanelSlot(panelFraction, shownPanel, state, player, lyrics, position, lyricOffset, nudged, onNudge, onResetNudge, leading = false)
        }
    }
}

@Composable
private fun RowScope.PanelSlot(
    panelFraction: Float,
    shownPanel: PlayerPanel,
    state: PlayerState,
    player: DesktopPlayer,
    lyrics: Lyrics?,
    position: State<Long>,
    lyricOffset: Long,
    nudged: Boolean,
    onNudge: (Long) -> Unit,
    onResetNudge: () -> Unit,
    leading: Boolean,
) {
    if (panelFraction <= 0.01f) return
    if (!leading) Spacer(Modifier.width(48.dp * panelFraction))
    Box(
        Modifier
            .weight((1.15f * panelFraction).coerceAtLeast(0.0001f))
            .fillMaxHeight()
            .reveal(0.5f, 16.dp)
            .graphicsLayer {
                alpha = panelFraction.coerceIn(0f, 1f)
                translationX = (1f - panelFraction) * 64.dp.toPx() * if (leading) -1f else 1f
            },
    ) {
        AnimatedContent(
            targetState = shownPanel,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(spring(0.85f, 380f)) { it / 20 }) togetherWith fadeOut(tween(120))
            },
            label = "panel_content",
        ) { shown ->
            when (shown) {
                PlayerPanel.Queue -> QueuePanel(state, player, Modifier.fillMaxSize())
                else -> LyricsPanel(
                    lyrics = lyrics,
                    position = position,
                    onSeek = player::seekTo,
                    modifier = Modifier.fillMaxSize(),
                    offsetMs = lyricOffset,
                    nudged = nudged,
                    onNudge = onNudge,
                    onResetNudge = onResetNudge,
                )
            }
        }
    }
    if (leading) Spacer(Modifier.width(48.dp * panelFraction))
}

/**
 * Lyrics as the whole player: large type across the width of the window, with what's playing
 * reduced to a glass bar along the bottom — cover, title, progress and transport.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenLyrics(
    state: PlayerState,
    player: DesktopPlayer,
    lyrics: Lyrics?,
    position: State<Long>,
    lyricOffset: Long,
    nudged: Boolean,
    onNudge: (Long) -> Unit,
    onResetNudge: () -> Unit,
    onArtworkBounds: (Rect) -> Unit,
    artworkAlpha: () -> Float,
    onExit: () -> Unit,
) {
    val song = state.current ?: return
    val duration = state.durationSeconds.coerceAtLeast(0.0)
    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth().reveal(0.4f, 24.dp), contentAlignment = Alignment.TopCenter) {
            LyricsPanel(
                lyrics = lyrics,
                position = position,
                onSeek = player::seekTo,
                modifier = Modifier.widthIn(max = 1240.dp).fillMaxSize(),
                scale = 1.5f,
                anchor = 0.34f,
                offsetMs = lyricOffset,
                nudged = nudged,
                onNudge = onNudge,
                onResetNudge = onResetNudge,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .reveal(0.55f, 24.dp)
                .widthIn(max = 1240.dp)
                .fillMaxWidth()
                .height(84.dp)
                .glassPlate(RoundedCornerShape(42.dp), base = Color.White.copy(alpha = 0.04f))
                .padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .onGloballyPositioned { onArtworkBounds(it.boundsInRoot()) }
                    .graphicsLayer { alpha = artworkAlpha() }
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                AsyncImage(song.thumbnail.resizedThumbnail(1200), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.width(220.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.playerMarquee())
                Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(18.dp))
            BarProgress(player, state, duration)
            Spacer(Modifier.width(14.dp))
            GlyphButton(48.dp, onClick = player::previous, nudge = -1f) {
                Icon(TransportIcons.Previous, "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            GlyphButton(60.dp, onClick = player::togglePlayPause, enabled = !state.isLoading) {
                if (state.isLoading && LocalPlayerActive.current) {
                    LoadingRing(Modifier.size(28.dp), color = Color.White, stroke = 2.5.dp)
                } else {
                    PlayPauseIcon(state.isPlaying, Color.White, Modifier.size(40.dp))
                }
            }
            GlyphButton(48.dp, onClick = player::next, enabled = state.hasNext, nudge = 1f) {
                Icon(TransportIcons.Next, "Next", tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.35f), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(10.dp))
            PlayerCircleButton(NavIcons.FullscreenExit, "Exit full-screen lyrics", active = true, onClick = onExit)
        }
    }
}

@Composable
private fun SyncToast(offsetMs: Long) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color(0xE6141116))
            .border(0.7.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(syncLabel(offsetMs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun PlayerBackground(thumbnail: String, colors: List<Color>, settings: Settings, paused: Boolean) {
    val style = settings.playerBackground
    val base = colors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val fluid = style == "ARTWORK" && settings.animatedBackground && fluidBackgroundAvailable
    when {
        fluid -> FluidArtwork(thumbnail.resizedThumbnail(120), Modifier.fillMaxSize(), brightness = 0.95f, paused = paused)
        style == "ARTWORK" || style == "BLUR" -> Crossfade(targetState = thumbnail, animationSpec = tween(700), modifier = Modifier.fillMaxSize(), label = "backdrop") { url ->
            AsyncImage(
                model = url.resizedThumbnail(544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.85f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
                    .blur(96.dp, BlurredEdgeTreatment.Rectangle),
            )
        }
        style == "GRADIENT" -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(if (colors.size >= 2) colors else listOf(base, MaterialTheme.colorScheme.surface))))
        style == "SOLID" -> Box(Modifier.fillMaxSize().background(base))
    }
    if (style == "GLOW" || style == "GRADIENT") AmbientGlow(colors, Modifier.fillMaxSize(), intensity = if (style == "GLOW") 1.3f else 0.6f, paused = paused)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.5f)))))
}

@Composable
private fun PlayerTopBar(
    panel: PlayerPanel,
    fullLyrics: Boolean,
    video: Boolean,
    onClose: () -> Unit,
    onToggle: (PlayerPanel) -> Unit,
    onToggleFull: () -> Unit,
    onMore: () -> Unit,
    onLeaveVideo: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().height(if (video) 46.dp else 76.dp).padding(horizontal = 28.dp)) {
        // The grab handle: the whole bar is draggable, this just says so.
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)))
        PlayerCircleButton(TransportIcons.Collapse, "Close player", active = false, modifier = Modifier.align(Alignment.CenterStart), onClick = onClose)
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (video) {
                // Lyrics and the queue open beside the artwork, and there is no artwork here — the
                // picture has the room, and it is a window of its own that nothing can be drawn over.
                // What belongs to watching lives on the picture itself; what is left is the way back.
                PlayerCircleButton(NavIcons.Image, "Back to the artwork", active = true, onClick = onLeaveVideo)
            } else {
                PlayerCircleButton(TransportIcons.Lyrics, "Lyrics", active = panel == PlayerPanel.Lyrics) { onToggle(PlayerPanel.Lyrics) }
                PlayerCircleButton(if (fullLyrics) NavIcons.FullscreenExit else NavIcons.Fullscreen, "Full-screen lyrics", active = fullLyrics, onClick = onToggleFull)
                PlayerCircleButton(TransportIcons.Queue, "Up next", active = panel == PlayerPanel.Queue) { onToggle(PlayerPanel.Queue) }
                PlayerCircleButton(NavIcons.More, "More", active = false, onClick = onMore)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingColumn(
    state: PlayerState,
    player: DesktopPlayer,
    art: Dp,
    width: Dp,
    dismissGesture: Modifier,
    onArtworkBounds: (Rect) -> Unit,
    artworkAlpha: () -> Float,
) {
    val song = state.current ?: return
    val settings = LocalSettings.current
    val videoMode by player.videoMode.collectAsState()
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(song, art, playing = state.isPlaying || state.isLoading, modifier = dismissGesture, onBounds = onArtworkBounds, alpha = artworkAlpha)

        // Only for a song YouTube actually has a video of; for everything else there's nothing to switch to.
        val videoAvailable by player.videoAvailable.collectAsState()
        if (videoAvailable || videoMode) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { player.setVideoMode(!videoMode) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (videoMode) NavIcons.New else NavIcons.Image,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (videoMode) "Switch to audio" else "Switch to video",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(30.dp))

        AnimatedContent(
            targetState = song,
            contentKey = { it.id },
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(spring(0.8f, 320f)) { it / 3 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            modifier = Modifier.reveal(0.45f, 18.dp),
            label = "player_title",
        ) { current ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.02).em),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.playerMarquee(),
                )
                // Artists and the album are links to their pages; the player folds away as one opens.
                val nav = LocalNavigator.current
                Text(
                    remember(current, nav) {
                        buildAnnotatedString {
                            val hover = TextLinkStyles(hoveredStyle = SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline))
                            current.artists.forEachIndexed { i, artist ->
                                if (i > 0) append(", ")
                                val id = artist.id
                                if (id != null && nav != null) {
                                    withLink(LinkAnnotation.Clickable("artist:$id", hover) { nav.openArtist(id, artist.name, null) }) { append(artist.name) }
                                } else {
                                    append(artist.name)
                                }
                            }
                            current.album?.let { album ->
                                if (current.artists.isNotEmpty()) append(" — ")
                                if (nav != null) {
                                    withLink(LinkAnnotation.Clickable("album:${album.id}", hover) { nav.openAlbum(album.id, album.name, current.thumbnail) }) { append(album.name) }
                                } else {
                                    append(album.name)
                                }
                            }
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.reveal(0.5f, 20.dp)) { ProgressSection(state, player, settings) }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.reveal(0.56f, 22.dp)) { TransportRow(state, player) }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.reveal(0.62f, 22.dp)) { VolumeRow(state, player) }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
        }
    }
}

/** The cover. The cue for a paused song: it sits back, and its shadow comes in close. */
@Composable
private fun Artwork(song: SongItem, size: Dp, playing: Boolean, modifier: Modifier, onBounds: (Rect) -> Unit, alpha: () -> Float) {
    val thumbnail = song.thumbnail
    val rest by animateFloatAsState(if (playing) 1f else 0.82f, spring(dampingRatio = 0.62f, stiffness = 180f), label = "art_rest")
    val elevation by animateDpAsState(if (playing) 48.dp else 14.dp, spring(0.8f, 200f), label = "art_shadow")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = rest
                scaleY = rest
            }
            // After the scale, so the flight into the player lands on the cover as drawn.
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .graphicsLayer { this.alpha = alpha() }
            .reveal(0.3f, 28.dp)
            .shadow(elevation, shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        Crossfade(targetState = thumbnail, animationSpec = tween(450), label = "artwork") { url ->
            AsyncImage(url.resizedThumbnail(1200), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (LocalSettings.current.motionCovers) {
            // Keyed on the song, so the next one starts from its still cover and fades its own in.
            key(song.id) { MotionCoverLayer(song, playing, Modifier.fillMaxSize()) }
        }
    }
}

/**
 * The song's moving cover over its still one, as the Android player shows it: looked up once, faded
 * in on its first frame, paused with the song. Nothing at all for a song that hasn't one.
 */
@Composable
private fun MotionCoverLayer(song: SongItem, playing: Boolean, modifier: Modifier) {
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { url = runCatching { MotionCovers.find(song) }.getOrNull() }
    val source = url ?: return
    val renderer = remember(source) { runCatching { MotionCoverRenderer(720) }.getOrNull() } ?: return
    DisposableEffect(renderer) {
        renderer.play(source)
        // Closing waits for mpv to wind down; not on the thread that draws the window.
        onDispose { Thread { runCatching { renderer.close() } }.apply { isDaemon = true }.start() }
    }
    LaunchedEffect(renderer, playing) { renderer.setPaused(!playing) }
    val started by remember(renderer) { derivedStateOf { renderer.frames.intValue > 0 } }
    val shown by animateFloatAsState(if (started) 1f else 0f, tween(700), label = "motion_cover")
    if (shown <= 0f) return
    Canvas(modifier.graphicsLayer { alpha = shown }) {
        // Read so every new frame redraws this, and only this.
        renderer.frames.intValue
        val frame = renderer.current ?: return@Canvas
        drawImage(frame, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High)
    }
}

@Composable
private fun ProgressSection(state: PlayerState, player: DesktopPlayer, settings: Settings) {
    val duration = state.durationSeconds.coerceAtLeast(0.0)
    var preview by remember { mutableStateOf<Float?>(null) }
    val position by player.rememberPosition(LocalPlayerActive.current)
    val fraction = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    val shown = preview?.let { it * duration } ?: position.coerceIn(0.0, duration.takeIf { it > 0 } ?: Double.MAX_VALUE)

    GlassTrack(
        value = preview ?: fraction,
        onChange = { preview = it },
        onCommit = {
            preview = null
            player.seekTo(it * duration)
        },
        enabled = !state.isLoading && duration > 0,
        restHeight = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { QualityBadge(state.streamInfo, settings) }
        Text("-" + formatTime(duration - shown), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
    }
}

/** The full-screen lyrics bar's times and track. It follows the position itself, so only it updates as the song plays. */
@Composable
private fun RowScope.BarProgress(player: DesktopPlayer, state: PlayerState, duration: Double) {
    val position by player.rememberPosition(LocalPlayerActive.current)
    val elapsed = position.coerceIn(0.0, duration.takeIf { it > 0 } ?: Double.MAX_VALUE)
    Text(formatTime(elapsed), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
    GlassTrack(
        value = if (duration > 0) (elapsed / duration).toFloat() else 0f,
        onCommit = { player.seekTo(it * duration) },
        enabled = !state.isLoading && duration > 0,
        restHeight = 5.dp,
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
    )
    Text("-" + formatTime(duration - elapsed), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
}

/** "Spatial Audio · 7.1" once Windows has opened a surround bed for Exhale; otherwise the codec. */
@Composable
private fun QualityBadge(streamInfo: String?, settings: Settings) {
    val output by AudioOutputStatus.state.collectAsState()
    val spatial = settings.spatialAudio != "OFF" && output?.isSurround == true
    val label = if (spatial) "Spatial Audio · ${output?.layout}" else streamInfo ?: return
    AnimatedContent(
        targetState = spatial to label,
        transitionSpec = { (fadeIn(tween(260)) + scaleIn(spring(0.7f, 400f), initialScale = 0.85f)) togetherWith fadeOut(tween(140)) },
        label = "quality_badge",
    ) { (isSpatial, text) ->
        Row(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = if (isSpatial) 0.16f else 0.09f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSpatial) {
                Icon(NavIcons.Surround, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

@Composable
private fun TransportRow(state: PlayerState, player: DesktopPlayer) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ToggleGlyph(TransportIcons.Shuffle, "Shuffle", state.shuffle, player::toggleShuffle)
        Spacer(Modifier.weight(1f))
        GlyphButton(62.dp, onClick = player::previous, nudge = -1f) {
            Icon(TransportIcons.Previous, "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(18.dp))
        GlyphButton(84.dp, onClick = player::togglePlayPause, enabled = !state.isLoading) {
            if (state.isLoading && LocalPlayerActive.current) {
                LoadingRing(Modifier.size(40.dp), color = Color.White, stroke = 3.dp)
            } else {
                PlayPauseIcon(state.isPlaying, Color.White, Modifier.size(58.dp))
            }
        }
        Spacer(Modifier.width(18.dp))
        GlyphButton(62.dp, onClick = player::next, enabled = state.hasNext, nudge = 1f) {
            Icon(TransportIcons.Next, "Next", tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.35f), modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.weight(1f))
        ToggleGlyph(
            if (state.repeat == RepeatMode.ONE) TransportIcons.RepeatOne else TransportIcons.Repeat,
            "Repeat",
            state.repeat != RepeatMode.OFF,
            player::cycleRepeat,
        )
    }
}

@Composable
private fun VolumeRow(state: PlayerState, player: DesktopPlayer) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (state.volume == 0) TransportIcons.VolumeOff else TransportIcons.VolumeDown,
            contentDescription = "Mute",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = player::toggleMute),
        )
        GlassTrack(
            value = state.volume / 100f,
            onChange = { player.setVolume((it * 100).roundToInt()) },
            onCommit = { player.setVolume((it * 100).roundToInt()) },
            restHeight = 5.dp,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Icon(TransportIcons.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

/**
 * A bare white glyph, the way Apple draws transport controls: no plate at rest; a soft disc blooms
 * behind it under the pointer, it dips when pressed, and skip glyphs nudge the way they point.
 */
@Composable
private fun GlyphButton(size: Dp, onClick: () -> Unit, enabled: Boolean = true, nudge: Float = 0f, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val disc by animateFloatAsState(if (pressed) 1f else if (hovered && enabled) 0.6f else 0f, spring(0.8f, 600f), label = "glyph_disc")
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "glyph_scale")
    val shift = remember { Animatable(0f) }
    Box(
        Modifier
            .size(size)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                if (nudge != 0f) {
                    scope.launch {
                        shift.animateTo(1f, tween(90))
                        shift.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                    }
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = disc
                    val s = 0.7f + 0.3f * disc
                    scaleX = s
                    scaleY = s
                }
                .background(Color.White.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shift.value * nudge * 7.dp.toPx()
            },
        ) { content() }
    }
}

@Composable
private fun ToggleGlyph(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.5f, 700f), label = "toggle_scale")
    val fill by animateColorAsState(if (active) Color.White.copy(alpha = 0.2f) else Color.Transparent, label = "toggle_fill")
    val tint by animateColorAsState(if (active) Color.White else Color.White.copy(alpha = 0.55f), label = "toggle_tint")
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * A frosted pill that fills with light. It thickens under the pointer and more while dragged;
 * [onChange] follows the drag, [onCommit] lands it.
 */
@Composable
fun GlassTrack(
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChange: ((Float) -> Unit)? = null,
    enabled: Boolean = true,
    restHeight: Dp = 7.dp,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val height by animateDpAsState(
        when {
            drag != null -> restHeight * 2f
            hovered && enabled -> restHeight * 1.5f
            else -> restHeight
        },
        spring(0.6f, 500f),
        label = "track_height",
    )
    val fraction = (drag ?: value).coerceIn(0f, 1f)
    val latestChange by rememberUpdatedState(onChange)
    val latestCommit by rememberUpdatedState(onCommit)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .height(26.dp)
            .hoverable(interaction)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures {
                    val f = (it.x / size.width).coerceIn(0f, 1f)
                    latestChange?.invoke(f)
                    latestCommit(f)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        val f = (it.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        latestChange?.invoke(f)
                    },
                    onDragEnd = {
                        drag?.let { latestCommit(it) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag = f
                    latestChange?.invoke(f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(shape).background(Color.White.copy(alpha = 0.18f)).border(0.5.dp, Color.White.copy(alpha = 0.10f), shape))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.95f)))),
        )
    }
}

@Composable
private fun PlayerCircleButton(icon: ImageVector, description: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else if (hovered) 1.05f else 1f, spring(0.55f, 600f), label = "player_button")
    val fill by animateColorAsState(if (active) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.04f), label = "player_button_fill")
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassPlate(CircleShape, base = fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun QueuePanel(state: PlayerState, player: DesktopPlayer, modifier: Modifier) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.index - 1).coerceAtLeast(0))
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text("Up Next", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("${state.queue.size} songs", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            // Only worth offering while something is actually waiting behind this song.
            if (state.index >= 0 && state.index < state.queue.size - 1) {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { player.clearUpNext() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).edgeFade()) {
            itemsIndexed(state.queue, key = { i, song -> "$i:${song.id}" }) { i, song ->
                QueueRow(
                    song,
                    current = i == state.index,
                    past = i < state.index,
                    playing = state.isPlaying,
                    onRemove = { player.removeFromQueue(i) },
                    onClick = { player.skipTo(i) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(song: SongItem, current: Boolean, past: Boolean, playing: Boolean, onRemove: () -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (current) Color.White.copy(alpha = 0.14f) else if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .graphicsLayer { alpha = if (past) 0.5f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            AsyncImage(song.thumbnail.resizedThumbnail(226), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (current) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    if (playing) EqualizerBars(Color.White, Modifier.size(18.dp))
                    else Icon(TransportIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        song.duration?.let {
            Text(formatTime(it.toDouble()), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        }
        // Takes this song out of the queue; the one playing hands over to whatever follows it.
        Box(Modifier.size(32.dp).padding(start = 6.dp), contentAlignment = Alignment.Center) {
            if (hovered) {
                Icon(
                    NavIcons.Delete,
                    contentDescription = "Remove from queue",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRemove),
                )
            }
        }
    }
}

/** The player menu, as a card off the More button. */
@Composable
internal fun PlayerMenu(song: SongItem, state: PlayerState, player: DesktopPlayer, onDismiss: () -> Unit) {
    val settings = LocalSettings.current
    val sleepAt by player.sleepTimerEndsAt.collectAsState()
    val sleepAtEndOfSong by player.sleepAtEndOfSong.collectAsState()
    val now by produceState(System.currentTimeMillis(), sleepAt) {
        while (sleepAt != null) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val link = "https://music.youtube.com/watch?v=${song.id}"

    val colors by rememberArtworkColors(song.thumbnail.resizedThumbnail(120))
    val wash = (colors.firstOrNull() ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.34f)
    val nav = LocalNavigator.current
    val linkedArtist = song.artists.firstOrNull { it.id != null }

    Column(
        Modifier
            .width(384.dp)
            .heightIn(max = 700.dp)
            .glassPlate(RoundedCornerShape(30.dp), base = Color(0xF2151218))
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState()),
    ) {
        // The cover's own colour behind what's playing, so the sheet belongs to this song.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(wash, Color.Transparent)))
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                song.thumbnail.resizedThumbnail(226),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(58.dp).shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.streamInfo?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // The four things anyone actually opens this sheet for.
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile(NavIcons.Radio, "Radio") {
                player.startRadio(song)
                onDismiss()
            }
            QuickTile(NavIcons.Link, "Copy link") {
                runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null) }
                onDismiss()
            }
            QuickTile(NavIcons.OpenInNew, "YT Music") {
                runCatching { Desktop.getDesktop().browse(URI(link)) }
                onDismiss()
            }
            QuickTile(NavIcons.Headphones, if (DolbyAccess.isInstalled) "Dolby" else "Get Dolby") {
                DolbyAccess.open()
                onDismiss()
            }
        }

        // Only offered when YouTube actually has a video of this — an official video, a live take.
        val videoAvailable by player.videoAvailable.collectAsState()
        val watching by player.videoMode.collectAsState()
        if (videoAvailable || watching) {
            Spacer(Modifier.height(10.dp))
            MenuRow(
                if (watching) NavIcons.New else NavIcons.Image,
                if (watching) "Switch to audio" else "Switch to video",
            ) {
                player.setVideoMode(!watching)
                onDismiss()
            }
        }

        if (nav != null && (linkedArtist != null || song.album != null)) {
            Spacer(Modifier.height(10.dp))
            linkedArtist?.let { artist ->
                MenuRow(NavIcons.Artists, "Go to ${artist.name}") {
                    nav.openArtist(artist.id!!, artist.name, null)
                    onDismiss()
                }
            }
            song.album?.let { album ->
                MenuRow(NavIcons.Library, "Go to ${album.name}") {
                    nav.openAlbum(album.id, album.name, song.thumbnail)
                    onDismiss()
                }
            }
        }

        val remaining = sleepAt?.let { ((it - now) / 1000).coerceAtLeast(0) }
        MenuCard(
            NavIcons.Moon,
            "Sleep timer",
            when {
                sleepAtEndOfSong -> "End of song"
                remaining != null -> formatTime(remaining.toDouble())
                else -> "Off"
            },
        ) {
            val minutes = listOf(15, 30, 45, 60)
            val selected = when {
                sleepAtEndOfSong -> minutes.size + 1
                sleepAt != null -> -1
                else -> 0
            }
            Segmented(listOf("Off") + minutes.map { "$it" } + "Song", selected) { index ->
                when (index) {
                    0 -> player.setSleepTimer(null)
                    minutes.size + 1 -> player.setSleepTimer(0)
                    else -> player.setSleepTimer(minutes[index - 1])
                }
            }
        }

        MenuCard(
            NavIcons.Surround,
            "Spatial audio",
            when (settings.spatialAudio) {
                "SURROUND_71" -> "7.1"
                "SURROUND_51" -> "5.1"
                else -> "Off"
            },
        ) {
            val modes = listOf("OFF" to "Off", "SURROUND_51" to "5.1", "SURROUND_71" to "7.1")
            Segmented(modes.map { it.second }, modes.indexOfFirst { it.first == settings.spatialAudio }) { index ->
                AppSettings.update { it.copy(spatialAudio = modes[index].first) }
            }
        }

        // One tap to a different sound, as on Android: the record-speed presets, with 8D beside them.
        val presets = com.ozyern.exhale.desktop.player.AudioEffects.SoundPresets
        MenuCard(
            TransportIcons.Equalizer,
            "Sound",
            listOfNotNull(
                presets.firstOrNull { it.first == settings.soundPreset }?.second?.takeIf { settings.soundPreset != "NORMAL" },
                "8D".takeIf { settings.eightD },
            ).joinToString(" · ").ifEmpty { "Normal" },
        ) {
            Segmented(presets.map { it.second }, presets.indexOfFirst { it.first == settings.soundPreset }) { index ->
                AppSettings.update { it.copy(soundPreset = presets[index].first) }
            }
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("8D off", "8D on"), if (settings.eightD) 1 else 0) { index ->
                AppSettings.update { it.copy(eightD = index == 1) }
            }
        }

        MenuCard(NavIcons.Speed, "Speed", "${settings.playbackSpeed}×") {
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            Segmented(speeds.map { if (it == 1f) "1×" else "$it" }, speeds.indexOfFirst { it == settings.playbackSpeed }) { index ->
                AppSettings.update { it.copy(playbackSpeed = speeds[index]) }
            }
        }

        MenuCard(TransportIcons.VolumeUp, "Volume", "${state.volume}%") {
            GlassTrack(
                value = state.volume / 100f,
                onChange = { player.setVolume((it * 100).roundToInt()) },
                onCommit = { player.setVolume((it * 100).roundToInt()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One of the four tiles across the top of the sheet. */
@Composable
private fun RowScope.QuickTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.94f
            hovered -> 1.03f
            else -> 1f
        },
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "quick_tile",
    )
    val fill by animateColorAsState(if (hovered) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f), tween(160), label = "quick_tile_fill")
    Column(
        Modifier
            .weight(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A titled group: the setting's name and where it stands, with its control under it. */
@Composable
private fun MenuCard(icon: ImageVector, label: String, value: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "menu_card_value",
            ) { shown ->
                Text(shown, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
        content()
    }
}

/**
 * A segmented control: the selected pill slides to whatever was picked rather than the colour simply
 * jumping between chips. [selected] of -1 means none of them — a sleep timer counting down, say.
 */
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // The Android app's liquid glass segmented control: click a segment, or drag the pill along.
    com.ozyern.exhale.desktop.ui.glass.LiquidSegmented(labels, selected, onSelect, accent = MaterialTheme.colorScheme.primary, fill = true)
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

