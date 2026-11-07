/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0.
 * Origin: playback/MusicService.kt (AppleTrackDrmInfo, buildAppleDrmSessionManager,
 * AppleLicenseCallback, APPLE_LICENSE_URL). Extracted into a standalone helper so Exhale's
 * MusicService can plug it into `DefaultMediaSourceFactory.setDrmSessionManagerProvider`.
 *
 * Apple Music serves fairplay/widevine-protected fMP4. ArchiveTune plays the Widevine flavour:
 * the HLS playlist is flattened into a single .m4a file by AppleMusicVirtualStream, and the
 * Widevine key is obtained by POSTing the challenge to Apple's web-playback license endpoint with
 * the same headers the music.apple.com web player uses.
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ozyern.exhale.playback.lossless.sources.applemusic

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AppleTrackDrmInfo(
    val adamId: String,
    val drmUri: String,
)

@OptIn(UnstableApi::class)
object AppleMusicDrm {
    private const val TAG = "AppleMusicDrm"

    const val APPLE_LICENSE_URL =
        "https://play.itunes.apple.com/WebObjects/MZPlay.woa/wa/acquireWebPlaybackLicense"

    private const val APPLE_MUSIC_WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    /**
     * mediaId → DRM info for tracks that resolved through Apple Music. Populated by the resolver
     * ([com.ozyern.exhale.playback.lossless.sources.LosslessStreamResolver]) right before the flattened
     * file URI is handed to the player and consulted by [drmSessionManagerProvider].
     */
    private val appleDrmTrackInfo: MutableMap<String, AppleTrackDrmInfo> = ConcurrentHashMap()

    private val licenseClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun register(mediaId: String, info: AppleTrackDrmInfo) {
        appleDrmTrackInfo[mediaId] = info
    }

    /**
     * Lookup by player mediaId. Exhale queues carry either the bare 11-char video id or a
     * `youtube_<id>` mediaId while the resolver registers under the bare id, so both are accepted.
     */
    fun infoFor(mediaId: String?): AppleTrackDrmInfo? {
        if (mediaId.isNullOrBlank()) return null
        appleDrmTrackInfo[mediaId]?.let { return it }
        val bare = mediaId.removePrefix("youtube_")
        return if (bare != mediaId) appleDrmTrackInfo[bare] else null
    }

    fun clear(mediaId: String) {
        appleDrmTrackInfo.remove(mediaId)
    }

    /**
     * Provider to install on the media source factory.
     *
     * `DefaultMediaSourceFactory` asks for the DrmSessionManager when the media source is
     * *created*, which — for a cold `youtube://` item — is before the load-thread resolve has run
     * and registered the Apple DRM info. ArchiveTune keys the lookup eagerly on mediaId and relies
     * on a re-prepare for that case; here the callback resolves the track info lazily at key-request
     * time instead (that is after the extractor parsed the flattened file, i.e. after the resolve),
     * so the first attempt succeeds. Items that cannot be Apple (no tokens, or a non-lossless
     * scheme) still get [DrmSessionManager.DRM_UNSUPPORTED]; clear content never touches the
     * session manager because `acquireSession` returns null for formats without DrmInitData.
     */
    val drmSessionManagerProvider: DrmSessionManagerProvider =
        DrmSessionManagerProvider { mediaItem: MediaItem ->
            val mediaId = mediaItem.mediaId
            val appleTrack = infoFor(mediaId)
            when {
                appleTrack != null -> buildAppleDrmSessionManager(appleTrack) ?: DrmSessionManager.DRM_UNSUPPORTED
                mediaId.isNotBlank() && mayResolveToApple(mediaItem) -> buildLazyAppleDrmSessionManager(mediaId)
                else -> DrmSessionManager.DRM_UNSUPPORTED
            }
        }

    /** Cheap, in-memory only: called on the player's application thread. */
    private fun mayResolveToApple(mediaItem: MediaItem): Boolean {
        if (!AppleMusicAudioProvider.isAvailable()) return false
        val scheme = mediaItem.localConfiguration?.uri?.scheme?.lowercase(java.util.Locale.US)
        return scheme == null || scheme == "youtube" || scheme == "file"
    }

    fun buildAppleDrmSessionManager(track: AppleTrackDrmInfo): DrmSessionManager? {
        val mediaToken = AppleMusicAudioProvider.mediaUserToken() ?: return null
        val devToken = AppleMusicAudioProvider.devToken()
        return buildWidevineManager(AppleLicenseCallback({ track }, devToken, mediaToken))
    }

    private fun buildLazyAppleDrmSessionManager(mediaId: String): DrmSessionManager =
        buildWidevineManager(
            AppleLicenseCallback(
                trackProvider = { infoFor(mediaId) },
                devToken = null,
                mediaToken = null,
            ),
        )

    private fun buildWidevineManager(callback: MediaDrmCallback): DrmSessionManager =
        DefaultDrmSessionManager
            .Builder()
            .setUuidAndExoMediaDrmProvider(
                C.WIDEVINE_UUID,
                ExoMediaDrm.Provider { uuid ->
                    // Apple's web player only ever gets L3 keys; forcing L3 avoids the HW-secure
                    // decoder path which cannot be used with a flattened file anyway.
                    val created =
                        runCatching { FrameworkMediaDrm.newInstance(uuid) }.getOrNull()?.apply {
                            runCatching { setPropertyString("securityLevel", "L3") }
                                .recoverCatching { setPropertyString("securityLevel", "3") }
                        }
                    created ?: FrameworkMediaDrm.DEFAULT_PROVIDER.acquireExoMediaDrm(uuid)
                },
            ).build(callback)

    /**
     * @param trackProvider resolved when the key request is executed (see [drmSessionManagerProvider]).
     * @param devToken / [mediaToken] snapshot to use; when null the live provider values are read at
     *   request time.
     */
    private class AppleLicenseCallback(
        private val trackProvider: () -> AppleTrackDrmInfo?,
        private val devToken: String?,
        private val mediaToken: String?,
    ) : MediaDrmCallback {
        // Widevine provisioning goes to Google's provisioning server carried in the request itself;
        // the plain HTTP callback handles that part exactly like ArchiveTune does.
        private val provisionFallback = HttpMediaDrmCallback(null, DefaultHttpDataSource.Factory())

        private fun fail(message: String): Nothing {
            Timber.tag(TAG).w(message)
            val uri = Uri.parse(APPLE_LICENSE_URL)
            throw MediaDrmCallbackException(
                DataSpec(uri),
                uri,
                emptyMap(),
                0L,
                IOException(message),
            )
        }

        override fun executeProvisionRequest(
            uuid: UUID,
            request: ExoMediaDrm.ProvisionRequest,
        ): MediaDrmCallback.Response = provisionFallback.executeProvisionRequest(uuid, request)

        override fun executeKeyRequest(
            uuid: UUID,
            request: ExoMediaDrm.KeyRequest,
        ): MediaDrmCallback.Response {
            val track = trackProvider() ?: fail("Apple license requested but no Apple Music track info is registered for this item")
            val mediaToken = mediaToken ?: AppleMusicAudioProvider.mediaUserToken() ?: fail("Apple license requested without a Media-User-Token")
            val devToken = devToken ?: AppleMusicAudioProvider.devToken()
            val body =
                JSONObject()
                    .put("challenge", android.util.Base64.encodeToString(request.data, android.util.Base64.NO_WRAP))
                    .put("key-system", "com.widevine.alpha")
                    .put("uri", track.drmUri)
                    .put("adamId", track.adamId)
                    .put("isLibrary", false)
                    .put("user-initiated", true)
            val builder =
                Request
                    .Builder()
                    .url(APPLE_LICENSE_URL)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://music.apple.com")
                    .header("Referer", "https://music.apple.com/")
                    .header("User-Agent", APPLE_MUSIC_WEB_UA)
                    .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            devToken?.let { builder.header("Authorization", "Bearer $it") }
            builder.header("Media-User-Token", mediaToken)
            licenseClient.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    fail("Apple license exchange failed: HTTP ${response.code} ${text.take(200)}")
                }
                val json =
                    runCatching { JSONObject(text) }.getOrElse {
                        fail("Apple license response is not JSON: ${text.take(200)}")
                    }
                val status = json.optInt("status", -1)
                if (status != 0) {
                    val customer = json.optString("customerMessage").ifBlank { text.take(200) }
                    fail("Apple license exchange rejected (status=$status): $customer")
                }
                val license = json.optString("license")
                if (license.isBlank()) fail("Apple license response has no license field")
                val decoded =
                    runCatching { android.util.Base64.decode(license, android.util.Base64.DEFAULT) }.getOrElse {
                        fail("Apple license base64 decode failed: ${it.message}")
                    }
                return MediaDrmCallback.Response(decoded)
            }
        }
    }
}
