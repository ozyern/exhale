package com.ozyern.exhale.ui.screens.settings.lossless

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.playback.lossless.sources.LosslessStreamResolver
import com.ozyern.exhale.playback.lossless.sources.SourceCheckResult
import com.ozyern.exhale.playback.lossless.sources.SourceCheckService
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicAudioProvider
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicProvider
import com.ozyern.exhale.playback.lossless.sources.audiosource.AudioSourceConfig
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicDevTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicMediaUserTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicQuality
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicQualityKey
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicSourceEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.AudioSourceOrderKey
import com.ozyern.exhale.playback.lossless.sources.constants.AudioSourceType
import com.ozyern.exhale.playback.lossless.sources.constants.MonochromeEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAccountNameKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAccountPremiumKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerArlKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAudioQuality
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAudioQualityKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.LosslessStreamingEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.PoolApiKeyKey
import com.ozyern.exhale.playback.lossless.sources.constants.PoolBaseUrlKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzAudioQuality
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzAudioQualityKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzInstancesKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzTokensKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzVerifiedInstancesKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzVerifiedTokensKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAccessTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAccountFirstKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAccountNameKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAudioQuality
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAudioQualityKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAuthFlowKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalCountryCodeKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalInstancesKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalNeedsReloginKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalRefreshTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalSubscriptionKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalSubscriptionStatus
import com.ozyern.exhale.playback.lossless.sources.constants.TidalTokenExpiryKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalUserIdKey
import com.ozyern.exhale.playback.lossless.sources.deezer.DeezerAudioProvider
import com.ozyern.exhale.playback.lossless.sources.pool.PoolAccountManager
import com.ozyern.exhale.playback.lossless.sources.qobuz.QobuzAudioProvider
import com.ozyern.exhale.playback.lossless.sources.qobuz.QobuzToken
import com.ozyern.exhale.playback.lossless.sources.qobuz.SourceInputParsing
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalInstanceHealthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** Snapshot of every lossless-source preference, read straight from the shared DataStore. */
data class LosslessSourcesUiState(
    val losslessEnabled: Boolean = LosslessStreamResolver.defaultEnabled(),
    val poolApiKey: String = "",
    val poolBaseUrl: String = "",
    val poolBuildTimeKeyPresent: Boolean = com.ozyern.exhale.BuildConfig.SOURCE_PROVIDER_KEY.isNotBlank(),
    val poolEffectiveBaseUrl: String = com.ozyern.exhale.BuildConfig.SOURCE_PROVIDER_URL,
    val sourceOrder: List<AudioSourceType> =
        listOf(AudioSourceType.MONOCHROME, AudioSourceType.TIDAL, AudioSourceType.QOBUZ, AudioSourceType.DEEZER, AudioSourceType.APPLE),
    // Monochrome
    val monochromeEnabled: Boolean = true,
    // Tidal
    val tidalEnabled: Boolean = true,
    val tidalQuality: TidalAudioQuality = TidalAudioQuality.HI_RES_LOSSLESS,
    val tidalAccountFirst: Boolean = true,
    val tidalAccountName: String = "",
    val tidalSignedIn: Boolean = false,
    val tidalNeedsRelogin: Boolean = false,
    val tidalSubscription: TidalSubscriptionStatus = TidalSubscriptionStatus.UNKNOWN,
    val tidalInstances: String = "",
    val tidalHealthyInstances: Int = 0,
    // Qobuz
    val qobuzEnabled: Boolean = true,
    val qobuzQuality: QobuzAudioQuality = QobuzAudioQuality.MAX,
    val qobuzTokens: List<QobuzToken> = emptyList(),
    val qobuzInstances: String = "",
    // Deezer
    val deezerEnabled: Boolean = true,
    val deezerQuality: DeezerAudioQuality = DeezerAudioQuality.FLAC,
    val deezerAccountName: String = "",
    val deezerSignedIn: Boolean = false,
    val deezerAccountPremium: Boolean = false,
    // Apple Music
    val appleEnabled: Boolean = true,
    val appleQuality: AppleMusicQuality = AppleMusicQuality.HI_RES_LOSSLESS,
    val appleMediaUserToken: String = "",
    val appleDevToken: String = "",
)

data class PoolCounts(
    val tidal: Int = 0,
    val qobuz: Int = 0,
    val deezer: Int = 0,
    val apple: Int = 0,
    val amazon: Int = 0,
) {
    val total: Int get() = tidal + qobuz + deezer + apple + amazon
}

@HiltViewModel
class LosslessSourcesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val dataStore: DataStore<Preferences> = context.dataStore

    private companion object {
        val LOSSLESS_TYPES = setOf(
            AudioSourceType.MONOCHROME,
            AudioSourceType.TIDAL,
            AudioSourceType.QOBUZ,
            AudioSourceType.DEEZER,
            AudioSourceType.APPLE
        )
    }

    val uiState: StateFlow<LosslessSourcesUiState> =
        dataStore.data
            .map { p ->
                LosslessSourcesUiState(
                    losslessEnabled = p[LosslessStreamingEnabledKey] ?: LosslessStreamResolver.defaultEnabled(),
                    poolApiKey = p[PoolApiKeyKey].orEmpty(),
                    poolBaseUrl = p[PoolBaseUrlKey].orEmpty(),
                    poolEffectiveBaseUrl = PoolAccountManager.baseUrlOrNull.orEmpty(),
                    sourceOrder = AudioSourceConfig.parseOrder(p[AudioSourceOrderKey]?.ifBlank { null }).filter { it in LOSSLESS_TYPES },
                    monochromeEnabled = p[MonochromeEnabledKey] ?: true,
                    tidalEnabled = p[TidalEnabledKey] ?: true,
                    tidalQuality = enumOrDefault(p[TidalAudioQualityKey], TidalAudioQuality.HI_RES_LOSSLESS),
                    tidalAccountFirst = p[TidalAccountFirstKey] ?: true,
                    tidalAccountName = p[TidalAccountNameKey].orEmpty(),
                    tidalSignedIn = !p[TidalAccessTokenKey].isNullOrBlank(),
                    tidalNeedsRelogin = p[TidalNeedsReloginKey] ?: false,
                    tidalSubscription = enumOrDefault(p[TidalSubscriptionKey], TidalSubscriptionStatus.UNKNOWN),
                    tidalInstances = p[TidalInstancesKey].orEmpty(),
                    tidalHealthyInstances = runCatching { TidalInstanceHealthManager.healthyUrls(context).size }.getOrDefault(0),
                    qobuzEnabled = p[QobuzEnabledKey] ?: true,
                    qobuzQuality = enumOrDefault(p[QobuzAudioQualityKey], QobuzAudioQuality.MAX),
                    qobuzTokens = QobuzToken.listFromJson(p[QobuzTokensKey]),
                    qobuzInstances = p[QobuzInstancesKey].orEmpty(),
                    deezerEnabled = p[DeezerEnabledKey] ?: true,
                    deezerQuality = enumOrDefault(p[DeezerAudioQualityKey], DeezerAudioQuality.FLAC),
                    deezerAccountName = p[DeezerAccountNameKey].orEmpty(),
                    deezerSignedIn = !p[DeezerArlKey].isNullOrBlank(),
                    deezerAccountPremium = p[DeezerAccountPremiumKey] ?: false,
                    appleEnabled = p[AppleMusicSourceEnabledKey] ?: true,
                    appleQuality = enumOrDefault(p[AppleMusicQualityKey], AppleMusicQuality.HI_RES_LOSSLESS),
                    appleMediaUserToken = p[AppleMusicMediaUserTokenKey].orEmpty(),
                    appleDevToken = p[AppleMusicDevTokenKey].orEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LosslessSourcesUiState())

    private val _poolCounts = MutableStateFlow(currentPoolCounts())
    val poolCounts: StateFlow<PoolCounts> = _poolCounts.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())

    /** Keys of long-running actions currently in flight ("pool", "TIDAL", "QOBUZ", ...). */
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    val checkResults: StateFlow<Map<AudioSourceType, SourceCheckResult>> = SourceCheckService.results
    val poolCheckResult: StateFlow<SourceCheckResult?> = SourceCheckService.poolResult

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    private val _poolStatusReport = MutableStateFlow<PoolAccountManager.PoolStatusReport?>(null)
    val poolStatusReport: StateFlow<PoolAccountManager.PoolStatusReport?> = _poolStatusReport.asStateFlow()

    fun consumeMessage() {
        _lastMessage.value = null
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PoolAccountManager.loadCached(context) }
            _poolCounts.value = currentPoolCounts()
            fetchPoolStatus()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private fun currentPoolCounts() =
        PoolCounts(
            tidal = PoolAccountManager.tidalAccounts().size,
            qobuz = PoolAccountManager.qobuzAccounts().size,
            deezer = PoolAccountManager.deezerAccounts().size,
            apple = PoolAccountManager.appleMusicAccounts().size,
            amazon = PoolAccountManager.amazonAccounts().size,
        )

    private fun <T> set(key: Preferences.Key<T>, value: T?) {
        viewModelScope.launch {
            dataStore.edit { prefs -> if (value == null) prefs.remove(key) else prefs[key] = value }
        }
    }

    private fun withBusy(tag: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.update { it + tag }
            try {
                block()
            } catch (t: Throwable) {
                Timber.tag("LosslessSettings").w(t, "Action %s failed", tag)
                _lastMessage.value = t.message ?: "Action failed"
            } finally {
                _busy.update { it - tag }
            }
        }
    }

    // ── Global / pool ─────────────────────────────────────────────────────────────────────────

    fun setLosslessEnabled(enabled: Boolean) {
        set(LosslessStreamingEnabledKey, enabled)
        if (!enabled) LosslessStreamResolver.clearAll()
        if (enabled && (uiState.value.poolApiKey.isNotBlank() || com.ozyern.exhale.BuildConfig.SOURCE_PROVIDER_KEY.isNotBlank() || PoolAccountManager.hasReadKey)) {
            refreshPool(force = true)
        }
    }

    fun savePoolApiKey(key: String) {
        val trimmed = key.trim()
        viewModelScope.launch {
            dataStore.edit { prefs ->
                if (trimmed.isBlank()) {
                    prefs.remove(PoolApiKeyKey)
                } else {
                    prefs[PoolApiKeyKey] = trimmed
                    // Entering a key is an explicit opt-in; turn the feature on unless the user
                    // already decided otherwise.
                    if (prefs[LosslessStreamingEnabledKey] == null) prefs[LosslessStreamingEnabledKey] = true
                }
            }
            PoolAccountManager.applyRuntimeConfig(uiState.value.poolBaseUrl, trimmed)
            checkPool()
        }
    }

    fun savePoolBaseUrl(url: String) {
        val trimmed = url.trim()
        viewModelScope.launch {
            dataStore.edit { prefs -> if (trimmed.isBlank()) prefs.remove(PoolBaseUrlKey) else prefs[PoolBaseUrlKey] = trimmed }
            PoolAccountManager.applyRuntimeConfig(trimmed, uiState.value.poolApiKey)
            checkPool()
        }
    }

    fun refreshPool(force: Boolean = true) =
        withBusy("pool") {
            PoolAccountManager.refresh(context, force = force)
            _poolCounts.value = currentPoolCounts()
            PoolAccountManager.lastFeedError?.let { _lastMessage.value = it }
        }

    fun checkPool() =
        withBusy("pool") {
            SourceCheckService.checkPool(context)
            _poolCounts.value = currentPoolCounts()
            fetchPoolStatus()
        }

    fun fetchPoolStatus() =
        withBusy("pool_status") {
            val report = PoolAccountManager.fetchPoolStatus(uiState.value.poolBaseUrl)
            _poolStatusReport.value = report
        }

    fun checkSource(source: AudioSourceType) =
        withBusy(source.name) {
            SourceCheckService.check(source, context)
            _poolCounts.value = currentPoolCounts()
        }

    /**
     * Persists the user's lossless order. Non-lossless entries of ArchiveTune's full order
     * (Qobuz backup, JioSaavn, YouTube) are kept after the lossless block so the stored string
     * stays a valid AudioSourceConfig order.
     */
    fun setSourceOrder(order: List<AudioSourceType>) {
        val lossless = order.filter { it in LOSSLESS_TYPES }.distinct()
        val rest = AudioSourceConfig.DEFAULT_ORDER.filterNot { it in LOSSLESS_TYPES }
        set(AudioSourceOrderKey, (lossless + rest).joinToString(",") { it.name })
        LosslessStreamResolver.clearAll()
    }

    fun clearResolvedCache() {
        LosslessStreamResolver.clearAll()
        _lastMessage.value = "Cleared resolved lossless streams"
    }

    // ── Monochrome ────────────────────────────────────────────────────────────────────────────

    fun setMonochromeEnabled(enabled: Boolean) {
        set(MonochromeEnabledKey, enabled)
        LosslessStreamResolver.clearAll()
    }

    // ── Tidal ─────────────────────────────────────────────────────────────────────────────────

    fun setTidalEnabled(enabled: Boolean) {
        set(TidalEnabledKey, enabled)
        if (enabled && PoolAccountManager.isEnabled) refreshPool(force = true)
    }

    fun setTidalQuality(quality: TidalAudioQuality) {
        set(TidalAudioQualityKey, quality.name)
        LosslessStreamResolver.clearAll()
    }

    fun setTidalAccountFirst(enabled: Boolean) = set(TidalAccountFirstKey, enabled)

    fun setTidalInstances(raw: String) {
        val normalized = SourceInputParsing.parseUrls(raw).joinToString("\n")
        set(TidalInstancesKey, normalized)
        refreshTidalInstances()
    }

    fun refreshTidalInstances() =
        withBusy("tidal_instances") {
            val records = TidalInstanceHealthManager.refresh(context, includeDiscovery = true, staggered = false)
            _lastMessage.value = "Tidal instances: ${records.count { it.isHealthy }} healthy of ${records.size}"
        }

    fun signOutTidal() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(TidalAccessTokenKey)
                prefs.remove(TidalRefreshTokenKey)
                prefs.remove(TidalTokenExpiryKey)
                prefs.remove(TidalAccountNameKey)
                prefs.remove(TidalUserIdKey)
                prefs.remove(TidalCountryCodeKey)
                prefs.remove(TidalAuthFlowKey)
                prefs.remove(TidalSubscriptionKey)
                prefs[TidalNeedsReloginKey] = false
            }
            LosslessStreamResolver.clearAll()
        }
    }

    // ── Qobuz ─────────────────────────────────────────────────────────────────────────────────

    fun setQobuzEnabled(enabled: Boolean) {
        set(QobuzEnabledKey, enabled)
        if (enabled && PoolAccountManager.isEnabled) refreshPool(force = true)
    }

    fun setQobuzQuality(quality: QobuzAudioQuality) {
        set(QobuzAudioQualityKey, quality.name)
        LosslessStreamResolver.clearAll()
    }

    fun setQobuzInstances(raw: String) {
        val normalized = SourceInputParsing.parseUrls(raw).joinToString("\n")
        set(QobuzInstancesKey, normalized)
        set(QobuzVerifiedInstancesKey, null)
    }

    /**
     * Accepts the same inputs ArchiveTune's Qobuz settings sheet does: raw tokens, `token:appId:secret`
     * triples, or JSON lists — see SourceInputParsing.parseQobuzTokens.
     */
    fun addQobuzTokens(raw: String) {
        viewModelScope.launch {
            val parsed = SourceInputParsing.parseQobuzTokens(raw)
            if (parsed.isEmpty()) {
                _lastMessage.value = "No Qobuz token found in the pasted text"
                return@launch
            }
            dataStore.edit { prefs ->
                val existing = QobuzToken.listFromJson(prefs[QobuzTokensKey])
                val merged = (existing.filterNot { e -> parsed.any { it.token == e.token } } + parsed)
                prefs[QobuzTokensKey] = QobuzToken.listToJson(merged)
                prefs.remove(QobuzVerifiedTokensKey)
                prefs[QobuzEnabledKey] = true
                prefs[LosslessStreamingEnabledKey] = true
            }
            _lastMessage.value = "Added ${parsed.size} Qobuz token(s)"
        }
    }

    fun removeQobuzToken(token: QobuzToken) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val existing = QobuzToken.listFromJson(prefs[QobuzTokensKey])
                prefs[QobuzTokensKey] = QobuzToken.listToJson(existing.filterNot { it.token == token.token })
                prefs.remove(QobuzVerifiedTokensKey)
            }
            QobuzAudioProvider.setTokens(emptyList())
        }
    }

    // ── Deezer ────────────────────────────────────────────────────────────────────────────────

    fun setDeezerEnabled(enabled: Boolean) {
        set(DeezerEnabledKey, enabled)
        if (enabled && PoolAccountManager.isEnabled) refreshPool(force = true)
    }

    fun setDeezerQuality(quality: DeezerAudioQuality) {
        set(DeezerAudioQualityKey, quality.name)
        LosslessStreamResolver.clearAll()
    }

    fun signOutDeezer() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(DeezerArlKey)
                prefs.remove(DeezerAccountNameKey)
                prefs.remove(DeezerAccountPremiumKey)
            }
            DeezerAudioProvider.setManualArl(null)
            LosslessStreamResolver.clearAll()
        }
    }

    // ── Apple Music ───────────────────────────────────────────────────────────────────────────

    fun setAppleEnabled(enabled: Boolean) {
        set(AppleMusicSourceEnabledKey, enabled)
        if (enabled && PoolAccountManager.isEnabled) refreshPool(force = true)
    }

    fun setAppleQuality(quality: AppleMusicQuality) {
        set(AppleMusicQualityKey, quality.name)
        LosslessStreamResolver.clearAll()
    }

    /** Manual token entry; verifies the pair against `/v1/me/storefront` before persisting (ArchiveTune behaviour). */
    fun saveAppleTokens(mediaUserToken: String, devToken: String) =
        withBusy("APPLE") {
            val media = mediaUserToken.trim()
            val dev = devToken.trim().ifBlank { AppleMusicProvider.currentDevToken().orEmpty() }
            if (media.isBlank()) {
                dataStore.edit { prefs ->
                    prefs.remove(AppleMusicMediaUserTokenKey)
                    if (devToken.isBlank()) prefs.remove(AppleMusicDevTokenKey) else prefs[AppleMusicDevTokenKey] = devToken.trim()
                }
                _lastMessage.value = "Apple Music tokens cleared"
                return@withBusy
            }
            val verified = dev.isNotBlank() && withContext(Dispatchers.IO) { AppleMusicAudioProvider.verifyTokens(media, dev) }
            if (!verified) {
                _lastMessage.value = "Apple Music rejected that token pair"
                return@withBusy
            }
            dataStore.edit { prefs ->
                prefs[AppleMusicMediaUserTokenKey] = media
                if (devToken.isNotBlank()) prefs[AppleMusicDevTokenKey] = devToken.trim()
                prefs[AppleMusicSourceEnabledKey] = true
                prefs[LosslessStreamingEnabledKey] = true
            }
            _lastMessage.value = "Apple Music tokens verified"
        }

    fun signOutApple() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(AppleMusicMediaUserTokenKey)
                prefs.remove(AppleMusicDevTokenKey)
            }
            LosslessStreamResolver.clearAll()
        }
    }
}
