package com.ozyern.exhale.lyrics

import kotlin.test.*

class LyricsSyncLevelTest {
    private val words = "[00:01.000]<00:01.000|00:01.500>Hello <00:01.800|00:02.300>world"

    @Test fun detectsRealWordTimingAndRejectsInvalidRuns() {
        assertEquals(LyricsSyncLevel.WORD, LyricsSyncLevel.detect(words))
        assertEquals(LyricsSyncLevel.LINE, LyricsSyncLevel.detect("[00:01.000]<00:01.000|00:00.500>Hello <00:01.000|00:00.700>world"))
        assertEquals(LyricsSyncLevel.LINE, LyricsSyncLevel.detect("[00:01.000]<00:01.000|00:03.000>An entire line"))
        assertEquals(LyricsSyncLevel.PLAIN, LyricsSyncLevel.detect("An entire line\nAnother line"))
        assertEquals(LyricsSyncLevel.LINE, LyricsSyncLevel.detect("[00:01.000]First\n[00:02.000]Second"))
        assertEquals(LyricsSyncLevel.WORD, LyricsSyncLevel.detect("[00:01.000]Hello world\n<Hello:1.0:1.5|world:1.8:2.3>"))
    }

    @Test fun upgradesWithoutDowngradingAndHonoursPriorityWithinQuality() {
        val policy = LyricsUpgradePolicy()
        assertNotNull(policy.consider("plain", 0, "Hello world"))
        assertNotNull(policy.consider("lines", 5, "[00:01.000]Hello world"))
        assertNotNull(policy.consider("words", 6, words))
        assertNull(policy.consider("late-plain", 0, "Hello world"))
        assertNull(policy.consider("late-words", 9, words))
        assertNotNull(policy.consider("preferred-words", 1, words))
        assertEquals("preferred-words", policy.best?.provider)
    }
}

class LyricsJudgedPolicyTest {
    private val taste = "[00:01.000]You'll get a taste of me\n[00:04.000]Lipstick on your coffee cup\n[00:08.000]Fake smile but the real stuff"
    private val tasteWords = "[00:01.000]<00:01.000|00:01.500>Taste <00:01.800|00:02.300>again\n[00:04.000]Lipstick on your coffee cup\n[00:08.000]Fake smile but the real stuff"
    private val otherSong = "[00:01.000]<00:01.000|00:01.500>Driving <00:01.800|00:02.300>home\n[00:04.000]Through the city lights tonight\n[00:08.000]Nothing gonna stop me now"

    @Test fun aSourceSingingAnotherSongLosesToOnesThatAgree() {
        val policy = LyricsUpgradePolicy(title = "Taste")
        assertNotNull(policy.consider("word-but-wrong", 0, otherSong))
        assertNotNull(policy.consider("lines", 3, taste))
        assertNotNull(policy.consider("words", 5, tasteWords))
        assertEquals("words", policy.best?.provider)
    }

    @Test fun withoutATitleTheLadderIsUnchanged() {
        val policy = LyricsUpgradePolicy()
        assertNotNull(policy.consider("word", 0, otherSong))
        assertNull(policy.consider("lines", 3, taste))
    }
}
