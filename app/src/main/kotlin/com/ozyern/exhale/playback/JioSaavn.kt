/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The catalogue calls and the rendition rule are BitChord's JioSaavnService
 * (github.com/kushagrasinghx/BitChord, GPL-3.0); the matcher is Exhale's own, stricter one.
 */

package com.ozyern.exhale.playback

import android.util.Base64
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * The same recording at a higher bitrate, from JioSaavn's catalogue.
 *
 * YouTube Music serves most listeners ~160 kbps Opus. JioSaavn files much of the same catalogue at
 * 320 kbps AAC, and that is the difference people hear between a 160 kbps stream and a player that plays
 * from there first. The whole risk is playing the *wrong* file — a radio edit, a remix, a cover, a
 * clean version — so a match has to be the same title and version, the same lead artist, within
 * two seconds of the same length, and no cleaner than what was asked for. Anything short of that
 * stays on YouTube.
 */
object JioSaavn {
    private const val TAG = "JioSaavn"

    // https://www.jiosaavn.com/api.php
    private val BASE_URL = String(Base64.decode("aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tL2FwaS5waHA=", Base64.DEFAULT), Charsets.UTF_8)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /** A decoded CDN address and the bitrate it really serves. */
    data class Stream(val url: String, val kbps: Int)

    /** Below this JioSaavn is no better than YouTube's own stream, so it is not worth switching to. */
    private const val MIN_USABLE_KBPS = 160

    /** The best matching stream for [song], or null when there is no exact match worth playing. */
    suspend fun streamFor(song: MediaMetadata): Stream? = withContext(Dispatchers.IO) {
        runCatching {
            if (song.duration <= 0 || song.title.isBlank()) return@runCatching null
            val lead = song.artists.firstOrNull()?.name.orEmpty()
            val candidates = search("${song.title} $lead")
            val match = candidates.firstOrNull { matches(song, it) } ?: return@runCatching null
            val stream = details(match.optString("id")) ?: return@runCatching null
            stream.takeIf { it.kbps >= MIN_USABLE_KBPS }
        }.onFailure { Timber.tag(TAG).w(it, "lookup failed for %s", song.id) }.getOrNull()
    }

    private fun get(vararg params: Pair<String, String>): String? {
        val url = BASE_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("_format", "json")
            addQueryParameter("_marker", "0")
            addQueryParameter("api_version", "4")
            addQueryParameter("ctx", "android")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-IN,en;q=0.9")
            .header("Cookie", "explicit_content=1")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun search(query: String): List<JSONObject> {
        val body = get("__call" to "search.getResults", "q" to query, "p" to "1", "n" to "10") ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            // Uncensored rows first.
            .sortedByDescending { isExplicit(it) }
    }

    private fun details(id: String): Stream? {
        if (id.isBlank()) return null
        val body = get("__call" to "song.getDetails", "pids" to id) ?: return null
        val root = JSONObject(body)
        // `song.getDetails` answers `{"<id>":{…}}`, not the `{"songs":[…]}` envelope search uses.
        val song = (root.optJSONArray("songs") as JSONArray?)?.optJSONObject(0)
            ?: root.keys().asSequence().mapNotNull { root.optJSONObject(it) }.firstOrNull()
            ?: return null
        val info = song.optJSONObject("more_info") ?: return null
        val url = decrypt(info.optString("encrypted_media_url"))
        val has320 = info.optString("320kbps").equals("true", ignoreCase = true)
        return best(url, has320)
    }

    private fun decrypt(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES"))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)), Charsets.UTF_8).trim()
        }.getOrDefault("")
    }

    /**
     * The 320 rendition when the catalogue says one exists, otherwise what the URL names. The
     * rewrite is anchored to the rendition token, not the end of the URL, so a query string after
     * `.mp4` cannot leave the 96 kbps file in place while reporting 320.
     */
    private fun best(url: String, has320: Boolean): Stream? {
        if (url.isBlank()) return null
        val rendition = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)(?=[?#]|$)", RegexOption.IGNORE_CASE).find(url)
            ?: return null
        val offered = rendition.groupValues[1].toInt()
        val https = { u: String -> u.replaceFirst(Regex("^http://"), "https://") }
        if (!has320) return Stream(https(url), offered)
        return Stream(https(url.replaceRange(rendition.range, "_320.${rendition.groupValues[2]}")), 320)
    }

    // ---- Matching -----------------------------------------------------------

    /** Words that make a different recording of the same song. Remasters are the same recording. */
    private val VersionWords = setOf(
        "remix", "live", "acoustic", "instrumental", "slowed", "reverb", "sped", "speed", "lofi",
        "karaoke", "cover", "edit", "unplugged", "version", "demo", "mashup", "reprise", "orchestral",
        "piano", "extended", "radio", "clean", "nightcore", "8d", "bass", "boosted", "mix", "vip",
    )

    private fun isExplicit(row: JSONObject): Boolean =
        row.optString("explicit_content").let { it == "1" || it.equals("true", true) }

    private fun matches(song: MediaMetadata, row: JSONObject): Boolean {
        val info = row.optJSONObject("more_info") ?: return false
        val seconds = info.optString("duration").toIntOrNull() ?: return false
        if (abs(seconds - song.duration) > 2) return false
        // A clean edit for an explicit request is a different file.
        if (song.explicit && !isExplicit(row)) return false

        val (wantTitle, wantVersion) = titleParts(song.title)
        val (haveTitle, haveVersion) = titleParts(unescape(row.optString("title")))
        if (wantTitle.isEmpty() || wantTitle != haveTitle) return false
        if (wantVersion != haveVersion) return false

        val lead = normalize(song.artists.firstOrNull()?.name.orEmpty())
        if (lead.isEmpty()) return false
        val primaries = info.optJSONObject("artistMap")?.optJSONArray("primary_artists")
        val names = buildList {
            if (primaries != null) {
                for (i in 0 until primaries.length()) add(normalize(unescape(primaries.optJSONObject(i)?.optString("name").orEmpty())))
            }
            add(normalize(unescape(row.optString("subtitle"))))
        }
        return names.any { it == lead || it.split(' ').windowed(lead.split(' ').size).any { w -> w.joinToString(" ") == lead } }
    }

    /** The bare title, and the version words that set this recording apart from others of it. */
    private fun titleParts(raw: String): Pair<String, Set<String>> {
        val lower = raw.lowercase()
        // Featured artists are credits, not part of the title.
        val noFeat = lower.replace(Regex("[(\\[]\\s*(feat|ft|with)\\.?[^)\\]]*[)\\]]"), " ")
            .replace(Regex("\\s(feat|ft)\\.?\\s.*$"), " ")
        val bracketed = Regex("[(\\[]([^)\\]]*)[)\\]]").findAll(noFeat).joinToString(" ") { it.groupValues[1] }
        val afterDash = noFeat.substringAfter(" - ", "")
        val base = noFeat.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ").substringBefore(" - ")
        val version = normalize("$bracketed $afterDash").split(' ').filter { it in VersionWords }.toSet()
        return normalize(base) to version
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun unescape(s: String): String =
        s.replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'").replace("&apos;", "'")
}
