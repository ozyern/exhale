/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The back button's glass, before and after, over a settings page washed with album-art colour. */
class BackButtonPreviewTest {
    /** rememberChromeGlassModifier, recipe for recipe, with the two numbers that change. */
    private fun Modifier.chrome(backdrop: Backdrop, tintAlpha: Float, blurRadius: Dp) = drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx() * 0.25f)
            lens(14f.dp.toPx(), 28f.dp.toPx(), true)
        },
        highlight = { Highlight.Default },
        shadow = { Shadow(radius = 14f.dp, color = Color.Black.copy(alpha = 0.45f)) },
        onDrawSurface = { drawRect(Color.Black.copy(alpha = tintAlpha)) },
    )

    @Composable
    private fun Arrow(chevron: Boolean) {
        Canvas(Modifier.size(if (chevron) 22.dp else 20.dp)) {
            val u = size.minDimension / 24f
            val stroke = Stroke(width = (if (chevron) 2.5f else 2.2f) * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val p = androidx.compose.ui.graphics.Path()
            if (chevron) {
                p.moveTo(14.6f * u, 5.2f * u); p.lineTo(7.8f * u, 12f * u); p.lineTo(14.6f * u, 18.8f * u)
            } else {
                p.moveTo(20f * u, 12f * u); p.lineTo(5f * u, 12f * u)
                p.moveTo(11f * u, 6f * u); p.lineTo(5f * u, 12f * u); p.lineTo(11f * u, 18f * u)
            }
            drawPath(p, Color.White, style = stroke)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun render() {
        val temp = File(System.getProperty("java.io.tmpdir"))
        assumeTrue(File(temp, "icons-old").isDirectory)
        val bytes = OkHttpClient().newCall(Request.Builder().url("https://i.ytimg.com/vi/eVli-tstM5E/hq720.jpg").build()).execute().use { it.body.bytes() }
        val cover = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        val scene = ImageComposeScene(1080, 420, Density(3f)) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                // A settings page: near-black ground, the album-art wash, and rows scrolling under the bar.
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF0B0B0D))) {
                    Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.35f))
                    Column(Modifier.padding(top = 44.dp, start = 12.dp, end = 12.dp)) {
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (after in listOf(false, true)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(if (after) 44.dp else 40.dp).chrome(backdrop, if (after) 0.16f else 0.42f, if (after) 20.dp else 56.dp),
                                contentAlignment = Alignment.Center,
                            ) { Arrow(chevron = after) }
                            Text(
                                if (after) "  After" else "  Before",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        File(File(temp, "render").apply { mkdirs() }, "back-button.png").writeBytes(scene.render(4 * 16_000_000L).encodeToData()!!.bytes)
        scene.close()
    }
}
