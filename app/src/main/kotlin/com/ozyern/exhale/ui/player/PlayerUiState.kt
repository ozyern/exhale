/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.playback.PlayerConnection

/** Shared state collected once for player skins. Fields remain [State] handles so each skin
 * observes only the values it reads. [connection] also provides imperative player actions.
 * Video lookup is opt-in because collecting [connection]'s video stream starts a network request.
 */
@Stable
class PlayerUiState(
    val connection: PlayerConnection,
    val mediaMetadata: State<MediaMetadata?>,
    /** Effective play state: follows the Cast receiver while casting. */
    val isPlaying: State<Boolean>,
    val crossfadeDurationMs: State<Long>,
    val queueWindows: State<List<Timeline.Window>>,
    val currentWindowIndex: State<Int>,
    val currentSong: State<Song?>,
    val currentLyrics: State<LyricsEntity?>,
    val isMuted: State<Boolean>,
    val canSkipPrevious: State<Boolean>,
    val canSkipNext: State<Boolean>,
)

@Composable
fun rememberPlayerUiState(connection: PlayerConnection): PlayerUiState {
    val mediaMetadata = connection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying = connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val crossfadeDurationMs = connection.crossfadeDurationMs.collectAsStateWithLifecycle()
    val queueWindows = connection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex = connection.currentWindowIndex.collectAsStateWithLifecycle()
    val currentSong = connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentLyrics = connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val isMuted = connection.isMuted.collectAsStateWithLifecycle()
    val canSkipPrevious = connection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext = connection.canSkipNext.collectAsStateWithLifecycle()
    return remember(connection) {
        PlayerUiState(
            connection = connection,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            crossfadeDurationMs = crossfadeDurationMs,
            queueWindows = queueWindows,
            currentWindowIndex = currentWindowIndex,
            currentSong = currentSong,
            currentLyrics = currentLyrics,
            isMuted = isMuted,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
        )
    }
}

/** Collects the result of an explicit video request. */
@Composable
fun PlayerUiState.rememberVideoStreamLookup(): State<com.ozyern.exhale.playback.VideoStreamLookup> =
    connection.videoStreamLookup.collectAsStateWithLifecycle()
