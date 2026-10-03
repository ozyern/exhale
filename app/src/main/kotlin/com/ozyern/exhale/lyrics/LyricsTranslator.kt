/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Line-by-line lyric translation through Google Translate's public endpoint.
 *
 * Replaces the translator library the lyrics used, which failed silently whenever the page it
 * scraped changed — the translate button then did nothing at all. This asks the same service for
 * JSON directly, keeps lines aligned one-to-one, and says which language it found, so a song that
 * is already in the target language can be reported as such instead of looking broken.
 */
object LyricsTranslator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** The common targets offered in the lyric settings, as (code, name). */
    val languages: List<Pair<String, String>> = listOf(
        "en", "es", "pt", "fr", "de", "it", "hi", "ja", "ko", "zh-CN", "ar", "ru", "tr", "id", "vi",
        "th", "nl", "pl", "uk", "bn", "ta", "te", "mr", "ur", "fa", "he", "sv", "tl",
    ).map { code -> code to Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() } }

    /** The phone's language as a Translate code. */
    fun deviceLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    data class Result(val lines: List<String?>, val sourceLanguage: String?)

    /**
     * Translates [lines] into [target]. Each result lines up with its input; a line that failed,
     * or came back unchanged, is null.
     */
    suspend fun translate(lines: List<String>, target: String): Result = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext Result(emptyList(), null)
        var source: String? = null
        val out = ArrayList<String?>(lines.size)
        // Batches keep requests few and short enough for a GET; newlines keep lines separate.
        for (batch in lines.chunked(25)) {
            val joined = batch.joinToString("\n")
            val response = runCatching { request(joined, target) }.getOrNull()
            val parts = response?.first?.split("\n")?.map { it.trim() }
            if (response?.second != null) source = response.second
            if (parts != null && parts.size == batch.size) {
                batch.zip(parts).forEach { (original, translated) -> out += translated.takeUnless { it.isBlank() || it.equals(original.trim(), ignoreCase = true) } }
            } else {
                // The service merged or split lines; fall back to one request per line.
                batch.forEach { line ->
                    val one = runCatching { request(line, target) }.getOrNull()
                    if (one?.second != null) source = one.second
                    out += one?.first?.trim()?.takeUnless { it.isBlank() || it.equals(line.trim(), ignoreCase = true) }
                }
            }
        }
        Result(out, source)
    }

    /** One call: the translated text and the language the service detected. */
    private fun request(text: String, target: String): Pair<String, String?> {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", target)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        val body = client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Translate HTTP ${response.code}" }
            response.body.string()
        }
        val root = JSONArray(body)
        val segments = root.getJSONArray(0)
        val translated = buildString {
            for (i in 0 until segments.length()) {
                val segment = segments.optJSONArray(i) ?: continue
                append(segment.optString(0))
            }
        }
        val detected = root.optString(2).takeIf { it.isNotBlank() && it != "null" }
        return translated to detected
    }
}
