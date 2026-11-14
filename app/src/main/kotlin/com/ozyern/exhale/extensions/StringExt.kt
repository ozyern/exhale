/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.extensions

import androidx.sqlite.db.SimpleSQLiteQuery
import java.net.InetSocketAddress
import java.net.InetSocketAddress.createUnresolved

/** Enum names persisted before their constants were renamed, mapped to the current names. */
@PublishedApi
internal val legacyEnumNames = mapOf(
    "VIVIMUSIC_1" to "EXHALE_1",
    "VIVIMUSIC" to "EXHALE",
    "BITCHORD_MESH" to "FLUID_MESH",
)

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) {
        defaultValue
    } else {
        try {
            enumValueOf(legacyEnumNames[this] ?: this)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)

fun String.toInetSocketAddress(): InetSocketAddress {
    // Bracketed IPv6 literal: colons in the address would clash with the port split.
    if (startsWith("[")) {
        val closeBracket = indexOf(']')
        val host = substring(1, closeBracket)
        val port = substring(closeBracket + 2) // skip "]:"
        return createUnresolved(host, port.toInt())
    }
    val (host, port) = split(":")
    return createUnresolved(host, port.toInt())
}
