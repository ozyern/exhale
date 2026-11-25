/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The Android Moods & Genres tiles, drawn with the live catalogue at phone size. */
class MoodsPreviewTest {
    private fun hsb(h: Float, s: Float, b: Float) = Color(java.awt.Color.HSBtoRGB(h / 360f, s, b))

    private fun colors(stripe: Long): Pair<Color, Color> {
        val argb = stripe.toInt()
        val hsv = java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, null)
        val hue = hsv[0] * 360f
        val grey = hsv[1] < 0.12f
        val saturation = if (grey) 0.10f else hsv[1].coerceAtLeast(0.62f).coerceAtMost(0.92f)
        return hsb(hue, saturation * 0.92f, if (grey) 0.46f else 0.80f) to hsb((hue + 14f) % 360f, saturation, if (grey) 0.24f else 0.44f)
    }

    @Composable
    private fun Tile(title: String, stripe: Long, height: Dp, large: Boolean, modifier: Modifier) {
        val (top, bottom) = colors(stripe)
        Box(
            modifier
                .height(height)
                .clip(RoundedCornerShape(if (large) 22.dp else 18.dp))
                .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width * 0.9f))
                },
        ) {
            Text(
                title.substringBefore(' ').substringBefore('&').trim(),
                fontSize = if (large) 92.sp else 70.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.13f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(if (large) Alignment.BottomEnd else Alignment.TopEnd)
                .graphicsLayer {
                    // Away from the label: under it on a large card, above it on a small tile.
                    rotationZ = -14f
                    translationX = size.width * 0.18f
                    translationY = size.height * (if (large) 0.30f else -0.28f)
                },
            )
            Text(
                title,
                fontSize = if (large) 22.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(if (large) Alignment.TopStart else Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun render() = runBlocking {
        val temp = File(System.getProperty("java.io.tmpdir"))
        assumeTrue(File(temp, "icons-old").isDirectory)
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val sections = YouTube.moodAndGenres().getOrThrow()
        val moods = sections[0].items
        val genres = sections[1].items
        val scene = ImageComposeScene(1170, 2400, Density(3f)) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Moods & Genres", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 24.dp))
                Text(sections[0].title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    moods.take(3).forEach { Tile(it.title, it.stripeColor, 188.dp, true, Modifier.width(158.dp)) }
                }
                Text(sections[1].title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 10.dp))
                genres.take(12).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { Tile(it.title, it.stripeColor, 104.dp, false, Modifier.weight(1f)) }
                    }
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        File(File(temp, "render").apply { mkdirs() }, "moods.png").writeBytes(scene.render(4 * 16_000_000L).encodeToData()!!.bytes)
        scene.close()
        Unit
    }
}
