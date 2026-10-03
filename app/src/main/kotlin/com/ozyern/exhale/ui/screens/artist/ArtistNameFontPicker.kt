/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.artist

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple Music 7's per-artist lettering, as a setting: every face shown set in itself on a little
 * artist-page header, so the choice is made by looking rather than by reading names. The one picked
 * is the default for every artist page; holding a name on an artist's page still gives just that
 * artist a face of their own, and those can all be reset from here.
 */
@Composable
fun ArtistNameFontPicker(
    selected: ArtistNameFont,
    onSelect: (ArtistNameFont) -> Unit,
) {
    val context = LocalContext.current
    var overrides by remember { mutableIntStateOf(ArtistNameFont.overrideCount(context)) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = ArtistNameFont.entries.indexOf(selected)
        if (index > 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Artist name style",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Text(
            text = "How artists' names are lettered at the top of their page. Hold a name on an artist's page to give just them their own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 12.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ArtistNameFont.entries) { font ->
                FontCard(font = font, selected = font == selected, onClick = { onSelect(font) })
            }
        }
        if (overrides > 0) {
            Text(
                text = "Reset $overrides artist-specific ${if (overrides == 1) "style" else "styles"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 10.dp, top = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        ArtistNameFont.clearOverrides(context)
                        overrides = 0
                        Toast.makeText(context, "Every artist now uses ${selected.label}", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FontCard(font: ArtistNameFont, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val accent = MaterialTheme.colorScheme.primary
    val rim by animateColorAsState(
        if (selected) accent else Color.White.copy(alpha = 0.10f),
        label = "fontCardRim",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 138.dp, height = 96.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3A3F55), Color(0xFF151722)),
                    ),
                )
                .border(if (selected) 2.dp else 0.5.dp, rim, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sample = "Artist"
            Text(
                text = if (font.uppercase) sample.uppercase() else sample,
                fontFamily = font.family,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp * font.scale,
                letterSpacing = (-0.3).sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).artistNameTexture(font),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = font.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
