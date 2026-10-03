/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import kotlin.math.abs

private val RemoveRed = Color(0xFFFF453A)

/**
 * What a swipe on a queue row is about to do: nothing at all at rest, and
 * while the row slides only the strip it has uncovered — a soft red that deepens towards the point
 * where letting go removes the song.
 *
 * The old background was a full-width red slab under every row. The rows themselves are
 * transparent so the player's colours show through them, which meant the slab showed through too:
 * the whole queue sat on red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSwipeBackground(state: SwipeToDismissBoxState, gutter: Dp) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val offset = runCatching { state.requireOffset() }.getOrDefault(0f)
                if (abs(offset) < 0.5f) return@drawWithContent
                val reach = (abs(offset) / (size.width * 0.45f)).coerceIn(0f, 1f)
                val left = if (offset > 0f) 0f else size.width + offset
                val right = if (offset > 0f) offset else size.width
                clipRect(left = left, top = 0f, right = right, bottom = size.height) {
                    drawRect(RemoveRed.copy(alpha = 0.28f + 0.6f * reach))
                    this@drawWithContent.drawContent()
                }
            }
            .padding(horizontal = gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RemoveGlyph()
        RemoveGlyph()
    }
}

@Composable
private fun RemoveGlyph() {
    Icon(
        painter = painterResource(R.drawable.delete),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(22.dp),
    )
}
