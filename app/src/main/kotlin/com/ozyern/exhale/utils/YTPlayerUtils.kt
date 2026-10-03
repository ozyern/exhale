/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import com.ozyern.exhale.constants.AudioCodec
import com.ozyern.exhale.constants.AudioQuality
import com.ozyern.exhale.constants.PlayerStreamClient
import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.IOS
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.ozyern.exhale.innertube.models.response.PlayerResponse
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_TESTSUITE
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_UNPLUGGED
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.IPADOS
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.IOS_MUSIC
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.MOBILE
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.TVHTML5
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.VISIONOS
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.WEB
import com.ozyern.exhale.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L

    class LoginRequiredForPlaybackException(
        val videoId: String,
        val targetUrl: String,
        reason: String?,
    ) : IllegalStateException(reason)

    private data class PlaybackGateFailure(
        val clientName: String,
        val status: String,
        val reason: String?,
    )

    @Volatile private var streamClientPair: Pair<java.net.Proxy?, OkHttpClient>? = null

    private fun currentStreamClient(): OkHttpClient {
        val current = YouTube.streamProxy
        streamClientPair?.let { (proxy, client) ->
            if (proxy == current) return client
        }
        val client = OkHttpClient.Builder()
            .proxy(current)
            .build()
        streamClientPair = current to client
        return client
    }
    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.ozyern.exhale.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX
    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        IOS,
        MOBILE,
        ANDROID_MUSIC,
        IOS_MUSIC,
        ANDROID_VR_NO_AUTH,
        ANDROID_VR_1_61_48,
        ANDROID_VR_1_43_32,
        ANDROID_CREATOR,
        ANDROID_TESTSUITE,
        ANDROID_UNPLUGGED,
        IPADOS,
        VISIONOS,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        WEB,
        WEB_CREATOR,
        WEB_REMIX
    )
    private data class CachedStreamUrl(
        val url: String,
        val expiresAtMs: Long,
    )

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()

    fun invalidateCachedStreamUrls(videoId: String) {
        val prefix = "$videoId:"
        streamUrlCache.keys.removeIf { it.startsWith(prefix) }
    }

    fun markStreamClientFailed(videoId: String, clientKey: String?, httpStatusCode: Int?) {
        if (httpStatusCode != 403) return
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return
        failedStreamClientsUntil[buildFailedClientKey(videoId, normalizedClientKey)] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
    }

    fun markPreferredClientFailed(videoId: String, client: PlayerStreamClient, httpStatusCode: Int?) {
        markStreamClientFailed(videoId, client.name, httpStatusCode)
    }

    private fun isStreamClientTemporarilyBlocked(videoId: String, clientKey: String?): Boolean {
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return false
        val key = buildFailedClientKey(videoId, normalizedClientKey)
        val until = failedStreamClientsUntil[key] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove(key)
            return false
        }
        return true
    }

    class InvalidPlaybackLoginContextException(
        val videoId: String,
        val targetUrl: String,
        cause: Throwable,
    ) : IllegalStateException("Invalid YouTube Music playback login context", cause)

    private fun normalizeStreamClientKey(clientKey: String?): String {
        return clientKey?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.US).orEmpty()
    }

    private fun buildFailedClientKey(videoId: String, clientKey: String): String {
        return "$videoId:${normalizeStreamClientKey(clientKey)}"
    }

    /**
     * The bitrate at which MAX mode stops shopping around.
     *
     * MAX used to mean "the highest bitrate the **first client that answered** happened to
     * offer" — the loop broke on the first validated stream. That is not the same thing as
     * the highest available: the clients genuinely differ in which itags they are served.
     * ANDROID_VR typically tops out at Opus itag 251 (~160 kbps); TVHTML5 and IOS are the
     * ones that surface AAC itag 141 (~256 kbps) on a Premium account. Which client won the
     * race decided your fidelity.
     *
     * Now MAX keeps probing until a client clears this bar, then takes the best of
     * everything it saw. The bar is set deliberately above anything YouTube is known to
     * serve for music, which makes the practical behaviour "probe every client, keep the
     * best" — with an early exit already in place for the day a higher tier appears. It is a
     * stopping rule, not a promise: no client flag can conjure a bitrate the server does not
     * hold, and YouTube's music catalogue has no 500 kbps rendition today.
     */
    private const val MAX_MODE_TARGET_BITRATE_BPS = 500_000

    /** A stream that passed [validateStatus], kept so MAX mode can compare clients. */
    private data class ValidatedStream(
        val format: PlayerResponse.StreamingData.Format,
        val url: String,
        val expiresInSeconds: Int,
        val response: PlayerResponse,
        val clientName: String,
    )

    /**
     * What one client offered: its player response, the first usable candidate and its URL,
     * and whether that URL passed [validateStatus]. Pure data, so it can be worked out for every
     * client at once and then read by the selection loop in the same order as always.
     */
    private class ClientAttempt(
        val response: PlayerResponse?,
        val format: PlayerResponse.StreamingData.Format? = null,
        val url: String? = null,
        val valid: Boolean = false,
    )

    /**
     * How many clients MAX mode asks at the same time. All of them would be asked anyway, one after
     * another; this only caps how many are in flight together.
     */
    private const val PARALLEL_CLIENTS = 8

    /**
     * The best stream score MAX mode has chosen this session, signed in and not. What a client can
     * offer is set by the account and by YouTube's encodes, not by the song, so after the first
     * full probe this is the ceiling every later song is measured against.
     */
    private val bestEverScores = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun ceilingKey(isLoggedIn: Boolean, codec: AudioCodec) = "ytBestScore_${if (isLoggedIn) "in" else "out"}_${codec.name}"

    private val ceilingPrefs by lazy {
        runCatching { com.ozyern.exhale.App.instance.getSharedPreferences("stream_ceiling", android.content.Context.MODE_PRIVATE) }.getOrNull()
    }

    /** Kept across launches, so the first song after opening the app starts as fast as the rest. */
    private fun bestEverScore(key: String): Long =
        bestEverScores.getOrPut(key) { ceilingPrefs?.getLong(key, 0L) ?: 0L }

    private val resolveCount = java.util.concurrent.atomic.AtomicInteger()

    private fun recordBestScore(key: String, score: Long) {
        if (score > bestEverScore(key)) {
            bestEverScores[key] = score
            ceilingPrefs?.edit()?.putLong(key, score)?.apply()
        }
    }

    /**
     * How long MAX mode keeps waiting for the rest once one stream is known to work.
     *
     * Every client is asked at the same moment, so a client still silent this long after another
     * has already answered *and* been validated is, in practice, one that is stuck: timing out,
     * retrying, or rate-shaped. Waiting for it bought nothing but seconds of silence before the
     * song started. Answers that arrive inside the window are compared exactly as before.
     */
    private const val STRAGGLER_GRACE_MS = 1_200L

    /** How long a validated stream waits for the separate metadata answer before going without it. */
    private const val METADATA_GRACE_MS = 600L

    /** NewPipe's player-code cache is shared static state; deciphering goes through it one at a time. */
    private val decipherLock = Any()

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        // if provided, this preference overrides ConnectivityManager.isActiveNetworkMetered
        networkMetered: Boolean? = null,
        avoidCodecs: Set<String> = emptySet(),
        preferredCodec: AudioCodec = AudioCodec.AUTO,
    ): Result<PlaybackData> = runCatching {
        val attempts =
            when (audioQuality) {
                // Never silently downgrade HIGHEST/AUTO to HIGH — the fallback hid the real
                // stream-fetch error and capped fidelity without the user knowing.
                AudioQuality.HIGHEST -> listOf(AudioQuality.HIGHEST)
                AudioQuality.AUTO -> listOf(AudioQuality.AUTO)
                else -> listOf(audioQuality)
            }.distinct()

        var lastError: Throwable? = null
        for (attempt in attempts) {
            val attemptResult =
                runCatching {
                    playerResponseForPlaybackOnce(
                        videoId = videoId,
                        playlistId = playlistId,
                        audioQuality = attempt,
                        connectivityManager = connectivityManager,
                        preferredStreamClient = preferredStreamClient,
                        networkMetered = networkMetered,
                        avoidCodecs = avoidCodecs,
                        preferredCodec = preferredCodec,
                    )
                }
            if (attemptResult.isSuccess) return@runCatching attemptResult.getOrThrow()
            lastError = attemptResult.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("Failed to resolve stream")
    }

    private suspend fun playerResponseForPlaybackOnce(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
        avoidCodecs: Set<String>,
        preferredCodec: AudioCodec,
    ): PlaybackData {
        Timber.tag(logTag).i("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).v("Signature timestamp: $signatureTimestamp")

        val isLoggedIn = YouTube.cookie != null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        Timber.tag(logTag).v("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"} (sessionId=${sessionId.orEmpty()})")

        return coroutineScope {
            var format: PlayerResponse.StreamingData.Format? = null
            var streamUrl: String? = null
            var streamExpiresInSeconds: Int? = null
            var streamPlayerResponse: PlayerResponse? = null

            val orderedFallbackClients =
                (
                    if (isLoggedIn) {
                        STREAM_FALLBACK_CLIENTS.filter { it.loginSupported } + STREAM_FALLBACK_CLIENTS.filterNot { it.loginSupported }
                    } else {
                        STREAM_FALLBACK_CLIENTS.toList()
                    }
                    ).distinct()

            val preferredYouTubeClient =
                when (preferredStreamClient) {
                    PlayerStreamClient.ANDROID_VR -> ANDROID_VR_NO_AUTH
                    PlayerStreamClient.WEB_REMIX -> WEB_REMIX
                    PlayerStreamClient.IOS -> IOS
                    PlayerStreamClient.TVHTML5 -> TVHTML5
                    PlayerStreamClient.ANDROID_MUSIC -> ANDROID_MUSIC
                }

            val metadataClient =
                preferredYouTubeClient.takeIf { preferredStreamClient == PlayerStreamClient.ANDROID_VR } ?: MAIN_CLIENT

            Timber.tag(logTag).i("Fetching metadata response using client: ${metadataClient.clientName}")
            // Started, not awaited: it answers a different question from the stream clients (loudness,
            // duration, tracking) and none of them needs it except to reuse it, so it is in flight
            // alongside them rather than in front of them.
            val metadataRequest =
                async(Dispatchers.IO) {
                    // Null on failure, not a throw: an async that throws cancels this whole scope,
                    // which turned one client's bad answer into a song that would not play.
                    YouTube.player(videoId, playlistId, metadataClient, signatureTimestamp).getOrNull()
                }

            val streamClientsInOrder =
                buildList {
                    add(preferredYouTubeClient)
                    addAll(orderedFallbackClients)
                    if (preferredYouTubeClient != MAIN_CLIENT) add(MAIN_CLIENT)
                }.distinct()

            // In MAX mode we do not accept the first client that merely works — see
            // [MAX_MODE_TARGET_BITRATE_BPS]. This holds the best validated result seen so far
            // across every client probed, so the loop can keep looking for a better one.
            val probeForBest = audioQuality == AudioQuality.HIGHEST || audioQuality == AudioQuality.AUTO

            // MAX mode asks every client anyway, and it used to ask them one after another: a player
            // request, a decipher and a validation round trip each, the song waiting through the sum
            // of all of them. Nothing one client does depends on another, so in MAX mode they are all
            // asked at once, and the loop below reads their answers in the same order and picks with
            // the same rule — the same stream as before, in the time of the slowest single answer
            // instead of the total. Other modes stop at the first working client, so they still ask
            // lazily, one at a time.
            val parallel = Semaphore(PARALLEL_CLIENTS)
            // Completes when the first client's stream validates; the grace timer runs from there.
            val firstValid = CompletableDeferred<Unit>()
            val stragglersDue: Deferred<Unit>? = null
            val attempts: Map<YouTubeClient, Deferred<ClientAttempt>> =
                if (!probeForBest) {
                    emptyMap()
                } else {
                    streamClientsInOrder
                        .filterNot { isStreamClientTemporarilyBlocked(videoId, it.clientName) }
                        .filterNot { it != MAIN_CLIENT && it.loginRequired && !isLoggedIn }
                        .associateWith { client ->
                            async(Dispatchers.IO) {
                                parallel.withPermit {
                                    attemptClient(
                                        client = client,
                                        metadataClient = metadataClient,
                                        metadataRequest = metadataRequest,
                                        videoId = videoId,
                                        playlistId = playlistId,
                                        signatureTimestamp = signatureTimestamp,
                                        audioQuality = audioQuality,
                                        isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered,
                                        isLoggedIn = isLoggedIn,
                                        avoidCodecs = avoidCodecs,
                                        preferredCodec = preferredCodec,
                                    ).also { if (it.valid) firstValid.complete(Unit) }
                                }
                            }
                        }
                }

            // MAX mode: take the clients' answers in the order they arrive, not the order they are
            // ranked in, and stop waiting the moment one of them is as good as anything this
            // account has ever been given — or a short grace after the first working stream.
            // Reading them in rank order meant the fastest answer sat unread behind the slowest.
            val ceilingName = ceilingKey(isLoggedIn, preferredCodec)
            if (probeForBest && attempts.isNotEmpty()) {
                // Every tenth song still hears every client out, so a better stream the account
                // has since become entitled to (a new subscription, say) raises the ceiling.
                val ceiling = if (resolveCount.incrementAndGet() % 10 == 0) 0L else bestEverScore(ceilingName)
                val pending = attempts.values.toMutableSet()
                var firstValidAt = 0L
                while (pending.isNotEmpty()) {
                    val done = attempts.values.filter { it.isCompleted && !it.isCancelled }
                        .mapNotNull { runCatching { it.getCompleted() }.getOrNull() }
                        .filter { it.valid && it.format != null }
                    if (done.isNotEmpty() && firstValidAt == 0L) firstValidAt = System.currentTimeMillis()
                    if (ceiling > 0L && done.any { formatScore(it.format!!, preferredCodec) >= ceiling }) break
                    val graceLeft = if (firstValidAt == 0L) Long.MAX_VALUE
                    else STRAGGLER_GRACE_MS - (System.currentTimeMillis() - firstValidAt)
                    if (graceLeft <= 0L) break
                    select<Unit> {
                        pending.forEach { deferred -> deferred.onAwait { pending.remove(deferred) } }
                        if (graceLeft != Long.MAX_VALUE) onTimeout(graceLeft) {}
                    }
                }
            }

            // Loudness, length and tracking: worth having, never worth holding a ready stream for.
            // A metadata answer that fails or lags past a short grace no longer fails or stalls the
            // song; whatever the stream client itself said stands in for it below.
            val haveStream = attempts.values.any { it.isCompleted && !it.isCancelled &&
                runCatching { it.getCompleted().valid }.getOrDefault(false) }
            val metadataPlayerResponse: PlayerResponse? =
                if (haveStream) {
                    kotlinx.coroutines.withTimeoutOrNull(METADATA_GRACE_MS) {
                        runCatching { metadataRequest.await() }.getOrNull()
                    }
                } else {
                    runCatching { metadataRequest.await() }.getOrNull()
                }
            val fallbackMetadata = attempts.values.firstNotNullOfOrNull { deferred ->
                if (deferred.isCompleted && !deferred.isCancelled) {
                    runCatching { deferred.getCompleted().response }.getOrNull()
                        ?.takeIf { it.playabilityStatus.status == "OK" }
                } else null
            }
            val audioConfig = (metadataPlayerResponse ?: fallbackMetadata)?.playerConfig?.audioConfig
            val videoDetails = (metadataPlayerResponse ?: fallbackMetadata)?.videoDetails
            val playbackTracking = (metadataPlayerResponse ?: fallbackMetadata)?.playbackTracking

            val streamClients =
                streamClientsInOrder.filterNot { client ->
                    val blocked = isStreamClientTemporarilyBlocked(videoId, client.clientName)
                    if (blocked) {
                        Timber.tag(logTag).w("Temporarily blocked stream client for $videoId: ${client.clientName}")
                    }
                    blocked
                }

            val botDetectedClients = mutableSetOf<String>()
            var gateFailure: PlaybackGateFailure? = null

            var best: ValidatedStream? = null

            for ((index, client) in streamClients.withIndex()) {
                format = null
                streamUrl = null
                streamExpiresInSeconds = null
                streamPlayerResponse = null

                Timber.tag(logTag).v(
                    "Trying ${if (client == MAIN_CLIENT) "MAIN_CLIENT" else "fallback client"} ${index + 1}/${streamClients.size}: ${client.clientName}"
                )

                if (client != MAIN_CLIENT && client.loginRequired && !isLoggedIn) {
                    Timber.tag(logTag).w("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                val pending = attempts[client]
                val attempt: ClientAttempt? =
                    if (pending != null) {
                        // MAX mode has already waited as long as it is going to; what is in is in.
                        if (pending.isCompleted && !pending.isCancelled) runCatching { pending.getCompleted() }.getOrNull() else null
                    } else {
                        attemptClient(
                            client = client,
                            metadataClient = metadataClient,
                            metadataRequest = metadataRequest,
                            videoId = videoId,
                            playlistId = playlistId,
                            signatureTimestamp = signatureTimestamp,
                            audioQuality = audioQuality,
                            isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered,
                            isLoggedIn = isLoggedIn,
                            avoidCodecs = avoidCodecs,
                            preferredCodec = preferredCodec,
                        )
                    }
                if (attempt == null) {
                    Timber.tag(logTag).i(
                        "${client.clientName} still answering ${STRAGGLER_GRACE_MS}ms after a stream validated; not waiting for it"
                    )
                    continue
                }
                streamPlayerResponse = attempt.response

                if (streamPlayerResponse == null) continue

                if (streamPlayerResponse.playabilityStatus.status != "OK") {
                    val reason = streamPlayerResponse.playabilityStatus.reason.orEmpty()
                    val isLoginRecovery = isLoginRecoveryError(reason)
                    val isBotDetection = isBotDetectionError(reason)
                    Timber.tag(logTag).w(
                        "Player response status not OK: ${streamPlayerResponse.playabilityStatus.status}, reason: $reason, loginRecovery: $isLoginRecovery, botDetection: $isBotDetection"
                    )
                    if (isLoginRecovery) {
                        gateFailure = PlaybackGateFailure(
                            clientName = client.clientName,
                            status = streamPlayerResponse.playabilityStatus.status,
                            reason = streamPlayerResponse.playabilityStatus.reason,
                        )
                    } else if (isBotDetection) {
                        botDetectedClients.add(client.clientName)
                    }
                    continue
                }

                val selectedFormat = attempt.format
                val selectedUrl = attempt.url
                if (selectedFormat == null || selectedUrl == null) continue

                format = selectedFormat
                streamUrl = selectedUrl
                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds

                if (streamExpiresInSeconds == null) continue

                Timber.tag(logTag).i("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")
                Timber.tag(logTag).v("Stream expires in: $streamExpiresInSeconds seconds")

                val valid = attempt.valid
                if (valid) {
                    val validated = ValidatedStream(
                        format = selectedFormat,
                        url = selectedUrl,
                        expiresInSeconds = streamExpiresInSeconds,
                        response = streamPlayerResponse,
                        clientName = client.clientName,
                    )
                    Timber.tag(logTag).i(
                        "Stream validated successfully with client: ${client.clientName} @ ${validated.format.bitrate}bps"
                    )

                    if (!probeForBest) break

                    // MAX mode: keep the better of what we had and what this client offered.
                    val incumbent = best
                    if (incumbent == null ||
                        formatScore(validated.format, preferredCodec) >
                        formatScore(incumbent.format, preferredCodec)
                    ) {
                        best = validated
                    }

                    val bestSoFar = best!!
                    // The best stream this account has been given all session: once a song's
                    // stream is as good as that, no client still answering can beat it, so there
                    // is nothing left to wait for. The same stream MAX mode would have picked,
                    // without the grace period on every single play.
                    val ceiling = bestEverScore(ceilingName)
                    if (ceiling > 0L && formatScore(bestSoFar.format, preferredCodec) >= ceiling) {
                        Timber.tag(logTag).i(
                            "${bestSoFar.clientName} matches the best stream this account gets " +
                                "(${bestSoFar.format.bitrate}bps); stopping the probe"
                        )
                        break
                    }
                    if (bestSoFar.format.bitrate >= MAX_MODE_TARGET_BITRATE_BPS) {
                        Timber.tag(logTag).i(
                            "Reached the MAX-mode bitrate target with ${bestSoFar.clientName} " +
                                "(${bestSoFar.format.bitrate}bps); stopping the probe"
                        )
                        break
                    }

                    Timber.tag(logTag).i(
                        "${client.clientName} tops out at ${validated.format.bitrate}bps, below the " +
                            "${MAX_MODE_TARGET_BITRATE_BPS}bps target — probing the next client for a better stream"
                    )
                    format = null
                    streamUrl = null
                    streamExpiresInSeconds = null
                    streamPlayerResponse = null
                    continue
                }

                Timber.tag(logTag).w("Stream validation failed with client: ${client.clientName}, trying next fallback")
                format = null
                streamUrl = null
                streamExpiresInSeconds = null
                streamPlayerResponse = null
            }

            // The loop only stops early when a stream clears the MAX target; whatever else was still
            // being asked is no longer wanted.
            attempts.values.forEach { it.cancel() }
            // Never left running: with no stream validated it would wait forever, and
            // coroutineScope does not return while a child is still going.
            stragglersDue?.cancel()

            // Fall back to the best stream the probe found. Non-null only in MAX mode, and only
            // when no single client cleared the target on its own.
            best?.let { winner ->
                val current = format
                if (current == null ||
                    formatScore(winner.format, preferredCodec) > formatScore(current, preferredCodec)
                ) {
                    Timber.tag(logTag).i(
                        "Best available across all clients: ${winner.clientName} @ ${winner.format.bitrate}bps"
                    )
                    format = winner.format
                    streamUrl = winner.url
                    streamExpiresInSeconds = winner.expiresInSeconds
                    streamPlayerResponse = winner.response
                }
            }

            if (streamPlayerResponse == null) {
                gateFailure?.let { failure ->
                    Timber.tag(logTag).w(
                        "Playback requires login recovery for $videoId via ${failure.clientName} (${failure.status}): ${failure.reason.orEmpty()}"
                    )
                    throw LoginRequiredForPlaybackException(
                        videoId = videoId,
                        targetUrl = "https://music.youtube.com/watch?v=$videoId",
                        reason = failure.reason,
                    )
                }
                if (botDetectedClients.isNotEmpty()) {
                    Timber.tag(logTag).e("Bot detection triggered on clients: $botDetectedClients - all clients failed")
                    throw PlaybackException(
                        "Sign in to confirm you're not a bot",
                        null,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
                Timber.tag(logTag).e("Bad stream player response - all clients failed")
                throw Exception("Bad stream player response")
            }

            if (streamPlayerResponse.playabilityStatus.status != "OK") {
                val errorReason = streamPlayerResponse.playabilityStatus.reason
                if (isLoginRecoveryError(errorReason.orEmpty())) {
                    Timber.tag(logTag).w("Playback requires login recovery for $videoId: $errorReason")
                    throw LoginRequiredForPlaybackException(
                        videoId = videoId,
                        targetUrl = "https://music.youtube.com/watch?v=$videoId",
                        reason = errorReason,
                    )
                }
                Timber.tag(logTag).e("Playability status not OK: $errorReason")
                throw PlaybackException(
                    errorReason,
                    null,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR
                )
            }

            if (streamExpiresInSeconds == null) {
                Timber.tag(logTag).e("Missing stream expire time")
                throw Exception("Missing stream expire time")
            }

            if (format == null) {
                Timber.tag(logTag).e("Could not find suitable format for quality: $audioQuality. Available formats from last client: ${streamPlayerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio }?.map { "${it.mimeType} @ ${it.bitrate}bps (itag: ${it.itag})" }}")
                throw Exception("Could not find format for quality: $audioQuality")
            }

            if (streamUrl == null) {
                Timber.tag(logTag).e("Could not find stream url for format: ${format.mimeType}, itag: ${format.itag}")
                throw Exception("Could not find stream url")
            }

            Timber.tag(logTag).i("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
            if (probeForBest) recordBestScore(ceilingName, formatScore(format, preferredCodec))

            streamUrlCache[buildCacheKey(videoId, format.itag)] =
                CachedStreamUrl(
                    url = streamUrl,
                    expiresAtMs = System.currentTimeMillis() + (streamExpiresInSeconds * 1000L),
                )

            PlaybackData(
                audioConfig,
                videoDetails,
                playbackTracking,
                format,
                streamUrl,
                streamExpiresInSeconds,
            )
        }
    }
    /**
     * One client's part of [playerResponseForPlaybackOnce]: its player response, the first usable
     * candidate and URL, and whether that URL validates. Exactly what the loop used to do inline for
     * each client, minus the decision — which stays in the loop, so the choice between clients is
     * unchanged whether this runs lazily or for all of them at once.
     */
    private suspend fun attemptClient(
        client: YouTubeClient,
        metadataClient: YouTubeClient,
        metadataRequest: Deferred<PlayerResponse?>,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        audioQuality: AudioQuality,
        isMetered: Boolean,
        isLoggedIn: Boolean,
        avoidCodecs: Set<String>,
        preferredCodec: AudioCodec,
    ): ClientAttempt {
        val response =
            if (client == metadataClient) {
                metadataRequest.await()
            } else {
                Timber.tag(logTag).i("Fetching player response for fallback client: ${client.clientName}")
                YouTube.player(videoId, playlistId, client, signatureTimestamp).getOrNull()
            } ?: return ClientAttempt(response = null)

        if (response.playabilityStatus.status != "OK") return ClientAttempt(response)

        val candidates =
            selectAudioFormatCandidates(
                response,
                audioQuality,
                isMetered,
                avoidCodecs = avoidCodecs,
                preferredCodec = preferredCodec,
            )
        if (candidates.isEmpty()) return ClientAttempt(response)

        // Only a logged-in session gets served previews, so only then is the real length needed.
        val expectedDurationMs =
            if (isLoggedIn) {
                // The client's own answer carries the length too; waiting on the separate metadata
                // request here held every client's validation behind the slowest of them.
                (response.videoDetails?.lengthSeconds?.toLongOrNull()?.takeIf { it > 0 }
                    ?: runCatching { metadataRequest.await() }.getOrNull()
                        ?.videoDetails?.lengthSeconds?.toLongOrNull()?.takeIf { it > 0 })
                    ?.times(1000L)
            } else {
                null
            }

        var selectedFormat: PlayerResponse.StreamingData.Format? = null
        var selectedUrl: String? = null

        for (candidate in candidates.asSequence().take(6)) {
            if (isLoggedIn && expectedDurationMs != null && isLikelyPreview(candidate, expectedDurationMs)) continue
            if (shouldSkipCipheredWebCandidate(client, candidate)) continue
            val cacheKey = buildCacheKey(videoId, candidate.itag)
            val cached = streamUrlCache[cacheKey]
            val candidateUrl =
                if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
                    cached.url
                } else {
                    synchronized(decipherLock) { findUrlOrNull(candidate, videoId, client) }
                } ?: continue
            selectedFormat = candidate
            selectedUrl = candidateUrl
            break
        }

        if (selectedFormat == null || selectedUrl == null) return ClientAttempt(response)

        // Validated only when the loop would have validated it: a response with no expiry is
        // skipped there before it gets that far.
        val valid =
            response.streamingData?.expiresInSeconds != null &&
                validateStatus(selectedUrl, client.userAgent)

        return ClientAttempt(response, selectedFormat, selectedUrl, valid)
    }

    /** A picture-only stream for the player's video mode, and the user agent it must be fetched with. */
    data class VideoStream(val url: String, val userAgent: String, val width: Int, val height: Int, val contentLength: Long?)

    /**
     * The best video-only stream of [videoId] up to [maxHeight], for showing the music video in
     * the player while the audio keeps playing from its own stream.
     *
     * H.264 in MP4 first: every phone decodes it in hardware, where VP9 and AV1 are a lottery on
     * mid-range chips and a software decode of 720p video next to the audio pipeline is exactly
     * the stutter this must not cause. Walks the same clients as audio playback, in the same order.
     */
    suspend fun videoStreamForDisplay(videoId: String, maxHeight: Int = 720): Result<VideoStream> = runCatching {
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val clients = listOf(MAIN_CLIENT) + STREAM_FALLBACK_CLIENTS.toList()
        for (client in clients) {
            val response = YouTube.player(videoId, null, client, signatureTimestamp).getOrNull() ?: continue
            if (response.playabilityStatus.status != "OK") continue
            // Adaptive video first; the single-file 360p stream as a last resort, which plays where
            // a picture-only stream is refused.
            val candidates = (response.streamingData?.adaptiveFormats.orEmpty() + response.streamingData?.formats.orEmpty())
                .filter { !it.isAudio && (it.height ?: 0) in 1..maxHeight }
                .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
                .sortedWith(
                    compareByDescending<PlayerResponse.StreamingData.Format> { it.mimeType.startsWith("video/mp4") && "avc1" in it.mimeType }
                        .thenByDescending { it.height ?: 0 }
                        .thenByDescending { it.fps ?: 0 },
                )
            for (candidate in candidates.take(4)) {
                val url = synchronized(decipherLock) { findUrlOrNull(candidate, videoId, client) } ?: continue
                return@runCatching VideoStream(url, client.userAgent, candidate.width ?: 0, candidate.height ?: 0, candidate.contentLength)
            }
        }
        error("No video stream for $videoId")
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).i("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = MAIN_CLIENT)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        // optional override from user preference; if non-null, use this instead of ConnectivityManager
        networkMetered: Boolean? = null,
        avoidCodecs: Set<String> = emptySet(),
        preferredCodec: AudioCodec = AudioCodec.AUTO,
    ): PlayerResponse.StreamingData.Format? {
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
        return selectAudioFormatCandidates(
            playerResponse,
            audioQuality,
            isMetered,
            avoidCodecs = avoidCodecs,
            preferredCodec = preferredCodec,
        ).firstOrNull()
    }

    private fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
        avoidCodecs: Set<String> = emptySet(),
        preferredCodec: AudioCodec = AudioCodec.AUTO,
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).i("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats =
            playerResponse.streamingData?.adaptiveFormats
                ?.asSequence()
                ?.filter { it.isAudio && it.bitrate > 0 }
                ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
                ?.filter { format ->
                    val codec = extractCodec(format.mimeType)?.lowercase()
                    codec == null || codec !in avoidCodecs
                }
                ?.toList()
                .orEmpty()

        if (audioFormats.isEmpty()) return emptyList()

        val effectiveQuality =
            when (audioQuality) {
                // Never downgrade on metered networks — the user chose HIGHEST/AUTO and the
                // track selector already enforces bandwidth limits at the ExoPlayer layer.
                AudioQuality.AUTO -> AudioQuality.HIGHEST
                else -> audioQuality
            }

        // A *ceiling*, not a target. Below, `belowOrEqual` filters the candidate list to formats
        // at or under this value, so any finite number here caps fidelity. HIGHEST used to sit at
        // 320_000, which sounds unbounded but isn't: it silently discarded any format above it.
        // null means "no ceiling at all" — sort every candidate by descending bitrate and take
        // the best the server offers.
        val targetBitrateBps =
            when (effectiveQuality) {
                AudioQuality.LOW -> 70_000
                // Raised from 160_000: YouTube Music's adaptive AAC stream peaks at 256 kbps,
                // so the old ceiling silently discarded the best available track.
                AudioQuality.HIGH -> 256_000
                AudioQuality.HIGHEST -> null
                AudioQuality.AUTO -> null
            }

        // Codec preference sits above bitrate: picking AAC has to mean AAC even when the Opus
        // rendition is the fatter one, which on a free account it always is. It sits *below*
        // `url != null` because a direct URL still beats a ciphered one -- deciphering can fail,
        // and a codec preference is not worth a failed stream. With AudioCodec.AUTO the term is
        // constant and the order collapses back to pure bitrate.
        val preferHigher =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { codecPriority(it, preferredCodec) }
                .thenByDescending { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { codecPriority(it, preferredCodec) }
                .thenBy { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates =
            if (targetBitrateBps == null) {
                audioFormats.sortedWith(preferHigher)
            } else {
                val belowOrEqual = audioFormats.filter { it.bitrate <= targetBitrateBps }
                if (belowOrEqual.isNotEmpty()) {
                    belowOrEqual.sortedWith(preferHigher)
                } else {
                    val aboveOrEqual = audioFormats.filter { it.bitrate >= targetBitrateBps }
                    if (aboveOrEqual.isNotEmpty()) aboveOrEqual.sortedWith(preferLowerAboveTarget)
                    else audioFormats.sortedWith(preferHigher)
                }
            }

        Timber.tag(logTag)
            .v(
                "Available audio formats: ${
                    candidates.take(12).map {
                        val codec = extractCodec(it.mimeType)
                        val direct = if (it.url != null) "direct" else "cipher"
                        "${it.mimeType} ($direct, codec=${codec ?: "unknown"}) @ ${it.bitrate}bps"
                    }
                }"
            )

        return candidates
    }

    private fun extractCodec(mimeType: String): String? {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType) ?: return null
        return match.groupValues.getOrNull(1)?.split(",")?.firstOrNull()?.trim()
    }

    private fun isCipheredFormat(format: PlayerResponse.StreamingData.Format): Boolean {
        return format.url == null && (format.signatureCipher != null || format.cipher != null)
    }

    private fun shouldSkipCipheredWebCandidate(
        client: YouTubeClient,
        format: PlayerResponse.StreamingData.Format,
    ): Boolean {
        if (!YouTube.webClientPoTokenEnabled) return false
        if (!StreamClientUtils.isWebClient(client.clientName)) return false
        if (!isCipheredFormat(format)) return false

        Timber.tag(logTag).w(
            "Skipping ciphered ${client.clientName} stream candidate while Web PoToken is enabled; using a direct URL or fallback client instead"
        )
        return true
    }

    /**
     * 1 when this format is the codec the user asked for, 0 otherwise.
     *
     * Deliberately binary rather than a ranked list. The preference answers one question --
     * "is this the stream I wanted?" -- and everything past that is decided on bitrate, so a
     * user who picks AAC and is served only Opus still gets the best Opus available instead of
     * an arbitrary reshuffle.
     */
    private fun codecPriority(
        format: PlayerResponse.StreamingData.Format,
        preferred: AudioCodec,
    ): Int {
        if (preferred == AudioCodec.AUTO) return 0
        val codec = extractCodec(format.mimeType)?.lowercase() ?: return 0
        return when (preferred) {
            AudioCodec.AAC -> if (codec.contains("mp4a")) 1 else 0
            AudioCodec.OPUS -> if (codec.contains("opus")) 1 else 0
            AudioCodec.AUTO -> 0
        }
    }

    /**
     * The value MAX mode maximises when comparing what different clients offered.
     *
     * It has to agree with the comparator used *within* a client, or the two fight: the
     * selector would hand back the preferred codec from client A, and then the probe would
     * throw it away because client B answered with a higher-bitrate stream in the codec the
     * user asked not to have. Codec preference is the high-order term for exactly that reason;
     * bitrate breaks ties beneath it. With [AudioCodec.AUTO] the first term is always 0 and
     * this degenerates to a plain bitrate comparison, which is the old behaviour.
     */
    private fun formatScore(
        format: PlayerResponse.StreamingData.Format,
        preferred: AudioCodec,
    ): Long = codecPriority(format, preferred) * 100_000_000L + format.bitrate.toLong()

    private fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            // AAC (mp4a) ranks above Opus: YouTube Music's peak stream is 256 kbps AAC, and at
            // equal bitrate it carries more detail than the Opus encode at the same rate.
            codec.contains("mp4a", ignoreCase = true) -> 3
            codec.contains("opus", ignoreCase = true) -> 2
            else -> 1
        }
    private fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approx = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approx in 1L..(minOf(90_000L, (expectedDurationMs * 9L) / 10L))
    }
    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String, userAgent: String): Boolean {
        Timber.tag(logTag).v("Validating stream URL status")
        try {
            val httpUrl = url.toHttpUrlOrNull()
            val clientParam = httpUrl?.queryParameter("c")?.trim().orEmpty()

            val resolvedUserAgent = StreamClientUtils.resolveUserAgent(clientParam).ifEmpty { userAgent }
            val originReferer = StreamClientUtils.resolveOriginReferer(clientParam)

            val probeRanges =
                if (StreamClientUtils.isWebClient(clientParam)) {
                    listOf("bytes=0-0", "bytes=262144-262145", "bytes=1048576-1048577")
                } else {
                    listOf("bytes=0-0")
                }

            for (range in probeRanges) {
                val rangeRequest =
                    okhttp3.Request.Builder()
                        .get()
                        .header("User-Agent", resolvedUserAgent)
                        .header("Range", range)
                        .apply {
                            originReferer.origin?.let { header("Origin", it) }
                            originReferer.referer?.let { header("Referer", it) }
                        }.url(url)
                        .build()

                val code = currentStreamClient().newCall(rangeRequest).execute().use { response -> response.code }
                if (code == 403) return false
                if (code !in 200..399 && code != 416) return false
            }

            return true
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }
    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        Timber.tag(logTag).i("Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).i("Signature timestamp obtained: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions.
     * Also patches cver to match the client version.
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
    ): String? {
        Timber.tag(logTag).i("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        var url = NewPipeUtils.getStreamUrl(format, videoId, client)
            .onSuccess { Timber.tag(logTag).i("Stream URL obtained successfully") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get stream URL")
                reportException(it)
            }
            .getOrNull() ?: return null

        // Patch cver in the URL to match the client we actually used
        if (client != null) {
            url = StreamClientUtils.patchClientVersion(url, client.clientVersion)
        }

        return url
    }

    private fun buildCacheKey(videoId: String, itag: Int): String {
        return "$videoId:$itag"
    }

    private fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            "confirm" in lower && "not a" in lower ||
            "not a robot" in lower ||
            "verify" in lower && "human" in lower
    }

    private fun isLoginRecoveryError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "confirm your age" in lower ||
            "age-restricted" in lower ||
            "age restricted" in lower ||
            "inappropriate for some users" in lower ||
            "mature audiences" in lower ||
            "adult" in lower && "sign in" in lower ||
            "allow" in lower && "youtube music" in lower
    }

    fun isBotDetectionException(error: PlaybackException): Boolean {
        val message = error.message.orEmpty()
        if (isBotDetectionError(message)) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (isBotDetectionError(cause.message.orEmpty())) return true
            cause = cause.cause
        }
        return false
    }
}
