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

@Composable
fun LosslessWaveformIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(2.5f * sx, 13.5f * sy)
            cubicTo(2.8f * sx, 8.2f * sy, 4.4f * sx, 5.0f * sy, 6.6f * sx, 5.0f * sy)
            cubicTo(8.8f * sx, 5.0f * sy, 9.4f * sx, 12.0f * sy, 11.2f * sx, 19.0f * sy)
            cubicTo(12.0f * sx, 19.0f * sy, 12.2f * sx, 11.2f * sy, 12.8f * sx, 5.0f * sy)
            cubicTo(14.6f * sx, 5.0f * sy, 15.2f * sx, 12.0f * sy, 17.0f * sx, 19.0f * sy)
            cubicTo(17.8f * sx, 19.0f * sy, 18.0f * sx, 11.2f * sy, 18.6f * sx, 5.0f * sy)
            cubicTo(20.4f * sx, 5.0f * sy, 21.2f * sx, 12.0f * sy, 22.4f * sx, 19.0f * sy)
            cubicTo(22.8f * sx, 19.0f * sy, 23.2f * sx, 16.5f * sy, 23.2f * sx, 13.5f * sy)
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.9.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
