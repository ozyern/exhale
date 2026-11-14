package com.ozyern.exhale.playback

import com.ozyern.exhale.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueBuilderTest {
    @Test
    fun extendSkipsQueuedAndRepeatedIdsAndPreservesCandidateOrder() {
        val existing = listOf(song("already-queued"))
        val candidates = listOf(
            song("already-queued"),
            song("first"),
            song("first"),
            song("second"),
        )

        val result = QueueBuilder.extend(existing, candidates, limit = 2)

        assertEquals(listOf("first", "second"), result.map(SongEntity::id))
    }

    private fun song(id: String) = SongEntity(id = id, title = id)
}
