package com.ozyern.exhale.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkEndpointFadeTest {
    @Test fun handoffNeverBlanksOrDoublesArtwork() {
        for (step in 0..1000) {
            val progress = step / 1000f
            val mini = miniArtworkEndpointAlpha(progress)
            val full = fullArtworkEndpointAlpha(progress)
            val flying = flyingArtworkEndpointAlpha(progress)
            assertTrue(mini in 0f..1f && full in 0f..1f && flying in 0f..1f)
            assertEquals(1f, mini + full + flying, 0.00001f)
        }
    }

    @Test fun closingReversesTheSameHandoff() {
        for (step in 0..100) {
            val progress = step / 100f
            assertEquals(miniArtworkEndpointAlpha(progress), fullArtworkEndpointAlpha(1f - progress), 0.00001f)
            assertEquals(flyingArtworkEndpointAlpha(progress), flyingArtworkEndpointAlpha(1f - progress), 0.00001f)
        }
        assertEquals(1f, miniArtworkEndpointAlpha(0f), 0f)
        assertEquals(1f, fullArtworkEndpointAlpha(1f), 0f)
        assertEquals(1f, flyingArtworkEndpointAlpha(0.5f), 0f)
    }
}
