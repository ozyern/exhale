/*
 * Exhale Project (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Orquestador de proveedores de canvas.
 * Reemplaza la implementación anterior basada en un único proxy server.
 *
 * Orden AUTO: Apple Music → Exhale API → Tidal
 */

package com.ozyern.exhale.ui.player

import com.ozyern.exhale.canvas.CanvasArtwork
import com.ozyern.exhale.canvas.providers.AppleMusicCanvasProvider
import com.ozyern.exhale.canvas.providers.TidalCanvasProvider
import com.ozyern.exhale.constants.CanvasSource
import java.util.Locale

/**
 * Intenta obtener el canvas animado para la canción en reproducción.
 *
 * @param songTitleRaw  Título tal como viene del MediaMetadata (puede tener feat., etc.)
 * @param artistNameRaw Nombre del artista tal como viene del MediaMetadata
 * @param albumName     Nombre del álbum (opcional, mejora la precisión de Apple Music)
 * @param storefront    Región de Apple Music (ej. "us", "mx")
 * @param source        Fuente a usar; [CanvasSource.AUTO] prueba todas en orden
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

    // Genera combinaciones (normalizado + raw) para mayor cobertura de búsqueda
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
        // Apple first, where most moving covers are; Tidal has some Apple doesn't.
        CanvasSource.AUTO ->
            AppleMusicCanvasProvider.getBySongArtist(song, artist, album, storefront)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: TidalCanvasProvider.getBySongArtist(song, artist, album)

        CanvasSource.APPLE_MUSIC ->
            AppleMusicCanvasProvider.getBySongArtist(song, artist, album, storefront)

        CanvasSource.TIDAL ->
            TidalCanvasProvider.getBySongArtist(song, artist, album)
    }

    return result?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
}

// ---------------------------------------------------------------------------
// Funciones de normalización
// ---------------------------------------------------------------------------

internal fun normalizeCanvasSongTitle(raw: String): String =
    raw
        // Eliminar corchetes: [Official Video], [Remastered], etc.
        .replace(Regex("\\s*\\[[^]]*]"), "")
        // Eliminar feat. / ft. / featuring entre paréntesis
        .replace(
            Regex(
                "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        // Eliminar sufijos de tipo de contenido entre paréntesis
        .replace(
            Regex(
                "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        // Eliminar sufijos separados por guion
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
