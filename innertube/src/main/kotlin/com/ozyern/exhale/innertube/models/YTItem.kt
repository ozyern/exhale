/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.innertube.models

import com.ozyern.exhale.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.ozyern.exhale.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import java.util.Locale

sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

data class Artist(
    val name: String,
    val id: String?,
)

data class Album(
    val name: String,
    val id: String,
)

enum class AlbumReleaseType {
    ALBUM,
    SINGLE,
    EP;

    companion object {
        fun fromLabel(label: String?): AlbumReleaseType {
            return when (label?.trim()?.lowercase(Locale.ROOT)) {
                "single", "singles" -> SINGLE
                "ep", "eps" -> EP
                else -> ALBUM
            }
        }
    }
}

data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val releaseType: AlbumReleaseType = AlbumReleaseType.ALBUM,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"
}

data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
    val description: String? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val subscriberCountText: String? = null,
    val monthlyListenerCountText: String? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filter {
            when (it) {
                is SongItem -> {
                    val musicVideoType = it.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    val isMusicVideo = musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
                    !isMusicVideo
                }
                else -> true
            }
        }
    } else {
        this
    }

/** The label's own music video (not a fan upload), which search shows marked as such. */
val YTItem.isOfficialMusicVideo: Boolean
    get() = this is SongItem &&
        endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_OMV

/** A music video or a user upload rather than the audio track itself. */
val YTItem.isMusicVideo: Boolean
    get() = this is SongItem && endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
        .let { it == MUSIC_VIDEO_TYPE_OMV || it == MUSIC_VIDEO_TYPE_UGC }

/**
 * The audio track of [video] among these items: a song (not itself a video) whose title is the
 * video's with its dressing taken off - "(Official Video)", "[4K]", an "Artist - " prefix - and that
 * shares an artist with it, or is by an artist the video's title names. Null if there is none, so a
 * cover or a different song of the same name never stands in for it.
 */
fun List<YTItem>.audioTrackFor(video: YTItem): SongItem? {
    if (video !is SongItem || !video.isMusicVideo) return null
    val videoTitle = video.title.songTitleKey()
    val rawVideoTitle = video.title.lowercase()
    val videoArtists = video.artists.map { it.name.lowercase().trim() }.filter { it.isNotEmpty() }.toSet()
    return asSequence()
        .filterIsInstance<SongItem>()
        .filter { !it.isMusicVideo && it.id != video.id }
        .firstOrNull { song ->
            val title = song.title.songTitleKey()
            val titleMatches = title.isNotEmpty() && (title == videoTitle || videoTitle.contains(title))
            val songArtists = song.artists.map { it.name.lowercase().trim() }
            val sameArtist = songArtists.any { it in videoArtists } ||
                songArtists.any { it.isNotEmpty() && rawVideoTitle.contains(it) }
            titleMatches && sameArtist
        }
}

private val titleDressing = Regex(
    """\s*[(\[](?:[^)\]]*\b(?:official|video|audio|lyrics?|visuali[sz]er|4k|hd|mv|m/v|live|from)\b[^)\]]*)[)\]]""",
    RegexOption.IGNORE_CASE,
)

/** A title reduced to the song's own name: no video dressing, no "Artist - " in front, one case. */
private fun String.songTitleKey(): String {
    var t = replace(titleDressing, "")
    val dash = t.indexOf(" - ")
    if (dash > 0) t = t.substring(dash + 3)
    return t.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}

/** Lowercase words, accents and punctuation gone: how a query and a title are compared. */
private fun String.searchWords(): List<String> =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase()
        .split(Regex("""[^\p{L}\p{N}]+"""))
        .filter { it.isNotEmpty() }

/** Versions of a song that are not the song: wanted only when the search asks for them. */
private val alternateVersions = listOf(
    "remix", "sped", "slowed", "reverb", "lofi", "lo", "nightcore", "8d", "live", "cover", "karaoke",
    "instrumental", "acoustic", "unplugged", "mashup", "edit", "version", "reprise", "remaster",
    "remastered", "extended", "bass", "boosted", "mix", "rework", "flip", "dj", "female", "male",
)

/**
 * The song a search for [query] is most likely after, among these results: the plain studio
 * recording, named by what was typed.
 *
 * YouTube's top result for a song's name is often not the song: its music video, a remix, a
 * sped-up edit, a cover, a lyric video, or the artist when the name is also in an artist's. Every
 * audio track here (never a video) is scored on how much of its title was typed, whether its
 * artist was typed too, and whether it is another version (a remix, a live take) that the search
 * didn't ask for, with YouTube's own order as the tie-break. A song only qualifies if what was
 * typed actually names it: all of its title, or all of what was typed found in its title and
 * artists. Null when nothing qualifies, so a search for an artist or an album keeps its answer.
 */
fun List<YTItem>.bestSongFor(query: String): SongItem? {
    val typed = query.searchWords()
    if (typed.isEmpty()) return null
    val typedSet = typed.toSet()
    return asSequence()
        .filterIsInstance<SongItem>()
        .filter { !it.isMusicVideo }
        .distinctBy { it.id }
        .withIndex()
        .mapNotNull { (rank, song) ->
            val title = song.title.songTitleKey().searchWords()
            if (title.isEmpty()) return@mapNotNull null
            val artistWords = song.artists.flatMap { it.name.searchWords() }.toSet()
            val titleTyped = title.all { it in typedSet }
            val allTypedFound = typed.all { it in title || it in artistWords }
            if (!titleTyped && !allTypedFound) return@mapNotNull null
            // Something of the title must have been typed: an artist's name alone names no song.
            if (typed.none { it in title }) return@mapNotNull null
            var score = 0
            if (titleTyped) score += 60
            if (allTypedFound) score += 30
            // Every typed word that is neither title nor artist is noise the song doesn't answer.
            score -= 8 * typed.count { it !in title && it !in artistWords }
            if (song.artists.any { artist -> artist.name.searchWords().let { it.isNotEmpty() && typedSet.containsAll(it) } }) score += 25
            // Another version of the song, which the search didn't name.
            val rawTitle = song.title.searchWords().toSet()
            val unasked = alternateVersions.count { it in rawTitle && it !in typedSet }
            score -= 70 * unasked
            score -= rank.coerceAtMost(20)
            song to score
        }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}

/**
 * Whether [query] is this artist's, album's or playlist's name as typed: the one case where it
 * stays the top result over a song whose title was typed too.
 */
fun YTItem.isNamedBy(query: String): Boolean {
    if (this is SongItem) return false
    val name = title.searchWords()
    return name.isNotEmpty() && name == query.searchWords()
}

/**
 * Songs before videos. Most people searching a track want the track — a video loads slower and
 * carries the intro, the dialogue and the scene. The lead item stays put unless it is a video (an
 * artist or album the search named is the right first answer); after it come the songs, then
 * everything else, then the videos, each group in the order the catalogue ranked it.
 */
fun <T : YTItem> List<T>.songsFirst(): List<T> {
    if (isEmpty()) return this
    fun rank(item: T) = when {
        item is SongItem && !item.isMusicVideo -> 0
        item.isMusicVideo -> 2
        else -> 1
    }
    val lead = first()
    return if (lead.isMusicVideo) sortedBy(::rank) else listOf(lead) + drop(1).sortedBy(::rank)
}
