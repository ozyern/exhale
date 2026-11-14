/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.playlist

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.chromeShadow
import com.ozyern.exhale.ui.utils.appTopBarWindowInsets
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.ozyern.exhale.ui.ios.compat.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import com.ozyern.exhale.ui.ios.compat.Button
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField as TextField
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFArrowClockwise
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFXmark
import androidx.compose.material3.TextFieldDefaults
import com.ozyern.exhale.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ozyern.exhale.ui.component.buildAlphabetSectionIndex
import com.ozyern.exhale.ui.component.ListScrollRail
import com.ozyern.exhale.ui.component.LargeScreenTitle
import com.ozyern.exhale.ui.component.AnimatedPlayPauseIcon
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.utils.rememberHeroZoom
import com.ozyern.exhale.ui.utils.heroPullZoom
import com.ozyern.exhale.ui.utils.listOverscroll
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import org.koin.androidx.compose.koinViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.SongSortDescendingKey
import com.ozyern.exhale.constants.SongSortType
import com.ozyern.exhale.constants.SongSortTypeKey
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.ui.component.EmptyPlaceholder
import com.ozyern.exhale.ui.component.ExpandableText
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.SongListItem
import com.ozyern.exhale.ui.component.SortHeader
import com.ozyern.exhale.ui.menu.CachePlaylistMenu
import com.ozyern.exhale.ui.menu.SelectionSongMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.listItemShape
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.CachePlaylistViewModel
import com.ozyern.exhale.ui.component.rememberHeroTint
import com.ozyern.exhale.ui.player.dimmed
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.HeroTintedContent
import com.ozyern.exhale.ui.component.GlassComponent
import com.ozyern.exhale.ui.component.LocalGlassEffectConfig
import com.ozyern.exhale.ui.component.withFixedStyle
import com.ozyern.exhale.ui.component.isGlassAllowed
import androidx.compose.ui.graphics.isSpecified
import com.ozyern.exhale.LocalAmbientBackgroundColor
import com.ozyern.exhale.ui.component.asSoftIconTint
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.ui.component.liquidGlass
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.component.LocalAppBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.layerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.component.GlassCircleButton
import com.ozyern.exhale.ui.component.ChromeScrim
import com.ozyern.exhale.ui.component.rememberChromeScrimProgress
import java.time.LocalDateTime
import com.ozyern.exhale.playback.DownloadTarget
import com.ozyern.exhale.playback.downloadSongs
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CachePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: CachePlaylistViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val cachedSongs by viewModel.cachedSongs.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val sortedSongs = remember(cachedSongs, sortType, sortDescending) {
        val sorted = when (sortType) {
            SongSortType.CREATE_DATE -> cachedSongs.sortedBy { it.song.dateDownload ?: LocalDateTime.MIN }
            SongSortType.NAME -> cachedSongs.sortedBy { it.song.title }
            SongSortType.ARTIST -> cachedSongs.sortedBy { song ->
                song.artists.joinToString(separator = "") { it.name }
            }
            SongSortType.PLAY_TIME -> cachedSongs.sortedBy { it.song.totalPlayTime }
        }
        if (sortDescending) sorted.reversed() else sorted
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val exitSearch = {
        focusManager.clearFocus()
        isSearching = false
        query = TextFieldValue()
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (isSearching) {
        BackHandler(onBack = exitSearch)
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val filteredSongs = remember(sortedSongs, query) {
        if (query.text.isEmpty()) sortedSongs
        else sortedSongs.filter { song ->
            song.title.contains(query.text, true) ||
                song.artists.any { it.name.contains(query.text, true) }
        }
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val heroUrl = filteredSongs.firstOrNull()?.thumbnailUrl
    // Instant seed: route thumb -> live tile thumb. Paints mesh bg frame one.
    val instantCacheThumb = com.ozyern.exhale.ui.component.detailSeed(
        viewModel.thumbArg,
        com.ozyern.exhale.ui.utils.pendingSharedArtworkThumbFor(
            navController.currentBackStackEntry?.arguments?.getString("playlist")
        ),
        heroUrl,
    )
    val bgCacheUrl = instantCacheThumb ?: heroUrl
    val cacheChrome = com.ozyern.exhale.ui.component.rememberDetailChromeEntrance(iconCount = 1)
    val appearance = com.ozyern.exhale.ui.component.rememberPlaylistAppearance(bgCacheUrl, 0xFF30D158.toInt())
    val tint = appearance.background
    com.ozyern.exhale.PublishAmbientBackgroundColor(tint)
    val onTint = appearance.foreground

    // Keep top-bar glass fixed; the global preference controls the nav bar and mini player.
    val glassConfig = LocalGlassEffectConfig.current.withFixedStyle()
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val heroBackdrop = rememberLayerBackdrop()
    val hazeState = remember { HazeState() }

    // Capture the tinted list behind sibling chrome; an opaque capture avoids a doubled image.
    val listBackdrop = rememberLayerBackdrop()
    val backdropFreeze = rememberBackdropFreeze()
    val heroZoom = rememberHeroZoom()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint)
    ) {
        HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
        val chromeShape = ContinuousRoundedRectangle(percent = 50)

        Box(modifier = Modifier.fillMaxSize()) {
            // Capture through a plain Box; LazyColumn's recycled item layers do not flatten reliably.
            Box(modifier = Modifier
            .nestedScroll(backdropFreeze.connection)

            // Cache list content so sibling redraws do not re-record the backdrop.
            .graphicsLayer()
            .layerBackdrop(listBackdrop, frozen = backdropFreeze.frozen)
            .graphicsLayer()
            .hazeSource(hazeState)) {
                com.ozyern.exhale.ui.component.ArtworkMeshBackground(bgCacheUrl, paused = { lazyListState.isScrollInProgress }, appearance = appearance)
            LazyColumn(
                state = lazyListState,
                overscrollEffect = heroZoom.listOverscroll(),
                modifier = Modifier.heroPullZoom(heroZoom),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                    item(key = "playlist_header") {
                        CachePlaylistHeader(
                            onStartSearch = { isSearching = true },
                            searchValue = query.takeIf { isSearching },
                            onSearchValueChange = { query = it },
                            onSearchImeAction = { focusManager.clearFocus() },
                            searchFocusRequester = focusRequester,
                            songs = filteredSongs,
                            context = context,
                            menuState = menuState,
                            modifier = Modifier.animateItem()
                        )
                    }

                if (filteredSongs.isEmpty() && !isSearching) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.padding(top = 100.dp)
                        )
                    }
                }

                if (filteredSongs.isEmpty() && isSearching) {
                    item(key = "no_results") {
                        EmptyPlaceholder(
                            icon = R.drawable.search,
                            text = stringResource(R.string.no_results_found),
                            modifier = Modifier.padding(top = 100.dp)
                        )
                    }
                } else {


                    if (filteredSongs.isNotEmpty()) {
                        item(key = "sort_header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .animateItem(),
                            ) {
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            SongSortType.NAME -> R.string.sort_by_name
                                            SongSortType.ARTIST -> R.string.sort_by_artist
                                            SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        filteredSongs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "song_row" }
                    ) { index, song ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(song.id)
                            } else {
                                selection.remove(song.id)
                            }
                        }

                        SongListItem(
                            song = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            flat = true,
                            secondaryAdditiveChrome = onTint == androidx.compose.ui.graphics.Color.White,
                            subtitleAdditiveChrome = false,
                            subtitleColor = onTint.copy(alpha = 0.78f),
                            shape = listItemShape(index, filteredSongs.size),
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = song.id in selection,
                                        onCheckedChange = onCheckedChange
                                    )
                                } else {
                                    IconButton(onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                                isFromCache = true,
                                            )
                                        }
                                    }) {
                                        SymbolIcon(symbol = sf("ellipsis"), contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .combinedBounceClick(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(song.id !in selection)
                                        } else if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = "Cache Songs",
                                                    items = cachedSongs.map { it.toMediaItem() },
                                                    startIndex = cachedSongs.indexOfFirst { it.id == song.id }
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
            }

            ListScrollRail(
                lazyListState = lazyListState,
                itemCount = filteredSongs.size,
                sectionIndexMap = when (sortType) {
                    SongSortType.NAME ->
                        remember(filteredSongs) {
                            buildAlphabetSectionIndex(filteredSongs) { it.title }
                        }

                    SongSortType.ARTIST ->
                        remember(filteredSongs) {
                            buildAlphabetSectionIndex(filteredSongs) { song ->
                                song.artists.firstOrNull()?.name.orEmpty()
                            }
                        }

                    else -> null
                },
            )

            val chromeScrimProgress = rememberChromeScrimProgress(lazyListState)
            // Sample the list's own attached capture (a sibling, so no
            // RenderNode cycle) rather than the ambient unattached heroBackdrop,
            // whose drawBackdrop early-returns and left this chrome flat.
            CompositionLocalProvider(LocalAppBackdrop provides listBackdrop) {
            com.ozyern.exhale.ui.component.TopFadeBlur(hazeState, tint, Modifier.align(Alignment.TopCenter))
            // Built inside the provider so liquidGlass captures listBackdrop at
            // composition time, not the ambient unattached backdrop.
            val chromeBackgroundModifier = if (useGlass) {
                Modifier.chromeShadow(chromeShape).liquidGlass(config = glassConfig, shape = chromeShape)
            } else {
                Modifier.background(onTint.copy(alpha = 0.15f), chromeShape)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
            ChromeScrim(progress = chromeScrimProgress)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(appTopBarWindowInsets())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inSelectMode) {
                    GlassCircleButton(onClick = onExitSelectionMode) {
                        SymbolIcon(symbol = sf("SFXmark"), contentDescription = null)
                    }

                    Text(
                        text = pluralStringResource(R.plurals.n_selected, selection.size, selection.size),
                        style = AppTheme.typography.titleMedium,
                        color = onTint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    com.ozyern.exhale.ui.component.GlassPillRow(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(chromeShape)
                            .then(com.ozyern.exhale.ui.component.pillGlassBackground(chromeShape))
                            .padding(horizontal = 8.dp),
                    ) {
                        Checkbox(
                            checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                            onCheckedChange = {
                                if (selection.size == filteredSongs.size) {
                                    selection.clear()
                                } else {
                                    selection.clear()
                                    selection.addAll(filteredSongs.map { it.id })
                                }
                            }
                        )
                        val ambientColor = LocalAmbientBackgroundColor.current.value
                        IconButton(
                            enabled = selection.isNotEmpty(),
                            onClick = {
                                menuState.show {
                                    SelectionSongMenu(
                                        songSelection = filteredSongs.filter { it.id in selection },
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode
                                    )
                                }
                            }
                        ) {
                            SymbolIcon(symbol = sf("ellipsis"), contentDescription = null, modifier = Modifier.additiveChrome(useGlass && ambientColor.isSpecified), tint = if (useGlass && ambientColor.isSpecified) ambientColor.asSoftIconTint() else onTint)
                        }
                    }
                } else {
                    GlassCircleButton(
                        onClick = { if (isSearching) exitSearch() else navController.navigateUp() },
                        onLongClick = { if (isSearching) exitSearch() else navController.backToMain() },
                        modifier = with(cacheChrome) {
                            Modifier.backEnter().then(com.ozyern.exhale.ui.component.backExitModifier())
                        },
                    ) {
                        SymbolIcon(symbol = sf("chevron.left"), contentDescription = null)
                    }

                    Text(
                        text = stringResource(R.string.cached_playlist),
                        color = onTint,
                        style = AppTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )

                }
            }
            }
            }
        }
      }
    }
}

@Composable
private fun CachePlaylistHeader(
    songs: List<Song>,
    context: android.content.Context,
    menuState: com.ozyern.exhale.ui.component.MenuState,
    modifier: Modifier = Modifier,
    onStartSearch: () -> Unit = {},
    searchValue: TextFieldValue? = null,
    onSearchValueChange: (TextFieldValue) -> Unit = {},
    onSearchImeAction: () -> Unit = {},
    searchFocusRequester: FocusRequester? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
val cacheLength = remember(songs) { songs.fastSumBy { it.song.duration } }
    val downloads by com.ozyern.exhale.LocalDownloadUtil.current.get().downloads.collectAsStateWithLifecycle()
    val title = context.getString(R.string.cached_playlist)
    val artwork = com.ozyern.exhale.ui.utils.pendingSharedArtworkThumbFor(com.ozyern.exhale.ui.utils.LocalMorphArtworkId.current)
        ?: songs.firstOrNull()?.thumbnailUrl
    val appearance = com.ozyern.exhale.ui.component.rememberPlaylistAppearance(artwork)
    com.ozyern.exhale.ui.component.CollectionDetailHeader(
        title = title,
        artwork = artwork,
        metadata = pluralStringResource(R.plurals.n_song, songs.size, songs.size) +
            if (cacheLength > 0) " \u00b7 " + makeTimeString(cacheLength * 1000L) else "",
        foreground = appearance.foreground,
        playing = isPlaying && songs.any { it.id == mediaMetadata?.id },
        enabled = songs.isNotEmpty(),
        onPlay = {
            if (songs.any { it.id == mediaMetadata?.id }) playerConnection.togglePlayPause()
            else playerConnection.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }))
        },
        onShuffle = { playerConnection.playQueue(ListQueue(title = title, items = songs.shuffled().map { it.toMediaItem() })) },
        onSearch = onStartSearch,
        searchValue = searchValue,
        onSearchValueChange = onSearchValueChange,
        onSearchImeAction = onSearchImeAction,
        searchFocusRequester = searchFocusRequester,
        onRightAction = {
                    menuState.show {
                        CachePlaylistMenu(
                            downloadState = Download.STATE_STOPPED,
                            onQueue = {
                                playerConnection.addToQueue(
                                    songs.map { it.toMediaItem() }
                                )
                            },
                            onDownload = {
                                downloadSongs(
                                    context,
                                    songs.map { DownloadTarget(it.id, it.title, it.artists.firstOrNull()?.name, it.song.duration) },
                                    downloads,
                                )
                            },
                            onDismiss = { menuState.dismiss() }
                        )
                    }
                },
        modifier = modifier.padding(top = com.ozyern.exhale.ui.component.topBarContentPadding()),
    )
}
