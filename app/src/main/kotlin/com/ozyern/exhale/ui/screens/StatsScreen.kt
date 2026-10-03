/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.DisableBlurKey
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.AuroraBackdrop
import com.ozyern.exhale.ui.component.HideOnScrollFAB
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.ItemThumbnail
import com.ozyern.exhale.ui.component.ListItem
import com.ozyern.exhale.ui.component.LocalAlbumsGrid
import com.ozyern.exhale.ui.component.LocalArtistsGrid
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.ArtistMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Your Sound Chem — the listening capsule.
 *
 * Month by month, one month at a time. The screen used to open on a mode dropdown
 * (Continuous / Weeks / Months / Years) above a rail of period chips, which meant the first thing
 * it asked you to do was configure a query. A capsule is a *period*, so the period is now fixed:
 * the month is the title, and the only control is a pair of chevrons to step through the months
 * you actually have history for.
 *
 * The deck below it is the point — total time, top artist, top song, and the share of listening a
 * single artist took, each stated once and stated large. The exhaustive ranked lists are still
 * here, below the deck, for anyone who wants to scroll into the detail.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val context = LocalContext.current

    val monthIndex by viewModel.indexChips.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()
    val firstEvent by viewModel.firstEvent.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Every month from the current one back to the month of the first thing ever played. Index 0
    // is this month, which is exactly the offset `statToPeriod(MONTHS, n)` expects, so the index
    // doubles as the query parameter with no translation.
    val currentMonth = remember { LocalDateTime.now().withDayOfMonth(1) }
    val months: List<LocalDateTime> = remember(firstEvent, currentMonth) {
        val oldest = firstEvent?.event?.timestamp?.withDayOfMonth(1)
        if (oldest == null) {
            listOf(currentMonth)
        } else {
            generateSequence(currentMonth) { it.minusMonths(1) }
                .takeWhile { !it.isBefore(oldest) }
                .toList()
        }
    }
    val selectedMonth = months.getOrNull(monthIndex) ?: currentMonth
    val monthName = remember(selectedMonth) {
        DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()).format(selectedMonth)
    }
    val yearName = remember(selectedMonth) { selectedMonth.year.toString() }
    val periodText = "$monthName $yearName"

    // ── The capsule numbers ───────────────────────────────────────────────────
    val totalMillis = remember(mostPlayedSongsStats) {
        mostPlayedSongsStats.sumOf { it.timeListened ?: 0L }
    }
    val totalMinutes = totalMillis / 60_000L
    val totalPlays = remember(mostPlayedSongsStats) {
        mostPlayedSongsStats.sumOf { it.songCountListened }
    }
    val topArtist = mostPlayedArtists.firstOrNull()
    val topSong = mostPlayedSongsStats.firstOrNull()
    val topArtistMillis = topArtist?.timeListened?.toLong() ?: 0L
    // Artist and song totals come from two separate queries, so the ratio is not guaranteed to sit
    // inside 0..100 — clamp rather than print an impossible share.
    val artistSharePercent = if (totalMillis > 0L) {
        ((topArtistMillis * 100.0) / totalMillis).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val hasData = mostPlayedSongsStats.isNotEmpty() || mostPlayedArtists.isNotEmpty()

    fun shareCapsule() {
        val summary = buildString {
            appendLine(context.getString(R.string.sound_chem))
            appendLine(periodText)
            appendLine()
            append(context.getString(R.string.sound_chem_time_listened))
            append(": ")
            appendLine(
                context.getString(
                    R.string.sound_chem_minutes_value,
                    formatCount(totalMinutes),
                ),
            )
            topArtist?.let {
                append(context.getString(R.string.sound_chem_top_artist))
                append(": ")
                appendLine(it.artist.name)
            }
            topSong?.let {
                append(context.getString(R.string.sound_chem_top_song))
                append(": ")
                appendLine(it.title)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.sound_chem_share_action),
                ),
            )
        }
    }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackdrop(
            animated = !disableBlur,
            modifier = Modifier.zIndex(-1f),
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        ) {
            item(key = "monthHeader") {
                SoundChemMonthHeader(
                    month = monthName,
                    year = yearName,
                    // "Older" walks up the list (higher index = further back); "newer" walks down.
                    canGoOlder = monthIndex < months.lastIndex,
                    canGoNewer = monthIndex > 0,
                    onOlder = { viewModel.indexChips.value = monthIndex + 1 },
                    onNewer = { viewModel.indexChips.value = monthIndex - 1 },
                )
                Spacer(Modifier.height(18.dp))
            }

            if (!hasData) {
                item(key = "empty") { SoundChemEmptyState() }
                return@LazyColumn
            }

            // ── The deck ──────────────────────────────────────────────────────
            item(key = "timeCard") {
                SoundChemHero(
                    covers = mostPlayedSongsStats.map { it.thumbnailUrl }.filter { it.isNotBlank() }.distinct().take(4),
                    minutes = totalMinutes,
                    periodText = periodText,
                    modifier = Modifier
                        .padding(horizontal = CapsuleGutter)
                        .capsuleEntrance(),
                )
                Spacer(Modifier.height(CapsuleGap))
            }

            if (topArtist != null || topSong != null) {
                item(key = "spotlightRow") {
                    // No intrinsic-height plumbing needed to keep the pair level: both cards pin
                    // their title to exactly two lines and both artworks are squares of the same
                    // width, so the heights already match by construction.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CapsuleGutter),
                        horizontalArrangement = Arrangement.spacedBy(CapsuleGap),
                    ) {
                        topArtist?.let { artist ->
                            SoundChemSpotlightCard(
                                label = stringResource(R.string.sound_chem_top_artist),
                                title = artist.artist.name,
                                accent = MaterialTheme.colorScheme.secondary,
                                imageUrl = artist.artist.thumbnailUrl,
                                circular = true,
                                onClick = { navController.navigate("artist/${artist.id}") },
                                modifier = Modifier
                                    .weight(1f)
                                    .capsuleEntrance(delayMillis = 60),
                            )
                        }
                        topSong?.let { song ->
                            SoundChemSpotlightCard(
                                label = stringResource(R.string.sound_chem_top_song),
                                title = song.title,
                                accent = MaterialTheme.colorScheme.tertiary,
                                imageUrl = song.thumbnailUrl,
                                circular = false,
                                onClick = {
                                    val preload = mostPlayedSongs.firstOrNull { it.id == song.id }
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = WatchEndpoint(song.id),
                                            preloadItem = preload?.toMediaMetadata(),
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .capsuleEntrance(delayMillis = 60),
                            )
                        }
                        // Keeps a lone card at half width instead of letting it stretch across the
                        // row and break the grid.
                        if (topArtist == null || topSong == null) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(CapsuleGap))
                }
            }

            // The share card only earns its size when one artist actually dominates the month —
            // "3% of your listening" is not an insight, it is noise.
            if (topArtist != null && artistSharePercent >= 5) {
                item(key = "shareCard") {
                    SoundChemShareCard(
                        artistName = topArtist.artist.name,
                        imageUrl = topArtist.artist.thumbnailUrl,
                        percent = artistSharePercent,
                        timeText = makeTimeString(topArtistMillis),
                        periodText = periodText,
                        onShare = ::shareCapsule,
                        onClick = { navController.navigate("artist/${topArtist.id}") },
                        modifier = Modifier
                            .padding(horizontal = CapsuleGutter)
                            .capsuleEntrance(delayMillis = 120),
                    )
                    Spacer(Modifier.height(CapsuleGap))
                }
            }

            item(key = "tallies") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CapsuleGutter),
                    horizontalArrangement = Arrangement.spacedBy(CapsuleGap),
                ) {
                    SoundChemTallyCard(
                        value = totalPlays,
                        label = stringResource(R.string.sound_chem_plays),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .capsuleEntrance(delayMillis = 180),
                    )
                    SoundChemTallyCard(
                        value = mostPlayedArtists.size,
                        label = stringResource(R.string.artists),
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .capsuleEntrance(delayMillis = 180),
                    )
                    SoundChemTallyCard(
                        value = mostPlayedAlbums.size,
                        label = stringResource(R.string.albums),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .weight(1f)
                            .capsuleEntrance(delayMillis = 180),
                    )
                }
                Spacer(Modifier.height(CapsuleGap))
            }

            item(key = "insights") {
                // Days so far for this month, the whole month for one that is over.
                val days = if (monthIndex == 0) LocalDateTime.now().dayOfMonth else selectedMonth.toLocalDate().lengthOfMonth()
                SoundChemInsights(
                    dailyAverageMinutes = totalMinutes / days.coerceAtLeast(1),
                    onRepeatTitle = topSong?.title,
                    onRepeatPlays = topSong?.songCountListened ?: 0,
                    artistCount = mostPlayedArtists.size,
                    modifier = Modifier
                        .padding(horizontal = CapsuleGutter)
                        .capsuleEntrance(delayMillis = 200),
                )
                Spacer(Modifier.height(CapsuleGap))
            }

            if (mostPlayedArtists.size >= 2) {
                item(key = "breakdown") {
                    SoundChemBreakdownCard(
                        artists = mostPlayedArtists.take(5),
                        onArtistClick = { navController.navigate("artist/${it.id}") },
                        modifier = Modifier
                            .padding(horizontal = CapsuleGutter)
                            .capsuleEntrance(delayMillis = 240),
                    )
                }
            }

            // ── The detail, below the fold ────────────────────────────────────
            item(key = "songsHeader") {
                Spacer(Modifier.height(12.dp))
                SoundChemSectionTitle(
                    text = "${mostPlayedSongsStats.size} ${stringResource(id = R.string.songs)}",
                )
            }

            itemsIndexed(
                items = mostPlayedSongsStats,
                key = { _, song -> song.id },
            ) { index, song ->
                ListItem(
                    title = song.title,
                    subtitle = joinByBullet(
                        pluralStringResource(
                            R.plurals.n_time,
                            song.songCountListened,
                            song.songCountListened,
                        ),
                        makeTimeString(song.timeListened),
                    ),
                    thumbnailContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SoundChemRank(index + 1)
                            Spacer(Modifier.width(8.dp))
                            ItemThumbnail(
                                thumbnailUrl = song.thumbnailUrl,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = WatchEndpoint(song.id),
                                            preloadItem = mostPlayedSongs[index].toMediaMetadata(),
                                        ),
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = mostPlayedSongs[index],
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                        .animateItem()
                )
            }

            item(key = "artistsHeader") {
                SoundChemSectionTitle(
                    text = "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}",
                )
            }

            itemsIndexed(
                items = mostPlayedArtists.chunked(2),
                key = { _, rowArtists -> rowArtists.first().id },
            ) { _, rowArtists ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowArtists.forEach { artist ->
                        LocalArtistsGrid(
                            title = artist.artist.name,
                            subtitle = joinByBullet(
                                pluralStringResource(
                                    R.plurals.n_time,
                                    artist.songCount,
                                    artist.songCount
                                ),
                                makeTimeString(artist.timeListened?.toLong()),
                            ),
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("artist/${artist.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            ArtistMenu(
                                                originalArtist = artist,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                )
                                .animateItem(),
                        )
                    }
                    repeat(2 - rowArtists.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item(key = "albums") {
                SoundChemSectionTitle(
                    text = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
                )

                if (mostPlayedAlbums.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier,
                    ) {
                        itemsIndexed(
                            items = mostPlayedAlbums,
                            key = { _, album -> album.id },
                        ) { index, album ->
                            LocalAlbumsGrid(
                                title = "${index + 1}. ${album.album.title}",
                                subtitle =
                                joinByBullet(
                                    pluralStringResource(
                                        R.plurals.n_time,
                                        album.songCountListened!!,
                                        album.songCountListened
                                    ),
                                    makeTimeString(album.timeListened?.toLong()),
                                ),
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${album.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }

        // FAB to shuffle most played songs
        if (mostPlayedSongs.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.most_played_songs),
                            items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled()
                        )
                    )
                }
            )
        }

        TopAppBar(
            title = { com.ozyern.exhale.ui.component.HeadingStyle {
                Text(
                    text = stringResource(R.string.sound_chem),
                    fontWeight = FontWeight.Bold,
                )
            } },
            navigationIcon = {
                LiquidBackButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                    icon = R.drawable.chevron_back,
                )
            },
            actions = {
                IconButton(
                    onClick = { navController.navigate("year_in_music") },
                    onLongClick = { }
                ) {
                    Icon(
                        painterResource(R.drawable.calendar_today),
                        contentDescription = stringResource(R.string.year_in_music),
                    )
                }
                IconButton(
                    onClick = ::shareCapsule,
                    onLongClick = { }
                ) {
                    Icon(
                        painterResource(R.drawable.share),
                        contentDescription = stringResource(R.string.sound_chem_share_action),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

/**
 * The capsule's title: the month in full, heavy weight, with the year trailing it in a quieter ink
 * — and the only two controls on the page.
 */
@Composable
private fun SoundChemMonthHeader(
    month: String,
    year: String,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CapsuleGutter + 4.dp, end = CapsuleGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = month,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = year,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }

        MonthStepButton(enabled = canGoOlder, flipped = true, onClick = onOlder)
        Spacer(Modifier.size(8.dp))
        MonthStepButton(enabled = canGoNewer, flipped = false, onClick = onNewer)
    }
}

/**
 * One glass chevron. [flipped] rotates the single chevron asset 180° rather than shipping a mirror
 * of it, and a disabled button stays visible at low opacity so the pair never reflows when you
 * reach either end of your history.
 */
@Composable
private fun MonthStepButton(
    enabled: Boolean,
    flipped: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .liquidGlassSurface(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.chevron_right),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    rotationZ = if (flipped) 180f else 0f
                    alpha = if (enabled) 1f else 0.25f
                },
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
