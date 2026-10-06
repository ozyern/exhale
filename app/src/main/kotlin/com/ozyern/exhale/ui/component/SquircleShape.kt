/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The corner geometry is ported from Morphlet (github.com/rit3zh/morphlet, MorphletSquirclePath):
 *
 *   MIT License
 *   Copyright (c) 2026 rit3zh
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 *   and associated documentation files (the "Software"), to deal in the Software without
 *   restriction, including without limitation the rights to use, copy, modify, merge, publish,
 *   distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *   Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all copies or
 *   substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 *   BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 *   DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * A rounded rectangle with continuous corners — the curvature eases into the straight edge instead
 * of meeting it at a hard tangent, which is why an iOS card's corner looks smooth where a circular
 * one looks stamped on.
 */
class SquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        return Outline.Generic(squirclePath(0f, 0f, size.width, size.height, r))
    }
}

private const val CONTINUOUS_CORNER_EXTENT = 1.52866483f

/** The continuous-corner outline of the rectangle [left], [top], [width] x [height]. */
fun squirclePath(left: Float, top: Float, width: Float, height: Float, cornerRadius: Float): Path {
    val w = max(width, 0.01f)
    val h = max(height, 0.01f)
    val budget = min(w, h) / 2
    val r = min(max(cornerRadius, 0.001f), budget / CONTINUOUS_CORNER_EXTENT)
    // Apple's continuous corner, as ten points along one corner from the top edge to the side.
    val shape = floatArrayOf(
        -CONTINUOUS_CORNER_EXTENT * r, 0f,
        -1.08849323f * r, 0f,
        -0.86840689f * r, 0f,
        -0.63149399f * r, 0.07491100f * r,
        -0.37282392f * r, 0.16906013f * r,
        -0.16906013f * r, 0.37282392f * r,
        -0.07491100f * r, 0.63149399f * r,
        0f, 0.86840689f * r,
        0f, 1.08849323f * r,
        0f, CONTINUOUS_CORNER_EXTENT * r,
    )
    val path = Path()
    fun corner(cx: Float, cy: Float, e1x: Float, e1y: Float, e2x: Float, e2y: Float, first: Boolean) {
        fun x(i: Int) = left + cx + e1x * shape[i * 2] + e2x * shape[i * 2 + 1]
        fun y(i: Int) = top + cy + e1y * shape[i * 2] + e2y * shape[i * 2 + 1]
        if (first) path.moveTo(x(0), y(0)) else path.lineTo(x(0), y(0))
        path.cubicTo(x(1), y(1), x(2), y(2), x(3), y(3))
        path.cubicTo(x(4), y(4), x(5), y(5), x(6), y(6))
        path.cubicTo(x(7), y(7), x(8), y(8), x(9), y(9))
    }
    corner(w, 0f, 1f, 0f, 0f, 1f, first = true)
    corner(w, h, 0f, 1f, -1f, 0f, first = false)
    corner(0f, h, -1f, 0f, 0f, -1f, first = false)
    corner(0f, 0f, 0f, -1f, 1f, 0f, first = false)
    path.close()
    return path
}

/**
 * A spring described the way SwiftUI and Morphlet describe one: [response] is roughly how long it
 * takes in seconds, [dampingFraction] how much it bounces (1 = not at all). Returned as Compose's
 * stiffness and damping ratio.
 */
fun swiftSpring(response: Float, dampingFraction: Float): Pair<Float, Float> {
    val period = max(response, 0.01f)
    val stiffness = (2f * PI.toFloat() / period).let { it * it }
    return stiffness to dampingFraction
}
