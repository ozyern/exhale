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
    fun titlesMatchHoweverApostrophesAndAccentsAreTyped() {
        assertTrue(LyricsJudge.soundsLikeTitle("and the hips don’t lie, no fighting", "Hips Don't Lie"))
        assertTrue(LyricsJudge.soundsLikeTitle("titi me pregunto si tengo muchas novias", "Tití Me Preguntó"))
        assertTrue(LyricsJudge.soundsLikeTitle("i'm gonna rap until the day i collapse, 'til then", "'Till I Collapse"))
        assertTrue(LyricsJudge.soundsLikeTitle("i been cleaning out my closet, my closet", "Cleanin' Out My Closet"))
        assertTrue(LyricsJudge.echoesTitle("una cancion de la tortura", "La Tórtura"))
    }

    @Test
    fun aVideoUploadIsTheSongInsideIt() {
        assertEquals("Without Me" to "Eminem", SongQuery.songAndArtist("Eminem - Without Me (Official Music Video)", listOf("EminemVEVO")))
        assertEquals(
            "Hips Don't Lie" to "Shakira",
            SongQuery.songAndArtist("Shakira - Hips Don't Lie (Official 4K Video) ft. Wyclef Jean", listOf("shakiraVEVO")),
        )
        assertEquals(
            "Espresso" to "Sabrina Carpenter",
            SongQuery.songAndArtist("Sabrina Carpenter - Espresso (Official Video)", listOf("SabrinaCarpenterVEVO")),
        )
        // Catalogue songs stay as they are, and a dash that isn't the channel's stays in the title.
        assertEquals("Lose Yourself" to "Eminem", SongQuery.songAndArtist("Lose Yourself", listOf("Eminem")))
        assertEquals("Shakira" to "Shakira", SongQuery.songAndArtist("Shakira", listOf("Shakira - Topic")))
        assertEquals("Part One - Part Two" to "Band", SongQuery.songAndArtist("Part One - Part Two", listOf("Band")))
    }

    @Test
    fun anotherLanguageOrMixIsNotTheSong() {
        assertFalse(SongQuery.sameSong("Despacito", "Luis Fonsi", "Despacito (Versión Portugués)", "Luis Fonsi, Israel Novaes"))
        assertFalse(SongQuery.sameSong("Despacito", "Luis Fonsi", "Despacito (Mandarin Version) [feat. JJ Lin]", "Luis Fonsi"))
        assertFalse(SongQuery.sameSong("Lose Yourself", "Eminem", "Lose Yourself (Mixed)", "Eminem"))
        assertTrue(SongQuery.sameSong("Despacito", "Luis Fonsi", "Despacito", "Luis Fonsi, Daddy Yankee"))
        assertTrue(SongQuery.sameSong("Hips Don't Lie", "Shakira", "Hips Don’t Lie (feat. Wyclef Jean)", "Shakira"))
        // Asked for by name, a version is the song.
        assertTrue(SongQuery.sameSong("Despacito (Remix)", "Luis Fonsi", "Despacito (feat. Justin Bieber) [Remix]", "Luis Fonsi"))
    }

    @Test
    fun whatNobodySingsIsTakenOutOfTimedLyrics() {
        val lrc = "[00:00.10]Shakira - Waka Waka\n[00:01.00]Rihanna：\n[00:02.00]You're a good soldier\n[00:04.00]Choosing your battles\n[00:06.00]作词 : Someone"
        assertEquals(
            "[00:02.00]You're a good soldier\n[00:04.00]Choosing your battles",
            LyricsJudge.withoutPaperwork(lrc, "Waka Waka"),
        )
        // A first line that only happens to say the title is sung.
        val sung = "[00:01.00]Stan is what they call me\n[00:03.00]Second line"
        assertEquals(sung, LyricsJudge.withoutPaperwork(sung, "Stan"))
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

    @Test
    fun aPerformanceIsReadFromItsTitleTag() {
        assertTrue(LyricsJudge.isOneOff("Espresso/Bed Chem (Live from the BRITs)"))
        assertTrue(LyricsJudge.isOneOff("Espresso / Bed Chem"))
        assertTrue(LyricsJudge.isOneOff("Please Please Please - Acoustic"))
        assertTrue(LyricsJudge.isOneOff("Taste | Live at Wembley"))
        // A word in the name, not a tag after it.
        assertFalse(LyricsJudge.isOneOff("Live Forever"))
        assertFalse(LyricsJudge.isOneOff("AC/DC"))
        assertFalse(LyricsJudge.isOneOff("Espresso"))
    }

    @Test
    fun studioTimingsMustCoverMostOfAPerformance() {
        // Timed lines running to 2:30, against a 5:13 recording.
        val studio = (0 until 30).joinToString("\n") { "[%02d:%02d.00]line number $it".format(it * 5 / 60, it * 5 % 60) }
        assertTrue(LyricsFit.fits(studio, 313, "Espresso"))
        assertFalse(LyricsFit.fits(studio, 313, "Espresso/Bed Chem (Live)"))
    }

    @Test
    fun aHeadingNamingTheArtistIsNotSung() {
        val lrc = "[00:00.50]Espresso+Bed Chem (2025 BRIT Awards Live) - Sabrina Carpenter\n[00:12.00]Now he's thinkin' 'bout me every night oh"
        val tidy = LyricsJudge.withoutPaperwork(lrc, "Espresso/Bed Chem (Live)", "Sabrina Carpenter")
        assertFalse("BRIT Awards" in tidy)
        assertTrue("every night" in tidy)
        // Without the artist, a heading that doesn't match the title stays: never a guess.
        assertTrue("BRIT Awards" in LyricsJudge.withoutPaperwork(lrc, "Espresso/Bed Chem (Live)"))
    }
}
