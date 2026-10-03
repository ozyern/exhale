/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.DiscordActivityDetailsKey
import com.ozyern.exhale.constants.DiscordActivityNameKey
import com.ozyern.exhale.constants.DiscordActivityPlatformKey
import com.ozyern.exhale.constants.DiscordActivityStateKey
import com.ozyern.exhale.constants.DiscordActivityTypeKey
import com.ozyern.exhale.constants.DiscordAdvancedKey
import com.ozyern.exhale.constants.DiscordLargeImageCustomUrlKey
import com.ozyern.exhale.constants.DiscordLargeImageTypeKey
import com.ozyern.exhale.constants.DiscordLargeTextCustomKey
import com.ozyern.exhale.constants.DiscordLargeTextSourceKey
import com.ozyern.exhale.constants.DiscordPresenceStatusKey
import com.ozyern.exhale.constants.DiscordShowWhenPausedKey
import com.ozyern.exhale.constants.DiscordSmallImageCustomUrlKey
import com.ozyern.exhale.constants.DiscordSmallImageTypeKey
import com.ozyern.exhale.ui.component.EditTextPreference
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.ListPreference
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.utils.rememberPreference

/**
 * Discord, Advanced: every part of the presence, back from the older builds.
 *
 * Off by default, and the main Discord page stays the default — Exhale, the song, the artist, the
 * cover. These choices only reach the presence while Advanced is switched on, so turning it off is
 * always a way back to the presence everyone expects, whatever was left set here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordAdvanced(navController: NavController) {
    val (advanced, setAdvanced) = rememberPreference(DiscordAdvancedKey, false)
    val (name, setName) = rememberPreference(DiscordActivityNameKey, "APP")
    val (details, setDetails) = rememberPreference(DiscordActivityDetailsKey, "SONG")
    val (state, setState) = rememberPreference(DiscordActivityStateKey, "ARTIST")
    val (type, setType) = rememberPreference(DiscordActivityTypeKey, "LISTENING")
    val (status, setStatus) = rememberPreference(DiscordPresenceStatusKey, "online")
    val (platform, setPlatform) = rememberPreference(DiscordActivityPlatformKey, "desktop")
    val (whenPaused, setWhenPaused) = rememberPreference(DiscordShowWhenPausedKey, false)
    val (largeImage, setLargeImage) = rememberPreference(DiscordLargeImageTypeKey, "thumbnail")
    val (largeUrl, setLargeUrl) = rememberPreference(DiscordLargeImageCustomUrlKey, "")
    val (smallImage, setSmallImage) = rememberPreference(DiscordSmallImageTypeKey, "artist")
    val (smallUrl, setSmallUrl) = rememberPreference(DiscordSmallImageCustomUrlKey, "")
    val (largeText, setLargeText) = rememberPreference(DiscordLargeTextSourceKey, "album")
    val (largeTextCustom, setLargeTextCustom) = rememberPreference(DiscordLargeTextCustomKey, "")

    val sources = listOf("APP", "SONG", "ARTIST", "ALBUM")
    val sourceLabel: (String) -> String = {
        when (it) {
            "APP" -> "Exhale"
            "SONG" -> "Song title"
            "ARTIST" -> "Artist"
            "ALBUM" -> "Album"
            else -> it
        }
    }
    val imageLabel: (String) -> String = {
        when (it) {
            "thumbnail" -> "Cover art"
            "artist" -> "Artist picture"
            "appicon" -> "Exhale icon"
            "custom" -> "Custom image"
            "none" -> "None"
            else -> it
        }
    }

    Scaffold { inner ->
        Column(Modifier.fillMaxSize()) {
            SettingsTopAppBar(
                title = { Text("Advanced") },
                navigationIcon = {
                    LiquidBackButton(onClick = navController::navigateUp, icon = R.drawable.chevron_back)
                },
            )
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = inner.calculateBottomPadding() + 96.dp),
            ) {
                PreferenceGroup {
                    SwitchPreference(
                        title = { Text("Advanced presence") },
                        description = "Use the choices below instead of the standard presence. Off puts it back to Exhale, the song and the artist",
                        icon = { Icon(painterResource(R.drawable.experiment), null) },
                        checked = advanced,
                        onCheckedChange = setAdvanced,
                    )
                }

                AnimatedVisibility(visible = advanced) {
                    Column {
                        PreferenceGroup(title = "What it says") {
                            ListPreference(
                                title = { Text("Activity name") },
                                icon = { Icon(painterResource(R.drawable.discord), null) },
                                selectedValue = name,
                                values = sources,
                                valueText = { sourceLabel(it) },
                                onValueSelected = setName,
                            )
                            ListPreference(
                                title = { Text("First line") },
                                icon = { Icon(painterResource(R.drawable.music_note), null) },
                                selectedValue = details,
                                values = sources,
                                valueText = { sourceLabel(it) },
                                onValueSelected = setDetails,
                            )
                            ListPreference(
                                title = { Text("Second line") },
                                icon = { Icon(painterResource(R.drawable.artist), null) },
                                selectedValue = state,
                                values = sources,
                                valueText = { sourceLabel(it) },
                                onValueSelected = setState,
                            )
                            ListPreference(
                                title = { Text("Activity type") },
                                icon = { Icon(painterResource(R.drawable.headphones), null) },
                                selectedValue = type,
                                values = listOf("LISTENING", "PLAYING", "WATCHING", "COMPETING", "STREAMING"),
                                valueText = { it.lowercase().replaceFirstChar(Char::uppercase) },
                                onValueSelected = setType,
                            )
                        }

                        PreferenceGroup(title = "Pictures") {
                            ListPreference(
                                title = { Text("Large image") },
                                icon = { Icon(painterResource(R.drawable.image), null) },
                                selectedValue = largeImage,
                                values = listOf("thumbnail", "artist", "appicon", "custom"),
                                valueText = { imageLabel(it) },
                                onValueSelected = setLargeImage,
                            )
                            if (largeImage == "custom") {
                                EditTextPreference(
                                    title = { Text("Large image URL") },
                                    icon = { Icon(painterResource(R.drawable.link), null) },
                                    value = largeUrl,
                                    onValueChange = setLargeUrl,
                                )
                            }
                            ListPreference(
                                title = { Text("Small image") },
                                icon = { Icon(painterResource(R.drawable.image), null) },
                                selectedValue = smallImage,
                                values = listOf("artist", "thumbnail", "appicon", "custom", "none"),
                                valueText = { imageLabel(it) },
                                onValueSelected = setSmallImage,
                            )
                            if (smallImage == "custom") {
                                EditTextPreference(
                                    title = { Text("Small image URL") },
                                    icon = { Icon(painterResource(R.drawable.link), null) },
                                    value = smallUrl,
                                    onValueChange = setSmallUrl,
                                )
                            }
                            ListPreference(
                                title = { Text("Text on the large image") },
                                icon = { Icon(painterResource(R.drawable.info), null) },
                                selectedValue = largeText,
                                values = listOf("album", "song", "artist", "app", "custom", "dontshow"),
                                valueText = {
                                    when (it) {
                                        "app" -> "Exhale"
                                        "dontshow" -> "None"
                                        else -> it.replaceFirstChar(Char::uppercase)
                                    }
                                },
                                onValueSelected = setLargeText,
                            )
                            if (largeText == "custom") {
                                EditTextPreference(
                                    title = { Text("Custom text") },
                                    icon = { Icon(painterResource(R.drawable.edit), null) },
                                    value = largeTextCustom,
                                    onValueChange = setLargeTextCustom,
                                )
                            }
                        }
                    }
                }

                // These two always applied, Advanced or not; they live here with the rest.
                PreferenceGroup(title = "Presence") {
                    ListPreference(
                        title = { Text("Status") },
                        icon = { Icon(painterResource(R.drawable.discord), null) },
                        selectedValue = status,
                        values = listOf("online", "idle", "dnd", "invisible"),
                        valueText = {
                            when (it) {
                                "online" -> "Online"
                                "idle" -> "Idle"
                                "dnd" -> "Do not disturb"
                                else -> "Invisible"
                            }
                        },
                        onValueSelected = setStatus,
                    )
                    ListPreference(
                        title = { Text("Shown as playing on") },
                        icon = { Icon(painterResource(R.drawable.headphones), null) },
                        selectedValue = platform,
                        values = listOf("desktop", "android", "ios", "xbox", "ps5", "web", "embedded"),
                        valueText = {
                            when (it) {
                                "ios" -> "iOS"
                                "ps5" -> "PS5"
                                else -> it.replaceFirstChar(Char::uppercase)
                            }
                        },
                        onValueSelected = setPlatform,
                    )
                    SwitchPreference(
                        title = { Text("Keep showing while paused") },
                        icon = { Icon(painterResource(R.drawable.pause), null) },
                        checked = whenPaused,
                        onCheckedChange = setWhenPaused,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Discord shows a change within about fifteen seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}
