/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * One-time copy of a legacy SharedPreferences file into the MMKV instance of
 * the same name, so existing user data survives the store swap without any
 * per-callsite knowledge. The marker lives in MMKV itself: its absence is the
 * only state needed to decide "not migrated yet". Legacy files are left in
 * place — harmless, and they keep an uninstall-free rollback possible.
 */
fun migrateSharedPreferencesToMMKV(context: Context, fileName: String) {
    val kv = MMKV.mmkvWithID(fileName)
    if (kv.decodeBool(MMKV_MIGRATED_KEY, false)) return
    val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    prefs.all.forEach { (key, value) ->
        when (value) {
            is String -> kv.encode(key, value)
            is Int -> kv.encode(key, value)
            is Long -> kv.encode(key, value)
            is Float -> kv.encode(key, value)
            is Boolean -> kv.encode(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") kv.encode(key, value as Set<String>)
        }
    }
    kv.encode(MMKV_MIGRATED_KEY, true)
}

private const val MMKV_MIGRATED_KEY = "mmkv_migrated_from_sp"
