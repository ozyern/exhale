package com.ozyern.exhale.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme

/** Temporary visual aid for tuning the mute symbol's bounds against the quiet speaker. */
@Preview(name = "Volume speaker alignment", showBackground = true, backgroundColor = 0xFF252525, widthDp = 360, heightDp = 410)
@Composable
private fun VolumeGlyphAlignmentPreview() {
    IosTheme(darkTheme = true) {
        val quietSpeaker = listOf("speaker.fill", "speaker")
            .firstOrNull { com.composables.sfsymbols.SfSymbol.named(it) != null }
            ?: "speaker.wave.3.fill"
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF252525)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Blue = right speaker at 20dp; overlays = left icon", color = Color.White)
            listOf(12, 15, 16, 17, 18, 20).forEach { muteSize ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            symbol = sf("speaker.slash.fill"),
                            contentDescription = null,
                            size = 13.dp,
                            tint = Color(0xAA42BFFF),
                        )
                        SymbolIcon(
                            symbol = sf(quietSpeaker),
                            contentDescription = null,
                            size = muteSize.dp,
                            tint = Color(0xAA54E6A5),
                        )
                    }
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        SymbolIcon(symbol = sf("speaker.wave.3.fill"), contentDescription = null, size = 20.dp, tint = Color(0xAA42BFFF))
                        SymbolIcon(
                            symbol = sf("speaker.slash.fill"),
                            contentDescription = null,
                            size = muteSize.dp,
                            tint = Color(0xAAFF8A42),
                        )
                    }
                    Text("left icon $muteSize dp", color = Color.White)
                }
            }
        }
    }
}
