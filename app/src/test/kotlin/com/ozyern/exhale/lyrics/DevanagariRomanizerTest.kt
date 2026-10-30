package com.ozyern.exhale.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevanagariRomanizerTest {
    private fun r(text: String) = DevanagariRomanizer.romanize(text)

    @Test
    fun finalInherentVowelIsNotSaid() {
        assertEquals("Dil", r("दिल"))
        assertEquals("Ham", r("हम"))
        // A word that is only the one sound keeps it.
        assertEquals("Na", r("न"))
    }

    @Test
    fun medialInherentVowelDropsBetweenVowels() {
        assertEquals("Karna", r("करना"))
        assertEquals("Apna", r("अपना"))
        assertEquals("Kamal", r("कमल"))
        // ड with a combining nukta, and the precomposed ड़: the same word either way.
        assertEquals("Dhadkan", r("धड़कन"))
        assertEquals("Dhadkan", r("धड़कन"))
    }

    @Test
    fun longVowelsAreDoubledExceptAtTheEnd() {
        assertEquals("Mera dil", r("मेरा दिल"))
        assertEquals("Pyaar", r("प्यार"))
        assertEquals("Nahin", r("नहीं"))
    }

    @Test
    fun nasalsAndConjuncts() {
        assertEquals("Ambar", r("अंबर"))
        assertEquals("Gyaan", r("ज्ञान"))
        // After a nasal-closed syllable the vowel stays: three consonants never pile up.
        assertEquals("Zindagi", r("ज़िंदगी"))
    }

    @Test
    fun otherScriptsPassThroughAndDandasGo() {
        assertEquals("I love you meri jaan", r("I love you मेरी जान"))
        assertEquals("Sapne", r("सपने।"))
    }

    @Test
    fun detection() {
        assertTrue(DevanagariRomanizer.isDevanagari("तुम ही हो"))
        assertFalse(DevanagariRomanizer.isDevanagari("tum hi ho"))
    }
}
