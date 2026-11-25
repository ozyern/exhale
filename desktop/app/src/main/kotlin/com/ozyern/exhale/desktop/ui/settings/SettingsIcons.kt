/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The colour each settings page wears, the same in every theme: a page is recognised by its hue the way
 * it is in a phone's own settings, so these are fixed and vivid rather than taken from the accent.
 */
internal fun categoryHue(id: String): Color? = when (id) {
    "account" -> Color(0xFF3D8BFF)
    "appearance" -> Color(0xFFFF8A3D)
    "player" -> Color(0xFF34C759)
    "lyrics" -> Color(0xFFB45CF0)
    "content" -> Color(0xFF22A6F2)
    "privacy" -> Color(0xFFFFA020)
    "storage" -> Color(0xFF1FC8B4)
    "backup" -> Color(0xFF4C7DFF)
    "integrations" -> Color(0xFF6E7BFF)
    "shortcuts" -> Color(0xFF8C94A6)
    "updates" -> Color(0xFFFF5C6C)
    "about" -> Color(0xFF9AA3B5)
    else -> null
}

/**
 * A page's icon: a flat two-tone drawing on no plate at all, in the hue of the page. Drawn rather than
 * taken from the glyph set because a glyph is one colour and a shape, where these are small pictures.
 */
@Composable
internal fun CategoryGlyph(id: String, hue: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val light = lerp(hue, Color.White, 0.5f)
        val deep = lerp(hue, Color.Black, 0.28f)
        when (id) {
            "account" -> account(u, hue)
            "appearance" -> appearance(u, hue, light)
            "player" -> headphones(u, hue, light, deep)
            "lyrics" -> lyrics(u, hue, light)
            "content" -> globe(u, hue, light)
            "privacy" -> lock(u, hue, light, deep)
            "storage" -> storage(u, hue, light, deep)
            "backup" -> cloud(u, hue, light)
            "integrations" -> link(u, hue, light)
            "shortcuts" -> keyboard(u, hue)
            "updates" -> refresh(u, hue, light)
            else -> about(u, hue)
        }
    }
}

private fun DrawScope.account(u: Float, hue: Color) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(2f * u, 2f * u), Size(20f * u, 20f * u))) }
    drawPath(disc, hue)
    clipPath(disc) {
        drawCircle(Color.White, 3.6f * u, Offset(12f * u, 9.4f * u))
        drawOval(Color.White, Offset(5.4f * u, 14.6f * u), Size(13.2f * u, 10f * u))
    }
}

private fun DrawScope.appearance(u: Float, hue: Color, light: Color) {
    drawCircle(hue, 10f * u, Offset(12f * u, 12f * u))
    clipRect(left = 12f * u) { drawCircle(light, 10f * u, Offset(12f * u, 12f * u)) }
    drawCircle(Color.White.copy(alpha = 0.9f), 3.2f * u, Offset(12f * u, 12f * u))
}

private fun DrawScope.headphones(u: Float, hue: Color, light: Color, deep: Color) {
    drawArc(hue, 180f, 180f, false, Offset(4.2f * u, 3.4f * u), Size(15.6f * u, 15.6f * u), style = Stroke(2.6f * u, cap = StrokeCap.Round))
    drawLine(hue, Offset(4.2f * u, 11.2f * u), Offset(4.2f * u, 13.5f * u), 2.6f * u, StrokeCap.Round)
    drawLine(hue, Offset(19.8f * u, 11.2f * u), Offset(19.8f * u, 13.5f * u), 2.6f * u, StrokeCap.Round)
    for (x in listOf(2.6f, 16.4f)) {
        drawRoundRect(light, Offset(x * u, 12f * u), Size(5f * u, 8.6f * u), CornerRadius(2.4f * u))
        drawRoundRect(deep, Offset((x + 1.2f) * u, 13.8f * u), Size(2.6f * u, 5f * u), CornerRadius(1.3f * u))
    }
}

private fun DrawScope.lyrics(u: Float, hue: Color, light: Color) {
    drawRoundRect(hue, Offset(2.4f * u, 3.6f * u), Size(19.2f * u, 14f * u), CornerRadius(4.6f * u))
    drawPath(Path().apply {
        moveTo(6.6f * u, 16f * u)
        lineTo(6.4f * u, 21.4f * u)
        lineTo(12.2f * u, 17f * u)
        close()
    }, hue)
    drawRoundRect(Color.White, Offset(6.4f * u, 7.6f * u), Size(11.2f * u, 2f * u), CornerRadius(1f * u))
    drawRoundRect(light, Offset(6.4f * u, 11.4f * u), Size(7.2f * u, 2f * u), CornerRadius(1f * u))
}

private fun DrawScope.globe(u: Float, hue: Color, light: Color) {
    val stroke = Stroke(2f * u)
    drawCircle(hue, 9f * u, Offset(12f * u, 12f * u), style = stroke)
    drawOval(light, Offset(8f * u, 3f * u), Size(8f * u, 18f * u), style = Stroke(1.7f * u))
    drawLine(hue, Offset(3f * u, 12f * u), Offset(21f * u, 12f * u), 1.8f * u, StrokeCap.Round)
    drawArc(light, 10f, 160f, false, Offset(4.6f * u, 5.2f * u), Size(14.8f * u, 7f * u), style = Stroke(1.5f * u))
    drawArc(light, 190f, 160f, false, Offset(4.6f * u, 11.8f * u), Size(14.8f * u, 7f * u), style = Stroke(1.5f * u))
}

private fun DrawScope.lock(u: Float, hue: Color, light: Color, deep: Color) {
    drawArc(light, 180f, 180f, false, Offset(7.4f * u, 3f * u), Size(9.2f * u, 10f * u), style = Stroke(2.6f * u))
    drawLine(light, Offset(7.4f * u, 8f * u), Offset(7.4f * u, 11f * u), 2.6f * u)
    drawLine(light, Offset(16.6f * u, 8f * u), Offset(16.6f * u, 11f * u), 2.6f * u)
    drawRoundRect(hue, Offset(4.4f * u, 10.2f * u), Size(15.2f * u, 11f * u), CornerRadius(3.4f * u))
    drawCircle(deep, 1.9f * u, Offset(12f * u, 14.9f * u))
    drawRoundRect(deep, Offset(11.1f * u, 15.6f * u), Size(1.8f * u, 3.2f * u), CornerRadius(0.9f * u))
}

private fun DrawScope.storage(u: Float, hue: Color, light: Color, deep: Color) {
    val rows = listOf(light to 3.6f, hue to 9.4f, deep to 15.2f)
    rows.forEach { (color, y) ->
        drawRoundRect(color, Offset(3f * u, y * u), Size(18f * u, 5.2f * u), CornerRadius(2.6f * u))
        drawCircle(Color.White.copy(alpha = 0.9f), 0.95f * u, Offset(17.6f * u, (y + 2.6f) * u))
        drawCircle(Color.White.copy(alpha = 0.55f), 0.95f * u, Offset(14.8f * u, (y + 2.6f) * u))
    }
}

private fun DrawScope.cloud(u: Float, hue: Color, light: Color) {
    drawCircle(light, 4.6f * u, Offset(7.6f * u, 14.2f * u))
    drawCircle(hue, 6f * u, Offset(13f * u, 10.6f * u))
    drawCircle(hue, 4.2f * u, Offset(18f * u, 14.6f * u))
    drawRoundRect(hue, Offset(4f * u, 13.2f * u), Size(16.4f * u, 6f * u), CornerRadius(3f * u))
    drawLine(Color.White, Offset(12.2f * u, 18.6f * u), Offset(12.2f * u, 11.8f * u), 1.9f * u, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(9.2f * u, 14.4f * u)
        lineTo(12.2f * u, 11.4f * u)
        lineTo(15.2f * u, 14.4f * u)
    }, Color.White, style = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.link(u: Float, hue: Color, light: Color) {
    rotate(-45f, Offset(12f * u, 12f * u)) {
        drawRoundRect(hue, Offset(1.6f * u, 8.6f * u), Size(12.4f * u, 6.8f * u), CornerRadius(3.4f * u), style = Stroke(2.3f * u))
        drawRoundRect(light, Offset(10f * u, 8.6f * u), Size(12.4f * u, 6.8f * u), CornerRadius(3.4f * u), style = Stroke(2.3f * u))
    }
}

private fun DrawScope.keyboard(u: Float, hue: Color) {
    drawRoundRect(hue, Offset(2f * u, 5.4f * u), Size(20f * u, 13.4f * u), CornerRadius(3.6f * u))
    for (row in 0..1) {
        for (col in 0..3) {
            drawRoundRect(
                Color.White.copy(alpha = if (row == 0) 0.95f else 0.7f),
                Offset((5f + col * 3.7f) * u, (8.3f + row * 3.4f) * u),
                Size(2.4f * u, 2.2f * u),
                CornerRadius(0.7f * u),
            )
        }
    }
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(7.6f * u, 15.2f * u), Size(8.8f * u, 1.9f * u), CornerRadius(0.95f * u))
}

private fun DrawScope.refresh(u: Float, hue: Color, light: Color) {
    val centre = Offset(12f * u, 12f * u)
    val radius = 7.6f * u
    drawArc(hue, 40f, 280f, false, Offset(centre.x - radius, centre.y - radius), Size(radius * 2, radius * 2), style = Stroke(2.7f * u, cap = StrokeCap.Round))
    // An arrowhead at the end of the arc, pointing the way it runs (clockwise on screen).
    val end = Math.toRadians(320.0)
    val tip = Offset(centre.x + radius * cos(end).toFloat(), centre.y + radius * sin(end).toFloat())
    val along = Offset(-sin(end).toFloat(), cos(end).toFloat())
    val across = Offset(cos(end).toFloat(), sin(end).toFloat())
    drawPath(Path().apply {
        moveTo(tip.x + along.x * 4.2f * u, tip.y + along.y * 4.2f * u)
        lineTo(tip.x + across.x * 3.6f * u, tip.y + across.y * 3.6f * u)
        lineTo(tip.x - across.x * 3.6f * u, tip.y - across.y * 3.6f * u)
        close()
    }, light)
}

private fun DrawScope.about(u: Float, hue: Color) {
    drawCircle(hue, 10f * u, Offset(12f * u, 12f * u))
    drawCircle(Color.White, 1.4f * u, Offset(12f * u, 7.6f * u))
    drawRoundRect(Color.White, Offset(10.8f * u, 10.4f * u), Size(2.4f * u, 7f * u), CornerRadius(1.2f * u))
}
