package com.ozyern.exhale.core.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws a rounded star inside the smaller dimension; it does not stretch to fill a rectangle. */
class RoundedStarShape(
    private val sides: Int,
    private val curve: Double = 0.09,
    private val rotation: Float = 0f,
    iterations: Int = 360,
) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val pointCount = iterations.coerceIn(1, 360)
    private val angleStep = TWO_PI / pointCount
    private val rotationDegree = (PI / 180) * rotation

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {
        val radius = min(size.height, size.width) * 0.4 * (1.0 - curve / 2.0)
        val xCenter = size.width * .5f
        val yCenter = size.height * .5f

        fun addPoint(index: Int) {
            val angle = index * angleStep
            val curveScale = 1.0 + curve * cos(sides * angle)
            val x = radius * cos(angle - rotationDegree) * curveScale + xCenter
            val y = radius * sin(angle - rotationDegree) * curveScale + yCenter

            if (index == 0) moveTo(x.toFloat(), y.toFloat())
            else lineTo(x.toFloat(), y.toFloat())
        }

        for (index in 0 until pointCount) addPoint(index)
        close()
    })
}
