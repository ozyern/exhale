package com.ozyern.exhale.history

import com.ozyern.exhale.db.entities.EventWithSong

interface HistoryLocalDataSource {
    suspend fun loadEvents(limit: Int, offset: Int, hideVideoSongs: Boolean, query: String): List<EventWithSong>
}
