/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.ozyern.exhale.constants.*
import com.ozyern.exhale.extensions.*
import com.ozyern.exhale.ui.screens.settings.ThemePalettes
import com.ozyern.exhale.ui.theme.ThemeSeedPalette
import com.ozyern.exhale.ui.theme.ThemeSeedPaletteCodec
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.PreferenceStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.reportException
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeLocale
import com.ozyern.exhale.kugou.KuGou
import com.ozyern.exhale.lastfm.LastFM
import com.ozyern.exhale.ui.player.CanvasArtworkPlaybackCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess
import timber.log.Timber
import java.net.Proxy
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var isInitialized = false
    private val didRunImageCacheTrim = AtomicBoolean(false)

    private fun currentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }
    }


    
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (currentProcessName()?.endsWith(":crash") == true) {
            Timber.plant(Timber.DebugTree())
            return
        }
        PreferenceStore.start(this)
        Timber.plant(Timber.DebugTree())
        try {
            Timber.plant(com.ozyern.exhale.utils.GlobalLogTree())
        } catch (_: Exception) {}

        initializeCriticalSync()
        initializeDeferredAsync()
    }

    private fun initializeCriticalSync() {
        CanvasArtworkPlaybackCache.init(this)

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it in CountryCodeToName } ?: "US",
            hl = locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        // STRICT locale must be live BEFORE the first Innertube request leaves the process —
        // HomeViewModel fires its feed load as soon as MainActivity composes, and if the
        // forced hl/gl only lands in the deferred async init, that first home/charts batch
        // still goes out with the IP-derived region (= the "India's biggest hits in an
        // English-only feed" leak). The one-shot blocking DataStore read here is cheap on a
        // warm page cache but it is the ONLY hard main-thread wait in cold start, so it is
        // bounded: if the preferences file is slow (first run, cold flash storage, OS I/O
        // contention) we give up after 500ms and let the deferred collector below apply the
        // strict locale instead of pinning the splash screen on a disk read.
        runCatching {
            val prefs = runBlocking {
                withTimeoutOrNull(500) { dataStore.data.first() }
            }
            if (prefs != null) {
                applyStrictLocale(
                    lang = prefs[ContentLanguageKey] ?: SYSTEM_DEFAULT,
                    country = prefs[ContentCountryKey] ?: SYSTEM_DEFAULT,
                    preferredCsv = prefs[PreferredAudioLanguagesKey].orEmpty(),
                )
            } else {
                Timber.w("Strict locale early-init timed out; deferring to async init")
            }
        }.onFailure { Timber.e(it, "Strict locale early-init failed") }
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }
        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET
        )
    }

    /**
     * Forces YouTube.locale (→ the `hl`/`gl` fields Innertube injects into every request
     * context) to strictly honor the user's selected language, never the IP region.
     *
     *  - Explicit country selected → that wins for `gl`.
     *  - Otherwise, if ANY language is selected (Content language or onboarding picks),
     *    `gl` is pinned to that language's canonical anchor region so Innertube stops
     *    geo-localizing the feed (en → US, hi → IN, ja → JP, …).
     *  - `hl` always tracks the selected content language when set.
     */
    private fun applyStrictLocale(lang: String, country: String, preferredCsv: String) {
        val effectiveLang = lang.takeIf { it != SYSTEM_DEFAULT }
            ?: preferredCsv.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() }

        var locale = YouTube.locale
        effectiveLang?.let { locale = locale.copy(hl = it) }

        val forcedGl = when {
            country != SYSTEM_DEFAULT -> country
            effectiveLang != null -> anchorRegionFor(effectiveLang)
            else -> null
        }
        forcedGl?.let { locale = locale.copy(gl = it) }
        YouTube.locale = locale
    }

    /**
     * Drops the cached visitor id when the user's locale no longer matches the one it was minted
     * under, so the next request mints a fresh one.
     *
     * A visitor id is not a random string — YouTube issues it against the session it first saw,
     * and it is presented on every later request. Minting one on first launch and then keeping it
     * forever means a user who installs in one region and *then* picks a language keeps
     * introducing themselves with the identity from before the choice.
     *
     * Skipped when a cookie or a poToken is present: a poToken is cryptographically bound to the
     * visitor id it was generated for, so re-minting underneath one trades a localization
     * annoyance for broken playback.
     */
    private suspend fun remintVisitorDataIfLocaleChanged() {
        val signature = "${YouTube.locale.hl}|${YouTube.locale.gl}"
        val prefs = dataStore.data.first()
        if (prefs[VisitorDataLocaleKey] == signature) return

        val boundToCredentials = !prefs[InnerTubeCookieKey].isNullOrBlank() ||
            !prefs[PoTokenKey].isNullOrBlank() ||
            !prefs[PoTokenPlayerKey].isNullOrBlank()

        dataStore.edit { settings ->
            settings[VisitorDataLocaleKey] = signature
            if (!boundToCredentials) settings.remove(VisitorDataKey)
        }
    }

    /** Canonical `gl` anchor region per language — overrides the IP-derived region. */
    private fun anchorRegionFor(lang: String): String = when (lang.lowercase().substringBefore("-")) {
        "en" -> "US"
        "es" -> "ES"
        "fr" -> "FR"
        "de" -> "DE"
        "it" -> "IT"
        "pt" -> "BR"
        "ru" -> "RU"
        "ja" -> "JP"
        "ko" -> "KR"
        "zh" -> "TW"
        "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "mr" -> "IN"
        "ar" -> "SA"
        "tr" -> "TR"
        "nl" -> "NL"
        "pl" -> "PL"
        "sv" -> "SE"
        "no", "nb" -> "NO"
        "da" -> "DK"
        "fi" -> "FI"
        "id" -> "ID"
        "vi" -> "VN"
        "th" -> "TH"
        "uk" -> "UA"
        "el" -> "GR"
        "he", "iw" -> "IL"
        "cs" -> "CZ"
        "hu" -> "HU"
        "ro" -> "RO"
        else -> "US"
    }

    private fun initializeDeferredAsync() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val prefs = dataStore.data.first()
                
                applyStrictLocale(
                    lang = prefs[ContentLanguageKey] ?: SYSTEM_DEFAULT,
                    country = prefs[ContentCountryKey] ?: SYSTEM_DEFAULT,
                    preferredCsv = prefs[PreferredAudioLanguagesKey].orEmpty(),
                )
                
                LastFM.sessionKey = prefs[LastFMSessionKey]

                if (prefs[ProxyEnabledKey] == true) {
                    try {
                        YouTube.proxy = Proxy(
                            prefs[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                            prefs[ProxyUrlKey]!!.toInetSocketAddress()
                        )
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@App, "Failed to parse proxy url.", LENGTH_SHORT).show()
                        }
                        reportException(e)
                    }
                    YouTube.streamBypassProxy = prefs[StreamBypassProxyKey] == true
                }

                if (prefs[UseLoginForBrowse] != false) {
                    YouTube.useLoginForBrowse = true
                }
                
                // Apply random theme on startup if enabled.
                //
                // Skipped while the Sabrina palette is on. Sabrina already wins at render time, so
                // this would not change what you see — it would quietly overwrite the palette you
                // had saved, and you would find it gone the moment you switched Sabrina back off.
                if (prefs[RandomThemeOnStartupKey] == true && prefs[SabrinaThemeKey] != true) {
                    val randomPalette = ThemePalettes.generateRandomPalette()
                    val seedPalette = ThemeSeedPalette(
                        primary = randomPalette.primary,
                        secondary = randomPalette.secondary,
                        tertiary = randomPalette.tertiary,
                        neutral = randomPalette.neutral
                    )
                    val encodedPalette = ThemeSeedPaletteCodec.encodeForPreference(seedPalette, "Random")
                    dataStore.edit { settings ->
                        settings[CustomThemeColorKey] = encodedPalette
                    }
                }
                
                isInitialized = true
            } catch (e: Exception) {
                Timber.e(e, "Error during deferred initialization")
                reportException(e)
            }
        }

        // STRICT locale injection: keep the Innertube hl/gl parameters in lockstep with the
        // DataStore content preferences, so changing the language in onboarding or
        // Settings → Content re-scopes every subsequent home/search/browse request immediately.
        //
        // CRITICAL: Innertube defaults `gl` to the caller's IP region. If the user explicitly
        // selected a language (e.g. English) but never picked a country, we must NOT leave the
        // IP-derived region in place — that is exactly what leaks "India's biggest hits"-style
        // regional shelves into an English-only feed. So whenever a language is chosen without
        // an explicit country override, we force `gl` to that language's canonical anchor
        // region (en→US, es→ES, fr→FR, …) on EVERY request context.
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map {
                    Triple(
                        it[ContentLanguageKey] ?: SYSTEM_DEFAULT,
                        it[ContentCountryKey] ?: SYSTEM_DEFAULT,
                        it[PreferredAudioLanguagesKey].orEmpty(),
                    )
                }
                .distinctUntilChanged()
                .collect { (lang, country, preferredCsv) ->
                    applyStrictLocale(lang, country, preferredCsv)
                    remintVisitorDataIfLocaleChanged()
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" }
                        ?: YouTube.visitorData().onFailure {
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }

        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    val stack = sw.toString()

                    val intent = Intent(this@App, DebugActivity::class.java).apply {
                        putExtra(DebugActivity.EXTRA_STACK_TRACE, stack)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(2)
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        forgetAccount(this@App)
                    }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[WebClientPoTokenEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    YouTube.webClientPoTokenEnabled = enabled
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[PoTokenKey] }
                .distinctUntilChanged()
                .collect { token ->
                    YouTube.poToken = token?.takeIf { it.isNotBlank() }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[PoTokenGvsKey] }
                .distinctUntilChanged()
                .collect { token ->
                    YouTube.poTokenGvs = token?.takeIf { it.isNotBlank() }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[PoTokenPlayerKey] }
                .distinctUntilChanged()
                .collect { token ->
                    YouTube.poTokenPlayer = token?.takeIf { it.isNotBlank() }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[LastFMSessionKey] }
                .distinctUntilChanged()
                .collect { sessionKey ->
                    LastFM.sessionKey = sessionKey
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val smartTrimmer = dataStore[SmartTrimmerKey] ?: false
        val imageCacheConfig = resolveImageDiskCacheConfig(dataStore[MaxImageCacheSizeKey])

        val diskCache = DiskCache.Builder()
            .directory(cacheDir.resolve("coil"))
            .maxSizeBytes(imageCacheConfig.maxSizeBytes)
            .build()

        if (smartTrimmer && imageCacheConfig.policy == CachePolicy.ENABLED && didRunImageCacheTrim.compareAndSet(false, true)) {
            applicationScope.launch(Dispatchers.IO) { trimImageDiskCache(diskCache) }
        }

        return ImageLoader.Builder(this)
            // A stated duration, not `true`.
            //
            // Coil's default is 100ms, which on a grid of covers reads as a flicker: tiles blink in
            // one after another as the decoder catches up. 220ms is long enough to register as a
            // fade and short enough that a cached image still feels instant.
            .crossfade(220)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            // Heavy in-memory caching: a quarter of the app heap keeps every on-screen
            // thumbnail (home shelves, search category artwork, player art) hot so scroll
            // and tab switches never re-decode from disk.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCache(diskCache)
            .diskCachePolicy(imageCacheConfig.policy)
            .build()
    }

    private fun trimImageDiskCache(diskCache: DiskCache) {
        try {
            val limitBytes = diskCache.maxSize
            if (limitBytes <= 0L || limitBytes == Long.MAX_VALUE) return

            val dir = java.io.File(diskCache.directory.toString())
            if (!dir.exists()) return

            val files = dir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
            var currentSize = files.sumOf { it.length() }
            if (currentSize <= limitBytes) return

            for (file in files) {
                if (currentSize <= limitBytes) break
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) currentSize -= size
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(PoTokenKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}

internal data class ImageDiskCacheConfig(
    val policy: CachePolicy,
    val maxSizeBytes: Long,
)

internal fun resolveImageDiskCacheConfig(maxImageCacheSizeMb: Int?): ImageDiskCacheConfig {
    val sizeMb = maxImageCacheSizeMb ?: 512
    if (sizeMb == 0) return ImageDiskCacheConfig(policy = CachePolicy.DISABLED, maxSizeBytes = 1L)
    if (sizeMb < 0) return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = Long.MAX_VALUE)
    val bytesPerMb = 1024L * 1024L
    val safeSizeMb = sizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
    return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = safeSizeMb * bytesPerMb)
}
