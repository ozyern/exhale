/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.LyricsClickKey
import com.ozyern.exhale.constants.LyricsLayout
import com.ozyern.exhale.constants.LyricsLayoutKey
import com.ozyern.exhale.constants.LyricsLineSpacingKey
import com.ozyern.exhale.constants.LyricsShowPronunciationKey
import com.ozyern.exhale.constants.LyricsShowTranslationKey
import com.ozyern.exhale.constants.LyricsTextSizeKey
import com.ozyern.exhale.constants.LyricsTranslateLanguageKey
import com.ozyern.exhale.lyrics.LyricsTranslator
import com.ozyern.exhale.ui.component.LiquidGlassSheet
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * What the lyrics' gear opens: the things that change how the words look and read, and nothing
 * else — which layout, how big, how far apart, translated into what, and where they come from.
 */
@Composable
internal fun LyricsSettingsSheet(
    onOpenSources: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (layout, setLayout) = rememberEnumPreference(LyricsLayoutKey, LyricsLayout.GLASS)
    val (textSize, setTextSize) = rememberPreference(LyricsTextSizeKey, 26f)
    val (lineSpacing, setLineSpacing) = rememberPreference(LyricsLineSpacingKey, 1.3f)
    val (translate, setTranslate) = rememberPreference(LyricsShowTranslationKey, false)
    val (translateTo, setTranslateTo) = rememberPreference(LyricsTranslateLanguageKey, "")
    val (pronunciation, setPronunciation) = rememberPreference(LyricsShowPronunciationKey, true)
    val (tapToSeek, setTapToSeek) = rememberPreference(LyricsClickKey, true)

    LiquidGlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "Lyrics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            SectionLabel("Style")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyleCard("Liquid glass", "Apple Music, full screen", layout == LyricsLayout.GLASS, Modifier.weight(1f)) {
                    setLayout(LyricsLayout.GLASS)
                }
                StyleCard("Classic", "Under the player's header", layout == LyricsLayout.CLASSIC, Modifier.weight(1f)) {
                    setLayout(LyricsLayout.CLASSIC)
                }
            }

            SectionLabel("Text")
            SliderRow("Size", "${textSize.roundToInt()}", textSize, 18f..40f, setTextSize)
            SliderRow("Line spacing", "%.1f×".format(lineSpacing), lineSpacing, 1.0f..2.0f, setLineSpacing)

            SectionLabel("Translation")
            SwitchRow(
                "Translate lines",
                "Each line with its translation under it",
                translate,
                setTranslate,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                LanguageChip("Phone language", translateTo.isBlank()) { setTranslateTo("") }
                LyricsTranslator.languages.forEach { (code, name) ->
                    LanguageChip(name, translateTo == code) {
                        setTranslateTo(code)
                        if (!translate) setTranslate(true)
                    }
                }
            }
            SwitchRow(
                "Pronunciation",
                "Romanised lines under Japanese, Korean and other scripts",
                pronunciation,
                setPronunciation,
            )

            SectionLabel("Behaviour")
            SwitchRow("Tap a line to jump to it", null, tapToSeek, setTapToSeek)

            SectionLabel("Source")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable(onClick = onOpenSources)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(painterResource(R.drawable.lyrics), null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lyrics source", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Wrong words or timing? Try another provider",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(painterResource(R.drawable.navigate_next), null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun StyleCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = current.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
