/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoAlignmentTest {
    private val rate = 8_000

    /** A song's opening: irregular beats, each a short burst over quiet noise. */
    private fun song(seconds: Int, seed: Int = 1): FloatArray {
        val random = Random(seed)
        val out = FloatArray(seconds * rate) { (random.nextFloat() - 0.5f) * 0.01f }
        var at = 0
        while (at < out.size) {
            for (i in at until minOf(at + 400, out.size)) out[i] += (random.nextFloat() - 0.5f) * 0.8f
            at += rate / 4 + random.nextInt(rate / 2)
        }
        return out
    }

    /** [intro] seconds of a different, quieter opening, then the song. */
    private fun videoWithIntro(song: FloatArray, intro: Double): FloatArray {
        val lead = (intro * rate).toInt()
        val random = Random(99)
        return FloatArray(lead + song.size) { i -> if (i < lead) (random.nextFloat() - 0.5f) * 0.02f else song[i - lead] }
    }

    @Test
    fun findsTheVideosIntro() {
        val s = song(50)
        val offset = VideoAlignment.match(videoWithIntro(s, 7.0), s)
        assertNotNull(offset)
        assertEquals(7_000.0, offset!!.toDouble(), 40.0)
    }

    @Test
    fun findsAVideoThatCutsTheSongsOpening() {
        val s = song(50)
        // The video starts 3 s into the song.
        val video = s.copyOfRange(3 * rate, s.size) + song(30, seed = 5)
        val offset = VideoAlignment.match(video, s)
        assertNotNull(offset)
        assertEquals(-3_000.0, offset!!.toDouble(), 40.0)
    }

    @Test
    fun unrelatedRecordingsAreNotLinedUp() {
        assertNull(VideoAlignment.match(song(120, seed = 3), song(50, seed = 4)))
    }
}
