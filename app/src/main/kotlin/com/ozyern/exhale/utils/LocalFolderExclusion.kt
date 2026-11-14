/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

/** Newline-separated preference format; paths containing newlines cannot round-trip. */
private const val DELIMITER = "\n"

fun encodeExcludedFolders(paths: Set<String>): String = paths.joinToString(DELIMITER)

fun decodeExcludedFolders(raw: String): Set<String> =
    if (raw.isEmpty()) emptySet() else raw.split(DELIMITER).toSet()
