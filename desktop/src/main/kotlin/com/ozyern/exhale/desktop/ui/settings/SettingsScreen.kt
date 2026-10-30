/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AccentPalettes
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.AppInfo
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.AppStorage
import com.ozyern.exhale.desktop.data.ContentLocale
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.EqualizerPresets
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.data.NetworkProxy
import com.ozyern.exhale.desktop.data.ReleaseInfo
import com.ozyern.exhale.desktop.data.Settings
import com.ozyern.exhale.desktop.data.SystemShell
import com.ozyern.exhale.desktop.data.UpdateChecker
import com.ozyern.exhale.desktop.integrations.LastFmAuth
import com.ozyern.exhale.desktop.integrations.ListenBrainz
import com.ozyern.exhale.desktop.lyrics.LyricsRepository
import com.ozyern.exhale.desktop.player.AudioOutputStatus
import com.ozyern.exhale.desktop.player.DolbyAccess
import com.ozyern.exhale.desktop.ui.AppShortcuts
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.account.SignInBrowser
import com.ozyern.exhale.desktop.ui.feedIntro
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.GlassIconButton
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ── Settings as data, so every row renders the same way and search can reach all of them ──

private sealed class Item {
    abstract val title: String
    abstract val subtitle: String?
    open val keywords: String get() = ""
    var icon: ImageVector? = null

    /** The stored key, when the row is one setting: marks it when changed and offers a reset. */
    var key: String? = null
}

private fun <T : Item> T.withIcon(icon: ImageVector, key: String? = null): T = apply {
    this.icon = icon
    this.key = key
}

private class SwitchItem(override val title: String, override val subtitle: String?, val checked: Boolean, val onChange: (Boolean) -> Unit) : Item()

private class ChoiceItem(
    override val title: String,
    override val subtitle: String?,
    val options: List<Pair<String, String>>,
    val selected: String,
    val onSelect: (String) -> Unit,
) : Item()

private class SliderItem(
    override val title: String,
    override val subtitle: String?,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val format: (Float) -> String,
    val onChange: (Float) -> Unit,
) : Item()

private class ActionItem(
    override val title: String,
    override val subtitle: String?,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
) : Item()

private class CustomItem(
    override val title: String,
    override val subtitle: String? = null,
    override val keywords: String = "",
    val content: @Composable () -> Unit,
) : Item()

private class Group(val title: String, val items: List<Item>)

private class Category(
    val id: String,
    val label: String,
    val summary: String,
    val icon: ImageVector,
    val accent: (ColorScheme) -> Color,
    val groups: List<Group>,
) {
    val keys: List<String> get() = groups.flatMap { group -> group.items.mapNotNull { it.key } }
}

private fun Item.matches(query: String) =
    title.lowercase().contains(query) || subtitle?.lowercase()?.contains(query) == true || keywords.lowercase().contains(query)

private fun update(transform: (Settings) -> Settings) = AppSettings.update(transform)

private val DiscordBlurple = Color(0xFF5865F2)
private val LastFmRed = Color(0xFFD51007)
private val ListenBrainzOrange = Color(0xFFEB743B)
private val DolbyViolet = Color(0xFF9D8CFF)

/** The accent of the category a row belongs to, for its icon puck. */
private val LocalRowAccent = compositionLocalOf { Color.Unspecified }

/** The fixed hue of the page a row is on, for its icon. */
private val LocalRowHue = compositionLocalOf { Color.Unspecified }

/** Keys that differ from their default, for the changed dot and the reset button. */
private val LocalChangedKeys = compositionLocalOf { emptySet<String>() }

/** Resets one key, running whatever has to follow (a new locale, a new proxy…). */
private val LocalReset = compositionLocalOf<(String) -> Unit> { {} }

/** What's typed in the settings search, so result rows can light up the match. */
private val LocalSearchQuery = compositionLocalOf { "" }

private val ContentLanguages = listOf(
    "en" to "English", "en-GB" to "English (UK)", "hi" to "हिन्दी", "es" to "Español", "pt" to "Português",
    "pt-BR" to "Português (Brasil)", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano", "nl" to "Nederlands",
    "pl" to "Polski", "tr" to "Türkçe", "ru" to "Русский", "uk" to "Українська", "ar" to "العربية", "he" to "עברית",
    "ja" to "日本語", "ko" to "한국어", "zh" to "中文", "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "th" to "ไทย",
    "vi" to "Tiếng Việt", "id" to "Bahasa Indonesia", "ms" to "Bahasa Melayu", "cs" to "Čeština", "el" to "Ελληνικά",
    "hu" to "Magyar", "ro" to "Română", "fi" to "Suomi", "da" to "Dansk", "no" to "Norsk", "sv" to "Svenska",
)

private val ContentCountries = listOf(
    "US" to "United States", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia", "IN" to "India",
    "DE" to "Germany", "FR" to "France", "ES" to "Spain", "IT" to "Italy", "NL" to "Netherlands", "PL" to "Poland",
    "TR" to "Turkey", "RU" to "Russia", "MX" to "Mexico", "BR" to "Brazil", "AR" to "Argentina", "CL" to "Chile",
    "CO" to "Colombia", "PE" to "Peru", "JP" to "Japan", "KR" to "South Korea", "CN" to "China", "TW" to "Taiwan",
    "HK" to "Hong Kong", "ID" to "Indonesia", "TH" to "Thailand", "VN" to "Vietnam", "PH" to "Philippines",
    "MY" to "Malaysia", "SG" to "Singapore", "ZA" to "South Africa", "EG" to "Egypt", "SA" to "Saudi Arabia",
    "AE" to "United Arab Emirates",
)

private fun offsetLabel(ms: Float): String {
    val value = ms.roundToInt()
    return when {
        value == 0 -> "In sync"
        value > 0 -> "$value ms earlier"
        else -> "${-value} ms later"
    }
}

private fun categories(
    s: Settings,
    onSignIn: () -> Unit,
    onEditTaste: () -> Unit,
    onHomeInputsChanged: () -> Unit,
    onDiscordLogin: () -> Unit,
): List<Category> = listOf(
    Category(
        "account", "Account", "YouTube Music sign-in", NavIcons.Artists, { it.primary },
        listOf(
            Group(
                "YouTube Music",
                listOfNotNull(
                    CustomItem("Account", "Sign in, sign out", keywords = "youtube google login") { AccountCard(onSignIn, onHomeInputsChanged) },
                    if (AccountSession.isSignedIn) {
                        SwitchItem("Use my account for Home", "Recommendations from your YouTube Music history instead of your region.", DesktopPrefs.useAccountForHome) {
                            AccountSession.setUseAccountForHome(it)
                            onHomeInputsChanged()
                        }.withIcon(NavIcons.Home)
                    } else {
                        null
                    },
                ),
            ),
        ),
    ),
    Category(
        "appearance", "Appearance", "Theme, glass and motion", NavIcons.Palette, { it.primary },
        listOf(
            Group("Preview", listOf(CustomItem("Live preview", "How Exhale looks with these settings", keywords = "preview look") { AppearancePreview(s) })),
            Group(
                "Theme",
                listOf(
                    CustomItem("Accent colour", AccentPalettes.byId(s.accent).label, keywords = "palette theme colour color") { PalettePicker(s) },
                    SwitchItem("Colour from artwork", "Tint the whole app from what's playing.", s.dynamicTheme) { v -> update { it.copy(dynamicTheme = v) } }.withIcon(NavIcons.Sparkles, "dynamic_theme"),
                    SwitchItem("Pure black", "True black backgrounds.", s.pureBlack) { v -> update { it.copy(pureBlack = v) } }.withIcon(NavIcons.Moon, "pure_black"),
                    SliderItem("Interface size", "Scales everything — text, artwork, controls.", s.uiScale, 0.8f..1.3f, 9, { "${(it * 100).roundToInt()}%" }) { v ->
                        update { it.copy(uiScale = (v * 20).roundToInt() / 20f) }
                    }.withIcon(NavIcons.TextFields, "ui_scale"),
                ),
            ),
            Group(
                "Layout",
                listOf(
                    ChoiceItem(
                        "Open on", "The page Exhale starts on.",
                        listOf("HOME" to "Home", "MOOD" to "Mood & Genres", "LIBRARY" to "Library", "SEARCH" to "Search"),
                        s.defaultTab,
                    ) { v -> update { it.copy(defaultTab = v) } }.withIcon(NavIcons.Home, "default_tab"),
                    ChoiceItem("Card size", null, listOf("BIG" to "Big", "SMALL" to "Small"), s.gridSize) { v -> update { it.copy(gridSize = v) } }.withIcon(NavIcons.New, "grid_size"),
                    SliderItem("Artwork corners", null, s.artworkCorner.toFloat(), 0f..32f, 15, { "${it.roundToInt()} dp" }) { v ->
                        update { it.copy(artworkCorner = v.roundToInt()) }
                    }.withIcon(NavIcons.Image, "artwork_corner"),
                ),
            ),
            Group(
                "Effects",
                listOf(
                    ChoiceItem(
                        "Graphics",
                        // What Auto found, so the choice it made isn't a mystery.
                        "Lite keeps the look with still frosted panels, for older PCs. Auto picked " +
                            (if (com.ozyern.exhale.desktop.data.Graphics.detectedLite) "Lite" else "Full") +
                            (com.ozyern.exhale.desktop.data.Graphics.detectedGpu?.let { " for $it." } ?: "."),
                        listOf("AUTO" to "Auto", "FULL" to "Full", "LITE" to "Lite"),
                        s.graphics,
                    ) { v -> update { it.copy(graphics = v) } }.withIcon(NavIcons.Chip, "graphics"),
                    SwitchItem("Liquid glass", "Blur and refraction on the dock, bars and menus. Off uses plain frosted panels.", s.liquidGlass) { v -> update { it.copy(liquidGlass = v) } }.withIcon(NavIcons.Glass, "liquid_glass"),
                    SwitchItem("Moving artwork", "The cover's colours drift slowly behind pages and the player. Drawn on the GPU.", s.animatedBackground) { v -> update { it.copy(animatedBackground = v) } }.withIcon(NavIcons.Sparkles, "animated_background"),
                    SwitchItem("Moving covers", "Plays a song's moving cover in the player, from Apple Music or Tidal, where it has one.", s.motionCovers) { v -> update { it.copy(motionCovers = v) } }.withIcon(NavIcons.Image, "motion_covers"),
                    SwitchItem("Ambient glow", "A wash of the artwork's colour behind the page.", s.ambientGlow) { v -> update { it.copy(ambientGlow = v) } }.withIcon(NavIcons.Palette, "ambient_glow"),
                    SwitchItem("Tilt cards under the pointer", "Covers lean toward the mouse and catch the light.", s.cardTilt) { v -> update { it.copy(cardTilt = v) } }.withIcon(NavIcons.Image, "card_tilt"),
                    ChoiceItem(
                        "Player background", null,
                        listOf("ARTWORK" to "Artwork", "BLUR" to "Blur", "GRADIENT" to "Gradient", "GLOW" to "Glow", "SOLID" to "Solid"),
                        s.playerBackground,
                    ) { v -> update { it.copy(playerBackground = v) } }.withIcon(NavIcons.Image, "player_background"),
                    SwitchItem("Reduce motion", "Skip the page intro and other decorative animation.", s.reducedMotion) { v -> update { it.copy(reducedMotion = v) } }.withIcon(NavIcons.Speed, "reduced_motion"),
                ),
            ),
        ),
    ),
    Category(
        "player", "Player & audio", "Quality, spatial audio, equalizer", NavIcons.Headphones, { it.tertiary },
        listOf(
            Group(
                "Streaming",
                listOf(
                    ChoiceItem("Audio quality", "Max takes the highest bitrate YouTube offers — 256 kbps with Premium.", listOf("MAX" to "Max", "HIGH" to "High", "LOW" to "Low"), s.audioQuality) { v -> update { it.copy(audioQuality = v) } }.withIcon(NavIcons.HighQuality, "audio_quality"),
                    ChoiceItem("Codec", "Preferred when a stream offers both.", listOf("AUTO" to "Auto", "OPUS" to "Opus", "AAC" to "AAC"), s.audioCodec) { v -> update { it.copy(audioCodec = v) } }.withIcon(TransportIcons.Equalizer, "audio_codec"),
                    ChoiceItem(
                        "Stream client", "Which YouTube client is asked first. Others are tried if it fails.",
                        listOf("AUTO" to "Automatic", "ANDROID_VR" to "Android VR", "IOS" to "iOS", "WEB_REMIX" to "YouTube Music web", "TVHTML5" to "TV", "ANDROID_MUSIC" to "Android Music"),
                        s.streamClient,
                    ) { v -> update { it.copy(streamClient = v) } }.withIcon(NavIcons.Link, "stream_client"),
                ),
            ),
            Group(
                "Lossless",
                listOf(
                    CustomItem("Lossless sources", keywords = "lossless hires hi-res flac alac wav subsonic navidrome jellyfin airsonic addon library folder") { LosslessSourcesCard() },
                    SwitchItem(
                        "Prefer lossless",
                        "When one of your sources has the song, play its copy instead of YouTube's stream.",
                        s.preferLossless,
                    ) { v -> update { it.copy(preferLossless = v) } }.withIcon(NavIcons.HighQuality, "prefer_lossless"),
                ),
            ),
            Group(
                "Spatial audio & output",
                listOf(
                    CustomItem("Dolby Access", keywords = "dolby atmos spatial sonic headphones surround") { SpatialAudioCard(s) },
                    ChoiceItem(
                        "Spatial audio", "Spreads stereo into a surround bed for Dolby Atmos or Windows Sonic to place around you.",
                        listOf("OFF" to "Off", "SURROUND_51" to "5.1", "SURROUND_71" to "7.1"),
                        s.spatialAudio,
                    ) { v -> update { it.copy(spatialAudio = v) } }.withIcon(NavIcons.Surround, "spatial_audio"),
                    SwitchItem("Exclusive mode", "Takes the device for bit-perfect output. Bypasses Windows effects, Dolby included.", s.exclusiveAudio) { v -> update { it.copy(exclusiveAudio = v) } }.withIcon(TransportIcons.VolumeUp, "exclusive_audio"),
                    SwitchItem(
                        "Dolby passthrough",
                        "Sends Dolby Atmos, Dolby Digital Plus and TrueHD untouched to a receiver or soundbar over HDMI or optical.",
                        s.dolbyPassthrough,
                    ) { v -> update { it.copy(dolbyPassthrough = v) } }.withIcon(NavIcons.DolbyAtmos, "dolby_passthrough"),
                    SwitchItem("High-quality resampler", "A sharper filter when your device doesn't run at 48 kHz.", s.hqResampler) { v -> update { it.copy(hqResampler = v) } }.withIcon(NavIcons.HighQuality, "hq_resampler"),
                ),
            ),
            Group(
                "Notification area",
                listOf(
                    SwitchItem("Icon in the notification area", "The song playing, with play, next and previous, beside the clock.", s.trayIcon) { v -> update { it.copy(trayIcon = v) } }.withIcon(TransportIcons.Play, "tray_icon"),
                    SwitchItem("Keep playing when closed", "Closing the window leaves the music on, in the notification area. Quit from its menu.", s.closeToTray) { v -> update { it.copy(closeToTray = v) } }.withIcon(TransportIcons.Pause, "close_to_tray"),
                ),
            ),
            Group(
                "Sound",
                listOf(
                    SwitchItem(
                        "Media keys control Exhale",
                        "Play, pause, next and previous on the keyboard reach Exhale even when another window is in front. Off leaves them to whatever else is playing.",
                        s.mediaKeys,
                    ) { v -> update { it.copy(mediaKeys = v) } }.withIcon(TransportIcons.Play, "media_keys"),
                    SwitchItem("Audio normalization", "Even out loudness between songs, using YouTube's loudness data.", s.normalization) { v -> update { it.copy(normalization = v) } }.withIcon(TransportIcons.VolumeDown, "normalization"),
                    SwitchItem("Skip silence", "Cut long silent stretches.", s.skipSilence) { v -> update { it.copy(skipSilence = v) } }.withIcon(TransportIcons.Next, "skip_silence"),
                    SliderItem("Fade between songs", null, s.crossfadeSeconds.toFloat(), 0f..12f, 11, { if (it < 0.5f) "Off" else "${it.roundToInt()} s" }) { v ->
                        update { it.copy(crossfadeSeconds = v.roundToInt()) }
                    }.withIcon(NavIcons.Timer, "crossfade_seconds"),
                    SliderItem("Playback speed", null, s.playbackSpeed, 0.5f..2f, 29, { String.format("%.2f×", it) }) { v ->
                        update { it.copy(playbackSpeed = (v * 20).roundToInt() / 20f) }
                    }.withIcon(NavIcons.Speed, "playback_speed"),
                    ChoiceItem(
                        "Sound preset", "Plays the song like a record at another speed, pitch and all. Normal leaves the speed above in charge.",
                        com.ozyern.exhale.desktop.player.AudioEffects.SoundPresets.map { it.first to it.second },
                        s.soundPreset,
                    ) { v -> update { it.copy(soundPreset = v) } }.withIcon(TransportIcons.Equalizer, "sound_preset"),
                    SwitchItem("8D audio", "The song circles your head, passing behind it. Best on headphones.", s.eightD) { v ->
                        update { it.copy(eightD = v) }
                    }.withIcon(NavIcons.Surround, "eight_d"),
                    SliderItem("8D speed", null, s.eightDRate, 0.05f..0.4f, 6, { "one lap every ${(1f / it).roundToInt()} s" }) { v ->
                        update { it.copy(eightDRate = v) }
                    }.withIcon(NavIcons.Timer, "eight_d_rate"),
                ),
            ),
            Group(
                "Equalizer",
                listOf(
                    SwitchItem("Equalizer", "10-band, applied by the audio engine.", s.equalizerEnabled) { v -> update { it.copy(equalizerEnabled = v) } }.withIcon(TransportIcons.Equalizer, "equalizer_enabled"),
                    CustomItem("Bands and presets", s.equalizerPreset, keywords = "eq bass treble preset") { EqualizerEditor(s) },
                ),
            ),
            Group(
                "Queue",
                listOf(
                    SwitchItem("Resume where you left off", "Bring the queue back when Exhale opens.", s.persistentQueue) { v ->
                        update { it.copy(persistentQueue = v) }
                        if (!v) DesktopPrefs.clearSavedQueue()
                    }.withIcon(TransportIcons.Queue, "persistent_queue"),
                    SwitchItem("Skip songs that won't play", "Move on to the next song after an error.", s.autoSkipOnError) { v -> update { it.copy(autoSkipOnError = v) } }.withIcon(TransportIcons.Next, "auto_skip_on_error"),
                    SwitchItem("Remember volume", null, s.rememberVolume) { v -> update { it.copy(rememberVolume = v) } }.withIcon(TransportIcons.VolumeUp, "remember_volume"),
                    ChoiceItem("Seek step", "Arrow keys in the full player.", listOf("5" to "5 s", "10" to "10 s", "15" to "15 s", "30" to "30 s"), s.seekStepSeconds.toString()) { v ->
                        update { it.copy(seekStepSeconds = v.toInt()) }
                    }.withIcon(NavIcons.Timer, "seek_step_seconds"),
                ),
            ),
        ),
    ),
    Category(
        "lyrics", "Lyrics", "Layout, karaoke, timing, sources", TransportIcons.Lyrics, { it.secondary },
        listOf(
            Group(
                "Layout",
                listOf(
                    CustomItem("Lyrics position", keywords = "side left right layout player lyrics swap") { LyricsSidePicker(s) },
                    CustomItem("Open lyrics as", keywords = "full screen fullscreen beside layout") { LyricsViewPicker(s) },
                ),
            ),
            Group(
                "Display",
                listOf(
                    ChoiceItem("Style", null, listOf("KARAOKE" to "Word by word", "LINE" to "Line by line"), s.lyricsStyle) { v -> update { it.copy(lyricsStyle = v) } }.withIcon(TransportIcons.Lyrics, "lyrics_style"),
                    SliderItem("Text size", null, s.lyricsTextSize.toFloat(), 20f..48f, 13, { "${it.roundToInt()} pt" }) { v -> update { it.copy(lyricsTextSize = v.roundToInt()) } }.withIcon(NavIcons.TextFields, "lyrics_text_size"),
                    SliderItem("Line spacing", null, s.lyricsLineSpacing.toFloat(), 0f..24f, 11, { "${it.roundToInt()} dp" }) { v -> update { it.copy(lyricsLineSpacing = v.roundToInt()) } }.withIcon(NavIcons.AlignLeft, "lyrics_line_spacing"),
                    ChoiceItem("Alignment", null, listOf("LEFT" to "Left", "CENTER" to "Centre"), s.lyricsAlign) { v -> update { it.copy(lyricsAlign = v) } }.withIcon(NavIcons.AlignLeft, "lyrics_align"),
                    SwitchItem("Click a line to jump to it", null, s.lyricsClickToSeek) { v -> update { it.copy(lyricsClickToSeek = v) } }.withIcon(NavIcons.Check, "lyrics_click_to_seek"),
                    SwitchItem("Follow the song", "Scroll with the lyrics as they're sung.", s.lyricsAutoScroll) { v -> update { it.copy(lyricsAutoScroll = v) } }.withIcon(TransportIcons.Collapse, "lyrics_auto_scroll"),
                ),
            ),
            Group(
                "Timing",
                listOf(
                    SliderItem(
                        "Sync offset",
                        "For lyrics that run behind or ahead of the song. In the player, [ and ] nudge just the song playing.",
                        s.lyricsOffsetMs.toFloat(), -1500f..1500f, 59, ::offsetLabel,
                    ) { v -> update { it.copy(lyricsOffsetMs = (v / 50).roundToInt() * 50) } }.withIcon(NavIcons.Sync, "lyrics_offset_ms"),
                ),
            ),
            Group(
                "Sources",
                listOf(
                    CustomItem("Providers", "Tried top to bottom; synced lyrics win.", keywords = "betterlyrics simpmusic lrclib kugou order") { ProviderOrderEditor(s) },
                    SwitchItem("Preload the next song's lyrics", null, s.preloadLyrics) { v -> update { it.copy(preloadLyrics = v) } }.withIcon(NavIcons.Update, "preload_lyrics"),
                ),
            ),
        ),
    ),
    Category(
        "content", "Content", "Language, region and filters", NavIcons.Language, { it.secondary },
        listOf(
            Group(
                "Your music",
                listOf(
                    ActionItem("Your Music Taste", "${DesktopPrefs.languages.size} languages · ${DesktopPrefs.artists.value.size} artists", "Edit", onClick = onEditTaste).withIcon(NavIcons.Taste),
                    ChoiceItem("Content language", "What YouTube is told you speak.", listOf("AUTO" to "Match Your Music Taste") + ContentLanguages, s.contentLanguage) { v ->
                        update { it.copy(contentLanguage = v) }
                        ContentLocale.apply()
                        onHomeInputsChanged()
                    }.withIcon(NavIcons.Language, "content_language"),
                    ChoiceItem("Content country", "Charts and releases come from here.", listOf("AUTO" to "From language") + ContentCountries, s.contentCountry) { v ->
                        update { it.copy(contentCountry = v) }
                        ContentLocale.apply()
                        onHomeInputsChanged()
                    }.withIcon(NavIcons.Language, "content_country"),
                ),
            ),
            Group(
                "Filters",
                listOf(
                    SwitchItem("Hide explicit content", null, s.hideExplicit) { v ->
                        update { it.copy(hideExplicit = v) }
                        onHomeInputsChanged()
                    }.withIcon(NavIcons.Explicit, "hide_explicit"),
                    SwitchItem("Hide music videos", "Keep shelves to audio releases.", s.hideVideos) { v ->
                        update { it.copy(hideVideos = v) }
                        onHomeInputsChanged()
                    }.withIcon(NavIcons.Image, "hide_videos"),
                ),
            ),
            Group(
                "Network",
                listOf(
                    SwitchItem("Proxy", "Send YouTube traffic through a proxy.", s.proxyEnabled) { v ->
                        update { it.copy(proxyEnabled = v) }
                        NetworkProxy.apply()
                        onHomeInputsChanged()
                    }.withIcon(NavIcons.Lock, "proxy_enabled"),
                    ChoiceItem("Proxy type", "Streams follow HTTP proxies only.", listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS"), s.proxyType) { v ->
                        update { it.copy(proxyType = v) }
                        NetworkProxy.apply()
                    }.withIcon(NavIcons.Link, "proxy_type"),
                    CustomItem("Proxy address", s.proxyUrl.ifBlank { null }, keywords = "proxy host port") {
                        TextSettingRow("Proxy address", "host:port", s.proxyUrl, "Apply") { v ->
                            update { it.copy(proxyUrl = v.trim()) }
                            NetworkProxy.apply()
                            onHomeInputsChanged()
                        }
                    },
                ),
            ),
        ),
    ),
    Category(
        "privacy", "Privacy", "Listen and search history", NavIcons.Lock, { it.error },
        listOf(
            Group(
                "History",
                listOf(
                    SwitchItem("Pause listen history", "Songs you play won't be added to Recently Played.", s.pauseHistory) { v -> update { it.copy(pauseHistory = v) } }.withIcon(NavIcons.Recent, "pause_history"),
                    SwitchItem("Pause search history", null, s.pauseSearchHistory) { v -> update { it.copy(pauseSearchHistory = v) } }.withIcon(NavIcons.Search, "pause_search_history"),
                    ActionItem("Clear listen history", null, "Clear", destructive = true) { DesktopPrefs.clearHistory() }.withIcon(NavIcons.Delete),
                    ActionItem("Clear search history", null, "Clear", destructive = true) { DesktopPrefs.clearSearchHistory() }.withIcon(NavIcons.Delete),
                ),
            ),
        ),
    ),
    Category(
        "storage", "Storage", "Caches and disk use", NavIcons.Storage, { it.secondary },
        listOf(
            Group(
                "Caches",
                listOf(
                    ChoiceItem("Image cache size", "Applies after restarting Exhale.", listOf("128" to "128 MB", "256" to "256 MB", "512" to "512 MB", "1024" to "1 GB"), s.imageCacheMb.toString()) { v ->
                        update { it.copy(imageCacheMb = v.toInt()) }
                    }.withIcon(NavIcons.Image, "image_cache_mb"),
                    CustomItem("Disk usage", "Image cache, lyrics, sign-in browser", keywords = "clear cache delete") { StorageRows() },
                ),
            ),
        ),
    ),
    Category(
        "backup", "Backup & restore", "Take Exhale to another PC", NavIcons.Backup, { it.tertiary },
        listOf(Group("Backup", listOf(CustomItem("Export and import", "Settings, taste, history and sessions", keywords = "backup restore export import") { BackupRows(onHomeInputsChanged) }))),
    ),
    Category(
        "integrations", "Integrations", "Discord, Last.fm, ListenBrainz", NavIcons.Link, { DiscordBlurple },
        listOf(
            Group(
                "Discord",
                listOf(
                    CustomItem("Discord account", s.discordUsername, keywords = "discord rich presence rpc") { DiscordCard(s, onDiscordLogin) },
                    SwitchItem("Rich presence", "Show what you're listening to on your profile.", s.discordEnabled) { v -> update { it.copy(discordEnabled = v) } }.withIcon(NavIcons.Artists, "discord_enabled"),
                    SwitchItem("Show while paused", null, s.discordShowPaused) { v -> update { it.copy(discordShowPaused = v) } }.withIcon(TransportIcons.Pause, "discord_show_paused"),
                ),
            ),
            Group(
                "Last.fm",
                listOf(
                    CustomItem("Last.fm account", s.lastFmUsername, keywords = "lastfm scrobble") { LastFmCard(s) },
                    SwitchItem("Scrobbling", null, s.lastFmEnabled) { v -> update { it.copy(lastFmEnabled = v) } }.withIcon(TransportIcons.Equalizer, "lastfm_enabled"),
                    SliderItem("Scrobble after", "Of the song's length…", s.scrobblePercent.toFloat(), 20f..100f, 15, { "${it.roundToInt()}%" }) { v -> update { it.copy(scrobblePercent = v.roundToInt()) } }.withIcon(NavIcons.Timer, "scrobble_percent"),
                    SliderItem("…or after", null, s.scrobbleMaxDelay.toFloat(), 30f..600f, 18, { "${it.roundToInt()} s" }) { v -> update { it.copy(scrobbleMaxDelay = (v / 30).roundToInt() * 30) } }.withIcon(NavIcons.Timer, "scrobble_max_delay"),
                    SliderItem("Shortest song to count", null, s.scrobbleMinDuration.toFloat(), 10f..120f, 21, { "${it.roundToInt()} s" }) { v -> update { it.copy(scrobbleMinDuration = (v / 5).roundToInt() * 5) } }.withIcon(NavIcons.Timer, "scrobble_min_duration"),
                ),
            ),
            Group(
                "ListenBrainz",
                listOf(
                    SwitchItem("Submit listens", "Uses the same timing as Last.fm.", s.listenBrainzEnabled) { v -> update { it.copy(listenBrainzEnabled = v) } }.withIcon(NavIcons.Backup, "listenbrainz_enabled"),
                    CustomItem("User token", "listenbrainz.org/settings", keywords = "listenbrainz token") { ListenBrainzToken(s) },
                ),
            ),
        ),
    ),
    Category(
        "shortcuts", "Keyboard shortcuts", "Play, search and lyrics from the keyboard", NavIcons.Keyboard, { it.secondary },
        listOf(
            Group("Anywhere", listOf(CustomItem("Anywhere", keywords = "keys hotkeys media f11") { ShortcutTable(AnywhereShortcuts) })),
            Group("In the player", listOf(CustomItem("In the player", keywords = "keys hotkeys space lyrics sync") { ShortcutTable(PlayerShortcuts) })),
        ),
    ),
    Category(
        "updates", "Updates", "Version ${AppInfo.VERSION}", NavIcons.Update, { it.primary },
        listOf(
            Group(
                "Updates",
                listOf(
                    SwitchItem("Check for updates when Exhale opens", null, s.checkUpdates) { v -> update { it.copy(checkUpdates = v) } }.withIcon(NavIcons.Update, "check_updates"),
                    CustomItem("Latest release", keywords = "version github") { UpdatesCard() },
                ),
            ),
        ),
    ),
    Category(
        "about", "About", "Exhale for Windows", NavIcons.Info, { it.onSurfaceVariant },
        listOf(Group("Exhale", listOf(CustomItem("About Exhale", keywords = "version licence github") { }))),
    ),
)

private val AnywhereShortcuts = listOf(
    "Play or pause" to listOf("Space"),
    "Next / previous song" to listOf("N", "P"),
    "Seek back / forward" to listOf("←", "→"),
    "Volume up / down" to listOf("+", "−"),
    "Mute" to listOf("M"),
    "Shuffle" to listOf("S"),
    "Open lyrics" to listOf("L"),
    "Up next" to listOf("Q"),
    "Search" to listOf("/"),
    "Search from a field" to listOf("Ctrl", "F"),
    "Full screen window" to listOf("F11"),
    "Media keys" to listOf("⏯", "⏭", "⏮"),
)

private val PlayerShortcuts = listOf(
    "Play or pause" to listOf("Space"),
    "Seek back / forward" to listOf("←", "→"),
    "Lyrics" to listOf("L"),
    "Full-screen lyrics" to listOf("F"),
    "Up next" to listOf("Q"),
    "Lyrics earlier / later" to listOf("[", "]"),
    "Reset this song's lyric timing" to listOf("\\"),
    "Close" to listOf("Esc"),
)

/**
 * Settings the way the Android app lays it out on a big screen: on the left, the landing — a large
 * title, a real search field (Ctrl+F), the Exhale hero, Essentials as tiles, Integrations as brand
 * pills and every category in a list whose highlight slides to the one open; on the right, that
 * category as grouped glass cards. Changed settings carry a dot and a reset.
 */
@Composable
fun SettingsScreen(
    insets: ChromeInsets,
    onBack: (() -> Unit)?,
    onSignIn: () -> Unit,
    onEditTaste: () -> Unit,
    onHomeInputsChanged: () -> Unit,
    onDiscordLogin: () -> Unit,
    startPage: String? = null,
) {
    val settings = LocalSettings.current
    // Recomposes with the account too, so signing in or out reshapes the Account page.
    val account by AccountSession.account.collectAsState()
    val update by UpdateChecker.available.collectAsState()
    var selected by remember { mutableStateOf(startPage ?: "appearance") }
    var query by remember { mutableStateOf("") }
    val all = remember(settings, account) { categories(settings, onSignIn, onEditTaste, onHomeInputsChanged, onDiscordLogin) }
    val changed = remember(settings) { AppSettings.changedKeys(settings) }
    val scheme = MaterialTheme.colorScheme
    val searchFocus = remember { FocusRequester() }

    val resetKey: (String) -> Unit = { key ->
        AppSettings.reset(key)
        when (key) {
            "content_language", "content_country" -> {
                ContentLocale.apply()
                onHomeInputsChanged()
            }
            "proxy_enabled", "proxy_type" -> {
                NetworkProxy.apply()
                onHomeInputsChanged()
            }
            "hide_explicit", "hide_videos" -> onHomeInputsChanged()
        }
    }

    DisposableEffect(Unit) {
        AppShortcuts.overlay = { event ->
            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                runCatching { searchFocus.requestFocus() }
                true
            } else {
                false
            }
        }
        onDispose { AppShortcuts.overlay = null }
    }

    fun open(id: String) {
        selected = id
        query = ""
    }

    CompositionLocalProvider(LocalChangedKeys provides changed, LocalReset provides resetKey) {
        Row(
            Modifier.fillMaxSize().padding(start = insets.start + 20.dp, top = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val landingState = rememberLazyListState()
            Column(Modifier.width(256.dp).fillMaxHeight().padding(bottom = insets.bottom + 12.dp)) {
                Row(Modifier.padding(start = 2.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                }
                SearchPill(query, searchFocus) { query = it }
                LazyColumn(
                    state = landingState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "profile") { MacProfileRow(selected = selected == "about" && query.isBlank()) { open("about") } }
                    update?.let { release ->
                        item(key = "update") { MacUpdateRow(release.tag) { open("updates") } }
                    }
                    item(key = "all") {
                        CategoryList(all.filter { it.id != "about" }, if (query.isBlank() && selected != "about") selected else null, changed, scheme, ::open)
                    }
                    if (changed.isNotEmpty()) {
                        item(key = "changed") { ChangedNote(changed.size) }
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = if (query.isBlank()) selected else "search:${query.trim()}",
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 16 }) togetherWith fadeOut(tween(140))
                    },
                    label = "settings_page",
                ) { page ->
                    val scroll = rememberScrollState()
                    val scope = rememberCoroutineScope()
                    val intro = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        if (AppSettings.current.reducedMotion) intro.snapTo(1f) else intro.animateTo(1f, tween(480, easing = LinearOutSlowInEasing))
                    }
                    val progress: () -> Float = { intro.value }
                    // A category's groups are built one per frame rather than all in the first: a big
                    // category laid out every row and switch at once, a 400 ms freeze on first open.
                    var shownGroups by remember { mutableIntStateOf(2) }
                    LaunchedEffect(Unit) {
                        while (shownGroups < 64) {
                            withFrameNanos { }
                            shownGroups++
                        }
                    }
                    var viewportTop by remember { mutableFloatStateOf(0f) }
                    val groupTops = remember { mutableStateMapOf<Int, Float>() }
                    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { viewportTop = it.positionInRoot().y }) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                                .padding(bottom = insets.bottom + 24.dp, end = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (!page.startsWith("search:")) {
                                val category = all.firstOrNull { it.id == page } ?: all.first()
                                val accent = category.accent(scheme)
                                if (category.id == "about") {
                                    // About is its own page, as on Android — not a list of settings.
                                    AboutPage(onOpenUpdates = { open("updates") }, progress = progress)
                                } else if (category.id == "updates") {
                                    UpdatesPage(progress = progress)
                                } else {
                                    val changedHere = category.keys.filter { it in changed }
                                    CategoryHeader(
                                        category = category,
                                        changedCount = changedHere.size,
                                        onResetAll = { changedHere.forEach(resetKey) },
                                        modifier = Modifier.settingsColumn().feedIntro(0f, progress),
                                    )
                                    Column(
                                        Modifier.settingsColumn(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        category.groups.forEachIndexed { index, group ->
                                            if (index >= shownGroups) return@forEachIndexed
                                            GroupCard(
                                                group.title,
                                                group.items,
                                                accent,
                                                categoryHue(category.id) ?: accent,
                                                Modifier
                                                    .onGloballyPositioned { groupTops[index] = it.positionInRoot().y - viewportTop + scroll.value }
                                                    .feedIntro(index + 1f, progress),
                                            )
                                        }
                                    }
                                }
                            } else {
                                val q = page.removePrefix("search:").trim().lowercase()
                                val hits = all.flatMap { category ->
                                    category.groups.mapNotNull { group ->
                                        val matching = group.items.filter { it.matches(q) }
                                        val title = "${category.label} · ${group.title}"
                                        when {
                                            matching.isNotEmpty() -> Triple(title, matching, category)
                                            group.title.lowercase().contains(q) || category.label.lowercase().contains(q) -> Triple(title, group.items, category)
                                            else -> null
                                        }
                                    }
                                }
                                Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                                    Text("Results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "${hits.sumOf { it.second.size }} for “${page.removePrefix("search:")}”",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 5.dp),
                                    )
                                }
                                if (hits.isEmpty()) EmptySearch(page.removePrefix("search:"))
                                CompositionLocalProvider(LocalSearchQuery provides q) {
                                    Column(
                                        Modifier.settingsColumn(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        hits.forEachIndexed { index, (title, items, category) ->
                                            GroupCard(title, items, category.accent(scheme), categoryHue(category.id) ?: category.accent(scheme), Modifier.feedIntro(index.toFloat(), progress))
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scroll),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom = insets.bottom),
                        )
                    }
                }
            }
        }
    }
}

// ── The grouped-table material, ported from the Android SettingsSurface ──

/** A tonal floor, glass over it, a diagonal sheen and a rim bright at the top: a plate, not a smudge. */
@Composable
internal fun Modifier.settingsGroup(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // A box of settings, not a card: one flat translucent fill, a hairline around it, and the barest
    // lift along the top edge. The sheen a plate would carry is what makes a list look like a poster.
    return clip(shape)
        .background(surface.copy(alpha = 0.55f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.White.copy(alpha = 0.008f))))
        .border(0.8.dp, Color.White.copy(alpha = 0.075f), shape)
}

/** The rounded-square plate a glyph sits on: brighter top, dimmer bottom, a rim in the same hue. */
internal fun Modifier.puck(accent: Color, shape: Shape = RoundedCornerShape(12.dp)): Modifier = clip(shape)
    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.11f))))
    .border(0.7.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.06f))), shape)

@Composable
internal fun Chevron() {
    Icon(
        TransportIcons.Collapse,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -90f },
    )
}

/** A quiet, tracked label over a plate — it names the group, it doesn't compete with it. */
@Composable
internal fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        modifier = modifier.padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
internal fun RowDivider(indent: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = indent), thickness = 1.dp, color = Color.White.copy(alpha = 0.07f))

/** Lights up every place the settings search matched inside [text]. */
@Composable
private fun highlighted(text: String): AnnotatedString {
    val query = LocalSearchQuery.current
    if (query.isBlank()) return AnnotatedString(text)
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text)
        val lower = text.lowercase()
        var from = 0
        while (true) {
            val index = lower.indexOf(query, from)
            if (index < 0) break
            addStyle(SpanStyle(background = accent.copy(alpha = 0.32f), fontWeight = FontWeight.SemiBold), index, index + query.length)
            from = index + query.length
        }
    }
}

@Composable
private fun Keycap(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.7.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ── The landing ──

@Composable
private fun SearchPill(query: String, focus: FocusRequester, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val rim by animateColorAsState(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent, label = "search_rim")
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .settingsGroup(RoundedCornerShape(10.dp))
            .border(1.5.dp, rim, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged {
                        focused = it.isFocused
                        // Typing here is typing, not shortcuts.
                        AppShortcuts.setTyping(it.isFocused)
                    }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                            if (query.isNotEmpty()) onChange("") else focusManager.clearFocus()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                Text("✕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Who this is, at the top of the sidebar the way an account sits at the top of System Settings. */
@Composable
private fun MacProfileRow(selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = if (hovered) 0.08f else 0.045f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.09f)), contentAlignment = Alignment.Center) {
            Image(appIcon, contentDescription = null, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Exhale", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
            Text("About · Version ${AppInfo.VERSION}", fontSize = 11.5.sp, color = ink.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MacUpdateRow(tag: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Update, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Update available", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(tag, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium)
        }
    }
}

/** How much has been tuned away from the defaults — each changed row carries a dot and a reset. */
@Composable
private fun ChangedNote(count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(
            if (count == 1) "1 setting changed" else "$count settings changed",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A glyph on a solid rounded square, white on the hue — the icon System Settings gives every pane. */
@Composable
internal fun IconSquircle(icon: ImageVector, accent: Color, size: Dp = 28.dp) {
    val ink = if (accent.luminance() > 0.62f) Color.Black.copy(alpha = 0.78f) else Color.White
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(accent, Color.White, 0.2f), accent))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.58f))
    }
}

/** Every category, with one highlight that springs to the open one instead of each row lighting up. */
@Composable
private fun CategoryList(all: List<Category>, selected: String?, changed: Set<String>, scheme: ColorScheme, onOpen: (String) -> Unit) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    val target = selected?.let { bounds[it] }
    val y = remember { Animatable(0f) }
    val h = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (!placed) {
            y.snapTo(t.first)
            h.snapTo(t.second)
            placed = true
        } else {
            launch { y.animateTo(t.first, spring(dampingRatio = 0.78f, stiffness = 420f)) }
            h.animateTo(t.second, spring(dampingRatio = 0.78f, stiffness = 420f))
        }
    }
    val accent = all.firstOrNull { it.id == selected }?.accent?.invoke(scheme) ?: scheme.primary
    val tint by animateColorAsState(accent, label = "category_tint")
    val shown by animateFloatAsState(if (selected != null) 1f else 0f, tween(200), label = "category_indicator")

    Box(Modifier.fillMaxWidth()) {
        if (placed) {
            // The selection is a rounded plate that slides between rows, inset from the box's edges.
            Box(
                Modifier
                    .offset { IntOffset(0, y.value.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { h.value.toDp() })
                    .padding(vertical = 1.dp)
                    .graphicsLayer { alpha = shown }
                    .clip(RoundedCornerShape(9.dp))
                    .background(scheme.primary.copy(alpha = 0.22f)),
            )
        }
        Column {
            all.forEach { category ->
                CategoryRow(
                    category = category,
                    accent = category.accent(scheme),
                    selected = selected == category.id,
                    changedCount = category.keys.count { it in changed },
                    modifier = Modifier.onGloballyPositioned { bounds[category.id] = it.positionInParent().y to it.size.height.toFloat() },
                ) { onOpen(category.id) }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, accent: Color, selected: Boolean, changedCount: Int, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateColorAsState(
        when {
            pressed -> Color.White.copy(alpha = 0.09f)
            hovered && !selected -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        label = "category_wash",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(wash)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hue = categoryHue(category.id)
        if (hue != null) CategoryGlyph(category.id, hue, 28.dp) else Icon(category.icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(11.dp))
        // One line, the way a sidebar names a place. What the page is for belongs on the page.
        Text(
            category.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (changedCount > 0) {
            Text(
                "$changedCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

// ── A category page ──

@Composable
private fun CategoryHeader(category: Category, changedCount: Int, onResetAll: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            category.label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.weight(1f),
        )
        if (changedCount > 0) {
            TextButton(onClick = onResetAll) {
                Text(if (changedCount == 1) "Reset 1 change" else "Reset $changedCount changes")
            }
        }
    }
}

/** A settings pane is a column of lists, held to a width a line of text can be read across. */
private fun Modifier.settingsColumn(): Modifier = fillMaxWidth().widthIn(max = 720.dp)

/** A chip per group, so a long category is one click from any part of it. */
@OptIn(ExperimentalLayoutApi::class)



@Composable
private fun GroupCard(title: String, items: List<Item>, accent: Color, hue: Color = accent, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalRowAccent provides accent, LocalRowHue provides hue) {
        Column(modifier.fillMaxWidth()) {
            SectionLabel(title)
            Column(Modifier.fillMaxWidth().settingsGroup()) {
                items.forEachIndexed { index, item ->
                    if (index > 0) RowDivider(if (item.icon != null) 56.dp else 14.dp)
                    ItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun EmptySearch(query: String) {
    Column(
        Modifier.fillMaxWidth().settingsGroup().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Nothing matches “$query”", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Try a different word.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The reset control on a changed row: a dot at rest that becomes a button under the pointer. */
@Composable
private fun ResetMark(settingKey: String?, hovered: Boolean) {
    if (settingKey == null || settingKey !in LocalChangedKeys.current) return
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    val reset = LocalReset.current
    val open by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "reset_open")
    Box(Modifier.padding(start = 10.dp).size(28.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = 1f - open }.clip(CircleShape).background(accent))
        if (open > 0.01f) {
            Box(
                Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        alpha = open
                        scaleX = 0.6f + 0.4f * open
                        scaleY = 0.6f + 0.4f * open
                    }
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .clickable { reset(settingKey) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(NavIcons.Reset, contentDescription = "Reset to default", tint = accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RowShell(
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    settingKey: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        when {
            onClick == null -> if (hovered) 0.025f else 0f
            pressed -> 0.09f
            hovered -> 0.045f
            else -> 0f
        },
        spring(stiffness = Spring.StiffnessHigh),
        label = "row_wash",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LocalRowHue.current.takeIf { it.isSpecified } ?: accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(highlighted(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(highlighted(it), fontSize = 11.5.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        ResetMark(settingKey, hovered)
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}

@Composable
private fun ItemRow(item: Item) {
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    when (item) {
        is SwitchItem -> RowShell(item.title, item.subtitle, item.icon, item.key, onClick = { item.onChange(!item.checked) }) {
            // The Android app's liquid glass switch: a drop of glass for a thumb, dragged or tapped.
            com.ozyern.exhale.desktop.ui.glass.LiquidToggle(
                checked = item.checked,
                onCheckedChange = item.onChange,
                accent = accent,
                modifier = Modifier.graphicsLayer {
                    scaleX = 0.82f
                    scaleY = 0.82f
                },
            )
        }
        is ChoiceItem -> RowShell(item.title, item.subtitle, item.icon, item.key) { ChoiceControl(item, accent) }
        is SliderItem -> SliderRow(item, accent)
        is ActionItem -> RowShell(item.title, item.subtitle, item.icon) {
            com.ozyern.exhale.desktop.ui.glass.LiquidButton(onClick = item.onClick) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.destructive) MaterialTheme.colorScheme.error else Color.White,
                )
            }
        }
        is CustomItem -> item.content()
    }
}

@Composable
private fun ChoiceControl(item: ChoiceItem, accent: Color) {
    if (item.options.size <= 5 && item.options.sumOf { it.second.length } <= 42) {
        GlassSegmented(item.options, item.selected, accent, item.onSelect)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.09f))
                .border(0.7.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(start = 11.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.options.firstOrNull { it.first == item.selected }?.second ?: item.selected,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.width(6.dp))
            Icon(TransportIcons.Collapse, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            item.options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        item.onSelect(value)
                    },
                    trailingIcon = if (value == item.selected) {
                        { Icon(NavIcons.Check, contentDescription = null, tint = accent) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** A segmented control whose selection is one pill that springs between the options. */
@Composable
private fun GlassSegmented(options: List<Pair<String, String>>, selected: String, accent: Color, onSelect: (String) -> Unit) {
    // A pill that springs between the options, and becomes a drop of glass when held and dragged.
    com.ozyern.exhale.desktop.ui.glass.LiquidSegmented(
        labels = options.map { it.second },
        selected = options.indexOfFirst { it.first == selected },
        onSelect = { onSelect(options[it].first) },
        accent = accent,
    )
}

@Composable
private fun SliderRow(item: SliderItem, accent: Color) {
    // The slider moves freely while dragged and only writes the setting when released.
    var local by remember(item.value) { mutableFloatStateOf(item.value) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.fillMaxWidth().hoverable(interaction).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let { icon ->
                Icon(icon, contentDescription = null, tint = LocalRowHue.current.takeIf { it.isSpecified } ?: accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(highlighted(item.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                item.subtitle?.let { Text(highlighted(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ResetMark(item.key, hovered)
            Spacer(Modifier.width(8.dp))
            Text(
                item.format(local),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        com.ozyern.exhale.desktop.ui.glass.LiquidSlider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { item.onChange(local) },
            valueRange = item.range,
            steps = item.steps,
            accent = accent,
            modifier = Modifier.padding(start = if (item.icon != null) 38.dp else 0.dp, top = 6.dp),
        )
    }
}

@Composable
private fun TextSettingRow(title: String, placeholder: String, value: String, action: String, secret: Boolean = false, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(title) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = { onSave(text) }, enabled = text != value) { Text(action) }
    }
}

// ── Custom rows ──

/** A small, live mock of Exhale in the current accent, corners, card size, glass and glow. */
@Composable
private fun AppearancePreview(s: Settings) {
    val scheme = MaterialTheme.colorScheme
    val corner = (s.artworkCorner * 0.55f).dp
    val small = s.gridSize == "SMALL"
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (s.pureBlack) Color.Black else scheme.background)
                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
        ) {
            if (s.ambientGlow) {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        drawRect(Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.38f), Color.Transparent), center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.8f))
                    },
                )
            }
            Column(Modifier.padding(16.dp)) {
                Text("Good evening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tones = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
                    repeat(if (small) 6 else 4) { i ->
                        Box(
                            Modifier
                                .size(if (small) 40.dp else 54.dp)
                                .clip(RoundedCornerShape(corner))
                                .background(Brush.linearGradient(listOf(tones[i % 3], scheme.surfaceVariant))),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (s.liquidGlass) Color.White.copy(alpha = 0.12f) else Color(0xE6141014))
                    .border(0.7.dp, Color.White.copy(alpha = if (s.liquidGlass) 0.24f else 0.08f), CircleShape)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(52.dp).height(26.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.3f)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(44.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(scheme.primary))
            }
        }
        Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Live preview", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("Accent, corners, card size, glass and glow, as you change them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { Box(Modifier.size(18.dp).clip(CircleShape).background(it)) }
            }
        }
    }
}

/** A choice drawn as the layout it gives you. */
@Composable
private fun LayoutOption(label: String, selected: Boolean, accent: Color, onClick: () -> Unit, diagram: @Composable (Modifier) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.96f, hoverScale = 1.02f)
    val rim by animateColorAsState(if (selected) accent else Color.White.copy(alpha = if (hovered) 0.18f else 0.08f), label = "layout_rim")
    Column(
        Modifier
            .width(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .border(if (selected) 2.dp else 1.dp, rim, RoundedCornerShape(18.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        diagram(Modifier.fillMaxWidth().height(104.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Icon(NavIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DiagramCover(accent: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f)))))
        Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
        Box(Modifier.fillMaxWidth(0.5f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
    }
}

@Composable
private fun DiagramLyrics(modifier: Modifier, thick: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (thick) 7.dp else 6.dp, Alignment.CenterVertically)) {
        listOf(0.62f, 0.9f, 0.74f, 0.55f).forEachIndexed { i, width ->
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(if (i == 1) (if (thick) 9.dp else 7.dp) else (if (thick) 6.dp else 5.dp))
                    .clip(CircleShape)
                    .background(if (i == 1) Color.White else Color.White.copy(alpha = if (i == 0) 0.4f else 0.22f)),
            )
        }
    }
}

@Composable
private fun DiagramFrame(accent: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.55f))))
            .padding(10.dp),
    ) { content() }
}

@Composable
private fun LyricsSidePicker(s: Settings) {
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    OptionsRow(
        icon = NavIcons.Split,
        title = "Lyrics position",
        subtitle = "Which side of the player lyrics and Up Next open on.",
        settingKey = "lyrics_side",
    ) {
        listOf("RIGHT" to "Player left · lyrics right", "LEFT" to "Lyrics left · player right").forEach { (value, label) ->
            LayoutOption(label, s.lyricsSide == value, accent, onClick = { update { it.copy(lyricsSide = value) } }) { modifier ->
                DiagramFrame(accent, modifier) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (value == "LEFT") DiagramLyrics(Modifier.weight(1.2f).fillMaxHeight())
                        DiagramCover(accent, Modifier.weight(0.8f).fillMaxHeight())
                        if (value == "RIGHT") DiagramLyrics(Modifier.weight(1.2f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsViewPicker(s: Settings) {
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    OptionsRow(
        icon = NavIcons.Fullscreen,
        title = "Open lyrics as",
        subtitle = "From the dock's lyrics button. In the player, F switches either way.",
        settingKey = "lyrics_view",
    ) {
        LayoutOption("Beside the player", s.lyricsView == "BESIDE", accent, onClick = { update { it.copy(lyricsView = "BESIDE") } }) { modifier ->
            DiagramFrame(accent, modifier) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.lyricsSide == "LEFT") DiagramLyrics(Modifier.weight(1.2f).fillMaxHeight())
                    DiagramCover(accent, Modifier.weight(0.8f).fillMaxHeight())
                    if (s.lyricsSide != "LEFT") DiagramLyrics(Modifier.weight(1.2f).fillMaxHeight())
                }
            }
        }
        LayoutOption("Full screen", s.lyricsView == "FULL", accent, onClick = { update { it.copy(lyricsView = "FULL") } }) { modifier ->
            DiagramFrame(accent, modifier) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagramLyrics(Modifier.weight(1f).fillMaxWidth(), thick = true)
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsRow(icon: ImageVector, title: String, subtitle: String, settingKey: String, options: @Composable () -> Unit) {
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.fillMaxWidth().hoverable(interaction).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = LocalRowHue.current.takeIf { it.isSpecified } ?: accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(highlighted(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(highlighted(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ResetMark(settingKey, hovered)
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options()
        }
    }
}

@Composable
private fun ShortcutTable(rows: List<Pair<String, List<String>>>) {
    Column {
        rows.forEachIndexed { index, (action, keys) ->
            if (index > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(highlighted(action), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    keys.forEach { Keycap(it) }
                }
            }
        }
    }
}

/** Dolby Access, what Windows actually opened for Exhale, and whether the two line up. */
@Composable
private fun SpatialAudioCard(s: Settings) {
    val output by AudioOutputStatus.state.collectAsState()
    val installed = DolbyAccess.isInstalled
    val current = output
    val warning = when {
        s.spatialAudio != "OFF" && s.exclusiveAudio ->
            "Exclusive mode bypasses Windows audio processing, Dolby Atmos included. Turn one of them off."
        s.spatialAudio != "OFF" && current != null && !current.isSurround ->
            "Windows is only taking stereo from Exhale, so there's nothing to place. In Dolby Access, turn on Dolby Atmos for Headphones (or Windows Sonic) for this device, then play a song again."
        s.spatialAudio == "OFF" && installed ->
            "Dolby Atmos already processes Exhale's stereo. Spatial audio gives it a 5.1 or 7.1 upmix to place around you instead."
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NavIcons.Headphones, contentDescription = null, tint = DolbyViolet, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (installed) "Dolby Access" else "Dolby Access isn't installed", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    current?.let { "Output: ${it.describe()}" } ?: "Play a song to see what Windows opens for Exhale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (current?.isSurround == true) {
                Text(
                    "Spatial · ${current.layout}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DolbyViolet,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DolbyViolet.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        warning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 50.dp))
        }
        Row(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { DolbyAccess.open() }) { Text(if (installed) "Open Dolby Access" else "Get Dolby Access") }
            OutlinedButton(onClick = { DolbyAccess.openSoundSettings() }) { Text("Windows sound settings") }
        }
    }
}

@Composable
private fun AccountCard(onSignIn: () -> Unit, onHomeInputsChanged: () -> Unit) {
    val account by AccountSession.account.collectAsState()
    val current = account
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (current == null && !AccountSession.isSignedIn) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(NavIcons.Artists, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Sign in to YouTube Music", style = MaterialTheme.typography.titleMedium)
                Text("Your library, likes and a Home built from your account instead of your location.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = onSignIn) { Text("Sign in") }
        } else {
            AsyncImage(current?.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(current?.name ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                listOfNotNull(current?.handle, current?.email).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = {
                AccountSession.signOut()
                onHomeInputsChanged()
            }) { Text("Sign out") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PalettePicker(s: Settings) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.fillMaxWidth().hoverable(interaction).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val accent = MaterialTheme.colorScheme.primary
            Icon(NavIcons.Palette, contentDescription = null, tint = LocalRowHue.current.takeIf { it.isSpecified } ?: accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(highlighted("Accent colour"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            ResetMark("accent", hovered)
            Spacer(Modifier.width(8.dp))
            Text(if (s.dynamicTheme) "From artwork" else AccentPalettes.byId(s.accent).label, style = MaterialTheme.typography.labelLarge, color = accent)
        }
        Spacer(Modifier.height(14.dp))
        // macOS's accent picker is one row of dots. The first row shows here, with the chosen colour always
        // among them; the rest wait behind "Show All Colours" — and aren't built until asked for, which
        // was most of what the Appearance page cost to open.
        var showAll by remember { mutableStateOf(false) }
        val row = 14
        val chosen = AccentPalettes.all.indexOfFirst { it.id == s.accent }
        val shown = when {
            showAll -> AccentPalettes.all
            chosen >= row -> AccentPalettes.all.take(row - 1) + AccentPalettes.all[chosen]
            else -> AccentPalettes.all.take(row)
        }
        FlowRow(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { palette ->
                val selected = palette.id == s.accent && !s.dynamicTheme
                val swatchInteraction = remember { MutableInteractionSource() }
                val swatchHovered by swatchInteraction.collectIsHoveredAsState()
                val scale by animateFloatAsState(if (selected) 1.12f else if (swatchHovered) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "swatch")
                val fill = if (palette.neutral != palette.primary) {
                    Brush.sweepGradient(listOf(palette.primary, palette.secondary, palette.tertiary, palette.neutral, palette.primary))
                } else {
                    SolidColor(palette.primary)
                }
                Box(
                    Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(fill)
                        .border(if (selected) 2.dp else 0.75.dp, if (selected) Color.White else Color.White.copy(alpha = 0.15f), CircleShape)
                        .hoverable(swatchInteraction)
                        .clickable(interactionSource = swatchInteraction, indication = null) { update { it.copy(accent = palette.id, dynamicTheme = false) } },
                    contentAlignment = Alignment.Center,
                ) {
                    // macOS marks the chosen accent with a small white dot in it.
                    if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(if (palette.primary.luminance() > 0.6f) Color.Black else Color.White))
                }
            }
        }
        Text(
            if (showAll) "Show Fewer Colours" else "Show All Colours",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 46.dp, top = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { showAll = !showAll }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerEditor(s: Settings) {
    val accent = LocalRowAccent.current.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EqualizerPresets.presets.forEach { (name, gains) ->
                val selected = s.equalizerPreset == name
                val background by animateColorAsState(if (selected) accent else Color.White.copy(alpha = 0.08f), label = "preset_bg")
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(background)
                        .clickable { update { it.copy(equalizerPreset = name, equalizerBands = gains, equalizerEnabled = true) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) (if (accent.luminance() > 0.5f) Color.Black else Color.White) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        EqualizerPresets.frequencies.forEachIndexed { index, frequency ->
            val stored = s.equalizerBands.getOrElse(index) { 0 }
            var local by remember(stored) { mutableFloatStateOf(stored.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (frequency >= 1000) "${frequency / 1000} kHz" else "$frequency Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                com.ozyern.exhale.desktop.ui.glass.LiquidSlider(
                    value = local,
                    onValueChange = { local = it },
                    onValueChangeFinished = {
                        update { current ->
                            current.copy(
                                equalizerBands = current.equalizerBands.toMutableList().also { it[index] = local.roundToInt() },
                                equalizerPreset = "Custom",
                                equalizerEnabled = true,
                            )
                        }
                    },
                    valueRange = -12f..12f,
                    steps = 23,
                    accent = if (s.equalizerEnabled) accent else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.weight(1f),
                )
                Text(String.format("%+d dB", local.roundToInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ProviderOrderEditor(s: Settings) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        s.lyricsProviderOrder.forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                fun move(to: Int) {
                    update { current ->
                        val order = current.lyricsProviderOrder.toMutableList()
                        order.add(to, order.removeAt(index))
                        current.copy(lyricsProviderOrder = order)
                    }
                    LyricsRepository.clearCache()
                }
                TextButton(onClick = { move(index - 1) }, enabled = index > 0) {
                    Icon(TransportIcons.Collapse, contentDescription = "Move up", modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f })
                }
                TextButton(onClick = { move(index + 1) }, enabled = index < s.lyricsProviderOrder.lastIndex) {
                    Icon(TransportIcons.Collapse, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
                com.ozyern.exhale.desktop.ui.glass.LiquidToggle(
                    modifier = Modifier.graphicsLayer { scaleX = 0.82f; scaleY = 0.82f },
                    checked = name !in s.lyricsDisabled,
                    onCheckedChange = { on ->
                        update { it.copy(lyricsDisabled = if (on) it.lyricsDisabled - name else it.lyricsDisabled + name) }
                        LyricsRepository.clearCache()
                    },
                )
            }
        }
    }
}

@Composable
private fun StorageRows() {
    var refresh by remember { mutableIntStateOf(0) }
    val imageSize by produceState(0L, refresh) { value = withContext(Dispatchers.IO) { AppStorage.sizeOf(AppStorage.imageCacheDir) } }
    val browserSize by produceState(0L, refresh) { value = withContext(Dispatchers.IO) { AppStorage.sizeOf(AppStorage.browserDir) } }
    Column {
        RowShell("Image cache", AppStorage.format(imageSize), NavIcons.Image) {
            OutlinedButton(onClick = {
                AppStorage.clearImageCache()
                refresh++
            }) { Text("Clear") }
        }
        RowDivider(66.dp)
        RowShell("Lyrics cache", "Recent lyrics kept in memory.", TransportIcons.Lyrics) {
            OutlinedButton(onClick = { LyricsRepository.clearCache() }) { Text("Clear") }
        }
        RowDivider(66.dp)
        RowShell(
            "Sign-in browser",
            "${AppStorage.format(browserSize)} · Chromium for YouTube Music and Discord sign-in. " +
                if (SignInBrowser.isRunning) "Restart Exhale to remove it." else "Downloads again next time it's needed.",
            NavIcons.Storage,
        ) {
            OutlinedButton(
                onClick = { if (AppStorage.deleteBrowserData()) refresh++ },
                enabled = !SignInBrowser.isRunning && browserSize > 0,
            ) { Text("Remove") }
        }
    }
}

@Composable
private fun BackupRows(onHomeInputsChanged: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    Column {
        RowShell("Export", "Settings, Your Music Taste, history and signed-in sessions, in one file. Keep it private.", NavIcons.Backup) {
            FilledTonalButton(onClick = {
                val target = SystemShell.chooseFile("Export Exhale backup", save = true, suggestedName = "exhale-backup.properties") ?: return@FilledTonalButton
                status = runCatching { DesktopPrefs.exportTo(target) }.fold({ "Saved to ${target.name}." }, { "Couldn't export: ${it.message}" })
            }) { Text("Export") }
        }
        RowDivider(66.dp)
        RowShell("Import", "Replaces everything on this PC with the backup.", NavIcons.Update) {
            FilledTonalButton(onClick = {
                val source = SystemShell.chooseFile("Import Exhale backup", save = false) ?: return@FilledTonalButton
                status = runCatching {
                    DesktopPrefs.importFrom(source)
                    ContentLocale.apply()
                    NetworkProxy.apply()
                    AccountSession.applySaved()
                    onHomeInputsChanged()
                }.fold({ "Restored from ${source.name}. Restart Exhale to apply everything." }, { "Couldn't import: ${it.message}" })
            }) { Text("Import") }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 66.dp, end = 16.dp, bottom = 14.dp))
        }
    }
}

@Composable
private fun DiscordCard(s: Settings, onDiscordLogin: () -> Unit) {
    if (s.discordToken == null) {
        RowShell("Discord", "Log in to show what you're listening to on your profile.", NavIcons.Link) {
            Button(onClick = onDiscordLogin) { Text("Log in with Discord") }
        }
    } else {
        RowShell("Logged in as ${s.discordUsername ?: "Discord user"}", "Exhale keeps your Discord token on this PC, like the Android app. Log out to remove it.", NavIcons.Link) {
            OutlinedButton(onClick = { update { it.copy(discordEnabled = false, discordToken = null, discordUsername = null) } }) { Text("Log out") }
        }
    }
}

@Composable
private fun LastFmCard(s: Settings) {
    if (!LastFmAuth.isConfigured) {
        RowShell("Last.fm isn't set up in this build", "Add LASTFM_API_KEY and LASTFM_SECRET to local.properties and rebuild.", TransportIcons.Equalizer)
        return
    }
    if (s.lastFmSession != null) {
        RowShell("Signed in as ${s.lastFmUsername ?: "Last.fm user"}", null, TransportIcons.Equalizer) {
            OutlinedButton(onClick = LastFmAuth::signOut) { Text("Sign out") }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sign in to scrobble", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        LastFmAuth.signIn(username, password).onFailure { error = it.message ?: "Sign-in failed" }
                        busy = false
                    }
                },
                enabled = username.isNotBlank() && password.isNotBlank() && !busy,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ListenBrainzToken(s: Settings) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    Column {
        TextSettingRow("User token", "Paste your token", s.listenBrainzToken, "Save", secret = true) { token ->
            scope.launch {
                val valid = token.isBlank() || withContext(Dispatchers.IO) { ListenBrainz.validate(token) }
                if (valid) {
                    update { it.copy(listenBrainzToken = token.trim(), listenBrainzEnabled = token.isNotBlank()) }
                    status = if (token.isBlank()) "Token removed." else "Token saved."
                } else {
                    status = "ListenBrainz didn't accept that token."
                }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) }
    }
}

@Composable
private fun UpdatesCard() {
    val scope = rememberCoroutineScope()
    val available by UpdateChecker.available.collectAsState()
    var release by remember { mutableStateOf<ReleaseInfo?>(available) }
    var status by remember { mutableStateOf<String?>(available?.let { "${it.tag} is available." }) }
    var checking by remember { mutableStateOf(false) }
    RowShell("Exhale for Windows ${AppInfo.VERSION}", status ?: "Checks GitHub for a newer release.", NavIcons.Info) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            release?.let { r -> OutlinedButton(onClick = { SystemShell.open(r.url) }) { Text("View release") } }
            Button(
                onClick = {
                    checking = true
                    scope.launch {
                        UpdateChecker.latest()
                            .onSuccess {
                                release = it
                                status = if (UpdateChecker.isNewer(it.tag)) "${it.tag} is available." else "You're up to date — the latest release is ${it.tag}."
                            }
                            .onFailure { status = "Couldn't check: ${it.message}" }
                        checking = false
                    }
                },
                enabled = !checking,
            ) {
                if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Check now")
            }
        }
    }
}
