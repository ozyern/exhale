/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.ArtistTally
import com.ozyern.exhale.desktop.data.ListeningLog
import com.ozyern.exhale.desktop.data.MonthCapsule
import com.ozyern.exhale.desktop.data.SongTally
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.cascade
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.GlassIconButton
import com.ozyern.exhale.desktop.ui.rememberCascade
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/*
 * Your Sound Chem, the Android app's monthly listening capsule, laid out for a wide window. On a
 * phone it is a stacked deck of poster cards; here the deck becomes a dashboard — the headline
 * number with a day-by-day chart beside the faces of the month, the share of listening one artist
 * took next to the tallies and the breakdown — with the ranked lists below it in columns. The cards
 * are the app's glass, deal in one after another, and count their numbers up rather than print them.
 */

private val CardShape = RoundedCornerShape(28.dp)
private val Gap = 16.dp
private val IntegerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

private fun formatCount(value: Long): String = IntegerFormat.format(value)

/** "18 hr 12 min", "43 min". */
private fun formatListened(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

private fun playsText(plays: Int): String = if (plays == 1) "1 play" else "${formatCount(plays.toLong())} plays"

@Composable
fun SoundChemScreen(player: DesktopPlayer, state: PlayerState, insets: ChromeInsets, onBack: (() -> Unit)?) {
    val version by ListeningLog.version.collectAsState()
    val months by produceState(listOf(YearMonth.now()), version) { value = withContext(Dispatchers.IO) { ListeningLog.months() } }
    var index by remember { mutableIntStateOf(0) }
    val month = months.getOrElse(index) { months.first() }
    val capsule by produceState<MonthCapsule?>(null, month, version) { value = withContext(Dispatchers.IO) { ListeningLog.capsule(month) } }
    val loaded = capsule?.takeIf { it.month == month }

    val nav = LocalNavigator.current
    val listState = rememberLazyListState()
    val cascade = rememberCascade("sound-chem:$month", ready = loaded != null)
    val start = insets.start + 20.dp

    val topArtist = loaded?.artists?.firstOrNull()
    val topSong = loaded?.songs?.firstOrNull()
    val topPortrait = rememberArtistPortrait(topArtist?.id, topArtist?.fallbackImage)
    val monthName = remember(month) { DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()).format(month) }

    ArtworkWash(topPortrait ?: topSong?.song?.thumbnail) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = start, end = 40.dp, top = 16.dp, bottom = insets.bottom + 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (onBack != null) item(key = "back") { GlassIconButton(NavIcons.Back, "Back", onBack, Modifier.padding(bottom = 18.dp)) }
                item(key = "header") {
                    ChemHeader(
                        month = monthName,
                        year = month.year.toString(),
                        canGoOlder = index < months.lastIndex,
                        canGoNewer = index > 0,
                        onOlder = { index++ },
                        onNewer = { index-- },
                        onShuffle = loaded?.songs?.takeIf { it.isNotEmpty() }?.let { songs -> { player.play(songs.map { it.song }.shuffled(), 0) } },
                        summary = loaded?.takeIf { !it.isEmpty }?.let { summaryText(it, monthName) },
                    )
                }
                when {
                    loaded == null -> item(key = "loading") { Box(Modifier.padding(top = 26.dp).fillMaxWidth().height(320.dp).glassPlate(CardShape)) }
                    loaded.isEmpty -> item(key = "empty") { ChemEmpty(monthName, current = index == 0) }
                    else -> {
                        item(key = "deck") {
                            Deck(loaded, topPortrait, monthName, cascade, onArtist = { a -> a.id?.let { nav?.openArtist?.invoke(it, a.name, null) } }, onSong = { tally ->
                                val songs = loaded.songs.map { it.song }
                                player.play(songs, songs.indexOfFirst { it.id == tally.song.id }.coerceAtLeast(0))
                            })
                        }
                        item(key = "songs") {
                            ChemSection("Top songs", "${loaded.songs.size} songs this month") {
                                TopSongs(loaded.songs.take(20), state, cascade) { i ->
                                    val songs = loaded.songs.map { it.song }
                                    if (songs[i].id == state.current?.id) player.togglePlayPause() else player.play(songs, i)
                                }
                            }
                        }
                        if (loaded.artists.isNotEmpty()) {
                            item(key = "artists") {
                                ChemSection("Top artists", "${loaded.artists.size} artists this month") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                        itemsIndexed(loaded.artists.take(20), key = { i, a -> "$i:${a.id ?: a.name}" }) { i, artist ->
                                            ArtistPoster(i + 1, artist, Modifier.cascade(i, cascade)) { artist.id?.let { nav?.openArtist?.invoke(it, artist.name, null) } }
                                        }
                                    }
                                }
                            }
                        }
                        if (loaded.albums.isNotEmpty()) {
                            item(key = "albums") {
                                ChemSection("Top albums", "${loaded.albums.size} albums this month") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        itemsIndexed(loaded.albums.take(20), key = { i, a -> "$i:${a.id}" }) { i, album ->
                                            AlbumPoster(i + 1, album.name, album.image, "${playsText(album.plays)} · ${formatListened(album.listenedMs)}", Modifier.cascade(i, cascade)) {
                                                nav?.openAlbum?.invoke(album.id, album.name, album.image)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PageScrollbar(listState, insets)
        }
    }
}

private fun summaryText(capsule: MonthCapsule, monthName: String): String = buildString {
    appendLine("Your Sound Chem — $monthName ${capsule.month.year}")
    appendLine()
    appendLine("Time listened: ${formatCount(capsule.listenedMs / 60_000L)} minutes (${formatListened(capsule.listenedMs)})")
    appendLine("Plays: ${formatCount(capsule.plays.toLong())}")
    capsule.artists.firstOrNull()?.let { appendLine("Top artist: ${it.name}") }
    capsule.songs.firstOrNull()?.let { appendLine("Top song: ${it.song.title} — ${it.song.artists.joinToString { a -> a.name }}") }
    append("Made with Exhale")
}

/** Artists' faces, found once per artist and remembered for the session. */
private object ArtistPortraits {
    val cache = ConcurrentHashMap<String, String>()
}

@Composable
private fun rememberArtistPortrait(id: String?, fallback: String?): String? {
    val portrait by produceState(id?.let { ArtistPortraits.cache[it] } ?: fallback, id, fallback) {
        val key = id ?: return@produceState
        ArtistPortraits.cache[key]?.let {
            value = it
            return@produceState
        }
        val found = withContext(Dispatchers.IO) { YouTube.artist(key).getOrNull()?.artist?.thumbnail }
        if (found != null) {
            ArtistPortraits.cache[key] = found
            value = found
        }
    }
    return portrait
}

@Composable
private fun rememberCountUp(target: Long): Long {
    val count = remember { Animatable(0f) }
    LaunchedEffect(target) { count.animateTo(target.toFloat(), tween(1_100, easing = FastOutSlowInEasing)) }
    return count.value.toLong()
}

// ── Header ──

@Composable
private fun ChemHeader(
    month: String,
    year: String,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onShuffle: (() -> Unit)?,
    summary: String?,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "YOUR SOUND CHEM",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.16.em),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = month to year,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 3 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 3 })
                },
                modifier = Modifier.weight(1f),
                label = "chem_month",
            ) { (m, y) ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(m, style = MaterialTheme.typography.displayMedium.copy(letterSpacing = (-0.03).em), fontWeight = FontWeight.Black, maxLines = 1)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        y,
                        style = MaterialTheme.typography.displayMedium.copy(letterSpacing = (-0.03).em),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                        maxLines = 1,
                    )
                }
            }
            MonthStep("Previous month", rotation = 90f, enabled = canGoOlder, onClick = onOlder)
            Spacer(Modifier.width(10.dp))
            MonthStep("Next month", rotation = -90f, enabled = canGoNewer, onClick = onNewer)
        }
        if (onShuffle != null || summary != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onShuffle != null) PillButton("Shuffle top songs", TransportIcons.Shuffle, primary = true, onClick = onShuffle)
                if (summary != null) {
                    PillButton(if (copied) "Copied" else "Copy summary", if (copied) NavIcons.Check else NavIcons.Copy, primary = false) {
                        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(summary), null) }
                        copied = true
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStep(description: String, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    val fade by animateFloatAsState(if (enabled) 1f else 0.3f, tween(200), label = "month_step")
    GlassIconButton(
        icon = TransportIcons.Collapse,
        contentDescription = description,
        onClick = { if (enabled) onClick() },
        size = 44.dp,
        modifier = Modifier.graphicsLayer { alpha = fade },
    ) {
        Icon(TransportIcons.Collapse, contentDescription = description, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation })
    }
}

// ── The deck ──

@Composable
private fun Deck(
    capsule: MonthCapsule,
    topPortrait: String?,
    monthName: String,
    cascade: () -> Float,
    onArtist: (ArtistTally) -> Unit,
    onSong: (SongTally) -> Unit,
) {
    val topArtist = capsule.artists.firstOrNull()
    val topSong = capsule.songs.firstOrNull()
    val share = if (topArtist != null && capsule.listenedMs > 0) ((topArtist.listenedMs * 100) / capsule.listenedMs).toInt().coerceIn(0, 100) else 0
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        val wide = maxWidth >= 1080.dp
        if (wide) {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                Row(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    TimeCard(capsule, cascade, fill = true, modifier = Modifier.weight(1.5f).fillMaxHeight().cascade(0, cascade))
                    if (topArtist != null) {
                        SpotlightCard(
                            "Top artist", topArtist.name, "${playsText(topArtist.plays)} · ${formatListened(topArtist.listenedMs)}",
                            topPortrait, CircleShape, secondary, fill = true,
                            modifier = Modifier.weight(1f).fillMaxHeight().cascade(1, cascade),
                        ) { onArtist(topArtist) }
                    }
                    if (topSong != null) {
                        SpotlightCard(
                            "Top song", topSong.song.title, "${playsText(topSong.plays)} · ${formatListened(topSong.listenedMs)}",
                            topSong.song.thumbnail.resizedThumbnail(544), RoundedCornerShape(18.dp), tertiary, fill = true,
                            modifier = Modifier.weight(1f).fillMaxHeight().cascade(2, cascade),
                        ) { onSong(topSong) }
                    }
                }
                Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    if (topArtist != null && share >= 5) {
                        ShareCard(topArtist, topPortrait, share, monthName, horizontal = true, modifier = Modifier.weight(1.25f).fillMaxHeight().cascade(3, cascade)) { onArtist(topArtist) }
                    }
                    Column(Modifier.weight(1.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                        Tallies(capsule, Modifier.fillMaxWidth().height(104.dp).cascade(4, cascade))
                        Breakdown(capsule.artists.take(5), capsule.listenedMs, Modifier.fillMaxWidth().weight(1f).cascade(5, cascade), onArtist)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                TimeCard(capsule, cascade, fill = false, modifier = Modifier.fillMaxWidth().cascade(0, cascade))
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    topArtist?.let {
                        SpotlightCard("Top artist", it.name, formatListened(it.listenedMs), topPortrait, CircleShape, secondary, fill = false, modifier = Modifier.weight(1f).cascade(1, cascade)) { onArtist(it) }
                    }
                    topSong?.let {
                        SpotlightCard("Top song", it.song.title, formatListened(it.listenedMs), it.song.thumbnail.resizedThumbnail(544), RoundedCornerShape(18.dp), tertiary, fill = false, modifier = Modifier.weight(1f).cascade(2, cascade)) { onSong(it) }
                    }
                }
                if (topArtist != null && share >= 5) ShareCard(topArtist, topPortrait, share, monthName, horizontal = false, modifier = Modifier.fillMaxWidth().cascade(3, cascade)) { onArtist(topArtist) }
                Tallies(capsule, Modifier.fillMaxWidth().height(104.dp).cascade(4, cascade))
                if (capsule.artists.size >= 2) Breakdown(capsule.artists.take(5), capsule.listenedMs, Modifier.fillMaxWidth().cascade(5, cascade), onArtist)
            }
        }
    }
}

@Composable
private fun ChemCard(modifier: Modifier, accent: Color, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered && onClick != null) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 320f), label = "chem_card")
    Column(
        modifier
            .graphicsLayer { translationY = -4.dp.toPx() * lift }
            .glassPlate(CardShape, base = accent.copy(alpha = 0.07f + 0.04f * lift))
            .then(if (onClick != null) Modifier.hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(22.dp),
        content = content,
    )
}

@Composable
private fun CardLabel(text: String, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
        if (chevron) Icon(TransportIcons.Collapse, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -90f })
    }
}

/** The headline: minutes listened, counted up, over a bar for every day of the month. */
@Composable
private fun TimeCard(capsule: MonthCapsule, cascade: () -> Float, fill: Boolean, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val minutes = capsule.listenedMs / 60_000L
    val shown = rememberCountUp(minutes)
    val busiest = capsule.minutesByDay.withIndex().maxByOrNull { it.value }
    ChemCard(modifier, accent) {
        CardLabel("Time listened")
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent)) { append(formatCount(shown)) }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)) { append("  minutes") }
            },
            style = MaterialTheme.typography.displayLarge.copy(letterSpacing = (-0.03).em),
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        if (minutes >= 60) Text(formatListened(capsule.listenedMs), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fill) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(22.dp))
        DailyBars(capsule.minutesByDay, accent, cascade, Modifier.fillMaxWidth().height(if (fill) 112.dp else 88.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (busiest != null && busiest.value > 0) {
                Text(
                    "Busiest day: ${busiest.index + 1} ${DateTimeFormatter.ofPattern("MMM", Locale.getDefault()).format(capsule.month)} · ${busiest.value} min",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Spacer(Modifier.weight(1f))
            }
            Text("${capsule.minutesByDay.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A bar per day, rising in left to right; the busiest day in full colour, empty days a faint stub. */
@Composable
private fun DailyBars(minutesByDay: List<Int>, accent: Color, cascade: () -> Float, modifier: Modifier) {
    val peak = (minutesByDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Canvas(modifier) {
        val count = minutesByDay.size.coerceAtLeast(1)
        val slot = size.width / count
        val barWidth = (slot * 0.62f).coerceAtMost(18.dp.toPx())
        val progress = cascade()
        minutesByDay.forEachIndexed { day, minutes ->
            val grow = FastOutSlowInEasing.transform(((progress - day * 0.012f) / 0.55f).coerceIn(0f, 1f))
            val x = slot * day + (slot - barWidth) / 2f
            drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2f))
            if (minutes <= 0) return@forEachIndexed
            val height = (size.height * minutes / peak * grow).coerceAtLeast(barWidth)
            val strong = minutes == peak
            drawRoundRect(
                Brush.verticalGradient(listOf(accent.copy(alpha = if (strong) 1f else 0.75f), accent.copy(alpha = if (strong) 0.7f else 0.35f)), startY = size.height - height, endY = size.height),
                Offset(x, size.height - height),
                Size(barWidth, height),
                CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun SpotlightCard(
    label: String,
    title: String,
    detail: String,
    image: String?,
    shape: Shape,
    accent: Color,
    fill: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    ChemCard(modifier, accent, onClick) {
        CardLabel(label, chevron = true)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        Box(
            (if (fill) Modifier.weight(1f) else Modifier.aspectRatio(1f)).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.12f), Color.Transparent))))
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .aspectRatio(1f)
                    .shadow(18.dp, shape, clip = false)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, accent.copy(alpha = 0.35f), shape),
            )
        }
    }
}

/** The share of the month one artist took — only shown when it's a real slice. */
@Composable
private fun ShareCard(artist: ArtistTally, portrait: String?, percent: Int, monthName: String, horizontal: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shown = rememberCountUp(percent.toLong())
    val face: @Composable (Modifier) -> Unit = { m ->
        Box(m, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.14f), Color.Transparent))))
            AsyncImage(
                portrait,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(0.8f).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, accent.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
    val words: @Composable ColumnScope.() -> Unit = {
        Text("$shown%", style = MaterialTheme.typography.displayLarge.copy(letterSpacing = (-0.04).em), fontWeight = FontWeight.Black, color = accent)
        Text(
            buildAnnotatedString {
                append("of everything you played in $monthName was ")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(artist.name) }
                append(".")
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text("${formatListened(artist.listenedMs)} · ${playsText(artist.plays)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ChemCard(modifier, accent, onClick) {
        if (horizontal) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                face(Modifier.fillMaxHeight().aspectRatio(1f))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), content = words)
            }
        } else {
            face(Modifier.fillMaxWidth().aspectRatio(1.6f))
            Spacer(Modifier.height(16.dp))
            words()
        }
    }
}

@Composable
private fun Tallies(capsule: MonthCapsule, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Tally(capsule.plays.toLong(), "Plays", scheme.primary, Modifier.weight(1f).fillMaxHeight())
        Tally(capsule.songs.size.toLong(), "Songs", scheme.tertiary, Modifier.weight(1f).fillMaxHeight())
        Tally(capsule.artists.size.toLong(), "Artists", scheme.secondary, Modifier.weight(1f).fillMaxHeight())
        Tally(capsule.albums.size.toLong(), "Albums", scheme.primary, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Tally(value: Long, label: String, accent: Color, modifier: Modifier) {
    val shown = rememberCountUp(value)
    ChemCard(modifier, accent) {
        Text(formatCount(shown), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The top artists as bars sorted by length — the comparison the month is actually about. */
@Composable
private fun Breakdown(artists: List<ArtistTally>, totalMs: Long, modifier: Modifier, onArtist: (ArtistTally) -> Unit) {
    if (totalMs <= 0 || artists.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val accents = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    ChemCard(modifier, scheme.secondary) {
        CardLabel("Where your time went")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            artists.forEachIndexed { i, artist ->
                BreakdownRow(artist, (artist.listenedMs.toFloat() / totalMs).coerceIn(0f, 1f), accents[i % accents.size]) { onArtist(artist) }
            }
        }
    }
}

@Composable
private fun BreakdownRow(artist: ArtistTally, fraction: Float, accent: Color, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(fraction) { shown = true }
    val grown by animateFloatAsState(if (shown) fraction else 0f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "breakdown_bar")
    val portrait = rememberArtistPortrait(artist.id, artist.fallbackImage)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(portrait, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.width(12.dp))
        Text(artist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(grown).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent))))
        }
        Spacer(Modifier.width(12.dp))
        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

// ── Below the deck ──

@Composable
private fun ChemSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Ranked songs in two columns on a wide window, with how often and how long each was played. */
@Composable
private fun TopSongs(songs: List<SongTally>, state: PlayerState, cascade: () -> Float, onPlay: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 980.dp) 2 else 1
        val perColumn = ((songs.size + columns - 1) / columns).coerceAtLeast(1)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            songs.chunked(perColumn).forEachIndexed { column, chunk ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chunk.forEachIndexed { i, tally ->
                        val rank = column * perColumn + i
                        RankedSong(rank + 1, tally, active = tally.song.id == state.current?.id, playing = state.isPlaying, modifier = Modifier.cascade(i + column, cascade)) { onPlay(rank) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedSong(rank: Int, tally: SongTally, active: Boolean, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    active -> Modifier.glassPlate(shape, base = accent.copy(alpha = 0.14f))
                    hovered -> Modifier.glassPlate(shape)
                    else -> Modifier.clip(shape)
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = if (rank <= 3) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(38.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(tally.song.thumbnail.resizedThumbnail(160), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    if (playing) EqualizerBars(Color.White, Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tally.song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (active) accent else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tally.song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(playsText(tally.plays), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(formatListened(tally.listenedMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArtistPoster(rank: Int, artist: ArtistTally, modifier: Modifier, onClick: () -> Unit) {
    val portrait = rememberArtistPortrait(artist.id, artist.fallbackImage)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val grow by animateFloatAsState(if (hovered) 1.05f else 1f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "artist_poster")
    Column(
        modifier
            .width(172.dp)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AsyncImage(
                portrait,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(160.dp).shadow(if (hovered) 20.dp else 0.dp, CircleShape, clip = false).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            RankBadge(rank, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(12.dp))
        Text(artist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(formatListened(artist.listenedMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AlbumPoster(rank: Int, title: String, image: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val grow by animateFloatAsState(if (hovered) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "album_poster")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .width(184.dp)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box {
            AsyncImage(
                image.resizedThumbnail(360),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(184.dp).shadow(if (hovered) 20.dp else 0.dp, shape, clip = false).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            RankBadge(rank, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RankBadge(rank: Int, modifier: Modifier, size: Dp = 34.dp) {
    Box(
        modifier
            .size(size)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(if (rank <= 3) MaterialTheme.colorScheme.primary else Color(0xE6141116)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary else Color.White,
        )
    }
}

@Composable
private fun ChemEmpty(monthName: String, current: Boolean) {
    Column(
        Modifier.padding(top = 26.dp).fillMaxWidth().glassPlate(CardShape).padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(NavIcons.Stats, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(if (current) "Your $monthName is just getting started" else "Nothing played in $monthName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Every song you listen to for more than a few seconds lands here — minutes, top artists and songs, month by month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
