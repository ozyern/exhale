/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.CONTENT_TYPE_HEADER
import com.ozyern.exhale.constants.CONTENT_TYPE_SONG
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.SongFilter
import com.ozyern.exhale.constants.SongFilterKey
import com.ozyern.exhale.constants.SongSortDescendingKey
import com.ozyern.exhale.constants.SongSortType
import com.ozyern.exhale.constants.SongSortTypeKey
import com.ozyern.exhale.constants.YtmSyncKey
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.ui.component.ChipsRow
import com.ozyern.exhale.ui.component.HideOnScrollFAB
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.SongListItem
import com.ozyern.exhale.ui.component.SortHeader
import com.ozyern.exhale.ui.menu.SelectionSongMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.utils.ItemWrapper
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val songs by viewModel.allSongs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    // Music on this phone needs the audio permission, asked for here, when it is first wanted.
    val screenContext = androidx.compose.ui.platform.LocalContext.current
    var localAllowed by remember { mutableStateOf(com.ozyern.exhale.utils.LocalMediaScanner.hasPermission(screenContext)) }
    val askForLocal = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> localAllowed = granted }
    LaunchedEffect(filter, localAllowed) {
        if (filter == SongFilter.LOCAL && localAllowed) viewModel.scanLocal()
    }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val wrappedSongs = songs.map { item -> ItemWrapper(item) }.toMutableList()
    var selection by remember {
        mutableStateOf(false)
    }

    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .pullToRefresh(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = { if (ytmSync) viewModel.refresh(filter) }
                ),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource(R.string.songs)) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = onDeselect,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = ""
                            )
                        },
                    )
                    ChipsRow(
                        chips =
                        listOf(
                            SongFilter.LIKED to stringResource(R.string.filter_liked),
                            SongFilter.LIBRARY to stringResource(R.string.filter_library),
                            SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                            SongFilter.LOCAL to stringResource(R.string.filter_local),
                        ),
                        currentValue = filter,
                        onValueUpdate = {
                            filter = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (filter == SongFilter.LOCAL && !localAllowed) {
                item(key = "local_permission", contentType = CONTENT_TYPE_HEADER) {
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(20.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Play the music on this phone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            "Exhale needs to see your audio files to list them here. They stay on your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.Button(
                            onClick = { askForLocal.launch(com.ozyern.exhale.utils.LocalMediaScanner.PermissionName) },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Allow access") }
                    }
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selection) {
                        val count = wrappedSongs.count { it.isSelected }
                        IconButton(
                            onClick = { selection = false },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                        Text(
                            text = pluralStringResource(R.plurals.n_song, count, count),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (count == wrappedSongs.size) {
                                    wrappedSongs.forEach { it.isSelected = false }
                                } else {
                                    wrappedSongs.forEach { it.isSelected = true }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all),
                                contentDescription = null,
                            )
                        }

                        IconButton(
                            onClick = {
                                menuState.show {
                                    SelectionSongMenu(
                                        songSelection = wrappedSongs.filter { it.isSelected }
                                            .map { it.item },
                                        onDismiss = menuState::dismiss,
                                        clearAction = { selection = false },
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    songs.size,
                                    songs.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            val filteredSongs = if (hideExplicit) {
                wrappedSongs.filter { !it.item.song.explicit }
            } else {
                wrappedSongs
            }
            itemsIndexed(
                items = filteredSongs,
                key = { _, item -> item.item.song.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG },
            ) { index, songWrapper ->
                SongListItem(
                    song = songWrapper.item,
                    showInLibraryIcon = true,
                    isActive = songWrapper.item.id == mediaMetadata?.id,
                    isPlaying = isPlaying,

                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = songWrapper.item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    isSelected = songWrapper.isSelected && selection,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (!selection) {
                                    if (songWrapper.item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = context.getString(R.string.queue_all_songs),
                                                items = songs.map { it.toMediaItem() },
                                                startIndex = index,
                                            ),
                                        )
                                    }
                                } else {
                                    songWrapper.isSelected = !songWrapper.isSelected
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!selection) {
                                    selection = true
                                }
                                wrappedSongs.forEach {
                                    it.isSelected = false
                                } // Clear previous selections
                                songWrapper.isSelected = true // Select current item
                            },
                        )
                        .animateItem(),
                )
            }
        }

        HideOnScrollFAB(
            visible = songs.isNotEmpty() == true,
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = context.getString(R.string.queue_all_songs),
                        items = songs.shuffled().map { it.toMediaItem() },
                    ),
                )
            },
        )

        PullToRefreshDefaults.Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}
