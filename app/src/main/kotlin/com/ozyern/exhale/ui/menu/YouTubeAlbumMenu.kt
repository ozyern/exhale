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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalListenTogetherManager
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ListItemHeight
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.db.entities.SpeedDialItem
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.playback.queues.YouTubeAlbumRadio
import com.ozyern.exhale.ui.component.ListDialog
import com.ozyern.exhale.ui.component.IosMenuGroup
import com.ozyern.exhale.ui.component.IosMenuItemData
import com.ozyern.exhale.ui.component.NewAction
import com.ozyern.exhale.ui.component.NewActionGrid
import com.ozyern.exhale.ui.component.SongListItem
import com.ozyern.exhale.ui.component.YouTubeListItem
import com.ozyern.exhale.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ozyern.exhale.playback.DownloadTarget
import com.ozyern.exhale.playback.combinedDownloadState
import com.ozyern.exhale.playback.cancelDownloads
import com.ozyern.exhale.playback.downloadSongs
import com.ozyern.exhale.playback.removeDownloads
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeAlbumMenu(
    albumItem: AlbumItem,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val downloadUtil = LocalDownloadUtil.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val album by database.albumWithSongs(albumItem.id).collectAsState(initial = null)
    val isPinned by database.speedDialDao.isPinned(albumItem.id).collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        database.album(albumItem.id).collect { album ->
            if (album == null) {
                YouTube
                    .album(albumItem.id)
                    .onSuccess { albumPage ->
                        database.transaction {
                            insert(albumPage)
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }

    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val downloadState = remember(album, downloads) {
        combinedDownloadState(album?.songs, downloads) { it.id }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<Song>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { playlistId ->
                    album?.album?.playlistId?.let { addPlaylistId ->
                        YouTube.addPlaylistToPlaylist(playlistId, addPlaylistId)
                    }
                }
            }
            album?.songs?.map { it.id }.orEmpty()
        },
        onDismiss = { showChoosePlaylistDialog = false }
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
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = album?.artists.orEmpty().distinctBy { it.id },
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(ListItemHeight)
                            .clickable {
                                showSelectArtistDialog = false
                                onDismiss()
                                navController.navigate("artist/${artist.id}")
                            }
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = artist.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    YouTubeListItem(
        item = albumItem,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        album?.album?.toggleLike()?.let(::update)
                    }
                },
            ) {
                SymbolIcon(symbol = if (album?.album?.bookmarkedAt != null) sf("heart.fill") else sf("heart"), contentDescription = null, tint = if (album?.album?.bookmarkedAt != null) AppTheme.colorScheme.error else LocalContentColor.current)
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

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
                    if (!isGuest) {
                        NewAction(
                            icon = {
                                SymbolIcon(symbol = sf("play.fill"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                            },
                            text = stringResource(R.string.play),
                            onClick = {
                                onDismiss()
                                album?.songs?.let { songs ->
                                    if (songs.isNotEmpty()) {
                                        playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
                                    }
                                }
                            }
                        )
                        NewAction(
                            icon = {
                                SymbolIcon(symbol = sf("shuffle"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                            },
                            text = stringResource(R.string.shuffle),
                            onClick = {
                                onDismiss()
                                album?.songs?.let { songs ->
                                    if (songs.isNotEmpty()) {
                                        playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
                                    }
                                }
                            }
                        )
                    } else null,
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            onDismiss()
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, albumItem.shareLink)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = if (isGuest) 1 else 3
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
                                album
                                    ?.songs
                                    ?.map { it.toMediaItem() }
                                    ?.let(playerConnection::playNext)
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
                                album
                                    ?.songs
                                    ?.map { it.toMediaItem() }
                                    ?.let(playerConnection::addToQueue)
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
                                    database.speedDialDao.delete(albumItem.id)
                                } else {
                                    database.speedDialDao.insert(SpeedDialItem.fromYTItem(albumItem))
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
                items = listOf(
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
                                    removeDownloads(context, album?.songs?.map { it.id }.orEmpty())
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
                                        album?.songs?.map { it.id }.orEmpty(),
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
                                        album?.songs
                                            ?.map { DownloadTarget(it.id, it.song.title, it.artists.firstOrNull()?.name, it.song.duration) }
                                            .orEmpty(),
                                        downloads,
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }

        albumItem.artists?.let { artists ->
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                IosMenuGroup(
                    items = listOf(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.view_artist)) },
                            description = { Text(text = artists.joinToString { it.name }) },
                            icon = {
                                SymbolIcon(symbol = sf("music.mic"), contentDescription = null)
                            },
                            onClick = {
                                if (artists.size == 1) {
                                    navController.navigate("artist/${artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            }
                        )
                    )
                )
            }
        }
    }
}
