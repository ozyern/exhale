/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.NavIcons

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
    Row(
        verticalAlignment = Alignment.CenterVertically,
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
    ) {
        if (badge == AudioBadge.LOSSLESS || badge == AudioBadge.HIRES_LOSSLESS) {
            LosslessWaveformIcon(
                modifier = Modifier.size(width = if (compact) 12.dp else 14.dp, height = if (compact) 8.dp else 10.dp),
                tint = contentColor.copy(alpha = 0.9f),
            )
            Spacer(Modifier.width(3.dp))
        } else if (badge == AudioBadge.DOLBY_ATMOS) {
            Icon(
                NavIcons.DolbyAtmos,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.9f),
                modifier = Modifier.size(if (compact) 11.dp else 13.dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 9.sp else 10.sp,
                letterSpacing = 0.4.sp,
            ),
            color = contentColor.copy(alpha = 0.9f),
        )
    }
}

/**
 * Authentic Apple-Music-style Audio Quality popover.
 * Shows high fidelity stream specifications and provenance when tapping the Lossless badge.
 */
@Composable
fun AudioQualityFlyout(
    state: PlayerState,
    onDismiss: () -> Unit,
) {
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                    .coerceIn(16, (windowSize.width - popupContentSize.width - 16).coerceAtLeast(16))
                val y = (anchorBounds.top - popupContentSize.height - 12)
                    .coerceIn(16, (windowSize.height - popupContentSize.height - 16).coerceAtLeast(16))
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .width(310.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF01C1C1E))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LosslessWaveformIcon(
                        tint = Color.White,
                        modifier = Modifier.size(width = 18.dp, height = 13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.isHiRes) "Hi-Res Lossless" else "Lossless",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Lossless audio compression preserves all the original data in the music file.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)),
                )
                Spacer(Modifier.height(10.dp))

                val sampleRateFormatted = state.sampleRate?.let { "${it / 1000.0} kHz".removeSuffix(".0 kHz").plus(" kHz") } ?: "44.1 kHz"
                val bitDepthFormatted = state.bitDepth?.let { "$it-bit" } ?: "16-bit"
                val formatString = "FLAC · $bitDepthFormatted / $sampleRateFormatted"
                val source = state.sourceLabel ?: "Sabrina's Basement"

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Format", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Text(formatString, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Source", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Text(source, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
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
