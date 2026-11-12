/*
 * Ported from AETHER (github.com/GHOSTxASR/AETHER, GPL-3.0): its cold-start film, a logo under a
 * swelling liquid lens. The optical model there is adapted from Travis Fischer's MIT-licensed
 * recreation of the liquid-lens effect. Adapted for Exhale: its own mark and wordmark, and its
 * reduced-motion setting.
 */

package com.ozyern.exhale.ui.component.lensboot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.ozyern.exhale.R
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Characters drawn once into a bitmap, white on transparent, for shaders to pick from. Measured,
 * not assumed: each glyph's ink is counted, so the ramp runs from emptiest to fullest in the font
 * actually used, and each glyph's shape is sampled under six staggered circles (two columns of
 * three), the way the boot film's type floor matches glyphs to a logo's edges.
 */
class GlyphAtlas private constructor(
    val bitmap: Bitmap,
    val glyphWidth: Int,
    val glyphHeight: Int,
    val chars: String,
    /** Ink per glyph, 0..1, in [chars] order. */
    val coverage: FloatArray,
    /** Six circle samples per glyph, normalised per circle over the set. */
    val shapes: Array<FloatArray>,
) {
    val count: Int get() = chars.length

    companion object {
        /** The ramp ASCII artwork is drawn with: enough steps for tone, few enough to stay legible. */
        const val RAMP_CANDIDATES = " .`',:;-~_^\"=+*<>!?|/\\()[]{}1ilrcxzvunjtfosaekhdbpqwmgyXZUJCLQ0OYEKHDBPRAMW#%&$@"

        /** The boot film's charset (after the reference export). */
        const val TYPE_CHARSET = " .'`,:;-_~^\"=+*<>!|/\\()[]{}#%@&$"

        val CIRCLES = arrayOf(
            floatArrayOf(0.3f, 0.22f), floatArrayOf(0.7f, 0.16f),
            floatArrayOf(0.3f, 0.52f), floatArrayOf(0.7f, 0.46f),
            floatArrayOf(0.3f, 0.82f), floatArrayOf(0.7f, 0.76f),
        )
        const val CIRCLE_RADIUS = 0.2f

        private val cache = HashMap<String, GlyphAtlas>()

        fun typeface(context: Context, bold: Boolean): Typeface {
            // The phone's own monospace: the film only needs glyphs of one width to decode through.
            val base = Typeface.MONOSPACE
            return Typeface.create(base, if (bold) 700 else 400, false)
        }

        /**
         * [steps] glyphs from [candidates], evenly spread over the ink they cover, emptiest first.
         * Built once per configuration and kept.
         */
        @Synchronized
        fun ramp(context: Context, steps: Int = 18, candidates: String = RAMP_CANDIDATES, glyphWidth: Int = 24, glyphHeight: Int = 40): GlyphAtlas {
            val key = "ramp|$steps|$candidates|$glyphWidth|$glyphHeight"
            cache[key]?.let { return it }
            val all = build(typeface(context, bold = false), candidates.toSet().joinToString(""), glyphWidth, glyphHeight)
            val order = all.chars.indices.sortedBy { all.coverage[it] }
            val max = all.coverage.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val picked = ArrayList<Int>()
            for (s in 0 until steps) {
                val target = max * s / (steps - 1)
                val best = order.minByOrNull { kotlin.math.abs(all.coverage[it] - target) } ?: continue
                if (best !in picked) picked += best
            }
            val chars = picked.sortedBy { all.coverage[it] }.map { all.chars[it] }.joinToString("")
            return build(typeface(context, bold = false), chars, glyphWidth, glyphHeight).also { cache[key] = it }
        }

        /** Every char of [charset] in its own cell, one row, measured. */
        @Synchronized
        fun charset(context: Context, charset: String, bold: Boolean = true, glyphWidth: Int = 48, glyphHeight: Int = 80): GlyphAtlas {
            val key = "set|$charset|$bold|$glyphWidth|$glyphHeight"
            cache[key]?.let { return it }
            return build(typeface(context, bold), charset, glyphWidth, glyphHeight).also { cache[key] = it }
        }

        private fun build(typeface: Typeface, chars: String, w: Int, h: Int): GlyphAtlas {
            val bitmap = Bitmap.createBitmap(w * chars.length, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.typeface = typeface
                textAlign = Paint.Align.CENTER
                textSize = h * 0.8f
            }
            val metrics = paint.fontMetrics
            val baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f
            chars.forEachIndexed { i, ch ->
                canvas.save()
                canvas.clipRect(i * w, 0, (i + 1) * w, h)
                canvas.drawText(ch.toString(), i * w + w / 2f, baseline, paint)
                canvas.restore()
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val coverage = FloatArray(chars.length)
            val raw = Array(chars.length) { FloatArray(6) }
            for (i in chars.indices) {
                var ink = 0f
                for (y in 0 until h) for (x in 0 until w) ink += (pixels[y * bitmap.width + i * w + x] ushr 24) / 255f
                coverage[i] = ink / (w * h)
                val r = CIRCLE_RADIUS * w
                for (k in 0 until 6) {
                    val px = CIRCLES[k][0] * w
                    val py = CIRCLES[k][1] * h
                    var sum = 0f
                    var n = 0
                    for (y in floor(py - r).toInt()..ceil(py + r).toInt()) for (x in floor(px - r).toInt()..ceil(px + r).toInt()) {
                        if (x < 0 || y < 0 || x >= w || y >= h) continue
                        if ((x - px) * (x - px) + (y - py) * (y - py) > r * r) continue
                        sum += (pixels[y * bitmap.width + i * w + x] ushr 24) / 255f
                        n++
                    }
                    raw[i][k] = if (n > 0) sum / n else 0f
                }
            }
            for (k in 0 until 6) {
                val m = raw.maxOf { it[k] }.coerceAtLeast(1e-6f)
                for (i in chars.indices) raw[i][k] /= m
            }
            return GlyphAtlas(bitmap, w, h, chars, coverage, raw)
        }
    }
}
