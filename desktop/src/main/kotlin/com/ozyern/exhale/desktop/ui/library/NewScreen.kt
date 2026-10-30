/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.data.ContentLanguageFilter
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.FeatureTile
import com.ozyern.exhale.desktop.ui.home.HomeFeed
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.home.MusicShelfTitle
import com.ozyern.exhale.desktop.ui.home.ShelfPlaceholder
import com.ozyern.exhale.desktop.ui.home.SongListGrid
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.AlbumReleaseType
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** What the New page shows, fetched once a session: moving between sidebar rows is instant after. */
private object NewContent {
    class Content(val albums: List<AlbumItem>, val latestSongs: List<SongItem>)

    @Volatile private var cached: Content? = null

    fun peek(): Content? = cached

    suspend fun load(fromHome: List<AlbumItem>): Content {
        cached?.let { return it }
        val content = withContext(Dispatchers.IO) {
            // Home has usually fetched the new releases already, cleaned and filtered to your languages.
            val albums = fromHome.ifEmpty {
                ContentLanguageFilter.filterItems(YouTube.newReleaseAlbums().getOrNull().orEmpty(), DesktopPrefs.languages.toSet())
            }
            // YouTube has no list of new songs, but a new single is one: its lead track, for the newest
            // dozen, asked for all at once.
            val latestSongs = coroutineScope {
                albums.filter { it.releaseType == AlbumReleaseType.SINGLE }.take(LatestSongCount)
                    .map { single -> async { YouTube.album(single.browseId).getOrNull()?.songs?.firstOrNull() } }
                    .awaitAll()
                    .filterNotNull()
                    .distinctBy { it.id }
            }
            Content(albums, latestSongs)
        }
        if (content.albums.isNotEmpty()) cached = content
        return content
    }
}

/** Three columns of four: the Music app's Latest Songs. */
private const val LatestSongCount = 12

/**
 * The Music app's New page: wide cards for the newest releases, the latest songs in columns,
 * then shelves â€” rather than one undifferentiated grid of covers.
 */
@Composable
fun NewScreen(feed: HomeFeed, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets) {
    val content by produceState(NewContent.peek(), feed.releases) {
        value = NewContent.load(feed.releases)
    }
    val open = rememberItemOpener(player)
    val start = insets.start + 20.dp
    val rowPadding = PaddingValues(start = start, end = 40.dp)
    val isActive: (YTItem) -> Boolean = { item -> item.id == state.current?.id || (item is AlbumItem && item.id == state.current?.album?.id) }
    val albums = content?.albums.orEmpty()
    val featured = albums.take(FeaturedCount)

    LazyColumn(
        contentPadding = PaddingValues(top = 30.dp, bottom = insets.bottom + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") { LargeTitle("New", Modifier.padding(start = start, end = 40.dp, bottom = 14.dp)) }

        val loaded = content
        if (loaded == null) {
            items(2) { ShelfPlaceholder(rowPadding, start) }
            return@LazyColumn
        }
        if (albums.isEmpty()) {
            item(key = "empty") {
                Text(
                    "New releases couldn't be loaded. Check your connection.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = start, top = 6.dp),
                )
            }
        }

        if (featured.isNotEmpty()) {
            item(key = "featured") {
                LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    itemsIndexed(featured, key = { _, album -> album.id }) { _, album ->
                        val active = isActive(album)
                        FeatureTile(
                            eyebrow = album.eyebrow(),
                            title = album.title,
                            subtitle = album.artists?.joinToString { it.name },
                            image = album.thumbnail,
                            caption = album.caption(),
                            active = active,
                            playing = active && state.isPlaying,
                            onClick = { open(album) },
                        )
                    }
                }
            }
        }

        if (loaded.latestSongs.isNotEmpty()) {
            item(key = "latest_songs") {
                Column {
                    MusicShelfTitle("Latest Songs", modifier = Modifier.padding(start = start, end = 40.dp, top = 30.dp, bottom = 10.dp))
                    SongListGrid(loaded.latestSongs, state, player, start)
                }
            }
        }

        val rest = albums.drop(FeaturedCount)
        if (rest.isNotEmpty()) shelf("releases", "New Releases", rest, start, rowPadding, isActive, open) { player.playNow(it) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    key: String,
    title: String,
    items: List<YTItem>,
    start: androidx.compose.ui.unit.Dp,
    rowPadding: PaddingValues,
    isActive: (YTItem) -> Boolean,
    open: (YTItem) -> Unit,
    play: (YTItem) -> Unit,
) {
    item(key = key) {
        Column {
            MusicShelfTitle(title, modifier = Modifier.padding(start = start, end = 40.dp, top = 30.dp, bottom = 10.dp))
            LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { _, item ->
                    Box { ItemCard(item, isActive(item), onClick = { open(item) }, onPlay = { play(item) }) }
                }
            }
        }
    }
}

/** How many of the newest releases get a wide card before the rest go to the shelf. */
private const val FeaturedCount = 6

private fun AlbumItem.eyebrow(): String = when (releaseType) {
    AlbumReleaseType.SINGLE -> "New Single"
    AlbumReleaseType.EP -> "New EP"
    AlbumReleaseType.ALBUM -> "New Album"
}

private fun AlbumItem.caption(): String = listOfNotNull(
    when (releaseType) {
        AlbumReleaseType.SINGLE -> "Single"
        AlbumReleaseType.EP -> "EP"
        AlbumReleaseType.ALBUM -> "Album"
    },
    year?.toString(),
).joinToString(" Â· ")
