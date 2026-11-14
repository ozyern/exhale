package com.ozyern.exhale.canvas

import org.junit.Assert.*
import org.junit.Test

class ApplePlaylistExportTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0"><dict><key>Tracks</key><dict>
        <key>1</key><dict><key>Name</key><string>First &amp; best</string><key>Artist</key><string>Artist</string><key>Total Time</key><integer>120000</integer></dict>
        <key>2</key><dict><key>Name</key><string>Second</string><key>Artist</key><string>Artist</string></dict>
        </dict><key>Playlists</key><array><dict><key>Name</key><string>Private mix</string>
        <key>Playlist Items</key><array><dict><key>Track ID</key><integer>2</integer></dict><dict><key>Track ID</key><integer>1</integer></dict><dict><key>Track ID</key><integer>99</integer></dict></array>
        </dict></array></dict></plist>""".trimIndent()

    @Test fun importsPrivateExportInPlaylistOrderWithoutFetchingTheDtd() {
        val result = ApplePlaylistExport.parse(xml.toByteArray())
        assertEquals("Private mix", result.title)
        assertEquals(listOf("Second", "First & best"), result.tracks.map { it.title })
        assertEquals(120000, result.tracks.last().durationMs)
        assertEquals(1, result.unsupportedTracks)
    }

    @Test fun rejectsEntityDeclarationsAndOversizedFiles() {
        for (bytes in listOf("<!DOCTYPE plist [<!ENTITY attack 'bad'>]><plist/>".toByteArray(), ByteArray(ApplePlaylistExport.MAX_BYTES + 1))) {
            var rejected = false
            try { ApplePlaylistExport.parse(bytes) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }

    @Test fun supportsUtf16Export() {
        val result = ApplePlaylistExport.parse(xml.replace("UTF-8", "UTF-16").toByteArray(Charsets.UTF_16))
        assertEquals("Private mix", result.title)
    }
}
