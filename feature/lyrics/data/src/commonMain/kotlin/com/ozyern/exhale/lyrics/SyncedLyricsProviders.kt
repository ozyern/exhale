package com.ozyern.exhale.lyrics

import com.ozyern.exhale.lyrics.providers.*
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How long a source may keep rephrasing before its answer would arrive too late to be used. */
private val PhrasingBudget = 9.seconds

/** One question to a source: the song, how it is phrased, and the recording when it is known. */
class LyricsAsk(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String?,
    val isrc: String?,
)

/**
 * The reference design's providers, adapted to Exhale's persisted lyric format and asked the way Exhale for
 * Windows asks them: the recording named first where a source can use it ([usesRecording]), then
 * every phrasing in turn ([LyricsSearch.phrasings]), and for the sources that match on length, once
 * more with the length set aside ([lengthFree]), since a video's runtime carries its intro and tail.
 * Whatever answers has to fit the song ([LyricsSearch.fits]) or the next phrasing is tried.
 */
class SyncedLyricsProvider(
    override val id: String,
    override val displayName: String,
    private val usesRecording: Boolean = false,
    private val lengthFree: Boolean = false,
    private val lookup: suspend (LyricsAsk) -> List<LyricLine>?,
) : LyricsProvider {
    override val requiresVideoId: Boolean get() = id == "SimpMusic"

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> = try {
        val durationMs = duration.toLong() * 1000L
        val phrasings = LyricsSearch.phrasings(title, artist)
        val isrc = if (usesRecording) {
            phrasings.firstOrNull()?.let { (song, singer) -> Recordings.isrc(song, singer, durationMs, album) }
        } else {
            null
        }
        val asks = phrasings.map { (song, singer) -> LyricsAsk(id, song, singer, durationMs, album, isrc) } +
            listOfNotNull(
                phrasings.firstOrNull()
                    ?.takeIf { lengthFree && durationMs > 0 }
                    ?.let { (song, singer) -> LyricsAsk(id, song, singer, 0L, null, isrc) },
            )
        var timedForAnother = false
        var found: List<LyricLine>? = null
        // A source that looks songs up by video id has nothing to rephrase.
        val started = TimeSource.Monotonic.markNow()
        for (ask in if (requiresVideoId) asks.take(1) else asks) {
            // The repository gives a source twelve seconds in all; no new phrasing after nine.
            if (started.elapsedNow() > PhrasingBudget) break
            val raw = lookup(ask)
            if (raw.isNullOrEmpty()) continue
            // KuGou answers searches it did not really match, and any source asked with the length
            // set aside is matching on the name alone; line-timed BetterLyrics is KuGou passed on.
            val loose = this.id == "Kugou" || ask.durationMs == 0L && durationMs > 0 ||
                (this.id.startsWith("BetterLyrics") && raw.none { it.isWordSynced })
            val lines = LyricsSearch.judge(raw, title, loose) ?: continue
            // Timed for another cut of the song: try the next phrasing, then let the next source answer.
            if (!LyricsSearch.fits(lines, durationMs, title)) {
                timedForAnother = true
                continue
            }
            found = lines
            break
        }
        when {
            found != null -> Result.success(found.toExhaleLyrics())
            timedForAnother -> Result.failure(NoSuchElementException("Lyrics are timed for another version"))
            else -> Result.failure(NoSuchElementException("No lyrics found"))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

internal fun List<LyricLine>.toExhaleLyrics(): String {
    if (none { it.timeMs > 0 || it.words.isNotEmpty() }) return joinToString("\n") { it.text }
    fun stamp(ms: Long) = "${(ms / 60_000).toString().padStart(2, '0')}:" +
        "${(ms / 1000 % 60).toString().padStart(2, '0')}.${(ms % 1000).toString().padStart(3, '0')}"
    fun LyricLine.row(background: Boolean = false): String = buildString {
        append("[${stamp(timeMs.coerceAtLeast(0))}]")
        if (background) append("{bg}")
        if (alignment == LyricAlignment.End) append("{agent:v2}")
        if (words.isEmpty()) append(text) else words.forEachIndexed { index, word ->
            if (index > 0) append(' ')
            append("<${stamp(word.startMs)}|${stamp(word.endMs)}>${word.text}")
        }
    }
    return flatMap { line -> listOfNotNull(line.row(), line.background?.row(true)) }.joinToString("\n")
}

object SyncedLyricsProviders {
    val all: List<LyricsProvider> = listOf(
        SyncedLyricsProvider("LrcRed", "lrc.red", usesRecording = true) { LrcRed.lyrics(it.title, it.artist, it.durationMs, it.isrc) },
        SyncedLyricsProvider("BiniLyrics", "BiniLyrics", usesRecording = true) {
            BiniLyrics.lyrics(it.title, it.artist, it.durationMs, it.album, it.isrc)?.lines
        },
        SyncedLyricsProvider("BetterLyrics", "Better Lyrics", lengthFree = true) {
            BetterLyrics.lyrics(it.title, it.artist, it.durationMs, it.album)
        },
        SyncedLyricsProvider("BetterLyricsPortato", "BetterLyrics Portato", lengthFree = true) {
            BetterLyrics.portato(it.title, it.artist, it.durationMs, it.album)
        },
        SyncedLyricsProvider("Paxsenix", "PaxSenix") { PaxSenix.lyrics(it.title, it.artist, it.durationMs, it.album) },
        SyncedLyricsProvider("PaxsenixSpotify", "PaxSenix: Spotify") { PaxSenix.spotifyLyrics(it.title, it.artist, it.durationMs) },
        SyncedLyricsProvider("PaxsenixMusixmatch", "PaxSenix: Musixmatch") { PaxSenix.musixmatchLyrics(it.title, it.artist, it.durationMs) },
        SyncedLyricsProvider("YouLyPlus", "LyricsPlus", usesRecording = true) {
            LyricsPlus.lyrics(it.title, it.artist, it.durationMs, it.album, it.isrc)
        },
        SyncedLyricsProvider("SimpMusic", "SimpMusic") { SimpMusicLyrics.lyrics(it.id, it.durationMs) },
        SyncedLyricsProvider("Unison", "Unison") { Unison.lyrics(it.title, it.artist, it.durationMs, it.album) },
        SyncedLyricsProvider("Megalobiz", "Megalobiz") { Megalobiz.lyrics(it.title, it.artist) },
        SyncedLyricsProvider("Kugou", "KuGou", lengthFree = true) { KuGou.lyrics(it.title, it.artist, it.durationMs, it.album) },
        SyncedLyricsProvider("LrcLib", "LRCLIB") { LrcLib.lyrics(it.title, it.artist, it.durationMs) },
        SyncedLyricsProvider("Musixmatch", "Musixmatch") { Musixmatch.lyrics(it.title, it.artist, it.durationMs) },
        SyncedLyricsProvider("Genius", "Genius") { Genius.lyrics(it.title, it.artist) },
    )
}
