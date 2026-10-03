/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * The month at a glance: the covers you played most, laid as a mosaic
 * and softened under a scrim, with the one number that sums the month set large across them.
 */
@Composable
internal fun SoundChemHero(
    covers: List<String>,
    minutes: Long,
    periodText: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val tiles = (covers + covers + covers).take(4)
        if (tiles.isNotEmpty()) {
            Column(Modifier.fillMaxSize().blur(2.dp)) {
                listOf(0, 2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        listOf(row, row + 1).forEach { index ->
                            AsyncImage(
                                model = tiles.getOrNull(index),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp),
        ) {
            Text(
                text = periodText.uppercase(),
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = "%,d".format(minutes),
                fontSize = 64.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                text = "minutes of music",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** Three small facts the totals don't say on their own. */
@Composable
internal fun SoundChemInsights(
    dailyAverageMinutes: Long,
    onRepeatTitle: String?,
    onRepeatPlays: Int,
    artistCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CapsuleGap)) {
        InsightTile("Daily average", "${dailyAverageMinutes} min", Modifier.weight(1f))
        InsightTile(
            "On repeat",
            onRepeatTitle?.let { "$onRepeatPlays×" } ?: "—",
            Modifier.weight(1f),
            caption = onRepeatTitle,
        )
        InsightTile("Artists heard", artistCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun InsightTile(label: String, value: String, modifier: Modifier, caption: String? = null) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .padding(14.dp),
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        caption?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A song's place in the month: the top three are lit, the rest are quiet numbers. */
@Composable
internal fun SoundChemRank(rank: Int, modifier: Modifier = Modifier) {
    val podium = when (rank) {
        1 -> Color(0xFFFFC94D)
        2 -> Color(0xFFD9DEE6)
        3 -> Color(0xFFE0A06A)
        else -> null
    }
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(podium?.copy(alpha = 0.22f) ?: Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rank.toString(),
            fontSize = 13.sp,
            fontWeight = if (podium != null) FontWeight.Black else FontWeight.SemiBold,
            color = podium ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
