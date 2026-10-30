/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/**
 * Hindi (Devanagari) lyrics in Latin letters, the way people type Hindi: "mera dil", "dhadkan",
 * "nahin" - not the scholarly transliteration with its macrons ("merā dil"), which nobody reads.
 *
 * Hindi drops most inherent "a" sounds that the script writes: at the end of a word ("दिल" is
 * "dil", not "dila"), and in the middle wherever a vowel sits on both sides ("धड़कन" is "dhadkan").
 * Both are handled by the usual rule, applied right to left. Text in any other script passes
 * through untouched, so a line mixing Hindi and English keeps its English.
 */
object DevanagariRomanizer {

    fun isDevanagari(text: String): Boolean = text.any { it in 'ऀ'..'ॿ' }

    fun romanize(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] in 'ऀ'..'ॿ') {
                var end = i
                while (end < text.length && text[end] in 'ऀ'..'ॿ') end++
                out.append(romanizeWord(text.substring(i, end)))
                i = end
            } else {
                out.append(text[i])
                i++
            }
        }
        // Dandas leave double spaces behind; a line reads as a line, capitalised.
        val line = out.toString().replace(Regex(" {2,}"), " ").trim()
        return line.replaceFirstChar { it.uppercaseChar() }
    }

    private class Syllable(
        val consonants: String,
        /** Single consonant, so its inherent vowel may drop; a conjunct's never does. */
        val simple: Boolean,
        val vowel: Char?,
        val inherent: Boolean,
    ) {
        var coda: Char? = null
        var dropped = false
    }

    private fun romanizeWord(word: String): String {
        val syllables = mutableListOf<Syllable>()
        val plain = StringBuilder()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            when {
                consonantOf(word, i) != null -> {
                    val cluster = StringBuilder()
                    var count = 0
                    var j = i
                    while (true) {
                        val (latin, width) = consonantOf(word, j) ?: break
                        // ज्ञ is "gy" as it is said, not the "jn" its letters spell.
                        if (cluster.endsWith("j") && latin == "n" && word[j] == 'ञ') {
                            cluster.setLength(cluster.length - 1)
                            cluster.append("gy")
                        } else {
                            cluster.append(latin)
                        }
                        count++
                        j += width
                        if (j < word.length && word[j] == VIRAMA && j + 1 < word.length && consonantOf(word, j + 1) != null) {
                            j++
                        } else {
                            break
                        }
                    }
                    val next = word.getOrNull(j)
                    when {
                        next == VIRAMA -> {
                            syllables += Syllable(cluster.toString(), count == 1, null, inherent = false)
                            j++
                        }
                        next != null && next in MATRAS -> {
                            syllables += Syllable(cluster.toString(), count == 1, next, inherent = false)
                            j++
                        }
                        else -> syllables += Syllable(cluster.toString(), count == 1, null, inherent = true)
                    }
                    i = j
                }
                c in VOWELS -> {
                    syllables += Syllable("", false, c, inherent = false)
                    i++
                }
                c == ANUSVARA || c == CHANDRABINDU || c == VISARGA -> {
                    syllables.lastOrNull()?.coda = c
                    i++
                }
                c in '०'..'९' -> {
                    plain.append('0' + (c - '०'))
                    i++
                }
                c == 'ॐ' -> {
                    plain.append("om")
                    i++
                }
                // Dandas, nukta strays, accents: nothing to say aloud.
                else -> i++
            }
        }
        if (syllables.isEmpty()) return plain.toString()

        // The final inherent vowel is not said, unless the word is that one sound or ends in a conjunct.
        val last = syllables.last()
        if (syllables.size > 1 && last.inherent && last.simple && last.coda == null) last.dropped = true
        // Medial: a vowel on both sides, never two in a row, and not after a closed syllable, where
        // dropping it would pile three consonants together ("zindagi", not "zindgi").
        var k = syllables.size - 2
        while (k >= 1) {
            val s = syllables[k]
            val before = syllables[k - 1]
            val after = syllables[k + 1]
            if (s.inherent && s.simple && s.coda == null && before.coda == null && !before.dropped && !after.dropped &&
                (before.inherent || before.vowel != null) && (after.inherent || after.vowel != null)
            ) {
                s.dropped = true
                k -= 2
            } else {
                k--
            }
        }

        val out = StringBuilder()
        syllables.forEachIndexed { index, s ->
            val final = index == syllables.lastIndex
            out.append(s.consonants)
            when {
                s.inherent && !s.dropped -> out.append('a')
                s.vowel != null -> out.append(vowelLatin(s.vowel, final))
            }
            s.coda?.let { coda ->
                out.append(
                    when (coda) {
                        VISARGA -> "h"
                        // Before p, b or m the nasal is an m: "ambar", not "anbar".
                        else -> if (syllables.getOrNull(index + 1)?.consonants?.firstOrNull() in LABIALS) "m" else "n"
                    },
                )
            }
        }
        return out.append(plain).toString()
    }

    /** The consonant at [i] in Latin letters and how many chars it takes (a trailing nukta included). */
    private fun consonantOf(word: String, i: Int): Pair<String, Int>? {
        val c = word.getOrNull(i) ?: return null
        NUKTA_FORMS[c]?.let { return it to 1 }
        val base = CONSONANTS[c] ?: return null
        if (word.getOrNull(i + 1) == NUKTA) {
            NUKTA_OF[c]?.let { return it to 2 }
            return base to 2
        }
        return base to 1
    }

    private fun vowelLatin(v: Char, final: Boolean): String = when (v) {
        'अ' -> "a"
        'आ', 'ा' -> if (final) "a" else "aa"
        'इ', 'ि' -> "i"
        'ई', 'ी' -> if (final) "i" else "ee"
        'उ', 'ु' -> "u"
        'ऊ', 'ू' -> if (final) "u" else "oo"
        'ऋ', 'ृ' -> "ri"
        'ए', 'े', 'ॅ', 'ऍ' -> "e"
        'ऐ', 'ै' -> "ai"
        'ओ', 'ो', 'ॉ', 'ऑ' -> "o"
        'औ', 'ौ' -> "au"
        else -> ""
    }

    private const val VIRAMA = '्'
    private const val NUKTA = '़'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'
    private val LABIALS = setOf('p', 'b', 'm', 'f')

    private val MATRAS = setOf('ा', 'ि', 'ी', 'ु', 'ू', 'ृ', 'े', 'ै', 'ो', 'ौ', 'ॅ', 'ॉ')
    private val VOWELS = setOf('अ', 'आ', 'इ', 'ई', 'उ', 'ऊ', 'ऋ', 'ए', 'ऐ', 'ओ', 'औ', 'ऍ', 'ऑ')

    private val CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
    )

    /** A base letter with a nukta after it: the Urdu sounds Hindi borrows. */
    private val NUKTA_OF = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "gh", 'ज' to "z",
        'ड' to "d", 'ढ' to "dh", 'फ' to "f", 'य' to "y",
    )

    /** The same sounds as single precomposed letters. */
    private val NUKTA_FORMS = mapOf(
        'क़' to "q", 'ख़' to "kh", 'ग़' to "gh", 'ज़' to "z",
        'ड़' to "d", 'ढ़' to "dh", 'फ़' to "f", 'य़' to "y",
    )
}
