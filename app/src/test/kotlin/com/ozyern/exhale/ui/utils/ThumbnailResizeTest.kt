package com.ozyern.exhale.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks that artwork URLs request a size appropriate for their display bounds. */
class ThumbnailResizeTest {

    private val ytimg = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"

    @Test
    fun `list and grid art asks above the old hqdefault floor`() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
            ytimg.resize(544, 544),
        )
    }

    @Test
    fun `player art still asks for maxres`() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ytimg.resize(1200, 1200),
        )
    }

    @Test
    fun `small art does not over-fetch`() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", ytimg.resize(96, 96))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", ytimg.resize(256, 256))
    }

    @Test
    fun `height alone still picks a rung`() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ytimg.resize(height = 1200),
        )
    }

    @Test
    fun `no dimensions leaves the url alone`() {
        assertEquals(ytimg, ytimg.resize())
    }

    @Test
    fun `google artwork updates dimensions without changing its format`() {
        val url = "https://lh3.googleusercontent.com/a-/image=w100-h100-p-l90-rj"

        assertEquals(
            "https://lh3.googleusercontent.com/a-/image=w200-h300-p-l90-rj",
            url.resize(200, 300),
        )
    }

    @Test
    fun `google avatar replaces its existing size`() {
        val url = "https://yt3.ggpht.com/a-/image=s120-c-k-c0x00ffffff-no-rj"

        assertEquals(
            "https://yt3.ggpht.com/a-/image=s200",
            url.resize(200, 200),
        )
    }

    @Test
    fun `apple artwork uses the larger requested dimension`() {
        val url = "https://is1-ssl.mzstatic.com/image/thumb/Music116/100x100bb.webp"

        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music116/300x300bb.jpg",
            url.resize(200, 300),
        )
    }
}
