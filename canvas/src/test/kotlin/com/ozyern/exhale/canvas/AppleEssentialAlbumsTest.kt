package com.ozyern.exhale.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleEssentialAlbumsTest {
    private fun parse(text: String) = AppleEssentialAlbumsParser.parse(Json.parseToJsonElement(text).jsonObject)

    @Test fun readsTheFeaturedAlbumsViewWithItsNotes() {
        val albums = parse(
            """
            {"data":[{"id":"1","views":{"featured-albums":{"data":[
              {"id":"a","attributes":{"name":"Short n' Sweet","releaseDate":"2024-08-23",
                "artwork":{"url":"https://x/{w}x{h}bb.jpg","width":3000,"height":3000,"bgColor":"2a4ea0"},
                "editorialNotes":{"short":"Please, please, please enjoy the year&#39;s most <i>anticipated</i> pop album."}}},
              {"id":"b","attributes":{"name":"emails i can't send"}}
            ]}}}]}
            """,
        )
        assertEquals(2, albums.size)
        assertEquals("Short n' Sweet", albums[0].name)
        assertEquals("2024", albums[0].year)
        assertEquals(0x2A4EA0, albums[0].artwork?.bgColor)
        assertEquals("Please, please, please enjoy the year's most anticipated pop album.", albums[0].note)
        assertNull(albums[1].note)
        assertNull(albums[1].artwork)
    }

    @Test fun noViewMeansNoAlbums() {
        assertTrue(parse("""{"data":[{"id":"1","attributes":{"name":"x"}}]}""").isEmpty())
        assertTrue(parse("""{"data":[]}""").isEmpty())
    }

    @Test fun genreSkipsTheCatchAll() {
        val attrs = Json.parseToJsonElement("""{"genreNames":["Music","Pop"]}""").jsonObject
        assertEquals("Pop", AppleEssentialAlbumsParser.genre(attrs))
    }
}
