/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * A whole song read from an iOS-client URL two ways, as the Android app reads it (a Range header,
 * 4 MB pieces, and then 1 MB) and as the Windows proxy does (the range in the URL, numbered, with
 * alr=yes): how far each gets before the server refuses. EXHALE_SMOKE=1 to run.
 */
class RangedReadProbe {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()

    @Test
    fun howFarEachGets() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (query in listOf("Sabrina Carpenter Espresso", "Eminem Houdini", "The Weeknd Blinding Lights")) {
            val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items
                .filterIsInstance<com.ozyern.exhale.innertube.models.SongItem>().first()
            for (client in listOf(YouTubeClient.IOS, YouTubeClient.VISIONOS)) {
                val sts = NewPipeUtils.getSignatureTimestamp(song.id).getOrNull()
                val response = YouTube.player(song.id, null, client, sts).getOrThrow()
                val format = response.streamingData!!.adaptiveFormats.filter { it.isAudio }.maxBy { it.bitrate }
                val total = format.contentLength ?: error("no length")
                println("== ${song.title} via ${client.clientName}: itag ${format.itag}, $total bytes")
                for (piece in listOf(4L shl 20, 1L shl 20)) {
                    val url = NewPipeUtils.getStreamUrl(format, song.id, client).getOrThrow()
                    println("   Range header, ${piece shr 20} MB pieces: " + read(total, piece) { from, to ->
                        Request.Builder().url(url).header("User-Agent", client.userAgent).header("Range", "bytes=$from-$to").build()
                    })
                }
                val url = NewPipeUtils.getStreamUrl(format, song.id, client).getOrThrow()
                val cpn = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
                var rn = 0
                var current = url
                println("   range in URL, 1 MB pieces:     " + read(total, 1L shl 20) { from, to ->
                    Request.Builder().url("$current&range=$from-$to&rn=${rn++}&rbuf=0&cpn=$cpn&alr=yes").header("User-Agent", client.userAgent).build()
                })
            }
        }
    }

    /** Reads the file piece by piece; says how far it got, and what stopped it. */
    private fun read(total: Long, piece: Long, request: (Long, Long) -> Request): String {
        var position = 0L
        val started = System.currentTimeMillis()
        while (position < total) {
            val end = minOf(position + piece - 1, total - 1)
            val got = http.newCall(request(position, end)).execute().use { r ->
                if (!r.isSuccessful) return "STOPPED at ${100 * position / total}% (HTTP ${r.code}) after ${System.currentTimeMillis() - started} ms"
                if (r.header("Content-Type")?.startsWith("text/plain") == true) {
                    return "handed off at ${100 * position / total}% (text/plain redirect body)"
                }
                r.body.bytes().size.toLong()
            }
            if (got <= 0) return "STOPPED at ${100 * position / total}% (empty)"
            position += got
        }
        return "whole file in ${System.currentTimeMillis() - started} ms"
    }
}
