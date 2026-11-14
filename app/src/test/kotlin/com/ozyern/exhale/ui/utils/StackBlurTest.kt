package com.ozyern.exhale.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The blur that crashed: the horizontal pass used a running counter where it needed the
 * row's fixed base offset, so its window reads walked diagonally into the next row and
 * off the end of the array (`length=2304; index=2305` on a 48x48 source).
 */
class StackBlurTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun green(p: Int) = (p shr 8) and 0xFF
    private fun blue(p: Int) = p and 0xFF
    private fun alpha(p: Int) = (p ushr 24) and 0xFF

    @Test
    fun `does not run off the end of the buffer`() {
        // Square and non-square, radii from tiny to larger than the image. 48x48 is the
        // exact size the crash report came from.
        val sizes = listOf(48 to 48, 64 to 36, 1 to 1, 2 to 7, 160 to 160, 5 to 3)
        for ((w, h) in sizes) {
            for (radius in listOf(1, 2, 5, 40)) {
                val pixels = IntArray(w * h) { argb(255, it % 256, (it * 7) % 256, (it * 13) % 256) }
                // Fails the test rather than the process if the indexing regresses.
                stackBlurPixels(pixels, w, h, radius)
                assertEquals("size must not change ($w x $h, r=$radius)", w * h, pixels.size)
            }
        }
    }

    @Test
    fun `a uniform image survives the blur unchanged`() {
        // The strongest statement available without reimplementing the filter: blurring a
        // flat colour must return that colour. The old diagonal walk failed this, because
        // the window sampled the wrong row.
        val w = 32
        val h = 32
        val flat = argb(255, 120, 60, 200)
        val pixels = IntArray(w * h) { flat }
        stackBlurPixels(pixels, w, h, radius = 4)

        for (p in pixels) {
            // Integer division by the window size loses at most a unit per pass.
            assertTrue("red $p", abs(red(p) - 120) <= 3)
            assertTrue("green $p", abs(green(p) - 60) <= 3)
            assertTrue("blue $p", abs(blue(p) - 200) <= 3)
            assertEquals("alpha must be preserved", 255, alpha(p))
        }
    }

    @Test
    fun `blurring reduces contrast between neighbouring rows`() {
        // Horizontal stripes: after the vertical passes the hard black/white step must
        // have softened. Catches a blur that silently does nothing.
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { index ->
            if ((index / w) % 2 == 0) argb(255, 0, 0, 0) else argb(255, 255, 255, 255)
        }
        stackBlurPixels(pixels, w, h, radius = 3)
        val spread = pixels.maxOf { red(it) } - pixels.minOf { red(it) }
        assertTrue("stripes should have averaged toward grey, spread=$spread", spread < 200)
    }

    @Test
    fun `radius below one is a no-op`() {
        val original = IntArray(9) { argb(255, it * 20, it * 10, it * 5) }
        val pixels = original.copyOf()
        stackBlurPixels(pixels, 3, 3, radius = 0)
        assertTrue(original.contentEquals(pixels))
    }
}
