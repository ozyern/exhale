/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R

/**
 * The back affordance. Every screen in the app that can be backed out of uses this one.
 *
 * A dark, smoky disc with a hairline rim and a bold white chevron, readable over a page or over
 * artwork alike. Under a finger it swells by about a quarter and clears to a pale frost, holds
 * there while it is held, and springs back when it is let go: the press is seen, not just felt.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    @DrawableRes icon: Int = R.drawable.chevron_back,
    enabled: Boolean = true,
    contentDescription: String? = null,
    /** Kept for callers; the chevron is white on its own dark disc wherever it sits. */
    @Suppress("UNUSED_PARAMETER") tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    // In the wide layout the rail carries back; the page's own disc would be a second one.
    if (icon == R.drawable.chevron_back && LocalRailBack.current) return
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) spring(dampingRatio = 0.62f, stiffness = 700f) else spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "backPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                val k = 1f + 0.26f * press
                scaleX = k
                scaleY = k
                alpha = if (enabled) 1f else 0.4f
            }
            .drawBehind {
                val p = press.coerceIn(0f, 1f)
                drawCircle(lerp(Color(0xFF1B1113).copy(alpha = 0.62f), Color(0xFFD9CFD0).copy(alpha = 0.55f), p))
                drawCircle(
                    Color.White.copy(alpha = 0.14f + 0.22f * p),
                    radius = size.minDimension / 2f - 0.5.dp.toPx(),
                    style = Stroke(1.dp.toPx()),
                )
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
