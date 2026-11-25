/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.models.Album
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class SongTally(val song: SongItem, val plays: Int, val listenedMs: Long)

data class ArtistTally(val name: String, val id: String?, val fallbackImage: String?, val plays: Int, val listenedMs: Long)

data class AlbumTally(val name: String, val id: String, val image: String, val plays: Int, val listenedMs: Long)

/** One month of Your Sound Chem. */
class MonthCapsule(
    val month: YearMonth,
    val listenedMs: Long,
    val plays: Int,
    val songs: List<SongTally>,
    val artists: List<ArtistTally>,
    val albums: List<AlbumTally>,
    /** Minutes listened on each day of the month; index 0 is the 1st. */
    val minutesByDay: List<Int>,
) {
    val isEmpty: Boolean get() = plays == 0
}

/**
 * What was actually listened to, for Your Sound Chem: a line for every song heard for at least
 * [MinimumListenMs], with how long it was heard. Kept in `%APPDATA%\Exhale\listening.tsv` — appended
 * to, never rewritten — so a crash can cost at most the song that was playing.
 */
object ListeningLog {
    /** Shorter than this is a skip, not a listen. */
    const val MinimumListenMs = 10_000L

    private const val LIST = ''
    private const val PAIR = ''

    private val file: File get() = File(DesktopPrefs.dataDir, "listening.tsv")
    private val lock = Any()
    private var cached: List<Event>? = null
    private var cachedLength = -1L

    private val _version = MutableStateFlow(0)

    /** Bumps whenever something is recorded, so a Sound Chem page that's open updates. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private class Event(val at: Long, val listenedMs: Long, val song: SongItem)

    fun record(song: SongItem, listenedMs: Long, at: Long = System.currentTimeMillis()) {
        if (listenedMs < MinimumListenMs || AppSettings.current.pauseHistory) return
        val fields = listOf(
            at.toString(),
            listenedMs.toString(),
            song.id,
            song.title,
            song.artists.joinToString(LIST.toString()) { "${it.name}$PAIR${it.id.orEmpty()}" },
            song.thumbnail,
            song.duration?.toString().orEmpty(),
            song.album?.name.orEmpty(),
            song.album?.id.orEmpty(),
        ).map { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }
        synchronized(lock) {
            runCatching {
                file.parentFile.mkdirs()
                file.appendText(fields.joinToString("\t") + "\n")
            }
        }
        _version.value = _version.value + 1
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file.delete() }
            cached = null
            cachedLength = -1L
        }
        _version.value = _version.value + 1
    }

    private fun events(): List<Event> = synchronized(lock) {
        val source = file
        val length = if (source.exists()) source.length() else 0L
        cached?.takeIf { length == cachedLength }?.let { return it }
        val parsed = if (length == 0L) {
            emptyList()
        } else {
            runCatching {
                source.readLines().mapNotNull { line ->
                    val p = line.split('\t')
                    if (p.size < 6) return@mapNotNull null
                    val at = p[0].toLongOrNull() ?: return@mapNotNull null
                    val heard = p[1].toLongOrNull() ?: return@mapNotNull null
                    Event(
                        at,
                        heard,
                        SongItem(
                            id = p[2],
                            title = p[3],
                            artists = p[4].split(LIST).filter { it.isNotEmpty() }.map {
                                Artist(name = it.substringBefore(PAIR), id = it.substringAfter(PAIR, "").ifBlank { null })
                            },
                            thumbnail = p[5],
                            duration = p.getOrNull(6)?.toIntOrNull(),
                            album = p.getOrNull(7)?.takeIf { it.isNotBlank() }?.let { name ->
                                p.getOrNull(8)?.takeIf { it.isNotBlank() }?.let { Album(name, it) }
                            },
                        ),
                    )
                }
            }.getOrDefault(emptyList())
        }
        cached = parsed
        cachedLength = length
        parsed
    }

    /** Every month from this one back to the first thing ever recorded, newest first. */
    fun months(zone: ZoneId = ZoneId.systemDefault()): List<YearMonth> {
        val now = YearMonth.now(zone)
        val oldest = events().minOfOrNull { it.at }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: now
        return generateSequence(now) { it.minusMonths(1) }.takeWhile { !it.isBefore(oldest) }.toList()
    }

    fun capsule(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): MonthCapsule {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inMonth = events().filter { it.at in start until end }

        val songs = inMonth.groupBy { it.song.id }
            .map { (_, heard) -> SongTally(heard.maxBy { it.at }.song, heard.size, heard.sumOf { it.listenedMs }) }
            .sortedWith(compareByDescending<SongTally> { it.listenedMs }.thenByDescending { it.plays })

        class Tally(val name: String, val id: String?, val image: String?) {
            var plays = 0
            var ms = 0L
        }

        val artists = LinkedHashMap<String, Tally>()
        val albums = LinkedHashMap<String, Tally>()
        val minutes = LongArray(month.lengthOfMonth())
        inMonth.forEach { event ->
            event.song.artists.forEach { artist ->
                artists.getOrPut(artist.id ?: artist.name.lowercase()) { Tally(artist.name, artist.id, event.song.thumbnail) }.apply {
                    plays++
                    ms += event.listenedMs
                }
            }
            event.song.album?.let { album ->
                albums.getOrPut(album.id) { Tally(album.name, album.id, event.song.thumbnail) }.apply {
                    plays++
                    ms += event.listenedMs
                }
            }
            val day = Instant.ofEpochMilli(event.at).atZone(zone).dayOfMonth - 1
            if (day in minutes.indices) minutes[day] += event.listenedMs
        }

        return MonthCapsule(
            month = month,
            listenedMs = inMonth.sumOf { it.listenedMs },
            plays = inMonth.size,
            songs = songs,
            artists = artists.values
                .map { ArtistTally(it.name, it.id, it.image, it.plays, it.ms) }
                .sortedByDescending { it.listenedMs },
            albums = albums.values
                .map { AlbumTally(it.name, it.id!!, it.image.orEmpty(), it.plays, it.ms) }
                .sortedByDescending { it.listenedMs },
            minutesByDay = minutes.map { (it / 60_000L).toInt() },
        )
    }
}
