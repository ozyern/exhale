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
 * Compiles the Android app's AGSL background with Skia — the language AGSL is built on — and draws it,
 * so the shader can be checked and looked at without a phone. Reads the shader straight from the source.
 */
class FluidShaderPreviewTest {
    private val side = 48

    private fun shaderSource(): String {
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
    fun render() {
        val out = File(System.getProperty("java.io.tmpdir"), "render").apply { mkdirs() }
        assumeTrue(File(System.getProperty("java.io.tmpdir"), "icons-old").isDirectory) // opt-in, like the other previews
        val effect = RuntimeEffect.makeForShader(shaderSource())
        val image = cover("https://i.ytimg.com/vi/eVli-tstM5E/hq720.jpg")
        val width = 360
        val height = 780
        val strip = Surface.makeRasterN32Premul(width * 3, height)
        for ((index, time) in listOf(0f, 25f, 60f).withIndex()) {
            val builder = RuntimeShaderBuilder(effect)
            val texture = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
            builder.child("cover", texture)
            builder.child("previous", texture)
            builder.uniform("size", width.toFloat(), height.toFloat())
            builder.uniform("time", time)
            builder.uniform("blend", 1f)
            builder.uniform("brightness", 0.9f)
            strip.canvas.save()
            strip.canvas.translate((index * width).toFloat(), 0f)
            // The shader is evaluated in the coordinates of what it is drawn into, as it is on Android.
            strip.canvas.drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), Paint().apply { shader = builder.makeShader() })
            strip.canvas.restore()
        }
        File(out, "fluid.png").writeBytes(strip.makeImageSnapshot().encodeToData()!!.bytes)
    }
}
