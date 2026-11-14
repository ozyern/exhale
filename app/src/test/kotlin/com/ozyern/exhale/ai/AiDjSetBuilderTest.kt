package com.ozyern.exhale.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiDjSetBuilderTest {
    @Test
    fun `concert roles follow the set arc and keep the seed first`() {
        val ids = listOf("seed", "closer", "unknown", "peak", "opener", "rising")
        val roles = mapOf(
            "seed" to "opener",
            "closer" to "closer",
            "peak" to "peak",
            "opener" to "opener",
            "rising" to "rising",
        )

        assertEquals(
            listOf("seed", "opener", "rising", "peak", "closer", "unknown"),
            AiDjSetBuilder.orderConcertSongs(ids, roles, "seed"),
        )
    }

    @Test
    fun `songs without concert roles keep their original order`() {
        val ids = listOf("first", "second", "third")

        assertEquals(ids, AiDjSetBuilder.orderConcertSongs(ids, emptyMap(), null))
    }
}
