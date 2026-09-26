/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val CustomThemeColorKey = stringPreferencesKey("customThemeColor")
val RandomThemeOnStartupKey = booleanPreferencesKey("randomThemeOnStartup")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")

/**
 * The Sabrina Carpenter palette, on or off.
 *
 * A boolean rather than another entry in the palette picker because it does not behave like one:
 * it overrides dynamic theme, it overrides the picker's selection, and it overrides pure black —
 * a palette built on warm cream surfaces has nothing left once #000 wins. Sitting it in the picker
 * would put a choice that silently beats three other settings in the same grid as ninety that do
 * not. See `SabrinaSeedPalette`.
 */
val SabrinaThemeKey = booleanPreferencesKey("sabrinaTheme")
val UseSystemFontKey = booleanPreferencesKey("useSystemFont")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val SlimNavBarKey = booleanPreferencesKey("slimNavBar")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val PlayerDesignStyleKey = stringPreferencesKey("playerDesignStyle")
val UseNewLibraryDesignKey = booleanPreferencesKey("useNewLibraryDesign")
val UseNewMiniPlayerDesignKey = booleanPreferencesKey("useNewMiniPlayerDesign")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val ExhaleCanvasKey = booleanPreferencesKey("ExhaleCanvas")

// Fuente del canvas: AUTO, APPLE_MUSIC, OPENTUNE, TIDAL
val CanvasSourceKey = stringPreferencesKey("canvasSource")
/**
 * App-wide interface scale, as a multiplier on the device's density. 1f is the system size.
 *
 * This scales `density`, not `fontScale`. The two are different settings on Android for a reason:
 * Display Size scales densityDpi so every dp AND sp grows together and the layout keeps its
 * proportions, while Font Size scales only sp and grows the type inside a layout that stays put.
 * Scaling both here would compound with the user's own accessibility font setting and give
 * someone at 130% text a 169% app.
 */
val UiScaleKey = floatPreferencesKey("uiScale")

/** Which launcher icon is selected. Values are [com.ozyern.exhale.utils.AppIconPack] names. */
val AppIconPackKey = stringPreferencesKey("appIconPack")
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropThumbnailToSquareKey = booleanPreferencesKey("cropThumbnailToSquare")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val DisableBlurKey = booleanPreferencesKey("disableBlur")
val BlurRadiusKey = floatPreferencesKey("blurRadius")
val MiniPlayerLastAnchorKey = intPreferencesKey("miniPlayerLastAnchor")

/**
 * The last build this install actually opened.
 *
 * Only the welcome screen reads it, and only to answer one question: is this the first run after
 * an update? Absent means the question cannot be answered from here — the key did not exist before
 * 1.0.203 — so the gate falls back to PackageManager's install and update times for that one case.
 */
val LastSeenVersionCodeKey = intPreferencesKey("lastSeenVersionCode")
val EnableHapticFeedbackKey = booleanPreferencesKey("enableHapticFeedback")
val PlayerFullscreenKey = booleanPreferencesKey("player_fullscreen")

val ProviderOrderKey = stringPreferencesKey("lyrics_provider_order")

val DefaultProviderOrder = listOf(
    PreferredLyricsProvider.LRCLIB,
    PreferredLyricsProvider.KUGOU,
    PreferredLyricsProvider.BETTER_LYRICS,
    PreferredLyricsProvider.SIMPMUSIC,
)

fun PreferredLyricsProvider.displayName(): String = when (this) {
    PreferredLyricsProvider.LRCLIB -> "LrcLib"
    PreferredLyricsProvider.KUGOU -> "KuGou"
    PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
    PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
}

enum class SliderStyle {
    Standard,
    Wavy,
    Thick,
    Circular,
    Simple,
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableSimpMusicLyricsKey = booleanPreferencesKey("enableSimpMusicLyrics")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoKey = booleanPreferencesKey("hideVideo")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val StreamBypassProxyKey = booleanPreferencesKey("streamBypassProxy")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
val SelectedYtmPlaylistsKey = stringPreferencesKey("ytm_selected_playlists")

val TogetherDisplayNameKey = stringPreferencesKey("together_display_name")
val TogetherClientIdKey = stringPreferencesKey("together_client_id")
val TogetherDefaultPortKey = intPreferencesKey("together_default_port")
val TogetherAllowGuestsToAddTracksKey = booleanPreferencesKey("together_allow_guests_add_tracks")
val TogetherAllowGuestsToControlPlaybackKey = booleanPreferencesKey("together_allow_guests_control_playback")
val TogetherRequireHostApprovalToJoinKey = booleanPreferencesKey("together_require_host_approval_to_join")
val TogetherLastJoinLinkKey = stringPreferencesKey("together_last_join_link")
val TogetherWelcomeShownKey = booleanPreferencesKey("together_welcome_shown")

// ListenBrainz scrobbling
val ListenBrainzEnabledKey = booleanPreferencesKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenbrainz_token")

// Last.fm scrobbling
val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

val AudioQualityKey = stringPreferencesKey("audioQuality")

/**
 * Force the "this connection is metered" branch of stream selection, regardless of what
 * `ConnectivityManager` reports.
 *
 * **Defaults to off.** It used to default to on, which meant every install started out asking
 * YouTube for the low-bandwidth ladder even on unmetered Wi-Fi — the setting overrides the
 * platform's own answer rather than adding to it, so switching it on is a statement that the
 * system's metered detection is wrong here, not a general "be careful with data" preference.
 * Android already reports metered tethering and metered Wi-Fi correctly on the overwhelming
 * majority of devices, so the sensible default is to believe it and let this be the escape hatch
 * for the minority where it lies.
 */
val NetworkMeteredKey = booleanPreferencesKey("networkMetered")

enum class AudioQuality {
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

/**
 * Which codec stream selection reaches for first.
 *
 * This is a *preference*, not a filter: if the chosen codec is not among the formats a client
 * was served, selection falls through to bitrate order and plays whatever is there. Nothing
 * here can conjure a rendition YouTube did not send.
 *
 * Worth knowing what the choice actually costs, because the bitrate numbers mislead. On a
 * signed-out or free account YouTube serves exactly two music formats: Opus itag 251 (VBR,
 * ~130-160 kbps) and AAC itag 140 (128 kbps). The 256 kbps AAC rendition, itag 141, is gated
 * behind a Premium subscription server-side and no client spoof reaches it. So [AAC] on a free
 * account is 128 kbps, which is *lower* than the Opus it replaces, and Opus is the more
 * efficient codec at that rate besides.
 *
 * It defaults to [AAC] anyway. AAC-LC decoding is mandatory on every Android device and gets
 * hardware offload where Opus is decoded in software, so it is the safer, cheaper stream; and
 * on a Premium account it is the one that unlocks 256 kbps. [AUTO] is the setting for anyone
 * who would rather have the highest bitrate on offer whatever the codec.
 */
val AudioCodecKey = stringPreferencesKey("audioCodec")

enum class AudioCodec {
    AAC,
    OPUS,
    AUTO,
}

val PlayerStreamClientKey = stringPreferencesKey("playerStreamClient")

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    IOS,
    TVHTML5,
    ANDROID_MUSIC,
}

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PermanentShuffleKey = booleanPreferencesKey("permanentShuffle")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")

/** Play a matching FLAC/WAV/AIFF already on the phone in place of YouTube's stream. */
val PreferLocalLosslessKey = booleanPreferencesKey("preferLocalLossless")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val SpatialAudioKey = booleanPreferencesKey("spatialAudio") // Cavern-style Atmos/spatial upscaling

/**
 * How wide the spatial stage is.
 *
 * A phone has two speakers, so "5.1" and "7.1" are not outputs it can have — what it can have is
 * how far the virtual stage is pushed, which is the thing those switches are really reaching for.
 */
val SpatialAudioProfileKey = stringPreferencesKey("spatialAudioProfile")

enum class SpatialAudioProfile {
    /** Barely there: a little width, no obvious processing. */
    NATURAL,

    /** The default: a clear stage outside the headphones without hollowing the centre. */
    WIDE,

    /** Room-sized, with the height shelf pushed — big on headphones, much on a speaker. */
    CINEMA,
}
val AudioOffload = booleanPreferencesKey("audioOffload")
val AudioCrossfadeDurationKey = intPreferencesKey("audioCrossfadeDuration")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val PauseOnDeviceMuteKey = booleanPreferencesKey("pauseOnDeviceMute")
val AutoStartOnBluetoothKey = booleanPreferencesKey("autoStartOnBluetooth")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val WakelockKey = booleanPreferencesKey("wakelock")
val ArtistSeparatorsKey = stringPreferencesKey("artistSeparators")
val ExternalDownloaderEnabledKey = booleanPreferencesKey("externalDownloaderEnabled")
val ExternalDownloaderPackageKey = stringPreferencesKey("externalDownloaderPackage")
val PlaylistTagsFilterKey = stringPreferencesKey("playlistTagsFilter")
val ShowHomeCategoryChipsKey = booleanPreferencesKey("showHomeCategoryChips")
val ShowTagsInLibraryKey = booleanPreferencesKey("showTagsInLibrary")
val LiquidGlassNavBarKey = booleanPreferencesKey("liquid_glass_nav_bar")

val EqualizerEnabledKey = booleanPreferencesKey("equalizerEnabled")
val EqualizerBandLevelsMbKey = stringPreferencesKey("equalizerBandLevelsMb")
val EqualizerOutputGainEnabledKey = booleanPreferencesKey("equalizerOutputGainEnabled")
val EqualizerOutputGainMbKey = intPreferencesKey("equalizerOutputGainMb")
val EqualizerBassBoostEnabledKey = booleanPreferencesKey("equalizerBassBoostEnabled")
val EqualizerBassBoostStrengthKey = intPreferencesKey("equalizerBassBoostStrength")
val EqualizerVirtualizerEnabledKey = booleanPreferencesKey("equalizerVirtualizerEnabled")
val EqualizerVirtualizerStrengthKey = intPreferencesKey("equalizerVirtualizerStrength")
val EqualizerSelectedProfileIdKey = stringPreferencesKey("equalizerSelectedProfileId")
val EqualizerCustomProfilesJsonKey = stringPreferencesKey("equalizerCustomProfilesJson")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val SmartTrimmerKey = booleanPreferencesKey("smartTrimmer")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val MaxCanvasCacheSizeKey = intPreferencesKey("maxCanvasCacheSize")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

val DiscordTokenKey = stringPreferencesKey("discordToken")
val DiscordInfoDismissedKey = booleanPreferencesKey("discordInfoDismissed")
val DiscordUsernameKey = stringPreferencesKey("discordUsername")
val DiscordNameKey = stringPreferencesKey("discordName")
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")
// Discord activity customization keys
val DiscordActivityNameKey = stringPreferencesKey("discordActivityName")
val DiscordActivityDetailsKey = stringPreferencesKey("discordActivityDetails")
val DiscordActivityStateKey = stringPreferencesKey("discordActivityState")
// Custom button labels and urls for Discord activity buttons
val DiscordActivityButton1LabelKey = stringPreferencesKey("discordActivityButton1Label")
val DiscordActivityButton1UrlSourceKey = stringPreferencesKey("discordActivityButton1UrlSource")
val DiscordActivityButton1CustomUrlKey = stringPreferencesKey("discordActivityButton1CustomUrl")
val DiscordActivityButton2LabelKey = stringPreferencesKey("discordActivityButton2Label")
val DiscordActivityButton2UrlSourceKey = stringPreferencesKey("discordActivityButton2UrlSource")
val DiscordActivityButton2CustomUrlKey = stringPreferencesKey("discordActivityButton2CustomUrl")
val DiscordActivityButton1EnabledKey = booleanPreferencesKey("discordActivityButton1Enabled")
val DiscordActivityButton2EnabledKey = booleanPreferencesKey("discordActivityButton2Enabled")
val DiscordShowWhenPausedKey = booleanPreferencesKey("discordShowWhenPaused")
// Activity type for Discord presence (PLAYING, STREAMING, LISTENING, WATCHING, COMPETING)
val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordPresenceIntervalValueKey = intPreferencesKey("discordPresenceIntervalValue")
val DiscordPresenceIntervalUnitKey = stringPreferencesKey("discordPresenceIntervalUnit") // "S", "M", "H"
val DiscordPresenceStatusKey = stringPreferencesKey("discordPresenceStatus") // "ONLINE", "IDLE", "DND", "INVISIBLE"

// Discord image selection keys
// Values for type keys: "thumbnail", "artist", "appicon", "custom"
val DiscordLargeImageTypeKey = stringPreferencesKey("discordLargeImageType")
val DiscordLargeTextSourceKey = stringPreferencesKey("discordLargeTextSource")
val DiscordLargeTextCustomKey = stringPreferencesKey("discordLargeTextCustom")
val DiscordLargeImageCustomUrlKey = stringPreferencesKey("discordLargeImageCustomUrl")
val DiscordSmallImageTypeKey = stringPreferencesKey("discordSmallImageType")
val DiscordSmallImageCustomUrlKey = stringPreferencesKey("discordSmallImageCustomUrl")
// Activity platform (discord client platform) selection
val DiscordActivityPlatformKey = stringPreferencesKey("discordActivityPlatform")

val TranslatorContextsKey = stringPreferencesKey("translatorContexts")
val TranslatorTargetLangKey = stringPreferencesKey("translatorTargetLang")
val EnableTranslatorKey = booleanPreferencesKey("enableTranslator")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")
val AudioCrossfadeGaplessKey = booleanPreferencesKey("audio_crossfade_gapless")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")
val SpeedDialSongIdsKey = stringPreferencesKey("speedDialSongIds")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")

val ShowLikedPlaylistKey = booleanPreferencesKey("show_liked_playlist")
val ShowDownloadedPlaylistKey = booleanPreferencesKey("show_downloaded_playlist")
val ShowTopPlaylistKey = booleanPreferencesKey("show_top_playlist")
val ShowCachedPlaylistKey = booleanPreferencesKey("show_cached_playlist")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    LOCAL,
}

enum class ArtistFilter {
    LIBRARY,
    LIKED
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    DOWNLOADED_FULL
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
    CUSTOM,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY ->
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            WEEK ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL_TIME -> 0
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC,
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY,
}

enum class PlayerDesignStyle {
    V1,
    V2,
    V3,
    V4,
    V5,
    V6,
    V7,
    V8
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
    FLUID,
}

// Keys for customized background
val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")
val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")


val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")
enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
}

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")
val UseLyricsV2Key = booleanPreferencesKey("useLyricsV2")

// Queue lyrics pre-load settings
val PreloadQueueLyricsEnabledKey = booleanPreferencesKey("preload_queue_lyrics_enabled")

/**
 * Publish timed lyrics to the OxygenOS / ColorOS lock screen ("Live Space").
 *
 * On every other ROM this is inert — the payload rides along on metadata we already build and is
 * simply ignored — so it defaults on. The switch exists for anyone who would rather their lyrics
 * not leave the app at all.
 */
val EnableLockScreenLyricsKey = booleanPreferencesKey("enableLockScreenLyrics")

/**
 * Write the line currently playing into the media session's own subtitle, so it appears on the
 * lock screen's standard media card.
 *
 * **Defaults to on, because it is the only one of the two lyric paths that works on a phone with
 * nothing installed on it.** [EnableLockScreenLyricsKey] publishes an OPlus `lyricInfo` document
 * to the ColorOS lock-screen lyric page, and that page is private vendor SystemUI gated on a
 * package whitelist a third-party player is never added to. The community bridge that opens it is
 * an LSPosed module and says so in its own requirements: without root, that path can be
 * spec-perfect and still paint nothing. The media card is the platform control every Android lock
 * screen has drawn since 11, it has no whitelist, and it renders whatever the session's subtitle
 * says.
 *
 * The two are not free to combine, and this used to be hard-gated off whenever the Live Space
 * path was on for that reason: the integration spec is explicit that a player must keep `TITLE`,
 * `ARTIST` and `DISPLAY_SUBTITLE` stable for the current track, because OPlus reads them as track
 * identity, so a line-by-line rewrite looks like the track changing every few seconds and its
 * metadata debounce can drop the `lyricInfo` document.
 *
 * That gate is gone, and its removal is the fix rather than a regression. The conflict is only
 * real on a phone where the Live Space path *works at all* — i.e. one with the bridge module
 * installed — and gating on a preference that is on by default meant the stock case, which is
 * almost every case, shipped with both channels silently dark. Trading a broken default for a
 * one-switch fix on rooted phones is the right way round. Users who have the bridge turn this
 * off; the description in Settings says so.
 *
 * The cost while it is on: the same field feeds the notification shade, Bluetooth head units and
 * Android Auto, so the artist name is replaced by the lyric everywhere the session is read.
 */
val LyricsOnMediaCardKey = booleanPreferencesKey("lyricsOnMediaCard")
val QueueLyricsPreloadCountKey = intPreferencesKey("queue_lyrics_preload_count")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")

/** `hl|gl` the cached visitor id was minted under, so a locale change can invalidate it. */
val VisitorDataLocaleKey = stringPreferencesKey("visitorDataLocale")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val PoTokenKey = stringPreferencesKey("poToken")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")

val WebClientPoTokenEnabledKey = booleanPreferencesKey("webClientPoTokenEnabled")
val PoTokenGvsKey = stringPreferencesKey("poTokenGvs")
val PoTokenPlayerKey = stringPreferencesKey("poTokenPlayer")
val UseVisitorDataKey = booleanPreferencesKey("useVisitorData")
val PoTokenSourceUrlKey = stringPreferencesKey("poTokenSourceUrl")

val LanguageCodeToName =
    mapOf(
        "en" to "English (US)",
        "en-GB" to "English (UK)",
        "ja" to "日本語",
        "ko" to "한국어",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "uk" to "Українська",
        "cs" to "Čeština",
        "el" to "Ελληνικά",
        "he" to "עברית",
        "hu" to "Magyar",
        "ro" to "Română",
        "fi" to "Suomi",
        "da" to "Dansk",
        "no" to "Norsk",
        "sv" to "Svenska",
        "sk" to "Slovenčina",
        "bg" to "Български",
        "hr" to "Hrvatski",
        "sr" to "Срpsки",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "et" to "Eesti",
    )

val CountryCodeToName =
    mapOf(
        "JP" to "Japan",
        "KR" to "South Korea",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CN" to "China",
        "TW" to "Taiwan",
        "HK" to "Hong Kong",
        "FR" to "France",
        "DE" to "Germany",
        "ES" to "Spain",
        "MX" to "Mexico",
        "BR" to "Brazil",
        "RU" to "Russia",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "PL" to "Poland",
        "TR" to "Turkey",
        "AU" to "Australia",
        "CA" to "Canada",
        "IN" to "India",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "PH" to "Philippines",
        "MY" to "Malaysia",
        "SG" to "Singapore",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "PE" to "Peru",
        "ZA" to "South Africa",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
    )

// App rating / star prompt preferences
val LaunchCountKey = intPreferencesKey("launch_count")
val HasPressedStarKey = booleanPreferencesKey("has_pressed_star")
val RemindAfterKey = intPreferencesKey("remind_after")

// Song Preferences onboarding (language -> contextual artists)
val SongPreferencesCompletedKey = booleanPreferencesKey("song_preferences_completed")
val PreferredAudioLanguagesKey = stringPreferencesKey("preferred_audio_languages") // CSV of language codes
val PreferredArtistsKey = stringPreferencesKey("preferred_artists") // CSV of artist names

// Update settings
val EnableUpdateNotificationKey = booleanPreferencesKey("enableUpdateNotification")
val UpdateChannelKey = stringPreferencesKey("updateChannel")
val LastUpdateCheckKey = longPreferencesKey("lastUpdateCheck")
val LastNotifiedVersionKey = stringPreferencesKey("lastNotifiedVersion")

val GitHubContributorsEtagKey = stringPreferencesKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringPreferencesKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longPreferencesKey("github_contributors_last_checked_at")

// NOTE: key names bumped to "_exhale" to orphan any stale cache from the old
// Exhale repository (which held 3.x releases). Forces a fresh fetch from ozyern/Exhale.
val GitHubReleasesEtagKey = stringPreferencesKey("github_releases_etag_exhale")
val GitHubReleasesJsonKey = stringPreferencesKey("github_releases_json_exhale")
val GitHubReleasesLastCheckedAtKey = longPreferencesKey("github_releases_last_checked_at_exhale")
val GitHubReleasesFingerprintKey = stringPreferencesKey("github_releases_fingerprint_exhale")

/** A relay the listener runs (or was given) themselves; when set, it is used instead of the shared list. */
val TogetherRelayUrlKey = stringPreferencesKey("togetherRelayUrl")
val TogetherOnlineEndpointCacheKey = stringPreferencesKey("together_online_endpoint_cache")
val TogetherOnlineEndpointLastCheckedAtKey = longPreferencesKey("together_online_endpoint_last_checked_at")

enum class UpdateChannel {
    STABLE,
    NIGHTLY,
}


// Always On Display

enum class AodStyle {
    CLASSIC,
    BACKGROUND,
    MINIMAL,
    LARGE,
    SPOTLIGHT,
}

enum class AodArtShape {
    ROUNDED,
    CIRCLE,
    SQUIRCLE,
    DIAMOND,
    HEXAGON,
    STAR,
    ARCH,
    PETAL,
}

enum class AodControlStyle {
    ROUNDED,
    SQUARE,
    ACCENT,
    MINIMAL_FLAT,
}

val AodStyleKey = stringPreferencesKey("aod_style")
val AodArtShapeKey = stringPreferencesKey("aod_art_shape")

val AodDarknessKey = floatPreferencesKey("aod_darkness")
val AodArtSizeKey = floatPreferencesKey("aod_art_size")

val AodShowTitleKey = booleanPreferencesKey("aod_show_title")
val AodShowArtistKey = booleanPreferencesKey("aod_show_artist")
val AodShowTimeKey = booleanPreferencesKey("aod_show_time_labels")
val AodShowProgressKey = booleanPreferencesKey("aod_show_progress")
val AodShowControlsKey = booleanPreferencesKey("aod_show_controls")

// AOD Auto-activation timeout (seconds): 0 = never, 15, 30, 60, 120
val AodAutoActivationKey = intPreferencesKey("aod_auto_activation_seconds")

// AOD Fullscreen mode (hide system UI)
val AodFullscreenKey = booleanPreferencesKey("aod_fullscreen_mode")

/** Intensidad del spotlight (0.0 – 1.0) */
val AodSpotlightIntensityKey = floatPreferencesKey("aod_spotlight_intensity")

/** Pulso / breathing del spotlight */
val AodSpotlightPulseKey = booleanPreferencesKey("aod_spotlight_pulse")

/** Duración de las transiciones entre canciones en ms */
val AodTransitionDurationKey = intPreferencesKey("aod_transition_duration")

/** Estilo visual de los botones de control */
val AodControlStyleKey = stringPreferencesKey("aod_control_style")

/** Escala de texto global (0.8 – 1.4) */
val AodTextScaleKey = floatPreferencesKey("aod_text_scale")

/** Mostrar reloj del sistema en el AOD */
val AodShowClockKey = booleanPreferencesKey("aod_show_clock")

val AodClockFormatKey = booleanPreferencesKey("aod_clock_24h")

val LyricsLineBlurKey = booleanPreferencesKey("lyricsLineBlur")

val LyricsSyncOffsetKey = intPreferencesKey("lyrics_sync_offset")


enum class CanvasSource {
    AUTO,
    APPLE_MUSIC,
    TIDAL,
}

// Home Screen Widget (ExhalePlayerWidget)

enum class WidgetBackgroundMode {
    BLUR,
    DOMINANT_COLOR,
    SOLID,
}

val WidgetBackgroundModeKey = stringPreferencesKey("widget_background_mode")

val WidgetScrimOpacityKey = floatPreferencesKey("widget_scrim_opacity")

val WidgetCornerRadiusKey = floatPreferencesKey("widget_corner_radius")

val WidgetShowProgressBarKey = booleanPreferencesKey("widget_show_progress_bar")


val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val ShowSpotifyPlaylistsKey = booleanPreferencesKey("show_spotify_playlists")
val SpotifyLibraryPlaylistsCacheKey = stringPreferencesKey("spotify_library_playlists_cache")