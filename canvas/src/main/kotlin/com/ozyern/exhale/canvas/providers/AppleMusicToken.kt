/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.canvas.providers

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The Apple Music web player's developer token, read from the script its own page loads.
 *
 * Every motion cover, album video and artist loop comes from Apple's catalogue, and the catalogue
 * only answers a request carrying this token. A token written into the app lasts a few months: the
 * last one ran out on 14 July, and from then on no song had a moving cover. So the token is fetched
 * the way the web player gets it, kept until it is close to expiring, and fetched again the moment
 * Apple refuses it.
 *
 * Applied by [interceptor], which every Apple client here installs: the requests themselves carry
 * no token of their own.
 */
object AppleMusicToken {

    /** Used only until a fresh one has been read — and while one can't be. */
    private const val FALLBACK =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ" +
            ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzgxMDMyODU1LCJleHAiOjE3ODQw" +
            "NTY4NTUsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
            ".fiMFcJWkfSlxKP9NVA0UW9CbItD1Rge0SISuepz203XcpU762OqdCpU9M-YkmtKkjRmaIWtjsfGgqZPrlMonpA"

    private const val CATALOGUE_HOST = "amp-api.music.apple.com"
    private const val AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /** After a failed fetch, how long before asking the page again. */
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L

    /** A token this close to its expiry is replaced before it is refused. */
    private const val EXPIRY_MARGIN_MS = 24 * 60 * 60 * 1000L

    private val INDEX_SCRIPT = Regex("""/assets/index[~-][^"']+\.js""")
    private val TOKEN = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

    @Volatile private var token: String? = null
    @Volatile private var lastFailureAt = 0L

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** The token to send now. Blocking: called from OkHttp's own threads. */
    @Synchronized
    fun current(): String {
        token?.takeUnless(::nearlyExpired)?.let { return it }
        fetch()?.let { fresh ->
            token = fresh
            return fresh
        }
        return token ?: FALLBACK
    }

    /** [refused] was turned away: a fresh token, or null when there is none better to try. */
    @Synchronized
    private fun replace(refused: String): String? {
        token?.takeIf { it != refused }?.let { return it }
        lastFailureAt = 0L
        val fresh = fetch()?.takeIf { it != refused } ?: return null
        token = fresh
        return fresh
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != CATALOGUE_HOST) return@Interceptor chain.proceed(request)
        val sent = current()
        val response = chain.proceed(request.withToken(sent))
        if (response.code != 401 && response.code != 403) return@Interceptor response
        val fresh = replace(sent) ?: return@Interceptor response
        response.close()
        chain.proceed(request.withToken(fresh))
    }

    private fun Request.withToken(value: String): Request =
        newBuilder()
            .header("Authorization", "Bearer $value")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .build()

    private fun fetch(): String? {
        if (System.currentTimeMillis() - lastFailureAt < RETRY_AFTER_MS) return null
        val found = runCatching {
            val page = get("https://music.apple.com/us/browse") ?: return@runCatching null
            val script = INDEX_SCRIPT.find(page)?.value ?: return@runCatching null
            val javascript = get("https://music.apple.com$script") ?: return@runCatching null
            TOKEN.findAll(javascript).map { it.value }.firstOrNull { !nearlyExpired(it) }
        }.getOrNull()
        if (found == null) lastFailureAt = System.currentTimeMillis()
        return found
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
        return http.newCall(request).execute().use { response: Response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }

    /** Whether the token's own `exp` has passed, or nearly. Unreadable counts as fine. */
    private fun nearlyExpired(jwt: String): Boolean {
        val payload = jwt.split('.').getOrNull(1) ?: return false
        val decoded = runCatching { String(Base64.getUrlDecoder().decode(payload)) }.getOrNull() ?: return false
        val exp = Regex(""""exp"\s*:\s*(\d+)""").find(decoded)?.groupValues?.get(1)?.toLongOrNull() ?: return false
        return exp * 1000L - EXPIRY_MARGIN_MS < System.currentTimeMillis()
    }
}
