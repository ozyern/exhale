/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.foundation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.shapes.ContinuousCapsule
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle

/*
 * iOS shapes.
 *
 * The squircle itself comes from the app's own [ContinuousRoundedRectangle] — a real
 * G2-continuous curve, and already the shape 40-odd screens are drawn with. This file
 * only names the iOS-specific cases on top of it.
 */

/** The capsule SwiftUI reaches for on pills, switches and glass buttons. */
val IosCapsule: Shape = ContinuousCapsule()

/** SwiftUI's Circle(). */
val IosCircle: Shape = CircleShape

/** A section's first row: rounded on top, square joins at the bottom. */
fun TopRoundedShape(radius: Dp): Shape =
    ContinuousRoundedRectangle(topStart = radius, topEnd = radius)

/** A section's last row: square joins on top, rounded at the bottom. */
fun BottomRoundedShape(radius: Dp): Shape =
    ContinuousRoundedRectangle(bottomEnd = radius, bottomStart = radius)

/**
 * SwiftUI's ConcentricRectangle — a rounded rect whose radius follows the container
 * it nests in, so the gap between the two edges stays even all the way round.
 *
 * @param uniform SwiftUI's .uniform variant: every corner keeps the container radius
 *   instead of shrinking with the inset.
 */
fun ConcentricRectangle(containerRadius: Dp, inset: Dp, uniform: Boolean = false): Shape =
    ContinuousRoundedRectangle(
        if (uniform) containerRadius else (containerRadius - inset).coerceAtLeast(0.dp)
    )

/**
 * SwiftUI's ContainerRelativeShape — the radius a container hands down to its
 * children, so nested cards stay concentric.
 */
@Immutable
data class IosContainerShape(val radius: Dp) {
    val shape: Shape get() = ContinuousRoundedRectangle(radius)

    /** The shape a child inset by [inset] should take to stay concentric. */
    fun relative(inset: Dp): Shape = ConcentricRectangle(radius, inset)
}
