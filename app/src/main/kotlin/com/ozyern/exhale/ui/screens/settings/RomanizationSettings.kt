/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Switch
import androidx.compose.material3.SwitchDefaults
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.LyricsRomanizeAsMainKey
import com.ozyern.exhale.constants.LyricsRomanizeBelarusianKey
import com.ozyern.exhale.constants.LyricsRomanizeBulgarianKey
import com.ozyern.exhale.constants.LyricsRomanizeChineseKey
import com.ozyern.exhale.constants.LyricsRomanizeHindiKey
import com.ozyern.exhale.constants.LyricsRomanizePunjabiKey
import com.ozyern.exhale.constants.LyricsRomanizeCyrillicByLineKey
import com.ozyern.exhale.constants.LyricsRomanizeJapaneseKey
import com.ozyern.exhale.constants.LyricsRomanizeKoreanKey
import com.ozyern.exhale.constants.LyricsRomanizeKyrgyzKey
import com.ozyern.exhale.constants.LyricsRomanizeMacedonianKey
import com.ozyern.exhale.constants.LyricsRomanizeRussianKey
import com.ozyern.exhale.constants.LyricsRomanizeSerbianKey
import com.ozyern.exhale.constants.LyricsRomanizeUkrainianKey
import com.ozyern.exhale.ui.component.ActionPromptDialog
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomanizationSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (lyricsRomanizeAsMain, onLyricsRomanizeAsMainChange) = rememberPreference(LyricsRomanizeAsMainKey, defaultValue = false)
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizePunjabi, onLyricsRomanizePunjabiChange) = rememberPreference(LyricsRomanizePunjabiKey, defaultValue = true)
    val (lyricsRomanizeRussian, onLyricsRomanizeRussianChange) = rememberPreference(LyricsRomanizeRussianKey, defaultValue = true)
    val (lyricsRomanizeUkrainian, onLyricsRomanizeUkrainianChange) = rememberPreference(LyricsRomanizeUkrainianKey, defaultValue = true)
    val (lyricsRomanizeSerbian, onLyricsRomanizeSerbianChange) = rememberPreference(LyricsRomanizeSerbianKey, defaultValue = true)
    val (lyricsRomanizeBulgarian, onLyricsRomanizeBulgarianChange) = rememberPreference(LyricsRomanizeBulgarianKey, defaultValue = true)
    val (lyricsRomanizeBelarusian, onLyricsRomanizeBelarusianChange) = rememberPreference(LyricsRomanizeBelarusianKey, defaultValue = true)
    val (lyricsRomanizeKyrgyz, onLyricsRomanizeKyrgyzChange) = rememberPreference(LyricsRomanizeKyrgyzKey, defaultValue = true)
    val (lyricsRomanizeMacedonian, onLyricsRomanizeMacedonianChange) = rememberPreference(LyricsRomanizeMacedonianKey, defaultValue = true)
    val (lyricsRomanizeCyrillicByLine, onLyricsRomanizeCyrillicByLineChange) = rememberPreference(LyricsRomanizeCyrillicByLineKey, defaultValue = false)
    val (showDialog, setShowDialog) = remember { mutableStateOf(false) }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.lyrics_romanize_title),
        pageColor = IosTheme.colors.systemGroupedBackground,
        horizontalPadding = 16.dp,
    ) {
        IosSettingsGroup(
            title = "Romanization",
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_romanize_as_main)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeAsMain,
                            onCheckedChange = onLyricsRomanizeAsMainChange
                        )
                    },
                    onClick = { onLyricsRomanizeAsMainChange(!lyricsRomanizeAsMain) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language_japanese_latin),
                    title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeJapanese,
                            onCheckedChange = onLyricsRomanizeJapaneseChange
                        )
                    },
                    onClick = { onLyricsRomanizeJapaneseChange(!lyricsRomanizeJapanese) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeKorean,
                            onCheckedChange = onLyricsRomanizeKoreanChange
                        )
                    },
                    onClick = { onLyricsRomanizeKoreanChange(!lyricsRomanizeKorean) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_chinese)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeChinese,
                            onCheckedChange = onLyricsRomanizeChineseChange
                        )
                    },
                    onClick = { onLyricsRomanizeChineseChange(!lyricsRomanizeChinese) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_hindi)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeHindi,
                            onCheckedChange = onLyricsRomanizeHindiChange
                        )
                    },
                    onClick = { onLyricsRomanizeHindiChange(!lyricsRomanizeHindi) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_punjabi)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizePunjabi,
                            onCheckedChange = onLyricsRomanizePunjabiChange
                        )
                    },
                    onClick = { onLyricsRomanizePunjabiChange(!lyricsRomanizePunjabi) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.lyrics_romanization_cyrillic),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_russian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeRussian,
                            onCheckedChange = onLyricsRomanizeRussianChange
                        )
                    },
                    onClick = { onLyricsRomanizeRussianChange(!lyricsRomanizeRussian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_ukrainian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeUkrainian,
                            onCheckedChange = onLyricsRomanizeUkrainianChange
                        )
                    },
                    onClick = { onLyricsRomanizeUkrainianChange(!lyricsRomanizeUkrainian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_serbian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeSerbian,
                            onCheckedChange = onLyricsRomanizeSerbianChange
                        )
                    },
                    onClick = { onLyricsRomanizeSerbianChange(!lyricsRomanizeSerbian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_bulgarian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeBulgarian,
                            onCheckedChange = onLyricsRomanizeBulgarianChange
                        )
                    },
                    onClick = { onLyricsRomanizeBulgarianChange(!lyricsRomanizeBulgarian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_belarusian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeBelarusian,
                            onCheckedChange = onLyricsRomanizeBelarusianChange
                        )
                    },
                    onClick = { onLyricsRomanizeBelarusianChange(!lyricsRomanizeBelarusian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_kyrgyz)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeKyrgyz,
                            onCheckedChange = onLyricsRomanizeKyrgyzChange
                        )
                    },
                    onClick = { onLyricsRomanizeKyrgyzChange(!lyricsRomanizeKyrgyz) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_macedonian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeMacedonian,
                            onCheckedChange = onLyricsRomanizeMacedonianChange
                        )
                    },
                    onClick = { onLyricsRomanizeMacedonianChange(!lyricsRomanizeMacedonian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.warning),
                    title = { Text(stringResource(R.string.line_by_line_option_title)) },
                    description = { Text(stringResource(R.string.line_by_line_option_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeCyrillicByLine,
                            onCheckedChange = {
                                if (it) {
                                    setShowDialog(true)
                                } else {
                                    onLyricsRomanizeCyrillicByLineChange(false)
                                }
                            }
                        )
                    },
                    onClick = {
                        if (!lyricsRomanizeCyrillicByLine) {
                            setShowDialog(true)
                        } else {
                            onLyricsRomanizeCyrillicByLineChange(false)
                        }
                    }
                )
            )
        )
        if (showDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.line_by_line_dialog_title),
                onDismiss = { setShowDialog(false) },
                onConfirm = {
                    onLyricsRomanizeCyrillicByLineChange(true)
                    setShowDialog(false)
                },
                onCancel = { setShowDialog(false) },
                content = {
                    Text(stringResource(R.string.line_by_line_dialog_desc))
                }
            )
        }
    
    }
}
