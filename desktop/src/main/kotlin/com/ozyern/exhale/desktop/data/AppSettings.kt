/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An accent the theme is generated from. Most are one seed; Sabrina spends all four, like on Android. */
data class AccentPalette(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color = primary,
    val tertiary: Color = primary,
    val neutral: Color = primary,
)

/** The Android app's palette picker, same ids, names and seeds. */
object AccentPalettes {
    val Sabrina = AccentPalette("sabrina", "Sabrina", Color(0xFFF08DAE), Color(0xFFE7C08A), Color(0xFFB6A4E4), Color(0xFF7A4E45))

    val all: List<AccentPalette> = listOf(
        Triple("default", "Default", 0xFFED5564), Triple("ocean_blue", "Ocean Blue", 0xFF4A90D9),
        Triple("arctic_blue", "Arctic", 0xFF00BFFF), Triple("midnight_navy", "Midnight", 0xFF2C3E50),
        Triple("sky_blue", "Sky Blue", 0xFF87CEEB), Triple("cobalt_blue", "Cobalt", 0xFF0047AB),
        Triple("electric_blue", "Electric", 0xFF7DF9FF), Triple("emerald_green", "Emerald", 0xFF2ECC71),
        Triple("teal_wave", "Teal Wave", 0xFF1ABC9C), Triple("forest_green", "Forest", 0xFF228B22),
        Triple("spotify_green", "Spotify", 0xFF1DB954), Triple("mint_fresh", "Mint", 0xFF98FF98),
        Triple("olive_garden", "Olive", 0xFF808000), Triple("sage_green", "Sage", 0xFF9CAF88),
        Triple("sunset_orange", "Sunset", 0xFFE67E22), Triple("golden_hour", "Golden", 0xFFF39C12),
        Triple("warm_amber", "Amber", 0xFFFFBF00), Triple("tangerine_blast", "Tangerine", 0xFFFF9800),
        Triple("peach", "Peach", 0xFFFFDAB9), Triple("mango", "Mango", 0xFFFF8243),
        Triple("royal_purple", "Royal Purple", 0xFF9B59B6), Triple("lavender_dream", "Lavender", 0xFFB39DDB),
        Triple("grape_purple", "Grape", 0xFF6B5B95), Triple("violet", "Violet", 0xFFEE82EE),
        Triple("amethyst", "Amethyst", 0xFF9966CC), Triple("ultra_violet", "Ultra Violet", 0xFF645394),
        Triple("cherry_blossom", "Sakura", 0xFFFFB7C5), Triple("rose_quartz", "Rose Quartz", 0xFFF7CAC9),
        Triple("magenta_pop", "Magenta", 0xFFFF00FF), Triple("hot_pink", "Hot Pink", 0xFFFF69B4),
        Triple("blush", "Blush", 0xFFDE5D83), Triple("coral", "Coral", 0xFFFF7F50),
        Triple("bubblegum", "Bubblegum", 0xFFFFC1CC), Triple("crimson_red", "Crimson", 0xFFDC143C),
        Triple("youtube_red", "YouTube", 0xFFFF0000), Triple("wine_red", "Wine", 0xFF722F37),
        Triple("ruby_red", "Ruby", 0xFFE0115F), Triple("scarlet", "Scarlet", 0xFFFF2400),
        Triple("charcoal", "Charcoal", 0xFF36454F), Triple("silver", "Silver", 0xFFC0C0C0),
        Triple("slate", "Slate", 0xFF708090), Triple("graphite", "Graphite", 0xFF474747),
        Triple("terracotta", "Terracotta", 0xFFE2725B), Triple("coffee", "Coffee", 0xFF6F4E37),
        Triple("mocha", "Mocha", 0xFF967969), Triple("sand", "Sand", 0xFFC2B280),
        Triple("clay", "Clay", 0xFFB66A50), Triple("pastel_pink", "Pastel Pink", 0xFFFFD1DC),
        Triple("pastel_blue", "Pastel Blue", 0xFFAEC6CF), Triple("pastel_green", "Pastel Green", 0xFF77DD77),
        Triple("pastel_yellow", "Pastel Yellow", 0xFFFDFD96), Triple("pastel_purple", "Pastel Purple", 0xFFB19CD9),
        Triple("neon_green", "Neon Green", 0xFF39FF14), Triple("neon_pink", "Neon Pink", 0xFFFF10F0),
        Triple("neon_blue", "Neon Blue", 0xFF00F5FF), Triple("neon_orange", "Neon Orange", 0xFFFF5F1F),
        Triple("cyberpunk", "Cyberpunk", 0xFFFF00FF), Triple("synthwave", "Synthwave", 0xFFFF6EC7),
        Triple("ocean", "Ocean", 0xFF006994), Triple("forest", "Deep Forest", 0xFF0B3D0B),
        Triple("autumn", "Autumn", 0xFFD2691E), Triple("winter", "Winter", 0xFFADD8E6),
        Triple("spring", "Spring", 0xFF98FB98), Triple("summer", "Summer", 0xFFFFD700),
        Triple("twilight", "Twilight", 0xFF4B0082), Triple("aurora", "Aurora", 0xFF00FF7F),
        Triple("candy", "Candy", 0xFFFF69B4), Triple("rainbow", "Rainbow", 0xFFFF0000),
    ).map { (id, label, argb) -> AccentPalette(id, label, Color(argb)) }.let { listOf(it.first(), Sabrina) + it.drop(1) }

    fun byId(id: String): AccentPalette = all.firstOrNull { it.id == id } ?: all.first()
}

/** A 10-band graphic equalizer (octave bands), gains in dB. */
object EqualizerPresets {
    val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val presets: Map<String, List<Int>> = linkedMapOf(
        "Flat" to List(10) { 0 },
        "Bass boost" to listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        "Treble boost" to listOf(0, 0, 0, 0, 0, 1, 2, 4, 5, 6),
        "Vocal" to listOf(-2, -2, -1, 1, 3, 4, 3, 1, 0, -1),
        "Pop" to listOf(-1, 0, 2, 3, 4, 3, 1, 0, -1, -1),
        "Rock" to listOf(4, 3, 2, 0, -1, -1, 1, 3, 4, 4),
        "Hip-hop" to listOf(5, 4, 2, 3, -1, -1, 1, 0, 2, 3),
        "Electronic" to listOf(5, 4, 1, 0, -2, 1, 0, 2, 4, 5),
        "Classical" to listOf(4, 3, 2, 1, -1, -1, 0, 2, 3, 4),
        "Acoustic" to listOf(3, 3, 2, 1, 1, 1, 2, 2, 2, 1),
        "Late night" to listOf(3, 2, 1, 0, -1, -2, -2, -1, 0, 1),
    )
}

/** Every setting, immutable. Changed only through [AppSettings.update]. */
data class Settings(
    // Appearance
    val accent: String = "default",
    val dynamicTheme: Boolean = false,
    val pureBlack: Boolean = false,
    val uiScale: Float = 1f,
    val artworkCorner: Int = 18,
    val gridSize: String = "BIG",
    val defaultTab: String = "HOME",
    val liquidGlass: Boolean = true,
    val ambientGlow: Boolean = true,
    val playerBackground: String = "ARTWORK",
    val reducedMotion: Boolean = false,
    val animatedBackground: Boolean = true,
    /** AUTO (judged from the PC, see Graphics), FULL or LITE. */
    val graphics: String = "AUTO",
    /** Apple Music's moving covers in the player, where a song has one. */
    val motionCovers: Boolean = true,
    /** Exhale's icon in the notification area, with the song and its controls. */
    val trayIcon: Boolean = true,
    /** Closing the window keeps the music playing from the notification area instead of quitting. */
    val closeToTray: Boolean = false,
    val cardTilt: Boolean = true,
    // Lyrics
    val lyricsStyle: String = "KARAOKE",
    val lyricsTextSize: Int = 30,
    val lyricsLineSpacing: Int = 6,
    val lyricsAlign: String = "LEFT",
    val lyricsClickToSeek: Boolean = true,
    val lyricsAutoScroll: Boolean = true,
    /** Which side of the player the lyrics panel opens on: RIGHT (player left) or LEFT (player right). */
    val lyricsSide: String = "RIGHT",
    /** How lyrics open from the dock: BESIDE the player, or FULL screen. */
    val lyricsView: String = "BESIDE",
    /** Positive shows lyrics earlier, negative later — for lyrics that run behind or ahead of the audio. */
    val lyricsOffsetMs: Int = 0,
    val lyricsProviderOrder: List<String> = com.ozyern.exhale.desktop.lyrics.LyricsRepository.defaultOrder,
    val lyricsDisabled: Set<String> = emptySet(),
    val preloadLyrics: Boolean = true,
    // Player & audio
    val audioQuality: String = "MAX",
    /**
     * The ceiling for a video's picture. Not AUTO by default: the client that serves the good
     * renditions serves them strictly in order, so a seek can only land in what has been fetched —
     * and at 2160p that is a long wait. 1080p buffers quickly enough that the slider still works.
     */
    val videoQuality: String = "1080",
    val audioCodec: String = "AUTO",
    val streamClient: String = "AUTO",
    /** OFF, SURROUND_51 or SURROUND_71: stereo upmixed for Dolby Atmos / Windows Sonic to place. */
    val spatialAudio: String = "OFF",
    val exclusiveAudio: Boolean = false,
    /** Dolby tracks go to the receiver or soundbar undecoded, for it to render (Atmos included). */
    val dolbyPassthrough: Boolean = false,
    val hqResampler: Boolean = true,
    /** Play a lossless copy from your own sources when one matches what's playing. */
    val preferLossless: Boolean = true,
    /** The keyboard's play/pause, next and previous keys reach Exhale from any window. */
    val mediaKeys: Boolean = true,
    val normalization: Boolean = true,
    val skipSilence: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Flat",
    val equalizerBands: List<Int> = List(10) { 0 },
    val playbackSpeed: Float = 1f,
    /** A record-at-another-speed sound — NORMAL, SPED_UP, NIGHTCORE, SLOWED, DAYCORE, VAPORWAVE — pitch following speed. */
    val soundPreset: String = "NORMAL",
    /** 8D: the song circles the listener's head. */
    val eightD: Boolean = false,
    /** Laps a second for 8D; 0.125 is one every eight seconds. */
    val eightDRate: Float = 0.125f,
    val persistentQueue: Boolean = true,
    val autoSkipOnError: Boolean = true,
    val rememberVolume: Boolean = true,
    val seekStepSeconds: Int = 10,
    // Content
    val contentLanguage: String = "AUTO",
    val contentCountry: String = "AUTO",
    val hideExplicit: Boolean = false,
    val hideVideos: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyType: String = "HTTP",
    val proxyUrl: String = "",
    // Privacy
    val pauseHistory: Boolean = false,
    val pauseSearchHistory: Boolean = false,
    // Storage
    val imageCacheMb: Int = 512,
    // Integrations
    val lastFmEnabled: Boolean = false,
    val lastFmUsername: String? = null,
    val lastFmSession: String? = null,
    val scrobblePercent: Int = 50,
    val scrobbleMinDuration: Int = 30,
    val scrobbleMaxDelay: Int = 180,
    val listenBrainzEnabled: Boolean = false,
    val listenBrainzToken: String = "",
    val discordEnabled: Boolean = false,
    val discordToken: String? = null,
    val discordUsername: String? = null,
    val discordShowPaused: Boolean = false,
    // Updates
    val checkUpdates: Boolean = true,
)

private fun Settings.toProps(): Map<String, String?> = mapOf(
    "accent" to accent,
    "dynamic_theme" to dynamicTheme.toString(),
    "pure_black" to pureBlack.toString(),
    "ui_scale" to uiScale.toString(),
    "artwork_corner" to artworkCorner.toString(),
    "grid_size" to gridSize,
    "default_tab" to defaultTab,
    "liquid_glass" to liquidGlass.toString(),
    "ambient_glow" to ambientGlow.toString(),
    "player_background" to playerBackground,
    "reduced_motion" to reducedMotion.toString(),
    "animated_background" to animatedBackground.toString(),
    "graphics" to graphics,
    "motion_covers" to motionCovers.toString(),
    "tray_icon" to trayIcon.toString(),
    "close_to_tray" to closeToTray.toString(),
    "card_tilt" to cardTilt.toString(),
    "spatial_audio" to spatialAudio,
    "exclusive_audio" to exclusiveAudio.toString(),
    "dolby_passthrough" to dolbyPassthrough.toString(),
    "hq_resampler" to hqResampler.toString(),
    "prefer_lossless" to preferLossless.toString(),
    "media_keys" to mediaKeys.toString(),
    "lyrics_style" to lyricsStyle,
    "lyrics_text_size" to lyricsTextSize.toString(),
    "lyrics_line_spacing" to lyricsLineSpacing.toString(),
    "lyrics_align" to lyricsAlign,
    "lyrics_click_to_seek" to lyricsClickToSeek.toString(),
    "lyrics_auto_scroll" to lyricsAutoScroll.toString(),
    "lyrics_side" to lyricsSide,
    "lyrics_view" to lyricsView,
    "lyrics_offset_ms" to lyricsOffsetMs.toString(),
    "lyrics_provider_order" to lyricsProviderOrder.joinToString(","),
    "lyrics_disabled" to lyricsDisabled.joinToString(","),
    "preload_lyrics" to preloadLyrics.toString(),
    "audio_quality" to audioQuality,
    "video_quality" to videoQuality,
    "audio_codec" to audioCodec,
    "stream_client" to streamClient,
    "normalization" to normalization.toString(),
    "skip_silence" to skipSilence.toString(),
    "crossfade_seconds" to crossfadeSeconds.toString(),
    "equalizer_enabled" to equalizerEnabled.toString(),
    "equalizer_preset" to equalizerPreset,
    "equalizer_bands" to equalizerBands.joinToString(","),
    "playback_speed" to playbackSpeed.toString(),
    "sound_preset" to soundPreset,
    "eight_d" to eightD.toString(),
    "eight_d_rate" to eightDRate.toString(),
    "persistent_queue" to persistentQueue.toString(),
    "auto_skip_on_error" to autoSkipOnError.toString(),
    "remember_volume" to rememberVolume.toString(),
    "seek_step_seconds" to seekStepSeconds.toString(),
    "content_language" to contentLanguage,
    "content_country" to contentCountry,
    "hide_explicit" to hideExplicit.toString(),
    "hide_videos" to hideVideos.toString(),
    "proxy_enabled" to proxyEnabled.toString(),
    "proxy_type" to proxyType,
    "proxy_url" to proxyUrl,
    "pause_history" to pauseHistory.toString(),
    "pause_search_history" to pauseSearchHistory.toString(),
    "image_cache_mb" to imageCacheMb.toString(),
    "lastfm_enabled" to lastFmEnabled.toString(),
    "lastfm_username" to lastFmUsername,
    "lastfm_session" to lastFmSession,
    "scrobble_percent" to scrobblePercent.toString(),
    "scrobble_min_duration" to scrobbleMinDuration.toString(),
    "scrobble_max_delay" to scrobbleMaxDelay.toString(),
    "listenbrainz_enabled" to listenBrainzEnabled.toString(),
    "listenbrainz_token" to listenBrainzToken,
    "discord_enabled" to discordEnabled.toString(),
    "discord_token" to discordToken,
    "discord_username" to discordUsername,
    "discord_show_paused" to discordShowPaused.toString(),
    "check_updates" to checkUpdates.toString(),
)

/** The lyrics source order saved before the Apple-catalogue and open sources came — never chosen, only kept. */
private val LegacyLyricsOrder = listOf("BetterLyrics", "SimpMusic", "LRCLIB", "KuGou")

/**
 * [saved] with every source added since put back where [defaults] places it — just ahead of the
 * source it comes before there — rather than at the bottom, where a new word-timed source would sit
 * behind every line-timed one the listener never moved. An order identical to the old default was
 * never chosen, so it simply becomes the new one.
 */
private fun withNewSources(saved: List<String>, defaults: List<String>): List<String> {
    if (saved.isEmpty() || saved == LegacyLyricsOrder || saved == LegacyLyricsOrder + "YouTube Music") return defaults
    val order = saved.distinct().toMutableList()
    defaults.forEachIndexed { index, source ->
        if (source in order) return@forEachIndexed
        val next = defaults.drop(index + 1).firstOrNull { it in order }
        if (next == null) order += source else order.add(order.indexOf(next), source)
    }
    return order
}

private fun settingsFrom(get: (String) -> String?): Settings {
    val d = Settings()
    fun s(key: String, default: String) = get(key)?.takeIf { it.isNotEmpty() } ?: default
    fun b(key: String, default: Boolean) = get(key)?.toBooleanStrictOrNull() ?: default
    fun i(key: String, default: Int) = get(key)?.toIntOrNull() ?: default
    fun f(key: String, default: Float) = get(key)?.toFloatOrNull() ?: default
    fun n(key: String) = get(key)?.takeIf { it.isNotBlank() }
    fun csv(key: String) = get(key)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }

    return Settings(
        accent = s("accent", d.accent),
        dynamicTheme = b("dynamic_theme", d.dynamicTheme),
        pureBlack = b("pure_black", d.pureBlack),
        uiScale = f("ui_scale", d.uiScale).coerceIn(0.75f, 1.4f),
        artworkCorner = i("artwork_corner", d.artworkCorner),
        gridSize = s("grid_size", d.gridSize),
        defaultTab = s("default_tab", d.defaultTab),
        liquidGlass = b("liquid_glass", d.liquidGlass),
        ambientGlow = b("ambient_glow", d.ambientGlow),
        playerBackground = s("player_background", d.playerBackground),
        reducedMotion = b("reduced_motion", d.reducedMotion),
        animatedBackground = b("animated_background", d.animatedBackground),
        graphics = s("graphics", d.graphics),
        motionCovers = b("motion_covers", d.motionCovers),
        trayIcon = b("tray_icon", d.trayIcon),
        closeToTray = b("close_to_tray", d.closeToTray),
        cardTilt = b("card_tilt", d.cardTilt),
        spatialAudio = s("spatial_audio", d.spatialAudio),
        exclusiveAudio = b("exclusive_audio", d.exclusiveAudio),
        dolbyPassthrough = b("dolby_passthrough", d.dolbyPassthrough),
        hqResampler = b("hq_resampler", d.hqResampler),
        preferLossless = b("prefer_lossless", d.preferLossless),
        mediaKeys = b("media_keys", d.mediaKeys),
        lyricsStyle = s("lyrics_style", d.lyricsStyle),
        lyricsTextSize = i("lyrics_text_size", d.lyricsTextSize),
        lyricsLineSpacing = i("lyrics_line_spacing", d.lyricsLineSpacing),
        lyricsAlign = s("lyrics_align", d.lyricsAlign),
        lyricsClickToSeek = b("lyrics_click_to_seek", d.lyricsClickToSeek),
        lyricsAutoScroll = b("lyrics_auto_scroll", d.lyricsAutoScroll),
        lyricsSide = s("lyrics_side", d.lyricsSide),
        lyricsView = s("lyrics_view", d.lyricsView),
        lyricsOffsetMs = i("lyrics_offset_ms", d.lyricsOffsetMs).coerceIn(-3_000, 3_000),
        lyricsProviderOrder = withNewSources(csv("lyrics_provider_order").orEmpty(), d.lyricsProviderOrder),
        lyricsDisabled = csv("lyrics_disabled")?.toSet() ?: d.lyricsDisabled,
        preloadLyrics = b("preload_lyrics", d.preloadLyrics),
        audioQuality = s("audio_quality", d.audioQuality),
        videoQuality = s("video_quality", d.videoQuality),
        audioCodec = s("audio_codec", d.audioCodec),
        streamClient = s("stream_client", d.streamClient),
        normalization = b("normalization", d.normalization),
        skipSilence = b("skip_silence", d.skipSilence),
        crossfadeSeconds = i("crossfade_seconds", d.crossfadeSeconds),
        equalizerEnabled = b("equalizer_enabled", d.equalizerEnabled),
        equalizerPreset = s("equalizer_preset", d.equalizerPreset),
        equalizerBands = csv("equalizer_bands")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 10 } ?: d.equalizerBands,
        playbackSpeed = f("playback_speed", d.playbackSpeed).coerceIn(0.5f, 2f),
        soundPreset = s("sound_preset", d.soundPreset),
        eightD = b("eight_d", d.eightD),
        eightDRate = f("eight_d_rate", d.eightDRate).coerceIn(0.05f, 0.4f),
        persistentQueue = b("persistent_queue", d.persistentQueue),
        autoSkipOnError = b("auto_skip_on_error", d.autoSkipOnError),
        rememberVolume = b("remember_volume", d.rememberVolume),
        seekStepSeconds = i("seek_step_seconds", d.seekStepSeconds),
        contentLanguage = s("content_language", d.contentLanguage),
        contentCountry = s("content_country", d.contentCountry),
        hideExplicit = b("hide_explicit", d.hideExplicit),
        hideVideos = b("hide_videos", d.hideVideos),
        proxyEnabled = b("proxy_enabled", d.proxyEnabled),
        proxyType = s("proxy_type", d.proxyType),
        proxyUrl = get("proxy_url").orEmpty(),
        pauseHistory = b("pause_history", d.pauseHistory),
        pauseSearchHistory = b("pause_search_history", d.pauseSearchHistory),
        imageCacheMb = i("image_cache_mb", d.imageCacheMb),
        lastFmEnabled = b("lastfm_enabled", d.lastFmEnabled),
        lastFmUsername = n("lastfm_username"),
        lastFmSession = n("lastfm_session"),
        scrobblePercent = i("scrobble_percent", d.scrobblePercent),
        scrobbleMinDuration = i("scrobble_min_duration", d.scrobbleMinDuration),
        scrobbleMaxDelay = i("scrobble_max_delay", d.scrobbleMaxDelay),
        listenBrainzEnabled = b("listenbrainz_enabled", d.listenBrainzEnabled),
        listenBrainzToken = get("listenbrainz_token").orEmpty(),
        discordEnabled = b("discord_enabled", d.discordEnabled),
        discordToken = n("discord_token"),
        discordUsername = n("discord_username"),
        discordShowPaused = b("discord_show_paused", d.discordShowPaused),
        checkUpdates = b("check_updates", d.checkUpdates),
    )
}

/**
 * The settings as one observable value, persisted to `settings.properties` alongside the rest of
 * [DesktopPrefs]. Writes only the keys that changed.
 */
object AppSettings {
    private val _state = MutableStateFlow(settingsFrom(DesktopPrefs::raw))
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    fun update(transform: (Settings) -> Settings) {
        val old = _state.value
        val new = transform(old)
        if (new == old) return
        _state.value = new
        val before = old.toProps()
        DesktopPrefs.putAll(new.toProps().filter { (key, value) -> before[key] != value })
    }

    /** After a backup is restored underneath us. */
    fun reload() {
        _state.value = settingsFrom(DesktopPrefs::raw)
    }

    private val defaults: Map<String, String?> by lazy { Settings().toProps() }

    /** Keys of every setting that differs from its default — Settings marks those rows and offers a reset. */
    fun changedKeys(settings: Settings): Set<String> =
        settings.toProps().filter { (key, value) -> defaults[key] != value }.keys

    /** Puts one setting back to its default, by its stored key. */
    fun reset(key: String) = update { current ->
        val props = current.toProps().toMutableMap()
        props[key] = defaults[key]
        settingsFrom { props[it] }
    }
}

/** The current settings for the composition; provided at the root. */
val LocalSettings = staticCompositionLocalOf { Settings() }
