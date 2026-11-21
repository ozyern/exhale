/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/**
 * The `lyricInfo` document for the song playing now, shared by every [SessionLyricPlayer] the
 * service creates: the session's player is swapped for the incoming one on every crossfade.
 *
 * As Echo Music publishes it (EchoMusicApp/Echo-Music#881).
 */
class SessionLyricInfo {
    class Payload(val mediaId: String, val json: String, val stamp: String)

    @Volatile private var payload: Payload? = null
    private var publishCount = 0L

    fun current(mediaId: String?): Payload? = payload?.takeIf { it.mediaId == mediaId }

    /** Returns true when the session has to republish its metadata. */
    @Synchronized
    fun set(mediaId: String?, json: String?): Boolean {
        val old = payload
        if (mediaId == null || json == null) {
            if (old == null) return false
            payload = null
            return true
        }
        if (old != null && old.mediaId == mediaId && old.json == json) return false
        publishCount++
        // Two invisible values in turn (word joiners): see SessionLyricPlayer.withLyricInfo.
        payload = Payload(mediaId, json, if (publishCount % 2 == 0L) STAMP_EVEN else STAMP_ODD)
        return true
    }

    private companion object {
        const val STAMP_ODD = "⁠"
        const val STAMP_EVEN = "⁠⁠"
    }
}

/**
 * The player the media session sees. It adds the song's lyrics to the session's metadata as
 * `lyricInfo`, where ColorOS / OxygenOS SystemUI reads them for the lock-screen lyric page, the
 * lock-screen media card and the always-on display. The app keeps talking to the ExoPlayer
 * directly, and its media items are never rewritten for lyrics.
 *
 * Media3 copies String extras into the platform session's metadata, but [MediaMetadata.equals]
 * ignores extras, so a change to the lyrics alone would never be republished, and lyrics usually
 * arrive after the song has started. `station` takes part in `equals` and is not exported to the
 * platform session, so it carries an invisible suffix that changes with every new document.
 */
class SessionLyricPlayer(
    player: Player,
    private val lyricInfo: SessionLyricInfo,
) : ForwardingPlayer(player) {

    private val listeners = HashMap<Player.Listener, Player.Listener>()

    private inner class LyricListener(private val delegate: Player.Listener) : Player.Listener by delegate {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            delegate.onMediaMetadataChanged(withLyricInfo(mediaMetadata))
        }
    }

    override fun addListener(listener: Player.Listener) {
        val wrapped = synchronized(listeners) { listeners.getOrPut(listener) { LyricListener(listener) } }
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        val wrapped = synchronized(listeners) { listeners.remove(listener) } ?: return
        super.removeListener(wrapped)
    }

    override fun getMediaMetadata(): MediaMetadata = withLyricInfo(super.getMediaMetadata())

    private fun withLyricInfo(metadata: MediaMetadata): MediaMetadata {
        val info = lyricInfo.current(super.getCurrentMediaItem()?.mediaId) ?: return metadata
        val extras = Bundle(metadata.extras ?: Bundle()).apply {
            putString(OplusLiveLyrics.METADATA_KEY, info.json)
        }
        return metadata.buildUpon()
            .setExtras(extras)
            .setStation("${metadata.station ?: ""}${info.stamp}")
            .build()
    }

    private fun playerMediaMetadata(): MediaMetadata = super.getMediaMetadata()

    /** Republishes the session's metadata after [SessionLyricInfo] changed. */
    fun onLyricInfoChanged() {
        val dispatch = Runnable {
            val metadata = playerMediaMetadata()
            synchronized(listeners) { listeners.values.toList() }.forEach { it.onMediaMetadataChanged(metadata) }
        }
        if (Looper.myLooper() == applicationLooper) dispatch.run() else Handler(applicationLooper).post(dispatch)
    }
}
