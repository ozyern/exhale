package com.ozyern.exhale.catalog.presentation

import com.ozyern.exhale.catalog.CatalogAlbumPage
import com.ozyern.exhale.catalog.CatalogArtistPage
import com.ozyern.exhale.catalog.CatalogEntry
import com.ozyern.exhale.catalog.CatalogError
import com.ozyern.exhale.catalog.CatalogHomePage
import com.ozyern.exhale.catalog.CatalogId
import com.ozyern.exhale.catalog.CatalogLibraryCollection
import com.ozyern.exhale.catalog.CatalogLibraryPage
import com.ozyern.exhale.catalog.CatalogPlaylistPage
import com.ozyern.exhale.catalog.CatalogResult
import com.ozyern.exhale.catalog.CatalogSearchFilter
import com.ozyern.exhale.catalog.CatalogSearchPage
import com.ozyern.exhale.catalog.CatalogTrackPage
import com.ozyern.exhale.catalog.MusicCatalogRepository
import com.ozyern.exhale.catalog.YOUTUBE_MUSIC_PROVIDER
import com.ozyern.exhale.library.AddSongToMusicPlaylist
import com.ozyern.exhale.library.CreateMusicPlaylist
import com.ozyern.exhale.library.MusicLibraryMutationError
import com.ozyern.exhale.library.MusicLibraryMutationResult
import com.ozyern.exhale.library.MusicPlaylistCreationResult
import com.ozyern.exhale.library.MusicPlaylistRemoteDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogPlaylistActionStateHolderTest {
    @Test
    fun pickerFiltersNonEditableAndForeignPlaylistsThenAddsTrack() = runTest {
        val repository = FakeMusicCatalogRepository().apply {
            playlistPages[null] = CatalogResult.Success(
                CatalogLibraryPage(
                    collection = CatalogLibraryCollection.PLAYLISTS,
                    entries = listOf(
                        playlist("editable"),
                        playlist("read-only", editable = false),
                        playlist("other-provider", provider = "another-provider"),
                    ),
                ),
            )
        }
        val remote = FakeMusicPlaylistRemoteDataSource()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogPlaylistActionStateHolder(
            repository,
            AddSongToMusicPlaylist(remote),
            CreateMusicPlaylist(remote),
            dispatcher,
            dispatcher,
        )

        holder.openPicker(track("song-1"))
        advanceUntilIdle()
        assertEquals(listOf("editable"), holder.state.value.playlists.map { it.id.value })
        assertFalse(holder.state.value.isLoadingPlaylists)

        holder.addToPlaylist(holder.state.value.playlists.single())
        advanceUntilIdle()

        assertEquals(listOf("editable" to "song-1"), remote.requests)
        assertFalse(holder.state.value.isPickerVisible)
        assertEquals("Added to editable.", holder.state.value.message)
        holder.close()
    }

    @Test
    fun failedAddLeavesPickerOpenAndShowsRecoverableMessage() = runTest {
        val repository = FakeMusicCatalogRepository().apply {
            playlistPages[null] = CatalogResult.Success(
                CatalogLibraryPage(CatalogLibraryCollection.PLAYLISTS, listOf(playlist("editable"))),
            )
        }
        val remote = FakeMusicPlaylistRemoteDataSource(
            result = MusicLibraryMutationResult.Failure(MusicLibraryMutationError.OFFLINE),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogPlaylistActionStateHolder(
            repository,
            AddSongToMusicPlaylist(remote),
            CreateMusicPlaylist(remote),
            dispatcher,
            dispatcher,
        )

        holder.openPicker(track("song-2"))
        advanceUntilIdle()
        holder.addToPlaylist(holder.state.value.playlists.single())
        advanceUntilIdle()

        assertTrue(holder.state.value.isPickerVisible)
        assertTrue(holder.state.value.pendingPlaylistIds.isEmpty())
        assertEquals("You're offline. Playlist changes need a connection.", holder.state.value.message)
        holder.close()
    }

    @Test
    fun loadingMoreAppendsUniqueEditablePlaylistsAndStopsRepeatedCursor() = runTest {
        val repository = FakeMusicCatalogRepository().apply {
            playlistPages[null] = CatalogResult.Success(
                CatalogLibraryPage(CatalogLibraryCollection.PLAYLISTS, listOf(playlist("first")), "page-2"),
            )
            playlistPages["page-2"] = CatalogResult.Success(
                CatalogLibraryPage(CatalogLibraryCollection.PLAYLISTS, listOf(playlist("second")), "page-2"),
            )
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogPlaylistActionStateHolder(
            repository,
            AddSongToMusicPlaylist(FakeMusicPlaylistRemoteDataSource()),
            CreateMusicPlaylist(FakeMusicPlaylistRemoteDataSource()),
            dispatcher,
            dispatcher,
        )

        holder.openPicker(track("song-3"))
        advanceUntilIdle()
        holder.loadMorePlaylists()
        advanceUntilIdle()

        assertEquals(listOf("first"), holder.state.value.playlists.map { it.id.value })
        assertEquals("page-2", holder.state.value.continuationToken)
        assertEquals("Couldn't load more playlists. Try again.", holder.state.value.playlistError)
        assertFalse(holder.state.value.isLoadingMorePlaylists)
        holder.close()
    }

    @Test
    fun rejectsTrackFromAnotherProviderWithoutCallingRemote() = runTest {
        val remote = FakeMusicPlaylistRemoteDataSource()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogPlaylistActionStateHolder(
            FakeMusicCatalogRepository(),
            AddSongToMusicPlaylist(remote),
            CreateMusicPlaylist(remote),
            dispatcher,
            dispatcher,
        )

        holder.openPicker(track("song-4", provider = "another-provider"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isPickerVisible)
        assertTrue(remote.requests.isEmpty())
        assertEquals("This track can't be added to a playlist.", holder.state.value.message)
        assertNull(holder.state.value.selectedTrack)
        holder.close()
    }

    @Test
    fun createAndAddCreatesRemotePlaylistBeforeAddingTheTrack() = runTest {
        val remote = FakeMusicPlaylistRemoteDataSource(
            createdPlaylistId = "new-playlist",
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogPlaylistActionStateHolder(
            FakeMusicCatalogRepository(),
            AddSongToMusicPlaylist(remote),
            CreateMusicPlaylist(remote),
            dispatcher,
            dispatcher,
        )

        holder.openPicker(track("song-5"))
        advanceUntilIdle()
        holder.createPlaylistAndAdd("  New Mix  ")
        advanceUntilIdle()

        assertEquals(listOf("New Mix"), remote.createRequests)
        assertEquals(listOf("new-playlist" to "song-5"), remote.requests)
        assertFalse(holder.state.value.isPickerVisible)
        assertEquals("Created and added to New Mix.", holder.state.value.message)
        holder.close()
    }

    @Test
    fun createdPlaylistRemainsAvailableWhenAddingTheTrackFails() = runTest {
        val remote = FakeMusicPlaylistRemoteDataSource(
            result = MusicLibraryMutationResult.Failure(MusicLibraryMutationError.OFFLINE),
            createdPlaylistId = "created-before-failure",
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holder = CatalogPlaylistActionStateHolder(
            FakeMusicCatalogRepository(),
            AddSongToMusicPlaylist(remote),
            CreateMusicPlaylist(remote),
            dispatcher,
            dispatcher,
        )

        holder.openPicker(track("song-6"))
        advanceUntilIdle()
        holder.createPlaylistAndAdd("Saved for later")
        advanceUntilIdle()

        assertTrue(holder.state.value.isPickerVisible)
        assertEquals(listOf("created-before-failure"), holder.state.value.playlists.map { it.id.value })
        assertEquals(
            "Playlist created, but couldn't add the track. You're offline. Playlist changes need a connection.",
            holder.state.value.message,
        )
        holder.close()
    }

    private class FakeMusicPlaylistRemoteDataSource(
        private val result: MusicLibraryMutationResult = MusicLibraryMutationResult.Success,
        private val createdPlaylistId: String = "created-playlist",
    ) : MusicPlaylistRemoteDataSource {
        val requests = mutableListOf<Pair<String, String>>()
        val createRequests = mutableListOf<String>()

        override suspend fun addSongToPlaylist(playlistId: String, songId: String): MusicLibraryMutationResult {
            requests += playlistId to songId
            return result
        }

        override suspend fun createPlaylist(title: String): MusicPlaylistCreationResult {
            createRequests += title
            return MusicPlaylistCreationResult.Success(createdPlaylistId)
        }
    }

    private class FakeMusicCatalogRepository : MusicCatalogRepository {
        val playlistPages = mutableMapOf<String?, CatalogResult<CatalogLibraryPage>>()

        override suspend fun home(continuationToken: String?) = failure<CatalogHomePage>()
        override suspend fun explore() = failure<CatalogHomePage>()
        override suspend fun charts(continuationToken: String?) = failure<CatalogHomePage>()
        override suspend fun browse(id: CatalogId, params: String?) = failure<CatalogHomePage>()
        override suspend fun search(query: String, filter: CatalogSearchFilter, continuationToken: String?) =
            failure<CatalogSearchPage>()
        override suspend fun playlist(id: CatalogId) = failure<CatalogPlaylistPage>()
        override suspend fun playlistContinuation(id: CatalogId, continuationToken: String) = failure<CatalogTrackPage>()
        override suspend fun album(id: CatalogId) = failure<CatalogAlbumPage>()
        override suspend fun artist(id: CatalogId) = failure<CatalogArtistPage>()

        override suspend fun library(
            collection: CatalogLibraryCollection,
            continuationToken: String?,
        ): CatalogResult<CatalogLibraryPage> = playlistPages[continuationToken] ?: failure()

        private fun <T> failure(): CatalogResult<T> = CatalogResult.Failure(CatalogError.Unknown)
    }

    private companion object {
        fun track(id: String, provider: String = YOUTUBE_MUSIC_PROVIDER) = CatalogEntry.Track(
            id = CatalogId(provider, id),
            title = id,
            artworkUrls = emptyList(),
            artists = emptyList(),
            album = null,
            durationMillis = null,
            isExplicit = false,
            isVideo = false,
            isPlayable = true,
        )

        fun playlist(id: String, editable: Boolean = true, provider: String = YOUTUBE_MUSIC_PROVIDER) =
            CatalogEntry.Playlist(
                id = CatalogId(provider, id),
                title = id,
                artworkUrls = emptyList(),
                description = null,
                trackCount = null,
                isEditable = editable,
            )
    }
}
