/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The music video for a song that is not one itself — the Android app's matching, brought over.
 *
 * Most songs are album tracks (YouTube's "ATV"): a still cover, no picture of their own. The video
 * the artist made is a separate upload, so it has to be found: the same title once decorations are
 * stripped, an artist in common, a running time close enough to be held in step. Among matches the
 * artist's own upload wins; a fan's lyric video is taken only when there is nothing better, and sped
 * up, live, cover and karaoke versions are never taken — no video beats the wrong one.
 */
object MusicVideoMatch {
    /** songId → the video to show, or "" when we looked and there is none. */
    private val found = ConcurrentHashMap<String, String>()

    private const val MAX_DURATION_DRIFT_S = 45

    /** The song's own id when it is a video already, the music video's when one is found, or null. */
    suspend fun videoIdFor(song: SongItem): String? {
        found[song.id]?.let { return it.ifEmpty { null } }
        val answer = withContext(Dispatchers.IO) {
            if (StreamResolver.hasVideo(song.id)) song.id else runCatching { search(song) }.getOrNull()
        }
        if (found.size > 400) found.clear()
        found[song.id] = answer.orEmpty()
        return answer
    }

    private suspend fun search(song: SongItem): String? {
        val wanted = normalize(song.title)
        if (wanted.isBlank()) return null
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        val results = YouTube.search("${song.title} $artist", YouTube.SearchFilter.FILTER_VIDEO)
            .getOrNull()?.items.orEmpty()
            .filterIsInstance<SongItem>()
        val artistNames = song.artists.map { normalize(it.name) }.filter { it.isNotBlank() }
        val songTitle = song.title.lowercase()
        val songSeconds = song.duration ?: 0
        return results.take(10).mapNotNull { video ->
            if (video.id == song.id) return@mapNotNull null
            val raw = video.title.lowercase()
            if (EDITS.any { it.containsMatchIn(raw) } && EDITS.none { it.containsMatchIn(songTitle) }) return@mapNotNull null
            val title = normalize(video.title)
            if (!title.contains(wanted)) return@mapNotNull null
            val channels = video.artists.map { normalize(it.name).replace(" ", "") }
            // The artist's own channel (or their VEVO), not an uploader who put the name in the title.
            val ownChannel = artistNames.any { name ->
                val compact = name.replace(" ", "")
                channels.any { it == compact || it == "${compact}vevo" || it == "${compact}official" }
            }
            val named = artistNames.any { name -> channels.any { it.contains(name.replace(" ", "")) } || title.contains(name) }
            if (!ownChannel && !named) return@mapNotNull null
            val drift = if (video.duration == null || songSeconds <= 0) 0 else abs(video.duration!! - songSeconds)
            if (drift > MAX_DURATION_DRIFT_S) return@mapNotNull null
            var score = 0
            if (ownChannel) score += 100
            if ("official" in raw && ("video" in raw || "mv" in raw)) score += 40
            if ("lyric" in raw || "visualizer" in raw || "audio" in raw) score -= 30
            score -= drift / 3
            video.id to score
        }.maxByOrNull { it.second }?.first
    }

    /** Versions of a song that are not its video: they run at another speed, or are someone else. */
    private val EDITS = listOf(
        "sped up", "speed up", "slowed", "reverb", "nightcore", "8d", "karaoke", "instrumental",
        "cover", "reaction", "1 hour", "10 hours", "loop", "live at", "live from", "(live", "[live",
        "remix", "mashup", "tutorial", "dance practice", "fanmade", "fan made", "zumba", "workout",
        "choreography", "dance cover", "dance fitness", "piano", "guitar",
    ).map { Regex("""(^|[^\p{L}])${Regex.escape(it)}($|[^\p{L}])""") }

    /** Lowercase, no accents, and none of the "(Official Video)" or "[Remastered]" decorations. */
    private fun normalize(text: String): String {
        val noBrackets = text.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        val noFeat = noBrackets.replace(Regex("""\b(feat|ft|featuring)\.?\b.*""", RegexOption.IGNORE_CASE), " ")
        val plain = Normalizer.normalize(noFeat, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        return plain.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
    }
}
