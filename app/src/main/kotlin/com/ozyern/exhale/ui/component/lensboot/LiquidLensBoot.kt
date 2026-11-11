/*
 * Ported from AETHER (github.com/GHOSTxASR/AETHER, GPL-3.0): its cold-start film, a logo under a
 * swelling liquid lens. The optical model there is adapted from Travis Fischer's MIT-licensed
 * recreation of the liquid-lens effect. Adapted for Exhale: its own mark and wordmark, and its
 * reduced-motion setting.
 */

package com.ozyern.exhale.ui.component.lensboot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import com.ozyern.exhale.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The cold-start sting (the liquid-lens recording and its export): the cross of light under a
 * swelling dome of oil, then AETHER under a long low lens, decoding out of type. [speed] runs the
 * 4-second film faster; a tap skips it; reduced motion shows only its last frame.
 */
@Composable
fun LiquidLensBoot(onFinished: () -> Unit, modifier: Modifier = Modifier, speed: Float = 1.2f) {
    val finish by rememberUpdatedState(onFinished)
    val context = LocalContext.current
    // Exhale's own reading of "no animations": the system's animator scale at zero.
    val reduce = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // No runtime shaders: the mark simply rises out of the dark.
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            alpha.animateTo(1f, tween(900))
            delay(600)
            finish()
        }
        Box(modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(
                text = WORDMARK_TEXT,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.pinyon_script)),
                modifier = Modifier.alpha(alpha.value),
            )
        }
        return
    }
    val kit = remember { BootKit(context.applicationContext) }
    var t by remember { mutableFloatStateOf(if (reduce) LensFilm.DURATION else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            val start = withFrameNanos { it }
            while (t < LensFilm.DURATION) withFrameNanos { now -> t = min(LensFilm.DURATION, (now - start) / 1e9f * speed) }
        }
        delay(if (reduce) 900 else 450)
        finish()
    }
    Canvas(
        modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .pointerInput(Unit) { detectTapGestures { finish() } },
    ) { kit.draw(this, t) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class BootKit(private val context: Context) {
    private val shader = RuntimeShader(LENS_SHADER)
    private val atlas = GlyphAtlas.charset(context, GlyphAtlas.TYPE_CHARSET, bold = true)
    private val atlasShader = BitmapShader(atlas.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private var marks: List<Mark>? = null
    private var marksFor: Size? = null
    private val floor = TypeFloor(atlas)

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, t: Float) = with(scope) {
        if (marks == null || marksFor != size) {
            marks = listOf(Mark.symbol(context, LensFilm.SYMBOL), Mark.wordmark(context, LensFilm.WORDMARK))
            marksFor = size
        }
        val index = if (t >= LensFilm.WORDMARK.start) 1 else 0
        val shot = LensFilm.SHOTS[index]
        val mark = marks!![index]
        val local = t - shot.start
        val f = LensFilm.frame(shot, local)
        val look = shot.look
        val aspect = size.width / size.height
        val half = LensFilm.viewHalf(aspect)
        // The symbol falls away into the cut: its rect shrinks about its centre.
        val k = f.recede
        val rect = floatArrayOf(
            mark.rect[0] + mark.rect[2] * (1 - k) / 2, mark.rect[1] + mark.rect[3] * (1 - k) / 2,
            mark.rect[2] * k, mark.rect[3] * k,
        )
        floor.build(aspect, half, mark, rect)

        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("viewHalf", half)
        shader.setFloatUniform("aspect", aspect)
        shader.setInputShader("markTex", mark.shader)
        shader.setFloatUniform("markRect", rect[0], rect[1], rect[2], rect[3])
        shader.setFloatUniform("markSize", mark.full.width.toFloat(), mark.full.height.toFloat())
        shader.setFloatUniform("ink", 0.93f, 0.947f, 1f)
        shader.setFloatUniform("floorCol", 0.0012f, 0.0014f, 0.002f)
        shader.setFloatUniform("depth", look.depth)
        shader.setFloatUniform("ior", look.ior)
        shader.setFloatUniform("dispersion", f.dispersion)
        shader.setFloatUniform("knee", look.dispersionKnee)
        shader.setFloatUniform("slopeCap", look.maxSlope)
        shader.setFloatUniform("samples", 8f)
        shader.setFloatUniform("reflectAmt", f.reflect)
        shader.setFloatUniform("softbox", look.softbox)
        shader.setFloatUniform("walls", look.horizon)
        shader.setFloatUniform("ringLight", look.ringLight)
        shader.setFloatUniform("ringSpread", look.ringLightWidth)
        shader.setFloatUniform("ball", shot.dome.centerX, shot.dome.centerY, f.ballZ, shot.ballRadius)
        shader.setFloatUniform("domeCenter", shot.dome.centerX, shot.dome.centerY)
        shader.setFloatUniform("dome", f.radius, f.height, f.exponent)
        shader.setFloatUniform("domeAspect", f.aspect)
        shader.setFloatUniform("shapeP", f.crown, f.flat, f.shoulder)
        shader.setFloatUniform("edgeP", f.edge)
        shader.setFloatUniform("wave", f.ring, look.ringWidth, look.ringWavelength, f.ringReach)
        shader.setFloatUniform("wavePhase", f.wavePhase)
        shader.setFloatUniform("ripple", f.ripple, look.rippleWavelength, f.ripplePhase, look.rippleReach)
        shader.setFloatUniform("reveal", look.reveal)
        shader.setFloatUniform("revealSoft", look.revealSoft)
        shader.setFloatUniform("seed", 0f)
        shader.setFloatUniform("typed", 1f)
        shader.setInputShader("typeMap", floor.shader!!)
        shader.setFloatUniform("typeOrigin", floor.originX, floor.originY)
        shader.setFloatUniform("typeCell", floor.cellW, floor.cellH)
        shader.setFloatUniform("typeDims", floor.cols.toFloat(), floor.rows.toFloat())
        shader.setInputShader("atlas", atlasShader)
        shader.setFloatUniform("glyphPx", atlas.glyphWidth.toFloat(), atlas.glyphHeight.toFloat())
        shader.setFloatUniform("resolve", f.resolve)
        shader.setFloatUniform("dots", 0.05f * f.dots)
        shader.setFloatUniform("balance", look.balance[0], look.balance[1], look.balance[2])
        shader.setFloatUniform("vignette", look.vignette)
        shader.setFloatUniform("exposure", look.exposure)
        shader.setFloatUniform("fade", f.fade)
        shader.setFloatUniform("saturation", look.saturation)
        shader.setFloatUniform("blackLevel", look.black)
        drawRect(ShaderBrush(shader))
    }
}

/**
 * A logo for one shot: its picture (white on transparent) at [TEXELS_PER_UNIT], a small copy for
 * reading coverage, and where it sits in the world (its visible ink fitted to the shot's slot).
 */
private class Mark(val full: Bitmap, val rect: FloatArray) {
    val shader = BitmapShader(full, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private var small: Bitmap? = null
    private var smallPixels: IntArray? = null
    private var smallFor = 0f

    /** Ink under world point (x, y), averaged over about [footprint] world units. */
    fun coverage(x: Float, y: Float, footprint: Float): Float {
        val u = (x - rect[0]) / rect[2]
        val v = (y - rect[1]) / rect[3]
        if (u < 0f || v < 0f || u > 1f || v > 1f) return 0f
        if (small == null || smallFor != footprint) prepareSmall(footprint)
        val bmp = small!!
        val px = smallPixels!!
        val fx = u * (bmp.width - 1)
        val fy = (1f - v) * (bmp.height - 1)
        val x0 = floor(fx).toInt().coerceIn(0, bmp.width - 1)
        val y0 = floor(fy).toInt().coerceIn(0, bmp.height - 1)
        val x1 = min(x0 + 1, bmp.width - 1)
        val y1 = min(y0 + 1, bmp.height - 1)
        val ax = fx - x0
        val ay = fy - y0
        fun a(xx: Int, yy: Int) = (px[yy * bmp.width + xx] ushr 24) / 255f
        val top = a(x0, y0) * (1 - ax) + a(x1, y0) * ax
        val bottom = a(x0, y1) * (1 - ax) + a(x1, y1) * ax
        return top * (1 - ay) + bottom * ay
    }

    private fun prepareSmall(footprint: Float) {
        // One texel per footprint, reached by halving so every source texel is averaged in.
        val targetW = max(8, (rect[2] / footprint).roundToInt())
        var b = full
        while (b.width / 2 >= targetW) b = Bitmap.createScaledBitmap(b, b.width / 2, max(1, b.height / 2), true)
        val h = max(1, (targetW.toFloat() * full.height / full.width).roundToInt())
        b = Bitmap.createScaledBitmap(b, targetW, h, true)
        small = b
        smallPixels = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        smallFor = footprint
    }

    companion object {
        const val TEXELS_PER_UNIT = 1400f

        /** Exhale's mark, in white: its own shape, whatever colour the icon pack draws it in. */
        fun symbol(context: Context, shot: LensFilm.Shot): Mark {
            val px = (TEXELS_PER_UNIT * shot.slotH).roundToInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val source = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.splash_logo_gold)
            if (source != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                }
                val side = min(source.width, source.height)
                val from = android.graphics.Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2)
                android.graphics.Canvas(bmp).drawBitmap(source, from, android.graphics.Rect(0, 0, px, px), paint)
            }
            return fit(bmp, shot)
        }

        /** Exhale, signed in the script. */
        fun wordmark(context: Context, shot: LensFilm.Shot): Mark {
            val face = ResourcesCompat.getFont(context, R.font.pinyon_script)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                typeface = face
                // The wordmark as the app signs it (Type.Inscription): the script, letters joined.
                textSize = TEXELS_PER_UNIT * shot.slotH * 0.62f
                letterSpacing = 0f
                textAlign = Paint.Align.CENTER
            }
            val text = WORDMARK_TEXT
            val w = (paint.measureText(text) * 1.08f).roundToInt()
            val h = (paint.textSize * 1.4f).roundToInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val fm = paint.fontMetrics
            c.drawText(text, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, paint)
            return fit(bmp, shot)
        }

        /** Where the whole picture sits so its visible ink fills the shot's slot. */
        private fun fit(bmp: Bitmap, shot: LensFilm.Shot): Mark {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            var minX = bmp.width; var minY = bmp.height; var maxX = -1; var maxY = -1
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                if ((pixels[y * bmp.width + x] ushr 24) > 12) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) { minX = 0; minY = 0; maxX = bmp.width - 1; maxY = bmp.height - 1 }
            val bx = minX.toFloat() / bmp.width
            val by = minY.toFloat() / bmp.height
            val bw = (maxX - minX + 1).toFloat() / bmp.width
            val bh = (maxY - minY + 1).toFloat() / bmp.height
            val aspect = bmp.width.toFloat() / bmp.height
            val inkAspect = bw * aspect / bh
            val inkW = min(shot.slotW, shot.slotH * inkAspect)
            val width = inkW / bw
            val height = width / aspect
            val cx = shot.slotCenterX - (bx + bw / 2 - 0.5f) * width
            val cy = shot.slotCenterY + (by + bh / 2 - 0.5f) * height
            return Mark(bmp, floatArrayOf(cx - width / 2, cy - height / 2, width, height))
        }
    }
}

/**
 * The ASCII floor: one texel per glyph cell over the whole view (plus a margin refraction pulls
 * in). R is the glyph whose six-circle shape best matches the logo under the cell, G whether the
 * cell holds ink (otherwise a faint dot the lens can bend). Rebuilt only when the logo moves.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class TypeFloor(private val atlas: GlyphAtlas) {
    var originX = 0f; var originY = 0f; var cellW = 1f; var cellH = 1f; var cols = 1; var rows = 1
    var shader: BitmapShader? = null
    private var bitmap: Bitmap? = null
    private var key: String? = null
    private val dot = atlas.chars.indexOf('.').coerceAtLeast(0)

    fun build(aspect: Float, half: Float, mark: Mark, rect: FloatArray) {
        cellH = 2f / ROWS
        cellW = cellH * atlas.glyphWidth / atlas.glyphHeight
        val hw = aspect * half + MARGIN
        val hh = half + MARGIN
        originX = floor(-hw / cellW) * cellW
        originY = floor(-hh / cellH) * cellH
        cols = ceil(2 * hw / cellW).toInt() + 2
        rows = ceil(2 * hh / cellH).toInt() + 2
        val k = "$cols|$rows|${rect.joinToString()}|${System.identityHashCode(mark)}"
        if (k == key) return
        key = k
        val empty = (0xFF shl 24) or (dot shl 16)
        val pixels = IntArray(cols * rows) { empty }
        val footprint = 2f * GlyphAtlas.CIRCLE_RADIUS * cellW
        val cx0 = max(0, floor((rect[0] - originX) / cellW).toInt() - 1)
        val cx1 = min(cols - 1, ceil((rect[0] + rect[2] - originX) / cellW).toInt() + 1)
        val cy0 = max(0, floor((rect[1] - originY) / cellH).toInt() - 1)
        val cy1 = min(rows - 1, ceil((rect[1] + rect[3] - originY) / cellH).toInt() + 1)
        val v = FloatArray(6)
        for (cy in cy0..cy1) for (cx in cx0..cx1) {
            val lx = originX + cx * cellW
            val ly = originY + cy * cellH
            var top = 0f
            for (i in 0 until 6) {
                val c = GlyphAtlas.CIRCLES[i]
                v[i] = mark.coverage(lx + c[0] * cellW, ly + (1f - c[1]) * cellH, footprint)
                if (v[i] > top) top = v[i]
            }
            if (top < 0.04f) continue
            val mean = v.sum() / 6f
            // Global contrast: normalise by the largest, raise, scale back.
            for (i in 0 until 6) v[i] = (v[i] / top).pow(CONTRAST) * top
            var best = 0
            var bestD = Float.MAX_VALUE
            for (g in 0 until atlas.count) {
                val s = atlas.shapes[g]
                var d = 0f
                for (i in 0 until 6) { val e = s[i] - v[i]; d += e * e }
                if (d < bestD) { bestD = d; best = g }
            }
            pixels[cy * cols + cx] = (0xFF shl 24) or (best shl 16) or (0xFF shl 8) or (mean * 255).roundToInt().coerceIn(0, 255)
        }
        val bmp = bitmap?.takeIf { it.width == cols && it.height == rows } ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        if (bmp !== bitmap) {
            bitmap = bmp
            shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_NEAREST }
        }
    }

    private companion object {
        const val ROWS = 90f
        const val MARGIN = 0.8f
        const val CONTRAST = 1.6f
    }
}

private const val WORDMARK_TEXT = "Exhale"

/**
 * The lens film as Exhale's cold-start intro: played over the app, already composed beneath it, and
 * dissolved onto it when it ends (or is tapped away) rather than cut.
 */
@Composable
fun LensBootSplash(onFinished: () -> Unit) {
    val fade = remember { Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ended = remember { booleanArrayOf(false) }
    LiquidLensBoot(
        onFinished = {
            if (!ended[0]) {
                ended[0] = true
                scope.launch {
                    fade.animateTo(0f, tween(420))
                    onFinished()
                }
            }
        },
        modifier = Modifier.graphicsLayer { alpha = fade.value },
    )
}
