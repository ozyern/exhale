/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The source picker follows BitChord's lyrics provider sheet
 * (github.com/kushagrasinghx/BitChord, GPL-3.0).
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.PreferredLyricsProvider
import com.ozyern.exhale.constants.ProviderOrderKey
import com.ozyern.exhale.constants.detail
import com.ozyern.exhale.constants.displayName
import com.ozyern.exhale.constants.wordSynced
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.di.LyricsHelperEntryPoint
import com.ozyern.exhale.lyrics.LyricsLookups
import com.ozyern.exhale.lyrics.LyricsSourceState
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.component.LiquidGlassSheet
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.screens.settings.SyncBadge
import com.ozyern.exhale.ui.screens.settings.lyricsSourceOrder
import com.ozyern.exhale.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/** The provider's display name for a source name as the lookup reports it. */
private fun sourceFor(name: String): PreferredLyricsProvider? =
    PreferredLyricsProvider.entries.firstOrNull { it.displayName().equals(name, ignoreCase = true) }
        ?: when (name) {
            "Kugou" -> PreferredLyricsProvider.KUGOU
            else -> null
        }

/**
 * Which source the lyrics on screen came from, as a small chip over the lyrics. Opens the picker.
 */
@Composable
internal fun LyricsSourceChip(mediaId: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A round glass disc, like the translate and pronunciation ones beside it.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .playerGlass(CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.lyrics),
            contentDescription = "Lyrics sources",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Every source for this song, in the order they are trusted, and where each one stands: still
 * looking, found, had nothing, or the one on screen. One that already answered switches at once;
 * one that hasn't been asked is asked now, and switches when it answers.
 */
@Composable
internal fun LyricsSourceSheet(mediaMetadata: MediaMetadata, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val lookup by LyricsLookups.state.collectAsState()
    val (savedOrder) = rememberPreference(ProviderOrderKey, "")
    val thisSong = lookup?.takeIf { it.mediaId == mediaMetadata.id }
    val helper = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java).lyricsHelper()
    }
    var requested by remember { mutableStateOf<String?>(null) }

    // The lookup's own order where there is one (it names the YouTube sources too), else the saved one.
    val names = thisSong?.order ?: lyricsSourceOrder(savedOrder).map { it.displayName() }

    LiquidGlassSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = "Lyrics source",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            Text(
                text = "Every source is asked at once; the first in your order with synced lyrics is shown.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                names.forEach { name ->
                    val state = thisSong?.states?.get(name)
                        ?: if (requested == name) LyricsSourceState.FETCHING else LyricsSourceState.WAITING
                    SourceRow(
                        name = name,
                        source = sourceFor(name),
                        state = state,
                        current = thisSong?.current == name,
                        onClick = {
                            requested = name
                            scope.launch {
                                val lyrics = helper.lyricsFrom(name, mediaMetadata)
                                if (lyrics != null) {
                                    database.query { upsert(LyricsEntity(id = mediaMetadata.id, lyrics = lyrics)) }
                                    onDismiss()
                                }
                                requested = null
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    source: PreferredLyricsProvider?,
    state: LyricsSourceState,
    current: Boolean,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val enabled = !current && state != LyricsSourceState.NOT_FOUND && state != LyricsSourceState.FETCHING
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ink.copy(alpha = if (current) 0.12f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background((if (current) accent else ink).copy(alpha = if (current) 0.2f else 0.08f)),
        ) {
            when {
                state == LyricsSourceState.FETCHING -> LoadingRing(modifier = Modifier.size(18.dp), color = ink.copy(alpha = 0.8f), stroke = 2.dp)
                current || state == LyricsSourceState.FOUND -> Icon(
                    painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = if (current) accent else ink.copy(alpha = 0.75f),
                    modifier = Modifier.size(19.dp),
                )
                state == LyricsSourceState.NOT_FOUND -> Icon(
                    painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
                else -> Icon(
                    painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = ink.copy(alpha = if (state == LyricsSourceState.NOT_FOUND) 0.5f else 1f),
                    maxLines = 1,
                )
                if (source != null) {
                    Spacer(Modifier.width(6.dp))
                    SyncBadge(wordSynced = source.wordSynced)
                }
            }
            Text(
                text = when {
                    current -> "Showing now"
                    state == LyricsSourceState.FOUND -> "Found — tap to switch"
                    state == LyricsSourceState.NOT_FOUND -> "Doesn't have this song"
                    state == LyricsSourceState.FETCHING -> "Looking…"
                    else -> source?.detail() ?: "Tap to ask"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
