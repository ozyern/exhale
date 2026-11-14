/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozyern.exhale.R
import com.ozyern.exhale.library.ObservePlaylists
import com.ozyern.exhale.library.PlaylistOrder
import com.ozyern.exhale.ui.component.SideBarLink
import com.ozyern.exhale.ui.component.SideBarSection
import com.ozyern.exhale.ui.screens.Screens
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The tablet side bar's link sections: Library shortcuts, pinned playlists and quick links. */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun rememberSideBarSections(
    observePlaylists: ObservePlaylists,
    showHistoryButton: Boolean,
    showStatsButton: Boolean,
    listenTogetherInTopBar: Boolean,
    navController: androidx.navigation.NavHostController,
    onLibraryFilterChange: (com.ozyern.exhale.constants.LibraryFilter) -> Unit,
): List<SideBarSection> {
    val context = androidx.compose.ui.platform.LocalContext.current
        val sidebarPlaylists by remember(observePlaylists) {
            flow {
                withFrameNanos { }
                emitAll(observePlaylists(PlaylistOrder.NAME, descending = false))
            }
        }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val sidebarSections = remember(
            showHistoryButton,
            showStatsButton,
            listenTogetherInTopBar,
            sidebarPlaylists,
            onLibraryFilterChange,
        ) {
            buildList {
                fun openLibrary(filter: com.ozyern.exhale.constants.LibraryFilter) {
                    onLibraryFilterChange(filter)
                    navController.navigate(Screens.Library.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                add(SideBarSection(
                    title = context.getString(R.string.filter_library),
                    links = listOf(
                        SideBarLink(context.getString(R.string.recently_added), R.drawable.music_history,
                            onClick = { openLibrary(com.ozyern.exhale.constants.LibraryFilter.LIBRARY) }),
                        SideBarLink(context.getString(R.string.filter_artists), R.drawable.artist,
                            onClick = { openLibrary(com.ozyern.exhale.constants.LibraryFilter.ARTISTS) }),
                        SideBarLink(context.getString(R.string.filter_albums), R.drawable.album,
                            onClick = { openLibrary(com.ozyern.exhale.constants.LibraryFilter.ALBUMS) }),
                        SideBarLink(context.getString(R.string.filter_songs), R.drawable.music_note,
                            onClick = { openLibrary(com.ozyern.exhale.constants.LibraryFilter.SONGS) }),
                        SideBarLink(context.getString(R.string.filter_downloaded), R.drawable.download,
                            onClick = { navController.navigate("auto_playlist/downloaded") }),
                    ),
                ))
                add(SideBarSection(
                    title = context.getString(R.string.playlists),
                    links = listOf(SideBarLink(context.getString(R.string.filter_playlists), R.drawable.playlist_play,
                        onClick = { openLibrary(com.ozyern.exhale.constants.LibraryFilter.PLAYLISTS) })) +
                        sidebarPlaylists.map { playlist ->
                            SideBarLink(label = playlist.name,
                                thumbnailUrl = playlist.artworkUrls.firstOrNull(),
                                sharedArtworkId = playlist.id,
                                onClick = { navController.navigate("local_playlist/${playlist.id}") })
                        },
                ))
                val quick = buildList {
                    if (showHistoryButton) {
                        add(
                            SideBarLink(
                                label = context.getString(R.string.history),
                                iconRes = R.drawable.music_history,
                                onClick = { navController.navigate("history") },
                            )
                        )
                    }
                    if (showStatsButton) {
                        add(
                            SideBarLink(
                                label = context.getString(R.string.stats),
                                iconRes = R.drawable.stats,
                                onClick = { navController.navigate("stats") },
                            )
                        )
                    }
                    if (listenTogetherInTopBar) {
                        add(
                            SideBarLink(
                                label = context.getString(R.string.together),
                                iconRes = R.drawable.group_outlined,
                                onClick = {
                                    navController.navigate("listen_together_from_topbar")
                                },
                            )
                        )
                    }
                }
                if (quick.isNotEmpty()) add(SideBarSection(links = quick))

            }
        }
    return sidebarSections
}
