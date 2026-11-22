/*
 * Ported from the reference design, GPL-3.0, for Exhale's default
 * player. The reference design's own code; only the package and the imports of its app-wide pieces changed,
 * those pieces being stood in for by BcShims.kt.
 */
package com.ozyern.exhale.ui.player.controls

import androidx.compose.ui.res.vectorResource
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.isSpecified

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * How long the shuffle glyph ignores further taps after one lands.
 *
 * Toggling shuffle replaces the upcoming stretch of the live queue. A second
 * tap while that command is crossing the session boundary could otherwise ask
 * to undo work that has not landed yet. One tap is all a toggle can usefully
 * mean in that window, so the rest are dropped rather than queued behind it.
 */
private const val SHUFFLE_TAP_WINDOW_MS = 400L
/**
 * The same gate for AutoPlay, held longer because its work is heavier: the
 * toggle crosses to the playback service, tears down the in-flight suggestion
 * load, and then either strips AutoPlay's tracks out of the queue or goes back
 * to the network for a fresh set of them.
 */
private const val AUTOPLAY_TAP_WINDOW_MS = 700L



/** How far [PlayerScrubber] draws its times above the slot they are laid out in. */
internal val SCRUBBER_LABEL_LIFT = 9.dp

/**
 * The seek bar, with elapsed and remaining either side under it and
 * [centerLabel] — the quality badge — between them.
 */
@Composable
internal fun PlayerScrubber(
    /**
     * Where the handle is, read in here so the playhead's tick recomposes the
     * bar and its two times rather than the player the bar sits in.
     */
    shown: () -> Float,
    durationMs: Long,
    /** A version switch's wait, drawn along the bar — see [ThinSlider.loading]. */
    loading: Boolean,
    /** An analysed Automix handoff, drawn as a travelling sheen. */
    mixing: Boolean,
    transitionWindow: ClosedFloatingPointRange<Float>?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    /** The right-hand label shows the track's length rather than the time left. */
    showTotalDuration: Boolean = false,
    centerLabel: @Composable BoxScope.() -> Unit = {},
) {
    val shown = shown()
    // Held as a State and read only inside the bar's draw: the blend republishes
    // every fade tick, and collecting it by value here would recompose the
    // scrubber and its labels thirty times a second for a change only the
    // canvas needs.
    val mixBlend = PlayerSettings.smartMixBlend.collectAsStateWithLifecycle()
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    // One clock for the bar and the "Mixing" label, so they breathe on the same beat.
    val mixPulse = rememberMixPulse({ mixBlend.value }, enabled = !reduceAnimation)
    Column(Modifier.fillMaxWidth()) {
        val ink = sliderInk()
        ThinSlider(
            value = shown,
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            loading = loading,
            mixing = mixing,
            mixPulse = mixPulse,
            transitionWindow = transitionWindow,
            vibrant = ink != null,
            activeColor = ink?.active ?: Color.White.copy(alpha = 0.92f),
            inactiveColor = ink?.inactive ?: Color.White.copy(alpha = 0.26f),
            markerColor = ink?.marker ?: Color.White.copy(alpha = 0.5f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // The slider's touch target extends well past the drawn bar,
                // so pull the labels back up under it.
                .offset(y = -SCRUBBER_LABEL_LIFT),
        ) {
            // Dodged like the bottom row's glyphs — see [SCRUBBER_LABELS_DODGED].
            val chrome = LocalPlayerChrome.current
            val dodged = chrome && SCRUBBER_LABELS_DODGED
            val timeColor = if (dodged) brightRowInk() else chromeWhite(0.55f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (dodged) Modifier.brightRowChrome(true) else Modifier.additiveChrome(chrome)),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime((shown * durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = timeColor,
                )
                Text(
                    text = if (showTotalDuration && durationMs > 0L) {
                        formatTime(durationMs)
                    } else {
                        "-" + formatTime(durationMs - (shown * durationMs).toLong())
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = timeColor,
                )
            }
            // Pinned to the box's own centre rather than squeezed into the gap
            // between the two timestamps: that gap's width changes by a digit's
            // worth every time a minute rolls over. Cross-faded with "Mixing"
            // for as long as a blend holds the bar.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - mixPulse.cover },
            ) {
                centerLabel()
            }
            val mixingShown by remember { derivedStateOf { mixPulse.cover > 0f } }
            if (mixingShown) {
                MixingLabel(mixPulse, Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * "Mixing", in the quality badge's place while an Automix blend runs, breathing on the same
 * beat as the bar above it. Its opacity is read in a graphics layer, so the pulse redraws the
 * label every frame without recomposing it. Plain text in the badge's own type, no icon and
 * no glow: over artwork, a white haze reads as grey fog rather than light.
 */
@Composable
private fun MixingLabel(pulse: MixPulse, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.mixing),
        modifier = modifier
            // Opens the mix view. Plain text that takes taps: no padding, no
            // ripple, so it lays out exactly as the label always did and
            // nothing around it moves when it appears.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { MixViewRequest.open = true }
            .graphicsLayer {
                alpha = pulse.cover * pulse.alpha(1f, restShare = MIXING_LABEL_REST)
                // A buffer to fade through would cut the blend below off from the backdrop.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .additiveChrome(LocalPlayerChrome.current),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + BADGE_SIZE_BUMP).sp,
        ),
        color = chromeWhite(0.85f),
        maxLines = 1,
    )
}


/** The label dips only to half between beats: text fading further stops reading as a word. */
private const val MIXING_LABEL_REST = 0.5f

/**
 * Sp over labelMedium for the quality badge and what stands in for it. Was a
 * whole point at SemiBold, which shouted next to the timestamps either side.
 */
private const val BADGE_SIZE_BUMP = 0.5f



/**
 * Previous, play/pause, next.
 *
 * [compact] is the landscape player on a phone, where the portrait player's
 * 100dp play target is a third of the window's height on its own.
 */
@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    /** While the stream resolves and buffers, the play glyph would be a lie. */
    isLoading: Boolean,
    /**
     * Lit whenever back has something to do — a track to step to, or enough
     * elapsed to restart this one. Faded and inert, not removed, while a party
     * host holds the controls: the transport keeps its shape either way.
     */
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean = false,
    animationsActive: Boolean = true,
) {
    val playSize = if (compact) 58.dp else 74.dp
    val playTouch = if (compact) 76.dp else 92.dp
    val skipSize = if (compact) 44.dp else PLAYER_SKIP_ICON_SIZE
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val previousMotion = rememberPlayerSkipMotion(active = animationsActive, reduceMotion = reduceAnimation)
    val nextMotion = rememberPlayerSkipMotion(active = animationsActive, reduceMotion = reduceAnimation)
    val playPauseMotion = rememberExpandedPlayPauseMotion(
        isPlaying = isPlaying,
        active = animationsActive && !isLoading,
        reduceMotion = reduceAnimation,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        // SpaceAround, not SpaceEvenly: the outer margins take half a gap, so
        // the three buttons spread a little further apart from each other.
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportGlyph(
            icon = Res.drawable.ic_player_previous,
            contentDescription = stringResource(Res.string.widget_previous),
            size = skipSize,
            touchSize = PLAYER_SKIP_TOUCH_SIZE,
            heightScale = PLAYER_SKIP_HEIGHT_SCALE,
            onClick = onPrevious,
            enabled = previousEnabled,
            haptic = Haptic.SkipPrevious,
            skipMotion = previousMotion,
            skipPrevious = true,
        )
        if (isLoading) {
            // Same footprint as the play/pause target — a smaller box here
            // would shunt everything below it on every load.
            Box(Modifier.size(playTouch), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(if (compact) 30.dp else 38.dp),
                )
            }
        } else {
            TransportGlyph(
                icon = if (isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play,
                contentDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                size = playSize,
                touchSize = playTouch,
                onClick = onPlayPause,
                haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                playPauseMotion = playPauseMotion,
            )
        }
        TransportGlyph(
            icon = Res.drawable.ic_player_next,
            contentDescription = stringResource(Res.string.widget_next),
            size = skipSize,
            touchSize = PLAYER_SKIP_TOUCH_SIZE,
            heightScale = PLAYER_SKIP_HEIGHT_SCALE,
            onClick = onNext,
            enabled = nextEnabled,
            haptic = Haptic.SkipNext,
            skipMotion = nextMotion,
        )
    }
}

/** ThinSlider's fixed touch target at the volume bar's size: 10dp + 22dp. */
internal val VOLUME_ROW_HEIGHT = 32.dp

/** The volume bar between its two speaker glyphs. */
@Composable
internal fun VolumeRow(
    /**
     * Read inside the row, so the level's tween recomposes the row alone rather
     * than the whole player around it.
     */
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val chrome = LocalPlayerChrome.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = null,
            tint = if (chrome) brightRowInk() else chromeWhite(0.5f),
            modifier = Modifier.size(20.dp).brightRowChrome(chrome),
        )
        Spacer(Modifier.width(10.dp))
        val ink = sliderInk()
        ThinSlider(
            value = value(),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
            vibrant = ink != null,
            activeColor = ink?.active ?: Color.White.copy(alpha = 0.92f),
            inactiveColor = ink?.inactive ?: Color.White.copy(alpha = 0.26f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = if (chrome) brightRowInk() else chromeWhite(0.5f),
            modifier = Modifier.size(20.dp).brightRowChrome(chrome),
        )
    }
}

/**
 * Lyrics, the capsule, and the queue — the row both layouts end on.
 *
 * The capsule swaps: output and party normally, the three playback modes while
 * the queue is up, since that is when they are what you are about to reach for.
 * It stays centred between the two glyphs either way.
 */
@Composable
internal fun PlayerActionRow(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    autoplayEnabled: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onOpenOutput: () -> Unit,
    onListenTogether: () -> Unit,
    /** Opens [ListenTogetherMembersSheet] rather than settings directly. */
    onOpenListenTogetherMembers: () -> Unit,
    // Exhale: 8D in the queue's capsule, and the output glyph Exhale picks for the device.
    eightDEnabled: Boolean = false,
    onToggleEightD: () -> Unit = {},
    outputSymbol: String? = null,
) {
    val showPartyButton by PlayerSettings.showListenTogetherButton.collectAsStateWithLifecycle()
    val inParty = false
    // Just the output glyph in the middle.
    val compact = !queueOpen && !showPartyButton && !inParty
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Sized for the wider of the two capsules — the three-up one — whether
        // that or the output/party one is up, so the lyrics and queue glyphs
        // hold still as they swap. A lone output glyph is spaced as though the
        // two-up capsule held it — closer in, without crowding — and they part
        // again as the queue opens to make room.
        val edgeInset = ((maxWidth - BOTTOM_ACTION_SIZE * 2 - pillWidth(4)) / 4).coerceAtLeast(0.dp)
        val compactInset = ((maxWidth - BOTTOM_ACTION_SIZE * 2 - pillWidth(2)) / 4).coerceAtLeast(0.dp)
        val closeIn = compactInset - edgeInset
        // Held as State and read only in the glyphs' placement, so the slide
        // re-places two glyphs a frame rather than recomposing and remeasuring
        // the row — the capsule's own animation included — on every one.
        val compactness = animateFloatAsState(
            targetValue = if (compact) 1f else 0f,
            // In step with the capsule's own width change.
            animationSpec = tween(220),
            label = "actionRowCompact",
        )
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = edgeInset),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((closeIn.toPx() * compactness.value).roundToInt(), 0) },
            ) {
                BottomGlyph(
                    icon = PlayerIcons.LyricsQuote,
                    contentDescription = stringResource(if (lyricsOpen) Res.string.close_lyrics else Res.string.open_lyrics),
                    // Lyrics and the queue are two things to show in one place, so
                    // opening either closes the other; lit to say which is up.
                    onClick = onToggleLyrics,
                    highlighted = lyricsOpen,
                    vibrant = true,
                )
            }
            AnimatedContent(
                targetState = queueOpen,
                transitionSpec = {
                    (fadeIn(tween(180, delayMillis = 140)) togetherWith fadeOut(tween(140)))
                        // Unclipped: the capsule's own rounded ends are what
                        // the eye follows through the width change, and the
                        // default clip cuts them square while it happens.
                        .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                },
                label = "playerBottomPill",
            ) { showQueueModes ->
                if (showQueueModes) {
                    // Spaced the same as the output/party capsule, so the two
                    // read as one control growing a segment.
                    Pill {
                        PillSegment(
                            icon = PlayerIcons.Shuffle,
                            contentDescription = stringResource(
                                if (shuffleEnabled) Res.string.shuffle_on else Res.string.shuffle_off,
                            ),
                            onClick = onToggleShuffle,
                            highlighted = shuffleEnabled,
                            haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                            tapWindowMs = SHUFFLE_TAP_WINDOW_MS,
                        )
                        PillDivider()
                        PillSegment(
                            icon = if (repeatMode == RepeatModes.ONE) null else PlayerIcons.Repeat,
                            label = if (repeatMode == RepeatModes.ONE) "1" else null,
                            contentDescription = when (repeatMode) {
                                RepeatModes.ONE -> stringResource(Res.string.repeat_one)
                                RepeatModes.ALL -> stringResource(Res.string.repeat_all)
                                else -> stringResource(Res.string.repeat_off)
                            },
                            onClick = onCycleRepeat,
                            highlighted = repeatMode != RepeatModes.OFF,
                            // Three states, so the buzz tracks the edges of the
                            // cycle: leaving off rises, returning to off falls,
                            // and the step between the two repeat modes is just
                            // a selection.
                            haptic = when (repeatMode) {
                                RepeatModes.OFF -> Haptic.ToggleOn
                                RepeatModes.ONE -> Haptic.ToggleOff
                                else -> Haptic.Select
                            },
                        )
                        PillDivider()
                        PillSegment(
                            icon = PlayerIcons.Infinity,
                            contentDescription = stringResource(
                                if (autoplayEnabled) Res.string.autoplay_on else Res.string.autoplay_off,
                            ),
                            onClick = onToggleAutoplay,
                            highlighted = autoplayEnabled,
                            haptic = if (autoplayEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                            tapWindowMs = AUTOPLAY_TAP_WINDOW_MS,
                        )
                        // Exhale: 8D, beside the other ways the queue plays.
                        PillDivider()
                        PillSegment(
                            icon = ImageVector.vectorResource(com.ozyern.exhale.R.drawable.ic_queue_8d),
                            contentDescription = stringResource(if (eightDEnabled) Res.string.eight_d_on else Res.string.eight_d_off),
                            onClick = onToggleEightD,
                            highlighted = eightDEnabled,
                            haptic = if (eightDEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                        )
                    }
                } else {
                    // Exhale: the output glyph alone, the one Exhale picks for what is playing it
                    // (earbuds, speaker, car...), where the reference design's output/party capsule sat.
                    Pill {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(PILL_SEGMENT_WIDTH)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onOpenOutput,
                                ),
                        ) {
                            com.ozyern.exhale.ui.component.SymbolIcon(
                                symbol = com.ozyern.exhale.ui.component.sf(outputSymbol ?: "airplayaudio"),
                                contentDescription = stringResource(Res.string.output),
                                size = 23.dp,
                                tint = Color.White.copy(alpha = 0.85f),
                                bounceOnChange = true,
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(-(closeIn.toPx() * compactness.value).roundToInt(), 0) },
            ) {
                BottomGlyph(
                    icon = PlayerIcons.Queue,
                    contentDescription = stringResource(Res.string.up_next),
                    onClick = onToggleQueue,
                    highlighted = queueOpen,
                    haptic = if (queueOpen) Haptic.Tap else Haptic.Expand,
                    vibrant = true,
                )
            }
        }
    }
}

/** How much more of the ink [CircleGlyph]'s disc takes than its plain white — 0.45 at rest, 0.85 lit. */
private const val CIRCLE_DISC_DODGE_GAIN = 2.5f

/** The star and ⋯ discs' fill at rest. */
private const val CIRCLE_DISC_ALPHA = 0.18f

/**
 * The lyrics, output and queue glyphs, and the volume bar's speakers, dodged
 * harder than the rest of the chrome: a thin glyph shows far less of a blend
 * than a filled disc does, and at the usual strength they read as dim on any
 * cover that isn't already bright. More of the cover's own colour, not more
 * white. False puts them back as they were.
 */
private const val BOTTOM_ROW_BRIGHT = true

/**
 * The bottom row's ink: solid, and a lighter grey than [VibrantChromeInk] for
 * a dodge of about 3x rather than 2.5x. Short of the 3.4x that flattened
 * bright, saturated covers to one loud colour.
 */
private val BrightChromeInk = Color(0xFFAAAAAA)

/**
 * The timestamps and the quality badge between them dodged as the bottom row's
 * glyphs are, rather than added in white. False puts them back.
 */
private const val SCRUBBER_LABELS_DODGED = true

/** The volume bar's two speakers, drawn as the bottom row's glyphs are. */
@Composable
@ReadOnlyComposable
private fun brightRowInk(): Color = when {
    !BOTTOM_ROW_BRIGHT -> VibrantChromeInk
    LocalChromeSoftened.current -> SoftenedChromeInk
    else -> BrightChromeInk
}

/** [BrightChromeInk] a touch down, about 2.75x, for [LocalChromeSoftened]. */
private val SoftenedChromeInk = Color(0xFFA2A2A2)

private fun Modifier.brightRowChrome(chrome: Boolean): Modifier = vibrantChrome(chrome)

/**
 * The seek and volume bars dodged as the bottom row's glyphs are, rather than
 * laid over the cover in white. False puts them back.
 */
private const val SLIDERS_DODGED = true

/**
 * The bars' ink: a shade lighter than the bottom row's, for about 3.2x. A bar
 * is a thin line too, and at the glyphs' strength it read as dim.
 */
private val SliderChromeInk = Color(0xFFB0B0B0)

/** [SliderChromeInk] a touch down, about 2.9x, for [LocalChromeSoftened]. */
private val SoftenedSliderInk = Color(0xFFA8A8A8)

@Composable
@ReadOnlyComposable
private fun sliderChromeInk(): Color =
    if (LocalChromeSoftened.current) SoftenedSliderInk else SliderChromeInk

/** The unplayed track's share of [SliderChromeInk]. */
private const val SLIDER_TRACK_ALPHA = 0.6f

/** Played, unplayed and the Automix window, in the bars' ink. */
private data class SliderInk(val active: Color, val inactive: Color, val marker: Color)

/** Null where the bars are drawn as they always were. */
@Composable
private fun sliderInk(): SliderInk? =
    if (LocalPlayerChrome.current && SLIDERS_DODGED) {
        SliderInk(
            active = sliderChromeInk(),
            // Enough of the cover to read as track, well short of the played part.
            inactive = sliderChromeInk().copy(alpha = SLIDER_TRACK_ALPHA),
            marker = sliderChromeInk().copy(alpha = 0.75f),
        )
    } else {
        null
    }

/**
 * Translucent circular button used for the track menu and the like control.
 *
 * [active] brightens the disc rather than only the glyph: this sits on album
 * artwork of any colour, and a white icon on a white-ish sleeve has no tint
 * change left to make. The filled star carries the state as a shape too —
 * see [PlayerIcons.StarFilled].
 */
@Composable
internal fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else CIRCLE_DISC_ALPHA,
        label = "glyphDisc",
    )
    val chrome = LocalPlayerChrome.current
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        // The disc carries the blend, dodged as the bottom row's glyphs are, and
        // the glyph sits on it in plain white. The ink is mixed in at a few times
        // the plain disc's strength: a dodge laid on this thinly is next to nothing.
        Box(
            Modifier
                .matchParentSize()
                .vibrantChrome(chrome)
                .background(discFill(chrome, discAlpha)),
        )
        // Outside the blend: the rim is light catching the edge, not ink.
        Box(Modifier.matchParentSize().glassRim(CircleShape))
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = 180),
            label = "playerMenuGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * The star and ⋯ discs' fill at [alpha]: under the blend, the ink at a few
 * times that strength, since a dodge laid on this thinly is next to nothing.
 */
private fun discFill(chrome: Boolean, alpha: Float = CIRCLE_DISC_ALPHA): Color =
    if (chrome) {
        VibrantChromeInk.copy(alpha = (alpha * CIRCLE_DISC_DODGE_GAIN).coerceAtMost(1f))
    } else {
        Color.White.copy(alpha = alpha)
    }

/**
 * Glass's edge: a hairline rim, brighter along the top where the light would
 * catch it. One rim for the Best Part pill and the star and ⋯ discs, so they
 * read as the same material.
 */
internal fun Modifier.glassRim(shape: Shape): Modifier = border(
    width = 1.dp,
    brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.42f),
        1f to Color.White.copy(alpha = 0.14f),
    ),
    shape = shape,
)

/**
 * Transport / bottom glyphs. The circular clip belongs on the touch target,
 * never on the [Icon] — clipping the icon itself shaves the corners off wide
 * glyphs like fast-forward and the queue list.
 */
@Composable
private fun TransportGlyph(
    icon: DrawableResource,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    touchSize: androidx.compose.ui.unit.Dp = size,
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
    /** Vertical squash of the glyph alone; its width and touch box are untouched. */
    heightScale: Float = 1f,
    skipMotion: PlayerSkipMotion? = null,
    skipPrevious: Boolean = false,
    playPauseMotion: ExpandedPlayPauseMotion? = null,
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(touchSize)
            .then(
                if (skipMotion != null) Modifier.playerSkipPressCircle(
                    motion = skipMotion,
                    interactionSource = interactionSource,
                    enabled = enabled,
                    tint = Color.White.copy(alpha = alpha),
                    glyphSize = size,
                    diameter = PLAYER_PRESS_CIRCLE_DIAMETER,
                ) else if (playPauseMotion != null) Modifier.expandedPlayPausePressCircle(
                    motion = playPauseMotion,
                    interactionSource = interactionSource,
                    tint = Color.White.copy(alpha = alpha),
                    glyphSize = size,
                    diameter = PLAYER_PRESS_CIRCLE_DIAMETER,
                ) else Modifier,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                skipMotion?.onTap()
                playPauseMotion?.onTap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (skipMotion != null) {
            PlayerSkipGlyph(
                motion = skipMotion,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .size(size)
                    .then(if (heightScale != 1f) Modifier.graphicsLayer { scaleY = heightScale } else Modifier),
                previous = skipPrevious,
            )
        } else if (playPauseMotion != null) {
            ExpandedPlayPauseGlyph(
                motion = playPauseMotion,
                interactionSource = interactionSource,
                playPainter = painterResource(Res.drawable.ic_player_play),
                pausePainter = painterResource(Res.drawable.ic_player_pause),
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .size(size)
                    .then(if (heightScale != 1f) Modifier.graphicsLayer { scaleY = heightScale } else Modifier),
            )
        }
    }
}

/** The skip glyphs' width, and their touch box, beside the larger play button. */
private val PLAYER_SKIP_ICON_SIZE = 53.dp
private val PLAYER_SKIP_TOUCH_SIZE = 53.dp

/** Draw-only diameter shared by expanded playback controls; hit targets stay unchanged. */
private val PLAYER_PRESS_CIRCLE_DIAMETER = 60.dp

/**
 * The skip glyphs are drawn a little flatter than they are wide, so the pair
 * reads lower and longer next to the play button without losing any width.
 */
private const val PLAYER_SKIP_HEIGHT_SCALE = 0.85f

private val BOTTOM_ACTION_SIZE = 44.dp

/** What the lyrics and queue glyphs are drawn at inside [BOTTOM_ACTION_SIZE]. */
private val BOTTOM_ICON_SIZE = 26.dp

/**
 * The lit lyrics/queue tile, measured off Apple Music's: the glyph fills about
 * three quarters of it, its corners about a fifth, and it is the tint at a
 * little over half strength so the artwork still colours it.
 */
private val BOTTOM_ACTIVE_TILE_SIZE = 35.dp
private val BOTTOM_ACTIVE_TILE_CORNER = 7.dp
private const val BOTTOM_ACTIVE_TILE_ALPHA = 0.62f

/**
 * One half of the output capsule — wider than it is tall, so the capsule reads
 * as a capsule rather than as two circles that have been pushed together.
 */
// The count reserve is kept on both halves, so entering a party never makes
// the capsule lopsided or shifts the queue control beside it.
private val PILL_SEGMENT_WIDTH = 64.dp





/** What a segment's glyph is drawn at when it has no optical quirk to correct. */
private val PILL_ICON_SIZE = 24.dp

/** How wide a capsule of [segments] comes out, dividers included. */
private fun pillWidth(segments: Int): Dp =
    PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

/**
 * A row of controls joined into one capsule.
 *
 * The join is a hairline rather than a gap, which is what makes several
 * controls read as a single object — the shape the player uses for a set of
 * choices that all answer the same question. There are two: where the sound is
 * going, and how the queue is played.
 */
@Composable
private fun Pill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
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




/**
 * One control inside a [Pill] — [BottomGlyph]'s twin, squared off.
 *
 * Same behaviour down to the tap window, and deliberately not the same
 * composable: a glyph's highlight is a circle sized to itself, and a segment's
 * has to fill its share of the capsule edge to edge or the join stops reading
 * as one.
 */
@Composable
private fun PillSegment(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconSize: Dp = PILL_ICON_SIZE,
    label: String? = null,
    trailingLabel: String? = null,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    loading: Boolean = false,
    /** See [BottomGlyph], where the same window means the same thing. */
    tapWindowMs: Long = 0L,
    /** Per-segment override — see [OutputPartyPill]'s three-up capsule. */
    width: Dp = PILL_SEGMENT_WIDTH,
    /** See [BottomGlyph], where the same flag means the same thing. */
    vibrant: Boolean = false,
) {
    val haptics = rememberHaptics()
    val lastTap = remember { mutableLongStateOf(-tapWindowMs) }
    val chrome = LocalPlayerChrome.current
    Box(
        modifier = Modifier
            .width(width)
            .height(BOTTOM_ACTION_SIZE)
            // Round the highlight and both fades, not inside them — see [CircleGlyph].
            .additiveChrome(chrome && !vibrant)
            .background(if (highlighted) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !loading,
            ) {
                val now = uptimeMillis()
                if (now - lastTap.longValue >= tapWindowMs) {
                    lastTap.longValue = now
                    haptics.play(haptic)
                    onClick()
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = when {
            vibrant && chrome -> brightRowInk()
            !highlighted -> chromeWhite(0.75f)
            chrome -> Color.White.copy(alpha = CHROME_FULL_ALPHA)
            else -> Color.White
        }
        Crossfade(
            targetState = loading,
            modifier = Modifier.vibrantChrome(chrome && vibrant),
            animationSpec = tween(durationMillis = 200),
            label = "pillSegmentLoading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else if (icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The size travels with the icon, so the outgoing glyph
                    // fades at its own size rather than the incoming one's.
                    Crossfade(
                        targetState = icon to iconSize,
                        animationSpec = tween(durationMillis = 200),
                        label = "pillSegmentIcon",
                    ) { (currentIcon, currentSize) ->
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(currentSize),
                        )
                    }
                    if (trailingLabel != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = trailingLabel,
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (label != null) {
                Text(
                    text = label,
                    color = tint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}






@Composable
private fun BottomGlyph(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    label: String? = null,
    iconSize: Dp = BOTTOM_ICON_SIZE,
    /**
     * Shortest gap between taps that both reach [onClick]. A tap inside the
     * window of the last one is dropped whole — haptic included, so a swallowed
     * tap doesn't buzz as though something happened. The default lets every tap
     * through: only the glyphs whose work is too heavy to repeat at finger speed
     * ask for a window.
     */
    tapWindowMs: Long = 0L,
    /** Dodged rather than added, for the bottom row's glyphs — see [vibrantChrome]. */
    vibrant: Boolean = false,
) {
    val haptics = rememberHaptics()
    // Read only from the click handler, never during composition, so writing it
    // costs no recomposition. Starts a full window in the past so the first tap
    // is never the one that gets swallowed.
    val lastTap = remember { mutableLongStateOf(-tapWindowMs) }
    val tileChrome = LocalPlayerChrome.current
    // The lit tile dodged like the glyphs around it, so lyrics or the queue
    // being up reads in the cover's colour rather than as a white patch.
    val tileDodged = tileChrome && vibrant && BOTTOM_ROW_BRIGHT
    val tileColor = if (tileDodged) brightRowInk() else chromeWhite(BOTTOM_ACTIVE_TILE_ALPHA)
    Box(
        modifier = Modifier
            .size(BOTTOM_ACTION_SIZE)
            // Lit, Apple's way: a rounded tile with the glyph cut out of it, the
            // artwork showing through. The glyph is drawn first and the tile over
            // it with SrcOut, which keeps the tile only where the glyph is not.
            // Offscreen only while lit, so an unlit glyph costs no extra layer.
            // The blend's own layer serves as that offscreen when it is on.
            .then(
                if (highlighted) {
                    (
                        if (tileDodged) {
                            Modifier.vibrantChrome(true)
                        } else if (tileChrome) {
                            Modifier.additiveChrome(true)
                        } else {
                            Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        }
                    )
                        .drawWithContent {
                            drawContent()
                            val side = BOTTOM_ACTIVE_TILE_SIZE.toPx()
                            val corner = BOTTOM_ACTIVE_TILE_CORNER.toPx()
                            drawRoundRect(
                                color = tileColor,
                                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                                size = Size(side, side),
                                cornerRadius = CornerRadius(corner, corner),
                                blendMode = BlendMode.SrcOut,
                            )
                        }
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val now = uptimeMillis()
                if (now - lastTap.longValue >= tapWindowMs) {
                    lastTap.longValue = now
                    haptics.play(haptic)
                    onClick()
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Not while lit: the tile's cut-out needs the glyph drawn plainly in the
        // layer above, which is blended as a whole.
        val chrome = LocalPlayerChrome.current && !highlighted
        val tint = when {
            vibrant && chrome -> brightRowInk()
            highlighted -> Color.White
            else -> chromeWhite(0.75f)
        }
        val glyphChrome = if (vibrant) Modifier.vibrantChrome(chrome) else Modifier.additiveChrome(chrome)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize).then(glyphChrome),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = glyphChrome,
            )
        }
    }
}

/** A credit that links somewhere, when [browseId] is known. */
internal fun Modifier.opensPage(browseId: String?, onOpen: (String) -> Unit): Modifier =
    if (browseId == null) {
        this
    } else {
        clip(LinkPressShape).clickable { onOpen(browseId) }
    }

/**
 * The press shape of anything in the player that links somewhere.
 *
 * Shared with [opensPage] so a credited name inside the artist line and a whole
 * title row above it answer a finger the same way, rather than one drawing a
 * rounded ripple and the other a square one.
 */
private val LinkPressShape = RoundedCornerShape(6.dp)

/** How fast the title/artist marquee crawls — unhurried, not a ticker. */
private const val MARQUEE_DP_PER_SEC = 26f

/** Clear air between the tail of the line and the copy chasing it round. */
private val MARQUEE_GAP = 48.dp

/** How long a line sits back at its start before the next pass — the "5 seconds" rest. */
private const val MARQUEE_REST_MS = 5_000L

/** Artist's head start is ceded to the title when both are scrolling, so they don't start as one block. */
internal const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

/**
 * A single line of text that scrolls in place, only when it is too long for
 * [modifier]'s width to show in full.
 *
 * Idle text never animates — the scroll only kicks in once measurement proves
 * an ellipsis would otherwise be needed. When it does, the line is drawn twice
 * with [MARQUEE_GAP] between the copies and the pair is crawled leftwards by
 * exactly one copy-plus-gap: the trailing copy chases the leading one in from
 * the right and lands precisely where it started, so the offset reset at the
 * end of the pass falls under a copy already in position and cannot be seen.
 * The line therefore only ever travels one way — right to left, round and back
 * to its resting place — rather than bouncing back the way it came.
 *
 * A pass is: wait [startDelayMillis] (used to stagger the artist line behind
 * the title), crawl one full loop, then rest [MARQUEE_REST_MS] at the start
 * before going again. [onOverflowChange] reports whether this line is scrolling
 * at all, so a sibling line can decide whether it needs to stagger behind it.
 *
 * A line too long for its width fades out at its end rather than stopping at
 * a hard edge, as Apple's does, and once it is crawling at its start too — see
 * [marqueeFade]. [leading] and [trailing] sit either side of the line,
 * outside the crawl — the explicit badge goes before the title.
 *
 * With [enabled] false the line holds still at its start — no copies, no
 * animation, nothing left running off screen — and only fades at its end.
 */
@Composable
internal fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayMillis: Long = 0L,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).clipToBounds()) {
            val maxWidthPx = constraints.maxWidth
            val layout = remember(text, style, maxWidthPx) {
                textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
            }
            val overflowing = layout.size.width > maxWidthPx
            val crawls = enabled && overflowing
            LaunchedEffect(crawls) { onOverflowChange(crawls) }

            // One whole copy plus the gap behind it: that is the distance at
            // which the second copy is sitting exactly where the first was.
            val travelPx = if (crawls) {
                layout.size.width + with(density) { MARQUEE_GAP.roundToPx() }
            } else {
                0
            }

            val offsetX = remember { Animatable(0f) }
            LaunchedEffect(text, travelPx, startDelayMillis) {
                offsetX.snapTo(0f)
                if (travelPx <= 0) return@LaunchedEffect
                val pxPerMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
                val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
                delay(startDelayMillis)
                while (true) {
                    offsetX.animateTo(-travelPx.toFloat(), tween(scrollMs, easing = LinearEasing))
                    // Invisible: the trailing copy has arrived at the leading
                    // one's starting mark, so the line is already back where
                    // this puts it.
                    offsetX.snapTo(0f)
                    delay(MARQUEE_REST_MS)
                }
            }

            Row(
                // Measured unbounded so the copies actually lay out at their
                // full width, wider than the clipped box around them — bounded,
                // the text is truncated during its own measurement and sliding
                // it sideways just moves an already-cut string.
                modifier = Modifier
                    .marqueeFade(overflowing, travelPx) { offsetX.value }
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarqueeLine(text = text, style = style, color = color)
                if (crawls) {
                    Spacer(Modifier.width(MARQUEE_GAP))
                    MarqueeLine(text = text, style = style, color = color)
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

/**
 * A marquee line's fades, where it [overflows] its box: out at its end always,
 * and at its start while it is away from its resting place — in over the first
 * fade's width of a pass of [travelPx] and back out over the last, by when the
 * copy chasing it has arrived where it started. [offset] is read as it draws.
 */
private fun Modifier.marqueeFade(overflows: Boolean, travelPx: Int, offset: () -> Float): Modifier =
    if (!overflows) {
        this
    } else {
        composed {
            val fadePx = with(LocalDensity.current) { LINE_FADE.toPx() }
            fadingEdges(
                LINE_FADE,
                start = {
                    val moved = -offset()
                    minOf(moved, travelPx - moved) / fadePx
                },
                end = { 1f },
            )
        }
    }

/** One copy of a marquee's line, laid out at its full width rather than clipped. */
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



/**
 * Measure a child wider than its slot by [gutter] on each side and place it back
 * over that margin, still reporting the original width to the parent.
 *
 * The lists are the only things in the player you can scroll, and the side
 * padding left a strip of bare sheet down each edge. A finger that drifted into
 * one scrolled nothing and closed the player instead. Matching content padding
 * puts every row back exactly where it was drawn, so this is invisible.
 */
internal fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
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

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}






