package com.ozyern.exhale.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.constants.PauseListenHistoryKey
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreListenHistorySettingsRepository(
    private val context: Context,
) : ListenHistorySettingsRepository {
    override fun observe(): Flow<ListenHistorySettingsState> = context.dataStore.data
        .map { preferences ->
            ListenHistorySettingsState.Ready(
                ListenHistorySettings(paused = preferences[PauseListenHistoryKey] ?: false),
            )
        }
        .distinctUntilChanged()

    override suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PauseListenHistoryKey] = paused
        }
    }
}
