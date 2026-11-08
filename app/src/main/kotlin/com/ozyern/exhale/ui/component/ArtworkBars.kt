/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs
import kotlin.math.max

/**
 * Cuts the bars a video thumbnail carries baked into its pixels: a portrait cover pillarboxed in
 * grey, a 16:9 frame letterboxed in black. Shown as they were, the player's banner, the bleed down
 * the screen and the blur behind it were all built from two flat stripes and a picture in between.
 *
 * Only bars that look like a player put them there are cut, so a cover designed with a border keeps
 * it: on both sides, the same width within a few percent, the same flat colour, and dark or grey.
 * A white or coloured margin is somebody's design and is left alone.
 */
object TrimVideoBars : Transformation() {
    override val cacheKey: String = "trimVideoBars-2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < MIN_SIDE || h < MIN_SIDE) return input

        val left = barDepth(input, horizontal = true, fromStart = true)
        val right = barDepth(input, horizontal = true, fromStart = false)
        val top = barDepth(input, horizontal = false, fromStart = true)
        val bottom = barDepth(input, horizontal = false, fromStart = false)

        val cutX = pairedBars(left, right, w, input, horizontal = true)
        val cutY = pairedBars(top, bottom, h, input, horizontal = false)
        if (!cutX && !cutY) return input

        val x = if (cutX) left else 0
        val y = if (cutY) top else 0
        val cw = w - x - (if (cutX) right else 0)
        val ch = h - y - (if (cutY) bottom else 0)
        if (cw < w * MIN_KEPT || ch < h * MIN_KEPT) return input
        return Bitmap.createBitmap(input, x, y, cw, ch)
    }

    /** True when [a] and [b] are a matching pair of player bars along one axis of [length]. */
    private fun pairedBars(a: Int, b: Int, length: Int, bitmap: Bitmap, horizontal: Boolean): Boolean {
        val minBar = length * MIN_BAR
        if (a < minBar || b < minBar) return false
        if (abs(a - b) > length * SYMMETRY) return false
        // Same colour each side, and a colour a player would use.
        val first = if (horizontal) bitmap.getPixel(a / 2, bitmap.height / 2) else bitmap.getPixel(bitmap.width / 2, a / 2)
        val second = if (horizontal) {
            bitmap.getPixel(bitmap.width - 1 - b / 2, bitmap.height / 2)
        } else {
            bitmap.getPixel(bitmap.width / 2, bitmap.height - 1 - b / 2)
        }
        return close(first, second) && isPlayerBarColour(first)
    }

    /** How many rows or columns in from one edge are a single flat colour. */
    private fun barDepth(bitmap: Bitmap, horizontal: Boolean, fromStart: Boolean): Int {
        val length = if (horizontal) bitmap.width else bitmap.height
        val across = if (horizontal) bitmap.height else bitmap.width
        val limit = (length * MAX_BAR).toInt()
        val reference = pixelAt(bitmap, horizontal, if (fromStart) 0 else length - 1, across / 2)
        var depth = 0
        while (depth < limit) {
            val line = if (fromStart) depth else length - 1 - depth
            if (!isFlatLine(bitmap, horizontal, line, across, reference)) break
            depth++
        }
        return depth
    }

    /**
     * Only the middle of the line is looked at: a thumbnail can be padded on two axes at once (grey
     * at the sides, black along the foot), and the far ends of a side bar run into the other bar.
     */
    private fun isFlatLine(bitmap: Bitmap, horizontal: Boolean, line: Int, across: Int, reference: Int): Boolean {
        val end = (across * (1f - EDGE_SKIP)).toInt()
        val step = max(1, (end - across * EDGE_SKIP).toInt() / SAMPLES)
        var i = (across * EDGE_SKIP).toInt()
        while (i < end) {
            if (!close(pixelAt(bitmap, horizontal, line, i), reference)) return false
            i += step
        }
        return true
    }

    private fun pixelAt(bitmap: Bitmap, horizontal: Boolean, line: Int, offset: Int): Int =
        if (horizontal) bitmap.getPixel(line, offset) else bitmap.getPixel(offset, line)

    private fun close(a: Int, b: Int): Boolean =
        abs(red(a) - red(b)) <= TOLERANCE && abs(green(a) - green(b)) <= TOLERANCE && abs(blue(a) - blue(b)) <= TOLERANCE

    /** Black or a neutral grey, never white or a hue: what a video player pads with. */
    private fun isPlayerBarColour(c: Int): Boolean {
        val r = red(c)
        val g = green(c)
        val b = blue(c)
        val spread = maxOf(r, g, b) - minOf(r, g, b)
        return spread <= GREY_SPREAD && maxOf(r, g, b) <= DARKEST_LIGHT
    }

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    private const val MIN_SIDE = 64

    /** How much of each end of a line [isFlatLine] leaves out. */
    private const val EDGE_SKIP = 0.2f
    private const val SAMPLES = 48
    private const val TOLERANCE = 14
    private const val GREY_SPREAD = 24
    private const val DARKEST_LIGHT = 170

    /** Bars thinner than this are a frame's own edge, not padding. */
    private const val MIN_BAR = 0.06f

    /** No bar is wider than this; past it the "bar" is the picture. */
    private const val MAX_BAR = 0.35f

    /** How unequal the two bars of a pair may be. */
    private const val SYMMETRY = 0.03f

    /** Never keep less than this much of either side. */
    private const val MIN_KEPT = 0.4f
}
