package com.ozyern.exhale.search

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ExplicitSearchTest {
    private fun song(id: String, title: String, explicit: Boolean = false) =
        SongItem(id = id, title = title, artists = listOf(Artist("Eminem", null)), thumbnail = "", explicit = explicit)

    @Test
    fun starredWordsTakeBackWhatTheUserTyped() {
        assertEquals("ass like that eminem remix", ExplicitSearch.uncensor("a$$ like that eminem remix", "ass like that"))
        assertEquals("eminem ass like that", ExplicitSearch.uncensor("eminem a** like that", "Ass like that"))
    }

    @Test
    fun aStarredWordWithNothingTypedForItStays() {
        assertEquals("a$$ like that", ExplicitSearch.uncensor("a$$ like that", "eminem"))
    }

    @Test
    fun theExplicitSongLeadsInsteadOfTheCleanEdit() {
        val clean = song("clean", "A** Like That (Super Clean Version)")
        val explicit = song("album", "Ass Like That", explicit = true)
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Top result", listOf(clean, song("other", "Eminem - A** Like That (Termik Remix)"))),
                SearchSummary("Songs", listOf(explicit, song("bow", "Bow Bow Bow"))),
            ),
        )

        val result = ExplicitSearch.preferExplicit(page)

        assertEquals(listOf("album", "clean", "other"), result.summaries[0].items.map { it.id })
        assertEquals(listOf("bow"), result.summaries[1].items.map { it.id })
    }

    @Test
    fun aCleanTopResultStaysWhenNoExplicitSongMatchesIt() {
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Top result", listOf(song("clean", "A** Like That (Super Clean Version)"))),
                SearchSummary("Songs", listOf(song("x", "Lose Yourself", explicit = true))),
            ),
        )
        assertSame(page, ExplicitSearch.preferExplicit(page))
    }
}
