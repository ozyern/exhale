/** GPL-3.0. Third-party portions: see NOTICE. */
package com.ozyern.exhale.credits

import com.music.innertube.models.YouTubeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One line of a song's credits: a role and the people in it. */
data class SongCredit(val role: String, val names: List<String>) {
    val text: String get() = names.joinToString(", ")
}

/**
 * A song's credits (performed, written and produced by, and the rest) from YouTube Music's own
 * credits page for the track. Catalogue tracks have one under `MPTC` + their video id; anything
 * else answers with nothing, and the song info simply has no credits section.
 */
object SongCredits {

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build() }
    private val cache = ConcurrentHashMap<String, List<SongCredit>>()

    /** The credits for [videoId], or empty when there are none or YouTube Music won't answer. */
    suspend fun fetch(videoId: String): List<SongCredit> {
        cache[videoId]?.let { return it }
        val credits = try {
            parseCreditsPage(browse("MPTC$videoId"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }
        cache[videoId] = credits
        return credits
    }

    private suspend fun browse(browseId: String): JsonElement = withContext(Dispatchers.IO) {
        val client = YouTubeClient.WEB_REMIX
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
            put("browseId", browseId)
        }.toString()
        val request = Request.Builder()
            .url("${YouTubeClient.API_URL_YOUTUBE_MUSIC}browse?prettyPrint=false")
            .header("User-Agent", client.userAgent)
            .header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            .header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    /**
     * Finds the credits browse endpoint in a `next` answer: one whose page type marks it as track
     * credits, else any menu link whose text says credits.
     */
    internal fun findCreditsEndpoint(root: JsonElement, videoId: String): JsonObject? {
        fun JsonObject.pageType(): String =
            (this["browseEndpointContextSupportedConfigs"] as? JsonObject)
                ?.let { it["browseEndpointContextMusicConfig"] as? JsonObject }
                ?.let { it["pageType"] as? JsonPrimitive }?.contentOrNull.orEmpty()

        fun isCreditsBrowse(browse: JsonObject): Boolean {
            val id = (browse["browseId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return "CREDIT" in browse.pageType() || id.startsWith("MPTC")
        }

        val rows = objectsNamed(root, "playlistPanelVideoRenderer")
            .filter { (it["videoId"] as? JsonPrimitive)?.contentOrNull == videoId }
            .toList()
            .ifEmpty { objectsNamed(root, "playlistPanelVideoRenderer").toList() }
        for (scope in rows + listOfNotNull(root as? JsonObject)) {
            val items = objectsNamed(scope, "menuNavigationItemRenderer").toList()
            fun browseOf(nav: JsonObject) = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
            val endpoint = items.firstNotNullOfOrNull { nav -> browseOf(nav)?.takeIf(::isCreditsBrowse) }
                ?: items.firstNotNullOfOrNull { nav ->
                    browseOf(nav)?.takeIf { nav.runsText().contains("credit", ignoreCase = true) }
                }
            if (endpoint != null) return endpoint
        }
        return objectsNamed(root, "browseEndpoint").firstOrNull(::isCreditsBrowse)
    }

    /** The credits on a credits page, whichever of YouTube Music's layouts it comes in. */
    internal fun parseCreditsPage(root: JsonElement): List<SongCredit> {
        val credits = mutableListOf<SongCredit>()
        // The dialog layout the MPTC page answers with.
        for (section in objectsNamed(root, "dismissableDialogContentSectionRenderer")) {
            val role = section["title"]?.runsText()?.trim().orEmpty()
            val names = splitNames(section["subtitle"]?.runsText().orEmpty())
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctCredits()
        // List rows: the first flex column is the role, the rest the names.
        for (item in objectsNamed(root, "musicResponsiveListItemRenderer")) {
            val flex = (item["flexColumns"] as? JsonArray).orEmpty()
            val role = flex.getOrNull(0)?.rendererText()?.trim().orEmpty()
            val names = if (flex.size > 1) {
                flex.drop(1).flatMap { splitNames(it.rendererText()) }
            } else {
                splitNames(item["subtitle"]?.runsText().orEmpty())
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctCredits()
        // Shelves: the header is the role, the contents the names.
        for (shelf in objectsNamed(root, "musicShelfRenderer") + objectsNamed(root, "musicCarouselShelfRenderer")) {
            val role = (shelf["header"] as? JsonObject)?.runsText()?.trim().orEmpty()
            val names = (shelf["contents"] as? JsonArray).orEmpty()
                .flatMap { splitNames((it as? JsonObject)?.runsText().orEmpty()) }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        return credits.distinctCredits()
    }

    private fun List<SongCredit>.distinctCredits() = distinctBy { it.role.lowercase() to it.text.lowercase() }

    private fun JsonElement.rendererText(): String =
        objectsNamed(this, "text").firstNotNullOfOrNull { it.runsText().takeIf(String::isNotBlank) } ?: runsText()

    private fun JsonElement.runsText(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString("") { it.runsText() }
        is JsonObject -> (this["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: (this["runs"] as? JsonArray)?.joinToString("") { run ->
                ((run as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            ?: this["text"]?.runsText()
            ?: ""
    }

    /** Names split on new lines, bullets, or a comma before a capitalised name. */
    private fun splitNames(joined: String): List<String> {
        if (joined.isBlank()) return emptyList()
        return joined.split(Regex("\\r?\\n+|\\s*[•·]\\s*|\\s*,\\s+(?=[\\p{Lu}\"'])"))
            .map { it.trim().trimEnd(',', '•', '·') }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(joined.trim()) }
    }

    private fun objectsNamed(root: JsonElement, name: String): Sequence<JsonObject> = sequence {
        when (root) {
            is JsonObject -> for ((key, value) in root) {
                if (key == name && value is JsonObject) yield(value)
                yieldAll(objectsNamed(value, name))
            }
            is JsonArray -> for (value in root) yieldAll(objectsNamed(value, name))
            else -> Unit
        }
    }
}
