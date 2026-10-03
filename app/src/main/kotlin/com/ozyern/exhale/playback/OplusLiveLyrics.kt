/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import com.ozyern.exhale.BuildConfig
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.LyricsUtils
import org.json.JSONObject

/**
 * Lock-screen lyrics for OxygenOS / ColorOS "Live Space".
 *
 * OPlus SystemUI reads timed lyrics off the active media session under a single string metadata
 * key, `lyricInfo`, holding a small JSON document. Publish it and the lock screen's music focus
 * mode — the one where the artwork fills the screen — shows our lyrics instead of "No lyrics",
 * and the Live Alert capsule can scroll the current line.
 *
 * This is a *published third-party protocol*, not an entry in the Android SDK: the schema below
 * comes from the ColorOS Live Lyrics Bridge integration spec, which documents the payload OPlus's
 * own players use. The reader hooks `MediaSession#setMetadata`, so what matters is not that the
 * payload exists on our [MediaItem] but that a `setMetadata` call carrying it actually reaches the
 * platform session — see [MusicService.publishLiveLyrics] for why that is the hard part under
 * Media3. Being a private SystemUI surface, the key can also change between OPlus releases.
 *
 * See `docs/PLAYER_INTEGRATION.md` of Andrea-lyz/ColorOS-Live-Lyrics-Bridge.
 */
object OplusLiveLyrics {

    /** The media-metadata key OPlus SystemUI reads the lyric document from. */
    const val METADATA_KEY = "lyricInfo"

    /**
     * Every key the whole-song document is published under, not just [METADATA_KEY].
     *
     * The lyric surfaces are separate SystemUI consumers of the same media session and they do
     * not have to agree on a key — which is exactly what the observed split looks like: the Live
     * Alert capsule scrolls our lines correctly while the lock screen still falls back to "no
     * lyrics". `lyricInfo` is the key documented against ColorOS 15; the class doc already warned
     * a private SystemUI key can move between OPlus releases, and a lock screen reading a renamed
     * key fails in precisely this way.
     *
     * Publishing under all of them is close to free — one `Bundle` with a few more short strings —
     * and it cannot regress the surface that already works, because that surface keeps reading the
     * key it always read. Every other ROM ignores all of them.
     */
    // The contract names one key. The three guesses that used to ride alongside it were copies
    // of the same document under names nothing documents, and a reader that meets the payload
    // twice is exactly the "duplicate lyric publication" the Bridge's checklist fails on.
    val METADATA_KEY_ALIASES = listOf(METADATA_KEY)

    /** Keys earlier builds wrote, so an upgrade clears them off a live session. */
    val RETIRED_KEYS = listOf(
        "oplus.lyricInfo",
        "com.oplus.media.metadata.LYRIC",
        "android.media.metadata.LYRIC",
    )

    /**
     * Keys carrying the **single line playing right now**, refreshed as playback advances.
     *
     * This is a different shape of protocol from [METADATA_KEY_ALIASES], not another guess at a
     * name for the same one, and it closes a real gap rather than widening a net. The document
     * keys hand the consumer a whole LRC and leave it to do its own timing against the session's
     * playback position; a consumer built the other way round — one that expects to be *fed* the
     * current line and simply renders whatever it was last given — receives nothing at all from
     * us today. A capsule that scrolls a document while a lock screen shows nothing is consistent
     * with the lock screen being that second kind of consumer.
     *
     * These ride on the session extras rather than the track metadata: extras are forwarded
     * verbatim and immediately, so publishing per line there costs nothing and cannot trip the
     * per-track metadata debounce the document path has to be careful about.
     */
    val CURRENT_LINE_KEY_ALIASES = listOf(
        "currentLyric",
        "oplus.currentLyric",
        "com.oplus.media.metadata.CURRENT_LYRIC",
        "lyric",
    )

    /** Companion keys for the current line's start time, in milliseconds. */
    val CURRENT_LINE_TIME_KEY_ALIASES = listOf(
        "currentLyricTime",
        "oplus.currentLyricTime",
    )

    /**
     * Manifest opt-in that keeps the ColorOS media card alive after the app is fully stopped.
     * Declared in AndroidManifest. Not required for lyric delivery itself.
     *
     * Worth being clear about what this is and is not: the name is the community bridge's, and it
     * is the *bridge* that reads it. Stock ColorOS SystemUI gates its lock-screen lyric surface on
     * its own package whitelist, which a third-party player is never going to be on. So on a
     * device without the bridge installed, a working Live Alert capsule and an empty lock screen
     * is a possible *correct* outcome of everything here — the capsule is an open surface, the
     * lock screen may simply not be one. The multi-key and current-line publication exist to cover
     * the case where it IS readable and we were addressing it wrongly; they cannot open a door
     * that is bolted from the other side.
     */
    const val MANIFEST_META_MEDIA_HISTORY =
        "io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"

    /**
     * The community bridge's package.
     *
     * Its presence is the one reliable signal that the `lyricInfo` document will actually be drawn
     * on an OPlus lock screen. Stock ColorOS 16.1 gates that surface on an OPlus-maintained remote
     * config of partner players - verified on device: the document reaches the platform session
     * under every key and SystemUI still renders nothing - and the bridge exists precisely to patch
     * those checks out.
     */
    const val BRIDGE_PACKAGE = "io.github.andrealtb.lockscreenlyrics"

    /** A line-level LRC timestamp: `[m:ss]`, `[mm:ss.xx]`, `[mm:ss:xxx]`. */
    private val LineTimeRegex = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")

    /** A word-level timestamp inside an enhanced-LRC line: `<mm:ss.xx>`. */
    private val WordTimeRegex = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    /**
     * Normalises whatever a provider gave us into the line-level LRC the ROM can scroll, or null
     * when the lyrics cannot drive a lock screen at all.
     *
     * Two provider formats reach us. Plain LRC passes through. TTML — which the word-synced
     * providers return, and which is what a premium-looking lyrics screen is usually rendering —
     * is not LRC and would otherwise be silently dropped here, so it is flattened to one timed
     * line per cue.
     *
     * Unsynced plain text is deliberately rejected: the lock screen cannot scroll it, and a
     * payload it cannot use is worse than none, because it displaces the "no lyrics" state the
     * ROM would otherwise fall back to.
     */
    fun toLrc(lyrics: String?): String? {
        if (lyrics.isNullOrBlank()) return null
        if (LyricsUtils.isTtml(lyrics)) return ttmlToLrc(lyrics) { entry -> entry.text }
        if (!LineTimeRegex.containsMatchIn(lyrics)) return null
        return lineLane(lyrics)
    }

    /**
     * The `lyric` lane as the contract defines it: line-timed rows only.
     *
     * Providers hand over whatever their LRC carries — `[ar:]`/`[offset:]` headers, blank spacer
     * rows, and in the word-synced flavour a `<mm:ss.xx>` tag before every word. The stock lyric
     * list reads this lane literally, so word tags showed up as text (or failed the parse) and a
     * header row with no time sat at the top. Words live in `rawLyric`; this keeps the lines.
     */
    private fun lineLane(lrc: String): String? {
        val rows = lrc.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            val tag = LineTimeRegex.find(line)?.takeIf { it.range.first == 0 } ?: return@mapNotNull null
            // Several leading tags on one row (a repeated chorus) each stand for their own line.
            var rest = line
            val tags = mutableListOf<String>()
            while (true) {
                val next = LineTimeRegex.find(rest)?.takeIf { it.range.first == 0 } ?: break
                tags += next.value
                rest = rest.substring(next.range.last + 1)
            }
            val text = rest.replace(WordTimeRegex, "").replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty() || tag.value.isEmpty()) null else tags.map { it + text }
        }.flatten().toList()
        return rows.takeIf { it.isNotEmpty() }?.joinToString("\n", postfix = "\n")
    }

    /**
     * The word-timed form, for ROMs that highlight per syllable, or null when we have no word
     * timings to offer. Enhanced LRC passes through; TTML is re-emitted with its word timings.
     */
    private fun toEnhancedLrc(lyrics: String): String? {
        if (LyricsUtils.isTtml(lyrics)) {
            return ttmlToLrc(lyrics) { entry ->
                val words = entry.words?.takeIf { it.isNotEmpty() } ?: return@ttmlToLrc null
                words.joinToString("") { word -> stamp(word.startTime, "<", ">") + word.text }
            }
        }
        return lyrics.takeIf { WordTimeRegex.containsMatchIn(it) }
    }

    /**
     * Builds the `lyricInfo` document, or null when [lyrics] cannot drive a lock screen.
     *
     * @param songId a *stable* id for the track. The ROM uses it to decide whether a metadata
     *   update is a new song or a refresh of the current one, so it must be our media id and
     *   nothing derived from the lyric text.
     */
    fun buildPayload(
        songId: String,
        songName: String,
        artist: String,
        lyrics: String?,
        album: String? = null,
        durationMs: Long = 0L,
    ): String? {
        val lrc = toLrc(lyrics) ?: return null
        return buildPayloadFromLrc(songId, songName, artist, lrc, lyrics, album, durationMs)
    }

    /** Bumped for every *new* document, so a reader can drop one that arrives out of order. */
    private val sessionGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Generations already handed out, keyed by the document's content.
     *
     * The counter has to stay off the hot path of *re*-building the same document. Publication is
     * idempotent by string comparison — MusicService asks "does the current item already carry
     * exactly this payload?" and skips the forced metadata rewrite when it does — so a field that
     * changed on every call made that question always answer no, and every track took the forced
     * path. The OPlus spec's one hard rule is that a second write close behind the first is how the
     * ROM decides to discard it, which is a lock screen showing nothing at all.
     *
     * So the generation is a property of the document, not of the call: build the same track's
     * lyrics twice and the bytes match, while a genuinely new document still gets a higher number
     * than everything before it. Bounded, because this is per playback session and the only thing
     * worth remembering is the recent past.
     */
    private val generations = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Int>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Int>?): Boolean = size > 32
        }
    )

    /**
     * The document as [buildPayload] would emit it, from an already-normalised [lrc].
     *
     * Split out so the caller can normalise once and keep the LRC for the line ticker instead of
     * running the TTML flattener a second time to get the same string back.
     */
    fun buildPayloadFromLrc(
        songId: String,
        songName: String,
        artist: String,
        lrc: String,
        rawLyrics: String?,
        album: String? = null,
        durationMs: Long = 0L,
    ): String {
        // The full document the ColorOS Live Lyrics contract describes, not just the four fields
        // the lock screen needs to draw a line.
        //
        // `trackKey` and `sessionGeneration` are what let the reader throw away a payload that
        // arrives after the song has already changed — without them a slow lyric fetch can paint
        // the previous song's words over the new one, which on Live Space is the failure people
        // report as "lyrics stuck on the last track". `provider` and `source` are diagnostics, so
        // a log from someone else's phone says which app and which build produced the document.
        // Per track, not per document: the contract bumps the generation only on a real track
        // change, and a second lyric source for the same song is the same generation.
        val identity = listOf(songId, songName, artist).joinToString("|")
        val generation = generations.getOrPut(identity) { sessionGeneration.incrementAndGet() }
        // `lyricType` and `noLyric` are the two fields the *stock* ColorOS lyric page reads before
        // it will draw anything: the first says this is the standard timed payload, the second that
        // there is a usable timeline. The document was complete in every other respect and still
        // showed nothing, because a reader that cannot tell a lyric payload from an empty one falls
        // back to its own "no lyrics" state.
        return JSONObject()
            .put("songName", songName)
            .put("artist", artist)
            .apply { album?.takeIf { it.isNotBlank() }?.let { put("album", it) } }
            .put("songId", songId)
            .put("lyricType", 0)
            .put("noLyric", false)
            .put("lyric", lrc)
            .apply { rawLyrics?.let { raw -> toEnhancedLrc(raw)?.let { put("rawLyric", it) } } }
            .put("provider", BuildConfig.APPLICATION_ID)
            .put("source", "${BuildConfig.APPLICATION_ID}-v${BuildConfig.VERSION_NAME}")
            // Identity in the order the contract recommends: media id first, then title, artist
            // and duration, so a reader with no stable id can still match on the triple.
            .put(
                "trackKey",
                listOfNotNull(
                    songId,
                    songName.lowercase(),
                    artist.lowercase(),
                    durationMs.takeIf { it > 0L }?.let { ms -> (ms / 1000).toString() },
                ).joinToString("|"),
            )
            .put("sessionGeneration", generation)
            .toString()
    }

    /** The payload currently attached to this item, if any. */
    fun MediaItem.lyricInfo(): String? = mediaMetadata.extras?.getString(METADATA_KEY)

    /**
     * Returns a copy of this item carrying [payload], and optionally [signal].
     *
     * `buildUpon` is what makes this safe: it preserves the local configuration, and with it the
     * `tag` that the whole app reads its own [com.ozyern.exhale.models.MediaMetadata] out of via
     * `Player.currentMetadata`. Rebuilding the item from scratch here would strip that tag and
     * quietly break every screen that asks the player what is playing.
     */
    fun MediaItem.withLyricInfo(payload: String, signal: Uri? = null): MediaItem {
        val extras = mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()
        METADATA_KEY_ALIASES.forEach { key -> extras.putString(key, payload) }
        return buildUpon()
            .setMediaMetadata(
                mediaMetadata.buildUpon()
                    .setExtras(extras)
                    .build()
            )
            .apply {
                if (signal != null) {
                    setRequestMetadata(
                        requestMetadata.buildUpon()
                            .setMediaUri(signal)
                            .build()
                    )
                }
            }
            .build()
    }

    /**
     * Returns a copy of this item whose displayed subtitle is [line], or [fallbackArtist] again
     * when [line] is null.
     *
     * Both `artist` and `subtitle` are written because readers disagree about which one they
     * take: Media3 maps `artist` to `METADATA_KEY_ARTIST` and `subtitle` to
     * `METADATA_KEY_DISPLAY_SUBTITLE`, and the platform media control prefers the display key
     * when it is present but falls back to the artist when it is not.
     *
     * `buildUpon` again, for the same reason as [withLyricInfo]: it keeps the local configuration
     * and with it the `tag` that the whole app reads its own metadata from. The app therefore
     * still shows the real artist everywhere — only the *session's* subtitle changes, which is
     * exactly the field the lock screen renders.
     */
    fun MediaItem.withDisplayLine(line: String?, fallbackArtist: String?): MediaItem =
        buildUpon()
            .setMediaMetadata(
                mediaMetadata.buildUpon()
                    .setArtist(line ?: fallbackArtist)
                    .setSubtitle(line ?: fallbackArtist)
                    .build()
            )
            .build()

    /**
     * The value written to `requestMetadata.mediaUri` to make a lyrics-only change visible to
     * Media3's session layer. See [MusicService.publishLiveLyrics] for why this is needed;
     * [revision] must differ from the previous publication for the same track.
     */
    fun signalUri(songId: String, revision: Int): Uri =
        "exhale://live-lyrics/$songId/$revision".toUri()

    /**
     * Flattens TTML to LRC. [line] renders one cue's body and may return null to drop it, which
     * is how the enhanced form opts out of cues that carry no word timings.
     */
    private fun ttmlToLrc(ttml: String, line: (LyricsEntry) -> String?): String? {
        val entries = runCatching { LyricsUtils.parseTtml(ttml) }.getOrNull().orEmpty()
        if (entries.isEmpty()) return null
        val rendered = entries.mapNotNull { entry ->
            val body = line(entry) ?: return@mapNotNull null
            stamp(entry.time / 1000.0, "[", "]") + body
        }
        return rendered.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /** `mm:ss.cc` wrapped in the given delimiters. [seconds] is clamped at zero. */
    private fun stamp(seconds: Double, open: String, close: String): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val minutes = totalMs / 60_000
        val secs = (totalMs % 60_000) / 1000
        val millis = totalMs % 1000
        return "%s%02d:%02d.%03d%s".format(open, minutes, secs, millis, close)
    }
}
