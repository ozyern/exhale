package com.ozyern.exhale.catalog.presentation

import com.ozyern.exhale.catalog.CatalogEntry
import com.ozyern.exhale.catalog.CatalogId
import com.ozyern.exhale.library.MusicLibraryMutationError
import com.ozyern.exhale.library.MusicLibraryMutationResult
import com.ozyern.exhale.library.MusicLibraryRemoteDataSource
import com.ozyern.exhale.library.SetMusicLibraryTrackSaved
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogLibraryActionStateHolderTest {
    @Test
    fun successfulSaveUpdatesRowAndClearsPendingState() = runTest {
        val remote = FakeMusicLibraryRemoteDataSource()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogLibraryActionStateHolder(SetMusicLibraryTrackSaved(remote), dispatcher, dispatcher)
        val track = track("song-1")

        holder.setSaved(track, isSaved = true)
        assertTrue(holder.isPending(track))
        advanceUntilIdle()

        assertEquals(listOf("song-1" to true), remote.requests)
        assertTrue(holder.isSaved(track))
        assertFalse(holder.isPending(track))
        assertEquals(null, holder.state.value.message)
        holder.close()
    }

    @Test
    fun failedRemovalKeepsSavedStateAndShowsRecoverableMessage() = runTest {
        val remote = FakeMusicLibraryRemoteDataSource(
            result = MusicLibraryMutationResult.Failure(MusicLibraryMutationError.OFFLINE),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogLibraryActionStateHolder(SetMusicLibraryTrackSaved(remote), dispatcher, dispatcher)
        val track = track("song-2", isInLibrary = true)

        holder.setSaved(track, isSaved = false)
        advanceUntilIdle()

        assertFalse(holder.isPending(track))
        assertTrue(holder.isSaved(track))
        assertEquals("You're offline. Library changes need a connection.", holder.state.value.message)
        holder.close()
    }

    private class FakeMusicLibraryRemoteDataSource(
        var result: MusicLibraryMutationResult = MusicLibraryMutationResult.Success,
    ) : MusicLibraryRemoteDataSource {
        val requests = mutableListOf<Pair<String, Boolean>>()

        override suspend fun setSongSaved(songId: String, isSaved: Boolean): MusicLibraryMutationResult {
            requests += songId to isSaved
            return result
        }
    }

    private companion object {
        fun track(id: String, isInLibrary: Boolean? = false) = CatalogEntry.Track(
            id = CatalogId("youtube_music", id),
            title = id,
            artworkUrls = emptyList(),
            artists = emptyList(),
            album = null,
            durationMillis = null,
            isExplicit = false,
            isVideo = false,
            isPlayable = true,
            isInLibrary = isInLibrary,
        )
    }
}
