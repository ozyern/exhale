package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.utils.sharedArtworkTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFMusicNoteList
import com.composables.sfsymbols.monochrome.SFPlayFill
import com.composables.sfsymbols.monochrome.SFPauseFill
import com.composables.sfsymbols.monochrome.SFShuffle
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.component.IosGlyph
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.Button
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.screens.library.apple.SfStyle

@Composable
fun PlaylistIdentity(
    title: String,
    artwork: String?,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    var artworkFailed by remember(artwork) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val coverSize = minOf(maxWidth * .68f, 300.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.sharedArtworkTarget().size(coverSize).clip(com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle(12.dp))
                .background(colors.secondarySystemGroupedBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork.isNullOrBlank() || artworkFailed) {
                IosGlyph(sf("SFMusicNoteList"), IosTheme.tint, 72.dp)
            } else {
                val reused = com.ozyern.exhale.ui.utils.SharedArtworkSeedImage(Modifier.fillMaxSize(), ContentScale.Crop)
                if (!reused) {
                    AsyncImage(onError = { if (!reused) artworkFailed = true }, model = artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        if (title.isNotBlank()) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = SfStyle(22.sp, 27.sp, FontWeight.Bold, (-0.45).sp), color = colors.label,
            maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = SfStyle(15.sp, 20.sp), color = colors.secondaryLabel,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
    }
}

@Composable
fun PlaylistPlaybackActions(
    enabled: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = IosTheme.tint
    val fill = IosTheme.colors.secondarySystemFill
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (shuffle in listOf(false, true)) {
            Row(
                Modifier.weight(1f).height(48.dp)
                    .clip(com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle(12.dp))
                    .background(fill)
                    .clickable(enabled = enabled, role = Role.Button, onClick = if (shuffle) onShuffle else onPlay),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IosGlyph(
                    sf(if (shuffle) "SFShuffle" else if (playing) "SFPauseFill" else "SFPlayFill"),
                    tint.copy(alpha = if (enabled) 1f else 0.4f), 20.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (shuffle) R.string.shuffle else if (playing) R.string.pause else R.string.play), color = tint.copy(alpha = if (enabled) 1f else 0.4f))
            }
        }
    }
}
