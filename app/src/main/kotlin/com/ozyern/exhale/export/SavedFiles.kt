/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.export

import android.content.Context
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Which songs have been saved to the phone, and where: the song's id to its file in Music/Exhale.
 *
 * The player reads this so a saved song plays from the file — offline, with no stream to resolve —
 * the way a local music player would. A file the user has since deleted is noticed on first use and
 * forgotten, and the song goes back to streaming.
 */
object SavedFiles {
    private const val PREFS = "exhale_saved_files"
    private val checked = ConcurrentHashMap<String, Boolean>()

    fun remember(context: Context, songId: String, uri: Uri) {
        prefs(context).edit().putString(songId, uri.toString()).apply()
        checked[songId] = true
    }

    /** The saved file for this song, if there is one and it is still there. */
    fun uriFor(context: Context, songId: String): Uri? {
        val raw = prefs(context).getString(songId, null) ?: return null
        val uri = Uri.parse(raw)
        val present = checked.getOrPut(songId) {
            runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
        }
        if (!present) {
            prefs(context).edit().remove(songId).apply()
            checked.remove(songId)
            return null
        }
        return uri
    }

    fun has(context: Context, songId: String): Boolean = uriFor(context, songId) != null

    /** For lists that filter by "on this phone" and have no context of their own. */
    fun has(songId: String): Boolean = has(com.ozyern.exhale.App.instance, songId)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
