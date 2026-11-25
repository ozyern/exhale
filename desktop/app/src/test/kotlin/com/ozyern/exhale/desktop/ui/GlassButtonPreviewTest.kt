/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Android play/pause glass, recipe for recipe, drawn over a real cover so it can be judged by eye.
 * Four states: playing and paused, each at rest and held.
 */
class GlassButtonPreviewTest {
    @Composable
    private fun GlassDrop(backdrop: Backdrop, playing: Boolean, press: Float, size: Dp = 56.dp) {
        val accent = Color(0xFF7FA8FF)
        val stain = if (playing) 1f else 0f
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val swell = 1f + 0.12f * press
                    scaleX = swell
                    scaleY = swell
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(0.5f.dp.toPx() + 1.5f.dp.toPx() * press)
                        lens(
                            (size.toPx() * 0.24f) * (0.85f + 0.15f * press),
                            (size.toPx() * 0.50f) * (0.85f + 0.15f * press),
                            true,
                            press > 0.02f,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.75f + 0.25f * press) },
                    innerShadow = {
                        InnerShadow(radius = 3f.dp + 4f.dp * press, color = Color.Black.copy(alpha = 0.18f), alpha = 0.5f + 0.2f * press)
                    },
                    shadow = { Shadow(radius = 10f.dp + 6f.dp * press, color = Color.Black.copy(alpha = 0.40f)) },
                    onDrawSurface = {
                        drawRect(accent.copy(alpha = 0.06f + 0.14f * stain))
                        // Held, it brightens — light gathering in the drop — rather than darkening.
                        drawRect(Color.White.copy(alpha = 0.10f * press))
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.14f + 0.16f * press), Color.Transparent),
                                center = Offset(this.size.width * 0.5f, this.size.height * 0.08f),
                                radius = this.size.width * 0.50f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                                center = Offset(this.size.width * 0.5f, this.size.height * 1.02f),
                                radius = this.size.width * 0.45f,
                            ),
                        )
                    },
                ),
        ) {
            Canvas(Modifier.size(size * 0.46f)) {
                val w = this.size.width
                if (playing) {
                    drawRoundRect(Color.White, Offset(w * 0.18f, w * 0.1f), Size(w * 0.22f, w * 0.8f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
                    drawRoundRect(Color.White, Offset(w * 0.60f, w * 0.1f), Size(w * 0.22f, w * 0.8f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
                } else {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.24f, w * 0.1f); lineTo(w * 0.9f, w * 0.5f); lineTo(w * 0.24f, w * 0.9f); close()
                    }
                    drawPath(p, Color.White)
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun render() {
        val temp = File(System.getProperty("java.io.tmpdir"))
        assumeTrue(File(temp, "icons-old").isDirectory)
        val bytes = OkHttpClient().newCall(Request.Builder().url("https://i.ytimg.com/vi/eVli-tstM5E/hq720.jpg").build()).execute().use { it.body.bytes() }
        val cover = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        val scene = ImageComposeScene(1200, 360, Density(3f)) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                }
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassDrop(backdrop, playing = true, press = 0f)
                    GlassDrop(backdrop, playing = true, press = 1f)
                    GlassDrop(backdrop, playing = false, press = 0f)
                    GlassDrop(backdrop, playing = false, press = 1f)
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        File(File(temp, "render").apply { mkdirs() }, "glass-button.png").writeBytes(scene.render(4 * 16_000_000L).encodeToData()!!.bytes)
        scene.close()
    }
}
