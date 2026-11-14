package com.ozyern.exhale.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlingProjectionTest {

    @Test
    fun `zero velocity projects nowhere`() {
        assertEquals(0f, projectMomentum(0f), 0.001f)
        assertEquals(50f, projectedEndpoint(50f, 0f), 0.001f)
    }

    @Test
    fun `apple decay factor at default rate`() {
        // (v/1000) * 0.998/0.002 = v * 0.499
        assertEquals(998f, projectMomentum(2000f), 1f)
        assertEquals(-499f, projectMomentum(-1000f), 1f)
    }

    @Test
    fun `snappier rate travels shorter`() {
        val normal = projectMomentum(2000f, 0.998f)
        val snappy = projectMomentum(2000f, 0.99f)
        assertEquals(198f, snappy, 1f)
        assert(normal > snappy)
    }

    @Test
    fun `flick throws past nearest release point`() {
        // At 0, snaps [0, 500]: release point says 0, fast flick says 500.
        assertEquals(500f, nearestProjectedSnap(0f, 2000f, listOf(0f, 500f))!!, 0.001f)
        assertEquals(0f, nearestProjectedSnap(0f, 0f, listOf(0f, 500f))!!, 0.001f)
    }

    @Test
    fun `empty snaps return null`() {
        assertNull(nearestProjectedSnap(0f, 1000f, emptyList()))
    }
}
