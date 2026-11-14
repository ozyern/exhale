/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import com.ozyern.exhale.constants.VisitorDataKey
import com.ozyern.exhale.utils.cipher.CipherDeobfuscator
import com.ozyern.exhale.utils.PlaybackLogManager
import com.ozyern.exhale.utils.PlaybackLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Refreshes guest session metadata conservatively, preserving the existing identity
 * when a refresh fails. Authenticated sessions are handled by the account login flow.
 */
object BotDetectionMitigator {
    private const val TAG = "BotDetectionMitigator"

    // Serialize token refreshes so concurrent failures cannot overwrite newer visitor data.
    private val rotationMutex = Mutex()
    private const val REFRESH_COOLDOWN_MS = 5 * 60 * 1000L
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val REFRESH_COOLDOWN_NANOS = REFRESH_COOLDOWN_MS * NANOS_PER_MILLISECOND
    private var lastRefreshNanos: Long? = null

    // Error reasons that indicate geographic restriction – NOT a bot signal.
    // Keep these specific to avoid false positives.
    private val GEO_ERROR_SIGNATURES = listOf(
        "not available in your country",
        "not available in your region",
        "not available in this country",
        "not available in this region",
        "geo-restricted",
        "GEO_RESTRICTED",
        "NOT_AVAILABLE_IN_THIS_COUNTRY",
        "only available in certain countries",
        "country restriction",
        "region restriction",
    )

    // Error reasons that strongly suggest bot / IP flagging by YouTube.
    private val BOT_ERROR_SIGNATURES = listOf(
        "Sign in to confirm",
        "confirm you're not a bot",
        "automated queries",
        "Error 2000",
        "This content isn't available on this device",
    )

    /**
     * Call this when a playback error occurs.
     * Returns true if rotation might help (looks like bot detection).
     */
    fun notifyPlaybackFailure(isLoggedIn: Boolean, errorMessage: String? = null): Boolean {
        if (isLoggedIn) return false
        if (isGeoError(errorMessage) || !isBotDetectionError(errorMessage)) return false

        return true
    }

    /**
     * Rotates the guest session by obtaining a fresh visitorData token while preserving locale.
     */
    suspend fun rotateGuestSession() = rotationMutex.withLock {
        if (!YouTube.cookie.isNullOrBlank()) return@withLock
        val now = System.nanoTime()
        val lastRefreshAtNanos = lastRefreshNanos
        if (lastRefreshAtNanos != null && now - lastRefreshAtNanos < REFRESH_COOLDOWN_NANOS) return@withLock
        lastRefreshNanos = now
        Timber.tag(TAG).i("Refreshing guest session metadata")
        PlaybackLogManager.log(
            PlaybackLogLevel.BOT,
            "Refreshing guest session",
            "Preserving current visitorData until replacement succeeds"
        )

        withContext(Dispatchers.IO) {
            // Snapshot locale so the new token is issued for the user's actual region.
            val currentLocale = YouTube.locale

            YouTube.refreshVisitorData().onSuccess { newData ->
                Timber.tag(TAG).i("New visitorData obtained successfully for region ${currentLocale.gl}.")
                
                // Keep the replacement token across process restarts.
                CipherDeobfuscator.appContext.dataStore.edit { settings ->
                    settings[VisitorDataKey] = newData
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag(TAG).e(e, "Failed to refresh visitorData during rotation")
            }
        }
        
    }

    fun isGeoError(message: String?): Boolean =
        message != null && GEO_ERROR_SIGNATURES.any { message.contains(it, ignoreCase = true) }

    fun isBotDetectionError(message: String?): Boolean {
        return message != null && BOT_ERROR_SIGNATURES.any { message.contains(it, ignoreCase = true) }
    }
}
