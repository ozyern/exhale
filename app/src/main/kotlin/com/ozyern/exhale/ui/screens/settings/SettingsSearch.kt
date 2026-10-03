/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

fun filterQuickActions(
    actions: List<SettingsQuickAction>,
    query: String,
): List<SettingsQuickAction> {
    if (query.isBlank()) return actions
    return actions.filter { it.label.contains(query, ignoreCase = true) }
}

fun filterSettingsGroups(
    groups: List<SettingsGroup>,
    query: String,
): List<SettingsGroup> {
    if (query.isBlank()) return groups
    return groups.mapNotNull { group ->
        val filtered = group.items
            .map { it to searchScore(it, query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
        if (filtered.isEmpty()) null else group.copy(items = filtered)
    }
}

fun matchesQuery(
    item: SettingsItem,
    query: String,
): Boolean = searchScore(item, query) > 0

/**
 * How well [item] answers [query], 0 when it doesn't.
 *
 * Every word typed has to be found, in any order: in the title (best), a word of the keywords or
 * of the page and section line, or through a synonym ("vibration" finds Haptics). A word matches
 * the start of a word, anywhere inside a longer one, or — past three letters — with one letter
 * wrong, so "crosfade" and "equaliser" still land.
 */
fun searchScore(item: SettingsItem, query: String): Int {
    val tokens = searchWords(query)
    if (tokens.isEmpty()) return 0
    val title = searchWords(item.title)
    val rest = searchWords(
        listOfNotNull(item.subtitle, item.badge).joinToString(" ") + " " + item.keywords.joinToString(" "),
    )
    var total = 0
    for (token in tokens) {
        val alternatives = listOf(token) + SettingsSynonyms[token].orEmpty().flatMap { searchWords(it) }
        val best = alternatives.maxOf { word ->
            val synonym = if (word == token) 0 else 8
            maxOf(wordScore(word, title) * 2, wordScore(word, rest)) - synonym
        }
        if (best <= 0) return 0
        total += best
    }
    // A title that reads like what was typed comes first.
    if (item.title.lowercase().startsWith(query.trim().lowercase())) total += 40
    return total
}

private fun searchWords(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}.]+")).filter { it.isNotBlank() }

private fun wordScore(token: String, words: List<String>): Int {
    var best = 0
    for (word in words) {
        val score = when {
            word == token -> 30
            word.startsWith(token) -> 24
            token.length >= 3 && word.contains(token) -> 14
            token.length >= 4 && withinOneEdit(token, word.take(token.length + 1)) -> 10
            token.length >= 4 && withinOneEdit(token, word) -> 10
            else -> 0
        }
        if (score > best) best = score
    }
    return best
}

/** True when [a] becomes [b] with at most one letter added, dropped or changed. */
private fun withinOneEdit(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            i++; j++
            continue
        }
        if (++edits > 1) return false
        when {
            a.length > b.length -> i++
            a.length < b.length -> j++
            else -> { i++; j++ }
        }
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}

fun filterInternalItems(
    items: List<SettingsItem>,
    query: String,
): List<SettingsItem> {
    if (query.isBlank()) return emptyList()
    return items
        .map { it to searchScore(it, query) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(30)
        .map { it.first }
}

fun filterIntegrations(
    integrations: List<SettingsIntegrationAction>,
    query: String,
): List<SettingsIntegrationAction> {
    if (query.isBlank()) return integrations
    return integrations.filter { it.label.contains(query, ignoreCase = true) }
}
