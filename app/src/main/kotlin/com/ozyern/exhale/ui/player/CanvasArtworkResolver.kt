/*
 * Exhale Project (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Picks between the animated artwork (canvas) providers.
 * Replaces the earlier version, which went through a single proxy server.
 *
 * Orden AUTO: the iOS music app → Exhale API → Tidal
 */

package com.ozyern.exhale.ui.player

import com.ozyern.exhale.canvas.CanvasArtwork
import com.ozyern.exhale.canvas.providers.AppleMusicCanvasProvider
import com.ozyern.exhale.canvas.providers.TidalCanvasProvider
import com.ozyern.exhale.constants.CanvasSource
import java.util.Locale

/**
 * Tries to find the animated artwork for the song that is playing.
 *
 * @param songTitleRaw  The title as MediaMetadata has it (may include feat., etc.)
 * @param artistNameRaw The artist as MediaMetadata has it
 * @param albumName     The album (optional; makes the Apple Music match more precise)
 * @param storefront    The Apple Music storefront (e.g. "us", "mx")
 * @param source        Which source; [CanvasSource.AUTO] tries each in turn
 */
internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    albumName: String? = null,
    storefront: String = "us",
    source: CanvasSource = CanvasSource.AUTO,
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)

    // Both the cleaned and the raw names, so more searches find a match
    val candidates = linkedSetOf(
        Triple(songTitle, artistName, albumName),
        Triple(songTitleRaw, artistName, albumName),
        Triple(songTitle, artistNameRaw, albumName),
        Triple(songTitleRaw, artistNameRaw, albumName),
    ).filter { (song, artist, _) -> song.isNotBlank() && artist.isNotBlank() }

    return candidates.firstNotNullOfOrNull { (song, artist, album) ->
        fetchFromSource(song, artist, album, storefront, source)
            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
    }
}

private suspend fun fetchFromSource(
    song: String,
    artist: String,
    album: String?,
    storefront: String,
    source: CanvasSource,
): CanvasArtwork? {

    val result: CanvasArtwork? = when (source) {
        // Apple first, where most moving covers are; then Spotify's Canvas; Tidal has some neither does.
        CanvasSource.AUTO ->
            AppleMusicCanvasProvider.getBySongArtist(song, artist, album, storefront)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                // Spotify's own Canvas, for a listener who has connected Spotify.
                ?: runCatching { com.ozyern.exhale.spotify.SpotifyCanvasSource.canvas(song, artist) }.getOrNull()
                ?: TidalCanvasProvider.getBySongArtist(song, artist, album)

        CanvasSource.APPLE_MUSIC ->
            AppleMusicCanvasProvider.getBySongArtist(song, artist, album, storefront)

        CanvasSource.TIDAL ->
            TidalCanvasProvider.getBySongArtist(song, artist, album)
    }

    return result?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
}

// ---------------------------------------------------------------------------
// Cleaning up names
// ---------------------------------------------------------------------------

internal fun normalizeCanvasSongTitle(raw: String): String =
    raw
        // Drop brackets: [Official Video], [Remastered], etc.
        .replace(Regex("\\s*\\[[^]]*]"), "")
        // Drop feat. / ft. / featuring in parentheses
        .replace(
            Regex(
                "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        // Drop content-type suffixes in parentheses
        .replace(
            Regex(
                "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        // Drop suffixes after a dash
        .replace(
            Regex(
                "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun normalizeCanvasArtistName(raw: String): String =
    raw
        .split(
            Regex(
                "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                RegexOption.IGNORE_CASE,
            ),
            limit = 2,
        )
        .firstOrNull().orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun splitAndNormalizeArtists(raw: String): List<String> =
    raw.split(
        Regex(
            "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
            RegexOption.IGNORE_CASE,
        )
    )
        .map { it.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
