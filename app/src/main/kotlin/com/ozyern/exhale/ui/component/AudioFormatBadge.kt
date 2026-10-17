/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.db.entities.FormatEntity

/**
 * Premium, Apple-Music-style audio-format badge.
 *
 * These reflect the ACTUAL decoded stream ([FormatEntity]) — they are not decorative.
 * Note: YouTube Music streams are lossy (Opus/AAC), so these light up for genuinely
 * lossless / hi-res / spatial sources (e.g. local FLAC/ALAC files or any future
 * lossless provider), matching how the iOS music app only tags real capabilities.
 */
enum class AudioBadge(val label: String) {
    DOLBY_ATMOS("Dolby Atmos"),
    HIRES_LOSSLESS("Hi-Res Lossless"),
    LOSSLESS("Lossless"),
}

private val ATMOS_TOKENS = listOf("ec-3", "ec3", "eac3", "e-ac-3", "ac-4", "ac4", "atmos", "joc")
private val LOSSLESS_TOKENS = listOf("flac", "alac", "audio/flac", "x-flac", "pcm", "wav", "aiff")

/** Hz threshold above which lossless audio is considered "high-resolution". */
private const val HIRES_SAMPLE_RATE = 48_000

/** Resolves the badges a track qualifies for from its real stream format. */
fun audioBadgesFor(format: FormatEntity?): List<AudioBadge> {
    format ?: return emptyList()
    val codecs = format.codecs.lowercase()
    val mime = format.mimeType.lowercase()
    val sampleRate = format.sampleRate ?: 0

    val badges = mutableListOf<AudioBadge>()

    val isAtmos = ATMOS_TOKENS.any { it in codecs || it in mime }
    if (isAtmos) badges += AudioBadge.DOLBY_ATMOS

    val isLossless = LOSSLESS_TOKENS.any { it in codecs || it in mime }
    if (isLossless) {
        badges += if (sampleRate > HIRES_SAMPLE_RATE) AudioBadge.HIRES_LOSSLESS else AudioBadge.LOSSLESS
    }

    return badges
}

/**
 * Renders the given [format]'s badges as a row of pills. Emits nothing when the track
 * has no premium-format capabilities, so it is safe to always place in the layout.
 */
@Composable
fun AudioFormatBadges(
    format: FormatEntity?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val badges = audioBadgesFor(format)
    if (badges.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { badge ->
            AudioFormatPill(badge = badge, compact = compact)
        }
    }
}

@Composable
private fun AudioFormatPill(badge: AudioBadge, compact: Boolean) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Text(
        text = badge.label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 9.sp else 10.sp,
            letterSpacing = 0.4.sp,
        ),
        color = contentColor.copy(alpha = 0.9f),
        modifier = Modifier
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
