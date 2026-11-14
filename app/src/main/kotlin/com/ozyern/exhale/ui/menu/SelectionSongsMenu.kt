/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.menu

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.menuBottomInset
import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import com.ozyern.exhale.ui.ios.compat.CircularProgressIndicator
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.offline.Download
import com.music.innertube.YouTube
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.LocalSyncUtils
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.PlaylistSongMap
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.IosMenuGroup
import com.ozyern.exhale.ui.component.IosMenuItemData
import com.ozyern.exhale.ui.component.NewAction
import com.ozyern.exhale.ui.component.NewActionGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import com.ozyern.exhale.playback.DownloadTarget
import com.ozyern.exhale.playback.combinedDownloadState
import com.ozyern.exhale.playback.cancelDownloads
import com.ozyern.exhale.playback.downloadSongs
import com.ozyern.exhale.playback.removeDownloads
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionSongMenu(
    songSelection: List<Song>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    songPosition: List<PlaylistSongMap>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val downloadUtil = LocalDownloadUtil.current.get()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current.get()
    val listenTogetherManager = com.ozyern.exhale.LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && listenTogetherManager.isHost == false

    val allInLibrary by remember {
        mutableStateOf(
            songSelection.all {
                it.song.inLibrary != null
            },
        )
    }

    val allLiked by remember(songSelection) {
        mutableStateOf(
            songSelection.isNotEmpty() && songSelection.all {
                it.song.liked
            },
        )
    }

    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val downloadState = remember(songSelection, downloads) {
        combinedDownloadState(songSelection, downloads) { it.id }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                songSelection.forEach { song ->
                    playlist.playlist.browseId?.let { browseId ->
                        YouTube.addToPlaylist(browseId, song.id)
                    }
                }
            }
            songSelection.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = AppTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        removeDownloads(context, songSelection.map { it.song.id })
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + menuBottomInset(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOfNotNull(
                    if (!isGuest) NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("play.fill"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.play),
                        onClick = {
                            onDismiss()
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Selection",
                                    items = songSelection.map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                        }
                    ) else null,
                    if (!isGuest) NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("shuffle"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.shuffle),
                        onClick = {
                            onDismiss()
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Selection",
                                    items = songSelection.shuffled().map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                        }
                    ) else null,
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }
        item {
            IosMenuGroup(
                items = buildList {
                    if (!isGuest) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.play_next)) },
                                description = { Text(text = stringResource(R.string.play_next_desc)) },
                                icon = {
                                    SymbolIcon(symbol = sf("text.line.first.and.arrowtriangle.forward"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.playNext(songSelection.map { it.toMediaItem() })
                                    clearAction()
                                }
                            )
                        )
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.shuffle)) },
                                description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                                icon = {
                                    SymbolIcon(symbol = sf("shuffle"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                }
                            )
                        )
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.add_to_queue)) },
                                description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                                icon = {
                                    SymbolIcon(symbol = sf("music.note.list"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                                    clearAction()
                                }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_playlist)) },
                            description = { Text(text = stringResource(R.string.add_to_playlist_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null)
                            },
                            onClick = {
                                showChoosePlaylistDialog = true
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (allInLibrary) R.string.remove_from_library else R.string.add_to_library
                                    )
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = 
                                        if (allInLibrary) sf("text.badge.checkmark") else sf("text.badge.plus")
                                    , contentDescription = null)
                            },
                            onClick = {
                                if (allInLibrary) {
                                    database.query {
                                        songSelection.forEach { song ->
                                            inLibrary(song.id, null)
                                        }
                                    }
                                    coroutineScope.launch {
                                        songSelection.forEach { song ->
                                            YouTube.toggleSongLibrary(song.id, false)
                                        }
                                    }
                                } else {
                                    database.transaction {
                                        songSelection.forEach { song ->
                                            insert(song.toMediaMetadata())
                                            inLibrary(song.id, LocalDateTime.now())
                                        }
                                    }
                                    coroutineScope.launch {
                                        songSelection.filter { it.song.inLibrary == null }
                                            .forEach { song ->
                                                YouTube.toggleSongLibrary(song.id, true)
                                            }
                                    }
                                }
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    add(
                        when (downloadState) {
                            Download.STATE_COMPLETED -> {
                                IosMenuItemData(
                                    title = {
                                        Text(
                                            text = stringResource(R.string.remove_download)
                                        )
                                    },
                                    icon = {
                                        SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null)
                                    },
                                    onClick = {
                                        showRemoveDownloadDialog = true
                                    }
                                )
                            }
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                IosMenuItemData(
                                    title = { Text(text = stringResource(R.string.downloading)) },
                                    icon = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    },
                                    onClick = {
                                        cancelDownloads(
                                            context,
                                            songSelection.map { it.id },
                                            downloads,
                                        )
                                    }
                                )
                            }
                            else -> {
                                IosMenuItemData(
                                    title = { Text(text = stringResource(R.string.action_download)) },
                                    icon = {
                                        SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null)
                                    },
                                    onClick = {
                                        downloadSongs(
                                            context,
                                            songSelection.map { DownloadTarget(it.id, it.song.title) },
                                            downloads,
                                        )
                                    }
                                )
                            }
                        }
                    )
                    add(
                        IosMenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (allLiked) R.string.dislike_all else R.string.like_all
                                    )
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = 
                                        if (allLiked) sf("heart.fill") else sf("heart")
                                    , contentDescription = null)
                            },
                            onClick = {
                                val allLiked = songSelection.all { it.song.liked }
                                onDismiss()
                                database.query {
                                    songSelection.forEach { song ->
                                        if ((!allLiked && !song.song.liked) || allLiked) {
                                            val s = song.song.toggleLike()
                                            update(s)
                                            syncUtils.likeSong(s)
                                        }
                                    }
                                }
                            }
                        )
                    )
                    if (songPosition?.isNotEmpty() == true) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    var i = 0
                                    database.query {
                                        songPosition.forEach { cur ->
                                            move(cur.playlistId, cur.position - i, Int.MAX_VALUE)
                                            delete(cur.copy(position = Int.MAX_VALUE))
                                            i++
                                        }
                                    }
                                    clearAction()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionMediaMetadataMenu(
    songSelection: List<MediaMetadata>,
    currentItems: List<Timeline.Window>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val downloadUtil = LocalDownloadUtil.current.get()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = com.ozyern.exhale.LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && listenTogetherManager.isHost == false

    val allLiked by remember(songSelection) {
        mutableStateOf(songSelection.isNotEmpty() && songSelection.all { it.liked })
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            songSelection.map {
                database.insert(it)
                it.id
            }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val downloadState = remember(songSelection, downloads) {
        combinedDownloadState(songSelection, downloads) { it.id }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = AppTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        removeDownloads(context, songSelection.map { it.id })
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + menuBottomInset(),
        ),
    ) {
        item {
            IosMenuGroup(
                items = buildList {
                    if (currentItems.isNotEmpty() && !isGuest) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    var i = 0
                                    currentItems.forEach { cur ->
                                        if (playerConnection.player.availableCommands.contains(
                                                Player.COMMAND_CHANGE_MEDIA_ITEMS
                                            )
                                        ) {
                                            playerConnection.player.removeMediaItem(cur.firstPeriodIndex - i++)
                                        }
                                    }
                                    clearAction()
                                }
                            )
                        )
                    }
                    if (!isGuest) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.play)) },
                                icon = {
                                    SymbolIcon(symbol = sf("play.fill"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                }
                            )
                        )
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.shuffle)) },
                                icon = {
                                    SymbolIcon(symbol = sf("shuffle"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                }
                            )
                        )
                        if (!isGuest) {
                            add(
                                IosMenuItemData(
                                    title = { Text(text = stringResource(R.string.add_to_queue)) },
                                    icon = {
                                        SymbolIcon(symbol = sf("music.note.list"), contentDescription = null)
                                    },
                                    onClick = {
                                        onDismiss()
                                        playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                                        clearAction()
                                    }
                                )
                            )
                        }
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_playlist)) },
                            icon = {
                                SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null)
                            },
                            onClick = {
                                showChoosePlaylistDialog = true
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    add(
                        IosMenuItemData(
                            title = {
                                Text(
                                    text = stringResource(R.string.like_all)
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = 
                                        if (allLiked) sf("heart.fill") else sf("heart")
                                    , contentDescription = null)
                            },
                            onClick = {
                                database.query {
                                    if (allLiked) {
                                        songSelection.forEach { song ->
                                            update(song.toSongEntity().toggleLike())
                                        }
                                    } else {
                                        songSelection.filter { !it.liked }.forEach { song ->
                                            update(song.toSongEntity().toggleLike())
                                        }
                                    }
                                }
                            }
                        )
                    )
                    add(
                        when (downloadState) {
                            Download.STATE_COMPLETED -> {
                                IosMenuItemData(
                                    title = {
                                        Text(
                                            text = stringResource(R.string.remove_download),
                                            color = AppTheme.colorScheme.surface
                                        )
                                    },
                                    icon = {
                                        SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null, tint = AppTheme.colorScheme.surface)
                                    },
                                    onClick = {
                                        showRemoveDownloadDialog = true
                                    },
                                )
                            }
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                IosMenuItemData(
                                    title = { Text(text = stringResource(R.string.downloading)) },
                                    icon = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    },
                                    onClick = {
                                        cancelDownloads(
                                            context,
                                            songSelection.map { it.id },
                                            downloads,
                                        )
                                    }
                                )
                            }
                            else -> {
                                IosMenuItemData(
                                    title = { Text(text = stringResource(R.string.action_download)) },
                                    icon = {
                                        SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null)
                                    },
                                    onClick = {
                                        downloadSongs(
                                            context,
                                            songSelection.map { DownloadTarget(it.id, it.title) },
                                            downloads,
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }
}
