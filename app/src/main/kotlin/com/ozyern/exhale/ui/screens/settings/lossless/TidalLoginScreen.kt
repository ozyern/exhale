/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0 (ui/screens/settings/TidalLoginScreen.kt).
 *
 * Tidal sign-in. Starts the official PKCE authorization-code flow inside a WebView (durable
 * refresh token, can unlock HiRes); if that fails it falls back to capturing the live Bearer
 * token that the Tidal web player (listen.tidal.com) sends to the API. Persists the Tidal
 * session directly to DataStore.
 */

package com.ozyern.exhale.ui.screens.settings.lossless

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAccessTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAccountNameKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAuthFlowKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalCountryCodeKey
import com.ozyern.exhale.playback.lossless.sources.constants.LosslessStreamingEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalNeedsReloginKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalRefreshTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalSubscriptionKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalSubscriptionStatus
import com.ozyern.exhale.playback.lossless.sources.constants.TidalTokenExpiryKey
import com.ozyern.exhale.playback.lossless.sources.constants.TidalUserIdKey
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalAccountManager
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val WEB_PLAYER_URL = "https://listen.tidal.com"

private val BEARER_HOOK_JS =
    """
    javascript:(function(){
      if(window.__atTidalHook)return;window.__atTidalHook=1;
      function send(h){try{if(!h)return;var m=/Bearer\s+([A-Za-z0-9._\-]+)/i.exec(h);if(m&&m[1]&&m[1].length>20){TidalAuth.onBearer(m[1]);}}catch(e){}}
      try{
        var of=window.fetch;
        if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){var hh=a.headers;var v=hh.get?hh.get('Authorization'):(hh['Authorization']||hh['authorization']);send(v);}}catch(e){}return of.apply(this,arguments);};}
      }catch(e){}
      try{
        var os=XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{if(k&&String(k).toLowerCase()==='authorization'){send(v);}}catch(e){}return os.apply(this,arguments);};
      }catch(e){}
    })()
    """.trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TidalLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pkce = remember { TidalAccountManager.buildPkceChallenge() }
    val handled = remember { AtomicBoolean(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun finishLogin(
        token: TidalAccountManager.TokenResult,
        flow: String,
    ) {
        scope.launch {
            val sub =
                token.userId?.let { uid ->
                    withContext(Dispatchers.IO) {
                        runCatching { TidalAccountManager.fetchSubscription(token.accessToken, uid) }
                            .getOrDefault(TidalAccountManager.Subscription.UNKNOWN)
                    }
                } ?: TidalAccountManager.Subscription.UNKNOWN
            val status =
                when (sub) {
                    TidalAccountManager.Subscription.PREMIUM -> TidalSubscriptionStatus.PREMIUM
                    TidalAccountManager.Subscription.FREE -> TidalSubscriptionStatus.FREE
                    TidalAccountManager.Subscription.UNKNOWN -> TidalSubscriptionStatus.UNKNOWN
                }
            context.dataStore.edit { prefs ->
                prefs[TidalAccessTokenKey] = token.accessToken
                if (token.refreshToken != null) {
                    prefs[TidalRefreshTokenKey] = token.refreshToken
                } else {
                    prefs.remove(TidalRefreshTokenKey)
                }
                prefs[TidalTokenExpiryKey] = token.expiresAtMillis
                prefs[TidalAccountNameKey] = token.username ?: "Tidal"
                token.userId?.let { prefs[TidalUserIdKey] = it }
                token.countryCode?.let { prefs[TidalCountryCodeKey] = it }
                prefs[TidalAuthFlowKey] = flow
                prefs[TidalSubscriptionKey] = status.name
                prefs[TidalNeedsReloginKey] = false
                prefs[TidalEnabledKey] = true
                prefs[LosslessStreamingEnabledKey] = true
            }
            toast(context.getString(R.string.lossless_tidal_login_success))
            if (status == TidalSubscriptionStatus.FREE) {
                toast(context.getString(R.string.lossless_tidal_account_free_warning))
            }
            onBack()
        }
    }

    fun switchToCapture(view: WebView) {
        toast(context.getString(R.string.lossless_tidal_login_webplayer_fallback))
        view.loadUrl(WEB_PLAYER_URL)
    }

    fun handleRedirect(
        view: WebView,
        url: String?,
    ): Boolean {
        if (url == null || !url.startsWith(TidalAccountManager.PKCE_REDIRECT_URI)) return false
        if (!handled.compareAndSet(false, true)) return true
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val code = uri?.getQueryParameter("code")
        val error = uri?.getQueryParameter("error")
        if (code.isNullOrBlank()) {
            handled.set(false)
            Timber.tag("TidalLogin").w("PKCE redirect without code (error=%s)", error)
            switchToCapture(view)
            return true
        }
        scope.launch {
            val token =
                withContext(Dispatchers.IO) {
                    TidalAccountManager.exchangePkceCode(code, pkce.verifier, pkce.uniqueKey)
                }
            if (token != null) {
                finishLogin(token, TidalAccountManager.FLOW_PKCE)
            } else {
                handled.set(false)
                switchToCapture(view)
            }
        }
        return true
    }

    AuthWebViewScreen(
        title = stringResource(R.string.lossless_tidal_login),
        subtitle = stringResource(R.string.lossless_auth_webview_tidal_subtitle),
        onBack = onBack,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            handleRedirect(view, url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            url: String?,
                        ): Boolean = handleRedirect(view, url)

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            if (url?.contains("tidal.com", ignoreCase = true) == true &&
                                url.contains("listen", ignoreCase = true)
                            ) {
                                view.loadUrl(BEARER_HOOK_JS)
                            }
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onBearer(bearer: String?) {
                            if (bearer.isNullOrBlank()) return
                            if (!handled.compareAndSet(false, true)) return
                            scope.launch {
                                val token =
                                    withContext(Dispatchers.IO) {
                                        TidalAccountManager.buildSessionFromBearer(bearer)
                                    }
                                if (token != null) {
                                    finishLogin(token, TidalAccountManager.FLOW_WEBCAPTURE)
                                } else {
                                    handled.set(false)
                                }
                            }
                        }
                    },
                    "TidalAuth",
                )
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    loadUrl(pkce.authUrl)
                }
            }
        },
    )
}
