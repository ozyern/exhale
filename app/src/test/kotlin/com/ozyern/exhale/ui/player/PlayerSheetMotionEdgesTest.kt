package com.ozyern.exhale.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Extreme inputs must rest, never poison the flight with NaN: a torn drag read
 * or a divide-by-zero used to propagate NaN into every lerp downstream and
 * draw nothing for the whole gesture.
 */
class PlayerSheetMotionEdgesTest {

    private val mini = Rect(16f, 900f, 400f, 964f)
    private val full = Rect(0f, 0f, 1080f, 2400f)

    @Test
    fun `easings clamp non-finite to rest`() {
        assertEquals(0f, easeInCubic(Float.NaN), 0.001f)
        assertEquals(0f, easeOutCubic(Float.NaN), 0.001f)
        assertEquals(1f, easeInCubic(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(1f, easeOutCubic(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(0f, easeInCubic(Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(0f, easeOutCubic(Float.NEGATIVE_INFINITY), 0.001f)
    }

    @Test
    fun `container treats nan and inf as rest and clamp`() {
        val rest = sharedContainerRect(mini, full, 0f)
        val nan = sharedContainerRect(mini, full, Float.NaN)
        assertEquals(rest.left, nan.left, 0.01f)
        assertEquals(rest.width, nan.width, 0.01f)
        val inf = sharedContainerRect(mini, full, Float.POSITIVE_INFINITY)
        val end = sharedContainerRect(mini, full, 1f)
        assertEquals(end.width, inf.width, 0.01f)
    }

    @Test
    fun `artwork keeps aspect and rests on nan`() {
        val art = Rect(24f, 900f, 72f, 964f)
        val target = Rect(140f, 300f, 940f, 1100f)
        val nan = sharedArtworkRect(art, target, Float.NaN)
        assertEquals(art.center.x, nan.center.x, 0.01f)
        assertEquals(art.width / art.height, nan.width / nan.height, 0.001f)
    }

    @Test
    fun `corner radius rests on nan progress and nan radii`() {
        assertEquals(
            sharedContainerCornerRadius(24f, 48f, 0f, 1f),
            sharedContainerCornerRadius(24f, 48f, Float.NaN, 1f),
            0.01f,
        )
        assertEquals(
            0f,
            sharedContainerCornerRadius(Float.NaN, Float.NaN, 0.5f, Float.NaN),
            0.01f,
        )
    }

    @Test
    fun `input owner never returns null state on nan`() {
        assertEquals(
            PlayerInputOwner.MINI,
            playerSheetInputOwner(false, false, false, Float.NaN, false),
        )
        assertEquals(
            PlayerInputOwner.NONE,
            playerSheetInputOwner(true, false, false, Float.NaN, false),
        )
    }
}
