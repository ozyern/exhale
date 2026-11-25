/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.BrowserSignIn
import com.ozyern.exhale.desktop.data.SignInBrowserApp
import com.ozyern.exhale.desktop.ui.ExhaleTheme
import com.ozyern.exhale.desktop.ui.ThemeSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.IProgressHandler
import org.cef.CefApp
import org.cef.network.CefCookieManager
import java.io.File

/**
 * Chromium inside Exhale, once per process — now only for the Discord login. jcefmaven downloads the
 * natives (~100 MB) into `%APPDATA%\Exhale\jcef` the first time it's needed.
 */
object SignInBrowser {
    private val installDir = File(
        System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config"),
        "Exhale/jcef",
    )

    @Volatile private var app: CefApp? = null

    val isRunning: Boolean get() = app != null

    fun ensure(onProgress: (EnumProgress, Float) -> Unit): CefApp {
        app?.let { return it }
        synchronized(this) {
            app?.let { return it }
            val built = CefAppBuilder().apply {
                setInstallDir(installDir)
                setProgressHandler(IProgressHandler { state, percent -> onProgress(state, percent) })
                cefSettings.user_agent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
            }.build()
            AccountSession.clearBrowserCookies = { CefCookieManager.getGlobalManager()?.deleteCookies("", "") }
            app = built
            return built
        }
    }

    fun shutdown() {
        runCatching { app?.dispose() }
        app = null
    }
}

private sealed interface SignInStatus {
    data object Opening : SignInStatus
    data class Waiting(val browser: SignInBrowserApp) : SignInStatus
    data object Finishing : SignInStatus
    data class Failed(val message: String) : SignInStatus
}

/**
 * Sign in with Google in the listener's own browser. This small window keeps them company while
 * they do: which browser opened, what happens next, and a way to start over or give up.
 */
@Composable
fun SignInWindow(onClose: () -> Unit, onSignedIn: () -> Unit) {
    var status by remember { mutableStateOf<SignInStatus>(SignInStatus.Opening) }
    var attempt by remember { mutableIntStateOf(0) }
    val latestSignedIn by rememberUpdatedState(onSignedIn)

    // A new attempt cancels the last one, which closes its browser window first.
    LaunchedEffect(attempt) {
        status = SignInStatus.Opening
        val browser = withContext(Dispatchers.IO) { BrowserSignIn.findBrowser() }
        if (browser == null) {
            status = SignInStatus.Failed(
                "Signing in needs Chrome, Microsoft Edge, Brave or Vivaldi, and none was found on this PC. Edge comes with Windows — reinstalling it fixes this.",
            )
            return@LaunchedEffect
        }
        status = SignInStatus.Waiting(browser)
        BrowserSignIn.signIn(browser, onCaptured = { status = SignInStatus.Finishing })
            .onSuccess { session ->
                AccountSession.completeSignIn(session.cookie, session.dataSyncId, session.visitorData)
                latestSignedIn()
            }
            .onFailure { status = SignInStatus.Failed(it.message ?: "Sign-in didn't finish.") }
    }

    Window(
        onCloseRequest = onClose,
        title = "Sign in to YouTube Music",
        icon = appIcon,
        resizable = false,
        state = rememberWindowState(width = 460.dp, height = 560.dp, position = WindowPosition(Alignment.Center)),
    ) {
        ExhaleTheme(AppSettings.current, ThemeSeed.artwork.value) {
            val scheme = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxSize(), color = scheme.background) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.32f), Color.Transparent))))
                        Image(appIcon, contentDescription = null, modifier = Modifier.size(76.dp).clip(CircleShape))
                    }
                    Spacer(Modifier.height(20.dp))
                    AnimatedContent(
                        targetState = status,
                        contentKey = { it::class },
                        transitionSpec = {
                            (fadeIn(tween(240, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.86f, stiffness = 380f), initialScale = 0.96f)) togetherWith fadeOut(tween(120))
                        },
                        label = "sign_in_status",
                    ) { shown ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (shown) {
                                SignInStatus.Opening -> {
                                    Headline("Opening your browser…")
                                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                                }
                                is SignInStatus.Waiting -> {
                                    Headline("Continue in ${shown.browser.name}")
                                    Body("Sign in with Google in the ${shown.browser.name} window that just opened. Exhale picks up your account the moment YouTube Music loads, and closes the window for you.")
                                    shown.browser.fallbackFrom?.let {
                                        Note("$it can't hand a sign-in back to apps, so this opens in ${shown.browser.name}.")
                                    }
                                    Note("It's a separate window with a fresh profile: your usual browser, tabs and passwords aren't touched, and nothing from it is kept.")
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Waiting for you to sign in…", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                                    }
                                }
                                SignInStatus.Finishing -> {
                                    Headline("You're signed in")
                                    Body("Getting your library ready…")
                                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                                }
                                is SignInStatus.Failed -> {
                                    Headline("Sign-in didn't finish")
                                    Body(shown.message)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    when (status) {
                        is SignInStatus.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { attempt++ }) { Text("Open the window again") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = onClose) { Text("Cancel") }
                        }
                        is SignInStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = onClose) { Text("Close") }
                            Spacer(Modifier.width(10.dp))
                            Button(onClick = { attempt++ }) { Text("Try again") }
                        }
                        else -> Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
