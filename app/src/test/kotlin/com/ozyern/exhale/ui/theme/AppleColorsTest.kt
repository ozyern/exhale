package com.ozyern.exhale.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Apple API bgColor → page background measured in the 27 colorExtractionTest reference screenshots. */
class AppleColorsTest {

    private val cases = listOf(
        0x9097a8 to 0x5e6476, 0x3b5469 to 0x3c5469, 0x505c62 to 0x3f4951, 0xd8c1c7 to 0x7a626a,
        0xddd2c8 to 0x71655c, 0x8a9ba0 to 0x57666c, 0x592c43 to 0x582b45, 0xc6716e to 0x9c4645,
        0x924728 to 0x803616, 0x0c0d10 to 0x0b0d11, 0x050e07 to 0x060e06, 0xefefef to 0x5f5f5f,
        0x674c3a to 0x674d3a, 0x464646 to 0x464646, 0x000001 to 0x000000, 0x95a4a1 to 0x5a6b67,
        0x3d4232 to 0x3d4233, 0x19181b to 0x1a191c, 0x0b0b0b to 0x0a0a0a, 0x3f5c60 to 0x3f5c60,
        0xfdfdfe to 0x5a5a5a, 0x080b0b to 0x090b0a, 0x080c17 to 0x080b18, 0xb5b39d to 0x6b6a53,
        0x271913 to 0x261914, 0x535453 to 0x535353, 0x0f0604 to 0x100504,
    )

    @Test
    fun `page background is within 2 of 255 per channel on every reference`() {
        for ((appleBg, measured) in cases) {
            val ours = AppleColors.pageBackground(appleBg)
            assertTrue(
                "bg %06x -> %06x, screenshot %06x".format(appleBg, ours, measured),
                AppleColors.accuracy(ours, measured) >= 0.99,
            )
        }
    }

    @Test
    fun `key colour wins the play fill, white otherwise`() {
        assertEquals(0x0198c7, AppleColors.playFill(0x0198c7))
        assertEquals(AppleColors.WHITE, AppleColors.playFill(null))
    }

    @Test
    fun `estimate finds a flat top band`() {
        val px = IntArray(64 * 64) { if (it < 64 * 8) 0x336699 else 0xffffff }
        assertEquals(0x336699, AppleColors.estimateArtworkBackground(px, 64, 64))
    }
}
