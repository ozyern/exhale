package com.ozyern.exhale.ui.component

import com.ozyern.exhale.lyrics.LyricsEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SingerBadgeNameTest {
    @Test
    fun `matches artists to leads by line count and ignores background lines`() {
        val entries = buildList {
            repeat(4) { add(line("v1")) }
            repeat(2) { add(line("v2")) }
            add(line("v3", isBackground = true))
        }

        val names = resolveSingerNames(entries, listOf(" Alice ", "Bob", "Carol"))

        assertEquals(linkedMapOf("v1" to "Alice", "v2" to "Bob"), names)
    }

    @Test
    fun `does not guess when lead counts are too close`() {
        val entries = listOf(line("v1"), line("v1"), line("v1"), line("v2"), line("v2"))

        assertEquals(emptyMap<String, String>(), resolveSingerNames(entries, listOf("Alice", "Bob")))
    }

    private fun line(agent: String, isBackground: Boolean = false) = LyricsEntry(
        time = 0,
        text = "line",
        agent = agent,
        isBackground = isBackground,
    )
}
