/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.spotify

import com.ozyern.exhale.App
import com.ozyern.exhale.canvas.CanvasArtwork
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.Normalizer
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpotifyLibraryEntryPoint {
    fun spotifyLibraryRepository(): SpotifyLibraryRepository
}

/**
 * Spotify's own Canvas — the looping video behind a track — as a moving-cover source.
 *
 * Only for a listener who has connected Spotify: Canvas has no public API, and the web player's
 * query needs the session that connection already keeps. Without one this returns null before any
 * request is made. The match is strict (same title, a shared artist) because a confident wrong
 * video is worse than the still cover.
 */
object SpotifyCanvasSource {

    suspend fun canvas(title: String, artist: String): CanvasArtwork? {
        if (!signedIn()) return null
        val hits = Spotify.search("$title $artist", types = listOf("track"), limit = 8)
            .getOrNull()?.tracks?.items.orEmpty()
        val wantTitle = norm(title)
        val wantArtists = artist.split(',', '&').map(::norm).filter { it.isNotEmpty() }
        val hit = hits.firstOrNull { track ->
            norm(track.name) == wantTitle &&
                (wantArtists.isEmpty() || track.artists.any { a -> norm(a.name) in wantArtists })
        } ?: return null
        val uri = hit.uri ?: "spotify:track:${hit.id}"
        val url = Spotify.canvasUrl(uri) ?: return null
        return CanvasArtwork(
            name = hit.name,
            artist = hit.artists.joinToString(", ") { it.name },
            albumName = hit.album?.name,
            animated = url,
        )
    }

    private suspend fun signedIn(): Boolean {
        if (Spotify.accessToken != null) return true
        val repository = runCatching {
            EntryPointAccessors.fromApplication(App.instance, SpotifyLibraryEntryPoint::class.java)
                .spotifyLibraryRepository()
        }.getOrNull() ?: return false
        return runCatching { repository.restoreSession().isAuthenticated }.getOrDefault(false)
    }

    private fun norm(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
            .substringBefore(" - ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
