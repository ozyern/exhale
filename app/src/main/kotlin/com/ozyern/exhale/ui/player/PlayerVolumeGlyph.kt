package com.ozyern.exhale.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.volumeBars

private val PlayerVolumeGlyphSize = 20.dp
private val PlayerQuietGlyphSize = 12.dp

/** Slash symbol size at which its speaker body matches the one inside the three-wave symbol. */
private val PlayerMuteGlyphSize = 13.dp
internal val PlayerVolumeIconSlotSize = 20.dp

/** A speaker with no waves; the symbol library decides which spelling it bundles. */
private val QuietSpeakerSymbol: String by lazy {
    listOf("speaker.fill", "speaker").firstOrNull { com.composables.sfsymbols.SfSymbol.named(it) != null }
        ?: "speaker.wave.3.fill"
}

/** Quiet end of the slider: a plain speaker, struck through while muted. */
@Composable
internal fun PlayerQuietVolumeGlyph(level: Float, tint: Color, modifier: Modifier = Modifier) {
    val muted = level <= 0.01f
    Box(modifier = modifier.size(PlayerVolumeIconSlotSize), contentAlignment = Alignment.CenterStart) {
        SymbolIcon(
            symbol = sf(if (muted) "speaker.slash.fill" else QuietSpeakerSymbol),
            contentDescription = if (muted) "Muted" else "Quiet",
            size = if (muted) PlayerMuteGlyphSize else PlayerQuietGlyphSize,
            tint = tint,
            bounceOnChange = true,
        )
    }
}

/**
 * Loud end of the slider. One wave symbol at every level, so the speaker keeps its size and only
 * the waves light up as the volume rises.
 */
@Composable
internal fun PlayerVolumeGlyph(level: Float, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(PlayerVolumeIconSlotSize), contentAlignment = Alignment.CenterEnd) {
        SymbolIcon(
            symbol = sf("speaker.wave.3.fill"),
            contentDescription = "Volume",
            size = PlayerVolumeGlyphSize,
            tint = tint,
            replayOn = volumeBars(level),
            variableValue = volumeBars(level),
        )
    }
}
