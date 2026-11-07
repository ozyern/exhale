/*
 * Startup wiring for the lossless-source stack, ported from ArchiveTune's App.kt (2026, © Rukamori,
 * GPL-3.0). Everything here is the glue ArchiveTune keeps in its Application class:
 *
 *  - Apple Music token providers (user tokens first, then the first pool account) + storefront
 *    cache invalidation when the Media-User-Token changes.
 *  - Deezer manual ARL push into DeezerAudioProvider whenever the preference changes.
 *  - Runtime Source Pool config (in-app API key / URL override) → PoolAccountManager.
 *  - Startup: pool cache load + refresh, Tidal instance health scan (with discovery when a pool URL
 *    is configured), Tidal/Qobuz probe-track restore, auto-scraped Apple developer token.
 *  - The 6-hourly SourceRefreshWorker schedule.
 */

package com.ozyern.exhale.playback.lossless.sources

import android.content.Context
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicAudioProvider
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicProvider
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicDevTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicMediaUserTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAccountPremiumKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerArlKey
import com.ozyern.exhale.playback.lossless.sources.constants.LosslessStreamingEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.PoolApiKeyKey
import com.ozyern.exhale.playback.lossless.sources.constants.PoolBaseUrlKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzLastProbeTrackKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalLastProbeTrackKey
import com.ozyern.exhale.playback.lossless.sources.constants.getAsync
import com.ozyern.exhale.playback.lossless.sources.deezer.DeezerAudioProvider
import com.ozyern.exhale.playback.lossless.sources.pool.PoolAccountManager
import com.ozyern.exhale.playback.lossless.sources.qobuz.QobuzAudioProvider
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalAudioProvider
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalInstanceHealthManager
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

object LosslessSources {
    private const val TAG = "LosslessSources"
    private val started = AtomicBoolean(false)

    @Volatile private var appleMusicDevTokenCache: String = ""

    @Volatile private var appleMusicMediaUserTokenCache: String = ""

    /**
     * Idempotent. Call once from Application.onCreate with an application-lifetime scope.
     * Cheap on the main thread: every network-bound step is launched on IO.
     */
    fun start(context: Context, scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val dataStore = app.dataStore

        AppleMusicProvider.devTokenProvider = {
            appleMusicDevTokenCache.ifBlank { null } ?: AppleMusicProvider.appleMusicToken.takeIf { it.isNotBlank() }
        }
        AppleMusicProvider.mediaUserTokenProvider = {
            appleMusicMediaUserTokenCache.ifBlank { null }
                ?: PoolAccountManager.appleMusicAccounts().firstOrNull()?.mediaUserToken
        }

        // Runtime pool config (in-app key/URL) → PoolAccountManager, then the pool + Tidal startup
        // work. The first emission also performs the initial load, so the startup refresh sees
        // the user's override rather than only the build-time key.
        scope.launch(Dispatchers.IO) {
            var first = true
            dataStore.data
                .map { Triple(it[PoolBaseUrlKey].orEmpty(), it[PoolApiKeyKey].orEmpty(), it[LosslessStreamingEnabledKey]) }
                .distinctUntilChanged()
                .collect { (url, key, enabledPref) ->
                    PoolAccountManager.applyRuntimeConfig(url, key)
                    val enabled = enabledPref ?: LosslessStreamResolver.defaultEnabled()
                    if (!enabled) {
                        first = false
                        return@collect
                    }
                    if (PoolAccountManager.isEnabled) {
                        if (first) runCatching { PoolAccountManager.loadCached(app) }
                        launch {
                            runCatching { PoolAccountManager.refresh(app, force = !first) }
                                .onFailure { Timber.tag(TAG).w(it, "Pool account refresh failed") }
                        }
                    }
                    if (first) {
                        first = false
                        launch { startupTidalScan(app) }
                        launch { restoreQobuzProbeTrack(app) }
                        launch {
                            runCatching { if (appleMusicDevTokenCache.isBlank()) AppleMusicProvider.refreshToken() }
                        }
                    }
                }
        }

        scope.launch(Dispatchers.IO) {
            var lastMedia = appleMusicMediaUserTokenCache
            dataStore.data
                .map { (it[AppleMusicDevTokenKey]?.trim().orEmpty()) to (it[AppleMusicMediaUserTokenKey]?.trim().orEmpty()) }
                .distinctUntilChanged()
                .collect { (newDev, newMedia) ->
                    if (newMedia != lastMedia) {
                        lastMedia = newMedia
                        AppleMusicAudioProvider.clearStorefrontCache()
                    }
                    appleMusicDevTokenCache = newDev
                    appleMusicMediaUserTokenCache = newMedia
                }
        }

        scope.launch(Dispatchers.IO) {
            dataStore.data
                .map { (it[DeezerArlKey] ?: "") to (it[DeezerAccountPremiumKey] ?: false) }
                .distinctUntilChanged()
                .collect { (arl, premium) -> DeezerAudioProvider.setManualArl(arl, premium) }
        }

        scope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[LosslessStreamingEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        runCatching { SourceRefreshWorker.schedule(app) }
                            .onFailure { Timber.tag(TAG).w(it, "Could not schedule SourceRefreshWorker") }
                    } else {
                        runCatching { SourceRefreshWorker.cancel(app) }
                            .onFailure { Timber.tag(TAG).w(it, "Could not cancel SourceRefreshWorker") }
                    }
                }
        }
    }

    private suspend fun startupTidalScan(app: Context) {
        try {
            if (app.dataStore.getAsync(TidalEnabledKey, true)) {
                app.dataStore.getAsync(TidalLastProbeTrackKey)?.takeIf { it.isNotBlank() }?.let {
                    if (TidalAudioProvider.lastResolvedTrackId.isNullOrBlank()) {
                        TidalAudioProvider.seedProbeTrack(it)
                    }
                }
                val autoDiscover = PoolAccountManager.isEnabled || BuildConfig.SOURCE_PROVIDER_URL.isNotBlank()
                TidalInstanceHealthManager.refresh(app, includeDiscovery = autoDiscover, staggered = true)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Tidal instance startup health scan failed")
        }
    }

    private suspend fun restoreQobuzProbeTrack(app: Context) {
        try {
            if (app.dataStore.getAsync(QobuzEnabledKey, false)) {
                app.dataStore.getAsync(QobuzLastProbeTrackKey)?.takeIf { it.isNotBlank() }?.let {
                    QobuzAudioProvider.seedProbeTrack(it)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Qobuz probe-track restore failed")
        }
    }
}
