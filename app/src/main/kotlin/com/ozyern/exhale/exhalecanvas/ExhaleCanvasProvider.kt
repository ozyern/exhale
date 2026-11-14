package com.ozyern.exhale.exhalecanvas

import com.ozyern.exhale.artistvideo.ArtistVideoCanvasProvider
import com.ozyern.exhale.artistvideo.ArtistVideoResponse
import com.ozyern.exhale.canvas.CanvasArtwork

object ExhaleCanvasProvider {
    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
    ): CanvasArtwork? = ArtistVideoCanvasProvider.getBySongArtist(song, artist, album)
        ?.toCanvasArtwork(album)

    suspend fun getByAlbumArtist(album: String, artist: String): CanvasArtwork? =
        ArtistVideoCanvasProvider.getBySongArtist(song = album, artist = artist, album = album)
            ?.toCanvasArtwork(album)
}

private fun ArtistVideoResponse.toCanvasArtwork(albumName: String?): CanvasArtwork = CanvasArtwork(
    name = name,
    artist = artist,
    albumId = albumId,
    albumName = albumName,
    static = static,
    animated = animated,
    videoUrl = videoUrl,
)
