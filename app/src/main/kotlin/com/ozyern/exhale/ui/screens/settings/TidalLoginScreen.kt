/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * TIDAL sign-in. TIDAL's own PKCE login in a WebView gives a session that renews itself; if that
 * login fails, the TIDAL web player is opened instead and its session is taken (an hour, then the
 * listener signs in again). Ported from master's TidalLoginScreen (from ArchiveTune, GPL-3.0).
 */

package com.ozyern.exhale.ui.screens.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.utils.tidal.TidalAccount
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/** Reads the Bearer token the TIDAL web player sends with its own API calls. */
private val BEARER_HOOK_JS =
    """
    javascript:(function(){
      if(window.__exTidalHook)return;window.__exTidalHook=1;
      function send(h){try{if(!h)return;var m=/Bearer\s+([A-Za-z0-9._\-]+)/i.exec(h);if(m&&m[1]&&m[1].length>20){TidalAuth.onBearer(m[1]);}}catch(e){}}
      try{var of=window.fetch;if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){var hh=a.headers;var v=hh.get?hh.get('Authorization'):(hh['Authorization']||hh['authorization']);send(v);}}catch(e){}return of.apply(this,arguments);};}}catch(e){}
      try{var os=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{if(k&&String(k).toLowerCase()==='authorization'){send(v);}}catch(e){}return os.apply(this,arguments);};}catch(e){}
    })()
    """.trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TidalLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pkce = remember { TidalAccount.buildPkceChallenge() }
    val handled = remember { AtomicBoolean(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun finish(token: TidalAccount.TokenResult, flow: String) {
        scope.launch {
            TidalAccount.save(context, token, flow)
            toast("Signed in to TIDAL" + (token.username?.let { " as $it" } ?: ""))
            navController.navigateUp()
        }
    }

    fun switchToWebPlayer(view: WebView) {
        toast("Sign in on the TIDAL web player instead")
        view.loadUrl(TidalAccount.WEB_PLAYER_URL)
    }

    fun handleRedirect(view: WebView, url: String?): Boolean {
        if (url == null || !url.startsWith(TidalAccount.PKCE_REDIRECT_URI)) return false
        if (!handled.compareAndSet(false, true)) return true
        val code = runCatching { Uri.parse(url).getQueryParameter("code") }.getOrNull()
        if (code.isNullOrBlank()) {
            handled.set(false)
            Timber.tag("TidalLogin").w("PKCE redirect without a code")
            switchToWebPlayer(view)
            return true
        }
        scope.launch {
            val token = TidalAccount.exchangePkceCode(code, pkce)
            if (token != null) {
                finish(token, TidalAccount.FLOW_PKCE)
            } else {
                handled.set(false)
                switchToWebPlayer(view)
            }
        }
        return true
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    SettingsScaffold(
        navController = navController,
        title = "Sign in to TIDAL",
        titleAlpha = 1f,
        pageColor = AppTheme.colorScheme.background,
    ) {
        AndroidView(
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            handleRedirect(view, url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean = handleRedirect(view, url)

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url?.contains("listen.tidal.com", ignoreCase = true) == true) view.loadUrl(BEARER_HOOK_JS)
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onBearer(bearer: String?) {
                                if (bearer.isNullOrBlank() || !handled.compareAndSet(false, true)) return
                                scope.launch {
                                    val token = TidalAccount.sessionFromBearer(bearer)
                                    if (token != null) finish(token, TidalAccount.FLOW_WEBCAPTURE) else handled.set(false)
                                }
                            }
                        },
                        "TidalAuth",
                    )
                    webView = this
                    loadUrl(pkce.authUrl)
                }
            },
        )
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
