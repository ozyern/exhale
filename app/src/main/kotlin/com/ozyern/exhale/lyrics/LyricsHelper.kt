/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.ozyern.exhale.utils.GlobalLog
import com.ozyern.exhale.constants.PreferredLyricsProvider
import com.ozyern.exhale.constants.PreferredLyricsProviderKey
import com.ozyern.exhale.constants.ProviderOrderKey
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.extensions.toEnum
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.reportException
import com.ozyern.exhale.utils.NetworkConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.SystemClock
import com.ozyern.exhale.constants.DefaultProviderOrder
import com.ozyern.exhale.constants.LegacyDefaultProviderOrder
import com.ozyern.exhale.constants.PreferWordSyncedLyricsKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        preferredProviderOnly: Boolean = false,
        /** Whether the player's source picker should show this lookup: not for preloads or exports. */
        trackLookup: Boolean = !preferredProviderOnly,
        /** Set on the inner lookup for a music video's song, so it does not go looking again. */
        forVideo: Boolean = false,
        /** Ask the sources even when a copy is already cached: looking for a better-timed one. */
        skipCache: Boolean = false,
    ): String {
        currentLyricsJob?.cancel()

        // A censored copy is not worth keeping: ask the sources again, where an uncensored one wins.
        val cached = if (skipCache) null else cache.get(mediaMetadata.id)?.firstOrNull()
            ?.takeUnless { Uncensor.isCensored(Uncensor.restore(it.lyrics)) }
        if (cached != null) {
            GlobalLog.append(Log.DEBUG, "LyricsHelper", "Found lyrics in cache for ${mediaMetadata.title}")
            return Uncensor.restore(cached.lyrics) ?: cached.lyrics
        }

        GlobalLog.append(Log.DEBUG, "LyricsHelper", "Fetching lyrics for ${mediaMetadata.title} (Artist: ${mediaMetadata.artists.joinToString { it.name }}, Album: ${mediaMetadata.album?.title})")

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) {
            GlobalLog.append(Log.WARN, "LyricsHelper", "Network unavailable, aborting lyrics fetch")
            return LYRICS_NOT_FOUND
        }

        val ordered = orderedProviders()
        // The first source that is switched on: the first in the list may well be off, and a
        // preload asking only it asked nobody.
        val enabled = ordered.filter { it.isEnabled(context) }
        val providers = if (preferredProviderOnly) enabled.take(1) else enabled
        val phrasings = phrasingsOf(mediaMetadata)
        val preferWord = context.dataStore.data.first()[PreferWordSyncedLyricsKey] ?: true

        if (trackLookup) LyricsLookups.begin(mediaMetadata.id, ordered.filter { it.isEnabled(context) }.map { it.name })

        // Every source at once, rather than one after another: asked in turn, a song the first few
        // sources don't have waited out each of their round trips before the one that had it was
        // even asked. The order still decides — see [pickBest].
        val picked = supervisorScope {
            // Which recording this is, settled once, alongside the race rather than ahead of it:
            // the sources that can name a recording wait on it (briefly), the rest don't.
            val recording = async(Dispatchers.IO) { identifyRecording(mediaMetadata, phrasings, ordered) }
            val jobs = providers.map { provider ->
                async(Dispatchers.IO) {
                    if (trackLookup) LyricsLookups.update(mediaMetadata.id, provider.name, LyricsSourceState.FETCHING)
                    // One source failing in a way [fetchFrom] doesn't expect — an Error, or a
                    // cancellation of its own rather than ours — must cost only that source. Left
                    // to escape, it failed the whole race in [pickBest], throwing away answers the
                    // other sources already had.
                    val found = try {
                        fetchFrom(provider, phrasings, mediaMetadata, recording)
                    } catch (e: CancellationException) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e
                        null
                    } catch (e: Throwable) {
                        reportException(e)
                        null
                    }
                    if (trackLookup) LyricsLookups.update(
                        mediaMetadata.id,
                        provider.name,
                        if (found != null) LyricsSourceState.FOUND else LyricsSourceState.NOT_FOUND,
                        found,
                    )
                    found
                }
            }
            try {
                // The deadline is the race's own, not a timeout around it: wrapped in
                // `withTimeoutOrNull`, a race still waiting out its grace for a better-timed source
                // when the 20 s ran out threw away the answer it already had, and the song was saved
                // as having no lyrics — while asking that same source by hand found them.
                pickBest(
                    jobs,
                    preferWord,
                    deadline = SystemClock.elapsedRealtime() + OVERALL_TIMEOUT_MS,
                    title = phrasings.first().first,
                    autoTranscribed = providers.map { it === YouTubeSubtitleLyricsProvider },
                )
            } finally {
                jobs.forEach { it.cancel() }
                recording.cancel()
            }
        }
        val lyrics = picked?.let { (index, text) ->
            if (trackLookup) LyricsLookups.choose(mediaMetadata.id, providers[index].name)
            text
        } ?: LYRICS_NOT_FOUND
        // Populate the in-memory cache on the MAIN fetch path too (previously only
        // getAllLyrics wrote it — under a different key — so the id-keyed lookup above
        // never hit). A prefetch on song start now makes the lyrics-tab open instant.
        // A music video: its title, length and id match no lyric source, and a song found anyway
        // is timed for the record, not the video's intro. So the lyrics come from the song the
        // video is of, moved later by however long the video plays before the song does.
        var result = lyrics
        if (!forVideo && !preferredProviderOnly && (result == LYRICS_NOT_FOUND || looksLikeVideo(mediaMetadata))) {
            runCatching { videoLyrics(mediaMetadata) }.getOrNull()?.let { result = it }
        }
        if (result != LYRICS_NOT_FOUND) {
            cache.put(mediaMetadata.id, listOf(LyricsResult(PREFETCH_PROVIDER_NAME, result)))
        }
        return Uncensor.restore(result) ?: result
    }

    private val videoWords = Regex("""(?i)(official\s+(music\s+)?video|music\s+video|\bm/?v\b|visuali[sz]er|lyric\s+video|performance\s+video|\(video\)|\[video\])""")

    // A video is often titled just like the song ("drop dead"), so the title alone missed most of
    // them and their lyrics ran early by the length of the intro; YouTube's own flag catches the
    // rest. A missing album is not evidence: a plain song in a radio can arrive without one, and
    // its correctly timed lyrics were then replaced by another release's, moved by a measurement.
    private fun looksLikeVideo(meta: MediaMetadata): Boolean =
        videoWords.containsMatchIn(meta.title) ||
            meta.id in com.ozyern.exhale.extensions.KnownMusicVideos ||
            meta.artists.any { it.name.endsWith("VEVO", ignoreCase = true) }

    private suspend fun videoLyrics(meta: MediaMetadata): String? {
        val song = catalogueSong(meta) ?: return null
        if (song.id == meta.id) return null
        val songMeta = meta.copy(
            id = song.id,
            title = song.title,
            artists = song.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
            duration = song.duration ?: meta.duration,
            album = song.album?.let { MediaMetadata.Album(id = it.id, title = it.name) } ?: meta.album,
        )
        val text = getLyrics(songMeta, trackLookup = false, forVideo = true)
        if (text == LYRICS_NOT_FOUND) return null
        // Bounded: the words should never wait long on the alignment. Unaligned lyrics now beat
        // perfectly aligned ones much later.
        // The measurement blocks its thread, so it runs detached and only the wait is timed out.
        val aligning = VideoLyricsAlignment.aligner?.let { align ->
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.async(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { align(meta.id, song.id) }.getOrNull()
            }
        }
        val timely = aligning?.let { withTimeoutOrNull(ALIGN_TIMEOUT_MS) { it.await() } }
        if (timely == null && aligning != null) {
            // Not measured in time: shown unmoved for now, and corrected the moment it is. It used to
            // stop there, so a video's lyrics stayed early by its whole intro for good.
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val late = aligning.await() ?: return@launch
                if (late == 0L) return@launch
                val shifted = LyricsShift.shift(text, late)
                if (!LyricsFit.fits(shifted, meta.duration)) return@launch
                val shown = Uncensor.restore(shifted) ?: shifted
                cache.put(meta.id, listOf(LyricsResult(PREFETCH_PROVIDER_NAME, shifted)))
                GlobalLog.append(Log.DEBUG, "LyricsHelper", "Video ${meta.id} → song ${song.id}, lyrics moved ${late}ms (late)")
                VideoLyricsAlignment.onLateLyrics?.invoke(meta.id, shown)
            }
        }
        val offset = timely ?: 0L
        GlobalLog.append(Log.DEBUG, "LyricsHelper", "Video ${meta.id} → song ${song.id}, lyrics moved ${offset}ms")
        // A measurement that pushes the lyrics out of the video's own length was a wrong one.
        return LyricsShift.shift(text, offset).takeIf { LyricsFit.fits(it, meta.duration) }
    }

    /** The catalogue song a video is of: same title and artist, length within the video's. */
    private suspend fun catalogueSong(meta: MediaMetadata): com.ozyern.exhale.innertube.models.SongItem? {
        val (title, artist) = SongQuery.songAndArtist(meta.title, meta.artists.map { it.name })
        val results = com.ozyern.exhale.innertube.YouTube.search(
            "$title $artist",
            com.ozyern.exhale.innertube.YouTube.SearchFilter.FILTER_SONG,
        ).getOrNull()?.items?.filterIsInstance<com.ozyern.exhale.innertube.models.SongItem>().orEmpty()
        fun norm(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val wantedTitle = norm(title)
        val wantedArtist = norm(artist)
        return results.mapNotNull { song ->
            val name = norm(SongQuery.cleanTitle(song.title))
            val titleScore = when {
                name == wantedTitle -> 50
                name.isNotEmpty() && (name.contains(wantedTitle) || wantedTitle.contains(name)) -> 30
                else -> return@mapNotNull null
            }
            val artists = song.artists.joinToString(" ") { norm(it.name) }
            val artistScore = if (wantedArtist.isNotEmpty() && (artists.contains(wantedArtist) || wantedArtist.contains(artists))) 30 else 0
            // A video runs longer than its song, never much shorter.
            val lengthOk = song.duration == null || meta.duration <= 0 ||
                (meta.duration - song.duration!!) in -8..150
            if (!lengthOk) return@mapNotNull null
            song to titleScore + artistScore
        }.filter { it.second >= 60 }.maxByOrNull { it.second }?.first
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        songAlbum: String?,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = orderedProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, songAlbum, duration) lyricsCallback@{ lyrics ->
                            if (!isMeaningfulLyrics(lyrics)) return@lyricsCallback
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    /**
     * Lyrics from one source, for the source picker: the answer the automatic lookup already had
     * if it had one, otherwise this source asked now. Marks the source as the song's current one
     * when it answers.
     */
    suspend fun lyricsFrom(sourceName: String, mediaMetadata: MediaMetadata): String? {
        LyricsLookups.found(mediaMetadata.id, sourceName)?.let {
            LyricsLookups.choose(mediaMetadata.id, sourceName)
            return it
        }
        val provider = orderedProviders().firstOrNull { it.name.equals(sourceName, ignoreCase = true) } ?: return null
        if (LyricsLookups.state.value?.mediaId != mediaMetadata.id) {
            LyricsLookups.begin(mediaMetadata.id, orderedProviders().filter { it.isEnabled(context) }.map { it.name })
        }
        LyricsLookups.update(mediaMetadata.id, sourceName, LyricsSourceState.FETCHING)
        val found = supervisorScope {
            val phrasings = phrasingsOf(mediaMetadata)
            val recording = async(Dispatchers.IO) { identifyRecording(mediaMetadata, phrasings, orderedProviders()) }
            try {
                withTimeoutOrNull(OVERALL_TIMEOUT_MS) { fetchFrom(provider, phrasings, mediaMetadata, recording) }
            } finally {
                recording.cancel()
            }
        }
        LyricsLookups.update(
            mediaMetadata.id,
            sourceName,
            if (found != null) LyricsSourceState.FOUND else LyricsSourceState.NOT_FOUND,
            found,
        )
        if (found != null) {
            LyricsLookups.choose(mediaMetadata.id, sourceName)
            cache.put(mediaMetadata.id, listOf(LyricsResult(sourceName, found)))
        }
        return found
    }

    /** The way a lyrics database would title the song, then as YouTube writes it. */
    private fun phrasingsOf(mediaMetadata: MediaMetadata): List<Pair<String, String>> {
        val rawTitle = mediaMetadata.title
        val rawArtist = mediaMetadata.artists.joinToString { it.name }
        val (title, artist) = SongQuery.songAndArtist(rawTitle, mediaMetadata.artists.map { it.name })
        return listOf(
            title to artist.ifBlank { rawArtist },
            rawTitle to rawArtist,
        ).distinct()
    }

    /**
     * Which recording this is, from the one source that can say — by ISRC, which names one
     * recording and so can't match a single's lyrics to its album cut. Skipped when that source is
     * switched off: a source somebody turned off is not contacted, not even to help the others.
     * Remembered per video, and never waited on for long — see [IDENTIFY_TIMEOUT_MS].
     */
    private suspend fun identifyRecording(
        mediaMetadata: MediaMetadata,
        phrasings: List<Pair<String, String>>,
        ordered: List<LyricsProvider>,
    ): Recording? {
        recordings.get(mediaMetadata.id)?.let { return it }
        if (ordered.none { it === BinimumLyricsProvider && it.isEnabled(context) }) return null
        val (title, artist) = phrasings.first()
        val found = withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            runCatching {
                BinimumLyricsProvider.identify(title, artist, mediaMetadata.album?.title, mediaMetadata.duration)
            }.getOrNull()
        } ?: return null
        recordings.put(mediaMetadata.id, found)
        return found
    }

    private suspend fun fetchFrom(
        provider: LyricsProvider,
        phrasings: List<Pair<String, String>>,
        mediaMetadata: MediaMetadata,
        recordingJob: Deferred<Recording?>? = null,
    ): String? {
        // Only the sources that can use it wait for the recording; everyone else starts at once.
        val recording = if (provider === BinimumLyricsProvider || provider === LyricsPlusProvider ||
            provider === LrcRedLyricsProvider
        ) {
            recordingJob?.let { job -> withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) { runCatching { job.await() }.getOrNull() } }
        } else {
            null
        }
        for ((title, artist) in phrasings) {
            try {
                val result = provider.getLyrics(
                    mediaMetadata.id,
                    title,
                    artist,
                    mediaMetadata.album?.title,
                    mediaMetadata.duration,
                    recording,
                )
                // Word-stamped LRC is stored as the TTML the word-by-word view reads; left as
                // LRC, it won the race as word-timed and then showed whole lines, with the
                // stamps printed in the text.
                val lyrics = result.getOrNull()?.let { text ->
                    val tidy = if (text.trimStart().startsWith("[")) LyricsJudge.withoutPaperwork(text, phrasings.first().first) else text
                    if (tidy.trimStart().startsWith("[") && ENHANCED_LRC_REGEX.containsMatchIn(tidy)) {
                        enhancedLrcToTtml(tidy) ?: tidy
                    } else {
                        tidy
                    }
                }
                if (lyrics != null && isMeaningfulLyrics(lyrics)) {
                    // Timed for another cut of the song: not shown, so a source that has this one wins.
                    if (!LyricsFit.fits(lyrics, mediaMetadata.duration)) {
                        GlobalLog.append(Log.DEBUG, "LyricsHelper", "${provider.name}: timing does not fit ${mediaMetadata.title} (${mediaMetadata.duration}s); passed over")
                        continue
                    }
                    if (!provesItself(provider, lyrics, phrasings.first().first)) {
                        GlobalLog.append(Log.DEBUG, "LyricsHelper", "${provider.name}: answer is not ${mediaMetadata.title}; passed over")
                        continue
                    }
                    return lyrics
                }
                // A source that simply doesn't have the song is not an error worth reporting.
                result.exceptionOrNull()?.takeIf { it !is NoSuchElementException }?.let(::reportException)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            }
        }
        return null
    }

    /**
     * Credits alone are never lyrics. KuGou, BetterLyrics' line-timed answers (which are KuGou's passed
     * on) and YouTube's transcript answer searches they did not really match, so those also have to
     * sound like the song asked for.
     */
    private fun provesItself(provider: LyricsProvider, lyrics: String, title: String): Boolean {
        val lines = runCatching { LyricsWords.linesOf(lyrics) }.getOrNull() ?: return true
        if (!LyricsJudge.isSong(lines)) return false
        val loose = provider === KuGouLyricsProvider || provider === YouTubeSubtitleLyricsProvider ||
            (provider === BetterLyricsProvider && syncLevel(lyrics) != 0)
        return !loose || LyricsJudge.soundsLikeTitle(lines.joinToString(" "), title)
    }

    /**
     * The answer to show: the highest-placed source with *synced* lyrics
     * wins, word- or line-timed — priority is priority — and plain text only when no source has
     * timing at all. With [preferWord], a line-timed answer is kept only until a word-timed one
     * turns up further down.
     *
     * Returns as soon as nothing still running could do better. Once there is an answer, sources
     * placed above it get [BETTER_ANSWER_GRACE_MS] to arrive and no more, so one slow host never
     * holds back lyrics that are already here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun pickBest(
        jobs: List<Deferred<String?>>,
        preferWord: Boolean,
        /** When to settle for the best answer in hand, whatever is still running. */
        deadline: Long = Long.MAX_VALUE,
        title: String,
        /** Per source: its timing and words are a machine's guess, so it ranks behind even plain text. */
        autoTranscribed: List<Boolean>,
    ): Pair<Int, String>? {
        val n = jobs.size
        if (n == 0) return null
        val results = arrayOfNulls<String>(n)
        val done = BooleanArray(n)
        var graceDeadline = Long.MAX_VALUE
        var firstAnswerAt = Long.MAX_VALUE
        val spoken = HashMap<Int, String>()
        val vocabularies = HashMap<Int, Set<String>>()
        var agreeing = emptySet<Int>()
        fun rank(i: Int): Int {
            val level = if (autoTranscribed[i]) 3 else syncLevel(results[i]!!)
            // In order of weight: an answer nobody backs up, one that never says the song's name, one
            // that masked its words. Each ranks behind every answer free of it, however it is timed.
            val contradicted = if (agreeing.isNotEmpty() && i !in agreeing) 12 * n * n else 0
            val nameless = if (!LyricsJudge.echoesTitle(spoken.getValue(i), title)) 6 * n * n else 0
            val censored = if (Uncensor.isCensored(results[i])) 3 * n * n else 0
            return contradicted + nameless + censored + when {
                preferWord -> level * n + i
                level <= 1 -> i
                else -> (level - 1) * n + i
            }
        }
        while (true) {
            (0 until n).filter { results[it] != null && it !in spoken }.forEach { i ->
                val text = runCatching { LyricsWords.linesOf(results[i]!!).joinToString(" ") }.getOrDefault(results[i]!!)
                spoken[i] = text
                vocabularies[i] = LyricsJudge.vocabulary(text)
                agreeing = LyricsJudge.agreeing(vocabularies)
            }
            val best = (0 until n).filter { results[it] != null }.minByOrNull(::rank)
            // The best any unfinished source could still do is word-timed at its own place.
            val couldBeat = (0 until n).any { j -> !done[j] && (best == null || j < rank(best)) }
            if (best != null && firstAnswerAt == Long.MAX_VALUE) firstAnswerAt = SystemClock.elapsedRealtime()
            // Nothing below can outrank it, but nobody has checked it either. Answers can only contradict
            // one another once there are two, and the top source answering first used to end the race
            // with whatever it said, in whatever language. It gets a moment's second opinion.
            val secondOpinionUntil = if (
                !couldBeat && best != null && results.count { it != null } < 2 && (0 until n).any { !done[it] }
            ) {
                firstAnswerAt + SECOND_OPINION_GRACE_MS
            } else {
                Long.MIN_VALUE
            }
            if (!couldBeat && secondOpinionUntil <= SystemClock.elapsedRealtime()) return best?.let { it to results[it]!! }
            // The Apple-catalogue sources wait on the recording being identified first, so they
            // are routinely the last to answer; a line-timed database back in a second used to
            // win on the clock alone, and a song every one of them has word-timed played
            // line by line. Until something word-timed is in, they get longer.
            if (best != null) {
                val grace = if (preferWord && syncLevel(results[best]!!) > 0) WORD_SYNC_GRACE_MS else BETTER_ANSWER_GRACE_MS
                graceDeadline = if (couldBeat) firstAnswerAt + grace else secondOpinionUntil
            }
            val until = minOf(graceDeadline, deadline)
            val waitMs = if (until == Long.MAX_VALUE) null else until - SystemClock.elapsedRealtime()
            if (waitMs != null && waitMs <= 0L) return best?.let { it to results[it]!! }
            val finished = select<Int> {
                for (i in 0 until n) {
                    if (!done[i]) {
                        jobs[i].onAwait { value ->
                            results[i] = value
                            i
                        }
                    }
                }
                if (waitMs != null) onTimeout(waitMs) { -1 }
            }
            if (finished == -1) return best?.let { it to results[it]!! }
            done[finished] = true
        }
    }

    /** Whether these lyrics carry a time for each word, not only for each line. */
    fun isWordSynced(lyrics: String): Boolean = syncLevel(lyrics) == 0

    /** 0 for word-timed, 1 for line-timed, 2 for plain text. */
    private fun syncLevel(lyrics: String): Int {
        val trimmed = lyrics.trimStart()
        return when {
            LyricsUtils.isTtml(trimmed) -> if (WORD_SPAN_REGEX.containsMatchIn(trimmed)) 0 else 1
            trimmed.startsWith("[") -> if (ENHANCED_LRC_REGEX.containsMatchIn(trimmed)) 0 else 1
            else -> 2
        }
    }

    private fun PreferredLyricsProvider.toLyricsProvider(): LyricsProvider = when (this) {
        PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
        PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
        PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
        PreferredLyricsProvider.SIMPMUSIC -> SimpMusicLyricsProvider
        PreferredLyricsProvider.LYRICS_PLUS -> LyricsPlusProvider
        PreferredLyricsProvider.BINIMUM -> BinimumLyricsProvider
        PreferredLyricsProvider.UNISON -> UnisonLyricsProvider
        PreferredLyricsProvider.MEGALOBIZ -> MegalobizLyricsProvider
        PreferredLyricsProvider.GENIUS -> GeniusLyricsProvider
        PreferredLyricsProvider.PAXSENIX -> PaxSenixLyricsProvider
        PreferredLyricsProvider.MUSIXMATCH -> MusixmatchLyricsProvider
        PreferredLyricsProvider.LRC_RED -> LrcRedLyricsProvider
    }

    private suspend fun orderedProviders(): List<LyricsProvider> {
        val prefs = context.dataStore.data.first()
        val saved = prefs[ProviderOrderKey]
            ?.split(",")
            ?.mapNotNull { name -> runCatching { PreferredLyricsProvider.valueOf(name.trim()) }.getOrNull() }
            .orEmpty()
        val order = if (saved.isNotEmpty() && saved != LegacyDefaultProviderOrder) {
            // Sources added since the order was saved go after the ones the listener placed.
            com.ozyern.exhale.constants.withNewSources(saved)
        } else {
            // LRCLIB is what the settings page showed as the default before, so a stored LRCLIB is
            // far more likely that default kept than a choice — and it put whole-line lyrics in
            // front of the word-timed Apple sources that lead the list. It goes back in its place.
            val preferred = prefs[PreferredLyricsProviderKey]
                ?.let { runCatching { PreferredLyricsProvider.valueOf(it) }.getOrNull() }
                ?.takeUnless { it == PreferredLyricsProvider.LRCLIB }
            if (preferred != null) listOf(preferred) + DefaultProviderOrder.filterNot { it == preferred }
            else DefaultProviderOrder
        }
        // YouTube's own lyrics last, in every order: they used to be left out entirely as soon
        // as an order had been saved.
        return order.distinct().map { it.toLyricsProvider() } +
            listOf(YouTubeSubtitleLyricsProvider, YouTubeLyricsProvider)
    }

    private fun isMeaningfulLyrics(lyrics: String): Boolean {
        val normalized =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        if (normalized.isEmpty()) return false
        if (normalized == LYRICS_NOT_FOUND) return false

        val remaining =
            TIMESTAMP_REGEX
                .replace(normalized, "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        return remaining.any { !it.isWhitespace() && it != '\u00A0' }
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        // Aggressive in-memory cache: lyrics strings are tiny (a few KB) — keep the whole
        // recent listening session hot so re-opening the lyrics tab never re-fetches.
        private const val MAX_CACHE_SIZE = 50
        private const val PREFETCH_PROVIDER_NAME = "Prefetch"
        private val TIMESTAMP_REGEX = Regex("""\[[0-9]{1,2}:[0-9]{2}(?:\.[0-9]{1,3})?]""")
        private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
        private val WORD_SPAN_REGEX = Regex("""<span[^>]*\bbegin=""", RegexOption.IGNORE_CASE)
        private val ENHANCED_LRC_REGEX = Regex("""<\d{1,2}:\d{1,2}(?:\.\d{1,3})?>""")
        private const val BETTER_ANSWER_GRACE_MS = 3_000L
        private const val WORD_SYNC_GRACE_MS = 8_000L
        private const val SECOND_OPINION_GRACE_MS = 1_500L
        private const val IDENTIFY_TIMEOUT_MS = 2_500L

        /** Video id to recording, for this session: a shortcut, never a store. */
        private val recordings = LruCache<String, Recording>(100)
        private const val OVERALL_TIMEOUT_MS = 20_000L
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

private const val ALIGN_TIMEOUT_MS = 6_000L
