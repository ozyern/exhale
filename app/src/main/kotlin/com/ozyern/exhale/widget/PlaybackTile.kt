/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ozyern.exhale.playback.MusicService

/**
 * The Quick Settings tile: plays or pauses whatever Exhale last had queued, without opening the
 * app. A tap is the widget's own play button, so it brings the restored queue back exactly the way
 * the widget does. The tile shows the song and lights while it plays, following the snapshot the
 * service saves on every play, pause and track change while the panel is open.
 */
class PlaybackTile : TileService() {

    private val redraw = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> draw() }

    override fun onStartListening() {
        prefs(this).registerOnSharedPreferenceChangeListener(redraw)
        draw()
    }

    override fun onStopListening() {
        prefs(this).unregisterOnSharedPreferenceChangeListener(redraw)
    }

    override fun onClick() {
        val intent = Intent(this, MusicService::class.java).setAction(MusicWidgetReceiver.ACTION_PLAY_PAUSE)
        runCatching { ContextCompat.startForegroundService(this, intent) }
    }

    private fun draw() {
        val tile = qsTile ?: return
        val prefs = prefs(this)
        val title = prefs.getString(KEY_TITLE, null)
        tile.state = if (prefs.getBoolean(KEY_PLAYING, false)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = title
        tile.updateTile()
    }

    companion object {
        private const val PREFS = "playback_tile"
        private const val KEY_PLAYING = "playing"
        private const val KEY_TITLE = "title"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Saves what the tile shows, and asks the system to redraw it if the panel is open. */
        fun publish(context: Context, playing: Boolean, title: String?) {
            val prefs = prefs(context)
            if (prefs.getBoolean(KEY_PLAYING, false) == playing && prefs.getString(KEY_TITLE, null) == title) return
            prefs.edit {
                putBoolean(KEY_PLAYING, playing)
                putString(KEY_TITLE, title)
            }
            runCatching {
                requestListeningState(context, ComponentName(context, PlaybackTile::class.java))
            }
        }
    }
}
