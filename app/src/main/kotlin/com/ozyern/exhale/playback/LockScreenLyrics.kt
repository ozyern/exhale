/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.ozyern.exhale.lyrics.LyricsEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Exhale's own lyrics over the lock screen.
 *
 * ColorOS draws lyrics on its lock screen only for players OPPO has whitelisted; a third-party app's
 * lyric document is accepted and never shown. So this does what the big streaming players do on every
 * ROM: when the screen goes off while music plays, an activity marked show-when-locked is put up,
 * and the next time the screen comes on it is there, above the keyguard, with the words in time.
 * Swiping it away (or unlocking from it) leaves the ordinary lock screen and the phone as they were.
 *
 * Starting an activity from the background needs "Display over other apps"; without it the screen
 * simply never appears, so the setting asks for it when switched on.
 */
object LockScreenLyrics {
    private val _lines = MutableStateFlow<List<LyricsEntry>>(emptyList())
    val lines: StateFlow<List<LyricsEntry>> = _lines

    private var playerRef: WeakReference<Player>? = null
    val player: Player? get() = playerRef?.get()

    /** Off unless switched on in Settings; mirrored here so the receiver need not read DataStore. */
    @Volatile var enabled: Boolean = false

    fun publishLines(lines: List<LyricsEntry>) {
        _lines.value = lines
    }

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Set by [LockScreenLyricsActivity] while it is on screen, so a second launch is not stacked. */
    @Volatile var showing: Boolean = false

    /**
     * Listens for the screen for as long as the service lives.
     *
     * The screen is put up when the display goes off, so it is already in place when the phone is
     * next woken. Some ROMs drop a launch made while the display is off, so waking to a locked
     * phone without it showing tries once more.
     */
    fun register(context: Context, player: Player): BroadcastReceiver {
        playerRef = WeakReference(player)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> if (shouldShow(c, player)) launch(c)
                    Intent.ACTION_SCREEN_ON -> {
                        val locked = c.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
                        if (locked && !showing && shouldShow(c, player)) launch(c)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    private fun shouldShow(c: Context, player: Player): Boolean =
        enabled && player.isPlaying && _lines.value.isNotEmpty() && (canShow(c) || canUseFullScreen(c))

    /** Opens the screen now, from Settings, so it can be seen without locking the phone. */
    fun preview(context: Context) {
        context.startActivity(
            Intent(context, LockScreenLyricsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private const val CHANNEL_ID = "lock_screen_lyrics"
    const val NOTIFICATION_ID = 0x4C59

    /** Whether Android will let a full-screen notification open the screen (Android 14+ asks). */
    fun canUseFullScreen(context: Context): Boolean {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    /** Whether the user has quietened the channel the screen is opened through. */
    fun channelMuted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(android.app.NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance < android.app.NotificationManager.IMPORTANCE_HIGH
    }

    /**
     * The path that ColorOS honours: a silent full-screen-intent notification, the mechanism
     * alarms and incoming calls use to put an activity over the lock screen while the screen is
     * off. The activity takes the notification away the moment it is up.
     */
    private fun postFullScreen(c: Context) {
        val nm = c.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL_ID, "Lock screen lyrics", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Opens the lyrics screen over the lock screen while music plays"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
        }
        val intent = Intent(c, LockScreenLyricsActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
        )
        val pending = android.app.PendingIntent.getActivity(
            c, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val title = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Lyrics"
        val notification = androidx.core.app.NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(com.ozyern.exhale.R.drawable.lyrics)
            .setContentTitle(title)
            .setContentText("Lyrics")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setFullScreenIntent(pending, true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    /**
     * Puts the screen up from the background.
     *
     * Android 15 narrowed the "Display over other apps" exemption: holding the permission is no
     * longer enough to start an activity from the background — the app must also have an overlay
     * window *showing* at that moment. So a one-pixel, invisible, untouchable overlay is added for
     * the instant of the launch and taken away again a second later.
     */
    private fun launch(c: Context) {
        if (canUseFullScreen(c)) postFullScreen(c)
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        var anchor: android.view.View? = null
        if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val view = android.view.View(c)
                val params = android.view.WindowManager.LayoutParams(
                    1, 1,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    android.graphics.PixelFormat.TRANSLUCENT,
                ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
                wm.addView(view, params)
                anchor = view
            }
        }
        val start = {
            runCatching {
                c.startActivity(
                    Intent(c, LockScreenLyricsActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
                )
            }
        }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        // A frame for the overlay to be laid out and shown before the launch leans on it.
        main.postDelayed({ start() }, if (anchor != null) 120L else 0L)
        anchor?.let { view -> main.postDelayed({ runCatching { wm?.removeView(view) } }, 1_500L) }
    }

    fun unregister(context: Context, receiver: BroadcastReceiver?) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        playerRef = null
    }
}
