package com.ozyern.exhale.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumPagePaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test
    fun `hsl round trip`() {
        val color = Color(0xFF7B6CC9)
        val (h, s, l) = color.toHsl()
        val back = hslColor(h, s, l)
        assertEquals(color.red, back.red, 0.01f)
        assertEquals(color.green, back.green, 0.01f)
        assertEquals(color.blue, back.blue, 0.01f)
    }

    @Test
    fun `page is deep and keeps the record's hue`() {
        val purple = 0x7B6CC9
        val palette = albumPagePalette(purple)
        val seedHue = Color(0xFF7B6CC9).toHsl().first
        assertEquals(0.13f, palette.background.toHsl().third, 0.01f)
        assertEquals(seedHue, palette.background.toHsl().first, 2f)
        assertEquals(seedHue, palette.onBackgroundVariant.toHsl().first, 2f)
        assertEquals(0.80f, palette.onBackgroundVariant.toHsl().third, 0.01f)
        assertEquals(Color.White, palette.onBackground)
    }

    @Test
    fun `white text and the pale tint both read on the page`() {
        for (seed in listOf(0x7B6CC9, 0xE8C547, 0x1E88E5, 0xD32F2F, 0x111111, 0xFAFAFA)) {
            val palette = albumPagePalette(seed)
            assertTrue("white on ${seed.toString(16)}", contrast(Color.White, palette.background) >= 7f)
            assertTrue("tint on ${seed.toString(16)}", contrast(palette.onBackgroundVariant, palette.background) >= 4.5f)
        }
    }

    @Test
    fun `a grey record stays grey`() {
        val palette = albumPagePalette(0x808080)
        assertTrue(palette.background.toHsl().second < 0.12f)
        assertTrue(palette.onBackgroundVariant.toHsl().second < 0.12f)
    }
}
