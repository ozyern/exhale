package com.ozyern.exhale.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsJudgeTest {
    @Test
    fun creditsAloneAreNotASong() {
        assertFalse(LyricsJudge.isSong(listOf("Written and composed:", "Warner Chappell Music", "JohnSmith/JaneDoe/AlexRay", "© 2024")))
        assertTrue(LyricsJudge.isSong(listOf("one line", "two lines", "three lines", "four lines")))
    }

    @Test
    fun aLooseAnswerHasToSoundLikeTheTitle() {
        // The real case: "Ass Like That" answered with "Lose Yourself", which says "like" and "that".
        val other = "his palms are sweaty knees weak arms are heavy, like that, there's vomit on his sweater"
        assertFalse(LyricsJudge.soundsLikeTitle(other, "Ass Like That"))
        assertTrue(LyricsJudge.soundsLikeTitle("she wants that ass like that all night", "Ass Like That"))
        assertTrue(LyricsJudge.soundsLikeTitle("you say my name, oh espresso", "Espresso"))
    }

    @Test
    fun missingTheNameIsOnlyDetectedForDistinctiveTitles() {
        assertFalse(LyricsJudge.echoesTitle("nothing of the sort is sung here", "Espresso"))
        assertTrue(LyricsJudge.echoesTitle("that's that me espresso", "Espresso"))
        // Nothing in this title is distinctive enough to look for.
        assertTrue(LyricsJudge.echoesTitle("nothing of the sort is sung here", "Just Like That"))
    }

    @Test
    fun theAnswerNobodyBacksUpIsTheOddOneOut() {
        val song = LyricsJudge.vocabulary("the river runs silver under the morning light carry over water tonight")
        val again = LyricsJudge.vocabulary("the river runs silver under morning light carry over the water")
        val other = LyricsJudge.vocabulary("neon signals flicker downtown taxis humming restless sound pocket quarters")
        assertEquals(setOf(0, 1), LyricsJudge.agreeing(mapOf(0 to song, 1 to again, 2 to other)))
        // With nobody agreeing, nobody can be called the odd one out.
        assertTrue(LyricsJudge.agreeing(mapOf(0 to song, 2 to other)).isEmpty())
    }

    @Test
    fun driftIsTheMedianGapAcrossSharedLines() {
        val saved = "[00:10.00]first line here\n[00:15.00]second line here\n[00:20.00]third line here\n[00:25.00]fourth line here\n[00:30.00]fifth line here"
        val fresh = "[00:12.00]first line here\n[00:17.00]second line here\n[00:22.00]third line here\n[00:27.00]fourth line here\n[00:90.00]fifth line here"
        // One line far off does not move the verdict.
        assertEquals(2_000L, LyricsWords.timingDriftMs(saved, fresh))
        assertEquals(-2_000L, LyricsWords.timingDriftMs(fresh, saved))
        assertEquals(0L, LyricsWords.timingDriftMs(saved, saved))
    }

    @Test
    fun driftIsUnknownWithTooLittleToCompare() {
        val timed = "[00:10.00]first line here\n[00:15.00]second line here\n[00:20.00]third line here\n[00:25.00]fourth line here"
        assertNull(LyricsWords.timingDriftMs(timed, "first line here\nsecond line here"))
        assertNull(LyricsWords.timingDriftMs(timed, "[00:10.00]something else\n[00:15.00]entirely different"))
    }
}
