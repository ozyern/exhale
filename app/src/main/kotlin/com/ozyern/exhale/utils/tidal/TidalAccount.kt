/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The listener's own TIDAL account: sign-in (TIDAL's PKCE flow, or the web player's session as a
 * fallback), token renewal, and lossless playback from api.tidal.com with that account. The session
 * is kept in this phone's settings only; nothing here ships a token.
 *
 * Auth and search ported from master's TidalAccountManager (itself from ArchiveTune, GPL-3.0).
 */

package com.ozyern.exhale.utils.tidal

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.constants.PreferTidalKey
import com.ozyern.exhale.constants.TidalAccessTokenKey
import com.ozyern.exhale.constants.TidalAccountNameKey
import com.ozyern.exhale.constants.TidalAuthFlowKey
import com.ozyern.exhale.constants.TidalCountryCodeKey
import com.ozyern.exhale.constants.TidalRefreshTokenKey
import com.ozyern.exhale.constants.TidalTokenExpiryKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object TidalAccount {
    private const val TAG = "TidalAccount"

    private const val PKCE_CLIENT_ID = "6BDSRdpK9hqEBTgU"
    private const val PKCE_CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
    private const val PKCE_AUTHORIZE_ENDPOINT = "https://login.tidal.com/authorize"
    const val PKCE_REDIRECT_URI = "https://tidal.com/android/login/auth"
    const val WEB_PLAYER_URL = "https://listen.tidal.com"

    /** TIDAL's Android TV app, the other client a pasted refresh token may belong to. */
    private const val TV_CLIENT_ID = "zU4XHVVkc2tDPo4t"
    private const val TV_CLIENT_SECRET = "VJKhDFqJPqvsPVNBV6ukXTJmwlvbttP7wlMlrc72se4="

    /** The open-source tidalapi client (Fire TV), used by many third-party players. */
    private const val TIDALAPI_CLIENT_ID = "fX2JxdmntZWK0ixT"
    private const val TIDALAPI_CLIENT_SECRET = "1Nn9AfDAjxrgJFJbKNWLeAyKGVGmINuXPPLHVXAvxAg="

    const val FLOW_PKCE = "pkce"
    /** A pasted refresh token belonging to the TV client; renewed with that client. */
    const val FLOW_TV = "tv"
    /** A pasted refresh token belonging to the tidalapi client; renewed with that client. */
    const val FLOW_TIDALAPI = "tidalapi"

    /** A refresh token only renews with the client that issued it; each one, by flow. */
    private fun clientFor(flow: String): Pair<String, String> = when (flow) {
        FLOW_TV -> TV_CLIENT_ID to TV_CLIENT_SECRET
        FLOW_TIDALAPI -> TIDALAPI_CLIENT_ID to TIDALAPI_CLIENT_SECRET
        else -> PKCE_CLIENT_ID to PKCE_CLIENT_SECRET
    }
    const val FLOW_WEBCAPTURE = "webcapture"

    private const val TOKEN_ENDPOINT = "https://auth.tidal.com/v1/oauth2/token"
    private const val API_BASE = "https://api.tidal.com/v1"
    private const val SCOPE = "r_usr+w_usr+w_sub"
    private const val DEFAULT_COUNTRY = "US"

    /** Renew this long before the token runs out, so a song never starts on a dead one. */
    private const val EXPIRY_MARGIN_MS = 60_000L

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    private val refreshLock = Mutex()

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val username: String?,
        val countryCode: String?,
    )

    data class Stream(
        val url: String,
        val trackId: Long,
        val title: String,
        val durationSec: Int?,
    )

    // ── Session ──────────────────────────────────────────────────────────────────────────────

    fun isSignedIn(context: Context): Boolean =
        context.dataStore.get(TidalAccessTokenKey, "").isNotBlank()

    /** Signed in and preferring TIDAL (the default once signed in). */
    fun isPreferred(context: Context): Boolean =
        isSignedIn(context) && context.dataStore.get(PreferTidalKey, true)

    suspend fun save(context: Context, token: TokenResult, flow: String) {
        context.dataStore.edit { p ->
            p[TidalAccessTokenKey] = token.accessToken
            if (token.refreshToken != null) p[TidalRefreshTokenKey] = token.refreshToken else p.remove(TidalRefreshTokenKey)
            p[TidalTokenExpiryKey] = token.expiresAtMillis
            p[TidalAccountNameKey] = token.username ?: "TIDAL"
            token.countryCode?.let { p[TidalCountryCodeKey] = it }
            p[TidalAuthFlowKey] = flow
        }
    }

    suspend fun signOut(context: Context) {
        context.dataStore.edit { p ->
            listOf(TidalAccessTokenKey, TidalRefreshTokenKey, TidalAccountNameKey, TidalCountryCodeKey, TidalAuthFlowKey)
                .forEach { p.remove(it) }
            p.remove(TidalTokenExpiryKey)
        }
    }

    /** A usable access token, renewed first when it has run out; null when signed out or renewal failed. */
    private suspend fun accessToken(context: Context, forceRefresh: Boolean = false): String? =
        refreshLock.withLock {
            val p = context.dataStore.data.first()
            val token = p[TidalAccessTokenKey]?.takeIf { it.isNotBlank() } ?: return@withLock null
            val expiry = p[TidalTokenExpiryKey] ?: 0L
            if (!forceRefresh && System.currentTimeMillis() < expiry - EXPIRY_MARGIN_MS) return@withLock token
            val refresh = p[TidalRefreshTokenKey]?.takeIf { it.isNotBlank() }
            if (refresh == null) {
                // A web-player session cannot be renewed; try it until TIDAL refuses it.
                return@withLock if (forceRefresh) null else token
            }
            val flow = p[TidalAuthFlowKey] ?: FLOW_PKCE
            val renewed = refreshAccessToken(refresh, flow) ?: return@withLock if (forceRefresh) null else token
            save(context, renewed.copy(refreshToken = renewed.refreshToken ?: refresh, username = renewed.username ?: p[TidalAccountNameKey]), flow)
            renewed.accessToken
        }

    private suspend fun refreshAccessToken(refreshToken: String, flow: String = FLOW_PKCE): TokenResult? =
        tokenRequest(
            FormBody.Builder()
                .add("client_id", clientFor(flow).first)
                .add("client_secret", clientFor(flow).second)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .add("scope", SCOPE)
                .build(),
            "refresh",
        )

    // ── Sign-in ──────────────────────────────────────────────────────────────────────────────

    /**
     * Signs in with a token the listener pastes: a refresh token (renewed with whichever TIDAL client
     * it belongs to, so it keeps working) or, failing that, an access token (good until it expires).
     * Saved on this phone only. Returns the account name, or null when TIDAL accepted neither.
     */
    suspend fun signInWithToken(context: Context, raw: String): String? {
        // Other apps copy the token alone, with "Bearer ", in quotes, or inside a JSON session.
        val text = raw.trim()
        val fromJson = runCatching {
            JSONObject(text).let { j -> j.optString("refresh_token").ifBlank { j.optString("refreshToken") }.ifBlank { j.optString("access_token") } }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val token = fromJson
            ?: Regex("""[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""").find(text)?.value
            ?: text.removePrefix("Bearer ").trim().trim('"')
        if (token.length < 20) return null
        lastTokenError = null
        val errors = mutableListOf<String>()
        for (flow in listOf(FLOW_TIDALAPI, FLOW_PKCE, FLOW_TV)) {
            val session = refreshAccessToken(token, flow) ?: run {
                lastTokenError?.let { errors += it }
                null
            } ?: continue
            // The token endpoint names no user on some clients; ask the session for it.
            val named = if (session.username == null || session.countryCode == null) {
                sessionFromBearer(session.accessToken)?.let { s ->
                    session.copy(username = session.username ?: s.username, countryCode = session.countryCode ?: s.countryCode)
                } ?: session
            } else {
                session
            }
            save(context, named.copy(refreshToken = named.refreshToken ?: token), flow)
            return named.username ?: "TIDAL"
        }
        // A token one client calls expired is expired for all; otherwise report the client mismatch.
        lastTokenError = errors.firstOrNull { it == "invalid_grant" } ?: errors.firstOrNull()
        val access = sessionFromBearer(token) ?: return null
        save(context, access, FLOW_WEBCAPTURE)
        return access.username ?: "TIDAL"
    }

    data class PkceChallenge(val verifier: String, val uniqueKey: String, val authUrl: String)

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun buildPkceChallenge(): PkceChallenge {
        val random = SecureRandom()
        val verifier = base64UrlNoPad(ByteArray(64).also { random.nextBytes(it) })
        val challenge = base64UrlNoPad(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val uniqueKey = ByteArray(8).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val authUrl = buildString {
            append(PKCE_AUTHORIZE_ENDPOINT)
            append("?response_type=code")
            append("&redirect_uri=").append(enc(PKCE_REDIRECT_URI))
            append("&client_id=").append(PKCE_CLIENT_ID)
            append("&lang=EN&appMode=android")
            append("&client_unique_key=").append(uniqueKey)
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&restrict_signup=true")
        }
        return PkceChallenge(verifier, uniqueKey, authUrl)
    }

    suspend fun exchangePkceCode(code: String, challenge: PkceChallenge): TokenResult? =
        tokenRequest(
            FormBody.Builder()
                .add("code", code)
                .add("client_id", PKCE_CLIENT_ID)
                .add("client_secret", PKCE_CLIENT_SECRET)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", PKCE_REDIRECT_URI)
                .add("scope", SCOPE)
                .add("code_verifier", challenge.verifier)
                .add("client_unique_key", challenge.uniqueKey)
                .build(),
            "code exchange",
        )

    /** The web player's own session, checked against TIDAL. It lasts an hour and cannot be renewed. */
    suspend fun sessionFromBearer(accessToken: String): TokenResult? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$API_BASE/sessions").header("Authorization", "Bearer $accessToken").get().build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) return@use null
                    val json = JSONObject(payload)
                    TokenResult(
                        accessToken = accessToken,
                        refreshToken = null,
                        expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
                        username = json.optString("username").ifBlank { null },
                        countryCode = json.optString("countryCode").ifBlank { null },
                    )
                }
            }.getOrElse {
                Timber.tag(TAG).w(it, "web session check failed")
                null
            }
        }

    /** TIDAL's reason for the last refused token request, for the sign-in message. */
    @Volatile
    private var lastTokenError: String? = null

    /** Why the last [signInWithToken] failed, in words the listener can act on. */
    fun lastSignInProblem(): String = when (val e = lastTokenError) {
        null -> "TIDAL could not be reached"
        "invalid_grant" -> "TIDAL says this token has expired or was signed out (invalid_grant)"
        "invalid_client", "unauthorized_client" -> "This token belongs to another app's TIDAL login, which Exhale can't renew ($e)"
        else -> "TIDAL refused the token ($e)"
    }

    private suspend fun tokenRequest(body: FormBody, what: String): TokenResult? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        Timber.tag(TAG).w("token %s failed: %d", what, response.code)
                        lastTokenError = runCatching {
                            JSONObject(payload).let { j -> j.optString("error").ifBlank { j.optString("sub_status") } }
                        }.getOrNull()?.ifBlank { null } ?: "HTTP ${response.code}"
                        return@use null
                    }
                    val json = JSONObject(payload)
                    val user = json.optJSONObject("user")
                    TokenResult(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token").ifBlank { null },
                        expiresAtMillis = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
                        username = user?.optString("username")?.ifBlank { null },
                        countryCode = user?.optString("countryCode")?.ifBlank { null },
                    )
                }
            }.getOrElse {
                Timber.tag(TAG).w(it, "token %s error", what)
                null
            }
        }

    // ── Playback ─────────────────────────────────────────────────────────────────────────────

    private class Unauthorized : Exception("TIDAL token rejected")

    /**
     * A direct lossless (16-bit FLAC) URL for this song from the listener's account, or null when
     * signed out, not found, or TIDAL refused. Renews the token once if TIDAL rejects it.
     */
    suspend fun losslessStream(
        context: Context,
        title: String,
        artists: List<String>,
        durationSec: Int?,
    ): Stream? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val country = context.dataStore.data.first()[TidalCountryCodeKey]?.ifBlank { null } ?: DEFAULT_COUNTRY
        var token = accessToken(context) ?: return@withContext null
        repeat(2) { attempt ->
            try {
                val match = searchTrack(token, title, artists, durationSec, country) ?: return@withContext null
                val url = playbackUrl(token, match.first, country) ?: return@withContext null
                return@withContext Stream(url, match.first, match.second, match.third)
            } catch (_: Unauthorized) {
                if (attempt == 1) return@withContext null
                token = accessToken(context, forceRefresh = true) ?: return@withContext null
            }
        }
        null
    }

    /** (id, title, duration in seconds) of the best match, or null below the match bar. */
    private fun searchTrack(
        token: String,
        title: String,
        artists: List<String>,
        durationSec: Int?,
        country: String,
    ): Triple<Long, String, Int?>? {
        val primaryArtist = artists.firstOrNull().orEmpty()
        val query = URLEncoder.encode("$title $primaryArtist".trim(), "UTF-8")
        val request = Request.Builder()
            .url("$API_BASE/search/tracks?query=$query&limit=15&countryCode=$country")
            .header("Authorization", "Bearer $token")
            .get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw Unauthorized()
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) return@use null
                val items = JSONObject(payload).optJSONArray("items") ?: return@use null
                var best: Triple<Long, String, Int?>? = null
                var bestScore = Int.MIN_VALUE
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optLong("id").takeIf { it > 0 } ?: continue
                    val candTitle = item.optString("title")
                    var score = when {
                        candTitle.equals(title, ignoreCase = true) -> 50
                        candTitle.contains(title, ignoreCase = true) || title.contains(candTitle, ignoreCase = true) -> 25
                        else -> 0
                    }
                    val candArtists = item.optJSONArray("artists")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                    }.orEmpty()
                    if (primaryArtist.isNotBlank() && candArtists.any { it.contains(primaryArtist, ignoreCase = true) }) score += 30
                    val candDuration = item.optInt("duration").takeIf { it > 0 }
                    if (durationSec != null && candDuration != null && abs(candDuration - durationSec) <= 5) score += 20
                    if (score > bestScore) {
                        bestScore = score
                        best = Triple(id, candTitle, candDuration)
                    }
                }
                if (bestScore >= 40) best else null
            }
        }.getOrElse {
            if (it is Unauthorized) throw it
            Timber.tag(TAG).w(it, "search error")
            null
        }
    }

    private fun playbackUrl(token: String, trackId: Long, country: String): String? {
        // LOSSLESS is a single FLAC file; HI_RES_LOSSLESS comes as DASH, which this build does not play.
        val request = Request.Builder()
            .url("$API_BASE/tracks/$trackId/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL&countryCode=$country")
            .header("Authorization", "Bearer $token")
            .get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw Unauthorized()
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    Timber.tag(TAG).w("playbackinfo failed: %d", response.code)
                    return@use null
                }
                val json = JSONObject(payload)
                if (json.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    Timber.tag(TAG).w("playbackinfo gave a preview; the account has no lossless streaming")
                    return@use null
                }
                // A lossy answer (an account without lossless) is no upgrade: let the next source play.
                val quality = json.optString("audioQuality")
                if (quality.isNotBlank() && !quality.contains("LOSSLESS", ignoreCase = true)) return@use null
                TidalService.decodeManifestUrl(json.optString("manifest"), json.optString("manifestMimeType"))
            }
        }.getOrElse {
            if (it is Unauthorized) throw it
            Timber.tag(TAG).w(it, "playbackinfo error")
            null
        }
    }
}
