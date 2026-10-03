/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.lyrics.LyricsEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The screen [LockScreenLyrics] puts up over the keyguard: the cover blurred into the background,
 * the time, the line being sung large with its neighbours quiet around it, and the transport.
 * Swipe up to unlock; it also gets out of the way by itself once the phone is unlocked.
 */
class LockScreenLyricsActivity : ComponentActivity() {
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()
        // Up: the notification that may have opened it has done its job.
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)?.cancel(LockScreenLyrics.NOTIFICATION_ID)
        }

        // Unlocked some other way (fingerprint, face): nothing more to show.
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) finish()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val player = LockScreenLyrics.player
        if (player == null) {
            finish()
            return
        }
        setContent { LockLyricsScreen(player, onUnlock = ::unlock) }
    }

    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = finish()
                },
            )
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }
}

@Composable
private fun LockLyricsScreen(player: Player, onUnlock: () -> Unit) {
    val lines by LockScreenLyrics.lines.collectAsState()
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artwork by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition
            playing = player.isPlaying
            val meta = player.currentMediaItem?.mediaMetadata
            title = meta?.title?.toString().orEmpty()
            artist = meta?.artist?.toString().orEmpty()
            artwork = meta?.artworkUri?.toString()
            now = System.currentTimeMillis()
            delay(80)
        }
    }

    val index = currentIndex(lines, position)
    var dragged by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragged < -120f) onUnlock()
                        dragged = 0f
                    },
                    onVerticalDrag = { _, amount -> dragged += amount },
                )
            },
    ) {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
            )

            // The words: the line before, the line now, the two after.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                LyricLine(lines.getOrNull(index - 1)?.text, Role.Past)
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (slideInVertically(tween(360)) { it / 2 } + fadeIn(tween(360))) togetherWith
                            (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(220)))
                    },
                    label = "lockLyric",
                ) { i ->
                    LyricLine(lines.getOrNull(i)?.text ?: if (i < 0) "♪" else null, Role.Now)
                }
                LyricLine(lines.getOrNull(index + 1)?.text, Role.Next)
                LyricLine(lines.getOrNull(index + 2)?.text, Role.Later)
            }

            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Transport(R.drawable.skip_previous, 52) { player.seekToPreviousMediaItem() }
                Transport(if (playing) R.drawable.pause else R.drawable.play, 68) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                Transport(R.drawable.skip_next, 52) { player.seekToNextMediaItem() }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Swipe up to unlock",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onUnlock).padding(8.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

private enum class Role { Past, Now, Next, Later }

@Composable
private fun LyricLine(text: String?, role: Role) {
    if (text.isNullOrBlank()) {
        Spacer(Modifier.height(if (role == Role.Now) 44.dp else 28.dp))
        return
    }
    val (size, alpha) = when (role) {
        Role.Now -> 30 to 1f
        Role.Next -> 22 to 0.55f
        Role.Past -> 20 to 0.35f
        Role.Later -> 20 to 0.35f
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = alpha),
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        fontWeight = if (role == Role.Now) FontWeight.Bold else FontWeight.SemiBold,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (role == Role.Now) 10.dp else 6.dp),
    )
}

@Composable
private fun Transport(icon: Int, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (size > 60) 0.22f else 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size((size * 0.44f).dp))
    }
}

/** The last line whose time has come, or -1 before the first. */
private fun currentIndex(lines: List<LyricsEntry>, position: Long): Int {
    var found = -1
    for (i in lines.indices) {
        if (lines[i].time <= position + 150) found = i else break
    }
    return found
}
