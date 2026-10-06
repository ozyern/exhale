/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.app.SearchManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import kotlinx.coroutines.flow.first

/**
 * "Hey Google, play Espresso on Exhale" — and every other way an assistant asks.
 *
 * Gemini and Assistant hand a music app more than the words: a *focus* saying what kind of thing
 * was asked for (a song, an artist, an album, a playlist, a genre, or nothing in particular) and,
 * where they could tell, the title, artist and album separately. This reads all of that and turns
 * it into something to play, the way a person would:
 *
 *  - a song plays that song, then carries on into its radio, so the music doesn't stop after one;
 *  - an artist plays the artist's own mix;
 *  - an album plays the album, in order;
 *  - a playlist plays a playlist of that name from the library, or else the best one online;
 *  - a genre or mood plays a radio seeded from the best match;
 *  - nothing at all ("play some music on Exhale") is answered by the caller — usually by resuming.
 *
 * Online first, because that is where nearly every song is; the library answers when the phone is
 * offline or the catalogue has nothing, so a request never falls flat when it could play.
 */
object VoiceSearch {

    data class Request(
        val query: String,
        val focus: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val genre: String? = null,
        val playlist: String? = null,
    ) {
        /** True for "play something" with nothing said about what. */
        val isOpenEnded: Boolean
            get() = query.isBlank() && title.isNullOrBlank() && artist.isNullOrBlank() &&
                album.isNullOrBlank() && genre.isNullOrBlank() && playlist.isNullOrBlank()
    }

    fun request(query: String?, extras: Bundle?): Request = Request(
        query = (query ?: extras?.getString(SearchManager.QUERY)).orEmpty().trim(),
        focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS),
        title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim(),
        artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim(),
        album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.trim(),
        genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)?.trim(),
        playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)?.trim(),
    )

    /** What to play for [request], first item first; empty when nothing fits. */
    suspend fun resolve(request: Request, database: MusicDatabase): List<MediaItem> {
        if (request.isOpenEnded) return emptyList()
        val online = runCatching { resolveOnline(request) }.getOrNull().orEmpty()
        if (online.isNotEmpty()) return online
        return resolveLocal(request, database)
    }

    private suspend fun resolveOnline(r: Request): List<MediaItem> = when (r.focus) {
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> artist(r.artist.orIfBlank(r.query)) ?: song(r)
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> album(listOfNotNull(r.album.orIfBlank(r.query), r.artist).joinToString(" ")) ?: song(r)
        MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> playlist(r.playlist.orIfBlank(r.query)) ?: song(r)
        MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> song(r.copy(query = "${r.genre.orIfBlank(r.query)} music"))
        else -> song(r)
    } ?: emptyList()

    /** The best-matching song, then its radio. */
    private suspend fun song(r: Request): List<MediaItem>? {
        val spoken = listOfNotNull(r.title, r.artist).filter { it.isNotBlank() }.joinToString(" ")
        val query = spoken.ifBlank { r.query }
        if (query.isBlank()) return null
        val songs = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>().orEmpty()
        if (songs.isEmpty()) return null
        val pick = songs.maxByOrNull { score(it, r) } ?: return null
        return withRadio(pick)
    }

    /** Ranks a result against what was said: the title closest to it, by the artist named. */
    private fun score(song: SongItem, r: Request): Int {
        val title = norm(song.title)
        val artists = norm(song.artists.joinToString(" ") { it.name })
        val wantTitle = norm(r.title.orIfBlank(r.query))
        val wantArtist = norm(r.artist.orEmpty())
        var s = 0
        if (title == wantTitle) s += 40 else if (wantTitle.isNotEmpty() && (title.contains(wantTitle) || wantTitle.contains(title))) s += 20
        if (wantArtist.isNotEmpty() && artists.contains(wantArtist)) s += 30
        // An unstructured request often says both at once ("espresso sabrina carpenter").
        if (wantArtist.isEmpty() && artists.isNotEmpty() && wantTitle.contains(artists)) s += 15
        // Covers, live takes and remixes only when asked for.
        val flavour = Regex("""\b(live|cover|remix|karaoke|instrumental|sped up|slowed|nightcore)\b""")
        if (flavour.containsMatchIn(title) && !flavour.containsMatchIn(norm(r.query + " " + r.title.orEmpty()))) s -= 25
        return s
    }

    private suspend fun withRadio(song: SongItem): List<MediaItem> {
        val radio = YouTube.next(WatchEndpoint(videoId = song.id, playlistId = "RDAMVM${song.id}"))
            .getOrNull()?.items.orEmpty()
            .filter { it.id != song.id }
            .take(40)
        return listOf(song.toMediaItem()) + radio.map { it.toMediaItem() }
    }

    private suspend fun artist(name: String): List<MediaItem>? {
        if (name.isBlank()) return null
        val artist = YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            ?.items?.filterIsInstance<ArtistItem>()?.firstOrNull() ?: return null
        val endpoint = artist.shuffleEndpoint ?: artist.radioEndpoint ?: artist.playEndpoint ?: return null
        return YouTube.next(endpoint).getOrNull()?.items?.take(50)?.map { it.toMediaItem() }?.takeIf { it.isNotEmpty() }
    }

    private suspend fun album(query: String): List<MediaItem>? {
        if (query.isBlank()) return null
        val album = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
            ?.items?.filterIsInstance<AlbumItem>()?.firstOrNull() ?: return null
        return YouTube.album(album.browseId).getOrNull()?.songs?.map { it.toMediaItem() }?.takeIf { it.isNotEmpty() }
    }

    private suspend fun playlist(name: String): List<MediaItem>? {
        if (name.isBlank()) return null
        val found = YouTube.search(name, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
            ?.items?.filterIsInstance<PlaylistItem>()?.firstOrNull()
            ?: YouTube.search(name, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                ?.items?.filterIsInstance<PlaylistItem>()?.firstOrNull()
            ?: return null
        val endpoint = found.playEndpoint ?: return null
        return YouTube.next(endpoint).getOrNull()?.items?.take(80)?.map { it.toMediaItem() }?.takeIf { it.isNotEmpty() }
    }

    /** Offline, or nothing online: whatever in the library matches. */
    private suspend fun resolveLocal(r: Request, database: MusicDatabase): List<MediaItem> {
        if (r.focus == MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE) {
            val name = r.playlist.orIfBlank(r.query)
            val playlist = database.searchPlaylists(name, previewSize = 1).first().firstOrNull()
            if (playlist != null) {
                val songs = database.playlistSongs(playlist.playlist.id).first().map { it.song }
                if (songs.isNotEmpty()) return songs.map { it.toMediaItem() }
            }
        }
        val query = listOfNotNull(r.title, r.artist).filter { it.isNotBlank() }.joinToString(" ").ifBlank { r.query }
        if (query.isBlank()) return emptyList()
        return database.searchSongs(query, previewSize = 50).first().map { it.toMediaItem() }
    }

    private fun String?.orIfBlank(other: String): String = if (this.isNullOrBlank()) other else this

    private fun norm(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
