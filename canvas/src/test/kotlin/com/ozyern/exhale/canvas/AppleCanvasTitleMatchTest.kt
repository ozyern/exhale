package com.ozyern.exhale.canvas

import com.ozyern.exhale.applecanvas.AppleMusicCanvasProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppleCanvasTitleMatchTest {
    private fun base(title: String) = AppleMusicCanvasProvider.baseTitle(title)

    @Test
    fun aSongThatOnlyContainsAnotherTitleIsADifferentSong() {
        assertNotEquals(base("Nonsense"), base("A Nonsense Christmas"))
    }

    @Test
    fun tagsAfterTheTitleDoNotMakeADifferentSong() {
        assertEquals(base("Nonsense"), base("Nonsense (Remastered)"))
        assertEquals(base("Nonsense"), base("Nonsense [Deluxe]"))
        assertEquals(base("Come and Get Your Love"), base("Come and Get Your Love - Single Version"))
    }

    @Test
    fun caseAndPunctuationAreIgnored() {
        assertEquals(base("Don't Smile"), base("don’t smile"))
    }
}
