/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import com.ozyern.exhale.betterlyrics.BetterLyrics
import com.ozyern.exhale.betterlyrics.TTMLParser
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.kugou.KuGou
import com.ozyern.exhale.lrclib.LrcLib
import com.ozyern.exhale.simpmusic.SimpMusicLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

data class LyricWord(val text: String, val startMs: Long, val endMs: Long, val isBackground: Boolean = false)

/**
 * One line. [words] is always filled for synced lyrics: real per-word timings when the provider
 * has them ([wordSynced]), otherwise spread across the line by character count — the same
 * synthesis the Android app does, so every synced song animates word by word.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val wordSynced: Boolean = false,
)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>, val source: String, val wordLevel: Boolean) : Lyrics
    data class Plain(val lines: List<String>, val source: String) : Lyrics
    data object NotFound : Lyrics
}

/**
 * What to search the lyrics sites for. YouTube titles carry things no lyrics database has in its
 * title — "(feat. Don Toliver)", "[Official Video]", "- 2011 Remaster" — and a search that includes
 * them often finds nothing, or a different song.
 */
internal object SongQuery {
    private val bracketed = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val noise = Regex("""(?i)\b(feat\.?|ft\.?|featuring|with|official|video|audio|lyrics?|lyrical|visuali[sz]er|m/?v|hd|hq|4k|explicit|clean|remaster(ed)?|prod\.?|song|full|teaser|promo|title\s+track)\b""")
    private val trailingFeature = Regex("""(?i)\s+(feat\.?|ft\.?|featuring)\s.*$""")
    private val trailingRemaster = Regex("""(?i)\s+-\s+[^-]*remaster[^-]*$""")

    /** An upload's tag after a dash rather than in brackets: "Kesariya – Full Video". */
    private val trailingDashNoise =
        Regex("""(?i)\s+[-–—]\s+[^-–—]*\b(full|official|video|audio|lyrics?|lyrical|song|teaser|promo|visuali[sz]er)\b[^-–—]*$""")

    /**
     * What to search for. A song uploaded as a plain video is titled the way a channel titles a
     * video — "Re Bawri (Song) | Alia Bhatt | Don't Be Shy | Sona Mohapatra | T-Series" — where
     * everything after the first bar is credits, and searching the whole string finds nothing at all.
     */
    fun cleanTitle(title: String): String {
        val head = title.substringBefore('|').trim().ifBlank { title }
        var cleaned = bracketed.replace(head) { match -> if (noise.containsMatchIn(match.groupValues[1])) "" else match.value }
        cleaned = trailingFeature.replace(cleaned, "")
        cleaned = trailingRemaster.replace(cleaned, "")
        cleaned = trailingDashNoise.replace(cleaned, "")
        return cleaned.replace(Regex("\\s+"), " ").trim().ifBlank { head.trim() }
    }

    /**
     * The artist as a lyrics site would have it. Uploads credit the label ("T-Series"), and a bar-
     * separated title usually names the singer itself — the segment after the song is a better guess
     * than the channel.
     */
    fun creditedArtist(title: String, artists: List<String>): String? {
        val lead = artists.firstOrNull()?.takeIf { it.isNotBlank() }
        val segments = title.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 2) return lead
        // A credit list rarely repeats the label first; take the segment that isn't the song or the channel.
        return segments.drop(1).firstOrNull { segment ->
            segment.length in 2..40 && !segment.equals(lead, ignoreCase = true) && !noise.containsMatchIn(segment)
        } ?: lead
    }
}

/**
 * Lyrics for a song, from every source at once rather than one after another: each is asked with a
 * cleaned-up title and the lead artist (then as YouTube writes it, if that finds nothing), and what
 * comes back is checked against the song before it's shown — timed lyrics that run past the song's
 * end or stop far short of it belong to another version, so they're only used for their words.
 * The listener's source order breaks ties; a word-timed result beats a line-timed one.
 */
object LyricsRepository {
    /** The sources the Android app added: Apple's catalogue first, then the community and open ones. */
    private val OnlineSources: List<OnlineSource> =
        listOf(LrcRed, Binimum, PaxSenix, LyricsPlus, Unison, Musixmatch, Megalobiz, Genius)

    /**
     * Every source, in the order they're asked out of the box — the same order as the Android app:
     * the Apple-catalogue hosts first, the syllable-timed community sources, the line-timed
     * databases, and plain text last.
     */
    val defaultOrder = listOf(
        "lrc.red", "Binimum", "BetterLyrics", "PaxSenix", "LyricsPlus", "SimpMusic", "Unison",
        "Musixmatch", "Megalobiz", "KuGou", "LRCLIB", "Genius", "YouTube Music",
    )

    val providerNames = defaultOrder

    /** How long the sources that use it wait for the recording to be named before asking without. */
    private const val IdentifyTimeoutMs = 2_500L

    /**
     * How long one source gets before the others are shown without it. Each source now tries several
     * phrasings in turn, so this is the whole chain's budget — and it is also how long someone waits
     * to be told there are no lyrics, which is the reason it isn't longer.
     */
    private const val ProviderTimeoutMs = 7_000L

    /**
     * Once timed words are in hand, how long the rest get to turn up something better. Only line-level
     * lyrics are ever waited on like this — a word-timed result ends the wait outright — so this is
     * the window in which a source that times every word can still beat one that times every line,
     * which is the difference between the words lighting up as they're sung and a whole line at a time.
     */
    private const val GraceMs = 2_200L

    private val cache = object : LinkedHashMap<String, Lyrics>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>) = size > 50
    }

    /** After a provider is switched on or off, so a cached "not found" doesn't stick. */
    fun clearCache() = synchronized(cache) { cache.clear() }

    private class Candidate(
        val source: String,
        val synced: List<LyricLine>?,
        val plain: List<String>?,
        /** Words masked out — "f***". Some sources only ever keep the radio edit's words. */
        val censored: Boolean = false,
        /** The song's own name turns up in the words, as it does in most songs. */
        val echoesTitle: Boolean = true,
    ) {
        /** Distinct words of three letters or more, to hold one source's answer against another's. */
        val vocabulary: Set<String> by lazy {
            val text = synced?.joinToString(" ") { it.text } ?: plain.orEmpty().joinToString(" ")
            text.lowercase(Locale.US).split(Regex("""[^\p{L}\p{N}']+""")).filter { it.length >= 3 }.toSet()
        }

        val wordLevel: Boolean get() = synced?.any { it.wordSynced } == true
    }

    /**
     * Words with their middles knocked out. Sources differ on this for the same recording, and a song
     * is not improved by being bleeped, so a copy that spells it out wins over one that doesn't.
     */
    private val masked = Regex("""\p{L}\*{2,}|\*{2,}\p{L}|\p{L}\*+\p{L}|(?<!\S)\*{3,}(?!\S)""")

    private fun isCensored(raw: String): Boolean = masked.containsMatchIn(raw)

    /**
     * Not lyrics at all. A loose search can come back with the record's paperwork — publishers,
     * writers, the label — and a source that has nothing for a song will hand that over rather than
     * admit it. It reads as lyrics to a parser and as nonsense to a listener, so it is only rejected
     * when the whole of it is paperwork: a real lyric mentioning a label stays.
     */
    private val creditLine = Regex("""(?i)(publishing|publisher|warner|chappell|universal music|sony/?atv|kobalt|songwriter|composer|lyricist|arranger|all rights reserved|℗|©|作词|作曲|编曲|制作人)""")

    private fun isPaperwork(text: String): Boolean {
        val line = text.trim()
        if (creditLine.containsMatchIn(line)) return true
        // Writers run together with slashes and no spaces — "MarshallMathers/AndreR.Young/MikeElizondo".
        if (writerList.matches(line)) return true
        // A label with nothing after it — "Sabrina Carpenter：" — is a heading in a file's metadata.
        // Sung lines do not end on a colon, and the full-width one belongs to the format, not a song.
        return line.length < 48 && (line.endsWith('：') || line.endsWith(':'))
    }

    private val writerList = Regex("""[^\s/]{2,}(/[^\s/]{2,}){2,}""")

    /** What is left once the paperwork is taken out; too little left over and it was never a song. */
    private const val MinimumLines = 4

    private val Stopwords = setOf(
        "this", "that", "with", "your", "from", "have", "what", "when", "they", "them", "just",
        "like", "about", "into", "over", "been", "were", "will", "part", "remix", "live", "version",
    )

    /**
     * Words from the title distinctive enough to look for in the words. Most songs say their own name.
     */
    private fun titleWords(title: String): List<String> =
        title.lowercase(Locale.US)
            .split(Regex("""[^\p{L}\p{N}']+"""))
            .filter { it.length >= 4 && it !in Stopwords }

    /**
     * Whether a candidate sounds like the song it was asked for.
     *
     * A source that matches on how long a recording is has no way of knowing that a video runs longer
     * than the song inside it — so asked for a 199-second "Taste" it can hand back another song by the
     * same artist that happens to run 199 seconds, which is exactly how every Sabrina Carpenter video
     * ended up singing "On My Way". Not every song says its own name, so this only ever demotes a
     * candidate; it decides nothing on its own except for the one source that searches loosely.
     */
    private fun echoesTitle(text: String, title: String): Boolean {
        val words = titleWords(title)
        if (words.isEmpty()) return true
        val body = text.lowercase(Locale.US)
        return words.any { it in body }
    }

    private val MinorWords = setOf("the", "and", "feat", "with", "from", "for", "you", "your")

    /**
     * The stricter test for a source that searches loosely: every word of the title is somewhere in
     * the words, or the title as a phrase is. "Ass Like That" has no word distinctive enough for
     * [echoesTitle], and the loose search answered it with "Lose Yourself" — which says "like" and
     * "that", but never "ass".
     */
    private fun soundsLikeTitle(text: String, title: String): Boolean {
        val body = text.lowercase(Locale.US)
        val phrase = title.lowercase(Locale.US).replace(Regex("""[^\p{L}\p{N}' ]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (phrase.isNotEmpty() && phrase in body.replace(Regex("""[^\p{L}\p{N}' ]+"""), " ").replace(Regex("""\s+"""), " ")) return true
        val words = phrase.split(' ').filter { it.length >= 3 && it !in MinorWords }
        if (words.isEmpty()) return true
        val sung = body.split(Regex("""[^\p{L}\p{N}']+""")).toSet()
        return words.all { it in sung }
    }

    private fun isTitleHeading(line: String, title: String): Boolean {
        fun bare(text: String) = text.lowercase(Locale.US).replace(Regex("""[^\p{L}\p{N}]+"""), "")
        val heading = bare(line.substringBefore(" - "))
        return heading.isNotEmpty() && heading == bare(title)
    }

    /** How much of the smaller of two answers the larger shares, word for word. */
    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (small, large) = if (a.size <= b.size) a to b else b to a
        return small.count { it in large }.toDouble() / small.size
    }

    /** Tries each lookup in turn until one returns something. */
    private suspend fun firstOf(vararg lookups: suspend () -> String?): String? {
        for (lookup in lookups) {
            val found = try {
                lookup()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    /**
     * Remembered against the runtime it was found with, not just the song. Half the sources match on
     * how long the recording is, so a lookup made before the song's length was known is a different
     * question with a worse answer — and keeping it under the same key would hand that answer back
     * for good, the song playing on without its timed words.
     */
    private fun cacheKey(id: String, duration: Int) = if (duration > 0) "$id@$duration" else id

    suspend fun lyricsFor(song: SongItem, fallbackDurationSeconds: Int): Lyrics = withContext(Dispatchers.IO) {
        val duration = song.duration ?: fallbackDurationSeconds
        val key = cacheKey(song.id, duration)
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val title = SongQuery.cleanTitle(song.title)
        val names = song.artists.map { it.name }.filter { it.isNotBlank() }
        val lead = names.firstOrNull().orEmpty()
        val everyone = names.joinToString(", ")
        // An upload credits its label, not its singer; the title's own credits are the better guess.
        val credited = SongQuery.creditedArtist(song.title, names)?.takeIf { !it.equals(lead, ignoreCase = true) }
        // The same search as written on YouTube, only when it differs from the cleaned one.
        val asWritten = song.title != title || everyone != lead
        val album = song.album?.name

        // Which recording this is, by ISRC — worked out once, by Binimum, and only if it is on: a
        // source someone switched off is not contacted, not even to help the others. lrc.red and
        // LyricsPlus can then ask for exactly this recording, so a single never gets its album
        // cut's timing or the clean edit's words.
        val recordingLock = Mutex()
        var recordingAsked = false
        var recording: Recording? = null
        val recordingFor: suspend () -> Recording? = {
            recordingLock.withLock {
                if (!recordingAsked) {
                    recordingAsked = true
                    if (DesktopPrefs.isLyricsProviderEnabled(Binimum.name)) {
                        recording = withTimeoutOrNull(IdentifyTimeoutMs) { Recordings.identify(song.id, title, lead, album, duration) }
                    }
                }
                recording
            }
        }
        val online = OnlineSources.map { source ->
            source.name to suspend {
                val known = if (source.usesRecording) recordingFor() else null
                firstOf(
                    { source.lyrics(title, lead, album, duration, known) },
                    { if (credited != null) source.lyrics(title, credited, album, duration, known) else null },
                )
            }
        }

        val providers = online + listOf<Pair<String, suspend () -> String?>>(
            "BetterLyrics" to {
                firstOf(
                    { BetterLyrics.getLyrics(title = title, artist = lead, album = album, durationSeconds = duration).getOrNull() },
                    { if (asWritten) BetterLyrics.getLyrics(title = song.title, artist = everyone, album = album, durationSeconds = duration).getOrNull() else null },
                    { if (credited != null) BetterLyrics.getLyrics(title = title, artist = credited, album = null, durationSeconds = duration).getOrNull() else null },
                    // A video carries the song with an intro on the front and a tail on the end, so its
                    // runtime is not the song's and every source that matches on one has just missed.
                    { BetterLyrics.getLyrics(title = title, artist = lead, album = null, durationSeconds = 0).getOrNull() },
                )
            },
            "SimpMusic" to {
                val tracks = SimpMusicLyrics.getLyricsByVideoId(song.id)
                val best = if (duration > 0 && tracks.size > 1) tracks.minByOrNull { abs((it.duration ?: 0) - duration) } else tracks.firstOrNull()
                best?.richSyncLyrics?.takeIf { it.isNotBlank() } ?: best?.syncedLyrics?.takeIf { it.isNotBlank() } ?: best?.plainLyrics
            },
            "LRCLIB" to {
                firstOf(
                    { LrcLib.getLyrics(title, lead, duration, album).getOrNull() },
                    { if (album != null) LrcLib.getLyrics(title, lead, duration, null).getOrNull() else null },
                    { if (asWritten) LrcLib.getLyrics(song.title, everyone, duration, null).getOrNull() else null },
                    { if (credited != null) LrcLib.getLyrics(title, credited, duration, null).getOrNull() else null },
                    // -1, not 0: it means "never mind how long it is", and leaves the name and the
                    // artist to decide. A video's runtime is not the song's, and every match made on
                    // length has just failed because of it.
                    { LrcLib.getLyrics(title, lead, -1, null).getOrNull() },
                )
            },
            "KuGou" to {
                firstOf(
                    { KuGou.getLyrics(title, lead, duration).getOrNull() },
                    { if (asWritten) KuGou.getLyrics(song.title, everyone, duration).getOrNull() else null },
                    { if (credited != null) KuGou.getLyrics(title, credited, duration).getOrNull() else null },
                    // Same again, with the length set aside. Loose enough to need the title check that
                    // guards it — see `echoesTitle`.
                    { KuGou.getLyrics(title, lead, -1).getOrNull() },
                )
            },
            // YouTube Music's own lyrics tab: untimed, but it has songs nobody else does.
            "YouTube Music" to {
                YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.lyricsEndpoint?.let { YouTube.lyrics(it).getOrNull() }
            },
        )

        // Lyrics → Sources: the listener's order, minus anything switched off.
        val order = AppSettings.current.lyricsProviderOrder
        val chosen = providers
            .filter { DesktopPrefs.isLyricsProviderEnabled(it.first) }
            .sortedBy { (name, _) -> order.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it } }

        val candidates = coroutineScope {
            val jobs = chosen.map { (name, fetch) ->
                name to async {
                    val raw = try {
                        withTimeoutOrNull(ProviderTimeoutMs) { fetch() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    raw?.takeIf { it.isNotBlank() }?.let { judge(name, it, duration, title, song.title) }
                }
            }
            // Taken as they arrive, not all at the end. Waiting for every source means every song waits
            // for the slowest of them — and a source that has gone down waits out its whole timeout, so
            // lyrics that were ready in half a second would still appear long after the singing started.
            val found = mutableListOf<Candidate>()
            val pending = jobs.toMutableList()
            val startedAt = System.currentTimeMillis()
            var firstAt = 0L
            while (pending.isNotEmpty()) {
                val now = System.currentTimeMillis()
                // What's in hand decides how long the rest are worth waiting for. Untimed words are a
                // last resort, not an answer — a source that hands those back quickly must not settle
                // the matter while a source with the timings is still on its way.
                val budget = if (found.any { it.synced != null }) {
                    GraceMs - (now - firstAt)
                } else {
                    ProviderTimeoutMs - (now - startedAt)
                }
                if (budget <= 0) break
                val answered = withTimeoutOrNull(budget) {
                    select<Pair<Deferred<Candidate?>, Candidate?>> {
                        pending.forEach { (_, job) -> job.onAwait { result -> job to result } }
                    }
                } ?: break
                val (job, candidate) = answered
                pending.removeAll { it.second === job }
                if (candidate != null) {
                    if (candidate.synced != null && found.none { it.synced != null }) {
                        firstAt = System.currentTimeMillis()
                    }
                    found.add(candidate)
                    // Word by word, in time and spelled out: nothing later can beat that, so nothing
                    // later is waited for. A bleeped copy is worth waiting out the grace to replace.
                    if (candidate.wordLevel && !candidate.censored) break
                }
            }
            pending.forEach { (_, job) -> job.cancel() }
            // Back into the listener's own order of sources, whatever order they answered in.
            found.sortedBy { candidate -> chosen.indexOfFirst { it.first == candidate.source } }
        }

        // Spelled out before bleeped, word-timed before line-timed, and the listener's own order of
        // sources to settle the rest — `candidates` already arrives in that order, and picking the
        // first smallest keeps it.
        // Sources that heard the same song say much the same words. One that agrees with none of the
        // others, when the others agree among themselves, is singing something else.
        val agreeing = candidates.filter { candidate ->
            candidates.any { other -> other !== candidate && overlap(candidate.vocabulary, other.vocabulary) >= 0.5 }
        }.toSet()
        val rank = compareBy<Candidate>(
            { if (agreeing.isNotEmpty() && it !in agreeing) 1 else 0 },
            { if (it.echoesTitle) 0 else 1 },
            { if (it.censored) 1 else 0 },
            { if (it.wordLevel) 0 else 1 },
        )
        val pick = candidates.filter { it.synced != null }.minWithOrNull(rank)
        val result = when {
            pick != null -> Lyrics.Synced(pick.synced!!, pick.source, wordLevel = pick.wordLevel)
            else -> candidates.filter { it.plain != null }.minWithOrNull(rank)
                ?.let { Lyrics.Plain(it.plain!!, it.source) } ?: Lyrics.NotFound
        }
        synchronized(cache) { cache[key] = result }
        result
    }

    private fun judge(source: String, raw: String, durationSeconds: Int, title: String, rawTitle: String = title): Candidate? {
        val parsed = LyricsParser.parse(raw)
        val bleeped = isCensored(raw)
        // Credits out first — a source with nothing for a song will hand over the record's paperwork,
        // and it parses as cleanly as a verse. Taking those lines out also tidies the metadata some
        // files carry at the top; if hardly anything is left, it was paperwork all along.
        val lines = parsed.filterNot { isPaperwork(it.text) }.let { kept ->
            // A file headed with the song's own name — "Smells Like Teen Spirit" at 0:03 — isn't a sung line.
            val head = kept.indexOfFirst { it.text.isNotBlank() }
            if (head >= 0 && isTitleHeading(kept[head].text, title)) kept.filterIndexed { i, _ -> i != head } else kept
        }
        val sung = lines.filter { it.text.isNotBlank() }
        if (sung.size >= MinimumLines) {
            val echo = echoesTitle(sung.joinToString(" ") { it.text }, title)
            val oneOff = isOneOff(rawTitle)
            // KuGou will answer a search it didn't really match — asked directly, or through
            // BetterLyrics, whose line-timed answers are KuGou's passed on. Everything else is asked by
            // title and artist, so only these have to prove they heard the question.
            val loose = source == "KuGou" || (source == "BetterLyrics" && sung.none { it.wordSynced })
            if (loose && !soundsLikeTitle(sung.joinToString(" ") { it.text }, title)) return null
            return if (fitsSong(sung, durationSeconds, oneOff)) Candidate(source, lines, null, bleeped, echo)
            // Right words, wrong timing (a radio edit, a live take): keep them as plain lyrics.
            else Candidate(source, null, sung.map { it.text }, bleeped, echo)
        }
        if (raw.trimStart().startsWith("<")) return null
        val text = raw.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") && !isPaperwork(it) }
        if (text.size < MinimumLines) return null
        val echo = echoesTitle(text.joinToString(" "), title)
        if ((source == "KuGou" || source == "BetterLyrics") && !soundsLikeTitle(text.joinToString(" "), title)) return null
        return Candidate(source, null, text, bleeped, echo)
    }

    /** Timed lyrics that could belong to this recording: they neither run past its end nor stop far short of it. */
    private fun fitsSong(lines: List<LyricLine>, durationSeconds: Int, oneOff: Boolean = false): Boolean {
        if (durationSeconds < 30) return true
        val first = lines.first().timeMs / 1000.0
        val last = lines.last().timeMs / 1000.0
        if (last > durationSeconds + 10) return false
        if (first > durationSeconds * 0.6) return false
        // A performance is its own recording: a medley runs two songs together, a live take stretches
        // and rearranges. One song's studio timings laid over that drift apart within a verse, so they
        // have to account for most of the runtime before they're trusted to scroll against it.
        val floor = if (oneOff) 0.75 else 0.4
        if (durationSeconds > 90 && last < durationSeconds * floor) return false
        return true
    }

    /**
     * A recording that isn't the studio single, as a title says so: the tag after the name — "(Live)",
     * "- Acoustic", "| Live at Wembley" — or two songs run together with a slash. Read as a tag and not
     * as a word anywhere in the title, so "Live Forever" stays an ordinary song.
     */
    private val recordingTag =
        Regex("""(?i)[(\[|\-–—]\s*[^)\]|]*\b(live|medley|acoustic|unplugged|remix|mashup|session|tour|concert|sped\s*up|slowed)\b""")

    /** "Espresso / Bed Chem" — a slash with a space beside it, so "AC/DC" and "w/" are left alone. */
    private val medleyJoin = Regex("""\s/|/\s""")

    private fun isOneOff(title: String): Boolean =
        recordingTag.containsMatchIn(title) || medleyJoin.containsMatchIn(title)
}

object LyricsParser {
    private val lineTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    fun parse(raw: String): List<LyricLine> {
        val trimmed = raw.trim()
        val isTtml = trimmed.startsWith("<") && (trimmed.contains("<tt", ignoreCase = true) || trimmed.contains("ns/ttml"))
        val lines = when {
            isTtml -> parseTtml(trimmed)
            // Word-stamped LRC goes through TTML, where a stamped run with no space after it is
            // glued to the next as one word ("<31.83>e<31.99>nough") instead of shown as two.
            wordTag.containsMatchIn(trimmed) -> enhancedLrcToTtml(withOffset(trimmed))?.let(::parseTtml)
                ?.takeIf { it.isNotEmpty() } ?: parseLrc(trimmed)
            else -> parseLrc(trimmed)
        }
        return withWordTimings(lines.sortedBy { it.timeMs })
    }

    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    /** `[offset:+500]` means every line shows 500 ms earlier; ignored, the whole file ran late. */
    private fun offsetOf(raw: String): Long =
        raw.lineSequence().firstNotNullOfOrNull { offsetTag.find(it.trim())?.groupValues?.get(1)?.toLongOrNull() } ?: 0L

    /** The file with its offset tag applied to every stamp, for the converters that don't read it. */
    private fun withOffset(raw: String): String {
        val offset = offsetOf(raw)
        if (offset == 0L) return raw
        val stamp = Regex("""([\[<])(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?([]>])""")
        return stamp.replace(raw) { m ->
            val (open, min, sec, frac, close) = m.destructured
            val ms = (stampMs(min, sec, frac) - offset).coerceAtLeast(0L)
            String.format(Locale.ROOT, "%s%02d:%02d.%03d%s", open, ms / 60_000, (ms / 1000) % 60, ms % 1000, close)
        }
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long, leadMs: Long = 250): Int {
        val target = positionMs + leadMs
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= target) low = mid + 1 else high = mid - 1
        }
        return high
    }

    private fun parseTtml(ttml: String): List<LyricLine> = runCatching {
        TTMLParser.parseTTML(ttml).map { line ->
            val words = line.words.filter { it.text.isNotEmpty() }.map {
                LyricWord(it.text, (it.startTime * 1000).toLong(), (it.endTime * 1000).toLong(), it.isBackground)
            }
            LyricLine((line.startTime * 1000).toLong(), line.text, words, wordSynced = words.isNotEmpty())
        }
    }.getOrDefault(emptyList())

    private fun stampMs(min: String, sec: String, frac: String): Long {
        val fraction = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fraction
    }

    /** LRC, including enhanced LRC whose `<mm:ss.xx>` tags time each word. */
    private fun parseLrc(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (rawLine in withOffset(raw).lineSequence()) {
            val line = rawLine.trim()
            var cursor = 0
            val stamps = ArrayList<Long>()
            while (true) {
                val match = lineTag.matchAt(line, cursor) ?: break
                val (min, sec, frac) = match.destructured
                stamps += stampMs(min, sec, frac)
                cursor = match.range.last + 1
            }
            if (stamps.isEmpty()) continue
            val rest = line.substring(cursor)

            val tags = wordTag.findAll(rest).toList()
            val words = if (tags.isEmpty()) emptyList() else tags.mapIndexedNotNull { i, tag ->
                val (min, sec, frac) = tag.destructured
                val textEnd = tags.getOrNull(i + 1)?.range?.first ?: rest.length
                val text = rest.substring(tag.range.last + 1, textEnd)
                if (text.isBlank() && text.isNotEmpty().not()) return@mapIndexedNotNull null
                val next = tags.getOrNull(i + 1)?.destructured?.let { (m, s, f) -> stampMs(m, s, f) }
                LyricWord(text, stampMs(min, sec, frac), next ?: -1L)
            }.filter { it.text.isNotEmpty() }
            val text = wordTag.replace(rest, "").replace(Regex("\\s+"), " ").trim()
            stamps.forEach { out += LyricLine(it, text, words, wordSynced = words.isNotEmpty()) }
        }
        return out
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x3040..0x30FF || c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0xAC00..0xD7AF

    /** Closes open-ended word timings, and spreads line-only lyrics across their words by length. */
    private fun withWordTimings(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { index, line ->
        val nextTime = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 5_000)
        if (line.words.isNotEmpty()) {
            val fixed = line.words.mapIndexed { i, word ->
                if (word.endMs > word.startMs) word
                else word.copy(endMs = line.words.getOrNull(i + 1)?.startMs?.takeIf { it > word.startMs } ?: minOf(nextTime, word.startMs + 1_200))
            }
            return@mapIndexed line.copy(words = fixed)
        }
        if (line.text.isBlank()) return@mapIndexed line

        val cjk = line.text.any(::isCjk)
        val tokens: List<String> = if (cjk) {
            buildList {
                val latin = StringBuilder()
                line.text.forEach { c ->
                    when {
                        isCjk(c) -> {
                            if (latin.isNotEmpty()) add(latin.toString()).also { latin.clear() }
                            add(c.toString())
                        }
                        c.isWhitespace() -> {
                            if (latin.isNotEmpty()) add(latin.toString()).also { latin.clear() }
                            if (isNotEmpty()) this[lastIndex] = last() + c
                        }
                        else -> latin.append(c)
                    }
                }
                if (latin.isNotEmpty()) add(latin.toString())
            }
        } else {
            line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
        if (tokens.isEmpty()) return@mapIndexed line

        // Weighted by syllables, not letters: "through" and "a" aren't sung 7:1.
        val weights = tokens.map { token ->
            val trimmed = token.trim()
            if (trimmed.length == 1 && isCjk(trimmed[0])) 1.0 else syllables(trimmed).toDouble()
        }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        // How long the line is actually sung: about 280 ms a syllable, never into the next line. The
        // old spread filled the whole gap, so before an instrumental break the last word crawled for
        // seconds and every word ran late.
        val sung = (totalWeight * 280 + 200).toLong().coerceIn(600, 7_000)
        val lineDuration = minOf(sung, (nextTime - line.timeMs - 150).coerceAtLeast(400)).coerceIn(400, 10_000)

        var offset = 0.0
        val words = tokens.mapIndexed { i, token ->
            val duration = lineDuration * weights[i] / totalWeight
            val start = line.timeMs + offset.toLong()
            offset += duration
            val text = if (!cjk && i < tokens.lastIndex) "$token " else token
            LyricWord(text, start, line.timeMs + offset.toLong())
        }
        line.copy(words = words)
    }

    private const val Vowels = "aeiouyàáâãäåæèéêëìíîïòóôõöøùúûüýÿœ"

    /** Vowel groups in a word, less a silent final e — a close-enough syllable count for timing. */
    private fun syllables(word: String): Int {
        val letters = word.lowercase().filter { it.isLetter() }
        if (letters.isEmpty()) return 1
        var groups = 0
        var inVowel = false
        letters.forEach { c ->
            val vowel = c in Vowels
            if (vowel && !inVowel) groups++
            inVowel = vowel
        }
        if (groups > 1 && letters.endsWith("e") && !letters.endsWith("le")) groups--
        // Scripts without Latin vowels (Devanagari, Cyrillic…): roughly a syllable per three letters.
        if (groups == 0) groups = (letters.length + 2) / 3
        return groups.coerceIn(1, 8)
    }
}

/**
 * Per-song lyric timing nudges from the player ([ and ] keys, or the Sync chip). Kept for the session
 * and added on top of Settings → Lyrics → Sync offset.
 */
object LyricsSync {
    private val offsets = androidx.compose.runtime.mutableStateMapOf<String, Long>()

    fun offsetFor(songId: String): Long = offsets[songId] ?: 0L

    fun nudge(songId: String, deltaMs: Long) {
        offsets[songId] = (offsetFor(songId) + deltaMs).coerceIn(-5_000, 5_000)
    }

    fun reset(songId: String) {
        offsets.remove(songId)
    }
}
