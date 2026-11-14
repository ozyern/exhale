package com.ozyern.exhale.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** Crop math behind bakeRamp's centre-crop (pure JVM, no android.graphics). */
class BakeRampTest {
    @Test
    fun `wide source crops sides to square`() {
        assertEquals(90 to 90, cropSize(160, 90, 1f))
    }

    @Test
    fun `square source crops height to 4-3`() {
        assertEquals(160 to 120, cropSize(160, 160, 4f / 3f))
    }

    @Test
    fun `tall source keeps width`() {
        assertEquals(90 to 90, cropSize(90, 160, 1f))
    }
}
