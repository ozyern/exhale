/** GPL-3.0. Third-party portions: see NOTICE. */
package com.ozyern.exhale.bestpart

import android.content.Context
import android.util.Log
import com.music.innertube.models.YouTubeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Songs' best parts ([BestPart]) for the player's Best Part button: YouTube's "Most replayed"
 * curves fetched and kept on disk, and the starts worked out from them.
 *
 * Only the curves are kept, never the starts: a start also depends on the lyrics, which can
 * change (a better source, a re-sync), so it is worked out again from the curve each time. A
 * curve keeps for a month, "this video has no curve" for a week, and a failed request nowhere,
 * since offline is not an answer about the video.
 *
 * Where the reference design asks its own lyrics sources, Exhale passes in whatever synced lyrics the player
 * already has, so finding a best part never sends a lyrics request of its own.
 */
object BestParts {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(HEATMAP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private val lock = Mutex()
    private var file: File? = null
    private val stored = ConcurrentHashMap<String, StoredHeat>()

    /** Where [videoId]'s best part starts, or null when nothing but a guess could place it. */
    suspend fun startMs(
        context: Context,
        videoId: String,
        durationMs: Long,
        lines: List<LyricLine>,
    ): Long? {
        ensureLoaded(context)
        val heat = heatmapFor(videoId)
        val duration = durationMs.takeIf { it > 0 } ?: heat?.durationMs ?: 0L
        val result = BestPart.pick(heat, lines, duration)
        // The player offers the button only when something real placed it.
        return result.takeIf { it.source != BestPartStart.Source.FALLBACK }?.startMs
    }

    /** Whether [id] is a YouTube video id rather than a local file or another source's key. */
    fun isYouTubeId(id: String): Boolean = YOUTUBE_ID.matches(id)

    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

    // ── The curve ───────────────────────────────────────────────────────────

    private suspend fun heatmapFor(id: String): Heatmap? {
        if (!isYouTubeId(id)) return null
        val now = System.currentTimeMillis()
        stored[id]?.let { kept ->
            val heat = kept.heatmap()
            val fresh = now - kept.fetchedAt < if (heat != null) MONTH_MS else WEEK_MS
            if (fresh) return heat
        }
        val answer = withTimeoutOrNull(HEATMAP_TIMEOUT_MS) {
            try {
                webNext(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "no heatmap answer for $id: ${e.message}")
                null
            }
        } ?: return stored[id]?.heatmap() // Stale beats nothing when the network won't answer.
        val heat = BestPart.parseHeatmap(answer)
        stored[id] = StoredHeat(heat?.segmentMs ?: 0L, heat?.intensity.orEmpty(), now)
        save()
        return heat
    }

    /**
     * youtube.com's own `next` answer for [videoId]. The heat markers are only in the WEB
     * client's answer, not YouTube Music's, and no account is needed for them.
     */
    private suspend fun webNext(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val client = YouTubeClient.WEB
        val locale = Locale.getDefault()
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.clientName)
                    put("clientVersion", client.clientVersion)
                    put("hl", locale.toLanguageTag())
                    locale.country.takeIf { it.isNotBlank() }?.let { put("gl", it) }
                }
            }
            put("videoId", videoId)
        }.toString()
        val request = Request.Builder()
            .url("$YOUTUBE_ORIGIN/youtubei/v1/next?prettyPrint=false")
            .header("User-Agent", client.userAgent)
            .header("Origin", YOUTUBE_ORIGIN)
            .header("Referer", "$YOUTUBE_ORIGIN/")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    // ── Disk ────────────────────────────────────────────────────────────────

    private suspend fun ensureLoaded(context: Context) = lock.withLock {
        if (file != null) return@withLock
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        withContext(Dispatchers.IO) {
            if (!target.exists()) return@withContext
            runCatching { json.decodeFromString(Stored.serializer(), target.readText()) }
                .onSuccess { kept -> stored.putAll(kept.heats) }
                .onFailure {
                    Log.w(TAG, "Discarding unreadable heatmap cache", it)
                    target.delete()
                }
        }
    }

    private suspend fun save() = lock.withLock {
        val target = file ?: return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                val kept = stored.entries
                    .sortedByDescending { it.value.fetchedAt }
                    .take(MAX_KEPT)
                    .associate { it.key to it.value }
                target.writeText(json.encodeToString(Stored.serializer(), Stored(heats = kept)))
            }.onFailure { Log.w(TAG, "Could not write heatmap cache", it) }
        }
    }

    @Serializable
    private data class Stored(val version: Int = 1, val heats: Map<String, StoredHeat> = emptyMap())

    /** A curve as fetched. An empty [intensity] means the video has none. */
    @Serializable
    private data class StoredHeat(val segmentMs: Long, val intensity: List<Float>, val fetchedAt: Long) {
        fun heatmap(): Heatmap? = if (segmentMs > 0 && intensity.isNotEmpty()) Heatmap(segmentMs, intensity) else null
    }

    private const val TAG = "ExhaleBestPart"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    private const val FILE_NAME = "best_part_heatmaps.json"
    private const val MAX_KEPT = 500
    private const val HEATMAP_TIMEOUT_MS = 8_000L
    private const val MONTH_MS = 30L * 24 * 3600 * 1000
    private const val WEEK_MS = 7L * 24 * 3600 * 1000
}
