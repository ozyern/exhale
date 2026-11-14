package com.ozyern.exhale.ui.player

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class NowPlayingGradientTest {
    private fun luminance(argb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    @Test fun `bright and saturated covers retain readable white text`() {
        val extreme = intArrayOf(-1, 0xFF000000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(),
            0xFFFF00FF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        for (color in extreme) for (hero in listOf(false, true)) {
            val gradient = nowPlayingGradient(IntArray(64) { color }, 8, 8, hero)
            for (stop in listOf(gradient.top, gradient.bottom)) {
                assertTrue("Insufficient contrast for $color, hero=$hero", 1.05 / (luminance(stop) + 0.05) >= 4.5)
                assertEquals(255, stop ushr 24)
            }
        }
    }

    @Test fun `monochrome cover stays neutral`() {
        val result = nowPlayingGradient(IntArray(4) { -1 }, 2, 2, true)
        for (stop in listOf(result.top, result.bottom)) {
            assertEquals((stop shr 16) and 255, (stop shr 8) and 255)
            assertEquals((stop shr 8) and 255, stop and 255)
        }
    }

    @Test fun `hero derives both stops from the artwork foot`() {
        val pixels = IntArray(100) { if (it < 50) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        val hero = nowPlayingGradient(pixels, 10, 10, true)
        val square = nowPlayingGradient(pixels, 10, 10, false)
        assertEquals(0, (hero.top shr 16) and 255)
        assertTrue((hero.top and 255) > 0)
        assertTrue(((square.top shr 16) and 255) > 0)
        assertEquals(0, square.top and 255)
    }

    @Test(expected = IllegalArgumentException::class) fun `invalid dimensions are rejected`() {
        nowPlayingGradient(IntArray(1), 2, 2, false)
    }
}
