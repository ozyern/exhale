package com.ozyern.exhale.ui.component.floatingtabbar

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the measured mini-player stagger: which axis leads, how long each waits, and that
 * both land exactly on the target (an overshooting curve must come back to 1).
 */
class AxisStaggerSpecTest {
    // Inline slot -> expanded slot is a pure translation+resize; any rects will do.
    private val from = Rect(100f, 100f, 300f, 150f)
    private val to = Rect(20f, 20f, 380f, 70f)

    @Test
    fun `collapse moves x first and holds y for 67ms`() {
        val early = BarMotion.CollapseAccessory.rectAt(50f, from, to)
        assertTrue("x has started", early.left != from.left)
        assertEquals("y still waiting", from.top, early.top, 1e-4f)
    }

    @Test
    fun `expand moves y first and holds x for 33ms`() {
        val early = BarMotion.ExpandAccessory.rectAt(20f, from, to)
        assertTrue("y has started", early.top != from.top)
        assertEquals("x still waiting", from.left, early.left, 1e-4f)
    }

    @Test
    fun `both specs land exactly on the target`() {
        for (spec in listOf(BarMotion.CollapseAccessory, BarMotion.ExpandAccessory)) {
            val end = spec.rectAt(spec.durationMs.toFloat(), from, to)
            assertEquals(to.left, end.left, 1e-3f)
            assertEquals(to.top, end.top, 1e-3f)
            assertEquals(to.right, end.right, 1e-3f)
            assertEquals(to.bottom, end.bottom, 1e-3f)
        }
    }

    @Test
    fun `spring easing overshoots visibly and ends exactly on 1`() {
        for ((easing, minPeak) in listOf(BarMotion.ExpandSpring to 1.08f, BarMotion.CollapseSpring to 1.06f)) {
            val peak = (0..100).maxOf { easing.transform(it / 100f) }
            assertTrue("peak $peak", peak > minPeak && peak < 1.2f)
            assertEquals(0f, easing.transform(0f), 1e-6f)
            assertEquals(1f, easing.transform(1f), 1e-6f)
            assertEquals("settled before the end", 1f, easing.transform(0.97f), 0.01f)
        }
    }

    @Test
    fun `only the body overshoots - the centre never passes the target`() {
        for (spec in listOf(BarMotion.CollapseAccessory, BarMotion.ExpandAccessory, BarMotion.surface(470, BarMotion.ExpandSpring))) {
            var widthPeak = 0f
            for (ms in 0..spec.durationMs step 2) {
                val r = spec.rectAt(ms.toFloat(), from, to)
                // Centre travels monotonically from -> to, never past it.
                assertTrue("centre x ${r.center.x} past target", r.center.x >= to.center.x - 1e-3f)
                assertTrue("centre y ${r.center.y} past target", r.center.y >= to.center.y - 1e-3f)
                widthPeak = maxOf(widthPeak, r.width)
            }
            assertTrue("width swells past ${to.width}: $widthPeak", widthPeak > to.width + 1f)
        }
        val critical = CriticalEasing()
        assertTrue((0..100).all { critical.transform(it / 100f) <= 1f + 1e-6f })
        assertEquals(1f, critical.transform(1f), 1e-6f)
    }

    @Test
    fun `accessory overshoot passes the target then settles`() {
        val peak = (0..340 step 4).maxOf { BarMotion.CollapseAccessory.rectAt(it.toFloat(), from, to).let { r -> from.left - r.left } }
        assertTrue("overshoots left travel of ${from.left - to.left}", peak > from.left - to.left)
    }
}
