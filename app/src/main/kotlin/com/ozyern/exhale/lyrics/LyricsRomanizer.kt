/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/**
 * A lyric line in any script, in Latin letters - for the scripts that have no engine of their own.
 *
 * Japanese and Korean keep their dedicated romanizers (readings and liaison a generic table cannot
 * know), and Hindi keeps [DevanagariRomanizer], which writes it the way people type it. Everything
 * else - Chinese as pinyin, Russian and the other Cyrillic languages, Greek, Arabic, Hebrew, Thai,
 * Tamil, Bengali, Punjabi and the rest - goes through ICU's transliterator, with the accents taken
 * off afterwards so it reads like something a person would type rather than a dictionary entry.
 */
object LyricsRomanizer {

    /** Whether the line has letters in a script other than Latin. */
    fun hasOtherScript(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp)) {
                val script = Character.UnicodeScript.of(cp)
                if (script != Character.UnicodeScript.LATIN &&
                    script != Character.UnicodeScript.COMMON &&
                    script != Character.UnicodeScript.INHERITED
                ) return true
            }
            i += Character.charCount(cp)
        }
        return false
    }

    private val transliterator by lazy {
        runCatching { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }

    /**
     * The line in Latin letters, or null when it already is or nothing can be made of it. Hindi goes
     * to [DevanagariRomanizer]; Japanese and Korean are expected to have been handled before this.
     */
    fun romanize(text: String): String? {
        if (DevanagariRomanizer.isDevanagari(text)) return DevanagariRomanizer.romanize(text)
        if (!hasOtherScript(text)) return null
        val t = transliterator ?: return null
        // An ICU transliterator is not safe to share between threads mid-call.
        val latin = synchronized(t) { runCatching { t.transliterate(text) }.getOrNull() } ?: return null
        val tidy = latin.replace(Regex("\\s{2,}"), " ").trim()
        return tidy.takeIf { it.isNotEmpty() && it != text.trim() }?.replaceFirstChar { it.uppercaseChar() }
    }
}
