/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class PlaybackLogLevel {
    INFO,
    WARNING,
    ERROR,
    DEBUG,
    BOT // Special level for highlighting bot mitigation events
}

data class PlaybackLogEntry(
    val timestamp: String,
    val level: PlaybackLogLevel,
    val message: String,
    val details: String? = null
)

object PlaybackLogManager {
    private const val MAX_LOG_ENTRIES = 500
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val _logs = MutableStateFlow<List<PlaybackLogEntry>>(emptyList())
    val logs: StateFlow<List<PlaybackLogEntry>> = _logs.asStateFlow()

    fun log(level: PlaybackLogLevel, message: String, details: String? = null) {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val entry = PlaybackLogEntry(timestamp, level, message, details)

        _logs.update { currentLogs ->
            val updatedLogs = currentLogs + entry
            if (updatedLogs.size > MAX_LOG_ENTRIES) updatedLogs.drop(1) else updatedLogs
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
