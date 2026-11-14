package com.ozyern.exhale.playback

import com.ozyern.exhale.db.entities.SongEntity

object QueueBuilder {

    fun extend(existing: List<SongEntity>, candidates: List<SongEntity>, limit: Int): List<SongEntity> {
        if (limit <= 0 || candidates.isEmpty()) return emptyList()
        val takenIds = existing.mapTo(mutableSetOf()) { it.id }
        val result = ArrayList<SongEntity>(minOf(limit, candidates.size))

        for (candidate in candidates) {
            if (result.size >= limit) break
            if (takenIds.add(candidate.id)) result += candidate
        }
        return result
    }

    fun isSameRecording(a: SongEntity, b: SongEntity): Boolean = a.id == b.id
}
