/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern) & WTTexe!
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.LyricsClickKey
import com.ozyern.exhale.constants.LyricsLineSpacingKey
import com.ozyern.exhale.constants.LyricsRomanizeJapaneseKey
import com.ozyern.exhale.constants.LyricsRomanizeKoreanKey
import com.ozyern.exhale.constants.LyricsScrollKey
import com.ozyern.exhale.constants.LyricsTextSizeKey
import com.ozyern.exhale.constants.UseSystemFontKey
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils.findCurrentLineIndex
import com.ozyern.exhale.lyrics.LyricsUtils.isChinese
import com.ozyern.exhale.lyrics.LyricsUtils.isJapanese
import com.ozyern.exhale.lyrics.LyricsUtils.isKorean
import com.ozyern.exhale.lyrics.LyricsUtils.isTtml
import com.ozyern.exhale.lyrics.LyricsUtils.parseLyrics
import com.ozyern.exhale.lyrics.LyricsUtils.parseTtml
import com.ozyern.exhale.lyrics.LyricsUtils.romanizeJapanese
import com.ozyern.exhale.lyrics.LyricsUtils.romanizeKorean
import com.ozyern.exhale.lyrics.WordTimestamp
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.ui.utils.smoothFadingEdge
import com.ozyern.exhale.utils.ComposeToImage
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs


// ──────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────

/** Lead time offset for LRC-style line-synced lyrics (ms). */
private const val LRC_LEAD_MS = 300L

/** Lead time offset for TTML word-synced lyrics (ms). */
private const val TTML_LEAD_MS = 0L

/** Seconds to wait before auto-scroll resumes after manual scroll. */
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

/** Apple-Music-style easing for smooth deceleration. */
/**
 * Extra lead on top of the format's own, in milliseconds of track at 1x.
 *
 * The eye needs to arrive at a line slightly before it is sung, not with it — a highlight that
 * lands exactly on the beat reads as late, because reading takes time that the timestamp does not
 * account for.
 */
private const val VISUAL_TUNING_OFFSET_MS = 150L

/**
 * Optical tracking for lyric type.
 *
 * Display-size text set at a body-text default reads loose — letter fitting that is right at 14sp
 * is visibly airy at 28sp, because the tracking a face needs falls as the size rises. Every
 * typographic system that cares (Apple's included) tightens large sizes, and lyrics are the one
 * place in this app running genuinely large type. -2.2% is enough to pull a line together into a
 * single object the eye reads at once, and not so much that the letters start to touch at the
 * largest user size.
 */
// Windows' tracking for Linotte: tight enough to read as a headline, loose enough that the rounded
// letterforms don't touch at lyric sizes.
/** One jump of the lyrics column: how far it went, to which line, and when. */
private class LineShift(val delta: Float, val target: Int, val at: Long)

private val LyricTracking = (-0.015).em

/**
 * How far ahead of the highlight the *scroll* runs, in milliseconds of track.
 *
 * The list used to start moving at the instant a line became active, so the line lit up wherever
 * it happened to be sitting and then travelled to the anchor over the next few hundred
 * milliseconds. You read it in the wrong place and it arrived late — which is the specific way
 * word-perfect sync can still feel out of step with the song.
 *
 * Running the scroll on its own slightly-earlier clock fixes it without touching the highlight:
 * the column is already in motion as the previous line finishes, and the new line is at the
 * anchor about when it lights. This is deliberately separate from the highlight's lead, which
 * compensates for something else entirely (frame latency and the eye reaching the line).
 */
private const val SCROLL_LEAD_MS = 220L

private val V2Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

/** Liquid fill easing: fast attack, very smooth deceleration (Apple Music-like). */
private val LiquidFillEasing = CubicBezierEasing(0.0f, 0.0f, 0.15f, 1.0f)

/** Sentinel entry prepended so auto-scroll has headroom above the first line. */
private val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")


// ──────────────────────────────────────────────────────────────────────
// Main Composable
// ──────────────────────────────────────────────────────────────────────


@SuppressLint("UnusedBoxWithConstraintsScope", "LocalContextGetResourceValueCall",
    "StringFormatInvalid"
)
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsV2(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // ── Preferences ──
    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (useSystemFont) = rememberPreference(UseSystemFontKey, defaultValue = false)
    val lyricsFontFamily = remember(useSystemFont) {
        if (useSystemFont) null else FontFamily(Font(R.font.linotte))
    }
    // ── Text colour ──
    // LyricsV2 only ever renders inside LyricsScreen, whose backdrop is ALWAYS the dark
    // blurred album art (Apple-Music style) — so the text is always white.
    val textColor = Color.White

    val inactiveAlpha = 0.35f

    // ── Selection mode state ──
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 5
    var showProgressDialog by remember { mutableStateOf(false) }
    var showShareCarouselSheet by remember { mutableStateOf(false) }
    var shareDialogData by remember {
        mutableStateOf<Triple<String, String, String>?>(null)
    }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var selectedGlassStyle by remember { mutableStateOf(LyricsGlassStyle.FrostedDark) }
    var paletteGlassStyle by remember { mutableStateOf<LyricsGlassStyle?>(null) }


    var showShareDialog by remember { mutableStateOf(false) }
    // ── Lyrics data ──
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = currentLyrics?.lyrics

    // ── Parse lyrics into entries ──
    val isSynced = remember(lyrics) { lyrics != null && (lyrics!!.startsWith("[") || isTtml(lyrics!!)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && isTtml(lyrics!!) }

    val lyricsEntries: List<LyricsEntry> = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) return@remember emptyList()
        val parsed = when {
            isTtml(lyrics!!) -> parseTtml(lyrics!!)
            lyrics!!.startsWith("[") -> parseLyrics(lyrics!!)
            else -> lyrics!!.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, line ->
                    LyricsEntry(time = -1L, text = line.trim())
                }
        }
        if (parsed.isNotEmpty() && parsed.first().time >= 0) {
            listOf(HEAD_LYRICS_ENTRY) + parsed
        } else {
            parsed
        }
    }

    // ── Synthesize word timings for LRC entries that lack them ──
    val entriesWithWords: List<LyricsEntry> = remember(lyricsEntries) {
        if (lyricsEntries.isEmpty()) return@remember emptyList()
        lyricsEntries.mapIndexed { index, entry ->
            if (entry.words != null || entry.time < 0 || entry.text.isBlank()) {
                entry // Already has word timings (TTML) or is non-synced
            } else {
                // Synthesize word-level timings for this LRC line
                val nextEntryTime = if (index < lyricsEntries.lastIndex) {
                    lyricsEntries[index + 1].time
                } else {
                    entry.time + 5000L // 5s fallback for last line
                }
                val lineDurationMs = (nextEntryTime - entry.time).coerceAtLeast(500L)
                val lineStartSec = entry.time / 1000.0

                val isCjkText = isJapanese(entry.text) || isChinese(entry.text) || isKorean(entry.text)
                val tokens = if (isCjkText) {
                    val chars = mutableListOf<String>()
                    var currentWord = StringBuilder()
                    entry.text.forEach { char ->
                        if (char.isWhitespace()) {
                            if (currentWord.isNotEmpty()) {
                                chars.add(currentWord.toString())
                                currentWord.clear()
                            }
                            chars.add(char.toString())
                        } else if (isJapanese(char.toString()) || isChinese(char.toString()) || isKorean(char.toString())) {
                            if (currentWord.isNotEmpty()) {
                                chars.add(currentWord.toString())
                                currentWord.clear()
                            }
                            chars.add(char.toString())
                        } else {
                            currentWord.append(char)
                        }
                    }
                    if (currentWord.isNotEmpty()) {
                        chars.add(currentWord.toString())
                    }

                    // Group spaces onto the preceding word
                    val groupedTokens = mutableListOf<String>()
                    var tempStr = StringBuilder()
                    chars.forEachIndexed { i, c ->
                        if (c.isBlank()) {
                            if (groupedTokens.isNotEmpty()) {
                                groupedTokens[groupedTokens.lastIndex] = groupedTokens.last() + c
                            }
                        } else {
                            groupedTokens.add(c)
                        }
                    }
                    groupedTokens
                } else {
                    entry.text.split(Regex("\\s+"))
                }
                if (tokens.isEmpty()) return@mapIndexed entry

                // Weight each token by character count for proportional distribution
                val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
                val words = mutableListOf<WordTimestamp>()
                var currentOffsetMs = 0.0

                tokens.forEachIndexed { wordIdx, token ->
                    val weight = token.length.toDouble() / totalChars
                    val wordDurMs = lineDurationMs * weight
                    val wordStartSec = lineStartSec + (currentOffsetMs / 1000.0)
                    val wordEndSec = wordStartSec + (wordDurMs / 1000.0)

                    val wordText = if (wordIdx < tokens.lastIndex && !isCjkText) "$token " else token
                    words.add(
                        WordTimestamp(
                            text = wordText,
                            startTime = wordStartSec,
                            endTime = wordEndSec,
                        )
                    )
                    currentOffsetMs += wordDurMs
                }
                entry.copy(words = words)
            }
        }
    }

    // ── Romanization ──
    LaunchedEffect(entriesWithWords, romanizeJapanese, romanizeKorean) {
        if (!romanizeJapanese && !romanizeKorean) return@LaunchedEffect
        entriesWithWords.forEach { entry ->
            if (entry.text.isBlank() || entry.romanizedTextFlow.value != null) return@forEach
            scope.launch(Dispatchers.Default) {
                val romanized = when {
                    romanizeJapanese && isJapanese(entry.text) -> romanizeJapanese(entry.text)
                    romanizeKorean && isKorean(entry.text) -> romanizeKorean(entry.text)
                    else -> null
                }
                if (romanized != null) entry.romanizedTextFlow.value = romanized
            }
        }
    }

    // ── Playback position tracking ──
    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }
    // The line the *scroll* is chasing. Usually the same as the highlighted one, briefly the next.
    var scrollLineIndex by remember { mutableIntStateOf(0) }

    // ── Position loop ──
    //
    // Frame-accurate while it matters, idle when it does not. It used to poll at a flat 60Hz for
    // as long as the lyrics tab was open, writing `currentPositionMs` on every one of those ticks
    // whether or not anything had moved — and that value feeds the per-word highlight, so a paused
    // song sat there recomposing the visible lines sixty times a second forever.
    //
    // Three states now: playing or being scrubbed, where nothing less than a frame will do;
    // paused, where 5Hz is enough to notice a resume; and unsynced, where the loop does not run.
    //
    // The lead is scaled by playback speed. It exists to compensate for a fixed amount of
    // wall-clock latency (drawing the frame, the eye reaching the line), and at 1.5x a fixed
    // number of *milliseconds of track* covers only two thirds of the wall-clock time it did at
    // 1x, so the highlight would drift late exactly when the words are hardest to follow.
    LaunchedEffect(entriesWithWords, isSynced) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        while (isActive) {
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: player.currentPosition
            val speed = player.playbackParameters.speed.takeIf { it > 0.05f } ?: 1f

            currentPositionMs = pos + ((leadMs + VISUAL_TUNING_OFFSET_MS) * speed).toLong()
            currentLineIndex = findCurrentLineIndex(entriesWithWords, currentPositionMs, 0L)
            scrollLineIndex = findCurrentLineIndex(
                entriesWithWords,
                currentPositionMs + (SCROLL_LEAD_MS * speed).toLong(),
                0L,
            )

            val live = sliderPos != null || player.isPlaying
            delay(if (live) 16L else 200L)
        }
    }

    // ── Scroll State ──
    val listState = rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    // Detect manual scrolling
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                    isManualScrolling = true
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Offset.Zero
            }
        }
    }

    // Resume auto-scroll after timeout
    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    // Where the column was last asked to go. Distinguishes a line advancing by one from a seek
    // that jumped halfway through the song — the two want completely different scrolls, and
    // without this the code could only ask "is the target on screen right now", which a line one
    // row below the fold also fails.
    var lastTrackedIndex by remember { mutableIntStateOf(-1) }

    // The Apple Music follow, as the Windows app does it.
    //
    // A line change used to scroll the column as one rigid sheet. Now the list jumps to where it is
    // going in one frame and publishes how far it went; every line on screen takes that distance
    // back as its own offset and springs home, each starting ~34ms after the line above it. The
    // sung line moves first and the verse below arrives in a wave behind it, which is the motion
    // that makes Apple's lyrics feel like they flow rather than scroll.
    var lineShift by remember { mutableStateOf<LineShift?>(null) }

    // ── Auto-scroll ──
    //
    // Driven by `scrollLineIndex`, which runs SCROLL_LEAD_MS ahead of the highlight, so the
    // column is already moving when the line lights rather than starting then.
    LaunchedEffect(scrollLineIndex, isManualScrolling, lyricsScroll) {
        if (!lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        if (scrollLineIndex < 0 || scrollLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val target = scrollLineIndex
        val step = if (lastTrackedIndex < 0) Int.MAX_VALUE else abs(target - lastTrackedIndex)
        lastTrackedIndex = target

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        // Apple Music keeps the active line pinned slightly above true center (~42%),
        // leaving more room below for the upcoming lines.
        val anchorY = layoutInfo.viewportStartOffset + (viewportHeight * 0.42f)

        // Firm, and critically damped. This was StiffnessLow (200), which takes the better part of
        // a second to cover a line — longer than plenty of lines last. Every time the next line
        // arrived mid-glide it cancelled this coroutine and started a fresh spring from wherever
        // the list had drifted to, so through a fast verse the column never actually reached the
        // anchor and the active line wandered up and down the screen. At 340 a normal line-to-line
        // move completes in roughly a third of a second, comfortably inside the gap, and the
        // column arrives and *sits* — which is what reads as locked to the song.
        val glide = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 340f,
        )

        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        when {
            itemInfo != null -> {
                // Measured and on screen: glide its centre onto the anchor.
                val delta = itemInfo.offset + itemInfo.size / 2f - anchorY
                if (abs(delta) > 4f) {
                    val moved = listState.scrollBy(delta)
                    lineShift = LineShift(delta = moved, target = target, at = System.nanoTime())
                }
            }

            step <= 2 -> {
                // Just past the fold. Ordinary playback walking off the bottom of the viewport
                // used to land here and get the seek treatment — a hard `scrollToItem` snap
                // followed by a settle — which is why the column would occasionally lurch for no
                // reason mid-song. It is one line further on; animate to it like any other.
                try {
                    listState.animateScrollToItem(
                        index = target,
                        scrollOffset = -(viewportHeight * 0.42f).toInt(),
                    )
                } catch (_: Exception) {
                }
            }

            else -> {
                // A genuine jump (seek, or opening the tab mid-song): snap into the vicinity so
                // the animation is not a thousand-line flight, then settle onto the anchor.
                listState.scrollToItem((target - 2).coerceAtLeast(0))
                listState.animateScrollToItem(
                    index = target,
                    scrollOffset = -(viewportHeight * 0.42f).toInt(),
                )
            }
        }
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast.makeText(
                context,
                context.getString(R.string.max_selection_limit, maxSelectionLimit),
                Toast.LENGTH_SHORT
            ).show()
            showMaxSelectionToast = false
        }
    }

    // ── Keep screen alive ──
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Render ──
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        if (lyrics == LYRICS_NOT_FOUND) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        if (lyrics == null) {
            ShimmerHost {
                repeat(6) {
                    TextPlaceholder()
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .smoothFadingEdge(vertical = 80.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" }
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(120.dp))
                    return@itemsIndexed
                }

                // ── Agent-based positioning ──
                // v1 or null -> Start, v2 -> End, others -> Center
                val textAlign = when (item.agent?.lowercase()) {
                    "v1", null -> TextAlign.Start
                    "v2" -> TextAlign.End
                    else -> TextAlign.Center
                }
                val horizontalAlignment = when (item.agent?.lowercase()) {
                    "v1", null -> Alignment.Start
                    "v2" -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex
                val isFuture = isSynced && index > currentLineIndex

                // Distance-based alpha for non-active lines
                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                // For word-synced lines, each word handles its own alpha independently
                // so we use 1f for active lines to not double-dim
                val lineAlpha = when {
                    !isSynced -> 0.9f
                    isActive -> 1f
                    else -> (inactiveAlpha - (distanceFromActive - 1) * 0.03f)
                        .coerceIn(0.15f, inactiveAlpha)
                }
                // Word-synced lines: pass 1f alpha so individual words control their own
                val wordLineAlpha = if (item.words != null && isSynced) 1f else lineAlpha

                // ── Apple-Music line transition physics ──
                // The active line pops to full size; inactive lines settle at 85% scale and
                // pick up a light depth blur that grows with distance. NOTHING snaps: alpha,
                // scale and blur all ride the same buttery spring the moment the active
                // index moves.
                // Stiffness falls off with distance from the active line, so a line change
                // ripples outward through the stack instead of every line in the viewport
                // arriving at its new state on the same frame. That synchronised move is what
                // made the transition read as a crossfade of three properties rather than as one
                // object being handed forward — and a spring cannot be given a start delay, so
                // the stagger has to come from the curve rather than from timing.
                val lineSpring = remember(distanceFromActive, isActive) {
                    spring<Float>(
                        dampingRatio = 0.8f,
                        stiffness = if (isActive) 420f
                        else (330f - distanceFromActive * 45f).coerceAtLeast(120f),
                    )
                }
                val animatedLineAlpha by animateFloatAsState(
                    targetValue = wordLineAlpha,
                    animationSpec = lineSpring,
                    label = "lineAlpha",
                )
                val animatedLineScale by animateFloatAsState(
                    targetValue = if (!isSynced || isActive) 1f else 0.85f,
                    animationSpec = lineSpring,
                    label = "lineScale",
                )
                // The active line lifts a couple of pixels off the column and the line just
                // behind it settles back. Small enough that it is never read as a layout shift,
                // large enough that the eye is told where the words are without relying on
                // brightness alone - which is the only cue that survives a bright wallpaper
                // behind translucent lyrics.
                val animatedLineLift by animateFloatAsState(
                    targetValue = when {
                        !isSynced -> 0f
                        isActive -> -2.5f
                        distanceFromActive == 1 -> 1.5f
                        else -> 0f
                    },
                    animationSpec = lineSpring,
                    label = "lineLift",
                )
                // Depth-of-field, and it only points forward.
                //
                // Blur used to be symmetric around the active line, because it keyed off
                // `distanceFromActive`, which is an absolute value. That is wrong for the thing
                // it is imitating: a lens racks focus onto what you are about to read. Words you
                // have already sung are not out of focus, they are *behind* you — Apple Music
                // leaves them sharp and simply dims them, and so does a karaoke prompter. Blurring
                // them makes the whole column feel like it is being read through frosted glass
                // with a hole punched in it, and it fights the scroll: the line leaving the top of
                // the viewport softens exactly as your eye is trying to leave it.
                //
                // So: past lines get 0 blur and lean on alpha alone for their recession, and the
                // blur ramp is spent entirely on what is coming. Because it now only has to cover
                // one direction it can be steeper, which buys a stronger sense of depth for the
                // same peak radius.
                val animatedLineBlur by animateFloatAsState(
                    targetValue = when {
                        !isSynced || isActive || isManualScrolling || isPast -> 0f
                        distanceFromActive == 1 -> 2f
                        distanceFromActive == 2 -> 4.5f
                        distanceFromActive == 3 -> 6.5f
                        else -> 8f
                    },
                    animationSpec = lineSpring,
                    label = "lineBlur",
                )
                // Grow/shrink around the line's leading edge like Apple Music (lines
                // anchored to their alignment edge, not ballooning from dead center).
                val lineTransformOrigin = when (horizontalAlignment) {
                    Alignment.End -> TransformOrigin(1f, 0.5f)
                    Alignment.CenterHorizontally -> TransformOrigin(0.5f, 0.5f)
                    else -> TransformOrigin(0f, 0.5f)
                }



                val waveOffset = remember { Animatable(0f) }
                LaunchedEffect(lineShift) {
                    val shift = lineShift ?: return@LaunchedEffect
                    // Only a shift that just happened. A line composed later (scrolled into view by
                    // hand) must not replay an old one and drift in from nowhere.
                    if (System.nanoTime() - shift.at > 120_000_000L) return@LaunchedEffect
                    waveOffset.snapTo(waveOffset.value + shift.delta)
                    val lag = (index - shift.target + 1).coerceIn(0, 12)
                    delay(lag * 34L)
                    waveOffset.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 120f))
                }

                // Background vocal detection
                val hasBackgroundWords = item.words?.any { it.isBackground } == true
                val isAllBackground = item.words?.all { it.isBackground || it.text.isBlank() } == true

                val isSelected = selectedIndices.contains(index)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected && isSelectionModeActive)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else
                                Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(
                            start = if (isAllBackground) 24.dp else 12.dp,
                            end = 12.dp,
                            top = if (index == 0 || (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)) 0.dp else (lyricsLineSpacing * 8).dp,
                            bottom = (lyricsLineSpacing * 8).dp,
                        )
                        .graphicsLayer {
                            alpha = animatedLineAlpha
                            scaleX = animatedLineScale
                            scaleY = animatedLineScale
                            translationY = animatedLineLift.dp.toPx() + waveOffset.value
                            transformOrigin = lineTransformOrigin
                        }
                        .then(
                            if (animatedLineBlur > 0.05f) {
                                Modifier.blur(
                                    radius = animatedLineBlur.dp,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .combinedClickable(
                            enabled = true,
                            onClick = {
                                if (isSelectionModeActive) {
                                    if (isSelected) {
                                        selectedIndices.remove(index)
                                        if (selectedIndices.isEmpty()) {
                                            isSelectionModeActive = false
                                        }
                                    } else {
                                        if (selectedIndices.size < maxSelectionLimit) {
                                            selectedIndices.add(index)
                                        } else {
                                            showMaxSelectionToast = true
                                        }
                                    }
                                } else if (lyricsClick && isSynced && item.time > 0) {
                                    player.seekTo(item.time)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionModeActive) {
                                    isSelectionModeActive = true
                                    selectedIndices.add(index)
                                } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                    selectedIndices.add(index)
                                } else if (!isSelected) {
                                    showMaxSelectionToast = true
                                }
                            }
                        ),
                    horizontalAlignment = horizontalAlignment,
                ) {
                    if (item.words != null && isSynced) {
                        // ── Word-synced rendering ──
                        LyricsLineV2(
                            words = item.words!!,
                            isActive = isActive,
                            isPast = isPast,
                            currentPositionMs = currentPositionMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                            baseFontSize = lyricsTextSize,
                            isLineAllBackground = isAllBackground,
                            textAlign = textAlign,
                            lyricsFontFamily = lyricsFontFamily,
                        )
                    } else {
                        // ── Plain text rendering ──
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                // One weight for every line, as on Windows: brightness says which line is
                                // sung. Swapping Medium for Bold on the active line re-wrapped it mid-glide.
                                fontWeight = FontWeight.Bold,
                                fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                                letterSpacing = LyricTracking,
                            ),
                            color = textColor.copy(alpha = if (isActive) 1f else inactiveAlpha),
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ── Romanization ──
                    val romanizedText by item.romanizedTextFlow.collectAsState()
                    if (romanizedText != null) {
                        Text(
                            text = romanizedText!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (lyricsTextSize * 0.55f).sp,
                                lineHeight = (lyricsTextSize * 0.75f).sp,
                                fontWeight = FontWeight.Normal,
                                fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily,
                            ),
                            color = textColor.copy(alpha = if (isActive) 0.75f else inactiveAlpha * 0.7f),
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = (lyricsTextSize * 0.3f).dp),
                        )
                    }
                }
            }

            // Bottom spacer for overscroll
            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

        // ── Resume auto-scroll button ──
        if (isManualScrolling && isSynced) {
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * 0.35f).toInt()
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (isSelectionModeActive) {
            mediaMetadata?.let { metadata ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.close),
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (selectedIndices.isNotEmpty())
                                        Color.White.copy(alpha = 0.9f)
                                    else
                                        Color.White.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable(enabled = selectedIndices.isNotEmpty()) {
                                    if (selectedIndices.isNotEmpty()) {
                                        val sortedIndices = selectedIndices.sorted()
                                        val selectedLyricsText = sortedIndices
                                            .mapNotNull { entriesWithWords.getOrNull(it)?.text }
                                            .joinToString("\n")

                                        if (selectedLyricsText.isNotBlank()) {
                                            shareDialogData = Triple(
                                                selectedLyricsText,
                                                metadata.title ?: "",
                                                metadata.artists.joinToString { it.name }
                                            )
                                            showShareCarouselSheet = true
                                        }
                                        isSelectionModeActive = false
                                        selectedIndices.clear()
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.share),
                                contentDescription = stringResource(R.string.share_selected),
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.share),
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }


        // ── Carrusel de estilos para compartir letras ──────────────────────────────
        if (showShareCarouselSheet) {
            shareDialogData?.let { (lyricText, title, artist) ->

                mediaMetadata?.let { metadata ->

                    LyricsShareCarouselSheet(
                        lyricText = lyricText,
                        mediaMetadata = metadata,
                        initialConfig = LyricsCardConfig(),
                        onDismiss = {
                            showShareCarouselSheet = false
                            shareDialogData = null
                        },
                        onShare = { config ->

                            showShareCarouselSheet = false
                            shareDialogData = null

                            scope.launch {
                                runCatching {

                                    val bitmap =
                                        ComposeToImage.createLyricsImageWithConfig(
                                            context = context,
                                            coverArtUrl = metadata.thumbnailUrl,
                                            songTitle = title,
                                            artistName = artist,
                                            lyrics = lyricText,
                                            config = config,
                                            outputSize = 1080,
                                        )

                                    val uri = ComposeToImage.saveBitmapAsFile(
                                        context = context,
                                        bitmap = bitmap,
                                        fileName = "lyrics_${System.currentTimeMillis()}",
                                    )

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }

                                    context.startActivity(
                                        Intent.createChooser(shareIntent, null)
                                    )
                                }
                            }
                        },
                        onSave = { config ->

                            scope.launch {
                                runCatching {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val bitmap =
                                            ComposeToImage.createLyricsImageWithConfig(
                                                context = context,
                                                coverArtUrl = metadata.thumbnailUrl,
                                                songTitle = title,
                                                artistName = artist,
                                                lyrics = lyricText,
                                                config = config,
                                                outputSize = 1080,
                                            )

                                        ComposeToImage.saveBitmapAsFile(
                                            context = context,
                                            bitmap = bitmap,
                                            fileName = "lyrics_saved_${System.currentTimeMillis()}",
                                        )
                                    }
                                }
                            }

                            showShareCarouselSheet = false
                            shareDialogData = null
                        },
                    )
                }
            }
        }
    }

    if (showProgressDialog) {
        BasicAlertDialog(onDismissRequest = {}) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.padding(32.dp)) {
                    Text(
                        text = stringResource(R.string.generating_image) + "\n" + stringResource(R.string.please_wait),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showShareDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val songLink = "https://music.youtube.com/watch?v=${mediaMetadata?.id}"
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "\"$lyricsText\"\n\n$songTitle - $artists\n$songLink")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_lyrics)))
                                showShareDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                shareDialogData = Triple(lyricsText, songTitle, artists)
                                showColorPickerDialog = true
                                showShareDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { showShareDialog = false }
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showColorPickerDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        val coverUrl = mediaMetadata?.thumbnailUrl

        LaunchedEffect(coverUrl) {
            if (coverUrl != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val loader = ImageLoader(context)
                        val req = ImageRequest.Builder(context).data(coverUrl).allowHardware(false).build()
                        val result = loader.execute(req)
                        val bmp = result.image?.toBitmap()
                        if (bmp != null) {
                            val palette = Palette.from(bmp).generate()
                            paletteGlassStyle = LyricsGlassStyle.fromPalette(palette)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        val availableStyles = remember(paletteGlassStyle) {
            val base = LyricsGlassStyle.allPresets.toMutableList()
            paletteGlassStyle?.let { base.add(0, it) }
            base
        }

        BasicAlertDialog(onDismissRequest = { showColorPickerDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.customize_colors),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        LyricsImageCard(
                            lyricText = lyricsText,
                            mediaMetadata = mediaMetadata ?: return@Box,
                            glassStyle = selectedGlassStyle,
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(id = R.string.customize_colors),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        availableStyles.forEach { style ->
                            val isSelected = selectedGlassStyle == style
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                        } else {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                        }
                                    )
                                    .clickable { selectedGlassStyle = style },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    style.surfaceTint.copy(alpha = 0.6f),
                                                    style.overlayColor.copy(alpha = 0.4f),
                                                )
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .fillMaxSize()
                                        .background(style.surfaceTint.copy(alpha = style.surfaceAlpha), RoundedCornerShape(10.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "Aa", color = style.textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showColorPickerDialog = false
                            showProgressDialog = true
                            scope.launch {
                                try {
                                    val exportSize = 1080
                                    val image = ComposeToImage.createLyricsImage(
                                        context = context,
                                        coverArtUrl = coverUrl,
                                        songTitle = songTitle,
                                        artistName = artists,
                                        lyrics = lyricsText,
                                        width = exportSize,
                                        height = exportSize,
                                        glassStyle = selectedGlassStyle,
                                    )
                                    val timestamp = System.currentTimeMillis()
                                    val filename = "lyrics_$timestamp"
                                    val uri = ComposeToImage.saveBitmapAsFile(context, image, filename)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to create image: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    showProgressDialog = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(text = stringResource(id = R.string.share), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}


// ──────────────────────────────────────────────────────────────────────
// Line-level composable: renders words with fluid fill animation
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineV2(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    lyricsFontFamily: FontFamily?,
) {
    val arrangement = when (textAlign) {
        TextAlign.Center -> Arrangement.Center
        TextAlign.End -> Arrangement.End
        else -> Arrangement.Start
    }

    // Split words into main and background
    val mainWords = words.filter { !it.isBackground }
    val bgWords = words.filter { it.isBackground }

    // 1. Render main words First (if any)
    if (mainWords.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            mainWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                            fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            letterSpacing = LyricTracking,
                        ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    lyricsFontFamily = lyricsFontFamily,
                )
            }
        }
    }

    // 2. Render background words explicitly on a NEW line, noticeably smaller
    if (bgWords.isNotEmpty()) {
        val spacerHeight = if (mainWords.isNotEmpty()) 4.dp else 0.dp
        if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(spacerHeight))

        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f), // Slightly dimmer overall
            horizontalArrangement = arrangement,
        ) {
            bgWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = (baseFontSize * 0.65f).sp,
                            fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            letterSpacing = LyricTracking,
                        ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex + mainWords.size,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f, // ~65% size of main text
                    isBackground = true, // Force dimmer styling inside AnimatedWordV2
                    lyricsFontFamily = lyricsFontFamily,
                )
            }
        }
    }
}


// ──────────────────────────────────────────────────────────────────────
// Word-level composable: liquid fill sweep + glow + bounce
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedWordV2(
    word: WordTimestamp,
    wordIndex: Int,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    lyricsFontFamily: FontFamily?,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    // Perfect linear progress [0..1] that matches individual word timings
    val progress = when {
        isWordComplete -> 1f
        currentPositionMs <= wordStartMs -> 0f
        else -> ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    }

    // ── The swell: one envelope, shared by lift, scale and glow ──
    //
    // This used to be `sin(pi * progress)` — a symmetric arch that peaks exactly halfway
    // through the word. A sung note is not symmetric. It attacks fast, sits while it is being
    // held, and releases as the singer moves off it, and on a long held syllable the sine spends
    // the entire first half still climbing, so the word reaches its brightest moment long after
    // you have heard it. That lag is the reason word-sync could look subtly *behind* the audio
    // even when the timestamps were perfect.
    //
    // So it is an ADSR envelope instead: up in the first 18% of the word, a small decay to a
    // sustain that holds for as long as the note does, and a release over the last quarter.
    // Short words are essentially all attack and release — a pop. Long words rise, hold lit, and
    // set down. That is the shape the ear is already expecting.
    val swell = when {
        progress < 0.18f -> progress / 0.18f
        progress < 0.75f -> 1f - 0.28f * ((progress - 0.18f) / 0.57f)
        else -> 0.72f * (1f - (progress - 0.75f) / 0.25f)
    }.coerceIn(0f, 1f)

    // Big enough to actually see. The old 1.5% scale was below the threshold at which a glyph
    // reads as having moved at all, so the "bounce" was costing a layer and buying nothing.
    val wordScale = 1f + (if (isBackground) 0.018f else 0.032f) * swell

    // Float is only applied when the word is actively sung, making it pop from the line.
    // The animatable is kept for the tail: when the word ends the target drops to 0 and this
    // eases it home over 350ms instead of snapping, so the word sets down rather than dropping.
    val targetFloat = if (isWordActive) -5.5f * swell else 0f
    val floatOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (isWordActive) 50 else 350,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "wordFloat",
    )

    // ── Glow intensity ──
    // "lines and words that are done animating shouldnt continue to glow" — so the target is a
    // hard 0 the moment the word ends. It is animated rather than assigned so the glow *fades*
    // over ~200ms as the next word lights up; two words briefly overlapping in light is what
    // makes the line read as one moving highlight instead of a row of bulbs switching.
    val targetGlow = if (isWordActive) swell else 0f
    val glow by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetGlow,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (isWordActive) 60 else 200,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "wordGlow",
    )
    val glowAlpha = glow * 0.5f
    val glowRadius = glow * 14f

    // How bright a word is *before* the highlight reaches it.
    //
    // Every unlit word used to sit at `inactiveAlpha` — the same value as a line three rows away.
    // So the words you are about to sing were as faint as words you will not reach for ten
    // seconds, and the only readable thing on screen was the part already sung. That is backwards
    // for a prompter: the whole reason a karaoke line is on screen ahead of time is to be read
    // ahead of time. Lifting the *active* line's unlit words well clear of the column gives the
    // line three distinct states — sung, singing-next, elsewhere — instead of two.
    val restingAlpha = if (isLineActive) {
        (inactiveAlpha * 1.6f).coerceIn(inactiveAlpha, 0.62f)
    } else {
        inactiveAlpha
    }

    val actualFontSize = if (isBackground) fontSize * 0.85f else fontSize
    val fontWeight = FontWeight.Bold // Same weight as every other line, so nothing re-wraps as it lights

    // ── Two-layer rendering: dim base + liquid fill overlay ──
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = floatOffset * density
                scaleX = wordScale
                scaleY = wordScale
                // Pivot on the baseline, not the middle of the glyph box. Scaling from the
                // centre pushes a growing word down into the descender space of the line as
                // hard as it pushes it up, which cancels half of the lift; pivoting at the
                // bottom means the word grows *upward* out of the line, the way a struck key
                // rises. It also keeps the baseline of the active word aligned with the words
                // either side of it, so a swelling word does not appear to sink.
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    ) {
        // Layer 1: Base text (always dimmed)
        Text(
            text = word.text,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = actualFontSize.sp,
                fontWeight = fontWeight,
                fontStyle = FontStyle.Normal,
                lineHeight = (actualFontSize * 1.35f).sp,
                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                letterSpacing = LyricTracking,
            ),
            color = textColor.copy(alpha = if (isBackground) restingAlpha * 0.7f else restingAlpha),
        )

        // Layer 2: Filled overlay with liquid sweep mask + glow
        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = actualFontSize.sp,
                    fontWeight = fontWeight,
                    fontStyle = FontStyle.Normal,
                    lineHeight = (actualFontSize * 1.35f).sp,
                    fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                    letterSpacing = LyricTracking,
                    shadow = if (glowAlpha > 0f) {
                        Shadow(
                            color = textColor.copy(alpha = glowAlpha),
                            offset = Offset.Zero,
                            blurRadius = glowRadius.coerceAtLeast(1f),
                        )
                    } else null,
                ),
                color = textColor.copy(
                    alpha = if (isBackground) 0.75f else 1f
                ),
                modifier = if (isWordActive && !isWordComplete) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // The wipe that reveals the lit word, softened at its leading edge.
                            //
                            // A fixed 8dp edge is a different thing on a three-letter word than
                            // on a twelve-letter one: on the short word it is most of the glyph
                            // (so the fill never looks solid) and on the long one it is a hard
                            // line sliding across (so it looks like a wiper blade). Scaling the
                            // feather with the word's own width keeps the *proportion* of the
                            // sweep that is soft constant, which is what the eye actually reads.
                            //
                            // Three stops rather than two: black → half → transparent gives the
                            // edge a shoulder, so the highlight trails off into the unlit text
                            // instead of ending on a visible boundary.
                            val edgeWidth = (size.width * 0.22f).coerceIn(6.dp.toPx(), 22.dp.toPx())
                            val center = (size.width + edgeWidth * 2) * progress - edgeWidth
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    0f to Color.Black,
                                    0.55f to Color.Black.copy(alpha = 0.45f),
                                    1f to Color.Transparent,
                                    startX = center - edgeWidth,
                                    endX = center + edgeWidth,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                }
            )
        }
    }
}