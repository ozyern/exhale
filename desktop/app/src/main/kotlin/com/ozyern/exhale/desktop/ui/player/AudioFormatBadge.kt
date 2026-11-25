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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
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
    // Lossless tiers look exactly as on Android: glyph and words, no outlined pill.
    if (badge == AudioBadge.LOSSLESS || badge == AudioBadge.HIRES_LOSSLESS) {
        LosslessQualityBadge(hiRes = badge == AudioBadge.HIRES_LOSSLESS, ink = contentColor, playing = false, modifier = modifier)
        return
    }
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

/**
 * The Lossless waveform, the very glyph the Android app draws (its `ic_lossless`, exported from the
 * reference Figma): 15 × 8, filled. Drawn fitted into whatever box it is given, never stretched.
 */
@Composable
fun LosslessWaveformIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(LosslessGlyph),
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = modifier,
    )
}

private val LosslessGlyph: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Lossless",
        defaultWidth = 15.dp,
        defaultHeight = 8.dp,
        viewportWidth = 15f,
        viewportHeight = 8f,
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(LOSSLESS_PATH).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White),
    ).build()
}

private const val LOSSLESS_PATH = "M8.18396 0C9.94395 0 10.7029 2.946 11.3379 4.888C11.6729 3.492 11.4969 3.192 11.8569 3.192C11.9899 3.192 12.1259 3.283 12.1259 3.448C12.1259 3.459 12.1229 3.489 12.1169 3.533L12.0909 3.708C12.0199 4.172 11.8449 5.144 11.6539 5.794C13.1979 9.841 14.3449 4.511 14.4739 3.422C14.4929 3.265 14.6119 3.192 14.7309 3.192C14.8909 3.192 15.0219 3.312 14.9969 3.493C14.7199 5.255 14.2949 8 12.5469 8C11.5819 8 11.0399 7.245 10.6109 6.38C9.53995 4.276 9.04696 0.743 7.99696 0.743C7.65996 0.743 7.41096 1.094 7.39396 1.094C7.36196 1.094 7.33696 0.951 7.02297 0.559C7.32196 0.217 7.73396 0 8.18396 0ZM2.45799 0.00400001C5.21097 0.00400001 5.45597 7.268 7.01397 7.268C7.19696 7.268 7.39396 7.157 7.60996 6.906C7.72896 7.108 7.85096 7.288 7.97796 7.446C7.66696 7.801 7.27996 8 6.79497 8C5.05398 7.999 4.30598 5.084 3.66298 3.116C3.51098 3.747 3.43198 4.319 3.40198 4.575C3.38198 4.738 3.26298 4.813 3.14298 4.813C3.00899 4.813 2.87399 4.721 2.87399 4.558L2.87699 4.52C2.96599 3.873 3.14598 2.893 3.34698 2.21C3.07898 1.508 2.74499 0.741 2.25199 0.741C1.25699 0.741 0.686997 3.241 0.526997 4.575C0.507998 4.738 0.387998 4.813 0.267999 4.813C0.134999 4.813 0 4.721 0 4.558C0 4.546 0.000999995 4.533 0.00199999 4.52C0.282999 2.486 0.807996 0.00400001 2.45799 0.00400001ZM5.31497 0C5.80897 0 6.33897 0.258 6.79697 0.861C6.82197 0.891 7.07797 1.289 7.15897 1.427C8.27696 3.452 8.81796 7.277 9.88095 7.277C10.0649 7.277 10.264 7.163 10.4839 6.906C10.6039 7.108 10.7259 7.288 10.8519 7.446C10.5419 7.8 10.155 8 9.66995 8C6.93297 7.999 6.63597 0.74 5.12797 0.74C4.78798 0.74 4.53598 1.094 4.51898 1.094C4.48698 1.094 4.46198 0.951 4.14798 0.559C4.45498 0.208 4.86998 0 5.31497 0Z"

/**
 * The quality line as the Android player draws it: the waveform held steady at 70% and the tier
 * name beside it, with a band of light sweeping through the words every three seconds while the
 * song plays. No outline, no pill: the words are the badge.
 */
@Composable
fun LosslessQualityBadge(
    hiRes: Boolean,
    ink: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LosslessWaveformIcon(
            tint = ink.copy(alpha = ink.alpha * 0.7f),
            modifier = Modifier.size(width = 15.dp, height = 8.dp),
        )
        Spacer(Modifier.width(4.dp))
        LosslessQualityText(if (hiRes) "Hi-Res Lossless" else "Lossless", ink, shimmering = playing)
    }
}

@Composable
private fun LosslessQualityText(text: String, ink: Color, shimmering: Boolean) {
    val style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    val baseColor = ink.copy(alpha = ink.alpha * 0.55f)
    if (!shimmering) {
        Text(text, style = style, color = baseColor, maxLines = 1)
        return
    }
    val progress = androidx.compose.animation.core.rememberInfiniteTransition(label = "qualityShimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(durationMillis = 3_000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "qualityShimmerProgress",
    )
    Text(
        text = text,
        style = style,
        color = ink,
        maxLines = 1,
        modifier = Modifier
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val width = size.width
                val band = width * 0.6f
                val center = -band + progress.value * (width + 2 * band)
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colorStops = arrayOf(0f to baseColor, 0.5f to ink, 1f to baseColor),
                        start = androidx.compose.ui.geometry.Offset(center - band, 0f),
                        end = androidx.compose.ui.geometry.Offset(center + band, 0f),
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn,
                )
            },
    )
}
