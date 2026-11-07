/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.player.PlayerState

/**
 * Premium Apple-Music-style audio format badge for Exhale Desktop.
 * Mirrors the Android app's format badges for genuinely lossless, hi-res, and spatial sources.
 */
enum class AudioBadge(val label: String) {
    DOLBY_ATMOS("Dolby Atmos"),
    HIRES_LOSSLESS("Hi-Res Lossless"),
    LOSSLESS("Lossless"),
}

fun audioBadgesFor(state: PlayerState): List<AudioBadge> {
    val badges = mutableListOf<AudioBadge>()
    if (state.immersive != null) {
        badges += AudioBadge.DOLBY_ATMOS
    }
    if (state.isLossless) {
        badges += if (state.isHiRes) AudioBadge.HIRES_LOSSLESS else AudioBadge.LOSSLESS
    }
    return badges
}

@Composable
fun AudioFormatBadges(
    state: PlayerState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentColor: Color = Color.White,
) {
    val badges = audioBadgesFor(state)
    if (badges.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { badge ->
            AudioFormatPill(badge = badge, compact = compact, contentColor = contentColor)
        }
    }
}

@Composable
fun AudioFormatPill(
    badge: AudioBadge,
    compact: Boolean = false,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Text(
        text = badge.label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 9.sp else 10.sp,
            letterSpacing = 0.4.sp,
        ),
        color = contentColor.copy(alpha = 0.9f),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
                RoundedCornerShape(6.dp),
            )
            .padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
    )
}
