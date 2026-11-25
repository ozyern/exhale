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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Which client versions YouTube still answers, and whether what comes back can be played.
 *
 * A client that asks with a version YouTube has retired is told to log in, whatever the video — so a
 * stale version string looks exactly like a video that needs an account. This asks the same video
 * with several versions of each client and prints what each one is told, which is the difference
 * between "we need to sign in" and "we need to say we're newer".
 */
class ClientVersionProbe {
    private fun YouTubeClient.withVersion(version: String) = copy(clientVersion = version)

    @Test
    fun whichVersionsStillAnswer() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val videoId = java.io.File(System.getProperty("user.home"), ".exhale-video-id")
            .takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } ?: "eVli-tstM5E"
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()

        val candidates = buildList {
            listOf("20.10.4", "20.03.02", "19.45.4").forEach {
                add("IOS $it" to YouTubeClient.IOS.withVersion(it))
            }
            listOf("20.10.4", "19.45.4").forEach {
                add("IPADOS $it" to YouTubeClient.IPADOS.withVersion(it))
            }
            listOf("8.20.2", "7.27.0", "8.05.1").forEach {
                add("IOS_MUSIC $it" to YouTubeClient.IOS_MUSIC.withVersion(it))
            }
            listOf("0.1", "1.0").forEach {
                add("VISIONOS $it" to YouTubeClient.VISIONOS.withVersion(it))
            }
        }

        for ((label, client) in candidates) {
            val response = YouTube.player(videoId, null, client, signatureTimestamp).getOrNull()
            if (response == null) {
                println("  %-26s request failed".format(label))
                continue
            }
            val status = response.playabilityStatus.status
            val formats = response.streamingData?.adaptiveFormats.orEmpty().filterNot { it.isAudio }
            val direct = formats.count { it.url != null }
            val best = formats.filter { it.url != null }.maxByOrNull { it.height ?: 0 }
            println(
                "  %-26s %-16s video=%d direct=%d best=%s".format(
                    label,
                    status,
                    formats.size,
                    direct,
                    best?.let { "${it.height}p itag ${it.itag}" } ?: "—",
                ),
            )
        }
    }

    /** What the player would actually get: the resolver's own answer, start to finish. */
    @Test
    fun theResolverReachesTheBestRendition() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val ids = listOf(
            "eVli-tstM5E" to "Espresso (official video)",
            "KEG7b851Ric" to "Taste (official video)",
            "1mtJvsfAtF0" to "BRITS performance",
        )
        for ((id, what) in ids) {
            val video = StreamResolver.resolveVideo(id)
            println(
                video.fold(
                    { "  %-26s %-8s from %-10s aspect %.2f".format(what, it.quality, it.clientName, it.aspect) },
                    { "  %-26s failed: %s".format(what, it.message?.take(90)) },
                ),
            )
        }
    }

    /**
     * Resolving is not playing. mpv is given a real window, because without one it is configured for
     * sound alone and a picture-only stream has nothing it can play — which looks exactly like a
     * broken stream in the log.
     */
    @Test
    fun theBestRenditionActuallyPlays() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        // Not the same video every probe: pull one hard enough, often enough, and YouTube starts
        // refusing it to this address partway through, which looks exactly like a bug in the player.
        val video = StreamResolver.resolveVideo(System.getenv("EXHALE_VIDEO_ID") ?: "dQw4w9WgXcQ").getOrThrow()
        println("  resolved ${video.quality} from ${video.clientName}")

        val frame = java.awt.Frame().apply {
            setSize(640, 360)
            isUndecorated = true
            // Off to one side rather than in the way; it only has to be a real window on screen.
            setLocation(-2000, -2000)
            isVisible = true
        }
        val canvas = java.awt.Canvas().also { frame.add(it); frame.validate() }
        val handle = com.sun.jna.Native.getComponentID(canvas)

        val loaded = java.util.concurrent.CountDownLatch(1)
        val position = java.util.concurrent.atomic.AtomicReference(0.0)
        val error = java.util.concurrent.atomic.AtomicReference<String?>(null)
        try {
            Mpv(
                object : Mpv.Listener {
                    override fun onFileLoaded() = loaded.countDown()
                    override fun onPositionChanged(seconds: Double) = position.set(seconds)
                    override fun onError(message: String) {
                        error.set(message)
                        loaded.countDown()
                    }
                },
                handle,
            ).use { mpv ->
                mpv.setVolume(0)
                mpv.loadVideo(video.videoUrl, video.audioUrl, video.headers)
                val opened = loaded.await(45, java.util.concurrent.TimeUnit.SECONDS)
                Thread.sleep(8_000)
                println("  opened=$opened error=${error.get()} position=${position.get()}")
                assertTrue("the picture never started: ${error.get()}", position.get() > 1.0)
            }
        } finally {
            frame.dispose()
        }
    }

    /** The ceiling the listener picks is the ceiling the resolver respects. */
    @Test
    fun theQualityCeilingIsHonoured() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        for (ceiling in listOf("AUTO", "1080", "720")) {
            com.ozyern.exhale.desktop.data.AppSettings.update { it.copy(videoQuality = ceiling) }
            val quality = StreamResolver.resolveVideo("eVli-tstM5E").map { it.quality }.getOrElse { "failed" }
            println("  ceiling %-6s -> %s".format(ceiling, quality))
        }
        com.ozyern.exhale.desktop.data.AppSettings.update { it.copy(videoQuality = "AUTO") }
    }

    /** Seeking. The picture has to keep playing from wherever it lands. */
    @Test
    fun seekingKeepsPlaying() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val videoId = System.getenv("EXHALE_VIDEO_ID") ?: "dQw4w9WgXcQ"
        val video = StreamResolver.resolveVideo(videoId).getOrThrow()
        println("  resolved $videoId at ${video.quality} (setting=${com.ozyern.exhale.desktop.data.AppSettings.current.videoQuality})")
        val frame = java.awt.Frame().apply {
            setSize(640, 360); isUndecorated = true; setLocation(-2000, -2000); isVisible = true
        }
        val canvas = java.awt.Canvas().also { frame.add(it); frame.validate() }
        val handle = com.sun.jna.Native.getComponentID(canvas)
        val loaded = java.util.concurrent.CountDownLatch(1)
        val position = java.util.concurrent.atomic.AtomicReference(0.0)
        val error = java.util.concurrent.atomic.AtomicReference<String?>(null)
        try {
            Mpv(
                object : Mpv.Listener {
                    override fun onFileLoaded() = loaded.countDown()
                    override fun onPositionChanged(seconds: Double) = position.set(seconds)
                    override fun onError(message: String) { error.set(message); loaded.countDown() }
                },
                handle,
            ).use { mpv ->
                mpv.setVolume(0)
                mpv.loadVideo(video.videoUrl, video.audioUrl, video.headers)
                loaded.await(45, java.util.concurrent.TimeUnit.SECONDS)
                // Deliberately short: the seek has to land well past anything already fetched, which is
                // the case that used to stop the picture dead.
                Thread.sleep(6_000)
                val before = position.get()
                println("  before seek: $before")
                mpv.seekTo(200.0)
                var after = 0.0
                repeat(12) {
                    Thread.sleep(10_000)
                    after = position.get()
                    println("  ${(it + 1) * 10}s after the seek: $after")
                    if (after > 201.0) return@repeat
                }
                println("  after seeking to 200s: $after error=${error.get()}")
                assertTrue("playback stopped after seeking past what had been fetched", after > 201.0)
            }
        } finally {
            frame.dispose()
        }
    }

    /** Is the sound that comes with a picture usable on its own, before mpv is involved at all? */
    @Test
    fun theVideosAudioUrlIsUsable() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val video = StreamResolver.resolveVideo("eVli-tstM5E").getOrThrow()
        println("  quality=${video.quality} audio=${video.audioUrl?.take(40)}")
        val audio = video.audioUrl
        if (audio == null) {
            println("  no separate audio")
            return@runBlocking
        }
        // Straight at the proxy, one piece, nothing else running.
        val client = okhttp3.OkHttpClient()
        listOf("bytes=0-1048575", "bytes=0-131071", "bytes=0-1023").forEach { range ->
            val request = okhttp3.Request.Builder().url(audio).header("Range", range).build()
            val code = runCatching { client.newCall(request).execute().use { it.code } }.getOrElse { -1 }
            println("  proxy $range -> $code")
        }
    }

    /**
     * Whether, asked the way YouTube's own player asks, a piece from the middle can be had without
     * everything before it. If it can, a seek is a request rather than a wait.
     */
    @Test
    fun theMiddleCanBeAskedForDirectly() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp("1mtJvsfAtF0").getOrNull()
        val response = YouTube.player("1mtJvsfAtF0", null, YouTubeClient.IOS, signatureTimestamp).getOrThrow()
        val format = response.streamingData?.adaptiveFormats
            ?.filterNot { it.isAudio }
            ?.filter { (it.height ?: 0) in 1..1080 }
            ?.maxByOrNull { it.height ?: 0 } ?: error("no 1080p format")
        val url = format.url ?: error("no URL")
        val total = format.contentLength ?: 0L
        val client = okhttp3.OkHttpClient()
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val nonce = (1..16).map { alphabet.random() }.joinToString("")

        // Two thirds in, on a URL nothing has read a byte from.
        val from = total * 2 / 3
        val to = from + 1024 * 1024 - 1
        val request = okhttp3.Request.Builder()
            .url("$url&range=$from-$to&rn=0&rbuf=0&cpn=$nonce&alr=yes")
            .header("User-Agent", YouTubeClient.IOS.userAgent)
            .build()
        client.newCall(request).execute().use { answer ->
            val body = answer.body.bytes()
            val text = body.take(200).toByteArray().decodeToString()
            println("  deep piece, first ask -> ${answer.code}, ${body.size} bytes, type ${answer.header("Content-Type")}")
            if (text.startsWith("http")) println("  handed back a URL instead: ${text.take(60)}")
        }
    }

    /**
     * What gets past the wall. YouTube's own players put a playback nonce on every media request and
     * number the requests; a URL asked for without them may be treated as a download rather than a
     * playback, and cut off a third of the way through. This tries each shape on its own fresh URL.
     */
    @Test
    fun whatCarriesAStreamPastAThirdOfIt() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val ua = YouTubeClient.IOS.userAgent
        val client = okhttp3.OkHttpClient()
        val chunk = 1L * 1024 * 1024
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

        val videoId = System.getenv("EXHALE_VIDEO_ID") ?: "1mtJvsfAtF0"

        suspend fun freshUrl(): Pair<String, Long> {
            val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
            val response = YouTube.player(videoId, null, YouTubeClient.IOS, signatureTimestamp).getOrThrow()
            val format = response.streamingData?.adaptiveFormats
                ?.filterNot { it.isAudio }
                ?.filter { (it.height ?: 0) in 1..1080 }
                ?.maxByOrNull { it.height ?: 0 } ?: error("no 1080p format")
            return (format.url ?: error("no URL")) to (format.contentLength ?: 0L)
        }

        fun walk(label: String, decorate: (String, Long, Long, Int) -> Pair<String, String?>) {
            val (url, total) = runBlocking { freshUrl() }
            val nonce = (1..16).map { alphabet.random() }.joinToString("")
            var at = 0L
            var asks = 0
            var code = 200
            while (at < total) {
                val end = minOf(at + chunk, total) - 1
                val (target, range) = decorate(url.replace("CPN", nonce), at, end, asks)
                code = runCatching {
                    val builder = okhttp3.Request.Builder().url(target).header("User-Agent", ua)
                    range?.let { builder.header("Range", it) }
                    client.newCall(builder.build()).execute().use { it.body.byteStream().readBytes(); it.code }
                }.getOrElse { -1 }
                asks++
                if (code !in 200..299) break
                at += chunk
            }
            println("  %-22s %d of %d bytes (%d%%) -> %d".format(label, at, total, if (total > 0) at * 100 / total else 0, code))
        }

        walk("header only") { url, from, to, _ -> url to "bytes=$from-$to" }
        walk("header + cpn") { url, from, to, _ -> "$url&cpn=CPN" to "bytes=$from-$to" }
        walk("range + rn + cpn") { url, from, to, n -> "$url&range=$from-$to&rn=$n&rbuf=0&cpn=CPN" to null }
        walk("range + rn + cpn + alr") { url, from, to, n -> "$url&range=$from-$to&rn=$n&rbuf=0&cpn=CPN&alr=yes" to null }
    }

    /**
     * Whether the refusal belongs to the URL or to this machine. If a freshly issued URL answers while
     * the old one is still refusing, a stream that dies partway through can simply be asked for again.
     */
    @Test
    fun aFreshUrlAnswersWhileTheOldOneRefuses() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val client = okhttp3.OkHttpClient()
        val ua = YouTubeClient.IOS.userAgent
        val chunk = 1L * 1024 * 1024

        suspend fun freshUrl(): String {
            val signatureTimestamp = NewPipeUtils.getSignatureTimestamp("eVli-tstM5E").getOrNull()
            val response = YouTube.player("eVli-tstM5E", null, YouTubeClient.IOS, signatureTimestamp).getOrThrow()
            return response.streamingData?.adaptiveFormats?.filterNot { it.isAudio }
                ?.firstOrNull { it.url != null }?.url ?: error("no direct format")
        }

        fun take(target: String, from: Long): Int = runCatching {
            client.newCall(
                okhttp3.Request.Builder().url(target).header("User-Agent", ua)
                    .header("Range", "bytes=$from-${from + chunk - 1}").build(),
            ).execute().use { response ->
                response.body.byteStream().readBytes()
                response.code
            }
        }.getOrElse { -1 }

        val first = freshUrl()
        var at = 0L
        var code = 200
        while (at < 200L * 1024 * 1024) {
            code = take(first, at)
            if (code !in 200..299) break
            at += chunk
        }
        println("  the first URL stopped at ${at / (1024 * 1024)}MB with $code")
        assumeTrue("no wall to study", code !in 200..299)

        val second = freshUrl()
        println("  same URL again          -> ${take(first, at)}")
        println("  fresh URL from the top  -> ${take(second, 0)}")
        println("  fresh URL at ${at / (1024 * 1024)}MB       -> ${take(second, at)}")
    }

    /** How a deep offset has to be asked for: in the header, or in the URL as YouTube's own players do. */
    @Test
    fun aDeepOffsetNeedsTheRightAsking() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp("eVli-tstM5E").getOrNull()
        val response = YouTube.player("eVli-tstM5E", null, YouTubeClient.IOS, signatureTimestamp).getOrThrow()
        val format = response.streamingData?.adaptiveFormats
            ?.filterNot { it.isAudio }
            ?.firstOrNull { it.url != null } ?: error("no direct format")
        val url = format.url!!
        val client = okhttp3.OkHttpClient()
        val ua = YouTubeClient.IOS.userAgent
        val deep = 169_098_534L
        val end = deep + 262_143

        fun ask(what: String, target: String, range: String?) {
            val builder = okhttp3.Request.Builder().url(target).header("User-Agent", ua)
            range?.let { builder.header("Range", it) }
            val code = runCatching { client.newCall(builder.build()).execute().use { it.code } }.getOrElse { -1 }
            println("  %-34s -> %s".format(what, code))
        }

        // Deep first, on a URL nothing has touched: if sequence is what matters, this tells us.
        ask("deep offset, first request", url, "bytes=$deep-$end")
        ask("header Range, offset 0", url, "bytes=0-262143")
        ask("header Range, deep offset", url, "bytes=$deep-$end")
        ask("&range= param, deep offset", "$url&range=$deep-$end", null)
        ask("&range= param + header, deep", "$url&range=$deep-$end", "bytes=$deep-$end")
        // The parameter makes the response its own resource, so the header has to be relative to it.
        ask("&range= param + relative header", "$url&range=$deep-$end", "bytes=0-${end - deep}")
        ask("&range= param + open header", "$url&range=$deep-$end", "bytes=0-")
        // What YouTube's own player attaches to every media request: which request number this is,
        // and how much it already has buffered.
        ask("&range&rn&rbuf, no header", "$url&range=$deep-$end&rn=1&rbuf=0", null)
        ask("&rn&rbuf + header", "$url&rn=1&rbuf=0", "bytes=$deep-$end")
        ask("&alr=yes + header", "$url&alr=yes", "bytes=$deep-$end")
    }

    /** Does the iOS client offer a segmented playlist, and will mpv play and seek it? */
    @Test
    fun theSegmentedPlaylistSeeks() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp("eVli-tstM5E").getOrNull()
        val response = YouTube.player("eVli-tstM5E", null, YouTubeClient.IOS, signatureTimestamp).getOrThrow()
        val manifest = response.streamingData?.hlsManifestUrl
        println("  manifest: ${manifest?.take(60) ?: "none"}")
        if (manifest == null) return@runBlocking

        val frame = java.awt.Frame().apply {
            setSize(640, 360); isUndecorated = true; setLocation(-2000, -2000); isVisible = true
        }
        val canvas = java.awt.Canvas().also { frame.add(it); frame.validate() }
        val handle = com.sun.jna.Native.getComponentID(canvas)
        val loaded = java.util.concurrent.CountDownLatch(1)
        val position = java.util.concurrent.atomic.AtomicReference(0.0)
        val error = java.util.concurrent.atomic.AtomicReference<String?>(null)
        try {
            Mpv(
                object : Mpv.Listener {
                    override fun onFileLoaded() = loaded.countDown()
                    override fun onPositionChanged(seconds: Double) = position.set(seconds)
                    override fun onError(message: String) { error.set(message); loaded.countDown() }
                },
                handle,
            ).use { mpv ->
                mpv.setVolume(0)
                mpv.loadVideo(manifest, null, StreamHeaders(YouTubeClient.IOS.userAgent, null, null))
                loaded.await(45, java.util.concurrent.TimeUnit.SECONDS)
                Thread.sleep(8_000)
                println("  playing at ${position.get()} error=${error.get()}")
                mpv.seekTo(90.0)
                Thread.sleep(10_000)
                println("  after seek: ${position.get()}")
                assertTrue("the playlist never played", position.get() > 91.0)
            }
        } finally {
            frame.dispose()
        }
    }
}
