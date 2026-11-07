/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0 (utils/DataStore.kt, trimmed).
 *
 * Blocking + suspending preference readers used throughout the ported lossless stack.
 * ArchiveTune keeps an in-memory snapshot of the whole preference map; Exhale does not, so
 * the blocking reader falls back to a short bounded `runBlocking` (the same fallback ArchiveTune
 * uses when its snapshot is not yet populated).
 */

package com.ozyern.exhale.playback.lossless.sources.constants

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(1500) { data.first()[key] }
    }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = get(key) ?: defaultValue

suspend fun <T> DataStore<Preferences>.getAsync(key: Preferences.Key<T>): T? = data.first()[key]

suspend fun <T> DataStore<Preferences>.getAsync(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue
