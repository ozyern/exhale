/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.library

import com.ozyern.exhale.desktop.ui.cascade
import com.ozyern.exhale.desktop.ui.rememberCascade
import com.ozyern.exhale.innertube.models.isMusicVideo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongRow
import com.ozyern.exhale.desktop.ui.StationCard
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.GlassIconButton
import com.ozyern.exhale.desktop.ui.home.HomeFeed
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.pages.PillButton
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The audio track itself, by YouTube's tag ("ATV") — not a video, an upload or an episode. */
val SongItem.isAudioTrack: Boolean
    get() = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType ==
        com.ozyern.exhale.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV

/** The pages behind the sidebar's rows, named as the Music app names them. */
enum class LibrarySection(val title: String) {
    New("New"),
    Radio("Radio"),
    RecentlyAdded("Recently Added"),
    Artists("Artists"),
    Albums("Albums"),
    Songs("Songs"),
    MusicVideos("Music Videos"),
    AllPlaylists("All Playlists"),
    Favorites("Favorite Songs"),
}

/**
 * Your YouTube Music library, for a signed-in account: liked songs, saved albums, followed artists
 * and playlists. Each is asked for once per account and kept, so moving between the sidebar's rows
 * is instant after the first visit.
 */
object AccountLibrary {
    private val cache = HashMap<String, List<YTItem>>()

    suspend fun load(what: String): List<YTItem> {
        if (!AccountSession.isSignedIn) return emptyList()
        val key = "${AccountSession.account.value?.name}|$what"
        synchronized(cache) { cache[key] }?.let { return it }
        val items = withContext(Dispatchers.IO) {
            when (what) {
                "songs" -> YouTube.playlist("LM").completed().getOrNull()?.songs.orEmpty()
                else -> YouTube.library(what).completed().getOrNull()?.items.orEmpty()
            }
        }
        if (items.isNotEmpty()) synchronized(cache) { cache[key] = items }
        return items
    }
}

/**
 * One of the Music app's library pages: a large title, Play and Shuffle where there are songs, then
 * the songs in columns or the albums, artists and playlists as a grid. Without an account it's made of
 * your favourites (and, for artists, the ones you picked); with one, your YouTube library joins in.
 */
@Composable
fun LibrarySectionScreen(section: LibrarySection, feed: HomeFeed, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets) {
    val favorites by DesktopPrefs.favorites.collectAsState()
    val picked by DesktopPrefs.artists.collectAsState()
    val account by AccountSession.account.collectAsState()
    val open = rememberItemOpener(player)
    val navigator = LocalNavigator.current

    val remote by produceState<List<YTItem>?>(null, section, account) {
        value = when (section) {
            LibrarySection.New -> withContext(Dispatchers.IO) { YouTube.newReleaseAlbums().getOrNull().orEmpty() }
            LibrarySection.Songs, LibrarySection.MusicVideos, LibrarySection.Favorites, LibrarySection.RecentlyAdded -> AccountLibrary.load("songs")
            LibrarySection.Albums -> AccountLibrary.load("FEmusic_liked_albums")
            LibrarySection.Artists -> AccountLibrary.load("FEmusic_library_corpus_artists")
            LibrarySection.AllPlaylists -> AccountLibrary.load("FEmusic_liked_playlists")
            LibrarySection.Radio -> emptyList()
        }
    }

    // Songs first from what's kept here, then what the account adds that isn't already in it.
    val songs: List<SongItem> = remember(section, favorites, remote) {
        val fromAccount = remote.orEmpty().filterIsInstance<SongItem>()
        when (section) {
            LibrarySection.Favorites, LibrarySection.RecentlyAdded -> (favorites + fromAccount).distinctBy { it.id }
            LibrarySection.Songs -> (favorites + fromAccount).distinctBy { it.id }.filterNot { it.isMusicVideo }
            LibrarySection.MusicVideos -> (favorites + fromAccount).distinctBy { it.id }.filter { it.isMusicVideo }
            else -> emptyList()
        }
    }
    val cards: List<YTItem> = remember(section, favorites, picked, remote) {
        when (section) {
            LibrarySection.New -> remote.orEmpty()
            LibrarySection.Albums -> {
                val fromFavorites = favorites.mapNotNull { song ->
                    song.album?.let { AlbumItem(browseId = it.id, playlistId = "", title = it.name, artists = song.artists, year = null, thumbnail = song.thumbnail) }
                }
                (remote.orEmpty().filterIsInstance<AlbumItem>() + fromFavorites).distinctBy { it.browseId }
            }
            LibrarySection.Artists -> {
                val chosen = picked.map { ArtistItem(id = it.id, title = it.name, thumbnail = it.thumbnail, shuffleEndpoint = null, radioEndpoint = null) }
                val fromFavorites = favorites.flatMap { it.artists }.filter { it.id != null }
                    .map { ArtistItem(id = it.id!!, title = it.name, thumbnail = null, shuffleEndpoint = null, radioEndpoint = null) }
                (chosen + remote.orEmpty().filterIsInstance<ArtistItem>() + fromFavorites).distinctBy { it.id }
            }
            LibrarySection.AllPlaylists -> remote.orEmpty().filterIsInstance<PlaylistItem>().filter { it.id.isNotBlank() }
            else -> emptyList()
        }
    }
    val isSongPage = section in setOf(LibrarySection.RecentlyAdded, LibrarySection.Songs, LibrarySection.MusicVideos, LibrarySection.Favorites)
    // ColorOS 17's cascade, once there is something to show — so a page whose items arrive late
    // still lands as a stack rather than popping in.
    val arrived = when {
        section == LibrarySection.Radio -> feed.topPicks.isNotEmpty()
        isSongPage -> songs.isNotEmpty()
        else -> cards.isNotEmpty()
    }
    val cascade = rememberCascade(section, arrived)

    LazyVerticalGrid(
        columns = if (isSongPage) GridCells.Adaptive(420.dp) else GridCells.Adaptive(176.dp),
        contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 30.dp, bottom = insets.bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isSongPage) 24.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (isSongPage) 0.dp else 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LargeTitle(section.title, Modifier.weight(1f))
                GlassIconButton(NavIcons.Stats, "Your Sound Chem", { navigator?.openSoundChem?.invoke() })
            }
        }
        if (isSongPage && songs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Play", TransportIcons.Play, primary = true) { player.play(songs, 0) }
                    Spacer(Modifier.width(10.dp))
                    PillButton("Shuffle", TransportIcons.Shuffle, primary = false) { player.play(songs.shuffled(), 0) }
                    Spacer(Modifier.width(14.dp))
                    Text("${songs.size} ${if (songs.size == 1) "song" else "songs"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val empty = when {
            section == LibrarySection.Radio -> feed.topPicks.isEmpty()
            isSongPage -> songs.isEmpty()
            else -> cards.isEmpty()
        }
        if (empty) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    emptyText(section, loading = remote == null && section != LibrarySection.Radio),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }

        when {
            section == LibrarySection.Radio -> itemsIndexed(feed.topPicks, key = { i, pick -> "radio:$i:${pick.item.id}" }) { i, pick ->
                Box(Modifier.cascade(i, cascade)) {
                    StationCard(pick.title, pick.eyebrow, pick.image, onClick = { player.playItem(pick.item) }, artSize = 176.dp)
                }
            }
            isSongPage -> itemsIndexed(songs, key = { i, song -> "song:$i:${song.id}" }) { i, song ->
                Box(Modifier.cascade(i, cascade)) {
                    SongRow(song, isActive = song.id == state.current?.id, onClick = { player.play(songs, i) }, player = player)
                }
            }
            else -> itemsIndexed(cards, key = { i, item -> "card:$i:${item.id}" }) { i, item ->
                val active = item.id == state.current?.id || (item is AlbumItem && item.id == state.current?.album?.id)
                Box(Modifier.cascade(i, cascade)) { ItemCard(item, active, onClick = { open(item) }, artSize = 176.dp, onPlay = { player.playNow(item) }) }
            }
        }
    }
}

private fun emptyText(section: LibrarySection, loading: Boolean): String = when {
    loading -> "Loading…"
    section == LibrarySection.New -> "New releases couldn't be loaded. Check your connection."
    section == LibrarySection.Radio -> "Stations appear once Home has loaded."
    section == LibrarySection.AllPlaylists ->
        if (AccountSession.isSignedIn) "Your YouTube Music playlists show up here." else "Sign in to YouTube Music in Settings to see your playlists."
    section == LibrarySection.Artists -> "Artists you pick in Your Music Taste, and the artists of your favourite songs, show up here."
    section == LibrarySection.Albums -> "Albums of the songs you favourite show up here."
    section == LibrarySection.MusicVideos -> "Music videos you favourite show up here."
    else -> "Choose Favorite from a song's ⋯ menu and it shows up here."
}
