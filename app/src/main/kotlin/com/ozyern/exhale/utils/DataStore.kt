/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ozyern.exhale.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val preferenceSnapshot = AtomicReference<Preferences?>(null)

private val preferenceMirrorStarted = AtomicBoolean(false)

private fun DataStore<Preferences>.startPreferenceMirror() {
    if (!preferenceMirrorStarted.compareAndSet(false, true)) return
    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        data.collect { preferenceSnapshot.set(it) }
    }
}

fun DataStore<Preferences>.warmPreferenceMirror() = startPreferenceMirror()

/** Never waits for disk. */
@PublishedApi
internal fun DataStore<Preferences>.cachedPreferenceSnapshot(): Preferences? {
    startPreferenceMirror()
    return preferenceSnapshot.get()
}

/** Blocks if the first preference snapshot is unavailable. */
private fun DataStore<Preferences>.snapshot(): Preferences {
    startPreferenceMirror()
    preferenceSnapshot.get()?.let { return it }
    return runBlocking(Dispatchers.IO) { data.first() }
        .also { preferenceSnapshot.set(it) }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    snapshot()[key]

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = snapshot()[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val store = context.dataStore

    // Defaults are displayed until loading completes, never persisted here.
    val initialValue = remember(store, key, defaultValue) {
        store.cachedPreferenceSnapshot()?.get(key) ?: defaultValue
    }
    val state =
        remember(store, key, defaultValue) {
            store.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue)

    return remember(store, key, coroutineScope, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        store.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val store = context.dataStore

    val initialValue = remember(store, key, defaultValue) {
        store.cachedPreferenceSnapshot()?.get(key).toEnum(defaultValue = defaultValue)
    }
    val state =
        remember(store, key, defaultValue) {
            store.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue)

    return remember(store, key, coroutineScope, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        store.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
