/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import com.ozyern.exhale.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class StreamHeaders(
    val userAgent: String,
    val origin: String?,
    val referer: String?,
) {
    /**
     * One set of headers for two streams. mpv sends the same headers to the picture and the sound, so
     * when they come from different clients each one's Origin and Referer has to survive; a web URL is
     * refused without them, and a native-client URL never minds them being there.
     */
    fun merge(other: StreamHeaders?): StreamHeaders =
        if (other == null) this else copy(origin = origin ?: other.origin, referer = referer ?: other.referer)
}

/** A song as a video: the picture, its sound (unless the picture already carries it), and where from. */
data class ResolvedVideo(
    val videoUrl: String,
    val audioUrl: String?,
    val headers: StreamHeaders,
    val quality: String,
    val clientName: String,
    /** The picture's own shape, so the surface can be cut to it instead of letterboxing inside it. */
    val aspect: Float = 16f / 9f,
    /** The heights this video actually has, so nothing is offered that it can't deliver. */
    val available: List<Int> = emptyList(),
)

data class ResolvedStream(
    val url: String,
    val headers: StreamHeaders,
    val mimeType: String,
    val bitrate: Int,
    val clientName: String,
    /** YouTube's measured loudness for the track, for audio normalization. */
    val loudnessDb: Double?,
    val sampleRate: Int? = null,
    /** Bits per sample, when the source says — a lossless copy's other half. */
    val bitDepth: Int? = null,
    val lossless: Boolean = false,
    /** The lossless source this came from, named in the badge. Null for YouTube's own streams. */
    val sourceLabel: String? = null,
) {
    /** "Opus · 160 kbps · 48 kHz", or "FLAC · 24-bit/96 kHz · My library" off a lossless source. */
    fun describe(): String {
        val codec = Regex("""codecs="([^"]+)"""").find(mimeType)?.groupValues?.get(1) ?: mimeType
        val name = when {
            codec.startsWith("opus") -> "Opus"
            codec.startsWith("mp4a") -> "AAC"
            codec.contains("flac", ignoreCase = true) -> "FLAC"
            codec.contains("alac", ignoreCase = true) -> "ALAC"
            codec.contains("aiff", ignoreCase = true) -> "AIFF"
            codec.contains("wav", ignoreCase = true) -> "WAV"
            codec.startsWith("audio/") -> codec.removePrefix("audio/").uppercase(Locale.US)
            else -> codec
        }
        val quality = if (lossless) {
            listOfNotNull(bitDepth?.let { "$it-bit" }, sampleRate?.let(::formatSampleRate)).joinToString("/")
        } else {
            listOfNotNull("${bitrate / 1000} kbps", sampleRate?.let(::formatSampleRate)).joinToString(" · ")
        }
        return listOfNotNull(name, quality.takeIf { it.isNotEmpty() }, sourceLabel).joinToString(" · ")
    }
}

/**
 * Video id → a URL mpv can open. A port of the Android app's YTPlayerUtils: client fallback,
 * NewPipe deciphering, range-probe validation, and the quality, codec and stream-client settings.
 */
object StreamResolver {
    // Same order as the Android app: its preferred ANDROID_VR first, then STREAM_FALLBACK_CLIENTS.
    private val clients: List<YouTubeClient> = listOf(
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.IOS,
        YouTubeClient.MOBILE,
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.IOS_MUSIC,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.ANDROID_TESTSUITE,
        YouTubeClient.ANDROID_UNPLUGGED,
        YouTubeClient.IPADOS,
        YouTubeClient.VISIONOS,
        YouTubeClient.TVHTML5,
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR,
        YouTubeClient.WEB_REMIX,
    )

    /** Player & audio → Stream client. AUTO leaves the order to what last worked. */
    private fun preferredClient(): YouTubeClient? = when (AppSettings.current.streamClient) {
        "ANDROID_VR" -> YouTubeClient.ANDROID_VR_NO_AUTH
        "IOS" -> YouTubeClient.IOS
        "WEB_REMIX" -> YouTubeClient.WEB_REMIX
        "TVHTML5" -> YouTubeClient.TVHTML5
        "ANDROID_MUSIC" -> YouTubeClient.ANDROID_MUSIC
        else -> null
    }

    @Volatile private var httpFor: Pair<Proxy?, OkHttpClient>? = null

    /** Probes go through the same proxy as everything else. */
    private fun http(): OkHttpClient {
        val proxy = YouTube.proxy
        httpFor?.let { (p, client) -> if (p == proxy) return client }
        return OkHttpClient.Builder().proxy(proxy).build().also { httpFor = proxy to it }
    }

    /**
     * Which client works is decided by the network (bot checks, IP reputation), not the song,
     * so the last one that played goes first. Otherwise every track pays for the same run of
     * failed requests before reaching it.
     */
    @Volatile private var lastWorkingClient: YouTubeClient? = null

    /**
     * Clients that can't serve this network right now — asking for a sign-in, or handing out URLs that
     * stop answering. Which ones is decided by the network, not the song, so asking each of them again
     * for every track was most of the wait before a song started. They get another chance after a while.
     */
    private val deadUntil = ConcurrentHashMap<String, Long>()
    private const val DeadForMs = 10 * 60_000L

    private fun isDead(client: YouTubeClient): Boolean {
        val until = deadUntil[client.clientName] ?: return false
        if (System.currentTimeMillis() < until) return true
        deadUntil.remove(client.clientName)
        return false
    }

    private fun markDead(client: YouTubeClient) {
        deadUntil[client.clientName] = System.currentTimeMillis() + DeadForMs
    }

    /** The client known to deliver whole files here, tried before the long list. */
    private val proven = YouTubeClient.VISIONOS

    /** Songs resolved a moment ago: replaying, or skipping back, shouldn't ask YouTube all over again. */
    private val cached = ConcurrentHashMap<String, Pair<Long, ResolvedStream>>()
    private const val CacheMs = 10 * 60_000L

    suspend fun resolve(videoId: String): Result<ResolvedStream> {
        cached[videoId]?.let { (at, stream) ->
            if (System.currentTimeMillis() - at < CacheMs) return Result.success(stream)
            cached.remove(videoId)
        }
        return resolveFresh(videoId).onSuccess { stream ->
            if (cached.size > 60) cached.clear()
            cached[videoId] = System.currentTimeMillis() to stream
        }
    }

    /** Drops a cached URL that mpv couldn't play, so the retry asks YouTube for a fresh one. */
    fun forget(videoId: String) {
        cached.remove(videoId)
    }

    /** Fills the cache ahead of time; the result is only wanted for the next song's sake. */
    suspend fun warm(videoId: String) {
        resolve(videoId)
    }

    private suspend fun resolveFresh(videoId: String): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        runCatching {
            val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
            val isLoggedIn = YouTube.cookie != null
            // Why each client/format fell through, so a failure says more than "no stream".
            val failures = mutableListOf<String>()
            val ordered = listOfNotNull(preferredClient(), lastWorkingClient, proven).plus(clients).distinct()

            for (client in ordered) {
                if (client.loginRequired && !isLoggedIn) continue
                if (isDead(client) && client != lastWorkingClient) continue
                val name = client.clientName

                val response = YouTube.player(videoId, null, client, signatureTimestamp)
                    .getOrElse { failures += "$name: ${it.message}"; continue }
                if (response.playabilityStatus.status != "OK") {
                    failures += "$name: ${response.playabilityStatus.status} ${response.playabilityStatus.reason.orEmpty()}"
                    if (response.playabilityStatus.status == "LOGIN_REQUIRED") markDead(client)
                    continue
                }

                val candidates = audioCandidates(response)
                if (candidates.isEmpty()) failures += "$name: no audio formats"

                val headers = headersFor(client)
                var refused = 0
                for (format in candidates.take(4)) {
                    val url = NewPipeUtils.getStreamUrl(format, videoId, client)
                        .getOrElse { failures += "$name/${format.itag}: ${it.message}"; continue }
                        .replace(Regex("cver=[^&]+"), "cver=${client.clientVersion}")
                    val status = probe(url, headers)
                    if (status in 200..399 || status == 416) {
                        lastWorkingClient = client
                        val loudness = response.playerConfig?.audioConfig?.let { it.loudnessDb ?: it.perceptualLoudnessDb }
                            ?: format.loudnessDb
                        return@runCatching ResolvedStream(playable(url, headers, client), headers, format.mimeType, format.bitrate, name, loudness, format.audioSampleRate)
                    }
                    failures += "$name/${format.itag}: HTTP $status"
                    // Two refusals in a row and the rest of this client's formats are the same wall.
                    if (status == 403 && ++refused >= 2) {
                        markDead(client)
                        break
                    }
                }
            }

            error("No playable stream for $videoId — ${failures.joinToString("; ")}")
        }
    }

    /**
     * Direct URLs first (deciphering can fail), then the codec the listener picked, then bitrate —
     * under the quality ceiling if there is one. Same ceilings as the Android app.
     */
    private fun audioCandidates(response: PlayerResponse): List<PlayerResponse.StreamingData.Format> {
        val settings = AppSettings.current
        val playable = response.streamingData?.adaptiveFormats.orEmpty()
            .filter { it.isAudio && it.bitrate > 0 }
            .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
        // YouTube also serves "stable volume" renditions with the dynamic range squashed. Same codec and
        // bitrate, flatter sound: only ever used when there's nothing else.
        val ordinary = playable.filterNot { it.isDynamicRangeCompressed() }.ifEmpty { playable }
        // Eclipsa Audio (IAMF) renditions need an IAMF renderer, which the audio engine doesn't have:
        // played as they are, only part of the mix would come out. The regular rendition is taken.
        val formats = ordinary.filterNot { ImmersiveAudio.isEclipsa(it.mimeType) }.ifEmpty { ordinary }
        val ceiling = when (settings.audioQuality) {
            "LOW" -> 70_000
            "HIGH" -> 256_000
            else -> null
        }
        val pool = ceiling?.let { cap -> formats.filter { it.bitrate <= cap }.ifEmpty { formats } } ?: formats
        // At Max, the best rendition goes first even when it needs deciphering (a Premium 256 kbps
        // Opus usually does); the loop in resolve() falls through to a direct URL if that fails.
        val bitrateFirst = settings.audioQuality == "MAX"
        return pool.sortedWith(
            compareByDescending<PlayerResponse.StreamingData.Format> {
                when (settings.audioCodec) {
                    "OPUS" -> "opus" in it.mimeType
                    "AAC" -> "mp4a" in it.mimeType
                    else -> false
                }
            }
                .thenByDescending { if (bitrateFirst) it.bitrate else 0 }
                // Opus over AAC at the same rate (Premium's 256 kbps pair, say): the newer codec holds
                // more of the music at any given bitrate, and it decodes through libopus here.
                .thenByDescending { settings.audioCodec == "AUTO" && "opus" in it.mimeType }
                .thenByDescending { it.url != null }
                .thenByDescending { it.bitrate },
        )
    }

    /**
     * The same song as a video: its picture and its sound, for "Switch to video".
     *
     * YouTube's only muxed rendition for music is 360p, so the good ones come as a video stream with no
     * sound and an audio stream of its own — mpv plays the pair together. The muxed one is the fallback
     * when a separate pair can't be had.
     */
    /** As far up as YouTube goes. A video that hasn't got it simply has nothing to match. */
    private const val MaxVideoHeight = 2160

    /** The listener's ceiling, or the highest there is. */
    private fun chosenHeight(): Int =
        AppSettings.current.videoQuality.toIntOrNull()?.coerceIn(144, MaxVideoHeight) ?: MaxVideoHeight

    suspend fun resolveVideo(videoId: String, maxHeight: Int = chosenHeight()): Result<ResolvedVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
            val isLoggedIn = YouTube.cookie != null
            val failures = mutableListOf<String>()
            // iOS first. Its URLs come ready to play, where WEB_REMIX's are scrambled and depend on
            // YouTube's player script parsing cleanly — which it currently doesn't — and ANDROID's are
            // withheld altogether unless a proof token comes with the request. The rest follow as the
            // fallback they always were.
            val ordered = (listOf(proven, YouTubeClient.IPADOS, YouTubeClient.IOS) + clients).distinct()
            var fallback: ResolvedVideo? = null
            var best: ResolvedVideo? = null
            var bestHeight = 0
            for (client in ordered) {
                if (client.loginRequired && !isLoggedIn) continue
                if (isDead(client) && client != proven) continue
                val name = client.clientName
                val response = YouTube.player(videoId, null, client, signatureTimestamp)
                    .getOrElse { failures += "$name: ${it.message}"; continue }
                if (response.playabilityStatus.status != "OK") {
                    failures += "$name: ${response.playabilityStatus.status}"
                    if (response.playabilityStatus.status == "LOGIN_REQUIRED") markDead(client)
                    continue
                }
                val streaming = response.streamingData ?: continue
                val headers = headersFor(client)

                val offered = streaming.adaptiveFormats
                    .filter { !it.isAudio }
                    .mapNotNull { it.height }
                    .filter { it > 0 }
                    .distinct()
                    .sortedDescending()
                val pictures = streaming.adaptiveFormats
                    .filter { !it.isAudio && (it.height ?: 0) in 1..maxHeight }
                    .sortedWith(
                        // A direct URL first of all: a 720p that plays beats a 1080p that can't be unscrambled.
                        compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                            .thenByDescending { it.height ?: 0 }
                            // H.264 over VP9 at the same size: every decoder on Windows takes it in hardware.
                            .thenByDescending { "avc1" in it.mimeType }
                            .thenByDescending { it.bitrate },
                    )
                val sounds = audioCandidates(response)
                for (picture in pictures.take(5)) {
                    val videoUrl = NewPipeUtils.getStreamUrl(picture, videoId, client)
                        .getOrElse { failures += "$name/${picture.itag}: ${it.message}"; continue }
                        .replace(Regex("cver=[^&]+"), "cver=${client.clientVersion}")
                    val status = probe(videoUrl, headers)
                    if (status !in 200..399 && status != 416) {
                        failures += "$name/${picture.itag}: HTTP $status"
                        continue
                    }
                    // The sound is the easy half: this client's own track if it has one, and otherwise
                    // whatever the ordinary audio path already knows plays. A picture is not worth
                    // throwing away over the track that goes with it.
                    var ownItag = 0
                    val own = sounds.firstNotNullOfOrNull { sound ->
                        NewPipeUtils.getStreamUrl(sound, videoId, client).getOrNull()
                            ?.replace(Regex("cver=[^&]+"), "cver=${client.clientVersion}")
                            ?.takeIf { probe(it, headers) in 200..399 }
                            ?.also { ownItag = sound.itag }
                    }
                    val borrowed = if (own != null) null else resolve(videoId).getOrNull()
                    val audioUrl = own ?: borrowed?.url
                    if (audioUrl == null) {
                        failures += "$name/${picture.itag}: picture without sound"
                        continue
                    }
                    val label = picture.qualityLabel ?: "${picture.height}p"
                    val found = ResolvedVideo(
                        videoUrl = playable(videoUrl, headers, client, reissue(videoId, picture.itag, client)),
                        audioUrl = audioUrl?.let {
                            // Only this client's own track can be asked for again by itag; a borrowed one
                            // came from somewhere else and is short enough not to need it.
                            playable(it, headers, client, if (own != null) reissue(videoId, ownItag, client) else null)
                        },
                        headers = headers.merge(borrowed?.headers),
                        quality = label,
                        clientName = name,
                        aspect = picture.shape(),
                        available = offered,
                    )
                    // The height asked for, or the nearest the video has under it. A client that could only
                    // give something lower doesn't end the search: the next one may have the real thing.
                    val wanted = offered.filter { it <= maxHeight }.maxOrNull() ?: 0
                    val height = picture.height ?: 0
                    if (height >= wanted) return@runCatching found
                    if (height > bestHeight) {
                        best = found
                        bestHeight = height
                    }
                    break
                }

                // Nothing adaptive worked from this client. Its one muxed rendition is kept back, not
                // played: another client further down may still have the real picture, and handing back
                // 360p from the first would leave that never asked.
                if (fallback == null && best == null) {
                    val muxed = streaming.formats.orEmpty().filter { (it.height ?: 0) > 0 }.maxByOrNull { it.height ?: 0 }
                    if (muxed != null) {
                        val url = NewPipeUtils.getStreamUrl(muxed, videoId, client).getOrNull()
                            ?.replace(Regex("cver=[^&]+"), "cver=${client.clientVersion}")
                        if (url != null && probe(url, headers) in 200..399) {
                            fallback = ResolvedVideo(
                                videoUrl = playable(url, headers, client, reissue(videoId, muxed.itag, client)),
                                audioUrl = null,
                                headers = headers,
                                quality = muxed.qualityLabel ?: "${muxed.height}p",
                                clientName = name,
                                aspect = muxed.shape(),
                                available = offered.ifEmpty { listOfNotNull(muxed.height) },
                            )
                        }
                    }
                }
            }
            best?.let { return@runCatching it }
            fallback?.let { println("Exhale video: only ${it.quality} muxed from ${it.clientName} — ${failures.takeLast(6).joinToString("; ")}"); return@runCatching it }
            error("No video for $videoId — ${failures.joinToString("; ")}")
        }
    }

    /** Whether this song is a video at all: YouTube marks a plain audio track ATV, a video OMV or UGC. */
    suspend fun hasVideo(videoId: String): Boolean {
        videoKnown[videoId]?.let { return it }
        val answer = withContext(Dispatchers.IO) {
            runCatching {
                val response = YouTube.player(videoId, null, YouTubeClient.WEB_REMIX, NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()).getOrNull()
                    ?: return@runCatching false
                if (response.playabilityStatus.status != "OK") return@runCatching false
                val type = response.videoDetails?.musicVideoType
                val pictures = response.streamingData?.adaptiveFormats.orEmpty().any { !it.isAudio }
                pictures && type != "MUSIC_VIDEO_TYPE_ATV"
            }.getOrDefault(false)
        }
        if (videoKnown.size > 200) videoKnown.clear()
        videoKnown[videoId] = answer
        return answer
    }

    private val videoKnown = ConcurrentHashMap<String, Boolean>()

    /** A format's width over its height, or the ordinary widescreen shape when it doesn't say. */
    private fun PlayerResponse.StreamingData.Format.shape(): Float {
        val w = width ?: return 16f / 9f
        val h = height ?: return 16f / 9f
        return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
    }

    /** The DRC variant is marked in its URL (xtags=drc=1), plain or inside the signature cipher. */
    private fun PlayerResponse.StreamingData.Format.isDynamicRangeCompressed(): Boolean =
        listOfNotNull(url, signatureCipher, cipher).any { "drc%3D1" in it || "drc%253D1" in it || "drc=1" in it }

    /** Native app clients stream without Origin/Referer; web and TV clients are refused without them. */
    /**
     * A URL mpv can actually open.
     *
     * The iOS client's URLs are served only in bounded pieces — ask for the whole thing, as every
     * ffmpeg-based player does, and the answer is 403. Those go through [MediaProxy], which does the
     * asking in the shape YouTube will answer. Everything else is handed over untouched.
     */
    private fun playable(
        url: String,
        headers: StreamHeaders,
        client: YouTubeClient,
        renew: (() -> String?)? = null,
    ): String =
        if (client.clientName.uppercase(Locale.US) in RangedClients) MediaProxy.serve(url, headers, renew) else url

    /**
     * How to be issued this same track again. A URL stops answering after enough of it has been read —
     * partway through a long video, or after a seek has walked through it twice — and the cure is a new
     * one rather than a smaller ask, so the proxy is given a way to get one.
     */
    private fun reissue(videoId: String, itag: Int, client: YouTubeClient): () -> String? = {
        runBlocking {
            runCatching {
                val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
                val response = YouTube.player(videoId, null, client, signatureTimestamp).getOrThrow()
                val streaming = response.streamingData
                val format = streaming?.adaptiveFormats?.firstOrNull { it.itag == itag }
                    ?: streaming?.formats?.firstOrNull { it.itag == itag }
                    ?: error("itag $itag is gone")
                NewPipeUtils.getStreamUrl(format, videoId, client).getOrThrow()
                    .replace(Regex("cver=[^&]+"), "cver=${client.clientVersion}")
            }.getOrNull()
        }
    }

    /** Clients whose media refuses an open-ended request. */
    private val RangedClients = setOf("IOS", "IOS_MUSIC")

    private fun headersFor(client: YouTubeClient): StreamHeaders {
        val name = client.clientName.uppercase(Locale.US)
        val webLike = name.startsWith("WEB") || name == "MWEB" || name.startsWith("TVHTML5")
        return StreamHeaders(
            userAgent = client.userAgent,
            origin = if (webLike) client.requestOrigin() else null,
            referer = if (webLike) client.requestReferer() else null,
        )
    }

    /**
     * HTTP status of a one-byte range request, or -1 if it never got an answer.
     *
     * The first byte proves nothing: the iOS client's URLs answer it and then refuse everything past
     * about a third of the file, so a song picked on that evidence plays a minute and stops. A byte
     * well into the stream is asked for as well; a client that can't serve it isn't playable.
     */
    private fun probe(url: String, headers: StreamHeaders): Int {
        val (first, total) = probeRange(url, headers, "bytes=0-0")
        if (first !in 200..399 && first != 416) return first
        val length = Regex("""[?&]clen=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: total ?: return first
        if (length < 1_000_000) return first
        val deep = length * 6 / 10
        return probeRange(url, headers, "bytes=$deep-$deep").first
    }

    /** Status of one range request, and the stream's total length if the answer says. */
    private fun probeRange(url: String, headers: StreamHeaders, range: String): Pair<Int, Long?> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", headers.userAgent)
            .header("Range", range)
            .apply {
                headers.origin?.let { header("Origin", it) }
                headers.referer?.let { header("Referer", it) }
            }
            .build()
        http().newCall(request).execute().use { it.code to it.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() }
    }.getOrDefault(-1 to null)
}
