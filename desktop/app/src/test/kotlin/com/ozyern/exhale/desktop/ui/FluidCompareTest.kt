/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Windows background beside the Android one, from the same cover at the same moment, so the gap
 * between them can be seen rather than guessed at. The Windows column is its CPU field written out as
 * a shader; the Android column is the app's own AGSL, read from the source.
 */
class FluidCompareTest {
    private val side = 48

    /** The desktop's renderField, as a shader: four layers, one octave of warp, saturation, vignette. */
    private val desktopAgsl = """
uniform shader cover;
uniform shader previous;
uniform float2 size;
uniform float time;
uniform float blend;
uniform float brightness;

half4 main(float2 fragCoord) {
    float aspect = size.x / size.y;
    float2 p = (fragCoord / size - 0.5) * float2(aspect, 1.0);
    half3 acc = half3(0.0);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float dir = (i == 1 || i == 3) ? -1.0 : 1.0;
        float a = time * (0.045 + 0.02 * fi) * dir + fi * 1.9;
        float c = cos(a);
        float s = sin(a);
        float scale = 1.0 / (1.35 + 0.45 * fi);
        float2 q = float2(c * p.x - s * p.y, s * p.x + c * p.y) * scale;
        float u = q.x + 0.10 * sin(time * 0.17 + fi * 2.3 + q.y * 4.0) + 0.5 + sin(time * 0.05 + fi) * 0.18;
        float v = q.y + 0.10 * cos(time * 0.13 + fi * 1.1 + q.x * 4.0) + 0.5 + cos(time * 0.04 + fi * 1.7) * 0.18;
        float2 uv = clamp(float2(u, v), 0.02, 0.98) * $side.0;
        half w = (i == 0) ? 1.6 : 1.0;
        acc += cover.eval(uv).rgb * w;
    }
    half3 col = acc / 4.6;
    half luma = dot(col, half3(0.299, 0.587, 0.114));
    col = max(half3(luma) + (col - half3(luma)) * 1.45, half3(0.0));
    float vignette = 1.0 - smoothstep(0.2, 1.3, length(p));
    col *= half(brightness * (0.62 + 0.38 * vignette));
    return half4(col, 1.0);
}
"""

    private fun androidAgsl(): String {
        val file = File(System.getProperty("user.dir")).parentFile
            .resolve("app/src/main/kotlin/com/ozyern/exhale/ui/component/FluidArtworkBackground.kt")
        val text = file.readText()
        val start = text.indexOf("private const val FluidAgsl = \"\"\"") + "private const val FluidAgsl = \"\"\"".length
        return text.substring(start, text.indexOf("\"\"\"", start)).replace("\$Side", side.toString())
    }

    private fun cover(url: String): Image {
        val bytes = OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
        val source = Image.makeFromEncoded(bytes)
        val bitmap = Bitmap().apply { allocPixels(ImageInfo(side, side, ColorType.RGBA_8888, ColorAlphaType.PREMUL)) }
        Canvas(bitmap).apply { drawImageRect(source, Rect.makeWH(side.toFloat(), side.toFloat())) }.close()
        return Image.makeFromBitmap(bitmap)
    }

    @Test
    fun compare() {
        val out = File(System.getProperty("java.io.tmpdir"), "render").apply { mkdirs() }
        assumeTrue(File(System.getProperty("java.io.tmpdir"), "icons-old").isDirectory)
        val image = cover("https://i.ytimg.com/vi/eVli-tstM5E/hq720.jpg")
        val width = 300
        val height = 640
        val times = listOf(0f, 30f)
        val columns = times.size * 2
        val strip = Surface.makeRasterN32Premul(width * columns, height)
        var column = 0
        for (source in listOf(desktopAgsl, androidAgsl())) {
            val effect = RuntimeEffect.makeForShader(source)
            for (time in times) {
                val builder = RuntimeShaderBuilder(effect)
                val texture = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
                builder.child("cover", texture)
                builder.child("previous", texture)
                builder.uniform("size", width.toFloat(), height.toFloat())
                builder.uniform("time", time)
                builder.uniform("blend", 1f)
                builder.uniform("brightness", 0.9f)
                strip.canvas.save()
                strip.canvas.translate((column * width).toFloat(), 0f)
                strip.canvas.drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), Paint().apply { shader = builder.makeShader() })
                strip.canvas.restore()
                column++
            }
        }
        File(out, "fluid-compare.png").writeBytes(strip.makeImageSnapshot().encodeToData()!!.bytes)
    }
}
