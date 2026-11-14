package com.ozyern.exhale.utils

import com.music.spine.ModuleManager
import com.music.spine.ModuleManifest
import com.music.spine.ModuleSearchResult
import com.music.spine.ModuleSetting
import com.music.spine.ModuleSettingOption
import com.music.spine.ModuleStreamResponse
import com.music.spine.ModuleStreamTrack
import com.music.spine.SpineModule
import org.junit.Assert.*
import org.junit.Test

class ModulePlaybackPolicyTest {
    private val module = ModuleManager.LoadedModule(SpineModule("tidal", "Tidal"), "", "", "engine", ModuleManifest(
        settings = mapOf("quality" to ModuleSetting(options = listOf("HI_RES", "HI_RES_DOLBY", "CD", "HIGH", "LOW")
            .map { ModuleSettingOption(it, it) })),
    ))

    @Test fun `devices without Dolby receive stereo for both new and legacy settings`() {
        assertEquals("HI_RES", ModulePlaybackResolver.qualityForDevice(module, "HI_RES_DOLBY", false))
        assertEquals("HI_RES", ModulePlaybackResolver.qualityForDevice(module, "EAC3_JOC", false))
        assertEquals("HI_RES", ModulePlaybackResolver.qualityForDevice(module, "MAX", false))
        assertEquals("HIGH", ModulePlaybackResolver.qualityForDevice(module, "HIGH", false))
    }

    @Test fun `Dolby capable devices retain the addon immersive choice`() {
        assertEquals("HI_RES_DOLBY", ModulePlaybackResolver.qualityForDevice(module, "HI_RES_DOLBY", true))
        assertEquals("HI_RES_DOLBY", ModulePlaybackResolver.qualityForDevice(module, "EAC3_JOC", true))
        assertEquals("LOSSLESS", ModulePlaybackResolver.hostTier("HI_RES_DOLBY"))
        assertEquals("LOW", ModulePlaybackResolver.hostTier("LOW"))
    }

    @Test fun `actual stereo FLAC response overrides Atmos search labels`() {
        val stream = ModuleStreamResponse(streamUrl = "https://example.com/music.m3u8", mimeType = "application/vnd.apple.mpegurl",
            streamType = "hls", track = ModuleStreamTrack(format = "flac", codec = "FLAC", audioQuality = "FLAC 16-bit / 44.1kHz"))
        val mime = ModulePlaybackResolver.mimeType(stream, ModuleSearchResult(audioQuality = "Dolby Atmos"), stream.streamUrl!!)
        assertEquals("audio/flac", mime)
        assertFalse(ModulePlaybackResolver.isAtmos(stream, mime))
    }

    @Test fun `HLS Atmos identifies its audio codec independently of playlist MIME`() {
        val stream = ModuleStreamResponse(streamUrl = "https://example.com/music.m3u8", mimeType = "application/vnd.apple.mpegurl",
            streamType = "hls", track = ModuleStreamTrack(format = "m4a", codec = "EAC3_JOC", audioQuality = "Dolby Atmos"))
        val mime = ModulePlaybackResolver.mimeType(stream, ModuleSearchResult(format = "flac"), stream.streamUrl!!)
        assertTrue(mime.contains("ec-3"))
        assertTrue(ModulePlaybackResolver.isAtmos(stream, mime))
    }
}
