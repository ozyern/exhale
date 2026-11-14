/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.ozyern.exhale.constants.EnableLockScreenLyricsKey
import com.ozyern.exhale.constants.LockScreenLyricsOverlayKey
import com.ozyern.exhale.constants.LyricsOnMediaCardKey
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lyrics outside the app: the three ways Exhale puts the words where the phone shows its music.
 *
 *  - **The media card line.** The line being sung is written into the session's subtitle, which
 *    every Android lock screen's media card draws under the title. No whitelist, no module: this
 *    is the one that works everywhere.
 *  - **The ColorOS / OxygenOS Live Space document.** The whole song as `lyricInfo` on the
 *    session ([OplusLiveLyrics]), for the lock screen's lyric page and the Live Alert capsule.
 *  - **Exhale's own lyrics screen** over the keyguard ([LockScreenLyrics]).
 *
 * The lyrics are whatever the player has saved for the current song, so this never fetches; it
 * follows the database and republishes when a better copy is saved.
 *
 * Everything that touches the player runs on the main thread ([scope] is a main scope).
 */
class LiveLyricsPublisher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> ExoPlayer,
    private val session: () -> MediaSession?,
    private val current: StateFlow<MediaMetadata?>,
    private val lyricsFor: (String) -> Flow<String?>,
    private val preferences: Flow<Preferences>,
) {
    private var receiver: BroadcastReceiver? = null
    private var tickerJob: Job? = null
    private var lines: List<LyricsEntry> = emptyList()
    private var lineIndex = -1
    private var cardLine: String? = null
    private var revision = 0

    @Volatile private var onMediaCard = true
    @Volatile private var liveSpace = true

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        receiver = LockScreenLyrics.register(context, player())
        preferences.map {
            Triple(it[LyricsOnMediaCardKey] ?: true, it[EnableLockScreenLyricsKey] ?: true, it[LockScreenLyricsOverlayKey] ?: true)
        }.distinctUntilChanged().onEach { (card, space, overlay) ->
            onMediaCard = card
            liveSpace = space
            LockScreenLyrics.enabled = overlay
            if (!card) publishCardLine(null)
        }.launchIn(scope)

        current.flatMapLatest { metadata ->
            if (metadata == null) flowOf(null to null)
            else lyricsFor(metadata.id).map { metadata to it }
        }.distinctUntilChanged().onEach { (metadata, lyrics) ->
            publish(metadata, lyrics?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND })
        }.launchIn(scope)
    }

    fun stop() {
        tickerJob?.cancel()
        LockScreenLyrics.unregister(context, receiver)
        receiver = null
    }

    private suspend fun publish(metadata: MediaMetadata?, lyrics: String?) {
        val (lrc, parsed) = withContext(Dispatchers.Default) {
            val lrc = OplusLiveLyrics.toLrc(lyrics)
            lrc to (lrc?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }.orEmpty())
        }
        startTicker(parsed)
        if (metadata == null || !liveSpace) return
        val payload = lrc?.let {
            OplusLiveLyrics.buildPayloadFromLrc(
                songId = metadata.id,
                songName = metadata.title,
                artist = metadata.artists.joinToString { a -> a.name },
                lrc = it,
                rawLyrics = lyrics,
                album = metadata.album?.title,
                durationMs = metadata.duration.takeIf { d -> d > 0 }?.times(1000L) ?: 0L,
            )
        } ?: return
        if (carries(metadata.id, payload)) return
        forceDocument(metadata.id, payload)
        // One retry, only if the first did not stick: OPlus debounces updates that arrive close
        // together, so an unconditional second write is what gets the first one discarded.
        delay(REPUBLISH_DELAY_MS)
        if (!carries(metadata.id, payload)) forceDocument(metadata.id, payload)
    }

    private fun carries(songId: String, payload: String): Boolean = with(OplusLiveLyrics) {
        val item = player().currentMediaItem ?: return false
        item.mediaId == songId && item.lyricInfo() == payload
    }

    /** Republishes the current item with [payload] and a fresh signal so the session passes it on. */
    private fun forceDocument(songId: String, payload: String) {
        val p = player()
        val item = p.currentMediaItem ?: return
        if (item.mediaId != songId) return
        revision++
        val updated = with(OplusLiveLyrics) { item.withLyricInfo(payload, signalUri(songId, revision)) }
        p.replaceMediaItem(p.currentMediaItemIndex, updated)
        Log.i(TAG, "$songId: lyric document published (#$revision)")
    }

    private fun startTicker(parsed: List<LyricsEntry>) {
        tickerJob?.cancel()
        lineIndex = -1
        cardLine = null
        lines = parsed
        LockScreenLyrics.publishLines(parsed)
        if (parsed.isEmpty()) {
            publishCurrentLine(null, 0L)
            publishCardLine(null)
            return
        }
        tickerJob = scope.launch {
            while (isActive) {
                val p = player()
                if (!p.isPlaying || lines.isEmpty()) {
                    delay(IDLE_TICK_MS)
                    continue
                }
                val position = p.currentPosition
                val index = currentIndex(lines, position + PUBLISH_LEAD_MS)
                if (index >= 0 && index != lineIndex) {
                    lineIndex = index
                    // A blank entry is a breath between lines: keep the line just sung rather
                    // than flicking the card back to the artist for a moment.
                    lines[index].text.replace(OplusLiveLyrics.AnyTimeTagRegex, "").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }?.let { text ->
                        publishCurrentLine(text, lines[index].time)
                        publishCardLine(text)
                    }
                }
                val next = lines.getOrNull(index + 1)?.time
                val speed = p.playbackParameters.speed.takeIf { it > 0.05f } ?: 1f
                val wait = if (next == null) TICK_MS else ((next - PUBLISH_LEAD_MS - position) / speed).toLong()
                delay(wait.coerceIn(MIN_TICK_MS, TICK_MS))
            }
        }
    }

    /** The line sung at [position]: the last line starting at or before it, or -1. */
    private fun currentIndex(lines: List<LyricsEntry>, position: Long): Int {
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].time <= position) { found = mid; low = mid + 1 } else high = mid - 1
        }
        return found
    }

    /** The current line on the session extras, for consumers that draw whichever line they are handed. */
    private fun publishCurrentLine(line: String?, timeMs: Long) {
        val session = session() ?: return
        if (line != null && cardLineWouldFightDocument) return
        val extras = Bundle(session.sessionExtras)
        OplusLiveLyrics.CURRENT_LINE_KEY_ALIASES.forEach { key -> if (line == null) extras.remove(key) else extras.putString(key, line) }
        OplusLiveLyrics.CURRENT_LINE_TIME_KEY_ALIASES.forEach { key -> if (line == null) extras.remove(key) else extras.putLong(key, timeMs) }
        session.sessionExtras = extras
    }

    /**
     * The line written into the session's displayed subtitle, which the lock-screen media card
     * draws under the title; null puts the real artist back. The item keeps its address and tag,
     * so the player takes it as a metadata edit, not a new song (see [ResolvingMediaSource]).
     */
    private fun publishCardLine(line: String?) {
        if (line != null && (!onMediaCard || cardLineWouldFightDocument)) return
        if (line == cardLine) return
        val p = player()
        val item = p.currentMediaItem ?: return
        val artist = current.value?.takeIf { it.id == item.mediaId }?.artists?.joinToString { it.name }
        cardLine = line
        with(OplusLiveLyrics) { p.replaceMediaItem(p.currentMediaItemIndex, item.withDisplayLine(line, artist)) }
    }

    /**
     * Where the ColorOS lyric page really draws the document (the Live Lyrics Bridge, or a system
     * that unlocks the page), rewriting the subtitle every line is what breaks it, so the card
     * line stands down there. Everywhere else the subtitle is the only lyric anyone will see.
     */
    private val cardLineWouldFightDocument: Boolean
        get() = liveSpace && isOplus && (bridgeInstalled || OplusLiveLyrics.lyricPageUnlockedBySystem())

    private val isOplus: Boolean by lazy {
        Build.MANUFACTURER.lowercase().let { it == "oppo" || it == "oneplus" || it == "realme" }
    }

    private val bridgeInstalled: Boolean by lazy {
        runCatching { context.packageManager.getPackageInfo(OplusLiveLyrics.BRIDGE_PACKAGE, 0) }.isSuccess
    }

    private companion object {
        const val TAG = "LiveLyrics"
        const val TICK_MS = 1_000L
        const val MIN_TICK_MS = 40L
        const val IDLE_TICK_MS = 1_000L
        const val PUBLISH_LEAD_MS = 150L
        const val REPUBLISH_DELAY_MS = 1_500L
    }
}
