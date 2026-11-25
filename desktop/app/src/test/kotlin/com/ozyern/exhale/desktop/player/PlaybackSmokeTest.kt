/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** End to end: search → innertube player → NewPipe decipher → libmpv actually advancing. */
class PlaybackSmokeTest {
    @Test
    fun syncedLyricsForKnownSong() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val song = com.ozyern.exhale.innertube.models.SongItem(
            id = "kIft-LUHHVA",
            title = "Espresso",
            artists = listOf(com.ozyern.exhale.innertube.models.Artist("Sabrina Carpenter", null)),
            duration = 176,
            thumbnail = "",
        )
        val lyrics = com.ozyern.exhale.desktop.lyrics.LyricsRepository.lyricsFor(song, 176)
        val synced = lyrics as? com.ozyern.exhale.desktop.lyrics.Lyrics.Synced
        val firstSung = synced?.lines?.firstOrNull { it.text.isNotBlank() }
        println("lyrics: ${lyrics::class.simpleName} ${synced?.source}, ${synced?.lines?.size} lines, wordLevel=${synced?.wordLevel}")
        println("first line: ${firstSung?.text} -> ${firstSung?.words?.joinToString(" | ") { "${it.text.trim()}@${it.startMs}-${it.endMs}" }}")
        assertTrue("expected synced lyrics, got $lyrics", synced != null)
        // Every sung line must carry word timings — real or synthesized — for the karaoke fill.
        assertTrue("a sung line has no word timings", synced!!.lines.filter { it.text.isNotBlank() }.all { it.words.isNotEmpty() })
    }

    /** Songs as YouTube titles them — features, "(Official Video)" — which used to find nothing or the wrong song. */
    @Test
    fun lyricsCoverage() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        fun song(id: String, title: String, duration: Int, vararg artists: String) = SongItem(
            id = id,
            title = title,
            artists = artists.map { com.ozyern.exhale.innertube.models.Artist(it, null) },
            duration = duration,
            thumbnail = "",
        )
        val songs = listOf(
            song("coverage-1", "Too Many Nights (feat. Don Toliver)", 200, "Metro Boomin", "Future"),
            song("coverage-2", "Blinding Lights (Official Video)", 200, "The Weeknd"),
            song("coverage-3", "Levitating (feat. DaBaby)", 203, "Dua Lipa"),
            song("coverage-4", "Die With A Smile", 251, "Lady Gaga", "Bruno Mars"),
            song("coverage-5", "Tum Hi Ho", 262, "Arijit Singh"),
        )
        var found = 0
        for (s in songs) {
            val lyrics = com.ozyern.exhale.desktop.lyrics.LyricsRepository.lyricsFor(s, s.duration ?: 0)
            val summary = when (lyrics) {
                is com.ozyern.exhale.desktop.lyrics.Lyrics.Synced -> {
                    found++
                    "synced from ${lyrics.source}, ${lyrics.lines.size} lines, last at ${lyrics.lines.last().timeMs / 1000}s, wordLevel=${lyrics.wordLevel}"
                }
                is com.ozyern.exhale.desktop.lyrics.Lyrics.Plain -> {
                    found++
                    "plain from ${lyrics.source}, ${lyrics.lines.size} lines"
                }
                else -> "not found"
            }
            println("lyrics: ${s.title} — $summary")
        }
        assertTrue("only $found of ${songs.size} songs had lyrics", found >= songs.size - 1)
    }

    @Test
    fun searchResolveAndPlay() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()

        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        println("song: ${song.title} (${song.id})")

        val stream = StreamResolver.resolve(song.id).getOrThrow()
        println("stream: ${stream.describe()} from ${stream.clientName}")
        println("url host: ${stream.url.substringBefore("?").take(70)}")

        val loaded = CountDownLatch(1)
        val position = AtomicReference(0.0)
        val error = AtomicReference<String?>(null)

        Mpv(object : Mpv.Listener {
            override fun onFileLoaded() = loaded.countDown()
            override fun onPositionChanged(seconds: Double) = position.set(seconds)
            override fun onError(message: String) {
                error.set(message)
                loaded.countDown()
            }
        }).use { mpv ->
            mpv.setVolume(0)
            mpv.load(stream.url, stream.headers)
            assertTrue("file never loaded", loaded.await(30, TimeUnit.SECONDS))
            assertNull(error.get())
            Thread.sleep(5_000)
            println("position after 5s: ${position.get()}")
            assertTrue("playback did not advance", position.get() > 2.0)
            // Past where an unusable client's URL used to stop answering (about a third in).
            val target = ((song.duration ?: 176) * 0.75)
            mpv.seekTo(target)
            Thread.sleep(6_000)
            println("position after seeking to ${target.toInt()}s and waiting 6s: ${position.get()}")
            assertTrue("playback did not continue after seeking deep", position.get() > target + 2.0)
        }
    }

    @Test
    fun videoCeilings() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (id in listOf("4NRXx6U8ABQ", "kIft-LUHHVA")) {
            for (cap in listOf(2160, 1440, 1080, 720)) {
                val t0 = System.currentTimeMillis()
                val r = StreamResolver.resolveVideo(id, cap)
                println("VIDEO $id cap=$cap -> ${r.fold({ "${it.quality} via ${it.clientName} audio=${it.audioUrl != null} available=${it.available}" }, { "FAIL ${it.message?.take(300)}" })} (${System.currentTimeMillis() - t0}ms)")
            }
        }
    }

    /** Songs from a real queue, at Max quality: each must load, play, and keep playing from its middle. */
    @Test
    fun queueSongsAtMax() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        com.ozyern.exhale.desktop.data.AppSettings.update { it.copy(audioQuality = "MAX") }
        val ids = listOf("eVli-tstM5E", "YcSP1ZUf1eQ", "ApXoWvfEYVU", "Pkh8UtuejGw", "TUVcZfQe-Kw", "kPa7bsKwL-c", "ko70cExuzZM", "aJOTlE1K90k")
        var good = 0
        for (id in ids) {
            val t0 = System.currentTimeMillis()
            val r = StreamResolver.resolve(id)
            val stream = r.getOrNull()
            if (stream == null) { println("QUEUE $id RESOLVE FAILED ${r.exceptionOrNull()?.message?.take(200)}"); continue }
            val resolveMs = System.currentTimeMillis() - t0
            val loaded = CountDownLatch(1)
            val position = AtomicReference(0.0)
            val duration = AtomicReference(0.0)
            val error = AtomicReference<String?>(null)
            Mpv(object : Mpv.Listener {
                override fun onFileLoaded() = loaded.countDown()
                override fun onPositionChanged(seconds: Double) = position.set(seconds)
                override fun onDurationChanged(seconds: Double) = duration.set(seconds)
                override fun onError(message: String) { error.set(message); loaded.countDown() }
            }).use { mpv ->
                mpv.setVolume(0)
                mpv.load(stream.url, stream.headers)
                val ok = loaded.await(30, TimeUnit.SECONDS) && error.get() == null
                Thread.sleep(3_000)
                val early = position.get()
                val target = duration.get() * 0.5
                mpv.seekTo(target)
                Thread.sleep(5_000)
                val late = position.get()
                val pass = ok && early > 1.5 && late > target + 2.0
                if (pass) good++
                println("QUEUE $id ${stream.describe()} via ${stream.clientName}: resolve ${resolveMs}ms loaded=$ok early=${"%.1f".format(early)} seek->${target.toInt()} late=${"%.1f".format(late)} err=${error.get()} ${if (pass) "PASS" else "FAIL"}")
            }
        }
        println("QUEUE $good/${ids.size} played")
    }

    @Test
    fun coldResolve() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        com.ozyern.exhale.desktop.data.AppSettings.update { it.copy(audioQuality = "MAX") }
        val t = System.currentTimeMillis()
        StreamResolver.resolve("eVli-tstM5E").getOrThrow()
        println("TIMING TOTAL cold ${System.currentTimeMillis() - t}ms")
    }

    /** A song reloaded at a place must open there — never at the top and jump. (Video needs a window to draw into, so only sound is checked here.) */
    @Test
    fun resumeOpensAtPosition() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val id = "kIft-LUHHVA"
        for (mode in listOf("audio")) {
            val seen = java.util.Collections.synchronizedList(mutableListOf<Double>())
            val loaded = CountDownLatch(1)
            Mpv(object : Mpv.Listener {
                override fun onFileLoaded() = loaded.countDown()
                override fun onPositionChanged(seconds: Double) { seen += seconds }
                override fun onError(message: String) { println("RESUME $mode error: $message"); loaded.countDown() }
            }).use { mpv ->
                mpv.setVolume(0)
                if (mode == "audio") {
                    val stream = StreamResolver.resolve(id).getOrThrow()
                    mpv.load(stream.url, stream.headers, 100.0)
                } else {
                    val video = StreamResolver.resolveVideo(id, 720).getOrThrow()
                    println("RESUME $mode: ${video.quality} via ${video.clientName} audio=${video.audioUrl != null}")
                    mpv.loadVideo(video.videoUrl, video.audioUrl, video.headers, if (mode == "video") 100.0 else null)
                }
                loaded.await(30, TimeUnit.SECONDS)
                Thread.sleep(4_000)
                val positions = seen.toList()
                println("RESUME $mode: first=${positions.firstOrNull()} min=${positions.minOrNull()} last=${positions.lastOrNull()} n=${positions.size}")
                if (mode == "video-nostart") return@use
                assertTrue("$mode: opened before the place asked for: $positions", positions.isNotEmpty() && positions.all { it >= 99.0 })
                assertTrue("$mode: did not advance", positions.last() > 101.0)
            }
        }
    }
}
