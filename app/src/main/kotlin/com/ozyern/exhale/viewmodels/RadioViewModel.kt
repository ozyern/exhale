package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RadioViewModel(
    context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val recentStations = combine(
        database.recentEvents(40),
        context.dataStore.data.map { it[HideExplicitKey] ?: false },
    ) { events, hideExplicit ->
        events.asSequence()
            .map { it.song }
            .filter { it.isRadioSeed() && (!hideExplicit || !it.song.explicit) }
            .distinctBy { it.artists.firstOrNull()?.id ?: it.id }
            .take(12)
            .toList()
    }.stateIn<List<Song>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun Song.isRadioSeed(): Boolean =
    !song.isLocal && song.id.length == 11 && song.id.all {
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-'
    }
