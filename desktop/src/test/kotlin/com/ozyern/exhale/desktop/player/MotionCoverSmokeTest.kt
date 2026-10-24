/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import androidx.compose.ui.graphics.asSkiaBitmap
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A real song's moving cover found and rendered through libmpv's software renderer: frames arrive,
 * and one is saved as a PNG to look at. EXHALE_SMOKE=1 to run.
 */
class MotionCoverSmokeTest {
    @Test
    fun espressoMoves() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        val started = System.currentTimeMillis()
        val url = MotionCovers.find(song)
        println("motion cover for ${song.title}: $url (${System.currentTimeMillis() - started} ms)")
        assertNotNull("no moving cover found", url)

        MotionCoverRenderer(480).use { renderer ->
            renderer.play(url!!)
            val deadline = System.currentTimeMillis() + 20_000
            while (renderer.frames.intValue < 60 && System.currentTimeMillis() < deadline) Thread.sleep(100)
            val count = renderer.frames.intValue
            println("frames rendered: $count")
            assertTrue("too few frames", count >= 30)
            val first = renderer.frames.intValue
            Thread.sleep(2_000)
            println("frames per second: ${(renderer.frames.intValue - first) / 2.0}")
            renderer.setPaused(true)
            Thread.sleep(300)
            val pausedAt = renderer.frames.intValue
            Thread.sleep(1_000)
            println("frames while paused: ${renderer.frames.intValue - pausedAt}")
        }
    }

    /** One song after another, as the player does: every renderer after the first has to draw too. */
    @Test
    fun oneAfterAnother() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        val url = MotionCovers.find(song)!!
        repeat(3) { round ->
            MotionCoverRenderer(480, File(System.getProperty("java.io.tmpdir"), "exhale-motion-$round.log").absolutePath).use { renderer ->
                renderer.play(url)
                val deadline = System.currentTimeMillis() + 15_000
                while (renderer.frames.intValue < 30 && System.currentTimeMillis() < deadline) Thread.sleep(100)
                println("round $round frames: ${renderer.frames.intValue}")
                assertTrue("renderer $round drew nothing", renderer.frames.intValue >= 30)
            }
        }
    }

    @Test
    fun saveAFrame() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        val url = MotionCovers.find(song) ?: return@runBlocking
        MotionCoverRenderer(480).use { renderer ->
            renderer.play(url)
            val deadline = System.currentTimeMillis() + 20_000
            while (renderer.frames.intValue < 45 && System.currentTimeMillis() < deadline) Thread.sleep(100)
            val frame = renderer.current ?: return@use
            val out = File(System.getProperty("java.io.tmpdir"), "exhale-motion-cover.png")
            frame.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(out::writeBytes)
            println("saved ${out.absolutePath}")
        }
    }
}
