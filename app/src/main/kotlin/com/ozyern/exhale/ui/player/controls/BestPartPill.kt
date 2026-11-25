/** GPL-3.0. Third-party portions: see NOTICE. */
package com.ozyern.exhale.ui.player.controls

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.bestpart.BestParts
import com.ozyern.exhale.bestpart.LyricLine

/**
 * "Best Part": a glass pill that skips to the part of the song people replay most, or failing
 * that to its chorus.
 *
 * Offered only while it is still ahead: once the playhead has reached the best part, by the
 * button or by just listening, it goes for the rest of the track. A track with no best part
 * known (no replay curve, no synced lyrics to find a chorus in) never shows it, rather than
 * guessing.
 *
 * The reference design blurs the player under the pill with its haze; Exhale's player has no haze to share
 * here, so the pill takes the reference design's flat fallback: the disc fill over a dark slab, under the
 * same light rim.
 */
@Composable
internal fun BestPartPill(
    videoId: String?,
    durationMs: Long,
    /** Synced lines from the lyrics the player already has; empty when there are none. */
    lines: List<LyricLine>,
    /** The playhead, read only inside derived state so its tick doesn't recompose the caller. */
    positionMs: () -> Long,
    /** Off while an Exhale crossfade or DJ mix is carrying the next track in. */
    mixing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val start by produceState<Long?>(null, videoId, durationMs > 0, lines) {
        value = null
        val id = videoId ?: return@produceState
        value = runCatching { BestParts.startMs(context, id, durationMs, lines) }.getOrNull()
    }
    // Remembered per track: having been heard is what puts the button away.
    var heard by remember(videoId) { mutableStateOf(false) }
    val at = start
    val ahead by remember(at) {
        derivedStateOf { at != null && positionMs() < at - AHEAD_SLACK_MS }
    }
    val reaching by remember(at) {
        // Only a playhead inside the best part counts as hearing it. Further on is a skip past
        // it, and the button should come back for a seek to before it.
        derivedStateOf { at != null && positionMs() in (at - AHEAD_SLACK_MS)..(at + HEARD_WINDOW_MS) }
    }
    LaunchedEffect(reaching) { if (reaching) heard = true }

    val haptics = rememberHaptics()
    // Always in the layout; only its opacity animates. Shown and hidden with AnimatedVisibility,
    // it added and removed itself (and expanded/shrank its room) while a song simply played,
    // inside the player's shared-transition layout -- and a node coming or going there while the
    // player was being swiped closed crashed Compose's lookahead measure pass.
    val visible = at != null && ahead && !heard && !mixing
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(BEST_PART_FADE_MS),
        label = "bestPartShown",
    )
    Box(modifier = modifier.graphicsLayer { alpha = shown }) {
        val shape = CircleShape
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(shape)
                .background(Color.Black.copy(alpha = GLASS_FLAT_ALPHA * 0.5f))
                .clickable(enabled = visible, role = Role.Button) {
                    val target = at ?: return@clickable
                    haptics.play(Haptic.Tap)
                    heard = true
                    onSeek(target)
                }
                .semantics { contentDescription = BEST_PART_DESCRIPTION },
        ) {
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = DISC_ALPHA)))
            Box(Modifier.matchParentSize().glassRim(shape))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 15.dp, top = 7.dp, bottom = 7.dp),
            ) {
                Icon(
                    imageVector = PlayerIcons.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = BEST_PART_LABEL,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val BEST_PART_LABEL = "Best Part"
private const val BEST_PART_DESCRIPTION = "Skip to the best part"

/** Just short of the best part already counts as there, so the pill doesn't flash back on a late reading. */
private const val AHEAD_SLACK_MS = 400L

/** How far into the best part a playhead still counts as having heard it. */
private const val HEARD_WINDOW_MS = 20_000L

private const val BEST_PART_FADE_MS = 280

/** The star and ⋯ discs' fill at rest, over the slab. */
private const val DISC_ALPHA = 0.18f

/** The slab alone, with no blur under it, thick enough to hold the glyph on any cover. */
private const val GLASS_FLAT_ALPHA = 0.72f
