/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.menu

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.menuBottomInset
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ozyern.exhale.ui.ios.compat.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.ListItem
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalListenTogetherManager
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.constants.ThumbnailCornerRadius
import com.ozyern.exhale.constants.ThumbnailRoundedShape
import com.ozyern.exhale.db.entities.SpeedDialItem
import com.ozyern.exhale.db.entities.PlaylistEntity
import com.ozyern.exhale.db.entities.PlaylistSongMap
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.ListDialog
import com.ozyern.exhale.ui.component.IosMenuGroup
import com.ozyern.exhale.ui.component.IosMenuItemData
import com.ozyern.exhale.ui.component.NewAction
import com.ozyern.exhale.ui.component.NewActionGrid
import com.ozyern.exhale.ui.component.YouTubeListItem
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ozyern.exhale.playback.DownloadTarget
import com.ozyern.exhale.playback.combinedDownloadState
import com.ozyern.exhale.playback.cancelDownloads
import com.ozyern.exhale.playback.downloadSongs
import com.ozyern.exhale.playback.removeDownloads
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubePlaylistMenu(
    playlist: PlaylistItem,
    songs: List<SongItem> = emptyList(),
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    selectAction: () -> Unit = {},
    canSelect: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val downloadUtil = LocalDownloadUtil.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val dbPlaylist by database.playlistByBrowseId(playlist.id).collectAsState(initial = null)
    val currentPlaylist = dbPlaylist?.playlist
    val isPlaylistLiked = currentPlaylist?.bookmarkedAt != null
    val isPinned by database.speedDialDao.isPinned(playlist.id).collectAsState(initial = false)

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showImportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<MediaMetadata>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { targetPlaylist ->
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(targetPlaylist.id).completed().getOrNull()?.songs.orEmpty()
                }.map {
                    it.toMediaMetadata()
                }
            database.transaction {
                allSongs.forEach(::insert)
            }
            coroutineScope.launch(Dispatchers.IO) {
                targetPlaylist.playlist.browseId?.let { playlistId ->
                    YouTube.addPlaylistToPlaylist(playlistId, targetPlaylist.id)
                }
            }
            allSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    YouTubeListItem(
        item = playlist,
        trailingContent = {
            if (playlist.id != "LM" && !playlist.isEditable) {
                IconButton(
                    onClick = {
                        if (currentPlaylist == null) {
                            database.transaction {
                                val playlistEntity = PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    remoteSongCount = playlist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params
                                ).toggleLike()
                                insert(playlistEntity)
                                coroutineScope.launch(Dispatchers.IO) {
                                    songs.ifEmpty {
                                        YouTube.playlist(playlist.id).completed()
                                            .getOrNull()?.songs.orEmpty()
                                    }.map { it.toMediaMetadata() }
                                        .onEach(::insert)
                                        .mapIndexed { index, song ->
                                            PlaylistSongMap(
                                                songId = song.id,
                                                playlistId = playlistEntity.id,
                                                position = index,
                                                setVideoId = song.setVideoId
                                            )
                                        }
                                        .forEach(::insert)
                                }
                            }
                        } else {
                            database.transaction {
                                update(currentPlaylist, playlist)
                                update(currentPlaylist.toggleLike())
                            }
                        }
                    }
                ) {
                    SymbolIcon(symbol = if (isPlaylistLiked) sf("heart.fill") else sf("heart"), contentDescription = null, tint = if (isPlaylistLiked) AppTheme.colorScheme.error else LocalContentColor.current)
                }
            }
        }
    )
    HorizontalDivider()

    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val downloadState = remember(songs, downloads) {
        combinedDownloadState(songs, downloads) { it.id }
    }
    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.title
                    ),
                    style = AppTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        removeDownloads(context, songs.map { it.id })
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    ImportPlaylistDialog(
        isVisible = showImportPlaylistDialog,
        onGetSong = {
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }.map {
                    it.toMediaMetadata()
                }
            database.transaction {
                allSongs.forEach(::insert)
            }
            allSongs.map { it.id }
        },
        playlistTitle = playlist.title,
        onDismiss = { showImportPlaylistDialog = false }
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(AppTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList, key = { it.id }) { song ->
                ListItem(
                    headlineContent = { Text(text = song.title) },
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(ListThumbnailSize),
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(ThumbnailRoundedShape),
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = joinByBullet(
                                song.artists.joinToString { it.name },
                                makeTimeString(song.duration * 1000L),
                            )
                        )
                    },
                )
            }
        }
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
                actions = buildList {
                    if (!isGuest) {
                        playlist.playEndpoint?.let { playEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        SymbolIcon(symbol = sf("play.fill"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                                    },
                                    text = stringResource(R.string.play),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(playEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        SymbolIcon(symbol = sf("shuffle"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                                    },
                                    text = stringResource(R.string.shuffle),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        playlist.radioEndpoint?.let { radioEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        SymbolIcon(symbol = sf("dot.radiowaves.left.and.right"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                                    },
                                    text = stringResource(R.string.start_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            IosMenuGroup(
                items = listOfNotNull(
                    if (!isGuest) {
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.play_next)) },
                            description = { Text(text = stringResource(R.string.play_next_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("text.line.first.and.arrowtriangle.forward"), contentDescription = null)
                            },
                            onClick = {
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube
                                                    .playlist(playlist.id)
                                                    .completed()
                                                    .getOrNull()
                                                    ?.songs
                                                    .orEmpty()
                                            }
                                        }.let { songs ->
                                            playerConnection.playNext(songs.map {
                                                it.copy(thumbnail = it.thumbnail.resize(1200, 1200))
                                                    .toMediaItem()
                                            })
                                        }
                                }
                                onDismiss()
                            }
                        )
                    } else null,
                    if (!isGuest) {
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_queue)) },
                            description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("music.note.list"), contentDescription = null)
                            },
                            onClick = {
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube
                                                    .playlist(playlist.id)
                                                    .completed()
                                                    .getOrNull()
                                                    ?.songs
                                                    .orEmpty()
                                            }
                                        }.let { songs ->
                                            playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                        }
                                }
                                onDismiss()
                            }
                        )
                    } else null,
                    IosMenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_playlist)) },
                        description = { Text(text = stringResource(R.string.add_to_playlist_desc)) },
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null)
                        },
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    ),
                    IosMenuItemData(
                        title = { 
                            Text(
                                text = if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial" 
                            ) 
                        },
                        icon = {
                            SymbolIcon(symbol = if (isPinned) sf("minus") else sf("plus"), contentDescription = null)
                        },
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                if (isPinned) {
                                    database.speedDialDao.delete(playlist.id)
                                } else {
                                    database.speedDialDao.insert(SpeedDialItem.fromYTItem(playlist))
                                }
                            }
                            onDismiss()
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    if (songs.isNotEmpty()) {
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
                                                songs.map { it.id },
                                                downloads,
                                            )
                                        }
                                    )
                                }
                                else -> {
                                    IosMenuItemData(
                                        title = { Text(text = stringResource(R.string.action_download)) },
                                        description = { Text(text = stringResource(R.string.download_desc)) },
                                        icon = {
                                            SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null)
                                        },
                                        onClick = {
                                            downloadSongs(
                                                context,
                                                songs.map { DownloadTarget(it.id, it.title, it.artists.firstOrNull()?.name) },
                                                downloads,
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.share)) },
                            description = { Text(text = stringResource(R.string.share_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = null)
                            },
                            onClick = {
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            }
                        )
                    )
                    if (canSelect) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.select)) },
                                icon = {
                                    SymbolIcon(symbol = sf("checklist"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    selectAction()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
