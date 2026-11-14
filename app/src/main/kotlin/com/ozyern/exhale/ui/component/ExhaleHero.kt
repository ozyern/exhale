/**
 * Exhale Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.ChillaxFontFamily

/** The warm light Exhale's About and Software update pages are lit by. */
object ExhaleGlow {
    val Ground = Color(0xFF050302)
    val Ember = Color(0xFF7A3A00)
    val Amber = Color(0xFFFF9A0A)
    val Gold = Color(0xFFFFC444)
    val Core = Color(0xFFFFF1CF)
}

/**
 * Two great rings of light crossing on a dark ground, their rims burning gold where they turn
 * towards the viewer and falling away to nothing behind. The rings drift very slowly, so the
 * page breathes without anything moving enough to notice.
 */
@Composable
fun ExhaleLightRibbons(modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "exhaleRibbons")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val t = if (animated) drift else 0.5f
    Canvas(modifier) {
        drawRect(ExhaleGlow.Ground)
        val w = size.width
        val h = size.height
        val unit = maxOf(w, h)
        // The upper ring: enters from the left a third of the way down and swells off the right edge.
        ring(
            center = Offset(w * (0.98f + 0.03f * t), h * 0.52f),
            radius = Size(unit * 0.70f, unit * 0.30f),
            angle = -14f + 4f * t,
            brightFrom = 160f,
        )
        // The lower ring: rises from the bottom left and leans away to the right.
        ring(
            center = Offset(w * (0.62f - 0.03f * t), h * (1.08f - 0.02f * t)),
            radius = Size(unit * 0.56f, unit * 0.32f),
            angle = 10f - 3f * t,
            brightFrom = 200f,
        )
        // The long streak where the two lights cross, from the left edge towards the centre.
        rotate(degrees = 22f + 2f * t, pivot = Offset(w * 0.22f, h * 0.40f)) {
            val y = h * 0.40f
            val streak = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.15f to ExhaleGlow.Gold.copy(alpha = 0.9f),
                0.45f to ExhaleGlow.Amber.copy(alpha = 0.55f),
                1f to Color.Transparent,
                startX = -w * 0.2f,
                endX = w * 0.75f,
            )
            for ((width, alpha) in GlowLayers) {
                drawLine(streak, Offset(-w * 0.2f, y), Offset(w * 0.75f, y), strokeWidth = width.dp.toPx() * 0.6f, alpha = alpha)
            }
        }
    }
}

private val GlowLayers = listOf(56f to 0.06f, 28f to 0.12f, 12f to 0.28f, 4f to 0.7f, 1.5f to 1f)

private fun DrawScope.ring(center: Offset, radius: Size, angle: Float, brightFrom: Float) {
    val topLeft = Offset(center.x - radius.width, center.y - radius.height)
    val box = Size(radius.width * 2, radius.height * 2)
    rotate(degrees = angle, pivot = center) {
        // The faint body of the ring, so the light seems to sit on glass rather than on nothing.
        drawOval(
            brush = Brush.radialGradient(
                listOf(ExhaleGlow.Ember.copy(alpha = 0.32f), ExhaleGlow.Ember.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(center.x - radius.width * 0.35f, center.y),
                radius = radius.width,
            ),
            topLeft = topLeft,
            size = box,
        )
        // The rim: brightest on the near side, gone by the far side.
        val rim = Brush.sweepGradient(
            0f to Color.Transparent,
            0.30f to Color.Transparent,
            0.42f to ExhaleGlow.Amber,
            0.52f to ExhaleGlow.Core,
            0.62f to ExhaleGlow.Gold,
            0.80f to ExhaleGlow.Amber.copy(alpha = 0.5f),
            1f to Color.Transparent,
            center = center,
        )
        rotate(degrees = brightFrom - 180f, pivot = center) {
            for ((width, alpha) in GlowLayers) {
                drawOval(rim, topLeft, box, alpha = alpha, style = Stroke(width.dp.toPx()))
            }
        }
    }
}

/** The Exhale wordmark: light, wide-set Chillax with a soft warm sheen. */
@Composable
fun ExhaleWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 64.sp) {
    Text(
        text = "Exhale",
        modifier = modifier,
        style = TextStyle(
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFE9E1D6))),
            fontFamily = ChillaxFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            letterSpacing = (fontSize.value * 0.01f).sp,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
    )
}

/** The wordmark with the version under it, centred over [ExhaleLightRibbons]. */
@Composable
fun ExhaleVersionLockup(version: String, modifier: Modifier = Modifier, fontSize: TextUnit = 64.sp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ExhaleWordmark(fontSize = fontSize)
        Text(
            text = version,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = (fontSize.value * 0.38f).sp,
            fontWeight = FontWeight.Light,
            fontFamily = ChillaxFontFamily,
        )
    }
}

/** A box lit by [ExhaleLightRibbons], for the pages that carry the Exhale glow. */
@Composable
fun ExhaleGlowBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        ExhaleLightRibbons(Modifier.matchParentSize())
        content()
    }
}
