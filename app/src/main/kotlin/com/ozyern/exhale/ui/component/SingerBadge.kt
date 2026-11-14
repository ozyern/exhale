/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shows which credited artist is singing the current lyric line.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.lyrics.GROUP_AGENT_ID
import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.primaryAgentId

/**
 * Resolves a display name for every lead voice of a song by matching voices
 * to the track's credited artists in descending order of led lines (the
 * first-billed artist typically sings the most lines). Only runs when the
 * shape of the data is believable (at least two credited artists, no more
 * lead voices than artists, one voice with a clear majority of lines);
 * otherwise voices keep their colors but show no name.
 */
fun resolveSingerNames(
    entries: List<LyricsEntry>,
    trackArtists: List<String>,
): Map<String, String> {
    val leadCounts = LinkedHashMap<String, Int>()
    entries.forEach { entry ->
        if (entry.isBackground) return@forEach
        val agent = entry.agent ?: return@forEach
        val id = primaryAgentId(agent)
        leadCounts[id] = (leadCounts[id] ?: 0) + 1
    }
    if (leadCounts.size < 2) return emptyMap()

    val leads = leadCounts.keys.filter { it != GROUP_AGENT_ID }
    val creditedArtists = trackArtists.mapNotNull { artist ->
        artist.trim().takeIf(String::isNotEmpty)
    }
    val ranked = leads.sortedByDescending { id -> leadCounts.getValue(id) }
    val top = leadCounts[ranked.firstOrNull()] ?: 0
    val second = leadCounts[ranked.getOrNull(1)] ?: 0
    val clearMajority = ranked.size == 1 || top >= second * 3 / 2 + 1
    val plausible = leads.isNotEmpty() && creditedArtists.size >= leads.size &&
        creditedArtists.size >= 2 && clearMajority
    if (!plausible) return emptyMap()

    val singerNames = LinkedHashMap<String, String>(ranked.size)
    ranked.forEachIndexed { rank, id -> singerNames[id] = creditedArtists[rank] }
    return singerNames
}

/**
 * Liquid Glass pill identifying the current vocalist: a colored dot plus the
 * singer's name, tinted with the singer's palette color on a translucent
 * surface so it stays legible over every player background style.
 */
@Composable
fun SingerBadge(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(
                width = 0.5.dp,
                color = color.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Animated wrapper for [SingerBadge]: fades/expands in when the lead vocalist
 * becomes active and collapses away otherwise, matching the timing of the
 * existing line-change animations so singer transitions never flicker.
 */
@Composable
fun AnimatedSingerBadge(
    visible: Boolean,
    name: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && name != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
            expandVertically(animationSpec = tween(durationMillis = 300)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200)) +
            shrinkVertically(animationSpec = tween(durationMillis = 200)),
        modifier = modifier,
    ) {
        if (name != null) {
            SingerBadge(
                name = name,
                color = color,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
