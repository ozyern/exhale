/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.dj

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.ozyern.exhale.R
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.playback.AudioCache
import com.ozyern.exhale.playback.smart.TrackAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import com.ozyern.exhale.playback.smart.StemQuality
import com.ozyern.exhale.playback.smart.StemSeparator
import com.ozyern.exhale.constants.StemQualityKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.extensions.toEnum
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(UnstableApi::class)
object LibraryAnalysis {
    data class Target(val id: String, val durationSeconds: Int, val title: String, val stems: Boolean = false)

    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val current: String? = null,
    )

    private const val POLL_MS = 500L
    private const val PER_SONG_TIMEOUT_MS = 5 * 60_000L
    private const val CHANNEL_ID = "analysis_progress"
    private const val NOTIFICATION_ID = 5679
    private const val ACTION_CANCEL = "com.ozyern.exhale.ANALYSIS_CANCEL"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ConcurrentLinkedQueue<Target>()
    private var job: Job? = null
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    @Synchronized
    fun analyze(context: Context, targets: List<Target>) {
        val waiting = queue.mapTo(HashSet()) { it.id }
        val fresh = targets.filter { waiting.add(it.id) }
        if (fresh.isEmpty()) return
        queue.addAll(fresh)
        _progress.value = _progress.value.copy(running = true, total = _progress.value.total + fresh.size)
        if (job?.isActive != true) job = scope.launch { work(context.applicationContext) }
    }

    suspend fun analyzeLibrary(context: Context, database: MusicDatabase, stems: Boolean = false) {
        analyze(context, database.localSongDurations().map { Target(it.id, it.duration, it.title, stems) })
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun work(context: Context) {
        val analyzer = TrackAnalyzer(context, AudioCache)
        val cancelReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = cancel()
        }
        ContextCompat.registerReceiver(context, cancelReceiver, IntentFilter(ACTION_CANCEL), ContextCompat.RECEIVER_NOT_EXPORTED)
        val quality = context.dataStore.data.first()[StemQualityKey].toEnum(StemQuality.FAST)
        try {
            while (true) {
                val song = queue.poll() ?: break
                _progress.value = _progress.value.copy(current = song.title)
                notify(context, _progress.value)
                val uri = if (song.id.startsWith("content://") || song.id.startsWith("file://")) song.id.toUri()
                else "https://music.youtube.com/watch?v=${song.id}".toUri()
                analyzer.request(song.id, uri, song.durationSeconds.toDouble())
                awaitAnalysis(analyzer, song.id)
                if (song.stems) {
                    withContext(Dispatchers.IO) {
                        val split = StemSeparator.separate(context, song.id, uri, song.durationSeconds, quality) { coroutineContext.isActive }
                        if (split) analyzer.applyStemMask(song.id)
                    }
                }
                _progress.value = _progress.value.copy(done = _progress.value.done + 1)
            }
        } finally {
            runCatching { context.unregisterReceiver(cancelReceiver) }
            analyzer.release()
            queue.clear()
            _progress.value = Progress()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        }
    }

    private suspend fun awaitAnalysis(analyzer: TrackAnalyzer, id: String) {
        var waited = 0L
        while (!analyzer.isAnalysed(id) && waited < PER_SONG_TIMEOUT_MS) {
            // Nothing queued and nothing stored: the request was skipped (e.g. phone too hot).
            if (waited > 0 && !analyzer.isAnalysing(id)) break
            delay(POLL_MS)
            waited += POLL_MS
        }
    }

    private fun notify(context: Context, progress: Progress) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.analysis_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.analysis_title))
            .setContentText(context.getString(R.string.analysis_progress, progress.done, progress.total, progress.current.orEmpty()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.analysis_advice)))
            .setProgress(progress.total, progress.done, false)
            .addAction(
                0,
                context.getString(R.string.analysis_cancel),
                PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_CANCEL).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        // Without the notification permission this is a no-op; the in-app banner still shows.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
