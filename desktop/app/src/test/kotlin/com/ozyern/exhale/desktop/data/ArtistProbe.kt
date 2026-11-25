package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real search results: which parser drops the artist. */
class ArtistProbe {
    @Test
    fun probe() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (q in listOf("espresso", "sabrina carpenter espresso", "manchild", "blinding lights", "tum hi ho")) {
            val summary = YouTube.searchSummary(q).getOrNull()
            summary?.summaries?.forEach { s ->
                s.items.filterIsInstance<SongItem>().take(3).forEach {
                    println("PROBE summary[$q] <${s.title}> '${it.title}' artists=${it.artists.map { a -> a.name + "|" + a.id }}")
                }
            }
            YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.take(2)?.forEach {
                println("PROBE songs[$q] '${it.title}' artists=${it.artists.map { a -> a.name + "|" + a.id }}")
            }
            YouTube.search(q, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items?.filterIsInstance<SongItem>()?.take(2)?.forEach {
                println("PROBE videos[$q] '${it.title}' artists=${it.artists.map { a -> a.name + "|" + a.id }}")
            }
        }
    }

    @Test
    fun probeNext() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (q in listOf("espresso", "blinding lights")) {
            for (filter in listOf(YouTube.SearchFilter.FILTER_SONG, YouTube.SearchFilter.FILTER_VIDEO)) {
                val song = YouTube.search(q, filter).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
                val endpoint = song.endpoint ?: com.ozyern.exhale.innertube.models.WatchEndpoint(videoId = song.id)
                val next = YouTube.next(endpoint).getOrNull()
                println("PROBE next[$q/${filter}] tapped='${song.title}' ${song.id} currentIndex=${next?.currentIndex}")
                next?.items?.take(3)?.forEachIndexed { i, it -> println("PROBE   [$i] '${it.title}' ${it.id} artists=${it.artists.map { a -> a.name }}") }
            }
        }
    }

    @Test
    fun probeLocales() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        val bad = Regex("""^\s*\d""")
        for ((hl, gl) in listOf("en" to "US", "en" to "IN", "hi" to "IN")) {
            YouTube.locale = com.ozyern.exhale.innertube.models.YouTubeLocale(gl = gl, hl = hl)
            YouTube.visitorData = YouTube.visitorData().getOrThrow()
            var total = 0
            var flagged = 0
            fun check(where: String, items: List<com.ozyern.exhale.innertube.models.YTItem>) {
                items.filterIsInstance<SongItem>().forEach {
                    total++
                    val names = it.artists.map { a -> a.name }
                    if (names.isEmpty() || names.any { n -> bad.containsMatchIn(n) && n.any(Char::isLetter).not() || n.contains(Regex("(?i)plays|views|song|video")) }) {
                        flagged++
                        println("PROBE [$hl-$gl] $where '${it.title}' ${it.id} artists=$names")
                    }
                }
            }
            for (q in listOf("espresso", "blinding lights", "tum hi ho", "manchild", "die with a smile")) {
                YouTube.searchSummary(q).getOrNull()?.summaries?.forEach { check("summary:$q:${it.title}", it.items) }
                YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.let { check("songs:$q", it.items) }
                YouTube.search(q, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.let { check("videos:$q", it.items) }
            }
            YouTube.home().getOrNull()?.sections?.forEach { check("home:${it.title}", it.items) }
            val first = YouTube.search("espresso", YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
            if (first != null) YouTube.next(com.ozyern.exhale.innertube.models.WatchEndpoint(videoId = first.id)).getOrNull()?.let { check("next", it.items) }
            println("PROBE [$hl-$gl] checked $total songs, $flagged without a real artist")
        }
    }

    @Test
    fun probeSuggestions() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (q in listOf("espresso", "espres", "blinding", "manchild", "tum hi")) {
            val s = YouTube.searchSuggestions(q).getOrNull()
            s?.recommendedItems?.forEach {
                val artists = (it as? SongItem)?.artists?.map { a -> a.name + "|" + a.id }
                println("PROBE suggest[$q] ${it::class.simpleName} '${it.title}' ${it.id} artists=$artists")
            }
        }
    }

    @Test
    fun probeAuthor() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (id in listOf("pL_l_YnzPAE", "iDVKMdnvgl8", "kIft-LUHHVA")) {
            val d = YouTube.player(id, null, com.ozyern.exhale.innertube.models.YouTubeClient.VISIONOS, null).getOrNull()?.videoDetails
            println("PROBE author[$id] '${d?.author}' channel=${d?.channelId}")
        }
    }

    @Test
    fun probeMoods() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        println("PROBE explore moods: ${YouTube.explore().getOrNull()?.moodAndGenres?.size}")
        YouTube.moodAndGenres().getOrNull()?.forEach { section ->
            println("PROBE section '${section.title}' ${section.items.size}: ${section.items.take(6).joinToString { it.title + "#" + java.lang.Long.toHexString(it.stripeColor) }}")
        }
        Unit
    }
}
