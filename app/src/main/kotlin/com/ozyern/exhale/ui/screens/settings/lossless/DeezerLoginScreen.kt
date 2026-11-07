/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0 (ui/screens/settings/DeezerLoginScreen.kt).
 *
 * Deezer sign-in. Loads deezer.com/login in a WebView and captures the `arl` session cookie the
 * site sets on a signed-in browser, verifies it against the gateway and persists it.
 *
 * Deezer is geo-fenced in a number of countries (India among them): www.deezer.com redirects the
 * login page away and the `arl` cookie never appears. The manual ARL entry below the web view is
 * the escape hatch — sign in from any browser (with a VPN where needed), copy the `arl` cookie
 * value and paste it here; verification and persistence are identical to the WebView path.
 */

package com.ozyern.exhale.ui.screens.settings.lossless

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAccountNameKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAccountPremiumKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerArlKey
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.LosslessStreamingEnabledKey
import com.ozyern.exhale.playback.lossless.sources.deezer.DeezerAudioProvider
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGIN_URL = "https://www.deezer.com/login"
private const val COOKIE_ORIGIN = "https://www.deezer.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handled = remember { AtomicBoolean(false) }

    // Manual ARL entry state (region-locked users).
    var manualArl by remember { mutableStateOf("") }
    var manualVerifying by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun readArl(): String? =
        CookieManager
            .getInstance()
            .getCookie(COOKIE_ORIGIN)
            ?.split(';')
            ?.firstNotNullOfOrNull { part ->
                val (name, value) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
                value.trim().takeIf { name.trim().equals("arl", ignoreCase = true) && it.isNotEmpty() }
            }

    suspend fun persist(arl: String): Boolean {
        val info = withContext(Dispatchers.IO) { DeezerAudioProvider.verifyArl(arl) }
        if (info == null) {
            toast(context.getString(R.string.lossless_deezer_arl_invalid))
            return false
        }
        context.dataStore.edit { prefs ->
            prefs[DeezerArlKey] = arl
            prefs[DeezerAccountNameKey] = info.name
            prefs[DeezerAccountPremiumKey] = info.lossless
            prefs[DeezerEnabledKey] = true
            prefs[LosslessStreamingEnabledKey] = true
        }
        DeezerAudioProvider.setManualArl(arl, info.lossless)
        toast(context.getString(R.string.lossless_deezer_login_success, info.name))
        return true
    }

    fun finishLogin(arl: String) {
        scope.launch {
            if (persist(arl)) onBack() else handled.set(false)
        }
    }

    fun submitManualArl() {
        val arl = manualArl.trim()
        if (arl.length < 20) {
            toast(context.getString(R.string.lossless_deezer_arl_invalid))
            return
        }
        if (manualVerifying) return
        manualVerifying = true
        scope.launch {
            try {
                if (persist(arl)) onBack()
            } finally {
                manualVerifying = false
            }
        }
    }

    AuthWebViewScreen(
        title = stringResource(R.string.lossless_deezer_login),
        subtitle = stringResource(R.string.lossless_auth_webview_deezer_subtitle),
        onBack = onBack,
        footer = {
            OutlinedTextField(
                value = manualArl,
                onValueChange = { manualArl = it },
                label = { Text(stringResource(R.string.lossless_deezer_manual_arl)) },
                supportingText = { Text(stringResource(R.string.lossless_deezer_manual_arl_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { submitManualArl() },
                enabled = !manualVerifying && manualArl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (manualVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.lossless_deezer_manual_arl_apply))
            }
        },
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            val arl = readArl() ?: return
                            if (!handled.compareAndSet(false, true)) return
                            finishLogin(arl)
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(LOGIN_URL)
                }
            }
        },
    )
}
