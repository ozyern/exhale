/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.musicchem

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.FrostedTopBar
import com.ozyern.exhale.ui.component.GlassCircleButton
import com.ozyern.exhale.ui.component.HeroBackground
import com.ozyern.exhale.ui.component.LargeScreenTitle
import com.ozyern.exhale.ui.component.LocalAlbumsGrid
import com.ozyern.exhale.ui.component.LocalArtistsGrid
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.LocalSongsGrid
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.component.rememberHeroSource
import com.ozyern.exhale.ui.component.rememberTitleCollapseProgressState
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.ArtistMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.screens.OptionStats
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.HeroTintedContent
import com.ozyern.exhale.ui.utils.albumRoute
import com.ozyern.exhale.ui.utils.appTopBarWindowInsets
import com.ozyern.exhale.ui.utils.artistRoute
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.ui.utils.bleedStart
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.ui.utils.heroPullZoom
import com.ozyern.exhale.ui.utils.listOverscroll
import com.ozyern.exhale.ui.utils.plusStart
import com.ozyern.exhale.ui.utils.rememberHeroZoom
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.viewmodels.StatsViewModel
import dev.chrisbanes.haze.hazeSource
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Your Music Chem: the month's listening as one capsule.
 *
 * A capsule is a period, so the period is fixed to the month: the month is the heading and the
 * only control is a pair of chevrons through the months there is history for. Below it the deck
 * says each number once and large: time listened, the top artist and song, the share one artist
 * took, the tallies, a few insights and the top artists side by side. The full ranked rails of
 * songs, artists and albums follow for anyone who wants the detail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicChemScreen(
    navController: NavController,
    // Its own instance, so the stats screen keeps its period while this one is month by month.
    viewModel: StatsViewModel = koinViewModel(key = "music_chem"),
) {
    // Set before anything collects: the flows start lazily, so the first query already runs
    // against the month rather than being corrected after one against the wrong window.
    remember(viewModel) {
        if (viewModel.selectedOption.value != OptionStats.MONTHS) {
            viewModel.selectedOption.value = OptionStats.MONTHS
            viewModel.indexChips.value = 0
        }
    }

    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val monthIndex by viewModel.indexChips.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val songStats by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val artists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val albums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()

    // Every month from this one back to the month of the first play. Index 0 is this month,
    // which is the offset `statToPeriod(MONTHS, n)` takes, so the index is the query parameter.
    val currentMonth = remember { LocalDateTime.now().withDayOfMonth(1) }
    val months: List<LocalDateTime> = remember(firstEvent, currentMonth) {
        val oldest = firstEvent?.event?.timestamp?.withDayOfMonth(1)
        if (oldest == null) {
            listOf(currentMonth)
        } else {
            generateSequence(currentMonth) { it.minusMonths(1) }
                .takeWhile { !it.isBefore(oldest.toLocalDate().atStartOfDay()) }
                .toList()
                .ifEmpty { listOf(currentMonth) }
        }
    }
    val selectedMonth = months.getOrNull(monthIndex) ?: currentMonth
    val monthName = remember(selectedMonth) { DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()).format(selectedMonth) }
    val yearName = selectedMonth.year.toString()
    val periodText = "$monthName $yearName"

    val totalMillis = remember(songStats) { songStats.sumOf { it.timeListened ?: 0L } }
    val totalMinutes = totalMillis / 60_000L
    val totalPlays = remember(songStats) { songStats.sumOf { it.songCountListened } }
    val topArtist = artists.firstOrNull()
    val topSong = songStats.firstOrNull()
    val topArtistMillis = topArtist?.timeListened?.toLong() ?: 0L
    // Artist and song totals come from separate queries: clamp rather than print an impossible share.
    val artistSharePercent = if (totalMillis > 0L) ((topArtistMillis * 100.0) / totalMillis).toInt().coerceIn(0, 100) else 0
    val hasData = songStats.isNotEmpty() || artists.isNotEmpty()

    fun shareCapsule() {
        val summary = buildString {
            appendLine(context.getString(R.string.music_chem))
            appendLine(periodText)
            appendLine()
            append(context.getString(R.string.music_chem_time_listened)).append(": ")
            appendLine(context.getString(R.string.music_chem_minutes_value, formatCount(totalMinutes)))
            topArtist?.let { append(context.getString(R.string.music_chem_top_artist)).append(": ").appendLine(it.artist.name) }
            topSong?.let { append(context.getString(R.string.music_chem_top_song)).append(": ").appendLine(it.title) }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.music_chem_share_action))) }
    }

    fun playSong(id: String) {
        if (id == mediaMetadata?.id) {
            playerConnection.togglePlayPause()
        } else {
            playerConnection.playQueue(
                YouTubeQueue(
                    endpoint = WatchEndpoint(id),
                    preloadItem = mostPlayedSongs.firstOrNull { it.id == id }?.toMediaMetadata(),
                ),
            )
        }
    }

    val tint = AppleTokens.BgElevated
    val ink = AppleTokens.onColor(tint)
    val heroSource = rememberHeroSource(staticArt = null)
    val heroBackdrop = rememberLayerBackdrop()
    val heroZoom = rememberHeroZoom()
    val lazyListState = rememberLazyListState()
    val titleProgress = rememberTitleCollapseProgressState(lazyListState)
    val sideInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues()
        .calculateStartPadding(LocalLayoutDirection.current)
    val railPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues().plusStart(sideInset)

    HeroBackground(
        tint = tint,
        heroSource = heroSource,
        heroScale = heroZoom.scale,
        modifier = Modifier.fillMaxSize(),
    ) {
      HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
        val hazeState = remember { dev.chrisbanes.haze.HazeState() }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                overscrollEffect = heroZoom.listOverscroll(),
                modifier = Modifier.heroPullZoom(heroZoom).hazeSource(hazeState),
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item(key = "title") {
                    LargeScreenTitle(title = stringResource(R.string.music_chem), color = ink)
                }

                item(key = "month") {
                    ChemMonthHeader(
                        month = monthName,
                        year = yearName,
                        ink = ink,
                        // Older walks up the list (a higher index is further back).
                        canGoOlder = monthIndex < months.lastIndex,
                        canGoNewer = monthIndex > 0,
                        onOlder = { viewModel.indexChips.value = monthIndex + 1 },
                        onNewer = { viewModel.indexChips.value = monthIndex - 1 },
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                }

                if (!hasData) {
                    item(key = "empty") {
                        ChemEmptyState(ink = ink, modifier = Modifier.padding(horizontal = ChemGutter))
                    }
                    return@LazyColumn
                }

                item(key = "hero") {
                    ChemHero(
                        covers = songStats.map { it.thumbnailUrl }.filter { it.isNotBlank() }.distinct().take(4),
                        minutes = totalMinutes,
                        periodText = periodText,
                        modifier = Modifier.padding(horizontal = ChemGutter).chemEntrance(),
                    )
                    Spacer(Modifier.height(ChemGap))
                }

                if (topArtist != null || topSong != null) {
                    item(key = "spotlight") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ChemGap),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = ChemGutter),
                        ) {
                            topArtist?.let { artist ->
                                ChemSpotlightCard(
                                    label = stringResource(R.string.music_chem_top_artist),
                                    title = artist.artist.name,
                                    imageUrl = artist.artist.thumbnailUrl,
                                    circular = true,
                                    ink = ink,
                                    onClick = { navController.navigate(artistRoute(artist.id, artist.artist.thumbnailUrl)) },
                                    modifier = Modifier.weight(1f).chemEntrance(60),
                                )
                            }
                            topSong?.let { song ->
                                ChemSpotlightCard(
                                    label = stringResource(R.string.music_chem_top_song),
                                    title = song.title,
                                    imageUrl = song.thumbnailUrl,
                                    circular = false,
                                    ink = ink,
                                    onClick = { playSong(song.id) },
                                    modifier = Modifier.weight(1f).chemEntrance(60),
                                )
                            }
                            // A lone card keeps its half width instead of stretching across.
                            if (topArtist == null || topSong == null) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(ChemGap))
                    }
                }

                // Only when one artist really led the month: "3% of your listening" is noise.
                if (topArtist != null && artistSharePercent >= 5) {
                    item(key = "share") {
                        ChemShareCard(
                            artistName = topArtist.artist.name,
                            imageUrl = topArtist.artist.thumbnailUrl,
                            percent = artistSharePercent,
                            timeText = makeTimeString(topArtistMillis),
                            ink = ink,
                            onShare = ::shareCapsule,
                            onClick = { navController.navigate(artistRoute(topArtist.id, topArtist.artist.thumbnailUrl)) },
                            modifier = Modifier.padding(horizontal = ChemGutter).chemEntrance(120),
                        )
                        Spacer(Modifier.height(ChemGap))
                    }
                }

                item(key = "tallies") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ChemGap),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ChemGutter),
                    ) {
                        ChemTallyCard(formatCount(totalPlays.toLong()), stringResource(R.string.music_chem_plays), ink, Modifier.weight(1f).chemEntrance(180))
                        ChemTallyCard(formatCount(artists.size.toLong()), stringResource(R.string.artists), ink, Modifier.weight(1f).chemEntrance(180))
                        ChemTallyCard(formatCount(albums.size.toLong()), stringResource(R.string.albums), ink, Modifier.weight(1f).chemEntrance(180))
                    }
                    Spacer(Modifier.height(ChemGap))
                }

                item(key = "insights") {
                    // Days so far for this month; the whole month for one that is over.
                    val days = if (monthIndex == 0) LocalDateTime.now().dayOfMonth else selectedMonth.toLocalDate().lengthOfMonth()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ChemGap),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ChemGutter),
                    ) {
                        ChemTallyCard(
                            value = stringResource(R.string.music_chem_daily_average_value, formatCount(totalMinutes / days.coerceAtLeast(1))),
                            label = stringResource(R.string.music_chem_daily_average),
                            ink = ink,
                            modifier = Modifier.weight(1f).chemEntrance(200),
                        )
                        ChemTallyCard(
                            value = topSong?.let { "${it.songCountListened}×" } ?: "—",
                            label = stringResource(R.string.music_chem_on_repeat),
                            ink = ink,
                            caption = topSong?.title,
                            modifier = Modifier.weight(1f).chemEntrance(200),
                        )
                        ChemTallyCard(
                            value = formatCount(artists.size.toLong()),
                            label = stringResource(R.string.music_chem_artists_heard),
                            ink = ink,
                            modifier = Modifier.weight(1f).chemEntrance(200),
                        )
                    }
                    Spacer(Modifier.height(ChemGap))
                }

                if (artists.size >= 2) {
                    item(key = "breakdown") {
                        ChemBreakdownCard(
                            artists = artists.take(5),
                            ink = ink,
                            onArtistClick = { navController.navigate(artistRoute(it.id, it.artist.thumbnailUrl)) },
                            modifier = Modifier.padding(horizontal = ChemGutter).chemEntrance(240),
                        )
                    }
                }

                // The detail, as the same rails the rest of the app uses.
                if (songStats.isNotEmpty()) {
                    item(key = "songsTitle") {
                        Spacer(Modifier.height(12.dp))
                        NavigationTitle(title = "${songStats.size} ${stringResource(R.string.songs)}", modifier = Modifier)
                    }
                    item(key = "songs") {
                        LazyRow(contentPadding = railPadding, modifier = Modifier.bleedStart(sideInset)) {
                            itemsIndexed(items = songStats, key = { _, song -> song.id }) { index, song ->
                                LocalSongsGrid(
                                    title = "${index + 1}. ${song.title}",
                                    subtitle = joinByBullet(
                                        pluralStringResource(R.plurals.n_time, song.songCountListened, song.songCountListened),
                                        makeTimeString(song.timeListened),
                                    ),
                                    thumbnailUrl = song.thumbnailUrl,
                                    isActive = song.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedBounceClick(
                                            onClick = { playSong(song.id) },
                                            onLongClick = {
                                                val original = mostPlayedSongs.firstOrNull { it.id == song.id } ?: return@combinedBounceClick
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = original,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }

                if (artists.isNotEmpty()) {
                    item(key = "artistsTitle") {
                        NavigationTitle(title = "${artists.size} ${stringResource(R.string.artists)}", modifier = Modifier)
                    }
                    item(key = "artists") {
                        LazyRow(contentPadding = railPadding, modifier = Modifier.bleedStart(sideInset)) {
                            itemsIndexed(items = artists, key = { _, artist -> artist.id }) { index, artist ->
                                LocalArtistsGrid(
                                    title = "${index + 1}. ${artist.artist.name}",
                                    subtitle = joinByBullet(
                                        pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                                        makeTimeString(artist.timeListened?.toLong()),
                                    ),
                                    thumbnailUrl = artist.artist.thumbnailUrl,
                                    modifier = Modifier.combinedBounceClick(
                                        onClick = { navController.navigate(artistRoute(artist.id, artist.artist.thumbnailUrl)) },
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
                                    ),
                                )
                            }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item(key = "albumsTitle") {
                        NavigationTitle(title = "${albums.size} ${stringResource(R.string.albums)}", modifier = Modifier)
                    }
                    item(key = "albums") {
                        LazyRow(contentPadding = railPadding, modifier = Modifier.bleedStart(sideInset)) {
                            itemsIndexed(items = albums, key = { _, album -> album.id }) { index, album ->
                                val playCount = album.songCountListened ?: 0
                                LocalAlbumsGrid(
                                    title = "${index + 1}. ${album.album.title}",
                                    subtitle = joinByBullet(
                                        pluralStringResource(R.plurals.n_time, playCount, playCount),
                                        makeTimeString(album.timeListened),
                                    ),
                                    thumbnailUrl = album.album.thumbnailUrl,
                                    sharedArtworkId = album.id,
                                    isActive = album.id == mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedBounceClick(
                                            onClick = { navController.navigate(albumRoute(album.id, album.album.thumbnailUrl)) },
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
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            com.ozyern.exhale.ui.component.TopFadeBlur(
                hazeState = hazeState,
                pageColor = tint,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            FrostedTopBar(
                title = stringResource(R.string.music_chem),
                titleAlpha = 0f,
                titleAlphaProvider = { titleProgress.value },
                titleColor = ink,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(appTopBarWindowInsets())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GlassCircleButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    SymbolIcon(symbol = sf("arrow.left"), contentDescription = null)
                }
                Spacer(Modifier.weight(1f))
                if (hasData) {
                    GlassCircleButton(onClick = ::shareCapsule) {
                        SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = stringResource(R.string.music_chem_share_action))
                    }
                }
            }
        }
      }
    }
}
