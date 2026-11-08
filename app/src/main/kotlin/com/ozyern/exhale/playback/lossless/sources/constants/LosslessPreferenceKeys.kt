/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0.
 * https://github.com/4nx3b/ArchiveTune
 *
 * Preference keys and enums used by the lossless source stack (Source Pool, Tidal, Qobuz,
 * Deezer, Apple Music). Key names are kept identical to ArchiveTune's
 * `constants/PreferenceKeys.kt`, `constants/ForkPreferenceKeys.kt` and
 * `constants/AppleMusicKeys.kt` so the ported providers behave exactly as upstream.
 *
 * All keys live in the app's single Preferences DataStore ("settings").
 */

package com.ozyern.exhale.playback.lossless.sources.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// ── Global switch / Source Pool ─────────────────────────────────────────────────────────────

/**
 * Master switch for lossless streaming sources. When off, [com.ozyern.exhale.playback.lossless.sources.LosslessStreamResolver]
 * is never consulted.
 */
val LosslessStreamingEnabledKey = booleanPreferencesKey("losslessStreamingEnabled")

/**
 * Exhale-specific: runtime override for the Source Pool read key (`atp_...`). When blank the
 * build-time `BuildConfig.SOURCE_PROVIDER_KEY` is used.
 */
val PoolApiKeyKey = stringPreferencesKey("sourcePoolApiKey")

/**
 * Exhale-specific: runtime override for the Source Pool base URL. When blank the build-time
 * `BuildConfig.SOURCE_PROVIDER_URL` is used.
 */
val PoolBaseUrlKey = stringPreferencesKey("sourcePoolBaseUrl")

/** ArchiveTune: shows the manual sign-in entries (Tidal / Qobuz / Deezer / Apple Music). */
val ManualSourceLoginEnabledKey = booleanPreferencesKey("dev_manual_source_login")

// ── Tidal ───────────────────────────────────────────────────────────────────────────────────

val TidalEnabledKey = booleanPreferencesKey("tidalEnabled")
val TidalAudioQualityKey = stringPreferencesKey("tidalAudioQuality")
val TidalAccountNameKey = stringPreferencesKey("tidal_account_name")

/** Newline-separated public instance base URLs added by the user. */
val TidalInstancesKey = stringPreferencesKey("tidalInstances")

/** JSON persisted by [com.ozyern.exhale.playback.lossless.sources.tidal.TidalInstanceHealthManager]. */
val TidalVerifiedInstancesKey = stringPreferencesKey("tidalVerifiedInstances")

/** Last track id that resolved through Tidal; reused as the health-probe track. */
val TidalLastProbeTrackKey = stringPreferencesKey("tidalLastProbeTrack")

val TidalAccessTokenKey = stringPreferencesKey("tidalAccessToken")
val TidalRefreshTokenKey = stringPreferencesKey("tidalRefreshToken")
val TidalTokenExpiryKey = longPreferencesKey("tidalTokenExpiry")
val TidalSubscriptionKey = stringPreferencesKey("tidalSubscription")

/** One of TidalAccountManager.FLOW_OAUTH / FLOW_PKCE / FLOW_WEBCAPTURE. */
val TidalAuthFlowKey = stringPreferencesKey("tidalAuthFlow")

val TidalCountryCodeKey = stringPreferencesKey("tidalCountryCode")
val TidalUserIdKey = longPreferencesKey("tidalUserId")

val TidalNeedsReloginKey = booleanPreferencesKey("tidalNeedsRelogin")

/** Try the (personal, then pooled) Tidal account token before the public instances. */
val TidalAccountFirstKey = booleanPreferencesKey("tidalAccountFirst")

enum class TidalSubscriptionStatus {
    UNKNOWN,
    PREMIUM,
    FREE,
}

enum class TidalAudioQuality {
    AAC_320,
    FLAC,
    HI_RES_LOSSLESS,
}

val TidalAudioQualityOptions =
    listOf(
        TidalAudioQuality.AAC_320,
        TidalAudioQuality.FLAC,
        TidalAudioQuality.HI_RES_LOSSLESS,
    )

// ── Apple Music ─────────────────────────────────────────────────────────────────────────────

enum class AppleMusicQuality {
    AAC,
    LOSSLESS,
    HI_RES_LOSSLESS,
}

val AppleMusicQualityKey = stringPreferencesKey("appleMusicQuality")

val AppleMusicSourceEnabledKey = booleanPreferencesKey("appleMusicSourceEnabled")

val AppleMusicMediaUserTokenKey = stringPreferencesKey("appleMusicMediaUserToken")
val AppleMusicDevTokenKey = stringPreferencesKey("appleMusicDevToken")

// ── Qobuz ───────────────────────────────────────────────────────────────────────────────────

val QobuzEnabledKey = booleanPreferencesKey("qobuzEnabled")

/** Newline-separated proxy instance base URLs added by the user. */
val QobuzInstancesKey = stringPreferencesKey("qobuzInstances")

val QobuzVerifiedInstancesKey = stringPreferencesKey("qobuzVerifiedInstances")

val QobuzLastProbeTrackKey = stringPreferencesKey("qobuzLastProbeTrack")

/** JSON list produced by QobuzToken.listToJson. */
val QobuzTokensKey = stringPreferencesKey("qobuzTokens")

val QobuzVerifiedTokensKey = stringPreferencesKey("qobuzVerifiedTokens")

enum class QobuzAudioQuality {
    FLAC,
    HI_RES,
    MAX,
}

val QobuzAudioQualityOptions =
    listOf(
        QobuzAudioQuality.FLAC,
        QobuzAudioQuality.HI_RES,
        QobuzAudioQuality.MAX,
    )

val QobuzAudioQualityKey = stringPreferencesKey("qobuzAudioQuality")

fun QobuzAudioQuality.toFormatId(): Int =
    when (this) {
        QobuzAudioQuality.FLAC -> 6
        QobuzAudioQuality.HI_RES -> 7
        QobuzAudioQuality.MAX -> 27
    }

// ── Deezer ──────────────────────────────────────────────────────────────────────────────────

val DeezerEnabledKey = booleanPreferencesKey("deezerEnabled")

/** Manually captured `arl` cookie, stored apart from the pool cache (which is rewritten). */
val DeezerArlKey = stringPreferencesKey("deezerArl")

val DeezerAccountNameKey = stringPreferencesKey("deezerAccountName")

val DeezerAccountPremiumKey = booleanPreferencesKey("deezerAccountPremium")

enum class DeezerAudioQuality {
    FLAC,
    MP3_320,
    MP3_128,
}

val DeezerAudioQualityOptions =
    listOf(
        DeezerAudioQuality.FLAC,
        DeezerAudioQuality.MP3_320,
        DeezerAudioQuality.MP3_128,
    )

val DeezerAudioQualityKey = stringPreferencesKey("deezerAudioQuality")

fun DeezerAudioQuality.toFormatName(): String =
    when (this) {
        DeezerAudioQuality.FLAC -> "FLAC"
        DeezerAudioQuality.MP3_320 -> "MP3_320"
        DeezerAudioQuality.MP3_128 -> "MP3_128"
    }

// ── Source chain ────────────────────────────────────────────────────────────────────────────

/**
 * Every source the resolution chain knows about. Kept identical to ArchiveTune so
 * [com.ozyern.exhale.playback.lossless.sources.audiosource.AudioSourceConfig] (order parsing) and
 * [com.ozyern.exhale.playback.lossless.sources.audiosource.DirectStream] compile unchanged.
 *
 * In Exhale: QOBUZ_BACKUP and AMAZON have no resolver (same as ArchiveTune for AMAZON;
 * ArchiveTune's Qobuz-backup relay was not ported), JIOSAAVN maps to the app's existing
 * `SaavnService` gate, and YOUTUBE is the terminal fallback.
 */
// ── Monochrome (FLAC) ──────────────────────────────────────────────────────────────────────
val MonochromeEnabledKey = booleanPreferencesKey("monochromeEnabled")

enum class AudioSourceType {
    MONOCHROME,
    TIDAL,
    QOBUZ,
    QOBUZ_BACKUP,
    DEEZER,
    APPLE,
    RADIOPARADISE,
    AMAZON,
    JIOSAAVN,
    YOUTUBE,
}

/** Comma-separated [AudioSourceType] names; see AudioSourceConfig.resolutionChain. */
val AudioSourceOrderKey = stringPreferencesKey("audioSourceOrder")

/** Per-song pinned source, encoded by AudioSourceConfig's SongSourceOverride. */
val SongSourceOverrideKey = stringPreferencesKey("songSourceOverride")
