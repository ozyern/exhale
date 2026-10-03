/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/**
 * Puts back the words a lyric source masked with asterisks ("f**k", "sh*t", "b***h").
 *
 * Exhale never censors lyrics; some sources hand them over censored. A masked word is matched
 * against the words it could be from its visible letters and its length, case kept, and left as
 * it came when nothing fits exactly.
 */
object Uncensor {
    private val words = listOf(
        "fuck", "fucks", "fucked", "fucker", "fuckers", "fuckin", "fucking", "motherfucker",
        "motherfuckers", "motherfuckin", "motherfucking", "shit", "shits", "shitty", "bullshit",
        "bitch", "bitches", "bitchin", "ass", "asses", "asshole", "assholes", "dick", "dicks",
        "pussy", "cock", "cunt", "damn", "goddamn", "hell", "nigga", "niggas", "nigger", "hoe",
        "hoes", "whore", "whores", "slut", "sluts", "bastard", "bastards", "piss", "pissed",
        "crap", "fag", "faggot", "twat", "wank", "wanker", "tits", "titties", "cum", "dope",
    )

    private val masked = Regex("""[A-Za-z]*\*+[A-Za-z*]*""")

    /** A masked word: letters with asterisks in them, or a run of three or more asterisks alone. */
    fun isCensored(text: String?): Boolean = text != null && masked.findAll(text).any { m ->
        m.value.any { it.isLetter() } || m.value.count { it == '*' } >= 3
    }

    fun restore(text: String?): String? {
        if (text == null || '*' !in text) return text
        return masked.replace(text) { match ->
            val token = match.value
            if (token.none { it.isLetter() }) return@replace token
            val pattern = Regex("^" + token.lowercase().map { if (it == '*') "[a-z]" else Regex.escape(it.toString()) }.joinToString("") + "$")
            val word = words.firstOrNull { pattern.matches(it) } ?: return@replace token
            buildString {
                word.forEachIndexed { i, c ->
                    val original = token.getOrNull(i)
                    append(if (original != null && original.isUpperCase()) c.uppercaseChar() else c)
                }
            }
        }
    }
}
