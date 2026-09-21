@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.player

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.ActionPromptDialog
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.BottomSheetPageState
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.bottomSheetDraggable
import com.ozyern.exhale.ui.menu.PlayerMenu
import com.ozyern.exhale.ui.utils.ShowMediaInfo
import com.ozyern.exhale.utils.makeTimeString
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt


/**
 * The top of the queue, as Apple Music's "Playing Next" lays it out: what is playing as one compact
 * row, then the queue's switches as labelled capsules that fill with the accent when they are on.
 *
 * It used to be three stacked plates — a status pill holding a lock, a menu and a count, a row of
 * Material toggle blocks with no labels, and a "Continue Playing" heading with a rule under it — which
 * is a settings panel, not the top of a list of songs. The count now lives in the "Up Next" heading
 * right above the songs it counts.
 */
@Composable
fun CurrentSongHeader(
    sheetState: BottomSheetState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    locked: Boolean,
    songCount: Int,
    queueDuration: Int,
    infiniteQueueEnabled: Boolean,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onToggleLike: () -> Unit,
    onMenuClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onLockClick: () -> Unit,
    onInfiniteQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .bottomSheetDraggable(sheetState)
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(onBackgroundColor.copy(alpha = 0.28f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(onBackgroundColor.copy(alpha = 0.06f))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor
                )
                val artists = mediaMetadata?.artists?.joinToString(", ") { it.name }.orEmpty()
                if (artists.isNotBlank()) {
                    Text(
                        text = artists,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onBackgroundColor.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            HeaderRoundButton(
                icon = if (mediaMetadata?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                description = stringResource(R.string.action_like),
                tint = if (mediaMetadata?.liked == true) MaterialTheme.colorScheme.primary else onBackgroundColor,
                wash = onBackgroundColor,
                onClick = onToggleLike,
            )
            Spacer(Modifier.width(8.dp))
            HeaderRoundButton(
                icon = R.drawable.more_horiz,
                description = null,
                tint = onBackgroundColor,
                wash = onBackgroundColor,
                onClick = onMenuClick,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueSwitch(
                icon = R.drawable.shuffle,
                label = "Shuffle",
                on = shuffleModeEnabled,
                foreground = onBackgroundColor,
                onClick = onShuffleClick,
                modifier = Modifier.weight(1f),
            )
            QueueSwitch(
                icon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                    else -> R.drawable.repeat
                },
                label = if (repeatMode == Player.REPEAT_MODE_ONE) "Repeat 1" else "Repeat",
                on = repeatMode != Player.REPEAT_MODE_OFF,
                foreground = onBackgroundColor,
                onClick = onRepeatClick,
                modifier = Modifier.weight(1f),
            )
            QueueSwitch(
                icon = R.drawable.all_inclusive,
                label = "Autoplay",
                on = infiniteQueueEnabled,
                foreground = onBackgroundColor,
                onClick = onInfiniteQueueClick,
                modifier = Modifier.weight(1f),
            )
            // Reordering and swipe-to-remove are off while this is locked; unlocking is the list's "Edit".
            QueueSwitch(
                icon = if (locked) R.drawable.lock else R.drawable.lock_open,
                label = if (locked) "Edit" else "Done",
                on = !locked,
                foreground = onBackgroundColor,
                onClick = onLockClick,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(
            visible = infiniteQueueEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = stringResource(R.string.queue_autoplaying_similar),
                style = MaterialTheme.typography.bodySmall,
                color = onBackgroundColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }
    }
}

/** A small round button on a faint wash of the page's own foreground colour. */
@Composable
private fun HeaderRoundButton(
    icon: Int,
    description: String?,
    tint: Color,
    wash: Color,
    onClick: () -> Unit,
    size: Dp = 36.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(wash.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * One of the queue's switches, labelled: a capsule that fills with the accent when on. The label is
 * the point — a row of bare glyphs made "which of these is on" a thing you had to decode.
 */
@Composable
private fun QueueSwitch(
    icon: Int,
    label: String,
    on: Boolean,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Glass on the song's colour: faint when off, brighter when on — Apple's queue buttons, which never
    // turn a solid accent because the row sits on artwork that already has a colour of its own.
    val fill by animateColorAsState(foreground.copy(alpha = if (on) 0.34f else 0.12f), tween(220), label = "queueSwitchFill")
    val ink by animateColorAsState(if (on) foreground else foreground.copy(alpha = 0.72f), tween(220), label = "queueSwitchInk")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "queueSwitchPress")
    Row(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
    }
}

/**
 * Milliseconds until the sleep timer pauses playback.
 *
 * A duration timer answers this directly. A song counter has to be estimated: the rest of the
 * track playing now, plus the length of each track that will follow it. That estimate is what the
 * collapsed player bars count down, and it is worth making — "3 songs" is the setting people
 * choose, but "11:42" is the thing they want to know when they glance at it half asleep.
 *
 * It walks the timeline with the player's own repeat mode, so repeat-one gives the current track
 * back N times, which is exactly what will happen.
 */
internal fun sleepTimerMillisLeft(playerConnection: PlayerConnection): Long {
    val timer = playerConnection.service.sleepTimer

    if (timer.triggerTime != -1L) {
        return timer.triggerTime - System.currentTimeMillis()
    }

    return upcomingSongsMillis(playerConnection, timer.songsRemaining)
}

/**
 * Milliseconds the next [songs] tracks will take, counting the one playing now.
 *
 * Walks the timeline with the player's own repeat mode, so repeat-one gives the current track back
 * N times, which is exactly what will happen, and stops at the end of the queue rather than
 * pretending there is more music than there is.
 */
internal fun upcomingSongsMillis(playerConnection: PlayerConnection, songs: Int): Long {
    if (songs <= 0) return 0L

    val player = playerConnection.player
    // `duration` is TIME_UNSET until the track is prepared, and TIME_UNSET is a very large
    // negative number rather than an error.
    var total = (player.duration - player.currentPosition).coerceAtLeast(0L)
    if (songs == 1) return total

    val timeline = player.currentTimeline
    val window = Timeline.Window()
    var index = player.currentMediaItemIndex

    repeat(songs - 1) {
        index = timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
        if (index == C.INDEX_UNSET) return total
        total += (timeline.getWindow(index, window).mediaItem.metadata?.duration ?: 0) * 1000L
    }

    return total
}

/** Wall-clock time, [millis] from now, in the reader's own short format. */
private fun clockTimeAfter(millis: Long): String =
    LocalTime.now()
        .plusSeconds((millis / 1000L).coerceAtLeast(0L))
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/**
 * The sleep timer.
 *
 * A dial and nine pills, and nothing else to do.
 *
 * Three versions came before this. The first two opened on a mode switch and a slider, which made
 * you set a number and then press Start -- three interactions and a confirmation to answer a
 * question you already knew the answer to when you reached for the menu. The third cut that to one
 * tap but paid for it with a scrolling column of rows: every option the same shape and the same
 * size, so nothing was findable at a glance, and the list was taller than the phone.
 *
 * This one fits on screen at once. The choices are a grid, which is what a set of nine fixed values
 * actually is -- you find "30 min" by where it sits, the way you find a key on a keypad, instead of
 * reading down a list for it. Each pill carries the clock time it lands on, because you are not
 * really choosing "45", you are choosing to stop at about a quarter to twelve, and the only version
 * of this control that lets you decide that directly is one that has already done the arithmetic.
 *
 * The dial is the part the third version had no answer for. Remaining time on its own cannot tell
 * you where you are: eleven minutes left is nearly over on a fifteen-minute timer and barely
 * started on a two-hour one. The ring drains, so a glance at it is enough, and +15 sits under it
 * because "I am still awake" is the one thing a running timer needs that costs a whole trip back
 * through the menu otherwise.
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirmMinutes: (Int) -> Unit,
    onConfirmSongs: (Int) -> Unit,
    onCancelTimer: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current
    val timer = playerConnection?.service?.sleepTimer

    val runningSongs = timer?.songsRemaining ?: 0
    val totalSongs = timer?.totalSongs ?: 0
    val runningTrigger = timer?.triggerTime ?: -1L
    val runningStart = timer?.startTime ?: -1L
    val timedMode = runningTrigger != -1L
    val isRunning = timedMode || runningSongs > 0

    // Re-read on a tick, so the times on these pills are the times they land on *now* and not the
    // ones they landed on when the sheet opened. Also what turns the dial into a countdown.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    val leftMillis = remember(now, runningSongs, runningTrigger, playerConnection) {
        if (isRunning && playerConnection != null) sleepTimerMillisLeft(playerConnection) else 0L
    }

    // How much of the timer is still ahead. A song counter has no clock to measure against, so it
    // measures itself in songs, which is the unit it was set in anyway.
    val remainingFraction = when {
        timedMode && runningStart != -1L && runningTrigger > runningStart ->
            (runningTrigger - now).toFloat() / (runningTrigger - runningStart).toFloat()
        runningSongs > 0 && totalSongs > 0 -> runningSongs.toFloat() / totalSongs.toFloat()
        else -> 0f
    }.coerceIn(0f, 1f)

    DefaultDialog(onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SleepTimerDial(
                running = isRunning,
                remainingFraction = remainingFraction,
                label = when {
                    runningSongs > 0 -> runningSongs.toString()
                    isRunning -> makeTimeString(leftMillis)
                    else -> null
                },
                caption = if (runningSongs > 0) {
                    pluralStringResource(R.plurals.n_song, runningSongs, runningSongs)
                } else {
                    null
                },
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (isRunning) {
                    stringResource(R.string.sleep_timer_stops_at, clockTimeAfter(leftMillis))
                } else {
                    stringResource(R.string.sleep_timer)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = if (isRunning) {
                    stringResource(R.string.sleep_timer_running_desc)
                } else {
                    stringResource(R.string.sleep_timer_idle_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (isRunning) {
                Spacer(Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Only offered on a clock timer. Adding minutes to a song counter would have
                    // to silently change which timer you set, and a control that answers a
                    // different question than the one you asked is worse than no control.
                    if (timedMode) {
                        FilledTonalButton(
                            onClick = { timer?.extend(SleepTimerExtendMinutes) },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.sleep_timer_extend,
                                    SleepTimerExtendMinutes,
                                ),
                                maxLines = 1,
                            )
                        }
                    }

                    TextButton(
                        onClick = onCancelTimer,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer_stop),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        SleepTimerSectionLabel(stringResource(R.string.sleep_timer_after_time))

        SleepTimerMinuteOptions.chunked(SleepTimerColumns).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(SleepTimerGridGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SleepTimerGridGap),
            ) {
                row.forEach { minutes ->
                    SleepTimerChip(
                        label = stringResource(R.string.sleep_timer_minutes_short, minutes),
                        clock = remember(now, minutes) { clockTimeAfter(minutes * 60_000L) },
                        onClick = { onConfirmMinutes(minutes) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        SleepTimerSectionLabel(stringResource(R.string.sleep_timer_after_songs))

        Row(
            horizontalArrangement = Arrangement.spacedBy(SleepTimerGridGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SleepTimerSongOptions.forEach { count ->
                // An estimate needs a queue to estimate from. Without one the pill still works --
                // it just does not make a promise it cannot keep.
                val estimate = remember(now, count, playerConnection) {
                    playerConnection?.let { upcomingSongsMillis(it, count) } ?: 0L
                }
                SleepTimerChip(
                    label = if (count == 1) {
                        stringResource(R.string.sleep_timer_this_song)
                    } else {
                        pluralStringResource(R.plurals.n_song, count, count)
                    },
                    clock = if (estimate > 0L) clockTimeAfter(estimate) else null,
                    onClick = { onConfirmSongs(count) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Six times, in one unit.
 *
 * "1 hr" next to "45 min" would be the tidier label and the worse grid: two units in a block you
 * are meant to compare at a glance means doing a conversion on every glance. Minutes all the way
 * up keeps the numbers on one scale.
 */
private val SleepTimerMinuteOptions = listOf(5, 15, 30, 45, 60, 90)

/** Three song counts. One is "this song", which is the most-used sleep timer there is. */
private val SleepTimerSongOptions = listOf(1, 3, 5)

private const val SleepTimerColumns = 3
private const val SleepTimerExtendMinutes = 15
private val SleepTimerGridGap = 8.dp
private val SleepTimerDialSize = 132.dp
private val SleepTimerDialStroke = 6.dp

/**
 * The countdown ring.
 *
 * Draining rather than filling: the arc is what is left, so an almost-empty dial means the music is
 * almost over, which is the reading you want half asleep in the dark. Idle, it is a full faint
 * circle around a moon -- the same object, waiting, rather than a different screen.
 */
@Composable
private fun SleepTimerDial(
    running: Boolean,
    remainingFraction: Float,
    label: String?,
    caption: String?,
) {
    val scheme = MaterialTheme.colorScheme
    val sweep by animateFloatAsState(
        targetValue = if (running) remainingFraction else 1f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "sleepTimerSweep",
    )
    val arcColor by animateColorAsState(
        targetValue = if (running) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 320),
        label = "sleepTimerArc",
    )
    val trackColor = scheme.onSurface.copy(alpha = 0.10f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(SleepTimerDialSize),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = SleepTimerDialStroke.toPx()
            val inset = stroke * 1.3f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (sweep > 0f) {
                // A wide, faint copy of the same arc under the real one. It is the light the ring
                // would throw if it were lit, and it is what keeps a 6dp stroke from reading as a
                // hairline drawn on top of the dialog.
                drawArc(
                    color = arcColor.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 2.6f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        AnimatedContent(
            targetState = label,
            label = "sleepTimerDialFace",
        ) { text ->
            if (text == null) {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = scheme.secondary,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    if (caption != null) {
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * One choice: what it is, and when it lands.
 *
 * The clock time is the second line rather than a column of its own, because in a grid there is no
 * column to read down -- the pill has to carry both halves itself or the arithmetic comes back.
 */
@Composable
private fun SleepTimerChip(
    label: String,
    clock: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "sleepTimerChipScale",
    )

    Surface(
        shape = shape,
        color = scheme.onSurface.copy(alpha = 0.06f),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .heightIn(min = 62.dp)
                .padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (clock != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = clock,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Codec information row displayed when showCodecOnPlayer is enabled.
 */
@Composable
fun CodecInfoRow(
    codec: String,
    bitrate: String,
    fileSize: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp, top = 6.dp, bottom = 2.dp)
    ) {
        Text(
            text = buildString {
                append(codec)
                if (bitrate != "Unknown") {
                    append(" • ")
                    append(bitrate)
                }
                if (fileSize.isNotEmpty()) {
                    append(" • ")
                    append(fileSize)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * V2 Design Style collapsed queue content.
 */
@Composable
fun QueueCollapsedContentV2(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    repeatMode: Int,
    mediaMetadata: MediaMetadata?,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    onRepeatModeClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showCodecOnPlayer && currentFormat != null) {
            val codec =
                currentFormat.codecs
                    .takeIf { it.isNotBlank() }
                    ?: currentFormat.mimeType.substringAfter("/", missingDelimiterValue = currentFormat.mimeType).uppercase()

            val container =
                currentFormat.mimeType.substringAfter("/", missingDelimiterValue = currentFormat.mimeType).uppercase()

            val codecLabel =
                if (container.isNotBlank() && !codec.equals(container, ignoreCase = true)) {
                    "$codec ($container)"
                } else {
                    codec
                }

            val bitrate =
                if (currentFormat.bitrate > 0) {
                    "${currentFormat.bitrate / 1000} kbps"
                } else {
                    "Unknown"
                }

            val sampleRateText =
                currentFormat.sampleRate?.takeIf { it > 0 }?.let { sampleRate ->
                    val khz = (sampleRate / 100.0).roundToInt() / 10.0
                    "$khz kHz"
                }

            val fileSizeText =
                if (currentFormat.contentLength > 0) {
                    "${(currentFormat.contentLength / 1024.0 / 1024.0).roundToInt()} MB"
                } else {
                    ""
                }

            val extraText =
                listOfNotNull(sampleRateText, fileSizeText.takeIf { it.isNotBlank() })
                    .joinToString(separator = " • ")

            CodecInfoRow(
                codec = codecLabel,
                bitrate = bitrate,
                fileSize = extraText,
                textColor = textBackgroundColor.copy(alpha = 0.7f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 10.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    ),
                ),
        ) {
            val buttonSize = 42.dp
            val iconSize = 24.dp
            val borderColor = textBackgroundColor.copy(alpha = 0.35f)

            // Queue button
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(
                        RoundedCornerShape(
                            topStart = 50.dp,
                            bottomStart = 50.dp,
                            topEnd = 10.dp,
                            bottomEnd = 10.dp
                        )
                    )
                    .border(
                        1.dp,
                        borderColor,
                        RoundedCornerShape(
                            topStart = 50.dp,
                            bottomStart = 50.dp,
                            topEnd = 10.dp,
                            bottomEnd = 10.dp
                        )
                    )
                    .clickable { onExpandQueue() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.queue_music),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = textBackgroundColor
                )
            }

            // Sleep timer button
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable { onSleepTimerClick() },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    label = "sleepTimer",
                    targetState = sleepTimerEnabled,
                ) { enabled ->
                    if (enabled) {
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft),
                            color = textBackgroundColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor
                        )
                    }
                }
            }

            // Lyrics button
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable { onShowLyrics() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lyrics),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = textBackgroundColor
                )
            }

            // Repeat mode button
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(
                        RoundedCornerShape(
                            topStart = 10.dp,
                            bottomStart = 10.dp,
                            topEnd = 50.dp,
                            bottomEnd = 50.dp
                        )
                    )
                    .border(
                        1.dp,
                        borderColor,
                        RoundedCornerShape(
                            topStart = 10.dp,
                            bottomStart = 10.dp,
                            topEnd = 50.dp,
                            bottomEnd = 50.dp
                        )
                    )
                    .clickable { onRepeatModeClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = when (repeatMode) {
                            Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> R.drawable.repeat
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                    tint = textBackgroundColor
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Menu button
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(textButtonColor)
                    .clickable { onMenuClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.more_vert),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = iconButtonColor
                )
            }
        }
    }
}

/**
 * V3 Design Style collapsed queue content.
 */
@Composable
fun QueueCollapsedContentV3(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showCodecOnPlayer && currentFormat != null) {
            val codec = currentFormat.mimeType.substringAfter("/").uppercase()
            val bitrate = "${currentFormat.bitrate / 1000} kbps"

            CodecInfoRow(
                codec = codec,
                bitrate = bitrate,
                fileSize = "",
                textColor = textBackgroundColor.copy(alpha = 0.5f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    ),
                ),
        ) {
            // Queue button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onExpandQueue() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = textBackgroundColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(id = R.string.queue),
                        color = textBackgroundColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            // Sleep timer button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSleepTimerClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    label = "sleepTimer",
                    targetState = sleepTimerEnabled,
                ) { enabled ->
                    if (enabled) {
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft),
                            color = textBackgroundColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = textBackgroundColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Lyrics button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShowLyrics() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = textBackgroundColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(id = R.string.lyrics),
                        color = textBackgroundColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            // Menu button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMenuClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.more_vert),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = textBackgroundColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * V1 Design Style collapsed queue content (text buttons).
 */
@Composable
fun QueueCollapsedContentV1(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showCodecOnPlayer && currentFormat != null) {
            val codec = currentFormat.mimeType.substringAfter("/").uppercase()
            val bitrate = "${currentFormat.bitrate / 1000} kbps"
            val fileSize = if (currentFormat.contentLength > 0) {
                "${(currentFormat.contentLength / 1024.0 / 1024.0).roundToInt()} MB"
            } else ""

            CodecInfoRow(
                codec = codec,
                bitrate = bitrate,
                fileSize = fileSize,
                textColor = textBackgroundColor.copy(alpha = 0.7f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 12.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
        ) {
            TextButton(
                onClick = onExpandQueue,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textBackgroundColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.queue),
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            TextButton(
                onClick = onSleepTimerClick,
                modifier = Modifier.weight(1.2f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.bedtime),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textBackgroundColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AnimatedContent(
                        label = "sleepTimer",
                        targetState = sleepTimerEnabled,
                    ) { enabled ->
                        if (enabled) {
                            Text(
                                text = makeTimeString(sleepTimerTimeLeft),
                                color = textBackgroundColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.basicMarquee()
                            )
                        } else {
                            Text(
                                text = stringResource(id = R.string.sleep_timer),
                                color = textBackgroundColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onShowLyrics,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textBackgroundColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.lyrics),
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }
    }
}

/**
 * V4 Design Style collapsed queue content (pill buttons).
 */
@Composable
fun QueueCollapsedContentV4(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    mediaMetadata: MediaMetadata?,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showCodecOnPlayer && currentFormat != null) {
            val codec = currentFormat.mimeType.substringAfter("/").uppercase()
            val bitrate = "${currentFormat.bitrate / 1000} kbps"
            val fileSize = if (currentFormat.contentLength > 0) {
                "${(currentFormat.contentLength / 1024.0 / 1024.0).roundToInt()} MB"
            } else ""

            CodecInfoRow(
                codec = codec,
                bitrate = bitrate,
                fileSize = fileSize,
                textColor = textBackgroundColor.copy(alpha = 0.6f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    ),
                ),
        ) {
            val buttonSize = 48.dp
            val iconSize = 22.dp

            // Queue button (pill)
            Box(
                modifier = Modifier
                    .height(buttonSize)
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(textBackgroundColor.copy(alpha = 0.1f))
                    .clickable { onExpandQueue() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = textBackgroundColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.queue),
                        color = textBackgroundColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Sleep timer button (circle)
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        if (sleepTimerEnabled) textBackgroundColor.copy(alpha = 0.2f)
                        else textBackgroundColor.copy(alpha = 0.1f)
                    )
                    .clickable { onSleepTimerClick() },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    label = "sleepTimer",
                    targetState = sleepTimerEnabled,
                ) { enabled ->
                    if (enabled) {
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft),
                            color = textBackgroundColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Lyrics button (pill)
            Box(
                modifier = Modifier
                    .height(buttonSize)
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(textBackgroundColor.copy(alpha = 0.1f))
                    .clickable { onShowLyrics() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = textBackgroundColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.lyrics),
                        color = textBackgroundColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun QueueCollapsedContentV7(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    onExpandQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onDeviceClick: () -> Unit,
    deviceName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showCodecOnPlayer && currentFormat != null) {
            val codec = currentFormat.mimeType.substringAfter("/").uppercase()
            val bitrate = "${currentFormat.bitrate / 1000} kbps"
            val fileSize = if (currentFormat.contentLength > 0) {
                "${(currentFormat.contentLength / 1024.0 / 1024.0).roundToInt()} MB"
            } else ""

            CodecInfoRow(
                codec = codec,
                bitrate = bitrate,
                fileSize = fileSize,
                textColor = textBackgroundColor.copy(alpha = 0.6f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    ),
                ),
        ) {
            val iconSize = 22.dp

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onExpandQueue,
                    shape = CircleShape,
                    color = textBackgroundColor.copy(alpha = 0.08f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor
                        )
                    }
                }

                Surface(
                    onClick = onShowLyrics,
                    shape = CircleShape,
                    color = textBackgroundColor.copy(alpha = 0.08f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lyrics),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor
                        )
                    }
                }
            }

            Surface(
                onClick = onDeviceClick,
                shape = RoundedCornerShape(20.dp),
                color = textBackgroundColor.copy(alpha = 0.08f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.bluetooth),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}