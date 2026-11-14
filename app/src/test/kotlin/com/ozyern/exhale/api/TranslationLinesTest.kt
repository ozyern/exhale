package com.ozyern.exhale.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationLinesTest {
    @Test
    fun `fitLineCount truncates extra lines and pads missing lines`() {
        assertEquals(listOf("one", "two"), fitLineCount(listOf("one", "two", "extra"), 2))
        assertEquals(listOf("one", ""), fitLineCount(listOf("one"), 2))
    }

    @Test
    fun `fitLineCount keeps lines when count already matches`() {
        assertEquals(listOf("one", "two"), fitLineCount(listOf("one", "two"), 2))
    }

    @Test
    fun `parseTranslationContent accepts JSON arrays inside markdown fences`() {
        assertEquals(listOf("one", "two"), parseTranslationContent("```json\n[\"one\", \"two\"]\n```"))
    }

    @Test
    fun `parseTranslationContent returns null when no lines can be parsed`() {
        assertEquals(null, parseTranslationContent("   "))
    }

    @Test
    fun `parsePlainLines trims quotes and skips blank lines`() {
        assertEquals(listOf("one", "two"), parsePlainLines("  \"one\"  \n\n 'two' "))
    }

    @Test
    fun `extractJsonArray finds the bracketed response`() {
        assertEquals("[\"one\"]", extractJsonArray("result: [\"one\"] done"))
        assertEquals(null, extractJsonArray("no array"))
    }
}
