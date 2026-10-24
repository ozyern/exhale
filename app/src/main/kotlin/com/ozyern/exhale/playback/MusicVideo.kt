/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.utils.YTPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The music video for whatever is playing, when YouTube has one.
 *
 * A plain "switch to video" swaps the audio for the video's soundtrack and stops there — you
 * still look at a cover. This finds the video and hands back a picture-only stream, so the player
 * can show it over the cover while the song keeps playing from its own, better, audio stream.
 *
 * Matching is deliberately strict: the same song title once the decorations are stripped, an
 * artist in common, and a running time close enough that the video and the song can be held in
 * step. Among those, the artist's own upload wins; a fan's lyric video is taken only when there is
 * nothing better, and sped-up, live, cover and karaoke versions are never taken — no video beats
 * the wrong one.
 */
object MusicVideo {
    data class Found(val videoId: String, val stream: YTPlayerUtils.VideoStream, val offsetMs: Long)

    /** songId → video id, or "" when we looked and there is none. */
    private val videoIds = ConcurrentHashMap<String, String>()
    private val streams = ConcurrentHashMap<String, Pair<YTPlayerUtils.VideoStream, Long>>()

    private const val STREAM_TTL_MS = 4 * 60 * 60 * 1000L
    private const val MAX_DURATION_DRIFT_S = 45

    /**
     * A stream that stopped answering. YouTube's URLs go stale partway through a long video or after
     * enough seeking, and the cure is a new one — so it is dropped, and the next [find] asks again.
     */
    fun forgetStream(videoId: String) {
        streams.remove(videoId)
    }

    /** Whether we already know there is no video, so the switch can be hidden without a request. */
    fun knownMissing(songId: String): Boolean = videoIds[songId] == ""

    /** Whether we already know there is one. */
    fun knownPresent(songId: String): Boolean = !videoIds[songId].isNullOrEmpty()

    /**
     * Whether the song has a video, found out without fetching the video's stream: one search, its
     * answer kept, so the Video switch is offered only for songs that have something to switch to.
     */
    suspend fun has(song: MediaMetadata): Boolean = withContext(Dispatchers.IO) {
        videoIds[song.id]?.let { return@withContext it.isNotEmpty() }
        val found = runCatching { search(song) }.getOrElse { return@withContext false }
        videoIds[song.id] = found.orEmpty()
        found != null
    }

    suspend fun find(song: MediaMetadata): Found? = withContext(Dispatchers.IO) {
        val videoId = videoIds[song.id]?.takeIf { it.isNotEmpty() }
            ?: if (videoIds[song.id] == "") return@withContext null else search(song)
        videoIds[song.id] = videoId.orEmpty()
        videoId ?: return@withContext null

        val cached = streams[videoId]?.takeIf { System.currentTimeMillis() - it.second < STREAM_TTL_MS }?.first
        val stream = cached ?: YTPlayerUtils.videoStreamForDisplay(videoId).getOrNull()?.also {
            streams[videoId] = it to System.currentTimeMillis()
        } ?: return@withContext null
        Found(videoId, stream, offsetMs = 0L)
    }

    private suspend fun search(song: MediaMetadata): String? {
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val wanted = normalize(song.title)
        if (wanted.isBlank()) return null
        val results = YouTube.search("${song.title} $artist", YouTube.SearchFilter.FILTER_VIDEO)
            .getOrNull()?.items.orEmpty()
            .filterIsInstance<SongItem>()
        val artistNames = song.artists.map { normalize(it.name) }.filter { it.isNotBlank() }
        // Every match is scored rather than the first one taken: search put fan-made lyric videos
        // above the artist's own video often enough that the player showed someone's slideshow
        // over a song that has a real video.
        val best = results.take(10).mapNotNull { video ->
            if (video.id == song.id) return@mapNotNull null
            val raw = video.title.lowercase()
            if (EDITS.any { it.containsMatchIn(raw) } && EDITS.none { it.containsMatchIn(song.title.lowercase()) }) return@mapNotNull null
            val title = normalize(video.title)
            if (!hasWords(title, wanted)) return@mapNotNull null
            val channels = video.artists.map { normalize(it.name).replace(" ", "") }
            // The artist's own channel (or their VEVO), not an uploader who put the name in the title.
            val ownChannel = artistNames.any { name ->
                val compact = name.replace(" ", "")
                channels.any { it == compact || it == "${compact}vevo" || it == "${compact}official" || it == "${compact}topic" }
            }
            // "Artist - Title": whoever the title names on the other side of the dash must be one
            // of this song's artists. Two singers' songs of the same name (Good Luck, Babe! by
            // Chappell Roan, and a version by someone else) differ only there, and a search for
            // one returns the other's official video near the top.
            val sides = video.title.split(TITLE_SEPARATOR, limit = 2).map(::normalize)
            if (sides.size == 2) {
                val other = if (hasWords(sides[0], wanted)) sides[1] else sides[0]
                val otherIsOurs = other.isBlank() || hasWords(other, wanted) ||
                    artistNames.any { hasWords(other, it) || hasWords(it, other) }
                if (!otherIsOurs) return@mapNotNull null
            }
            // Named as whole words, not as a substring: a short artist name was found inside other
            // words of a stranger's title.
            val named = artistNames.any { name -> channels.any { it.startsWith(name.replace(" ", "")) } || hasWords(title, name) }
            if (!ownChannel && !named) return@mapNotNull null
            // Off the artist's own channel, only a video that presents itself as the video: a
            // label's or a VEVO's upload says so in its title. A fan's re-upload naming the artist
            // ("bass boosted", a lyric slideshow, a visualiser) was winning when the official one
            // ranked lower in search.
            if (!ownChannel && OFFICIAL_MARKERS.none { it in raw }) return@mapNotNull null
            val drift = if (video.duration == null || song.duration <= 0) 0 else abs(video.duration!! - song.duration)
            if (drift > MAX_DURATION_DRIFT_S) return@mapNotNull null
            var score = 0
            if (ownChannel) score += 100
            // The video is titled exactly the song (once its artist and decorations are off).
            if (sides.any { it == wanted } || title == wanted) score += 30
            if ("official" in raw && ("video" in raw || "mv" in raw)) score += 40
            if ("lyric" in raw || "visualizer" in raw || "audio" in raw) score -= 30
            score -= drift / 3
            video.id to score
        }.maxByOrNull { it.second }
        return best?.first ?: song.id.takeIf { results.any { it.id == song.id } }
    }

    /** Versions of a song that are not its video: they run at another speed, or are someone else. */
    private val EDITS = listOf(
        "sped up", "speed up", "slowed", "reverb", "nightcore", "8d", "karaoke", "instrumental",
        "cover", "reaction", "1 hour", "10 hours", "loop", "live at", "live from", "(live", "[live",
        "remix", "mashup", "tutorial", "dance practice", "fanmade", "fan made", "zumba", "workout",
        "choreography", "dance cover", "dance fitness", "piano", "guitar",
        "bass boosted", "boosted", "bass", "extended", "edit", "audio", "visualizer", "visualiser",
    ).map { Regex("""(^|[^\p{L}])${Regex.escape(it)}($|[^\p{L}])""") }

    /** What a label's upload of the actual video calls itself. */
    private val OFFICIAL_MARKERS = listOf("official", "music video", "full video", "video song", "(mv)", "[mv]", " m/v")

    /** "Artist - Title", with any of the dashes and bars uploaders use between the two. */
    private val TITLE_SEPARATOR = Regex("""\s+[-–—|]\s+""")

    /** Whether [needle] appears in [haystack] as whole words. Both already [normalize]d. */
    private fun hasWords(haystack: String, needle: String): Boolean =
        needle.isNotBlank() && " $haystack ".contains(" $needle ")

    /** Lowercase, no accents, and none of the "(Official Video)" or "[Remastered]" decorations. */
    private fun normalize(text: String): String {
        val noBrackets = text.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        val noFeat = noBrackets.replace(Regex("""\b(feat|ft|featuring)\.?\b.*""", RegexOption.IGNORE_CASE), " ")
        val plain = Normalizer.normalize(noFeat, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        return plain.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
    }
}
