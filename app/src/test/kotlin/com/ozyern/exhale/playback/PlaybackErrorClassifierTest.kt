/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorClassifierTest {

    private fun errorOf(code: Int, cause: Throwable? = null, message: String = "") =
        PlaybackException(message, cause, code)

    @Test
    fun `classifies decoder reclaim before generic decoder init failure`() {
        val reclaim = errorOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            cause = RuntimeException("codec reclaimed by resource manager")
        )
        assertEquals(PlaybackErrorType.DecoderReclaim, PlaybackErrorClassifier.classify(reclaim, isNetworkConnected = true))

        val ordinaryInitFailure = errorOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, cause = RuntimeException("no codec found"))
        assertEquals(PlaybackErrorType.Other, PlaybackErrorClassifier.classify(ordinaryInitFailure, isNetworkConnected = true))
    }

    @Test
    fun `classifies audio renderer before network when both could apply`() {
        val audioTrack = errorOf(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)
        assertEquals(PlaybackErrorType.AudioRenderer, PlaybackErrorClassifier.classify(audioTrack, isNetworkConnected = false))
    }

    @Test
    fun `falls back to network type when offline regardless of error code`() {
        val unrelated = errorOf(PlaybackException.ERROR_CODE_REMOTE_ERROR)
        assertEquals(PlaybackErrorType.Network, PlaybackErrorClassifier.classify(unrelated, isNetworkConnected = false))
        assertEquals(PlaybackErrorType.Other, PlaybackErrorClassifier.classify(unrelated, isNetworkConnected = true))
    }

    @Test
    fun `detects page reload error via message keyword across languages`() {
        val reload = errorOf(PlaybackException.ERROR_CODE_REMOTE_ERROR, message = "The page needs to be reloaded")
        assertEquals(PlaybackErrorType.PageReload, PlaybackErrorClassifier.classify(reload, isNetworkConnected = true))
    }
}
