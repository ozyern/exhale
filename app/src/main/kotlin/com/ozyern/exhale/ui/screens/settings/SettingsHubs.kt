/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.utils.backToMain

/** ColorOS's Settings colours, one to a row. */
private val Yellow = Color(0xFFF7B500)
private val Blue = Color(0xFF2F7BF6)
private val Green = Color(0xFF2DBE5A)
private val Orange = Color(0xFFFF7A1A)
private val Red = Color(0xFFF5413B)
private val Purple = Color(0xFF8E5CF7)
private val Teal = Color(0xFF19B3C8)
private val Grey = Color(0xFF9AA0A6)

/**
 * The front page of Settings: a handful of sections, each a page of its own, rather than every
 * page in one long list. What each section holds is in [settingsHub].
 */
@Composable
fun buildSettingsIndex(navController: NavController, hasUpdate: Boolean, resetSearch: () -> Unit): List<SettingsGroup> {
    @Composable
    fun row(icon: Int, title: String, color: Color, route: String, update: Boolean = false) = SettingsItem(
        icon = painterResource(icon),
        title = title,
        accentColor = color,
        showUpdateIndicator = update,
        onClick = { resetSearch(); navController.navigate(route) },
    )
    return listOf(
        SettingsGroup("", listOf(row(R.drawable.fire, "Listen Together", Teal, "settings/music_together"))),
        SettingsGroup(
            "",
            listOf(
                row(R.drawable.lyrics, "Player & Lyrics", Red, "settings/section/player_lyrics"),
                row(R.drawable.graphic_eq, "Playback & Audio", Red, "settings/section/playback_audio"),
                row(R.drawable.palette, "Appearance", Purple, "settings/section/appearance"),
            ),
        ),
        SettingsGroup(
            "",
            listOf(
                row(R.drawable.library_music, "Library & Content", Orange, "settings/section/library_content"),
                row(R.drawable.account, "Accounts & Services", Green, "settings/section/accounts"),
            ),
        ),
        SettingsGroup(
            "",
            listOf(
                row(R.drawable.security, "Privacy & Storage", Blue, "settings/section/privacy_storage"),
                row(R.drawable.history, "History", Teal, "history"),
                row(R.drawable.stats, "Stats", Yellow, "stats"),
            ),
        ),
        SettingsGroup(
            "",
            listOf(
                row(R.drawable.update, "Software Update", Grey, "settings/update", update = hasUpdate),
                row(R.drawable.info, "Help & About", Grey, "settings/section/help_about"),
            ),
        ),
    )
}

/** A section's title and what it holds. Rows open a page, and where the page is long, the part of it they name. */
@Composable
private fun settingsHub(id: String, navController: NavController): Pair<String, List<SettingsGroup>> {
    @Composable
    fun row(icon: Int, title: String, subtitle: String, color: Color, route: String, jumpTo: String? = null) = SettingsItem(
        icon = painterResource(icon),
        title = title,
        subtitle = subtitle,
        accentColor = color,
        onClick = {
            jumpTo?.let(SettingsJump::request)
            navController.navigate(route)
        },
    )
    val player = stringResource(R.string.player)
    val lyrics = stringResource(R.string.lyrics)
    val misc = stringResource(R.string.misc)
    return when (id) {
        "player_lyrics" -> "Player & Lyrics" to listOf(
            SettingsGroup(
                "Player",
                listOf(
                    row(R.drawable.style, "Player design", "Now Playing, Espresso, Liquid Glass, Classic and more", Red, "settings/appearance/player_style"),
                    row(R.drawable.tune, "Controls & layout", "Thumbnail, mini player, buttons and badges", Red, "settings/appearance", player),
                    row(R.drawable.image, "Canvas & background", "Animated artwork and what sits behind the player", Teal, "settings/appearance", player),
                ),
            ),
            SettingsGroup(
                "Lyrics",
                listOf(
                    row(R.drawable.lyrics, "Lyrics style", "Animation, position, scrolling and swipe", Red, "settings/content", lyrics),
                    row(R.drawable.text_fields, "Text & layout", "Text size, spacing and full-screen lyrics", Orange, "settings/content", lyrics),
                    row(R.drawable.list, "Sources & priority", "Choose and order lyric providers", Blue, "settings/content/lyrics_sources"),
                    row(R.drawable.translate, "Romanization & translation", "Lyrics in Latin letters, or in your language", Purple, "settings/content", lyrics),
                ),
            ),
        )
        "playback_audio" -> "Playback & Audio" to listOf(
            SettingsGroup(
                "Sound",
                listOf(
                    row(R.drawable.equalizer, "Equalizer", "Shape the sound", Green, "settings/player/equalizer"),
                    row(R.drawable.graphic_eq, "Quality & sound", "Audio quality, normalization and skip silence", Blue, "settings/player", "Streaming"),
                    row(R.drawable.mix, "Song transitions", "Crossfade, gapless and AutoMix", Purple, "settings/player", "Playback"),
                ),
            ),
            SettingsGroup(
                "Queue & behaviour",
                listOf(
                    row(R.drawable.queue_music, "Queue & autoplay", "Persistent queue, similar songs, shuffle memory", Orange, "settings/player", stringResource(R.string.queue)),
                    row(R.drawable.bluetooth, "Bluetooth & output", "Resume on connect, pause when muted, Cast", Blue, "settings/player", "Devices"),
                ),
            ),
            SettingsGroup(
                "Advanced",
                listOf(
                    row(R.drawable.speed, "Advanced playback", "Offload, bit-perfect and queue rules", Grey, "settings/player", "Output"),
                    row(R.drawable.music_note, "Lossless sources", "Where FLAC comes from, and in what order", Teal, "settings/player/lossless_sources"),
                ),
            ),
        )
        "appearance" -> "Appearance" to listOf(
            SettingsGroup(
                "Theme & colors",
                listOf(
                    row(R.drawable.palette, "Theme", "Accent, dark mode and pure black", Purple, "settings/appearance", stringResource(R.string.theme)),
                    row(R.drawable.style, "Presets", "Ready-made looks", Red, "settings/appearance/palette_picker"),
                    row(R.drawable.tune, "Make your own", "Design a theme from scratch", Orange, "settings/appearance/theme_creator"),
                    row(R.drawable.image, "Liquid Glass", "Blur, tint, highlights and lens", Blue, "settings/appearance", stringResource(R.string.display)),
                ),
            ),
            SettingsGroup(
                "App",
                listOf(
                    row(R.drawable.text_fields, "Font & display", "Typeface, density and the dock", Grey, "settings/appearance", stringResource(R.string.display)),
                    row(R.drawable.exhale, "App icon", "Home screen icon", Blue, "settings/appearance/app_icon"),
                    row(R.drawable.list, "Layout & home", "Default tab, home sections and playlists", Orange, "settings/appearance", misc),
                ),
            ),
            SettingsGroup(
                "Advanced",
                listOf(
                    row(R.drawable.sliders, "Interface size", "Scale everything up or down", Grey, "settings/appearance/ui_scale"),
                    row(R.drawable.haptic, "Haptics", "Intensity and how touches feel", Green, "settings/haptics"),
                    row(R.drawable.bedtime, "Always-on display", "What shows while the screen sleeps", Purple, "settings/appearance/always_on_display"),
                ),
            ),
        )
        "library_content" -> "Library & Content" to listOf(
            SettingsGroup(
                "Content",
                listOf(
                    row(R.drawable.language, "Region & filters", "Language, country, explicit, Shorts, data saver", Blue, "settings/content", stringResource(R.string.general)),
                    row(R.drawable.translate, "App language", "The language Exhale speaks", Purple, "settings/content", stringResource(R.string.app_language)),
                    row(R.drawable.person, "Artist pages & home", "Quick picks, charts and what the home page shows", Orange, "settings/content", misc),
                ),
            ),
            SettingsGroup(
                "Library",
                listOf(
                    row(R.drawable.storage, "Downloads & cache", "What is kept on this phone", Green, "settings/storage"),
                    row(R.drawable.restore, "Backup & restore", "Save or restore your library", Teal, "settings/backup_restore"),
                ),
            ),
        )
        "accounts" -> "Accounts & Services" to listOf(
            SettingsGroup(
                "Accounts",
                listOf(
                    row(R.drawable.account, "YouTube Music", "Sign-in and sync", Red, "account"),
                    row(R.drawable.discord, "Discord", "Listening status", Color(0xFF5865F2), "settings/discord"),
                    row(R.drawable.music_note, "Last.fm", "Scrobbling", Red, "settings/lastfm"),
                    row(R.drawable.integration, "Integrations", "Every service Exhale connects to", Purple, "settings/integration"),
                ),
            ),
            SettingsGroup(
                "Services",
                listOf(
                    row(R.drawable.wifi_proxy, "Network & proxy", "IP version and proxy", Grey, "settings/content", stringResource(R.string.proxy)),
                    row(R.drawable.token, "Playback tokens", "How streams are signed", Teal, "settings/po_token"),
                    row(R.drawable.android_auto, "Android Auto", "Exhale in the car", Blue, "settings/android_auto"),
                ),
            ),
        )
        "privacy_storage" -> "Privacy & Storage" to listOf(
            SettingsGroup(
                "",
                listOf(
                    row(R.drawable.security, "Privacy", "History and screenshots", Blue, "settings/privacy"),
                    row(R.drawable.storage, "Storage", "Downloads and caches", Grey, "settings/storage"),
                    row(R.drawable.restore, "Backup & restore", "Save or restore your library", Green, "settings/backup_restore"),
                ),
            ),
        )
        else -> "Help & About" to listOf(
            SettingsGroup(
                "",
                listOf(
                    row(R.drawable.update, "Changelog", "What's new", Blue, "settings/changelog"),
                    row(R.drawable.experiment, "Experimental", "Things still being tried", Orange, "settings/misc"),
                    row(R.drawable.info, "About", "Version, licenses and project", Grey, "settings/about"),
                ),
            ),
        )
    }
}

/** One section of Settings: its groups of rows under its name. */
@Composable
fun SettingsSectionScreen(navController: NavController, id: String) {
    val (title, groups) = settingsHub(id, navController)
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        groups.forEach { SettingsGroupCard(it) }
        Spacer(Modifier.height(24.dp))
    }
    SettingsTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}
