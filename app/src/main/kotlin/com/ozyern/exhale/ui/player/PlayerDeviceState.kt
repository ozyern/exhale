/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableFloatState
import com.ozyern.exhale.playback.PlayerConnection
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ozyern.exhale.core.ActiveAudioOutput
import com.ozyern.exhale.core.getActiveAudioOutput
import kotlin.math.max

/** The connected Bluetooth, USB or wired output, kept live from broadcasts and device callbacks. */
@Composable
fun rememberActiveAudioOutput(): State<ActiveAudioOutput?> {
    val context = LocalContext.current
    return produceState<ActiveAudioOutput?>(initialValue = getActiveAudioOutput(context)) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = getActiveAudioOutput(context)
            }
        }

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getActiveAudioOutput(context)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getActiveAudioOutput(context)
                }
            }
        } else null

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.media.AUDIO_BECOMING_NOISY")
        }
    
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
    
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        }
    }
}

/** The system music volume as 0..1, plus the manager and max it was derived from. */
@Stable
class SystemVolume(val audioManager: AudioManager, val max: Float, val volume: State<Float>)

@Composable
fun rememberSystemVolume(): SystemVolume {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    // The volume broadcast can be missed while the app is backgrounded, so refresh on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    val systemVolumeState = remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume
        )
    }
    var systemVolume by systemVolumeState
    DisposableEffect(lifecycleOwner, maxSystemVolume) {
        fun refresh() {
            systemVolume =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return SystemVolume(audioManager, maxSystemVolume, systemVolumeState)
}

/** The live audio format of the active player; re-reads on a track change or same-item retry. */
@Composable
fun rememberPlayerAudioFormat(playerConnection: PlayerConnection): State<androidx.media3.common.Format?> {
    // Follow playerFlow so a crossfade swap moves this listener to the new player.
    val activePlayer by playerConnection.service.playerFlow.collectAsStateWithLifecycle(
        initialValue = playerConnection.player
    )
    return produceState(initialValue = activePlayer?.audioFormat, activePlayer) {
        val target = activePlayer ?: return@produceState
        value = target.audioFormat
        val listener = object : Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                // Same-item retries may change formats without TRACKS_CHANGED; the seek
                // that starts the retry also emits POSITION_DISCONTINUITY.
                if (events.contains(androidx.media3.common.Player.EVENT_TRACKS_CHANGED) ||
                    events.contains(androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY)
                ) {
                    value = (player as? ExoPlayer)?.audioFormat
                }
            }
        }
        target.addListener(listener)
        awaitDispose {
            target.removeListener(listener)
        }
    }
}
