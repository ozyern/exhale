/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0 (ui/screens/settings/SourceCheckService.kt).
 *
 * Health probes behind the "Check source" buttons in Settings → Lossless Sources. The Qobuz-backup,
 * Amazon, JioSaavn and YouTube probes were not ported (no resolver for the first two in this app;
 * JioSaavn/YouTube have their own paths in Exhale). A Source Pool feed probe was added.
 */

package com.ozyern.exhale.playback.lossless.sources

import android.content.Context
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicAudioProvider
import com.ozyern.exhale.playback.lossless.sources.constants.AudioSourceType
import com.ozyern.exhale.playback.lossless.sources.deezer.DeezerAudioProvider
import com.ozyern.exhale.playback.lossless.sources.pool.PoolAccountManager
import com.ozyern.exhale.playback.lossless.sources.qobuz.QobuzAudioProvider
import com.ozyern.exhale.playback.lossless.sources.qobuz.QobuzToken
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalAccountManager
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalAudioProvider
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalInstanceHealthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Verdict of a source health probe. Distinct from a boolean so the row (and the result dialog)
 * can tell "structurally cannot play in this build" (UNSUPPORTED) apart from "down right now"
 * (UNREACHABLE) and "nothing configured" (NOT_CONFIGURED) — those need completely different advice.
 */
enum class SourceCheckStatus {
    READY,
    DEGRADED,
    NOT_CONFIGURED,
    UNSUPPORTED,
    UNREACHABLE,
}

data class SourceCheckResult(
    val status: SourceCheckStatus,
    val summary: String,
    val checkedAtMs: Long = System.currentTimeMillis(),
) {
    val healthy: Boolean get() = status == SourceCheckStatus.READY
}

object SourceCheckService {
    private val _results = MutableStateFlow<Map<AudioSourceType, SourceCheckResult>>(emptyMap())

    /** Last verdict per source — survives recomposition, navigation and re-checks. */
    val results: StateFlow<Map<AudioSourceType, SourceCheckResult>> = _results.asStateFlow()

    private val _poolResult = MutableStateFlow<SourceCheckResult?>(null)

    /** Last verdict of the Source Pool feed probe. */
    val poolResult: StateFlow<SourceCheckResult?> = _poolResult.asStateFlow()

    fun cachedResult(source: AudioSourceType): SourceCheckResult? = _results.value[source]

    suspend fun check(source: AudioSourceType, context: Context): SourceCheckResult {
        val result =
            withContext(Dispatchers.IO) {
                when (source) {
                    AudioSourceType.MONOCHROME -> checkMonochrome()
                    AudioSourceType.TIDAL -> checkTidal(context)
                    AudioSourceType.QOBUZ -> checkQobuz(context)
                    AudioSourceType.DEEZER -> checkDeezer(context)
                    AudioSourceType.APPLE -> checkAppleMusic()
                    AudioSourceType.RADIOPARADISE ->
                        SourceCheckResult(
                            status = SourceCheckStatus.READY,
                            summary = "Radio Paradise: 7 lossless FLAC channels online (Main, Mellow, Rock, World, Serenity, 2050, KFAT).",
                        )
                    AudioSourceType.AMAZON ->
                        SourceCheckResult(
                            status = SourceCheckStatus.UNSUPPORTED,
                            summary = "Amazon Music serves CENC-protected audio and no decryption step is shipped, " +
                                "so pooled Amazon accounts are parsed but never used for playback (same as ArchiveTune).",
                        )
                    AudioSourceType.QOBUZ_BACKUP ->
                        SourceCheckResult(
                            status = SourceCheckStatus.UNSUPPORTED,
                            summary = "The Qobuz community backup relay is not available in Exhale.",
                        )
                    AudioSourceType.JIOSAAVN, AudioSourceType.YOUTUBE ->
                        SourceCheckResult(
                            status = SourceCheckStatus.READY,
                            summary = "Handled by Exhale's built-in streaming paths.",
                        )
                }
            }
        _results.update { it + (source to result) }
        return result
    }

    /** Probes the ArchivePool feed with the key currently in force and refreshes the account cache. */
    suspend fun checkPool(context: Context): SourceCheckResult {
        val result =
            withContext(Dispatchers.IO) {
                when {
                    !PoolAccountManager.isEnabled ->
                        SourceCheckResult(
                            status = SourceCheckStatus.NOT_CONFIGURED,
                            summary = "No Source Pool URL configured (SOURCE_PROVIDER_URL is blank and no runtime URL was entered).",
                        )
                    !PoolAccountManager.hasReadKey ->
                        SourceCheckResult(
                            status = SourceCheckStatus.NOT_CONFIGURED,
                            summary = "No Source Pool API key. Create a read key (atp_…) in the pool dashboard and paste it above, " +
                                "or bake it into the build via SOURCE_PROVIDER_KEY in local.properties.",
                        )
                    else -> {
                        val ok = runCatching { PoolAccountManager.refresh(context, force = true) }.getOrDefault(false)
                        val counts =
                            "tidal=${PoolAccountManager.tidalAccounts().size} " +
                                "qobuz=${PoolAccountManager.qobuzAccounts().size} " +
                                "deezer=${PoolAccountManager.deezerAccounts().size} " +
                                "apple=${PoolAccountManager.appleMusicAccounts().size} " +
                                "amazon=${PoolAccountManager.amazonAccounts().size}"
                        val error = PoolAccountManager.lastFeedError
                        val poolDisplay = PoolAccountManager.baseUrlOrNull
                            ?.takeUnless { it.contains("archivepool.vercel", ignoreCase = true) }
                            ?.let { "at $it" }
                            ?: ""
                        when {
                            error != null && !ok ->
                                SourceCheckResult(SourceCheckStatus.UNREACHABLE, "$error\nCached accounts: $counts")
                            error != null ->
                                SourceCheckResult(SourceCheckStatus.DEGRADED, "$error\nUsing cached accounts: $counts")
                            ok ->
                                SourceCheckResult(
                                    SourceCheckStatus.READY,
                                    "ArchiveTune pool feed $poolDisplay answered and decrypted. Accounts: $counts".trim(),
                                )
                            else ->
                                SourceCheckResult(
                                    SourceCheckStatus.DEGRADED,
                                    "ArchiveTune pool feed answered but returned no accounts yet. Accounts: $counts",
                                )
                        }
                    }
                }
            }
        _poolResult.value = result
        return result
    }

    private suspend fun checkTidal(context: Context): SourceCheckResult {
        PoolAccountManager.refresh(context, force = false)
        val accounts = PoolAccountManager.tidalAccounts()
        if (accounts.isEmpty()) {
            val healthyInstances = runCatching { TidalInstanceHealthManager.healthyUrls(context).size }.getOrDefault(0)
            return if (healthyInstances > 0) {
                SourceCheckResult(
                    status = SourceCheckStatus.DEGRADED,
                    summary = "No Tidal accounts in the source pool, but $healthyInstances public " +
                        "instance(s) are reachable — playback works at reduced quality (may serve previews). " +
                        "For lossless, sign in with your own Tidal account below.",
                )
            } else {
                SourceCheckResult(
                    status = SourceCheckStatus.NOT_CONFIGURED,
                    summary = "No Tidal accounts in the source pool and no public instance is reachable. " +
                        "Sign in with your own Tidal account below, or refresh the pool to pull fresh accounts.",
                )
            }
        }
        val premium = accounts.count { it.premium }

        val probeAccount = accounts.firstOrNull { it.premium } ?: accounts.first()
        val session = runCatching { TidalAccountManager.buildSessionFromBearer(probeAccount.token) }.getOrNull()
        val subscription =
            session?.userId?.let { userId ->
                runCatching { TidalAccountManager.fetchSubscription(probeAccount.token, userId) }.getOrNull()
            }
        val accountLabel =
            when {
                session == null -> "token rejected by the Tidal API (expired — refresh the pool)"
                subscription == TidalAccountManager.Subscription.PREMIUM -> "valid (premium — lossless available)"
                subscription == TidalAccountManager.Subscription.FREE -> "valid but FREE (previews only, no lossless)"
                else -> "valid, subscription tier unknown"
            }
        val accountPathReady = session != null && subscription != TidalAccountManager.Subscription.FREE

        val healthyInstances = runCatching { TidalInstanceHealthManager.healthyUrls(context).size }.getOrDefault(0)

        val summary =
            buildString {
                append("Pool accounts: ${accounts.size} ($premium premium)\n")
                append("Account stream path: $accountLabel\n")
                append("Public instances (optional fallback): $healthyInstances healthy")
                if (accountPathReady) {
                    append("\n\nTidal source is READY via the account path.")
                    if (healthyInstances == 0) {
                        append(
                            " No public instance is reachable, but none is needed — " +
                                "the pool's subscriber token streams directly from Tidal.",
                        )
                    }
                } else {
                    append("\n\nTidal source is ")
                    append(
                        if (healthyInstances > 0) {
                            "PARTIALLY ready: the account path failed, so playback will fall back to a public " +
                                "instance (lower quality, may serve previews)."
                        } else {
                            "NOT ready: the account path failed and no public instance is reachable. " +
                                "Refresh the pool to pull fresh tokens, or add a private Tidal instance."
                        },
                    )
                }
            }
        return SourceCheckResult(
            status =
                when {
                    accountPathReady -> SourceCheckStatus.READY
                    healthyInstances > 0 -> SourceCheckStatus.DEGRADED
                    else -> SourceCheckStatus.UNREACHABLE
                },
            summary = summary,
        )
    }

    private suspend fun checkQobuz(context: Context): SourceCheckResult {
        PoolAccountManager.refresh(context, force = false)
        val accounts = PoolAccountManager.qobuzAccounts()
        if (accounts.isEmpty()) {
            return SourceCheckResult(
                status = SourceCheckStatus.NOT_CONFIGURED,
                summary = "No Qobuz accounts in the source pool. Sign in with your own Qobuz account " +
                    "(token + app_id + app_secret) below, or refresh the pool to pull fresh accounts.",
            )
        }
        val premium = accounts.count { it.premium }

        val first = accounts.first()
        val token =
            QobuzToken(
                token = first.token,
                appId = first.appId,
                appSecret = first.appSecret,
                label = "Source Pool",
                subscription = if (first.premium) "premium" else "",
            )
        val health = QobuzAudioProvider.verifyToken(token, probeTrackId = null, formatId = 5)
        return when (health) {
            TidalAudioProvider.InstanceHealth.HEALTHY ->
                SourceCheckResult(
                    status = SourceCheckStatus.READY,
                    summary = "Pool accounts: ${accounts.size} ($premium premium)\n" +
                        "First token probe: healthy (premium)\n" +
                        "Qobuz source is READY.",
                )

            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                SourceCheckResult(
                    status = SourceCheckStatus.DEGRADED,
                    summary = "Pool accounts: ${accounts.size} ($premium premium)\n" +
                        "First token probe: preview-only (no subscription)\n" +
                        "The token works, but without a subscription only 30-second previews will play.",
                )

            else ->
                SourceCheckResult(
                    status = SourceCheckStatus.UNREACHABLE,
                    summary = "Pool accounts: ${accounts.size} ($premium premium)\n" +
                        "First token probe: unreachable (token invalid / app_secret mismatch)\n" +
                        "Qobuz source is NOT ready — refresh the pool to pull fresh tokens, " +
                        "or add your own token below.",
                )
        }
    }

    private suspend fun checkAppleMusic(): SourceCheckResult {
        val mediaToken = AppleMusicAudioProvider.mediaUserToken()
        val devToken = AppleMusicAudioProvider.devToken()
        if (mediaToken == null || devToken == null) {
            val missing =
                buildList {
                    if (devToken == null) add("dev (Bearer) token")
                    if (mediaToken == null) add("Media-User-Token")
                }.joinToString(" and ")
            val pool = PoolAccountManager.appleMusicAccounts()
            if (pool.isNotEmpty()) {
                return SourceCheckResult(
                    status = SourceCheckStatus.DEGRADED,
                    summary = "Signed in via the Source Pool (%d shared Apple Music account%s), but ".format(
                        pool.size,
                        if (pool.size == 1) "" else "s",
                    ) +
                        "playback also needs a developer token and none is set. Sign in once via " +
                        "Sign in with Apple Music (web) — it fetches both tokens automatically.",
                )
            }
            return SourceCheckResult(
                status = SourceCheckStatus.NOT_CONFIGURED,
                summary = "No $missing. Sign in via Sign in with Apple Music (web) " +
                    "— the token pair is fetched automatically — or paste the tokens below.",
            )
        }
        return runCatching {
            val storefront = AppleMusicAudioProvider.resolveStorefront()
            SourceCheckResult(
                status = SourceCheckStatus.READY,
                summary = "Apple Music reachable — storefront '$storefront' resolved from your token.",
            )
        }.getOrElse {
            SourceCheckResult(
                status = SourceCheckStatus.UNREACHABLE,
                summary = "Token present but the API rejected it (${it.message}). Sign in again — " +
                    "a fresh token pair is fetched automatically.",
            )
        }
    }

    private suspend fun checkDeezer(context: Context): SourceCheckResult {
        // Not forced: a throttled pool refresh (the 6h worker keeps it warm) already answers
        // "are credentials available", and a forced refresh on every tap hammered the pool host.
        PoolAccountManager.refresh(context, force = false)
        val availability = DeezerAudioProvider.accountAvailability()
        if (availability.total == 0) {
            return SourceCheckResult(
                status = SourceCheckStatus.NOT_CONFIGURED,
                summary = "No Deezer credentials available. Sign in with your own Deezer account below, " +
                    "or refresh the pool to pick up shared accounts.",
            )
        }
        val origin =
            buildList {
                if (availability.manual) {
                    add("your own account${if (availability.manualPremium) " (premium)" else ""}")
                }
                if (availability.pooled > 0) {
                    add("${availability.pooled} pool account(s), ${availability.pooledPremium} premium")
                }
            }.joinToString(" + ")
        val info = DeezerAudioProvider.verifyPreferredAccount()
        return if (info == null) {
            SourceCheckResult(
                status = SourceCheckStatus.UNREACHABLE,
                summary = "Found $origin, but the Deezer gateway rejected the credential it would use " +
                    "first. Sign in again below, or refresh the pool to pull fresh accounts.",
            )
        } else {
            val tier = if (info.lossless) "lossless (FLAC) available" else "no lossless — 320kbps MP3 at best"
            SourceCheckResult(
                status = SourceCheckStatus.READY,
                summary = "Credentials: $origin. Verified as '${info.name}' — $tier. Deezer source is READY.",
            )
        }
    }

    private fun checkMonochrome(): SourceCheckResult {
        val probe = LosslessStreamResolver.probeMonochromeMirrors()
        return if (probe.mirror != null) {
            SourceCheckResult(
                status = SourceCheckStatus.READY,
                summary = "Monochrome FLAC answered from ${probe.mirror.removePrefix("https://")}. Free lossless FLAC streaming active.",
            )
        } else {
            SourceCheckResult(
                status = SourceCheckStatus.UNREACHABLE,
                summary = "No Monochrome mirror answered (${probe.failure ?: "no response"}).",
            )
        }
    }
}
