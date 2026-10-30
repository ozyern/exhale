/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.ozyern.exhale.ui.player

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.ozyern.exhale.ui.utils.fadingMarquee
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.ozyern.exhale.LocalPlayerConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.EnableHapticFeedbackKey
import com.ozyern.exhale.constants.MiniPlayerHeight
import com.ozyern.exhale.constants.MiniPlayerPillHorizontalInset
import com.ozyern.exhale.constants.ConvxChromeExtraInset
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.ui.component.pressScaleContainer
import com.ozyern.exhale.ui.component.rememberPinnedArtworkRequest
import com.ozyern.exhale.ui.utils.safeHorizontalChromeInset
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.together.TogetherSessionState
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.utils.rememberHaptic
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxScope


@Composable
fun SwipeableMiniPlayerBox(
    modifier: Modifier = Modifier,
    swipeSensitivity: Float,
    swipeThumbnail: Boolean,
    playerConnection: PlayerConnection,
    layoutDirection: LayoutDirection,
    coroutineScope: CoroutineScope,
    pureBlack: Boolean = false,
    useLegacyBackground: Boolean = false,
    // The pill draws shorter than its slot; the slot itself keeps [MiniPlayerHeight] because the
    // sheet's collapsed bound is captured once and cannot change per route. See
    // [com.ozyern.exhale.constants.CompactMiniPlayerHeight].
    compact: Boolean = false,
    content: @Composable (Float) -> Unit
) {
    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun calculateAutoSwipeThreshold(swipeSensitivity: Float): Int {
        return (600 / (1f + kotlin.math.exp(-(-11.44748 * swipeSensitivity + 9.04945)))).roundToInt()
    }
    val autoSwipeThreshold = calculateAutoSwipeThreshold(swipeSensitivity)

    // Symmetric, and unioned with the display cutout — see `safeHorizontalChromeInset`. This
    // replaced a `windowInsetsPadding(systemBars.only(Horizontal))`, which was wrong twice over:
    // it ignored the cutout entirely (so the pill slid under a landscape "Fluid Cloud"), and it
    // padded each edge independently while the morph that has to land on this pill uses ONE
    // symmetric inset.
    val safeChromeInset = safeHorizontalChromeInset()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .let { baseModifier ->
                if (useLegacyBackground) {
                    baseModifier
                        .padding(horizontal = safeChromeInset)
                        .background(
                            if (pureBlack) Color.Black
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                } else {
                    // Must match BottomSheet's Dynamic-Island morph inset, or the full player
                    // shrinks into a rectangle that doesn't line up with this pill. The host
                    // composes the morph target from exactly these two terms.
                    baseModifier.padding(
                        horizontal = safeChromeInset + MiniPlayerPillHorizontalInset +
                            if (com.ozyern.exhale.ui.component.LocalConvxChrome.current) ConvxChromeExtraInset else 0.dp,
                    )
                }
            }
            .let { baseModifier ->
                if (swipeThumbnail) {
                    baseModifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragStartTime = System.currentTimeMillis()
                                totalDragDistance = 0f
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = animationSpec
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val adjustedDragAmount =
                                    if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                                val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                                val canSkipNext = playerConnection.player.nextMediaItemIndex != -1
                                val allowLeft = adjustedDragAmount < 0 && canSkipNext
                                val allowRight = adjustedDragAmount > 0 && canSkipPrevious
                                if (allowLeft || allowRight) {
                                    totalDragDistance += kotlin.math.abs(adjustedDragAmount)
                                    coroutineScope.launch {
                                        offsetXAnimatable.snapTo(offsetXAnimatable.value + adjustedDragAmount)
                                    }
                                }
                            },
                            onDragEnd = {
                                val dragDuration = System.currentTimeMillis() - dragStartTime
                                val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                                val currentOffset = offsetXAnimatable.value

                                val minDistanceThreshold = 50f
                                val velocityThreshold = (swipeSensitivity * -8.25f) + 8.5f

                                val shouldChangeSong = (
                                        kotlin.math.abs(currentOffset) > minDistanceThreshold &&
                                                velocity > velocityThreshold
                                        ) || (kotlin.math.abs(currentOffset) > autoSwipeThreshold)

                                if (shouldChangeSong) {
                                    val isRightSwipe = currentOffset > 0
                                    val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                                    val canSkipNext = playerConnection.player.nextMediaItemIndex != -1

                                    if (isRightSwipe && canSkipPrevious) {
                                        playerConnection.seekToPrevious()
                                        if (com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.isRunning()) {
                                            try { com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.restart() } catch (_: Exception) {}
                                        }
                                    } else if (!isRightSwipe && canSkipNext) {
                                        playerConnection.seekToNext()
                                        if (com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.isRunning()) {
                                            try { com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.restart() } catch (_: Exception) {}
                                        }
                                    }
                                }

                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = animationSpec
                                    )
                                }
                            }
                        )
                    }
                } else {
                    baseModifier
                }
            },
        // Centres the pill in its slot, which only matters when it is shorter than the slot. The
        // swipe indicators below place themselves with an explicit `align`, so nothing else in
        // here changes.
        contentAlignment = Alignment.Center,
    ) {
        content(offsetXAnimatable.value)

        // Visual indicator
        if (offsetXAnimatable.value.absoluteValue > 50f) {
            Box(
                modifier = Modifier
                    .align(if (offsetXAnimatable.value > 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (offsetXAnimatable.value > 0) R.drawable.skip_previous else R.drawable.skip_next
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(
                        alpha = (offsetXAnimatable.value.absoluteValue / autoSwipeThreshold).coerceIn(0f, 1f)
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun RowScope.MiniPlayerInfo(
    mediaMetadata: MediaMetadata,
    // Drops one type step so two lines still fit the slim pill without the artist clipping.
    compact: Boolean = false,
) {
    val convx = com.ozyern.exhale.ui.component.LocalConvxChrome.current
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = mediaMetadata.title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "title"
        ) { title ->
            // Convx's pill: the title bold at reading size, the artist under it in the regular
            // weight — the two told apart by weight, not by shrinking the artist to a caption.
            Text(
                text = title,
                fontSize = if (convx) 17.sp else 16.sp,
                lineHeight = if (convx) 21.sp else 20.sp,
                fontWeight = if (convx) FontWeight.SemiBold else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                // The glass is nearly clear, as Convx's is; a faint shadow keeps the line off a bright
                // cover passing beneath without reading as an effect.
                style = androidx.compose.ui.text.TextStyle(shadow = MiniTextShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fadingMarquee()
            )
        }

        Spacer(Modifier.height(1.dp))

        AnimatedContent(
            targetState = mediaMetadata.artists,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "artist"
        ) { artists ->
            Text(
                text = artists.joinToString { it.name },
                fontSize = if (convx) 13.sp else 14.sp,
                lineHeight = if (convx) 16.sp else 17.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                style = androidx.compose.ui.text.TextStyle(shadow = MiniTextShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Ellipsis, not marquee. Two independently scrolling lines 1dp apart is two
                // things moving in the corner of the eye at all times, and the artist is the
                // line you least need to read to the end — the screenshot that prompted this was
                // caught mid-loop, showing the tail of one pass, a gap, and the head of the next,
                // which reads as a layout fault rather than as a long name.
                //
                // Deliberately NOT clickable. This line used to open the artist, which sounds
                // harmless until you notice the text sits in a weight(1f) column: its tap target
                // was most of the pill's width, so the ordinary gesture — tap the capsule to open
                // the player — landed on the artist instead and threw you onto a different screen.
                // The pill has one job at this size. Artist navigation lives in the full player,
                // where the name is its own line with room around it.
            )
        }
    }
}

/**
 * The music video the person was watching when they closed the player, small, over the cover:
 * the video carries on in the dock rather than stopping when the player goes away. It fades in on
 * its first frame, so until then (or with no video) the cover underneath is what shows.
 */
@Composable
internal fun DockedVideo(songId: String?, modifier: Modifier = Modifier, visible: () -> Boolean = { true }) {
    val docked = SharedMusicVideo.docked ?: return
    if (songId == null || docked.songId != songId) return
    // Several pills carry the song (the sheet's and the dock's, open and inline) and only one is on
    // screen at a time. Only that one takes the picture; a hidden one would take it and show nothing.
    val isVisible by androidx.compose.runtime.rememberUpdatedState(visible)
    val shown by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { isVisible() } }
    if (!shown) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    MusicVideoSurface(
        stream = docked.found.stream,
        positionMs = { playerConnection.player.currentPosition + docked.found.offsetMs },
        isPlaying = isPlaying,
        cropToFill = true,
        // A dead link in the dock just goes back to the cover.
        onError = { SharedMusicVideo.docked = null },
        priority = 0,
        modifier = modifier,
    )
}

@Composable
private fun MiniPlayerArtwork(
    mediaMetadata: MediaMetadata?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    // A rounded square, which is what a cover is.
    //
    // It was a disc, on the argument that at 48dp an album's corners carry nothing and the round
    // shape marks out which object at the bottom of the screen is the record. In a capsule that is
    // itself fully round, a disc reads as a second, smaller pill rather than as artwork - and the
    // pill now appears over settings pages too, where there is no dock of round glyphs for it to
    // play off. Apple shows the cover as the cover.
    // Convx rounds its cover at 22% of its size; the classic pill keeps 10dp.
    val shape = RoundedCornerShape(if (com.ozyern.exhale.ui.component.LocalConvxChrome.current) size * 0.22f else 10.dp)
    // This is where the cover is lifted from when the player opens, and where it lands on close.
    val flight = LocalCoverFlight.current
    val flightOwner = remember { Any() }
    if (flight != null) {
        DisposableEffect(flight) { onDispose { flight.releaseMini(flightOwner) } }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(
                flight?.run {
                    // The sheet's pill: on screen whenever the dock isn't drawing the song itself.
                    Modifier.reportMini(
                        flightOwner,
                        corner = 10.dp,
                        slot = "sheetPill",
                        visible = { com.ozyern.exhale.ui.component.foldingdock.FoldingDockMotion.fold < 0.5f },
                    )
                } ?: Modifier,
            )
            .size(size)
            // A real shadow under the art. The pill is a pane of glass and the row on it was
            // uniformly flat — every element sitting at exactly the same depth is what made the
            // whole thing read as a printed strip rather than as objects on a surface. The art
            // is the one thing in the row that is an image, so it is the one that should have
            // physical thickness.
            .shadow(
                elevation = 5.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // A light rim, not an `outline` stroke. The pill is a pane of glass and everything
            // on it is lit from above; a flat grey border drawn at full strength on all four
            // sides is the one element that read as a Material list thumbnail pasted onto it.
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)),
                ),
                shape = shape,
            )
    ) {
        val thumbnailUrl = mediaMetadata?.thumbnailUrl
        if (thumbnailUrl != null) {
            AsyncImage(
                // Pinned to the memory cache under the URL so the artwork does not blink
                // when the player morphs between this pill and the nav bar capsule.
                model = rememberPinnedArtworkRequest(thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.exhale),
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f)
            )
        }

        DockedVideo(
            mediaMetadata?.id,
            Modifier.fillMaxSize(),
            visible = { com.ozyern.exhale.ui.component.foldingdock.FoldingDockMotion.fold < 0.5f },
        )

        // Buffering veil over the art rather than a ring around it: the state belongs to the
        // track, so it reads better sitting on the track than orbiting it.
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
            ) {
                LoadingRing(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    stroke = 2.dp,
                )
            }
        }
    }
}

/**
 * The State-A mini player: the wide pill that floats above the dock.
 *
 * Three decisions carry it, all of them about hierarchy:
 *
 *  * **Artwork is a rounded square**, like every other piece of album art in the app. It used to
 *    be a disc wearing a circular progress ring, so the actual thumbnail got 36dp of a 44dp box
 *    and was cropped to a circle - the least legible way to show a square image.
 *  * **Nothing inside the row navigates.** Tapping anywhere on the pill opens the player, full
 *    stop. The artist line used to be a link, but it spans the whole middle of the capsule, so
 *    the tap most people make was being answered by a jump to the artist screen. Artist
 *    navigation belongs in the full player, and lives there instead.
 *  * **Play/pause is the loudest thing in the pill**, because it is the one control the pill
 *    exists for. See [MiniPlayerPlayPauseButton].
 *
 * Progress is a hairline along the bottom rather than a ring around the artwork: more legible,
 * out of the way of the thing it describes, and drawn rather than laid out, so ticking it forward
 * costs one draw pass and no recomposition of the row above it.
 *
 * @param compact the slim layout for surfaces whose bottom row is the search bar. Everything
 *   scales down one step and the like button drops out - at that width it was the least-used
 *   control competing with the most-used one for the same edge of the pill.
 */
@Composable
fun NewMiniPlayerContent(
    pureBlack: Boolean,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    compact: Boolean = false,
) {
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val togetherSessionState by playerConnection.service.togetherSessionState.collectAsState()

    val isLoading = playbackState == Player.STATE_BUFFERING

    val progressColor = MaterialTheme.colorScheme.primary
    val progressTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    // Convx 2.0.4: a 38dp cover in a 52dp pill (measured off its debug build).
    val convx = com.ozyern.exhale.ui.component.LocalConvxChrome.current
    val artworkSize = if (convx) 38.dp else if (compact) 42.dp else 48.dp
    // 34dp, not 40. In a 56dp pill a 40dp button is over two thirds of the height, filled with a
    // solid accent — it stopped being the loudest thing in the row and became the only thing in
    // it. The wide pill keeps 48dp because it has 64dp to spend and a like button to out-rank.
    val buttonSize = if (compact) 34.dp else 48.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Progress first, so it is *under* the row rather than painted across it.
        //
        // It used to be the last child, inset 22dp from each end and lifted 7dp off the bottom.
        // On a 64dp pill that puts the line at y=54..57 — squarely inside the 48dp artwork's own
        // band, which starts at y=8 — and being drawn last put it on top. The visible result was
        // a short accent-coloured stub apparently glued to the bottom-left corner of the album
        // art, which is what a progress bar looks like when it is a few percent through a song
        // and has been laid over the one opaque object in the row. Nothing about it read as
        // progress.
        //
        // Now it sits in the 8dp margin *below* the artwork and behind everything. The end inset
        // is what the capsule's own curve allows at that height: 3dp up from the bottom of a 32dp
        // radius leaves ±13.5dp of chord about each end centre, so anything less than ~19dp of
        // inset gets clipped to a stub by the pill itself.
        // The progress is the ring around play and pause now; a hairline as well said it twice.

        // In the wide layout the pill is the bottom bar's transport, as Apple Music's is on a large
        // screen: back, play and forward lead, the song follows. The tabs are on the rail there,
        // so the bar has the width for them.
        val wideTransport = com.ozyern.exhale.ui.component.LocalWideChrome.current
        val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
        val canSkipNext by playerConnection.canSkipNext.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (convx) 12.dp else 8.dp),
        ) {
            if (wideTransport) {
                val transportLatestPosition by rememberUpdatedState(position)
                val transportLatestDuration by rememberUpdatedState(duration)
                MiniTransportGlyph(
                    icon = R.drawable.skip_previous,
                    label = stringResource(R.string.previous),
                    enabled = canSkipPrevious,
                    onClick = { playerConnection.seekToPrevious() },
                )
                com.ozyern.exhale.ui.component.RingPlayPauseButton(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    progress = {
                        if (transportLatestDuration > 0) transportLatestPosition.toFloat() / transportLatestDuration else 0f
                    },
                    onClick = { playerConnection.player.togglePlayPause() },
                    size = if (compact) 36.dp else 44.dp,
                )
                MiniTransportGlyph(
                    icon = R.drawable.skip_next,
                    label = stringResource(R.string.next),
                    enabled = canSkipNext,
                    onClick = { playerConnection.seekToNext() },
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            MiniPlayerArtwork(
                mediaMetadata = mediaMetadata,
                isLoading = isLoading,
                size = artworkSize,
            )

            Spacer(modifier = Modifier.width(if (convx) 10.dp else 12.dp))

            mediaMetadata?.let {
                MiniPlayerInfo(
                    mediaMetadata = it,
                    compact = compact,
                )
            } ?: Spacer(Modifier.weight(1f))

            if (togetherSessionState !is TogetherSessionState.Idle) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.all_inclusive),
                        contentDescription = stringResource(R.string.music_together),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Convx's pill: play or pause, then forward — solid glyphs straight on the glass, with
            // no heart, no ring and no disc. Play is the loudest thing in the row by size alone;
            // forward is a step quieter and takes a little of the accent. The wide transport
            // already leads with its own controls.
            if (!wideTransport) {
                val ink = MaterialTheme.colorScheme.onSurface
                // Convx: SF Symbols' play/pause.fill and forward.fill, white, at its control sizes.
                MiniGlyphButton(
                    icon = when {
                        convx && isPlaying -> R.drawable.sf_pause_fill
                        convx -> R.drawable.sf_play_fill
                        isPlaying -> R.drawable.pause
                        else -> R.drawable.play
                    },
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    tint = (if (convx) Color.White else ink).copy(alpha = 0.92f),
                    onClick = { playerConnection.player.togglePlayPause() },
                    glyphSize = if (convx) 22.dp else 30.dp,
                )
                MiniGlyphButton(
                    icon = if (convx) R.drawable.sf_forward_fill else R.drawable.forward_fill,
                    contentDescription = stringResource(R.string.next),
                    tint = (if (convx) Color.White else androidx.compose.ui.graphics.lerp(ink, MaterialTheme.colorScheme.primary, 0.45f))
                        .copy(alpha = if (canSkipNext) 0.9f else 0.35f),
                    onClick = { if (canSkipNext) playerConnection.seekToNext() },
                    glyphSize = 24.dp,
                )
            }
        }

    }
}

/**
 * A bare transport glyph on the mini player, as Convx's pill draws them: a solid play/pause or
 * forward with no ring or disc around it, on a full 44dp target. The pill is the surface; a second
 * shape inside it for each control was what made the row read as a toolbar.
 */
@Composable
internal fun MiniGlyphButton(
    icon: Int,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    glyphSize: Dp = 26.dp,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A press sinks the glyph a little, the only feedback a bare glyph can give.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "miniGlyphPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.55f, stiffness = 700f), initialScale = 0.6f) + fadeIn(tween(120))) togetherWith
                    (scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(100)))
            },
            label = "miniGlyph",
        ) { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(glyphSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

/** Back or forward in the wide transport: a quiet glyph with a full 44dp target. */
@Composable
private fun MiniTransportGlyph(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.9f else 0.35f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The hairline along the bottom of the pill.
 *
 * Drawn, not laid out: ticking it forward every second costs one draw pass and never recomposes
 * or re-measures the row above it. See the call site for why it is inset the way it is.
 */
@Composable
private fun BoxScope.MiniPlayerProgress(
    position: Long,
    duration: Long,
    compact: Boolean,
    trackColor: Color,
    progressColor: Color,
) {
    Canvas(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Inset far enough in from both ends that the capsule's own curve never clips the
            // line into a stub, and lifted far enough off the bottom that the rounded cap does
            // not ride the rim. At 2dp up on a 28dp radius the chord is only ~25dp wide of the
            // 39dp the old inset assumed, so both ends were being shaved by the pill itself and
            // the fill looked like a stray mark rather than a progress bar.
            .padding(
                start = if (compact) 22.dp else 26.dp,
                end = if (compact) 22.dp else 26.dp,
                bottom = if (compact) 4.dp else 5.dp,
            )
            .height(3.dp),
    ) {
        val fraction =
            if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val stroke = size.height
        val y = size.height / 2f
        drawLine(
            color = trackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (fraction > 0f) {
            // Fades in from the left rather than starting at full strength, so a track that has
            // barely begun does not put a hard accent dot under the artwork.
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(progressColor.copy(alpha = 0.45f), progressColor),
                    startX = 0f,
                    endX = (size.width * fraction).coerceAtLeast(1f),
                ),
                start = Offset(0f, y),
                end = Offset(size.width * fraction, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Under the mini player's two lines: soft, low, just enough to hold them off a bright cover. */
private val MiniTextShadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
    blurRadius = 6f,
)
