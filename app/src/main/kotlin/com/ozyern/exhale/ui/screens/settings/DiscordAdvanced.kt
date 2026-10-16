/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerConnection
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
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.utils.rememberPreference

/**
 * Discord, Advanced: every part of the presence, with the presence itself at the top.
 *
 * The page used to be a column of rows that each opened a dialog, so every change meant a round
 * trip and you never saw what you had made. Now the live preview leads the page and each choice
 * is a row of pills under its name, so a tap changes the card above straight away - and, since the
 * presence manager now watches these settings, Discord a few seconds later.
 *
 * Off by default, and turning it off is always the way back to Exhale, the song and the artist.
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

    val sources = listOf("APP" to "Exhale", "SONG" to "Song", "ARTIST" to "Artist", "ALBUM" to "Album")
    val images = listOf(
        "thumbnail" to "Cover",
        "artist" to "Artist",
        "appicon" to "Exhale icon",
        "custom" to "Custom",
    )

    val playerConnection = LocalPlayerConnection.current
    val song by (playerConnection?.currentSong ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState(null)
    fun source(v: String, fallback: ActivitySource) = runCatching { ActivitySource.valueOf(v) }.getOrDefault(fallback)

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
                // What Discord will show, drawn from exactly the values below.
                RichPresence(
                    song = song,
                    currentPlaybackTimeMillis = playerConnection?.player?.currentPosition ?: 0L,
                    nameSource = if (advanced) source(name, ActivitySource.APP) else ActivitySource.APP,
                    detailsSource = if (advanced) source(details, ActivitySource.SONG) else ActivitySource.SONG,
                    stateSource = if (advanced) source(state, ActivitySource.ARTIST) else ActivitySource.ARTIST,
                    activityType = if (advanced) type else "LISTENING",
                    largeImageType = if (advanced) largeImage else "thumbnail",
                    largeImageCustomUrl = if (advanced) largeUrl else "",
                    smallImageType = if (advanced) smallImage else "artist",
                    smallImageCustomUrl = if (advanced) smallUrl else "",
                    isPlaying = playerConnection?.player?.isPlaying == true,
                )

                PreferenceGroup {
                    SwitchPreference(
                        title = { Text("Advanced presence") },
                        description = if (advanced) "Your own presence, as built below" else "Off: Exhale, the song and the artist",
                        icon = { Icon(painterResource(R.drawable.experiment), null) },
                        checked = advanced,
                        onCheckedChange = setAdvanced,
                    )
                }

                AnimatedVisibility(
                    visible = advanced,
                    enter = fadeIn(tween(220)) + expandVertically(),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column {
                        PreferenceGroup(title = "What it says") {
                            PillChoice("Activity", type, listOf(
                                "LISTENING" to "Listening",
                                "PLAYING" to "Playing",
                                "WATCHING" to "Watching",
                                "COMPETING" to "Competing",
                                "STREAMING" to "Streaming",
                            ), setType)
                            PreferenceGroupDivider()
                            PillChoice("Name", name, sources, setName)
                            PreferenceGroupDivider()
                            PillChoice("First line", details, sources, setDetails)
                            PreferenceGroupDivider()
                            PillChoice("Second line", state, sources, setState)
                        }

                        PreferenceGroup(title = "Pictures") {
                            PillChoice("Large picture", largeImage, images, setLargeImage)
                            if (largeImage == "custom") {
                                EditTextPreference(
                                    title = { Text("Large picture URL") },
                                    icon = { Icon(painterResource(R.drawable.link), null) },
                                    value = largeUrl,
                                    onValueChange = setLargeUrl,
                                )
                            }
                            PreferenceGroupDivider()
                            PillChoice("Small picture", smallImage, images + ("none" to "None"), setSmallImage)
                            if (smallImage == "custom") {
                                EditTextPreference(
                                    title = { Text("Small picture URL") },
                                    icon = { Icon(painterResource(R.drawable.link), null) },
                                    value = smallUrl,
                                    onValueChange = setSmallUrl,
                                )
                            }
                            PreferenceGroupDivider()
                            PillChoice("Text on the picture", largeText, listOf(
                                "album" to "Album",
                                "song" to "Song",
                                "artist" to "Artist",
                                "app" to "Exhale",
                                "custom" to "Custom",
                                "dontshow" to "None",
                            ), setLargeText)
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

                // These apply whether Advanced is on or not.
                PreferenceGroup(title = "Presence") {
                    PillChoice(
                        "Status",
                        status,
                        listOf("online" to "Online", "idle" to "Idle", "dnd" to "Do not disturb", "invisible" to "Invisible"),
                        setStatus,
                        dot = { v ->
                            when (v) {
                                "online" -> Color(0xFF23A55A)
                                "idle" -> Color(0xFFF0B232)
                                "dnd" -> Color(0xFFF23F43)
                                else -> Color(0xFF80848E)
                            }
                        },
                    )
                    PreferenceGroupDivider()
                    PillChoice("Shown as playing on", platform, listOf(
                        "desktop" to "Desktop",
                        "android" to "Android",
                        "ios" to "iOS",
                        "web" to "Web",
                        "xbox" to "Xbox",
                        "ps5" to "PS5",
                        "embedded" to "Embedded",
                    ), setPlatform)
                    PreferenceGroupDivider()
                    SwitchPreference(
                        title = { Text("Keep showing while paused") },
                        icon = { Icon(painterResource(R.drawable.pause), null) },
                        checked = whenPaused,
                        onCheckedChange = setWhenPaused,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Changes reach Discord within a few seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}

/** A setting's name over a row of pills, one per value; the chosen one filled. */
@Composable
private fun PillChoice(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    dot: ((String) -> Color)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            options.forEach { (value, text) ->
                val on = value.equals(selected, ignoreCase = true)
                val fill by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    tween(160),
                    label = "pillFill",
                )
                val ink by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    tween(160),
                    label = "pillInk",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .clickable {
                            if (!on) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(value)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    dot?.let {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(it(value)))
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(text, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = ink)
                }
            }
        }
    }
}
