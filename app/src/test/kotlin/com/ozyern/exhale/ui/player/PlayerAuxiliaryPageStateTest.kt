package com.ozyern.exhale.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAuxiliaryPageStateTest {
    @Test
    fun returningToCoverRetainsTheOutgoingPane() {
        val pages = PlayerAuxiliaryPageState()
        assertFalse(pages.showing)
        pages.value = PlayerInternalState.LYRICS
        assertTrue(pages.showing)
        pages.value = PlayerInternalState.COVER
        assertFalse(pages.showing)
        assertEquals(PlayerInternalState.LYRICS, pages.value)
    }

    @Test
    fun reopeningDuringExitSelectsTheRequestedPane() {
        val pages = PlayerAuxiliaryPageState()
        pages.value = PlayerInternalState.QUEUE
        pages.value = PlayerInternalState.COVER
        pages.value = PlayerInternalState.LYRICS
        assertTrue(pages.showing)
        assertEquals(PlayerInternalState.LYRICS, pages.value)
    }

    @Test
    fun switchingBetweenPanesKeepsThePlayerOpen() {
        val pages = PlayerAuxiliaryPageState()
        pages.value = PlayerInternalState.LYRICS
        pages.value = PlayerInternalState.QUEUE
        assertTrue(pages.showing)
        assertEquals(PlayerInternalState.QUEUE, pages.value)
    }
}
