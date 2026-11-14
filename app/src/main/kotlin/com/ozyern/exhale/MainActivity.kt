/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.ozyern.exhale.constants.AppLanguageKey
import com.ozyern.exhale.constants.DisableScreenshotKey
import com.ozyern.exhale.constants.SYSTEM_DEFAULT
import com.ozyern.exhale.constants.StopMusicOnTaskClearKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.di.ValueProvider
import com.ozyern.exhale.history.ListenHistoryRepository
import com.ozyern.exhale.history.ListenHistorySettingsRepository
import com.ozyern.exhale.library.ObservePlaylists
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.playback.MusicService
import com.ozyern.exhale.playback.PlaybackServiceConnector
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.setAppLocale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLAY_INTRO = "com.ozyern.exhale.extra.PLAY_INTRO"
        internal const val ACTION_SEARCH = "com.ozyern.exhale.action.SEARCH"
        internal const val ACTION_LIBRARY = "com.ozyern.exhale.action.LIBRARY"
    }

    private val database: MusicDatabase by inject()
    private val downloadUtil: DownloadUtil by inject()
    private val syncUtils: SyncUtils by inject()
    private val listenHistoryRepository: ListenHistoryRepository by inject()
    private val listenHistorySettingsRepository: ListenHistorySettingsRepository by inject()
    private val observePlaylists: ObservePlaylists by inject()
    private val listenTogetherManager: com.ozyern.exhale.listentogether.ListenTogetherManager by inject()
    private val partyManager: com.ozyern.exhale.jam.JamManager by inject()
    private val playbackServiceConnector: PlaybackServiceConnector by inject()

    private var pendingIntent: Intent? = null

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var activityStarted = false
    private var pendingPlaybackBind: Runnable? = null

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (MusicService.isRunning) {
            bindPlaybackService()
        } else {
            val bind = Runnable {
                pendingPlaybackBind = null
                if (activityStarted) bindPlaybackService()
            }
            pendingPlaybackBind = bind
            window.decorView.postDelayed(bind, 32L)
        }
    }

    override fun onStop() {
        activityStarted = false
        pendingPlaybackBind?.let(window.decorView::removeCallbacks)
        pendingPlaybackBind = null
        playbackServiceConnector.unbind(this)
        super.onStop()
    }

    private fun bindPlaybackService() {
        // The service promotes itself when bound, so bind only while this activity is visible.
        playbackServiceConnector.bind(this, lifecycleScope) { connection ->
            playerConnection = connection
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Handle back navigation here; swiping the task away is handled by the service.
        if (dataStore.get(StopMusicOnTaskClearKey, false) &&
            playerConnection?.isPlaying?.value == true &&
            isFinishing
        ) {
            // onStop already unbound the connection; only stop the service here.
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    internal fun takePendingIntent(): Intent? {
        val current = pendingIntent
        pendingIntent = null
        return current
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Retain the intent until Compose's listener is ready to handle it.
        pendingIntent = intent
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            com.ozyern.exhale.ui.component.MenuAnchor.lastDown =
                androidx.compose.ui.geometry.Offset(ev.x, ev.y)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale = dataStore[AppLanguageKey]
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Cold launch from the launcher only (WelcomeActivity hands off with this extra);
        // notification/deep-link opens go straight to the app.
        // The system splash carries the logo now, so the intro animation stays off.
        val playIntro = false && savedInstanceState == null && intent.getBooleanExtra(EXTRA_PLAY_INTRO, false)
        setContent {
            var showIntro by rememberSaveable { mutableStateOf(playIntro) }
            Box(Modifier.fillMaxSize()) {
                AppRootContent(
                    activity = this@MainActivity,
                    intentRouter = deepLinkRouter,
                    playerConnection = playerConnection,
                    database = ValueProvider { database },
                    downloadUtil = ValueProvider { downloadUtil },
                    syncUtils = ValueProvider { syncUtils },
                    listenHistoryRepository = ValueProvider { listenHistoryRepository },
                    listenHistorySettingsRepository = ValueProvider { listenHistorySettingsRepository },
                    observePlaylists = observePlaylists,
                )
                if (showIntro) com.ozyern.exhale.ui.component.ExhaleIntro(onFinished = { showIntro = false })
            }
        }
        window.decorView.doOnPreDraw {
            // A frame-clock callback runs before drawing. Post first, then wait for
            // the queue to become idle so optional startup never competes for that frame.
            window.decorView.post {
                android.os.Looper.myQueue().addIdleHandler {
                    (application as App).initializeAfterFirstDraw()
                    false
                }
            }
        }
    }

    internal val deepLinkRouter by lazy {
        AppIntentRouter(
            this,
            { playerConnection },
            ValueProvider { listenTogetherManager },
            ValueProvider { partyManager },
        )
    }
}
