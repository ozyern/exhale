/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DiscordIpcTest {
    private fun activity(
        title: String = "Espresso",
        image: String? = "https://example.com/art.jpg",
        startMs: Long? = 1_000L,
        endMs: Long? = 2_000L,
    ) = DiscordActivity("Exhale", title, "Sabrina Carpenter", image, "Short n' Sweet", "https://example.com/i.png", "Exhale", startMs, endMs)

    @Test
    fun framesAreLittleEndianOpThenLengthThenBody() {
        val frame = DiscordIpc.encodeFrame(1, "{}")
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buffer.int)
        assertEquals(2, buffer.int)
        assertEquals("{}", String(frame, 8, 2))
    }

    @Test
    fun anActivityReadsAsListeningWithTheAppsOwnName() {
        val json = DiscordIpc.activityJson(activity())
        assertEquals("Exhale", json.getString("name"))
        assertEquals(2, json.getInt("type"))
        assertEquals("Espresso", json.getString("details"))
        assertEquals("Sabrina Carpenter", json.getString("state"))
        assertEquals(2_000L, json.getJSONObject("timestamps").getLong("end"))
    }

    /** Discord refuses the whole activity over one bad field, so none of these may reach it. */
    @Test
    fun fieldsDiscordWouldRefuseAreFixedOrLeftOut() {
        assertEquals(2, DiscordIpc.activityJson(activity(title = "7")).getString("details").length)
        assertEquals(128, DiscordIpc.activityJson(activity(title = "x".repeat(300))).getString("details").length)
        assertEquals("Exhale", DiscordIpc.activityJson(activity(title = "  ")).getString("details"))
        val assets = DiscordIpc.activityJson(activity(image = "https://example.com/" + "a".repeat(300))).getJSONObject("assets")
        assertFalse(assets.has("large_image"))
        assertFalse(assets.has("large_text"))
        assertTrue(assets.has("small_image"))
    }

    @Test
    fun aPausedSongHasNoClock() {
        assertFalse(DiscordIpc.activityJson(activity(startMs = null, endMs = null)).has("timestamps"))
    }
}
