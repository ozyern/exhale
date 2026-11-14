package com.ozyern.exhale.api

import org.json.JSONArray

internal fun fitLineCount(lines: List<String>, expectedCount: Int): List<String> =
    List(expectedCount) { index -> lines.getOrElse(index) { "" } }

internal fun parseTranslationContent(content: String): List<String>? {
    parseJsonArray(content)?.let { return it }

    val cleaned = content.replace("```json", "").replace("```", "").trim()
    parseJsonArray(cleaned)?.let { return it }

    val extractedArray = extractJsonArray(cleaned) ?: return null
    parseJsonArray(extractedArray)?.let { return it }

    return parsePlainLines(cleaned)
}

internal fun extractJsonArray(content: String): String? {
    val start = content.indexOf('[')
    val end = content.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return content.substring(start, end + 1)
}

internal fun parsePlainLines(content: String): List<String> = content.lines()
    .filter { it.trim().isNotEmpty() }
    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }

internal fun parseJsonArray(content: String): List<String>? = runCatching {
    val array = JSONArray(content)
    (0 until array.length()).map { array.optString(it) }
}.getOrNull()
