package com.ozyern.exhale.canvas

import org.junit.Assert.*
import org.junit.Test

class ApplePlaylistLinkTest {
    @Test fun acceptsSharedCatalogLinks() {
        assertEquals(ApplePlaylistLink("us", "pl.u-abc123"), ApplePlaylistLink.parse("https://music.apple.com/us/playlist/my-music/pl.u-abc123?l=en"))
        assertEquals(ApplePlaylistLink("in", "pl.abc123"), ApplePlaylistLink.parse("https://music.apple.com/in/playlist/pl.abc123"))
    }
    @Test fun rejectsWrongServicesPrivateIdsAndMisleadingHosts() {
        listOf("https://music.apple.com.evil.test/us/playlist/name/pl.abc", "https://music.apple.com@evil.test/us/playlist/name/pl.abc",
            "https://music.apple.com/us/album/name/pl.abc", "https://music.apple.com/us/playlist/name/p.abc",
            "https://music.apple.com:444/us/playlist/name/pl.abc", "invalid").forEach { assertNull(ApplePlaylistLink.parse(it)) }
    }
}
