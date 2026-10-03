/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R

/**
 * OPPO Sans, the ColorOS system face, cut down to Latin. One variable file, named at each weight
 * so `FontWeight` picks the right point on its axis.
 */
val OppoSans: FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(
            R.font.oppo_sans,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

/** Lays [content] out in OPPO Sans, every text style of the theme switched over to it. */
@Composable
fun ColorOsType(content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    fun TextStyle.oppo() = copy(fontFamily = OppoSans)
    val typography = Typography(
        displayLarge = t.displayLarge.oppo(), displayMedium = t.displayMedium.oppo(), displaySmall = t.displaySmall.oppo(),
        headlineLarge = t.headlineLarge.oppo(), headlineMedium = t.headlineMedium.oppo(), headlineSmall = t.headlineSmall.oppo(),
        titleLarge = t.titleLarge.oppo(), titleMedium = t.titleMedium.oppo(), titleSmall = t.titleSmall.oppo(),
        bodyLarge = t.bodyLarge.oppo(), bodyMedium = t.bodyMedium.oppo(), bodySmall = t.bodySmall.oppo(),
        labelLarge = t.labelLarge.oppo(), labelMedium = t.labelMedium.oppo(), labelSmall = t.labelSmall.oppo(),
    )
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = MaterialTheme.shapes, typography = typography) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge, content)
    }
}

private val GoldLight = Color(0xFFFFE7A8)
private val Gold = Color(0xFFFFC24A)
private val GoldDeep = Color(0xFFE08A12)

/**
 * The "Exhale" wordmark, lettered the way ColorOS letters its own name: a monoline geometric face
 * with round ends, a round "Ɛ" for the capital, single-storey "a", and a gold that is pale at the
 * top of the strokes and deep at their feet, glowing warm behind them.
 *
 * No font has that Ɛ, so the six letters are drawn here as strokes on a fixed grid (units of the
 * ascender height), which also keeps them sharp at every size.
 */
@Composable
fun ExhaleGlowWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 64.sp,
) {
    val height = (fontSize.value * 0.78f).dp
    val width = height * (WordmarkWidth / WordmarkH)
    val path = remember { exhalePath() }
    Box(modifier.size(width, height), contentAlignment = Alignment.Center) {
        // A soft shadow under the letters, as the ColorOS mark sits on its wallpaper: it lifts
        // them off a bright background without a glow drawing attention to itself.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.compose.foundation.Canvas(
                Modifier
                    .matchParentSize()
                    .blur(height * 0.07f, BlurredEdgeTreatment.Unbounded),
            ) {
                val k = size.height / WordmarkH
                scale(k, k, pivot = Offset.Zero) {
                    translate(top = 0.035f) {
                        drawPath(path, Color.Black.copy(alpha = 0.30f), style = wordmarkStroke(StrokeW * 1.15f))
                    }
                }
            }
        }
        // The letters: white, a breath translucent so the wallpaper's colour carries into them,
        // a touch brighter at the top than at the foot.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val k = size.height / WordmarkH
            scale(k, k, pivot = Offset.Zero) {
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.97f), Color.White.copy(alpha = 0.86f)),
                        startY = 0f,
                        endY = WordmarkH,
                    ),
                    style = wordmarkStroke(StrokeW),
                )
            }
        }
    }
}

private const val WordmarkH = 1.45f
private const val XTop = 0.45f
private const val StrokeW = 0.14f
private const val Gap = 0.14f
private const val CapH = 1.36f
private const val CapSquash = 0.96f

private fun wordmarkStroke(w: Float) = androidx.compose.ui.graphics.drawscope.Stroke(
    width = w,
    cap = androidx.compose.ui.graphics.StrokeCap.Round,
    join = androidx.compose.ui.graphics.StrokeJoin.Round,
)

private val WordmarkWidth: Float = run {
    val rE = (CapH - StrokeW) / 2
    val rA = (WordmarkH - XTop - StrokeW) / 2
    val advances = listOf(2 * rE * CapSquash + StrokeW, 0.78f + StrokeW, 0.72f + StrokeW, 2 * rA + StrokeW, StrokeW, 2 * rA + StrokeW)
    advances.sum() + Gap * (advances.size - 1)
}

/** The six letters as one stroked path, on a grid where the ascender is [WordmarkH] tall. */
private fun exhalePath(): androidx.compose.ui.graphics.Path {
    val p = androidx.compose.ui.graphics.Path()
    val w = StrokeW
    val bottom = WordmarkH - w / 2
    var x = 0f

    // Ɛ: an open oval, its mouth to the right, and the bar through its middle.
    val rE = (CapH - w) / 2
    val eCx = x + w / 2 + rE * CapSquash
    val eCy = WordmarkH - CapH / 2
    p.arcTo(
        androidx.compose.ui.geometry.Rect(eCx - rE * CapSquash, eCy - rE, eCx + rE * CapSquash, eCy + rE),
        startAngleDegrees = 40f, sweepAngleDegrees = 280f, forceMoveTo = true,
    )
    p.moveTo(x + w / 2, eCy); p.lineTo(eCx + rE * CapSquash * 0.78f, eCy)
    x += 2 * rE * CapSquash + w + Gap

    // x
    val xw = 0.78f
    p.moveTo(x + w / 2, XTop + w / 2); p.lineTo(x + w / 2 + xw, bottom)
    p.moveTo(x + w / 2 + xw, XTop + w / 2); p.lineTo(x + w / 2, bottom)
    x += xw + w + Gap

    // h
    val rr = 0.36f
    val sx = x + w / 2
    p.moveTo(sx, w / 2); p.lineTo(sx, bottom)
    val hCy = XTop + w / 2 + rr
    p.moveTo(sx, hCy)
    p.arcTo(androidx.compose.ui.geometry.Rect(sx, hCy - rr, sx + 2 * rr, hCy + rr), 180f, 180f, false)
    p.lineTo(sx + 2 * rr, bottom)
    x += 2 * rr + w + Gap

    // a: a full bowl and its stem on the right.
    val rA = (WordmarkH - XTop - w) / 2
    var cx = x + w / 2 + rA
    val cy = XTop + w / 2 + rA
    p.addOval(androidx.compose.ui.geometry.Rect(cx - rA, cy - rA, cx + rA, cy + rA))
    p.moveTo(cx + rA, XTop + w / 2); p.lineTo(cx + rA, bottom)
    x += 2 * rA + w + Gap

    // l
    p.moveTo(x + w / 2, w / 2); p.lineTo(x + w / 2, bottom)
    x += w + Gap

    // e: the bar across, then the bowl round from its end, open at the lower right.
    cx = x + w / 2 + rA
    p.moveTo(cx - rA, cy); p.lineTo(cx + rA, cy)
    p.arcTo(androidx.compose.ui.geometry.Rect(cx - rA, cy - rA, cx + rA, cy + rA), 0f, -312f, true)
    return p
}

/** The version under the wordmark, in OPPO Sans: large, light, white. */
@Composable
fun ColorOsVersionText(version: String, fontSize: TextUnit = 40.sp, modifier: Modifier = Modifier) {
    Text(
        text = version,
        style = TextStyle(
            fontFamily = OppoSans,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            color = Color.White.copy(alpha = 0.94f),
            letterSpacing = 0.2.sp,
        ),
        modifier = modifier,
    )
}
