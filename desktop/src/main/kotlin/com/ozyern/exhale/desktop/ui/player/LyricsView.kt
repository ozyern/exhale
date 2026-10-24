/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.lyrics.LyricLine
import com.ozyern.exhale.desktop.lyrics.LyricWord
import com.ozyern.exhale.desktop.lyrics.Lyrics
import com.ozyern.exhale.desktop.lyrics.LyricsParser
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.NavIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far ahead of the audio the highlight runs, to cover the time it takes to draw and be seen. */
private const val WordLeadMs = 120L

/** Text size multiplier for the lyrics under it — full-screen lyrics set it large. */
private val LocalLyricsScale = compositionLocalOf { 1f }

internal fun syncLabel(ms: Long): String = when {
    ms == 0L -> "Lyrics in sync"
    ms > 0 -> "Lyrics $ms ms earlier"
    else -> "Lyrics ${-ms} ms later"
}

/**
 * Playback position at frame resolution, as a clock rather than a staircase. mpv reports time-pos
 * unevenly; between reports the clock runs on at the playback speed, and each report steers it
 * gently instead of yanking it — so the word sweep never stutters, and never steps backwards over
 * a few milliseconds of report jitter. A seek, pause or track change still lands immediately.
 */
@Composable
fun rememberSmoothPositionMs(state: PlayerState, playerPosition: StateFlow<Double>, active: Boolean = true): State<Long> {
    val isActive by rememberUpdatedState(active)
    val position = remember { mutableLongStateOf((playerPosition.value * 1000).toLong()) }
    // Read straight from the flow inside the frame loop: the clock follows it without recomposing anything.
    val source by rememberUpdatedState(playerPosition)
    val running by rememberUpdatedState(state.isPlaying && !state.isLoading)
    val speed by rememberUpdatedState(com.ozyern.exhale.desktop.player.AudioEffects.effectiveSpeed(LocalSettings.current).toDouble())
    LaunchedEffect(Unit) {
        var clock = position.longValue.toDouble()
        var shown = clock
        var lastFrame = -1L
        var lastReported = Double.NaN
        while (true) {
            // No lyrics on screen: follow the song at the position's own pace instead of every frame.
            if (!isActive) {
                clock = source.value * 1000.0
                shown = clock
                position.longValue = shown.toLong()
                lastFrame = -1L
                delay(200)
                continue
            }
            withFrameNanos { now ->
                val reported = source.value
                val dt = if (lastFrame < 0) 0.0 else (now - lastFrame) / 1_000_000.0
                lastFrame = now
                val target = reported * 1000.0
                if (running) clock += dt.coerceAtMost(250.0) * speed
                if (reported != lastReported) {
                    lastReported = reported
                    val error = target - clock
                    if (!running || abs(error) > 400) clock = target else clock += error * 0.25
                } else if (!running) {
                    clock = target
                }
                // Hold small backward corrections instead of showing them.
                shown = if (running && clock < shown && shown - clock < 150) shown else clock
                position.longValue = shown.toLong()
            }
            if (!running) {
                lastFrame = -1L
                delay(90)
            }
        }
    }
    return position
}

/** Fades a list out at its top and bottom edges instead of cutting it off. */
internal fun Modifier.edgeFade(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun lyricStyle(
    fontSize: Float = LocalSettings.current.lyricsTextSize * LocalLyricsScale.current,
): TextStyle = MaterialTheme.typography.headlineMedium.copy(
    fontSize = fontSize.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = (fontSize * 1.3f).sp,
    letterSpacing = (-0.015).em,
)

/**
 * Lyrics for the player. [offsetMs] shifts them against the audio (Settings → Lyrics → Sync offset
 * plus this song's nudges); [onNudge] shows a Sync chip under the pointer to nudge by hand.
 * [scale] and [anchor] (where the sung line sits, as a fraction of the height) are for full-screen lyrics.
 */
@Composable
internal fun LyricsPanel(
    lyrics: Lyrics?,
    position: State<Long>,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    anchor: Float = 0.26f,
    offsetMs: Long = 0L,
    nudged: Boolean = false,
    onNudge: ((Long) -> Unit)? = null,
    onResetNudge: (() -> Unit)? = null,
) {
    val latestOffset by rememberUpdatedState(offsetMs)
    val adjusted = remember(position) { derivedStateOf { position.value + latestOffset } }
    val seek: (Double) -> Unit = { seconds -> onSeek((seconds - offsetMs / 1000.0).coerceAtLeast(0.0)) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    CompositionLocalProvider(LocalLyricsScale provides scale) {
        Box(modifier.hoverable(interaction)) {
            when (lyrics) {
                null -> LyricsSkeleton(Modifier.align(Alignment.CenterStart))
                Lyrics.NotFound -> Text(
                    "No lyrics for this song.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                )
                is Lyrics.Plain -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize().edgeFade(),
                ) {
                    itemsIndexed(lyrics.lines) { _, line ->
                        Text(line, style = lyricStyle(), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 14.dp))
                    }
                    item { SourceLabel("${lyrics.source} · not synced") }
                }
                is Lyrics.Synced -> SyncedLyricsView(lyrics, adjusted, seek, anchor)
            }

            if (onNudge != null && lyrics is Lyrics.Synced) {
                val shown by animateFloatAsState(if (hovered || nudged) 1f else 0f, tween(220), label = "sync_chip")
                if (shown > 0.01f) {
                    SyncChip(
                        offsetMs = offsetMs,
                        nudged = nudged,
                        onNudge = onNudge,
                        onReset = onResetNudge,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).graphicsLayer { alpha = shown },
                    )
                }
            }
        }
    }
}

/** − and + around the current offset: − runs the lyrics later, + earlier. */
@Composable
private fun SyncChip(offsetMs: Long, nudged: Boolean, onNudge: (Long) -> Unit, onReset: (() -> Unit)?, modifier: Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color(0xCC141116))
            .border(0.7.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyncButton("−") { onNudge(-100) }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NavIcons.Sync, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (offsetMs == 0L) "In sync" else if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        SyncButton("+") { onNudge(100) }
        if (nudged && onReset != null) SyncButton("↺", onReset)
    }
}

@Composable
private fun SyncButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (hovered) 0.18f else 0.08f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun SourceLabel(text: String) {
    Text("Lyrics from $text", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 12.dp))
}

/**
 * The lyrics, laid out by hand rather than by a list: every line knows where it should
 * stand relative to the sung one, and springs there on its own — the sung line first, each line
 * under it a beat later, so a line change ripples down the page like a waterfall. Lines away from
 * the sung one soften with distance; pointing at the lyrics brings them all into focus to read.
 * The wheel scrolls freely and the view drifts back to the song after a few quiet seconds.
 * Long instrumental breaks — before the first line or between two — show breathing dots.
 */
@Composable
private fun SyncedLyricsView(lyrics: Lyrics.Synced, position: State<Long>, onSeek: (Double) -> Unit, anchor: Float) {
    val settings = LocalSettings.current
    val centered = settings.lyricsAlign == "CENTER"
    val lines: List<LyricLine> = lyrics.lines
    // Recomposes only when the sung line changes, not every frame.
    val current by remember(lines) { derivedStateOf { LyricsParser.currentIndex(lines, position.value) } }
    val interlude by remember(lines) {
        derivedStateOf {
            val p = position.value
            val index = current
            if (index < 0) {
                val first = lines.firstOrNull() ?: return@derivedStateOf false
                first.timeMs - p > 2_500
            } else {
                val line = lines[index]
                val next = lines.getOrNull(index + 1) ?: return@derivedStateOf false
                val end = line.words.lastOrNull()?.endMs ?: (line.timeMs + 4_000)
                line.text.isNotBlank() && p > end + 600 && next.timeMs - p > 2_000
            }
        }
    }
    val gap = with(LocalDensity.current) { (settings.lyricsLineSpacing + 4).dp.toPx() }

    // Each line's measured height, and the source label's in the last slot.
    val heights = remember(lines) { mutableStateListOf(*Array(lines.size + 1) { 0 }) }
    var userOffset by remember(lines) { mutableFloatStateOf(0f) }
    var scrolls by remember(lines) { mutableIntStateOf(0) }
    val scrollable = rememberScrollableState { delta ->
        userOffset += delta
        scrolls++
        delta
    }
    LaunchedEffect(scrolls) {
        if (scrolls == 0 || !settings.lyricsAutoScroll) return@LaunchedEffect
        // Long enough to read ahead; "Back to the current line" is there for anyone who doesn't want to wait.
        delay(8_000)
        animate(userOffset, 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 90f)) { value, _ -> userOffset = value }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()
    // Scrolled more than a line or so away from the one being sung.
    val awayThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val away by remember(lines, awayThreshold) { derivedStateOf { abs(userOffset) > awayThreshold } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .edgeFade()
            .hoverable(interaction)
            .scrollable(scrollable, Orientation.Vertical),
    ) {
        val anchorPx = constraints.maxHeight * anchor
        val focus = if (settings.lyricsAutoScroll) current.coerceAtLeast(0) else 0
        val ready = heights.all { it > 0 }
        val tops = FloatArray(heights.size)
        var running = 0f
        for (i in heights.indices) {
            tops[i] = running
            running += heights[i] + gap
        }
        val base = anchorPx - tops[focus.coerceIn(0, heights.lastIndex)]

        lines.forEachIndexed { index, line ->
            key(index) {
                val active = index == current
                val past = current >= 0 && index < current
                val dotsBefore = interlude && current < 0 && index == 0
                val dotsAfter = interlude && active
                LyricLineItem(
                    index = index,
                    focus = focus,
                    active = active,
                    target = base + tops[index],
                    ready = ready,
                    scroll = { userOffset },
                    sharp = hovered,
                    centered = centered,
                    onMeasured = { heights[index] = it },
                    onClick = { onSeek(line.timeMs / 1000.0) },
                    viewport = constraints.maxHeight.toFloat(),
                ) {
                    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                        if (dotsBefore) {
                            BreathingDots(true)
                            Spacer(Modifier.height(6.dp))
                        }
                        when {
                            line.text.isBlank() -> BreathingDots(active)
                            settings.lyricsStyle == "KARAOKE" && active && line.words.isNotEmpty() -> WordLine(line.words, position.value + WordLeadMs)
                            else -> Text(
                                line.text,
                                style = lyricStyle(),
                                color = Color.White.copy(alpha = if (active) 1f else if (past) 0.85f else 0.72f),
                                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (dotsAfter) {
                            Spacer(Modifier.height(6.dp))
                            BreathingDots(true)
                        }
                    }
                }
            }
        }
        key("source") {
            LyricLineItem(
                index = lines.size,
                focus = focus,
                active = false,
                target = base + tops[lines.size],
                ready = ready,
                scroll = { userOffset },
                sharp = true,
                centered = centered,
                onMeasured = { heights[lines.size] = it },
                onClick = null,
                viewport = constraints.maxHeight.toFloat(),
            ) { SourceLabel(if (lyrics.wordLevel) "${lyrics.source} · word synced" else lyrics.source) }
        }

        BackToNowButton(
            visible = away && current >= 0,
            // Scrolled back up through earlier lines, the sung one is further down, and the other way round.
            below = userOffset > 0,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        ) {
            scope.launch {
                animate(userOffset, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 240f)) { value, _ -> userOffset = value }
            }
        }
    }
}

/**
 * Appears once the lyrics have been scrolled away from the sung line. One click glides the view back
 * to it, and from there it follows the song again.
 */
@Composable
private fun BackToNowButton(visible: Boolean, below: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 380f), label = "back_to_now")
    if (shown <= 0.01f) return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val grow by animateFloatAsState(if (hovered) 1.05f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "back_to_now_hover")
    Row(
        modifier
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 24.dp.toPx()
                val s = (0.88f + 0.12f * shown) * grow
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Color(0xE6141116))
            .border(0.8.dp, Color.White.copy(alpha = if (hovered) 0.3f else 0.14f), CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (below) "Back to the current line  ↓" else "Back to the current line  ↑",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun LyricLineItem(
    index: Int,
    focus: Int,
    active: Boolean,
    target: Float,
    ready: Boolean,
    scroll: () -> Float,
    sharp: Boolean,
    centered: Boolean,
    onMeasured: (Int) -> Unit,
    onClick: (() -> Unit)?,
    viewport: Float = Float.MAX_VALUE,
    content: @Composable () -> Unit,
) {
    val settings = LocalSettings.current
    val y = remember { Animatable(target) }
    // Until every line has been measured, lines are placed, not animated — no unfolding on arrival.
    val settled = remember { booleanArrayOf(false) }
    LaunchedEffect(target, ready) {
        if (!ready || !settled[0] || settings.reducedMotion) {
            y.snapTo(target)
            if (ready) settled[0] = true
            return@LaunchedEffect
        }
        val lag = (index - focus + 1).coerceIn(0, 12)
        delay(lag * 34L)
        y.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 120f))
    }

    val distance = abs(index - focus)
    val lineAlpha by animateFloatAsState(
        when {
            active -> 1f
            onClick == null -> 0.9f
            distance <= 1 -> 0.62f
            else -> 0.4f
        },
        tween(420),
        label = "lyric_alpha",
    )
    val scale by animateFloatAsState(if (active) 1f else 0.965f, spring(dampingRatio = 0.8f, stiffness = 180f), label = "lyric_scale")
    // Blur only the lines around the sung one. Every blurred line is its own offscreen layer, and blurring
    // a whole song's worth of them was the lag in the first seconds after the player opened; lines further
    // out are faint enough that sharpness doesn't read.
    val blurTarget = if (sharp || active || onClick == null || settings.reducedMotion || distance > 4) 0f else (distance * 1.2f).coerceAtMost(5f)
    val blur by animateFloatAsState(blurTarget, tween(520), label = "lyric_blur")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val clickable = onClick != null && settings.lyricsClickToSeek

    Box(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (y.value + scroll()).roundToInt()) }
            // Lines well outside the view are laid out (their heights place the others) but not drawn.
            .drawWithContent {
                val top = y.value + scroll()
                if (top + size.height < -400f || top > viewport + 400f) return@drawWithContent
                drawContent()
            }
            .onSizeChanged { onMeasured(it.height) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = lineAlpha
                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
            }
            .then(if (blur > 0.15f) Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovered && clickable) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (clickable) Modifier.clickable(interactionSource = interaction, indication = null) { onClick?.invoke() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = if (centered) Alignment.TopCenter else Alignment.TopStart,
    ) { content() }
}

/** Main words on one flowing line, background vocals on a smaller line under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLine(words: List<LyricWord>, positionMs: Long) {
    val main = words.filter { !it.isBackground }
    val background = words.filter { it.isBackground }
    val size = LocalSettings.current.lyricsTextSize * LocalLyricsScale.current
    val arrangement = if (LocalSettings.current.lyricsAlign == "CENTER") Arrangement.Center else Arrangement.Start
    Column {
        if (main.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
                main.forEach { word -> AnimatedWord(word, positionMs, size, isBackground = false) }
            }
        }
        if (background.isNotEmpty()) {
            if (main.isNotEmpty()) Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
                background.forEach { word -> AnimatedWord(word, positionMs, size * 0.65f, isBackground = true) }
            }
        }
    }
}

/**
 * The Android app's AnimatedWordV2: a dim base word with a lit copy revealed by a soft-edged sweep
 * that tracks the word's own timing, plus a swell (lift, scale, glow) shaped like a sung note —
 * fast attack, held sustain, release — rather than a symmetric arch that peaks late.
 */
@Composable
private fun AnimatedWord(word: LyricWord, positionMs: Long, fontSize: Float, isBackground: Boolean) {
    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
    val complete = positionMs >= word.endMs
    val singing = positionMs in word.startMs until word.endMs
    val progress = when {
        complete -> 1f
        positionMs <= word.startMs -> 0f
        else -> ((positionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
    }

    val swell = when {
        progress < 0.18f -> progress / 0.18f
        progress < 0.75f -> 1f - 0.28f * ((progress - 0.18f) / 0.57f)
        else -> 0.72f * (1f - (progress - 0.75f) / 0.25f)
    }.coerceIn(0f, 1f)

    val wordScale = 1f + (if (isBackground) 0.018f else 0.032f) * swell
    val lift by animateFloatAsState(
        targetValue = if (singing) -5.5f * swell else 0f,
        animationSpec = tween(durationMillis = if (singing) 50 else 350, easing = FastOutSlowInEasing),
        label = "word_lift",
    )
    val glow by animateFloatAsState(
        targetValue = if (singing) swell else 0f,
        animationSpec = tween(durationMillis = if (singing) 60 else 200, easing = FastOutSlowInEasing),
        label = "word_glow",
    )

    // Unlit words on the sung line sit brighter than the rest of the page, so the line reads ahead.
    val restingAlpha = if (isBackground) 0.4f else 0.56f
    val style = lyricStyle(fontSize)

    Box(
        Modifier.graphicsLayer {
            translationY = lift * density
            scaleX = wordScale
            scaleY = wordScale
            // Grow up out of the baseline, the way a struck key rises.
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        Text(word.text, style = style, color = Color.White.copy(alpha = restingAlpha))
        if (complete || singing) {
            Text(
                word.text,
                style = style.copy(
                    shadow = if (glow > 0f) Shadow(Color.White.copy(alpha = glow * 0.5f), Offset.Zero, (glow * 14f).coerceAtLeast(1f)) else null,
                ),
                color = Color.White.copy(alpha = if (isBackground) 0.75f else 1f),
                modifier = if (singing) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Feather scales with the word, so short and long words sweep alike.
                            val edge = (size.width * 0.22f).coerceIn(6.dp.toPx(), 22.dp.toPx())
                            val center = (size.width + edge * 2) * progress - edge
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    0.55f to Color.Black.copy(alpha = 0.45f),
                                    1f to Color.Transparent,
                                    startX = center - edge,
                                    endX = center + edge,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            )
        }
    }
}

/** An instrumental break: three dots that breathe while it lasts. */
@Composable
private fun BreathingDots(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "dots")
    val dot = (12 * LocalLyricsScale.current).dp
    Row(Modifier.height(dot * 3.3f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dot * 0.85f)) {
        repeat(3) { i ->
            val pulse by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700, delayMillis = i * 180, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "dot_$i",
            )
            val s = if (active) pulse else 0.8f
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer {
                        scaleX = s
                        scaleY = s
                        alpha = if (active) pulse else 0.5f
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/**
 * Lyrics on their way. Lines of the size and rhythm the real ones will have, lit by a sweep that
 * travels down them — the shape of the answer arriving, rather than a spinner that says only that
 * something, somewhere, is happening.
 */
@Composable
private fun LyricsSkeleton(modifier: Modifier = Modifier) {
    val widths = remember { listOf(0.68f, 0.84f, 0.52f, 0.74f, 0.44f) }
    val size = LocalSettings.current.lyricsTextSize * LocalLyricsScale.current
    val travel = rememberInfiniteTransition(label = "lyrics_wait")
    val sweep by travel.animateFloat(
        initialValue = -1.4f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing)),
        label = "lyrics_sweep",
    )

    Column(
        modifier.padding(start = 14.dp, end = 40.dp),
        verticalArrangement = Arrangement.spacedBy((size * 0.5f).dp),
    ) {
        widths.forEachIndexed { index, fraction ->
            // Each line is a little behind the one above it, so the light reads as one pass down the
            // verse rather than five bars blinking together.
            val lit = (sweep - index * 0.22f).coerceIn(0f, 1f)
            val glow = 1f - kotlin.math.abs(lit - 0.5f) * 2f
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height((size * 0.82f).dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.07f + 0.05f * glow.coerceAtLeast(0f)),
                                Color.White.copy(alpha = 0.12f + 0.16f * glow.coerceAtLeast(0f)),
                                Color.White.copy(alpha = 0.07f + 0.05f * glow.coerceAtLeast(0f)),
                            ),
                        ),
                    ),
            )
        }
    }
}
