/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AppleMusicLyricsBlurKey
import com.ozyern.exhale.constants.LyricsGlowEffectKey
import com.ozyern.exhale.constants.FlamingoBrightSingingWordsKey
import com.ozyern.exhale.constants.FlamingoWordSwellKey
import com.ozyern.exhale.constants.FlamingoGlowColorKey
import com.ozyern.exhale.constants.LyricsStandardBlurKey
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.ios.compat.Switch
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.constants.LyricsAnimationStyle
import com.ozyern.exhale.constants.LyricsAnimationStyleKey
import com.ozyern.exhale.ui.component.EnumDialog
import com.ozyern.exhale.ui.component.ColorPickerDialog
import com.ozyern.exhale.ui.component.lyricsStyleLabel
import androidx.compose.runtime.saveable.rememberSaveable

/** Lyrics look: glow and the two blur modes. Kept apart from the animation style and text layout. */
@Composable
fun LyricsSettingsScreen(navController: NavController) {
    val (animationStyle, selectAnimationStyle) = rememberEnumPreference(LyricsAnimationStyleKey, LyricsAnimationStyle.FLAMINGO)
    var showStyleDialog by rememberSaveable { mutableStateOf(false) }
    if (showStyleDialog) {
        EnumDialog(
            onDismiss = { showStyleDialog = false },
            onSelect = { selectAnimationStyle(it); showStyleDialog = false },
            title = stringResource(R.string.lyrics_animation_style),
            current = animationStyle,
            values = LyricsAnimationStyle.entries,
            valueText = { lyricsStyleLabel(it) },
        )
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val credentials = remember(context) { context.getSharedPreferences("lyrics_credentials", android.content.Context.MODE_PRIVATE) }
    var paxKey by remember { mutableStateOf(credentials.getString("paxsenix_api_key", "").orEmpty()) }
    val (glow, onGlowChange) = rememberPreference(LyricsGlowEffectKey, defaultValue = true)
    val (brightSingingWords, onBrightSingingWordsChange) = rememberPreference(FlamingoBrightSingingWordsKey, defaultValue = true)
    val (wordSwell, onWordSwellChange) = rememberPreference(FlamingoWordSwellKey, defaultValue = true)
    val (glowColorInt, onGlowColorChange) = rememberPreference(FlamingoGlowColorKey, defaultValue = Color.White.toArgb())
    val glowColor = Color(glowColorInt)
    var showGlowColorPicker by rememberSaveable { mutableStateOf(false) }
    val (appleBlur, onAppleBlurChange) = rememberPreference(AppleMusicLyricsBlurKey, defaultValue = true)
    val (standardBlur, onStandardBlurChange) = rememberPreference(LyricsStandardBlurKey, defaultValue = false)

    if (showGlowColorPicker) {
        ColorPickerDialog(
            initialColor = glowColor,
            title = stringResource(R.string.flamingo_glow_color),
            onDismiss = { showGlowColorPicker = false },
            onConfirm = {
                onGlowColorChange(it.toArgb())
                showGlowColorPicker = false
            },
            defaultColor = Color.White,
            onReset = {
                onGlowColorChange(Color.White.toArgb())
                showGlowColorPicker = false
            },
        )
    }

    SettingsPage(navController = navController, title = stringResource(R.string.lyrics), horizontalPadding = 16.dp) {
        IosSettingsGroup(title = stringResource(R.string.lyrics_animation_style), items = listOf(
            IosSettingsItem(
                icon = painterResource(R.drawable.lyrics),
                title = { Text(lyricsStyleLabel(animationStyle)) },
                description = { Text(stringResource(R.string.lyrics_centered_scroll_description)) },
                onClick = { showStyleDialog = true },
            ),
        ))
        LyricsFontSettings()
        if (animationStyle == LyricsAnimationStyle.FLAMINGO) {
            IosSettingsGroup(title = stringResource(R.string.lyrics_style_flamingo), items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.flamingo_bright_singing_words)) },
                    description = { Text(stringResource(R.string.flamingo_bright_singing_words_desc)) },
                    trailingContent = { Switch(checked = brightSingingWords, onCheckedChange = onBrightSingingWordsChange) },
                    onClick = { onBrightSingingWordsChange(!brightSingingWords) },
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.flamingo_word_swell)) },
                    description = { Text(stringResource(R.string.flamingo_word_swell_desc)) },
                    trailingContent = { Switch(checked = wordSwell, onCheckedChange = onWordSwellChange) },
                    onClick = { onWordSwellChange(!wordSwell) },
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.flamingo_glow_color)) },
                    description = { Text(stringResource(R.string.flamingo_glow_color_desc)) },
                    trailingContent = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(glowColor)
                                .border(1.dp, Color.Gray, CircleShape),
                        )
                    },
                    onClick = { showGlowColorPicker = true },
                ),
            ))
        }
        IosSettingsGroup(title = "Lyrics sources", items = listOf(
            IosSettingsItem(
                icon = painterResource(R.drawable.lyrics),
                title = { Text("Provider order and availability") },
                description = { Text("Drag to reorder sources and enable or disable each provider") },
                onClick = { navController.navigate("settings/content/advanced") },
            ),
        ))
        com.ozyern.exhale.ui.ios.compat.OutlinedTextField(
            value = paxKey,
            onValueChange = {
                paxKey = it
                credentials.edit().putString("paxsenix_api_key", it.trim()).apply()
            },
            label = { Text("PaxSenix API key") },
            supportingText = { Text("Used by PaxSenix Spotify and Musixmatch sources") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        IosSettingsGroup(
            title = "Lyrics effects",
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_glow_effect)) },
                    description = { Text(stringResource(R.string.lyrics_glow_effect_desc)) },
                    trailingContent = { Switch(checked = glow, onCheckedChange = onGlowChange) },
                    onClick = { onGlowChange(!glow) },
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.apple_music_lyrics_blur)) },
                    description = { Text(stringResource(R.string.apple_music_lyrics_blur_desc)) },
                    trailingContent = { Switch(checked = appleBlur, onCheckedChange = onAppleBlurChange) },
                    onClick = { onAppleBlurChange(!appleBlur) },
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.standard_lyrics_blur)) },
                    description = { Text(stringResource(R.string.apple_music_lyrics_blur_desc)) },
                    trailingContent = { Switch(checked = standardBlur, onCheckedChange = onStandardBlurChange) },
                    onClick = { onStandardBlurChange(!standardBlur) },
                ),
            ),
        )
        Spacer(Modifier.height(32.dp))
    }
}
