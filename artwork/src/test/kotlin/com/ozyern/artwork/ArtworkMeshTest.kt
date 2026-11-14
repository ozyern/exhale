package com.ozyern.artwork

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class ArtworkMeshTest {
    @Test
    fun preservesOriginalAverageForDetailContrast() {
        val palette = ArtworkMeshPalette.fromPixels(IntArray(9) { 0xFF806040.toInt() }, 3, 3)
        assertEquals(128 / 255f, palette.sourceAverage.red, .0001f)
        assertEquals(96 / 255f, palette.sourceAverage.green, .0001f)
        assertEquals(64 / 255f, palette.sourceAverage.blue, .0001f)
        assertNotEquals(palette.sourceAverage, palette.average)
    }
    @Test
    fun samplesAllNineArtworkRegionsAndPreservesGrey() {
        val grey = 0xFF777777.toInt()
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), grey, grey, grey, grey, grey, grey)
        val palette = ArtworkMeshPalette.fromPixels(pixels, 3, 3)
        assertEquals(MeshColor(1f, 0f, 0f), palette.cells[0])
        assertEquals(MeshColor(0f, 1f, 0f), palette.cells[1])
        assertEquals(MeshColor(0f, 0f, 1f), palette.cells[2])
        assertEquals(MeshColor(119 / 255f, 119 / 255f, 119 / 255f), palette.cells[4])
    }

    @Test
    fun monochromeArtworkStaysUniformAndOpaqueAtEveryPhase() {
        val colour = 0xFF777777.toInt()
        val palette = ArtworkMeshPalette.fromPixels(IntArray(9) { colour }, 3, 3)
        val mesh = ArtworkMesh(12, 28)
        val pixels = IntArray(mesh.width * mesh.height)
        for (phase in listOf(0f, 1f, 12f, ArtworkMesh.PhasePeriod, Float.NaN)) {
            mesh.render(palette, phase, pixels)
            assertTrue(pixels.all { (it ushr 24) == 255 })
            assertTrue(pixels.all { abs((it and 255) - 119) <= 1 })
        }
    }

    @Test
    fun phaseWrapHasNoVisibleColourJump() {
        val mesh = ArtworkMesh()
        val before = IntArray(mesh.width * mesh.height)
        val after = IntArray(before.size)
        mesh.render(ArtworkMeshPalette.Fallback, ArtworkMesh.PhasePeriod - .00001f, before)
        mesh.render(ArtworkMeshPalette.Fallback, 0f, after)
        for (index in before.indices) for (channel in listOf(0, 8, 16)) {
            assertTrue(abs(((before[index] ushr channel) and 255) - ((after[index] ushr channel) and 255)) <= 1)
        }
    }

    @Test
    fun onePixelArtworkAndOnePixelOutputRemainValid() {
        val palette = ArtworkMeshPalette.fromPixels(intArrayOf(0xFFFF0000.toInt()), 1, 1)
        val output = IntArray(1)
        ArtworkMesh(1, 1).render(palette, 0f, output)
        assertEquals(0xFFFF0000.toInt(), output[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnIncompletePixelBuffer() {
        ArtworkMeshPalette.fromPixels(IntArray(2), 3, 3)
    }
}
