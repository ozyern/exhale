/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.menu

import com.ozyern.exhale.ui.component.DownloadRingIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.menuBottomInset
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ozyern.exhale.ui.ios.compat.CircularProgressIndicator
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.ListItem
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.LocalListenTogetherManager
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.LocalSyncUtils
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ListItemHeight
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.constants.ThumbnailCornerRadius
import com.ozyern.exhale.constants.ThumbnailRoundedShape
import com.ozyern.exhale.db.entities.SpeedDialItem
import com.ozyern.exhale.db.entities.SongEntity
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.ExoDownloadService
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.ListDialog
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.IosMenuGroup
import com.ozyern.exhale.ui.component.IosMenuItemData
import com.ozyern.exhale.ui.component.NewAction
import com.ozyern.exhale.ui.component.NewActionGrid
import com.ozyern.exhale.ui.utils.ShowMediaInfo
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
    onHistoryRemoved: () -> Unit = {}
) {
    val context = LocalContext.current
    val ringtoneViewModel = com.ozyern.exhale.LocalRingtoneViewModel.current
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.song(song.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.get().getDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current.get()
    val listenTogetherManager = LocalListenTogetherManager.current
    val isPinned by database.speedDialDao.isPinned(song.id).collectAsState(initial = false)
    val artists = remember(song.artists) {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    AddToPlaylistDialog(  
        isVisible = showChoosePlaylistDialog,  
        onGetSong = { playlist ->  
            database.withTransaction {  
                insert(song.toMediaMetadata())  
            }  
            coroutineScope.launch(Dispatchers.IO) {  
                playlist.playlist.browseId?.let { browseId ->  
                    YouTube.addToPlaylist(browseId, song.id)  
                }  
            }  
            listOf(song.id)  
        },  
        onDismiss = { showChoosePlaylistDialog = false }  
    )  

    var showSelectArtistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    if (showSelectArtistDialog) {  
        ListDialog(  
            onDismiss = { showSelectArtistDialog = false },  
        ) {  
            items(artists, key = { it.id!! }) { artist ->
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    modifier =  
                    Modifier  
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
                        modifier =  
                        Modifier  
                            .fillParentMaxWidth()  
                            .height(ListItemHeight)  
                            .clickable {  
                                navController.navigate("artist/${artist.id}")  
                                showSelectArtistDialog = false  
                                onDismiss()  
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

    ListItem(  
        headlineContent = {
            Text(
                text = song.title,
                modifier = Modifier.basicMarquee(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },  
        supportingContent = {  
            Text(  
                text = joinByBullet(
                    song.artists.joinToString { it.name },
                    song.duration?.let { makeTimeString(it * 1000L) },
                )
            )  
        },  
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(ThumbnailRoundedShape)
            ) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ThumbnailRoundedShape)
                )
            }
        },
        trailingContent = {  
            IconButton(  
                onClick = {  
                    database.transaction {  
                        librarySong.let { librarySong ->  
                            val s: SongEntity  
                            if (librarySong == null) {  
                                insert(song.toMediaMetadata(), SongEntity::toggleLike)  
                                s = song.toMediaMetadata().toSongEntity().let(SongEntity::toggleLike)  
                            } else {  
                                s = librarySong.song.toggleLike()  
                                update(s)  
                            }  
                            syncUtils.likeSong(s)  
                        }  
                    }  
                },  
            ) {  
                SymbolIcon(symbol = if (librarySong?.song?.liked == true) sf("heart.fill") else sf("heart"), contentDescription = null, tint = if (librarySong?.song?.liked == true) AppTheme.colorScheme.error else LocalContentColor.current)  
            }  
        },  
    )  

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

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
                                SymbolIcon(symbol = sf("text.line.first.and.arrowtriangle.forward"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                            },
                            text = stringResource(R.string.play_next),
                            onClick = {
                                playerConnection.playNext(song.copy(thumbnail = song.thumbnail.resize(1200, 1200)).toMediaItem())
                                onDismiss()
                            }
                        )
                    } else null,
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.add_to_an_playlist),
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    ),
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, song.shareLink)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                            onDismiss()
                        }
                    )
                ),
                columns = if (isGuest) 2 else 3,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            IosMenuGroup(
                items = listOfNotNull(
                    if (listenTogetherManager != null && listenTogetherManager.isInRoom && !listenTogetherManager.isHost) {
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.suggest_to_host)) },
                            icon = {
                                SymbolIcon(symbol = sf("music.note.list"), contentDescription = null)
                            },
                            onClick = {
                                val durationMs = song.duration?.takeIf { it > 0 }?.times(1000L) ?: 180000L
                                val trackInfo = com.ozyern.exhale.listentogether.TrackInfo(
                                    id = song.id,
                                    title = song.title,
                                    artist = artists.joinToString(", ") { it.name },
                                    album = song.album?.name,
                                    duration = durationMs,
                                    thumbnail = song.thumbnail
                                )
                                listenTogetherManager.suggestTrack(trackInfo)
                                onDismiss()
                            }
                        )
                    } else null,
                    if (!isGuest) {
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.start_radio)) },
                            description = { Text(text = stringResource(R.string.start_radio_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("dot.radiowaves.left.and.right"), contentDescription = null)
                            },
                            onClick = {
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
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
                                playerConnection.addToQueue(song.toMediaItem())
                                onDismiss()
                            }
                        )
                    } else null
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    song.historyRemoveToken?.let { historyRemoveToken ->
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_history)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    coroutineScope.launch {
                                        YouTube.feedback(listOf(historyRemoveToken))
                                        delay(500)
                                        onHistoryRemoved()
                                        onDismiss()
                                    }
                                }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { 
                                Text(
                                    text = if (isPinned) stringResource(R.string.unpin_from_speed_dial) else stringResource(R.string.pin_to_speed_dial)
                                ) 
                            },
                            icon = {
                                SymbolIcon(symbol = if (isPinned) sf("minus") else sf("plus"), contentDescription = null)
                            },
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(song.id)
                                    } else {
                                        database.speedDialDao.insert(SpeedDialItem.fromYTItem(song))
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = {
                                Text(text = if (librarySong?.song?.inLibrary != null) stringResource(R.string.remove_from_library) else stringResource(R.string.add_to_library))
                            },
                            description = { Text(text = stringResource(R.string.add_to_library_desc)) },
                            icon = {
                                SymbolIcon(symbol = if (librarySong?.song?.inLibrary != null) sf("text.badge.checkmark") else sf("text.badge.plus"), contentDescription = null)
                            },
                            onClick = {
                                val isInLibrary = librarySong?.song?.inLibrary != null

                                coroutineScope.launch {
                                    YouTube.toggleSongLibrary(song.id, !isInLibrary)
                                }

                                if (isInLibrary) {
                                    database.query {
                                        inLibrary(song.id, null)
                                    }
                                } else {
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                        inLibrary(song.id, LocalDateTime.now())
                                        addLibraryTokens(
                                            song.id,
                                            song.libraryAddToken,
                                            song.libraryRemoveToken
                                        )
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
                items = listOf(
                    when (download?.state) {
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
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        song.id,
                                        false,
                                    )
                                }
                            )
                        }
                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.downloading)) },
                                icon = {
                                    DownloadRingIcon(download)
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        song.id,
                                        false,
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
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                    }
                                    val downloadRequest = DownloadRequest
                                        .Builder(song.id, song.id.toUri())
                                        .setCustomCacheKey(song.id)
                                        .setData(song.title.toByteArray())
                                        .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    if (artists.isNotEmpty()) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.view_artist)) },
                                description = { Text(text = song.artists.joinToString { it.name }) },
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
                    }
                    val albumId = song.album?.id?.takeIf { it.isNotBlank() }
                        ?: librarySong?.song?.albumId?.takeIf { it.isNotBlank() }
                        ?: librarySong?.album?.id?.takeIf { it.isNotBlank() }
                    val albumName = song.album?.name
                        ?: librarySong?.song?.albumName
                        ?: librarySong?.album?.title
                    if (!albumId.isNullOrBlank()) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.view_album)) },
                                description = albumName?.let { name ->
                                    { Text(text = name) }
                                },
                                icon = {
                                    SymbolIcon(symbol = sf("square.stack"), contentDescription = null)
                                },
                                onClick = {
                                    navController.navigate("album/$albumId")
                                    onDismiss()
                                }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("info.circle"), contentDescription = null)
                            },
                            onClick = {
                                onDismiss()
                                bottomSheetPageState.showFloating {
                                    ShowMediaInfo(song.id)
                                }
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.set_as_ringtone)) },
                            icon = {
                                SymbolIcon(symbol = sf("bell"), contentDescription = null)
                            },
                            onClick = {
                                ringtoneViewModel.showTrimmer(
                                    song.id,
                                    song.title,
                                    song.artists.joinToString { it.name },
                                    song.duration ?: 0
                                )
                                onDismiss()
                            }
                        )
                    )
                }
            )
        }
    }
}
