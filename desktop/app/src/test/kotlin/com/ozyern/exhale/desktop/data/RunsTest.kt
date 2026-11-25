/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.models.NavigationEndpoint
import com.ozyern.exhale.innertube.models.Run
import com.ozyern.exhale.innertube.models.oddElements
import com.ozyern.exhale.innertube.models.artistGroupRuns
import com.ozyern.exhale.innertube.models.splitBySeparator
import com.ozyern.exhale.innertube.models.BrowseEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

/** A subtitle's play count must never come out as an artist. */
class RunsTest {
    private val link = NavigationEndpoint()
    private fun run(text: String, linked: Boolean = false) = Run(text, if (linked) link else null)
    private val dot = run(" • ")
    private fun names(vararg runs: Run) = runs.toList().oddElements().map { it.text }

    @Test fun countAfterArtist() = assertEquals(listOf("Sabrina Carpenter"), names(run("Sabrina Carpenter", true), dot, run("1.2M plays")))
    @Test fun countInTheMiddle() = assertEquals(listOf("Artist"), names(run("Artist", true), dot, run("12K views"), dot, run("3:45")))
    @Test fun songArtistCount() = assertEquals(listOf("Song", "Artist"), names(run("Song"), dot, run("Artist", true), dot, run("3.4B plays")))
    @Test fun groupedThousands() = assertEquals(listOf("Artist"), names(run("Artist", true), dot, run("1,234,567 plays")))
    @Test fun countOnlyLeavesNothing() = assertEquals(emptyList<String>(), names(run("5.5M views")))

    // Names that begin with a digit are artists, not counts.
    @Test fun twoChainz() = assertEquals(listOf("2 Chainz"), names(run("2 Chainz")))
    @Test fun twentyOneSavage() = assertEquals(listOf("21 Savage", "Metro Boomin"), names(run("21 Savage"), run(" & "), run("Metro Boomin")))
    @Test fun fiftyCent() = assertEquals(listOf("50 Cent"), names(run("50 Cent", true), dot, run("2M plays")))
    @Test fun untouchedWhenNoCount() = assertEquals(listOf("A", "B"), names(run("A", true), run(" & "), run("B", true)))

    // A row that carries the runtime in the artist column: "3:45" is not a name.
    @Test fun runtimeIsNotAnArtist() = assertEquals(listOf("Artist"), names(run("Artist", true), dot, run("3:45")))
    @Test fun artistAlbumRuntime() = assertEquals(listOf("Artist", "Album"), names(run("Artist", true), dot, run("Album"), dot, run("2:59")))
    @Test fun viewsAndRuntimeBothGo() = assertEquals(listOf("Artist"), names(run("Artist", true), dot, run("1.2M views"), dot, run("3:45")))

    private fun channel(name: String) = Run(name, NavigationEndpoint(browseEndpoint = BrowseEndpoint(browseId = "UC123")))
    private fun group(vararg runs: Run) = runs.toList().splitBySeparator().artistGroupRuns().map { it.text }

    // "Song • Artist • 2:56": the artist, not the label.
    @Test fun labelledSong() = assertEquals(listOf("Sabrina Carpenter"), group(run("Song"), dot, channel("Sabrina Carpenter"), dot, run("2:56")))
    // An upload with no label: the channel is first and the second group is the view count.
    @Test fun unlabelledUpload() = assertEquals(listOf("LatinHype"), group(channel("LatinHype"), dot, run("1.2M views")))
    // Only a label and a count: nothing is a name, and "Video" must not become one.
    @Test fun labelOnlyIsNoArtist() = assertEquals(emptyList<String>(), group(run("Video"), dot, run("3.4M views"), dot, run("3:02")))
    // Unlinked names still count when the row names them.
    @Test fun unlinkedArtist() = assertEquals(listOf("Arijit Singh"), group(run("Song"), dot, run("Arijit Singh"), dot, run("4:22")))
}
