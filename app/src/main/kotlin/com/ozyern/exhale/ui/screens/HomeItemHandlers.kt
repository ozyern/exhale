/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.navigation.NavController
import com.ozyern.exhale.constants.HideHomeFavoriteIconKey
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.AlbumGridItem
import com.ozyern.exhale.ui.component.ArtistGridItem
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.SongGridItem
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.ArtistMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.utils.albumRoute
import com.ozyern.exhale.ui.utils.artistRoute
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope

internal class HomeItemHandlers(
    val onLocalItemClick: (LocalItem) -> Unit,
    val localGridItem: @Composable (LocalItem) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberHomeItemHandlers(
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    mediaMetadataState: State<MediaMetadata?>,
    isPlayingState: State<Boolean>,
): HomeItemHandlers {
    val (hideHomeFavoriteIcon) = rememberPreference(HideHomeFavoriteIconKey, false)

    // The tap behaviour of localGridItem below, reachable without rendering a tile —
    // the spotlight row draws its own card but wants the same navigation.
    val onLocalItemClick: (LocalItem) -> Unit = { localItem ->
        when (localItem) {
            is Song -> if (localItem.id == mediaMetadataState.value?.id) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(YouTubeQueue.radio(localItem.toMediaMetadata()))
            }

            is Album -> navController.navigate(albumRoute(localItem.id, localItem.thumbnailUrl))
            is Artist -> navController.navigate(artistRoute(localItem.id, localItem.thumbnailUrl))
            // Matches localGridItem, which also renders no tap target for a playlist
            // here: this shelf is built from songs and albums only.
            is Playlist -> Unit
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedBounceClick(
                        onClick = {
                            if (it.id == mediaMetadataState.value?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata()),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadataState.value?.id,
                isPlaying = isPlayingState.value && it.id == mediaMetadataState.value?.id,
                showLikedIcon = !hideHomeFavoriteIcon,
                showDuration = false,
                // GridItem applies its own width after the outer modifier.
                fillMaxWidth = true,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadataState.value?.album?.id,
                isPlaying = isPlayingState.value && it.id == mediaMetadataState.value?.album?.id,
                coroutineScope = scope,
                showLikedIcon = !hideHomeFavoriteIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedBounceClick(
                        onClick = {
                            navController.navigate(albumRoute(it.id, it.thumbnailUrl))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    ),
                fillMaxWidth = true,
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedBounceClick(
                        onClick = {
                            navController.navigate(artistRoute(it.id, it.thumbnailUrl))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                fillMaxWidth = true,
            )

            is Playlist -> {}
        }
    }
    return remember(navController, playerConnection, menuState, haptic, scope, hideHomeFavoriteIcon) {
        HomeItemHandlers(onLocalItemClick, localGridItem)
    }
}
