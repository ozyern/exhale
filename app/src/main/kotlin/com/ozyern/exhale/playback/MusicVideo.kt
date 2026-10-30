/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.isMusicVideo
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

    /** What Auto means: 720p, the tallest picture every phone decodes in hardware next to the song. */
    const val AUTO_HEIGHT = 720

    /** The qualities the player offers, tallest first; 0 is Auto. */
    val QUALITIES = listOf(0, 1080, 720, 480, 360)

    /**
     * The tallest picture to fetch (0 = Auto), as chosen in the full-screen player. Kept here rather
     * than passed down, so every lookup (inline, full screen, the dock) asks for the same stream.
     */
    @Volatile
    var preferredHeight: Int = 0

    /** Auto on mobile data: 480p, about 0.8 Mbps, which a phone network holds where 720p's 2 doesn't. */
    private const val AUTO_METERED_HEIGHT = 480

    /** The lowest Auto steps down to. */
    private const val AUTO_FLOOR_HEIGHT = 360

    /**
     * What Auto is fetching now: 720p on Wi-Fi, 480p on mobile data ([refreshNetwork]), and a step
     * lower each time the picture keeps stalling ([stepDownAuto]). A quality picked by hand is
     * never changed.
     */
    @Volatile
    private var autoHeight: Int = AUTO_HEIGHT

    /** Whether the last network seen was metered, so stepping down survives until it changes. */
    @Volatile
    private var lastMetered: Boolean? = null

    private val maxHeight get() = preferredHeight.takeIf { it > 0 } ?: autoHeight

    /** Sets Auto for the network in use; called before each lookup. A change of network starts it afresh. */
    fun refreshNetwork(context: android.content.Context) {
        val connectivity = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val metered = connectivity?.isActiveNetworkMetered ?: true
        if (metered == lastMetered) return
        lastMetered = metered
        autoHeight = if (metered) AUTO_METERED_HEIGHT else AUTO_HEIGHT
    }

    /**
     * The picture keeps stalling on Auto: one quality lower for the rest of this network. True when
     * it went lower, so the caller fetches the new stream; false on a hand-picked quality or at the floor.
     */
    fun stepDownAuto(): Boolean {
        if (preferredHeight > 0) return false
        val next = QUALITIES.filter { it in AUTO_FLOOR_HEIGHT until autoHeight }.maxOrNull() ?: return false
        autoHeight = next
        return true
    }

    /** (videoId|songId) → how far into the video the song starts, once measured. */
    private val offsets = ConcurrentHashMap<String, Long>()

    /**
     * The video lined up with the song: how far into the video the song's own audio begins.
     *
     * A music video almost never starts where the song does — a cold open, a spoken intro, a
     * longer instrumental lead-in — so held to the song's clock unmodified it ran seconds early or
     * late for the whole song, and the picture's own small drift correction could never close a
     * gap that size. Measured once per pair from the two soundtracks (the same alignment the
     * video's lyrics use) and kept; until it lands, or when it can't be measured, the offset is 0.
     */
    suspend fun aligned(found: Found, songId: String): Found {
        if (found.videoId == songId) return found
        val key = "${found.videoId}|$songId"
        offsets[key]?.let { return found.copy(offsetMs = it) }
        val measured = runCatching {
            com.ozyern.exhale.lyrics.VideoLyricsAlignment.aligner?.invoke(found.videoId, songId)
        }.getOrNull() ?: return found
        offsets[key] = measured
        return found.copy(offsetMs = measured)
    }

    /** The measured offset, if this pair has been aligned already; the video can start in step. */
    fun knownOffset(videoId: String, songId: String): Long = offsets["$videoId|$songId"] ?: 0L

    /**
     * A stream that stopped answering. YouTube's URLs go stale partway through a long video or after
     * enough seeking, and the cure is a new one — so it is dropped, and the next [find] asks again.
     */
    fun forgetStream(videoId: String) {
        streams.keys.removeAll { it.startsWith("$videoId@") }
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

        // Kept per quality, so switching back to one already fetched is instant.
        val height = maxHeight
        val key = "$videoId@$height"
        val cached = streams[key]?.takeIf { System.currentTimeMillis() - it.second < STREAM_TTL_MS }?.first
        val stream = cached ?: YTPlayerUtils.videoStreamForDisplay(videoId, height).getOrNull()?.also {
            streams[key] = it to System.currentTimeMillis()
        } ?: return@withContext null
        Found(videoId, stream, offsetMs = knownOffset(videoId, song.id))
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
            // An audio upload is not a video, whatever its title says.
            val type = video.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
            if (type == com.ozyern.exhale.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV) return@mapNotNull null
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
        // The song itself only when it *is* a video. Every song's own audio upload (a still of the
        // cover) turns up in a video search too, and taking that as its video put the Video switch
        // on songs with no video at all, which then showed the cover standing still.
        return best?.first ?: song.id.takeIf { results.any { it.id == song.id && it.isMusicVideo } }
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
