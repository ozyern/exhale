/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R

private val ReturnEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The quiet way back to the line being sung, shown only while that line is off screen.
 *
 * The old pill was a refracting glass slab with a drop shadow, a countdown ring and the live lyric
 * riding inside it: four things competing with the lyrics it sat on. This is one small frosted
 * capsule with an arrow that points at the song and a single word. It eases in from just below its
 * resting place and fades away again, with no bounce.
 *
 * @param direction -1 when the sung line is above the screen, 1 when below.
 */
@Composable
fun LyricsReturnButton(
    visible: Boolean,
    direction: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220, easing = ReturnEase)) +
            slideInVertically(tween(320, easing = ReturnEase)) { it / 3 },
        exit = fadeOut(tween(160)) +
            slideOutVertically(tween(200, easing = ReturnEase)) { it / 4 },
    ) {
        val turn by animateFloatAsState(
            targetValue = if (direction < 0) 180f else 0f,
            animationSpec = tween(260, easing = ReturnEase),
            label = "lyricsReturnArrow",
        )
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val dim by animateFloatAsState(
            targetValue = if (pressed) 0.55f else 1f,
            animationSpec = tween(if (pressed) 60 else 180),
            label = "lyricsReturnPress",
        )
        val haptic = LocalHapticFeedback.current

        Row(
            modifier = Modifier
                .graphicsLayer { alpha = dim }
                .clip(CircleShape)
                // Dark enough that the lyric passing under it never reads through the word.
                .background(Color(0xFF1C1C1E).copy(alpha = 0.78f))
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .clickable(interactionSource = source, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .heightIn(min = 34.dp)
                .padding(start = 12.dp, end = 15.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_downward),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer { rotationZ = turn },
            )
            Text(
                text = stringResource(R.string.lyrics_sync),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
    }
}
