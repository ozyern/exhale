/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.openflac

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
}
