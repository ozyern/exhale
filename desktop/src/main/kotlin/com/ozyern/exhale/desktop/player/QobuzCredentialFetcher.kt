/*
 * Exhale Project (2026)
 * Ported from Strawberry Music Player (Jonas Kvinge)
 * Licensed Under GPL-3.0
 */

package com.ozyern.exhale.desktop.player

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

data class QobuzApiCredentials(
    val appId: String,
    val appSecret: String,
    val loginAppId: String? = null,
    val privateKey: String? = null,
    val fetchedAtMs: Long = System.currentTimeMillis(),
)

/**
 * Dynamically fetches and extracts live Qobuz API credentials from the official web player,
 * mirroring Strawberry Music Player's QobuzApiCredentialFetcher.
 *
 * This avoids stale/hardcoded secrets and allows searching and streaming with fresh keys.
 */
object QobuzCredentialFetcher {
    private const val LOGIN_PAGE_URL = "https://play.qobuz.com/login"
    private const val PLAY_QOBUZ_BASE = "https://play.qobuz.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    @Volatile
    private var cachedCredentials: QobuzApiCredentials? = null

    private val BUNDLE_REGEX = Regex("""<script src="(/resources/[\d.]+-[a-z]\d+/bundle\.js)"></script>""")
    private val APP_ID_REGEX = Regex("""production:\{api:\{appId:"(\d+)"""")
    private val LOGIN_APP_ID_REGEX = Regex("""\{appId:"(\d{8,10})"""")
    private val PRIVATE_KEY_REGEX = Regex("""privateKey:"([A-Za-z0-9]+)"""")
    private val SEED_REGEX = Regex("""[a-z]\.initialSeed\("([\w=]+)",window\.utimezone\.([a-z]+)\)""")
    private val INFO_REGEX = Regex("""name:"\w+/(\w+)",info:"([\w=]+)",extras:"([\w=]+)"""")
    private val HEX_SECRET_REGEX = Regex("""^[a-f0-9]{32}$""")

    private val PREFERRED_TIMEZONES = listOf("berlin", "london", "abidjan", "paris")

    fun getCredentials(client: OkHttpClient): QobuzApiCredentials? {
        val cached = cachedCredentials
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAtMs < CACHE_TTL_MS) {
            return cached
        }
        val fresh = fetchCredentials(client)
        if (fresh != null) {
            cachedCredentials = fresh
            return fresh
        }
        return cached
    }

    private fun fetchCredentials(client: OkHttpClient): QobuzApiCredentials? {
        return runCatching {
            // 1. Fetch login page
            val loginReq = Request.Builder()
                .url(LOGIN_PAGE_URL)
                .header("User-Agent", USER_AGENT)
                .build()

            val loginHtml = client.newCall(loginReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.string()
            }

            val bundleMatch = BUNDLE_REGEX.find(loginHtml) ?: return@runCatching null
            val bundlePath = bundleMatch.groupValues[1]

            // 2. Fetch bundle.js
            val bundleReq = Request.Builder()
                .url(PLAY_QOBUZ_BASE + bundlePath)
                .header("User-Agent", USER_AGENT)
                .build()

            val bundleJs = client.newCall(bundleReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.string()
            }

            // 3. Extract appId
            val appId = APP_ID_REGEX.find(bundleJs)?.groupValues?.get(1)
                ?: LOGIN_APP_ID_REGEX.find(bundleJs)?.groupValues?.get(1)
                ?: return@runCatching null

            val loginAppId = LOGIN_APP_ID_REGEX.find(bundleJs)?.groupValues?.get(1)
            val privateKey = PRIVATE_KEY_REGEX.find(bundleJs)?.groupValues?.get(1)

            // 4. Extract seeds
            val seeds = mutableMapOf<String, String>()
            for (m in SEED_REGEX.findAll(bundleJs)) {
                val seed = m.groupValues[1]
                val tz = m.groupValues[2].lowercase(Locale.ROOT)
                seeds[tz] = seed
            }

            // 5. Extract timezone info & extras
            val infos = mutableMapOf<String, Pair<String, String>>()
            for (m in INFO_REGEX.findAll(bundleJs)) {
                val tz = m.groupValues[1].lowercase(Locale.ROOT)
                val info = m.groupValues[2]
                val extras = m.groupValues[3]
                infos[tz] = Pair(info, extras)
            }

            // 6. Combine & decode secret
            var appSecret: String? = null
            val candidateTzs = (PREFERRED_TIMEZONES + seeds.keys).distinct()
            for (tz in candidateTzs) {
                val seed = seeds[tz] ?: continue
                val pair = infos[tz] ?: continue
                val combined = seed + pair.first + pair.second
                if (combined.length <= 44) continue
                val trimmed = combined.substring(0, combined.length - 44)
                val decoded = runCatching {
                    val bytes = java.util.Base64.getDecoder().decode(trimmed)
                    String(bytes, Charsets.ISO_8859_1)
                }.getOrNull() ?: continue

                if (HEX_SECRET_REGEX.matches(decoded)) {
                    appSecret = decoded
                    break
                }
            }

            if (appSecret == null) return@runCatching null

            QobuzApiCredentials(
                appId = appId,
                appSecret = appSecret,
                loginAppId = loginAppId,
                privateKey = privateKey,
            )
        }.getOrNull()
    }
}
