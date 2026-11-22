/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.ui.lyricsflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins how stored lyrics with backing vocals become the panel's lines. */
class FlowLinesTest {

    @Test
    fun `a backing vocal sung against its lead line rides under it`() {
        val lines = toFlowLines(
            """
            [00:10.00]Leave me with nothing when I come down
            [00:11.00]{bg}(come down)
            [00:20.00]Next line
            """.trimIndent(),
        )
        assertEquals(listOf(10_000L, 20_000L), lines.map { it.timeMs })
        assertEquals("(come down)", lines[0].background?.text)
    }

    @Test
    fun `a backing vocal after its lead line has finished is a line of its own`() {
        val lines = toFlowLines(
            """
            [00:10.00]Short
            [00:16.00]{bg}(ooh yeah)
            [00:20.00]Next line
            """.trimIndent(),
        )
        assertEquals(listOf(10_000L, 16_000L, 20_000L), lines.map { it.timeMs })
        assertNull(lines[0].background)
        assertEquals("(ooh yeah)", lines[1].text)
    }

    @Test
    fun `a second backing vocal on one line is kept, not dropped`() {
        val lines = toFlowLines(
            """
            [00:10.00]A long line that is sung over a good few seconds here
            [00:10.50]{bg}(first)
            [00:11.00]{bg}(second)
            [00:20.00]Next line
            """.trimIndent(),
        )
        assertEquals("(first)", lines.first { it.timeMs == 10_000L }.background?.text)
        assertEquals("(second)", lines.single { it.timeMs == 11_000L }.text)
    }

    @Test
    fun `a backing vocal before the first line stands alone`() {
        val lines = toFlowLines(
            """
            [00:02.00]{bg}(intro)
            [00:10.00]First line
            """.trimIndent(),
        )
        assertEquals(listOf("(intro)", "First line"), lines.map { it.text })
    }

    @Test
    fun `plain parenthesised lines are ordinary lines`() {
        val lines = toFlowLines(
            """
            [00:10.00]Lead
            [00:15.00](xyz)
            """.trimIndent(),
        )
        assertEquals(listOf("Lead", "(xyz)"), lines.map { it.text })
    }
}
