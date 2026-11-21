/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.openflac

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Asks the live catalogues for every shelf and the first release's tracks. Opt in with EXHALE_SMOKE=1. */
class OpenFlacProbe {
    @Test
    fun shelvesAnswer() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        System.getenv("JAMENDO_CLIENT_ID")?.let { OpenFlac.jamendoClientId = it }
        val shelves = OpenFlac.shelves().getOrThrow()
        for (shelf in shelves) {
            println("${shelf.title}: ${shelf.releases.size} releases")
            val first = shelf.releases.first()
            val tracks = OpenFlac.tracks(first.key).getOrThrow()
            println("  ${first.title} — ${first.artist}: ${tracks.size} tracks")
            tracks.take(2).forEach { println("    ${it.title} | ${it.durationSeconds}s | ${it.credit.licence} | ${it.url}") }
        }
    }

    /** An album page looks its songs up all at once: each must find its copy, not only the first. */
    @Test
    fun albumSongsFoundTogether() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        // A song from every album on the list, as YouTube Music names them: straight apostrophes,
        // and albums whose files carry no title of their own.
        val songs = listOf(
            "Manchild" to 214, "Tears" to 160, "My Man on Willpower" to 198, "buy me presents" to 177,
            "Bed Chem" to 172, "Couldn't Make It Any Harder" to 180, "Feather" to 186, "Nonsense" to 164,
            "Bad Time" to 185, "Exhale" to 165, "Thumbs" to 216, "Eyes Wide Open" to 193,
            "Can't Blame a Girl for Trying" to 169, "Don't Worry I'll Make You Worry" to 222,
        )
        val started = System.currentTimeMillis()
        val found = songs.map { (title, length) ->
            async { title to OpenFlac.findCopy(title, "Sabrina Carpenter", length) }
        }.map { it.await() }
        found.forEach { (title, track) -> println("$title -> ${track?.url ?: "NONE"} (${track?.bitDepth}bit)") }
        println("took ${System.currentTimeMillis() - started} ms")
        org.junit.Assert.assertTrue(found.all { it.second != null })
    }
}
