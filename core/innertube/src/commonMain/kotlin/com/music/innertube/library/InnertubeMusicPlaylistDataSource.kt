package com.music.innertube.library

import com.ozyern.exhale.library.MusicLibraryMutationError
import com.ozyern.exhale.library.MusicLibraryMutationResult
import com.ozyern.exhale.library.MusicPlaylistCreationResult
import com.ozyern.exhale.library.MusicPlaylistRemoteDataSource
import com.music.innertube.InnertubeRepository
import kotlinx.coroutines.CancellationException

class InnertubeMusicPlaylistDataSource(
    private val innertube: InnertubeRepository,
) : MusicPlaylistRemoteDataSource {
    override suspend fun addSongToPlaylist(
        playlistId: String,
        songId: String,
    ): MusicLibraryMutationResult {
        if (playlistId.isBlank() || songId.isBlank()) {
            return MusicLibraryMutationResult.Failure(MusicLibraryMutationError.REJECTED)
        }

        return try {
            innertube.addToPlaylist(playlistId, songId).fold(
                onSuccess = { MusicLibraryMutationResult.Success },
                onFailure = { error -> error.toMusicLibraryMutationFailure() },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.toMusicLibraryMutationFailure()
        }
    }

    override suspend fun createPlaylist(title: String): MusicPlaylistCreationResult {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            return MusicPlaylistCreationResult.Failure(MusicLibraryMutationError.REJECTED)
        }

        return try {
            val playlistId = innertube.createPlaylist(normalizedTitle).trim()
            if (playlistId.isBlank()) {
                MusicPlaylistCreationResult.Failure(MusicLibraryMutationError.REJECTED)
            } else {
                MusicPlaylistCreationResult.Success(playlistId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.toMusicPlaylistCreationFailure()
        }
    }
}

private fun Throwable.toMusicPlaylistCreationFailure(): MusicPlaylistCreationResult.Failure =
    MusicPlaylistCreationResult.Failure(toMusicLibraryMutationFailure().error)
