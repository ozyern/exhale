/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.BrowserSignIn
import com.ozyern.exhale.desktop.data.SignInBrowserApp
import com.ozyern.exhale.desktop.ui.ExhaleTheme
import com.ozyern.exhale.desktop.ui.ThemeSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private sealed interface DiscordStatus {
    data object Opening : DiscordStatus
    data class Waiting(val browser: SignInBrowserApp) : DiscordStatus
    data object Finishing : DiscordStatus
    data class Failed(val message: String) : DiscordStatus
}

/**
 * Log in to Discord in the listener's own browser, so rich presence has an account to post as.
 *
 * Discord's rich presence is posted over its gateway as the account itself, which needs the account's
 * own token — there is no sign-in-with-Discord that grants one. So the browser opens on Discord's
 * ordinary login page with a profile of its own, and Exhale reads the token the web client is given
 * once it exists. This window is only company while that happens.
 */
@Composable
fun DiscordLoginWindow(onClose: () -> Unit) {
    var status by remember { mutableStateOf<DiscordStatus>(DiscordStatus.Opening) }
    var attempt by remember { mutableIntStateOf(0) }
    val latestClose by rememberUpdatedState(onClose)

    LaunchedEffect(attempt) {
        status = DiscordStatus.Opening
        val browser = withContext(Dispatchers.IO) { BrowserSignIn.findBrowser() }
        if (browser == null) {
            status = DiscordStatus.Failed(
                "Logging in needs Chrome, Microsoft Edge, Brave or Vivaldi, and none was found on this PC. " +
                    "Edge comes with Windows — reinstalling it fixes this.",
            )
            return@LaunchedEffect
        }
        status = DiscordStatus.Waiting(browser)
        BrowserSignIn.discordToken(browser, onCaptured = { status = DiscordStatus.Finishing })
            .onSuccess { token ->
                val name = withContext(Dispatchers.IO) { discordUsername(token) }
                AppSettings.update { it.copy(discordToken = token, discordUsername = name, discordEnabled = true) }
                latestClose()
            }
            .onFailure { status = DiscordStatus.Failed(it.message ?: "The log-in didn't finish.") }
    }

    Window(
        onCloseRequest = onClose,
        title = "Log in with Discord",
        icon = appIcon,
        resizable = false,
        state = rememberWindowState(width = 460.dp, height = 520.dp, position = WindowPosition(Alignment.Center)),
    ) {
        ExhaleTheme(AppSettings.current, ThemeSeed.artwork.value) {
            val scheme = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxSize(), color = scheme.background) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.radialGradient(listOf(DiscordBlurple.copy(alpha = 0.34f), Color.Transparent))),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        when (status) {
                            is DiscordStatus.Opening -> "Opening your browser…"
                            is DiscordStatus.Waiting -> "Log in to Discord"
                            is DiscordStatus.Finishing -> "Almost there…"
                            is DiscordStatus.Failed -> "That didn't work"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (val shown = status) {
                            is DiscordStatus.Opening -> "Finding the browser to log in with."
                            is DiscordStatus.Waiting ->
                                "${shown.browser.name} is open on Discord's login page. Log in there and this window " +
                                    "will finish on its own — it closes as soon as Discord signs you in."
                            is DiscordStatus.Finishing -> "Reading your account, then closing the browser."
                            is DiscordStatus.Failed -> shown.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    if (status is DiscordStatus.Failed) {
                        Button(onClick = { attempt++ }) { Text("Try again") }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onClose) { Text("Not now") }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        TextButton(onClick = onClose) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

private val DiscordBlurple = Color(0xFF5865F2)

/** The name to show in Settings, so the account that presence posts as is named rather than a token. */
private fun discordUsername(token: String): String? = runCatching {
    val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    val request = Request.Builder()
        .url("https://discord.com/api/v10/users/@me")
        .header("Authorization", token)
        .build()
    val body = http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@runCatching null
        response.body.string()
    }
    val user = JSONObject(body)
    val handle = user.optString("username").takeIf { it.isNotBlank() } ?: return@runCatching null
    val discriminator = user.optString("discriminator").takeIf { it.isNotBlank() && it != "0" }
    if (discriminator != null) "$handle#$discriminator" else handle
}.getOrNull()
