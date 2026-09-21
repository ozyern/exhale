/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.innertube.pages

import com.ozyern.exhale.innertube.models.Album
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.MusicResponsiveListItemRenderer
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.Run
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.clean
import com.ozyern.exhale.innertube.models.oddElements
import com.ozyern.exhale.innertube.models.splitBySeparator
import com.ozyern.exhale.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        val title = renderer.titleText ?: return null
        val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
        val metadata = renderer.metadataGroups()
        return when {
            renderer.isSong -> {
                val endpoint = renderer.watchEndpoint()
                SongItem(
                    id = renderer.playlistItemData?.videoId ?: endpoint?.videoId ?: return null,
                    title = title,
                    artists = metadata.artistGroup().toArtists(),
                    album =
                        metadata.getOrNull(1)?.firstOrNull()
                            ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                            )
                        },
                    duration = metadata.duration(),
                    thumbnail = thumbnail ?: return null,
                    explicit = renderer.isExplicit,
                    endpoint = endpoint,
                )
            }
            renderer.isArtist -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title = title,
                    thumbnail = thumbnail,
                    playEndpoint = renderer.watchEndpoint(),
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    radioEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }
            renderer.isAlbum -> {
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId =
                        renderer.watchEndpoint()
                            ?.playlistId
                            ?: return null,
                    title = title,
                    artists = metadata.artistGroup().toArtists().takeIf { it.isNotEmpty() },
                    year = metadata.year(),
                    thumbnail = thumbnail ?: return null,
                    explicit = renderer.isExplicit,
                )
            }
            renderer.isPlaylist -> {
                val playlistMetadata = renderer.metadataGroups(clean = false)
                PlaylistItem(
                    id =
                        renderer.navigationEndpoint?.browseEndpoint?.browseId?.removePrefix("VL")
                            ?: renderer.watchEndpoint()?.playlistId?.removePrefix("VL")
                            ?: return null,
                    title = title,
                    author = playlistMetadata.playlistAuthor(),
                    songCountText = playlistMetadata.lastText(),
                    thumbnail = thumbnail,
                    playEndpoint =
                        renderer.watchEndpoint(),
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    radioEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }
            else -> null
        }
    }
}

private val MusicResponsiveListItemRenderer.titleText: String?
    get() =
        flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.joinToString(separator = "") { it.text }
            ?.takeIf { it.isNotBlank() }

private val MusicResponsiveListItemRenderer.isExplicit: Boolean
    get() =
        badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true

private fun MusicResponsiveListItemRenderer.metadataGroups(clean: Boolean = true): List<List<Run>> {
    val groups =
        flexColumns
            .drop(1)
            .flatMap {
                it.musicResponsiveListItemFlexColumnRenderer.text?.runs?.splitBySeparator()
                    .orEmpty()
            }
    return if (clean) groups.clean() else groups
}

private fun MusicResponsiveListItemRenderer.watchEndpoint(): WatchEndpoint? =
    navigationEndpoint?.anyWatchEndpoint
        ?: overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.anyWatchEndpoint

private fun List<Run>?.toArtists(): List<Artist> =
    this
        ?.oddElements()
        // A runtime is not an artist. Some rows lead with one, and "2:59" as the artist name quietly
        // poisons everything downstream: the lyrics search, radio, and going to the artist.
        ?.filterNot { it.text.parseTime() != null }
        ?.map {
            Artist(
                name = it.text,
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
            )
        }
        .orEmpty()

/**
 * Which metadata group holds the artists. Usually the first, but not on every layout, so a group
 * carrying a real artist link is taken first — an artist's id is a channel (`UC…`), where an album's
 * is `MPRE…` — and only failing that the first group that is neither a runtime nor a bare number.
 */
private fun List<List<Run>>.artistGroup(): List<Run>? =
    firstOrNull { group ->
        group.any { it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true }
    } ?: firstOrNull { group ->
        group.none { run -> run.text.parseTime() != null || run.text.trim().toIntOrNull() != null }
    }

private fun List<List<Run>>.duration(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.parseTime()?.let { return it }
        }
    }
    return null
}

private fun List<List<Run>>.year(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun List<List<Run>>.playlistAuthor(): Artist? {
    val authorIndex = if (size >= 3) 1 else 0
    return getOrNull(authorIndex)
        ?.firstOrNull()
        ?.let {
            Artist(
                name = it.text,
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
            )
        }
}

private fun List<List<Run>>.lastText(): String? =
    lastOrNull()
        ?.joinToString(separator = "") { it.text }
        ?.takeIf { it.isNotBlank() }
