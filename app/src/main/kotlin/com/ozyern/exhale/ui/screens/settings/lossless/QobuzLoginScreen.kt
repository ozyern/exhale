/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0 (ui/screens/settings/QobuzLoginScreen.kt).
 *
 * Qobuz sign-in. The user logs into play.qobuz.com; a JS hook captures the X-User-Auth-Token,
 * X-App-Id, and scrapes the app_secret (32-char hex) from the loaded bundle scripts. When all
 * three are found the session is saved automatically with no manual input. If the secret cannot
 * be scraped a fallback dialog lets the user paste it manually.
 */

package com.ozyern.exhale.ui.screens.settings.lossless

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.lossless.sources.constants.LosslessStreamingEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzTokensKey
import com.ozyern.exhale.playback.lossless.sources.qobuz.QobuzToken
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val QOBUZ_WEB_PLAYER_URL = "https://play.qobuz.com/login"

private val QOBUZ_HOOK_JS =
    """
    javascript:(function(){
      if(window.__atQobuzHook)return;window.__atQobuzHook=1;
      var tok=null,app=null;
      function pushCreds(){try{if(tok&&app){QobuzAuth.onCredentials(tok,app);}}catch(e){}}
      function scanHeaders(h){try{if(!h)return;
        var t=h.get?h.get('X-User-Auth-Token'):(h['X-User-Auth-Token']||h['x-user-auth-token']);
        var a=h.get?h.get('X-App-Id'):(h['X-App-Id']||h['x-app-id']);
        if(t&&t.length>20){tok=t;} if(a&&a.length>3){app=a;} pushCreds();
      }catch(e){}}
      try{var of=window.fetch;if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){scanHeaders(a.headers);}}catch(e){}return of.apply(this,arguments);};}}catch(e){}
      try{var os=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{var kk=String(k).toLowerCase();if(kk==='x-user-auth-token'&&v&&v.length>20){tok=v;}if(kk==='x-app-id'&&v){app=v;}pushCreds();}catch(e){}return os.apply(this,arguments);};}catch(e){}
      // Scrape the app_secret from bundle scripts: it is a 32-char lowercase hex string that
      // appears as a standalone value (surrounded by quotes or punctuation) in the JS bundle.
      try{
        var scripts=document.querySelectorAll('script[src]');
        var scraped=false;
        function scanBundle(js){
          if(scraped)return;
          var matches=js.match(/[^a-fA-F0-9]([a-f0-9]{32})[^a-fA-F0-9]/g);
          if(!matches)return;
          var seen={};
          for(var i=0;i<matches.length;i++){
            var m=matches[i].replace(/[^a-f0-9]/g,'');
            if(m.length===32&&!seen[m]){seen[m]=1;
              // Skip known non-secret patterns (md5 of empty string, common constants).
              if(m==='d41d8cd98f00b204e9800998ecf8427e')continue;
              scraped=true;
              try{QobuzAuth.onSecret(m);}catch(e){}
              return;
            }
          }
        }
        for(var i=0;i<scripts.length;i++){
          (function(src){
            fetch(src).then(function(r){return r.text();}).then(scanBundle).catch(function(){});
          })(scripts[i].src);
        }
      }catch(e){}
    })()
    """.trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QobuzLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialHandled = remember { AtomicBoolean(false) }

    var captured by remember { mutableStateOf<Pair<String, String>?>(null) }
    var scrapedSecret by remember { mutableStateOf<String?>(null) }
    var showSecretDialog by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun saveToken(token: String, appId: String, appSecret: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val existing = QobuzToken.listFromJson(prefs[QobuzTokensKey])
                val merged =
                    (existing.filterNot { it.token == token }) +
                        QobuzToken(token = token, appId = appId, appSecret = appSecret, label = "Web login")
                prefs[QobuzTokensKey] = QobuzToken.listToJson(merged)
                prefs[QobuzEnabledKey] = true
                prefs[LosslessStreamingEnabledKey] = true
            }
            toast(context.getString(R.string.lossless_qobuz_login_success))
            onBack()
        }
    }

    fun onCredentialsReceived(token: String, appId: String) {
        val secret = scrapedSecret
        if (secret != null) {
            saveToken(token, appId, secret)
        } else {
            captured = token to appId
        }
    }

    fun onSecretReceived(secret: String) {
        scrapedSecret = secret
        val (token, appId) = captured ?: return
        showSecretDialog = false
        saveToken(token, appId, secret)
    }

    if (showSecretDialog) {
        captured?.let { (token, appId) ->
            var secretInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {
                    showSecretDialog = false
                    credentialHandled.set(false)
                },
                title = { Text(stringResource(R.string.lossless_qobuz_app_secret_title)) },
                text = {
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        placeholder = { Text(stringResource(R.string.lossless_qobuz_app_secret_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = secretInput.trim().length >= 16,
                        onClick = { saveToken(token, appId, secretInput.trim()) },
                    ) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSecretDialog = false
                            credentialHandled.set(false)
                        },
                    ) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
    }

    AuthWebViewScreen(
        title = stringResource(R.string.lossless_qobuz_login),
        subtitle = stringResource(R.string.lossless_auth_webview_qobuz_subtitle),
        onBack = onBack,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url?.contains("qobuz.com", ignoreCase = true) == true) {
                                view.loadUrl(QOBUZ_HOOK_JS)
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
                        fun onCredentials(token: String?, appId: String?) {
                            if (token.isNullOrBlank() || appId.isNullOrBlank()) return
                            if (!credentialHandled.compareAndSet(false, true)) return
                            scope.launch { onCredentialsReceived(token, appId) }
                        }

                        @JavascriptInterface
                        fun onSecret(secret: String?) {
                            if (secret.isNullOrBlank() || secret.length != 32) return
                            scope.launch { onSecretReceived(secret) }
                        }
                    },
                    "QobuzAuth",
                )
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    loadUrl(QOBUZ_WEB_PLAYER_URL)
                }
            }
        },
    )

    captured?.let {
        LaunchedEffect(it) {
            delay(4_000)
            if (scrapedSecret == null && !showSecretDialog) {
                showSecretDialog = true
            }
        }
    }
}
