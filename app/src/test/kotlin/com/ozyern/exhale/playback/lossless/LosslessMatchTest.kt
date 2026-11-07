package com.ozyern.exhale.playback.lossless

import com.ozyern.exhale.playback.lossless.sources.LosslessStreamResolver
import com.ozyern.exhale.playback.lossless.sources.constants.AudioSourceType
import com.ozyern.exhale.playback.lossless.sources.audiosource.DirectStream
import com.ozyern.exhale.playback.lossless.sources.audiosource.TitleMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LosslessMatchTest {
    private fun request(title: String, artist: String = "Sabrina Carpenter", seconds: Long? = 180) =
        LosslessStreamResolver.Request(
            mediaId = "id",
            title = title,
            artists = listOf(artist),
            album = null,
            durationMs = seconds?.let { it * 1000L },
        )

    private fun stream(title: String, artist: String = "Sabrina Carpenter", seconds: Long = 180) = DirectStream(
        uri = "https://example.invalid/track/1",
        mimeType = "audio/flac",
        codecs = "flac",
        contentLength = null,
        label = "test",
        source = AudioSourceType.MONOCHROME,
        matchedTitle = title,
        matchedArtist = artist,
        matchedDurationMs = seconds * 1000L,
    )

    private fun matches(wanted: String, found: DirectStream, seconds: Long? = 180) =
        TitleMatch.evaluate(wanted, listOf("Sabrina Carpenter"), null, seconds?.let { it * 1000L }, found).accepted

    @Test
    fun queriesGoFromExactToLoose() {
        assertEquals(
            listOf(
                "Sabrina Carpenter Please Please Please (feat. Dolly Parton)",
                "Sabrina Carpenter Please Please Please",
                "Please Please Please",
            ),
            LosslessStreamResolver.monochromeQueries(request("Please Please Please (feat. Dolly Parton)")),
        )
    }

    @Test
    fun aPlainTitleIsNotSearchedTwice() {
        assertEquals(
            listOf("Sabrina Carpenter Manchild", "Manchild"),
            LosslessStreamResolver.monochromeQueries(request("Manchild")),
        )
    }

    @Test
    fun catalogueSpellingsOfTheSameSongMatch() {
        // Curly apostrophe in the catalogue, straight one on YouTube.
        assertTrue(matches("Nobody's Son", stream("Nobody’s Son")))
        assertTrue(matches("Dumb & Poetic", stream("Dumb & Poetic")))
        assertTrue(matches("When Did You Get Hot?", stream("When Did You Get Hot?")))
        // The catalogue lists both artists.
        assertTrue(matches("Espresso", stream("Espresso", artist = "Sabrina Carpenter, Someone")))
    }

    @Test
    fun aTrackManifestGivesTheFileItself() {
        val manifest = java.util.Base64.getEncoder().encodeToString(
            """{"mimeType":"audio/flac","codecs":"flac","urls":["https://cdn.example.invalid/a.flac"]}""".toByteArray(),
        )
        val answer = org.json.JSONObject("""{"data":{"bitDepth":24,"sampleRate":96000,"manifest":"$manifest"}}""")
        val found = LosslessStreamResolver.monochromeManifest(answer)!!
        assertEquals("https://cdn.example.invalid/a.flac", found.url)
        assertEquals(96000, found.sampleRate)
        assertEquals(24, found.bitDepth)
    }

    @Test
    fun aManifestWithoutAFileIsNoStream() {
        // A DASH manifest (XML) is not a file the player can be handed.
        val dash = java.util.Base64.getEncoder().encodeToString("<MPD></MPD>".toByteArray())
        assertEquals(null, LosslessStreamResolver.monochromeManifest(org.json.JSONObject("""{"data":{"manifest":"$dash"}}""")))
        assertEquals(
            "https://cdn.example.invalid/b.flac",
            LosslessStreamResolver.monochromeManifest(org.json.JSONObject("""{"data":{"url":"https://cdn.example.invalid/b.flac"}}"""))?.url,
        )
    }

    @Test
    fun anotherRecordingIsRejected() {
        assertFalse(matches("Espresso", stream("Espresso", seconds = 230)))
        assertFalse(matches("Espresso", stream("Espresso", artist = "Someone Else")))
        assertFalse(matches("Taste", stream("Juno")))
    }
}
