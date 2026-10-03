/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.PreferenceGroup
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.*
import com.ozyern.exhale.ui.component.*
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.setAppLocale
import java.net.Proxy
import java.util.Locale
import androidx.core.net.toUri

private fun getLanguageDisplayName(languageCode: String): String {
    return when (languageCode) {
        SYSTEM_DEFAULT -> "System Default"
        else -> LanguageCodeToName[languageCode] ?: languageCode
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (appLanguage, onAppLanguageChange) = rememberPreference(
        key = AppLanguageKey,
        defaultValue = SYSTEM_DEFAULT
    )

    val (contentLanguage, onContentLanguageChange) = rememberPreference(
        key = ContentLanguageKey,
        defaultValue = "system"
    )
    val (contentCountry, onContentCountryChange) = rememberPreference(
        key = ContentCountryKey,
        defaultValue = "system"
    )
    val (hideExplicit, onHideExplicitChange) = rememberPreference(
        key = HideExplicitKey,
        defaultValue = false
    )
    val (hideVideo, onHideVideoChange) = rememberPreference(
        key = HideVideoKey,
        defaultValue = false
    )
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(
        key = ProxyEnabledKey,
        defaultValue = false
    )
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(
        key = ProxyTypeKey,
        defaultValue = Proxy.Type.HTTP
    )
    val (proxyUrl, onProxyUrlChange) = rememberPreference(
        key = ProxyUrlKey,
        defaultValue = "host:port"
    )
    val (streamBypassProxy, onStreamBypassProxyChange) = rememberPreference(
        key = StreamBypassProxyKey,
        defaultValue = false
    )
    val (enableKugou, onEnableKugouChange) = rememberPreference(
        key = EnableKugouKey,
        defaultValue = true
    )
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(
        key = EnableLrcLibKey,
        defaultValue = true
    )
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(
        key = EnableBetterLyricsKey,
        defaultValue = true
    )
    val (enableSimpMusicLyrics, onEnableSimpMusicLyricsChange) =
        rememberPreference(
            key = EnableSimpMusicLyricsKey,
            defaultValue = true
        )
    val (enableLyricsPlus, onEnableLyricsPlusChange) = rememberPreference(EnableLyricsPlusKey, defaultValue = true)
    val (enableBinimum, onEnableBinimumChange) = rememberPreference(EnableBinimumLyricsKey, defaultValue = true)
    val (enableUnison, onEnableUnisonChange) = rememberPreference(EnableUnisonLyricsKey, defaultValue = true)
    val (enableMegalobiz, onEnableMegalobizChange) = rememberPreference(EnableMegalobizLyricsKey, defaultValue = true)
    val (enableGenius, onEnableGeniusChange) = rememberPreference(EnableGeniusLyricsKey, defaultValue = true)
    val (preferredProvider, onPreferredProviderChange) =
        rememberEnumPreference(
            key = PreferredLyricsProviderKey,
            defaultValue = PreferredLyricsProvider.BINIMUM,
        )
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) = rememberPreference(
        LyricsRomanizeJapaneseKey,
        defaultValue = true
    )
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) = rememberPreference(
        LyricsRomanizeKoreanKey,
        defaultValue = true
    )
    val (preloadQueueLyricsEnabled, onPreloadQueueLyricsEnabledChange) = rememberPreference(
        PreloadQueueLyricsEnabledKey,
        defaultValue = true
    )
    val (lockScreenLyrics, onLockScreenLyricsChange) = rememberPreference(
        EnableLockScreenLyricsKey,
        defaultValue = true
    )
    val (lyricsOnMediaCard, onLyricsOnMediaCardChange) = rememberPreference(
        LyricsOnMediaCardKey,
        defaultValue = true
    )
    val (queueLyricsPreloadCount, onQueueLyricsPreloadCountChange) = rememberPreference(
        QueueLyricsPreloadCountKey,
        defaultValue = 1
    )
    val (lengthTop, onLengthTopChange) = rememberPreference(
        key = TopSize,
        defaultValue = "50"
    )
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(
        key = QuickPicksKey,
        defaultValue = QuickPicks.QUICK_PICKS
    )

    var showLanguageSelector by remember { mutableStateOf(false) }

    val languageOptions = remember {
        LanguageCodeToName.map { (code, name) ->
            LanguageOption(code = code, displayName = name)
        }
    }

    var showProviderOrderDialog by remember { mutableStateOf(false) }

    val (providerOrder, onProviderOrderChange) = rememberPreference(
        key = ProviderOrderKey,
        defaultValue = DefaultProviderOrder.joinToString(",") { it.name },
    )

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(title = stringResource(R.string.general))
        PreferenceGroup {
            PreferenceEntry(
                title = { Text("Song preferences") },
                subtitle = { Text("Set your audio languages & favorite artists") },
                icon = { Icon(painterResource(R.drawable.style), null) },
                onClick = { navController.navigate("song_preferences") },
            )

            PreferenceGroupDivider()
            ListPreference(
                title = { Text(stringResource(R.string.content_language)) },
                icon = { Icon(painterResource(R.drawable.language), null) },
                selectedValue = contentLanguage,
                values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                valueText = {
                    LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                },
                onValueSelected = { newValue ->
                    val locale = Locale.getDefault()
                    val languageTag = locale.toLanguageTag().replace("-Hant", "")

                    YouTube.locale = YouTube.locale.copy(
                        hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                            ?: locale.language.takeIf { it in LanguageCodeToName }
                            ?: languageTag.takeIf { it in LanguageCodeToName }
                            ?: "en"
                    )

                    onContentLanguageChange(newValue)
                }
            )

            PreferenceGroupDivider()
            ListPreference(
                title = { Text(stringResource(R.string.content_country)) },
                icon = { Icon(painterResource(R.drawable.location_on), null) },
                selectedValue = contentCountry,
                values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                valueText = {
                    CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                },
                onValueSelected = { newValue ->
                    val locale = Locale.getDefault()

                    YouTube.locale = YouTube.locale.copy(
                        gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                            ?: locale.country.takeIf { it in CountryCodeToName }
                            ?: "US"
                    )

                    onContentCountryChange(newValue)
                }
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.hide_explicit)) },
                icon = { Icon(painterResource(R.drawable.explicit), null) },
                checked = hideExplicit,
                onCheckedChange = onHideExplicitChange,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.hide_video)) },
                icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
                checked = hideVideo,
                onCheckedChange = onHideVideoChange,
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.app_language))
        PreferenceGroup {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.app_language)) },
                    subtitle = {
                        Text(
                            text = getLanguageDisplayName(appLanguage)
                        )
                    },
                    icon = { Icon(painterResource(R.drawable.translate), null) },
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_LOCALE_SETTINGS,
                                "package:${context.packageName}".toUri()
                            )
                        )
                    }
                )
            } else {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.app_language)) },
                    subtitle = {
                        Text(
                            text = getLanguageDisplayName(appLanguage)
                        )
                    },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    onClick = { showLanguageSelector = true }
                )
            }
        }

        PreferenceGroupTitle(title = stringResource(R.string.proxy))
        PreferenceGroup {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_proxy)) },
                icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                checked = proxyEnabled,
                onCheckedChange = onProxyEnabledChange,
            )

            if (proxyEnabled) {
                Column {
                    ListPreference(
                        title = { Text(stringResource(R.string.proxy_type)) },
                        selectedValue = proxyType,
                        values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                        valueText = { it.name },
                        onValueSelected = onProxyTypeChange,
                    )
                    EditTextPreference(
                        title = { Text(stringResource(R.string.proxy_url)) },
                        value = proxyUrl,
                        onValueChange = onProxyUrlChange,
                    )
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stream_bypass_proxy)) },
                        description = stringResource(R.string.stream_bypass_proxy_desc),
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        checked = streamBypassProxy,
                        onCheckedChange = {
                            onStreamBypassProxyChange(it)
                            YouTube.streamBypassProxy = it
                        },
                    )
                }
            }
        }

        PreferenceGroupTitle(title = stringResource(R.string.lyrics))
        PreferenceGroup {
            // Which sources, and in what order, in one reorderable list of its own.
            val sourceOrder = remember(providerOrder) { lyricsSourceOrder(providerOrder) }
            val enabledCount = sourceOrder.count { source ->
                when (source) {
                    com.ozyern.exhale.constants.PreferredLyricsProvider.LRCLIB -> enableLrclib
                    com.ozyern.exhale.constants.PreferredLyricsProvider.KUGOU -> enableKugou
                    com.ozyern.exhale.constants.PreferredLyricsProvider.BETTER_LYRICS -> enableBetterLyrics
                    com.ozyern.exhale.constants.PreferredLyricsProvider.SIMPMUSIC -> enableSimpMusicLyrics
                    com.ozyern.exhale.constants.PreferredLyricsProvider.LYRICS_PLUS -> enableLyricsPlus
                    com.ozyern.exhale.constants.PreferredLyricsProvider.BINIMUM -> enableBinimum
                    com.ozyern.exhale.constants.PreferredLyricsProvider.UNISON -> enableUnison
                    com.ozyern.exhale.constants.PreferredLyricsProvider.MEGALOBIZ -> enableMegalobiz
                    com.ozyern.exhale.constants.PreferredLyricsProvider.GENIUS -> enableGenius
                    else -> true
                }
            }
            PreferenceEntry(
                title = { Text("Lyrics sources") },
                description = "$enabledCount of ${sourceOrder.size} on · " +
                    sourceOrder.take(3).joinToString(" → ") { it.displayName() } + " …",
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                onClick = { navController.navigate("settings/content/lyrics_sources") },
            )

            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeJapanese,
                onCheckedChange = onLyricsRomanizeJapaneseChange,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsRomanizeKorean,
                onCheckedChange = onLyricsRomanizeKoreanChange,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.preload_queue_lyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = preloadQueueLyricsEnabled,
                onCheckedChange = onPreloadQueueLyricsEnabledChange,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.lock_screen_lyrics)) },
                description = stringResource(R.string.lock_screen_lyrics_desc) + "\n" + lockScreenLyricsStatus(),
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lockScreenLyrics,
                onCheckedChange = onLockScreenLyricsChange,
            )

            // The path that works on stock ColorOS: Exhale's own screen over the keyguard.
            val (lockOverlay, onLockOverlayChange) = rememberPreference(
                com.ozyern.exhale.constants.LockScreenLyricsOverlayKey,
                defaultValue = true,
            )
            SwitchPreference(
                title = { Text("Lyrics screen on the lock screen") },
                description = "While music plays, Exhale's own lyrics screen sits over the lock screen. " +
                    "Works on any phone, ColorOS included, without root.",
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lockOverlay,
                onCheckedChange = onLockOverlayChange,
            )

            if (lockOverlay) {
                LockScreenLyricsSetup()
            }

            // Independent of the Live Space switch above, and on by default. See
            // `LyricsOnMediaCardKey`: the two do interfere, but only on the phones where Live Space
            // works at all, and disabling this one whenever that one was on left the stock case -
            // almost every case - with no lyrics on the lock screen whatsoever.
            SwitchPreference(
                title = { Text(stringResource(R.string.lyrics_on_media_card)) },
                description = stringResource(R.string.lyrics_on_media_card_desc),
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = lyricsOnMediaCard,
                onCheckedChange = onLyricsOnMediaCardChange,
            )

            if (preloadQueueLyricsEnabled) {
                NumberPickerPreference(
                    title = { Text(stringResource(R.string.queue_lyrics_preload_count)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    value = queueLyricsPreloadCount,
                    onValueChange = onQueueLyricsPreloadCountChange,
                    minValue = 0,
                    maxValue = 10,
                    valueText = { if (it == 0) "Off" else it.toString() },
                )
            }
        }

        PreferenceGroupTitle(title = stringResource(R.string.misc))
        PreferenceGroup {
            EditTextPreference(
                title = { Text(stringResource(R.string.top_length)) },
                icon = { Icon(painterResource(R.drawable.trending_up), null) },
                value = lengthTop,
                isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                onValueChange = onLengthTopChange,
            )

            ListPreference(
                title = { Text(stringResource(R.string.set_quick_picks)) },
                icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                selectedValue = quickPicks,
                values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN),
                valueText = {
                    when (it) {
                        QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                        QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                    }
                },
                onValueSelected = onQuickPicksChange,
            )
        }

    }

    LanguageSelectorBottomSheet(
        show = showLanguageSelector,
        title = "Select App Language",
        languages = languageOptions,
        selectedCode = appLanguage,
        systemDefaultCode = SYSTEM_DEFAULT,
        systemDefaultLabel = "System Default",
        searchPlaceholder = "Search language...",
        onDismiss = { showLanguageSelector = false },
        onLanguageSelected = { selectedCode ->
            onAppLanguageChange(selectedCode)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val newLocale = if (selectedCode == SYSTEM_DEFAULT) {
                    Locale.getDefault()
                } else {
                    Locale.forLanguageTag(selectedCode)
                }
                setAppLocale(context, newLocale)
            }

            showLanguageSelector = false
        }
    )

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.content)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        }
    )
}
/**
 * Which lock-screen path this phone is on, in one line, so "it doesn't show" can be answered by
 * reading the setting rather than by guessing. Stock ColorOS draws `lyricInfo` only for OPlus's
 * partner players; the ColorOS Live Lyrics Bridge (an LSPosed module) is what admits Exhale.
 */
@Composable
private fun lockScreenLyricsStatus(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bridge = androidx.compose.runtime.remember {
        runCatching {
            context.packageManager.getPackageInfo(com.ozyern.exhale.playback.OplusLiveLyrics.BRIDGE_PACKAGE, 0)
        }.isSuccess
    }
    return when {
        !com.ozyern.exhale.utils.DeviceAudio.isOplusDevice -> "Not a ColorOS / OxygenOS phone: the media card line is used"
        bridge -> "Live Lyrics Bridge found: full synced lyrics on the lock screen"
        else -> "Live Lyrics Bridge not found: stock ColorOS only shows lyrics from its partner apps, so the " +
            "current line is shown on the media card instead. Install the Bridge (LSPosed) for full lock-screen lyrics"
    }
}

/**
 * What the lock-screen lyrics screen needs from the system, each row saying whether it has it and
 * opening the right page when it does not. Re-read every time the page comes back into view, so
 * a permission granted in Settings shows as granted on return.
 */
@Composable
private fun LockScreenLyricsSetup() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var checks by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val lock = com.ozyern.exhale.playback.LockScreenLyrics
    val fullScreen = androidx.compose.runtime.remember(checks) { lock.canUseFullScreen(context) && !lock.channelMuted(context) }
    val overlay = androidx.compose.runtime.remember(checks) { lock.canShow(context) }

    fun open(action: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(action, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    PreferenceEntry(
        title = { Text("Full-screen notifications") },
        description = if (fullScreen) "Allowed" else "Not allowed — tap to allow. This is what opens the lyrics screen when the phone locks",
        icon = { Icon(painterResource(if (fullScreen) R.drawable.check else R.drawable.info), null) },
        onClick = {
            when {
                !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                    lock.channelMuted(context) -> runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                android.os.Build.VERSION.SDK_INT >= 34 -> open(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                else -> open(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
        },
    )
    PreferenceEntry(
        title = { Text("Display over other apps") },
        description = if (overlay) "Allowed" else "Not allowed — tap to allow. A second way in, for phones that hold back full-screen notifications",
        icon = { Icon(painterResource(if (overlay) R.drawable.check else R.drawable.info), null) },
        onClick = { open(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION) },
    )
    if (com.ozyern.exhale.utils.DeviceAudio.isOplusDevice) {
        PreferenceEntry(
            title = { Text("ColorOS permissions") },
            description = "In Exhale's app info, under Permissions → Other permissions, allow " +
                "\"Show on Lock screen\" and \"Display pop-up windows while running in the background\"",
            icon = { Icon(painterResource(R.drawable.settings), null) },
            onClick = { open(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
        )
    }
    PreferenceEntry(
        title = { Text("Preview") },
        description = "Open the lyrics screen now. Play a song with synced lyrics first",
        icon = { Icon(painterResource(R.drawable.lyrics), null) },
        onClick = { lock.preview(context) },
    )
}
