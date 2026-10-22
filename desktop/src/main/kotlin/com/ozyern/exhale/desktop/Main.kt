/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.jetbrains.WindowDecorations
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.AppLifecycle
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.AppStorage
import com.ozyern.exhale.desktop.data.ContentLocale
import com.ozyern.exhale.desktop.data.NetworkProxy
import com.ozyern.exhale.desktop.integrations.DiscordPresence
import com.ozyern.exhale.desktop.integrations.Integrations
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.MediaKeys
import com.ozyern.exhale.desktop.player.WindowsMediaSession
import com.ozyern.exhale.desktop.ui.AppShortcuts
import com.ozyern.exhale.desktop.ui.ExhaleApp
import com.ozyern.exhale.desktop.ui.ExhaleTheme
import com.ozyern.exhale.desktop.ui.LocalWindowState
import com.ozyern.exhale.desktop.ui.ThemeSeed
import com.ozyern.exhale.desktop.ui.account.SignInBrowser
import com.ozyern.exhale.desktop.ui.shell.ExhaleTitleBar
import com.ozyern.exhale.desktop.ui.shell.ExhaleTray
import com.ozyern.exhale.desktop.ui.shell.LocalTitleBarHeight
import com.ozyern.exhale.desktop.ui.shell.ResizeHandles
import com.ozyern.exhale.desktop.ui.shell.TitleBarHeight
import com.ozyern.exhale.desktop.ui.shell.WindowChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image
import java.awt.GraphicsEnvironment

/** Title bar + taskbar icon. The installer's icon is icons/exhale.ico, set in build.gradle.kts. */
internal val appIcon: BitmapPainter by lazy {
    val bytes = object {}.javaClass.getResourceAsStream("/exhale.png")!!.use { it.readAllBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

fun main() {
    // Direct3D 12 on Windows: the glass, the moving artwork shader and the blurred lyric lines all
    // render on the GPU at the display's refresh rate. `-Dskiko.renderApi=OPENGL` still overrides it.
    if (System.getProperty("skiko.renderApi") == null) System.setProperty("skiko.renderApi", "DIRECT3D")
    // On a laptop with two GPUs, render on the fast one. Windows otherwise hands a Java app the
    // integrated chip, which can't keep refraction and blur at a 2560×1600 / 240 Hz panel's pace.
    // `-Dskiko.directx.gpu.priority=integrated` puts it back for battery life.
    if (System.getProperty("skiko.directx.gpu.priority") == null) System.setProperty("skiko.directx.gpu.priority", "discrete")
    // The FLAC page's Creative Commons shelf, from the build's secrets like Last.fm's keys.
    com.ozyern.exhale.openflac.OpenFlac.jamendoClientId = System.getProperty("exhale.jamendo.id").orEmpty()

    // The listener's languages (or Content overrides) decide hl/gl before any request leaves — not
    // the JVM default, which let YouTube localise purely by IP. Half a second of setup that has nothing
    // to do with the window, so it runs while the window and the GPU come up; nothing that talks to
    // YouTube is composed until it has finished (see [boot] below).
    val boot = kotlin.concurrent.thread(name = "exhale-boot", isDaemon = true) {
        ContentLocale.apply()
        NetworkProxy.apply()
        AccountSession.applySaved()
    }

    // Artwork cache on disk, sized by Storage → Image cache.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(AppStorage.imageCacheDir.toOkioPath())
                    .maxSizeBytes(AppSettings.current.imageCacheMb * 1024L * 1024L)
                    .build()
            }
            // Decoded artwork lives in native memory. Uncapped, Coil sizes this cache from the JVM's heap
            // limit — hundreds of megabytes of bitmaps held for covers long scrolled past.
            .memoryCache { MemoryCache.Builder().maxSizeBytes(160L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }

    application {
        val scope = rememberCoroutineScope()
        // Null until the player exists. Creating it loads libmpv and starts its engine, which is worth
        // doing beside the window coming up rather than in front of it.
        val player by produceState<Result<DesktopPlayer>?>(null) {
            value = withContext(Dispatchers.Default) {
                boot.join()
                runCatching { DesktopPlayer(scope) }
            }
        }
        LaunchedEffect(player) { player?.getOrNull()?.let { Integrations.start(it, scope) } }
        DisposableEffect(Unit) {
            onDispose {
                player?.getOrNull()?.close()
                DiscordPresence.close()
            }
        }
        val settings by AppSettings.state.collectAsState()
        val artworkSeed by ThemeSeed.artwork.collectAsState()
        val windowState = rememberWindowState(width = 1320.dp, height = 860.dp)
        val closeApp: () -> Unit = {
            SignInBrowser.shutdown()
            exitApplication()
        }
        // An update's installer closes Exhale the same way the window's close button does.
        AppLifecycle.exit = closeApp

        // Closed with "Keep playing when closed" on, the window only hides: the music, the media
        // session and the integrations carry on, and the notification area brings it back.
        var windowVisible by remember { mutableStateOf(true) }
        val closeWindow: () -> Unit = {
            if (AppSettings.current.closeToTray) windowVisible = false else closeApp()
        }
        if (settings.trayIcon || !windowVisible) {
            ExhaleTray(player?.getOrNull(), onShow = { windowVisible = true }, onQuit = closeApp)
        }

        Window(
            visible = windowVisible,
            onCloseRequest = closeWindow,
            title = "Exhale",
            icon = appIcon,
            state = windowState,
            // Windows' title bar is replaced by Exhale's own; see WindowChrome.
            undecorated = !WindowChrome.nativeSupported,
            onPreviewKeyEvent = { AppShortcuts.dispatch(it) },
            // After the focused field has had its say: Space belongs to whatever is being typed into.
            onKeyEvent = { AppShortcuts.dispatchPlain(it) },
        ) {
            window.minimumSize = java.awt.Dimension(960, 640)

            // Join Windows' media list, so Exhale appears in the quick settings panel and the keyboard's
            // media keys reach it from any window. Needs the window itself, which only exists here.
            LaunchedEffect(player) {
                val playing = player?.getOrNull() ?: return@LaunchedEffect
                val handle = runCatching { com.sun.jna.Native.getComponentID(window) }.getOrNull() ?: return@LaunchedEffect
                val joined = WindowsMediaSession.start(handle) { button ->
                    when (button) {
                        WindowsMediaSession.MediaButton.Play, WindowsMediaSession.MediaButton.Pause -> playing.togglePlayPause()
                        WindowsMediaSession.MediaButton.Next -> playing.next()
                        WindowsMediaSession.MediaButton.Previous -> playing.previous()
                    }
                }
                // Windows now delivers the keys as session buttons; claiming them as hotkeys too would
                // act on every press twice.
                if (joined) MediaKeys.stop()
            }
            var titleBar by remember { mutableStateOf<WindowDecorations.CustomTitleBar?>(null) }
            // The native caption strip is sized in window units; Interface size scales our dp.
            val barHeight = TitleBarHeight.value * settings.uiScale
            DisposableEffect(barHeight) {
                if (WindowChrome.nativeSupported) {
                    titleBar = WindowChrome.install(window, barHeight)
                } else {
                    // An undecorated window maximises over the taskbar unless told where to stop.
                    window.maximizedBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                }
                onDispose { }
            }

            CompositionLocalProvider(LocalWindowState provides windowState, LocalTitleBarHeight provides TitleBarHeight) {
                ExhaleTheme(settings, artworkSeed) {
                    // Surface rather than a background modifier: it also sets LocalContentColor,
                    // without which any Text that doesn't pass a colour renders black.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            player?.fold(
                                onSuccess = { ExhaleApp(it) },
                                onFailure = { error -> PlayerUnavailable(error) },
                            )
                            ExhaleTitleBar(titleBar, windowState, onClose = closeWindow)
                            if (!WindowChrome.nativeSupported && windowState.placement == WindowPlacement.Floating) {
                                ResizeHandles(window)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerUnavailable(error: Throwable) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Couldn't start the audio engine: ${error.message}\n\n" +
                "libmpv-2.dll is missing — run desktop/scripts/fetch-libmpv.ps1.",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
