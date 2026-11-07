/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0 (ui/screens/settings/AppleMusicLoginScreen.kt).
 *
 * Apple Music sign-in. The Music User Token is captured from BOTH places the web player puts it:
 * the `media-user-token` cookie on the music.apple.com origin (the primary source — the web
 * player writes it there right after the Apple ID handshake) and localStorage (the web app also
 * mirrors it there once the player SPA boots). Every candidate is handed to the AMP API for
 * verification (`/v1/me/storefront` answers 200 only for a valid pairing), and the winner is
 * persisted (plus a developer token when the user hasn't pasted one — scraped from the web player).
 */

package com.ozyern.exhale.ui.screens.settings.lossless

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicAudioProvider
import com.ozyern.exhale.playback.lossless.sources.applemusic.AppleMusicProvider
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicDevTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicMediaUserTokenKey
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicSourceEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.LosslessStreamingEnabledKey
import com.ozyern.exhale.playback.lossless.sources.constants.getAsync
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGIN_URL = "https://music.apple.com/login"
private const val COOKIE_ORIGIN = "https://music.apple.com"
private const val TAG = "AppleMusicLogin"

/**
 * Shape shared by every capture path: a JWT (three dot-separated base64url segments, `eyJ…`) or
 * the classic `0.` + base64 media-user-token.
 */
private fun looksLikeMediaUserToken(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    if (value.length < 40 || value.length > 4096) return false
    return MEDIA_TOKEN_JWT_REGEX.matches(value) || MEDIA_TOKEN_CLASSIC_REGEX.matches(value)
}

private val MEDIA_TOKEN_JWT_REGEX = Regex("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
private val MEDIA_TOKEN_CLASSIC_REGEX = Regex("^0\\.[A-Za-z0-9+/=]{40,}$")

/**
 * Collects every localStorage value that looks like a media-user-token, skipping obvious
 * developer / player tokens. Returns a JSON array (string[]).
 */
private const val TOKEN_PROBE_JS = """
(function(){
  function ok(v){
    if(!v||typeof v!=='string')return false;
    if(v.length<40||v.length>4096)return false;
    return /^eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(v)
      || /^0\.[A-Za-z0-9+\/=]{40,}$/.test(v);
  }
  var out=[];
  try{
    var d=localStorage.getItem('media-user-token');
    if(ok(d)) out.push(d);
  }catch(e){}
  try{
    for(var i=0;i<localStorage.length;i++){
      var k=localStorage.key(i); if(!k)continue;
      var lk=k.toLowerCase();
      if(lk.indexOf('token')===-1)continue;
      if(lk.indexOf('developer')>=0||lk.indexOf('dev-')>=0||lk.indexOf('devtoken')>=0
        ||lk.indexOf('amtv')>=0||lk.indexOf('jwt')>=0||lk.indexOf('media-user-token')>=0)continue;
      var v=localStorage.getItem(k);
      if(ok(v)) out.push(v);
      else if(v&&v.length<2000&&v.charAt(0)==='{'){
        try{
          var j=JSON.parse(v);
          for(var key in j){ if(ok(j[key])) out.push(j[key]); }
        }catch(e2){}
      }
    }
  }catch(e){}
  return out;
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppleMusicLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val handled = remember { AtomicBoolean(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun readSessionCookie(): Boolean =
        CookieManager
            .getInstance()
            .getCookie(COOKIE_ORIGIN)
            ?.split(';')
            ?.any { it.trim().startsWith("its.pod=", ignoreCase = true) || it.trim().startsWith("pxro=", ignoreCase = true) }
            ?: false

    /**
     * The primary capture path: the `media-user-token` cookie on the music.apple.com origin.
     * CookieManager sees every cookie the WebView stored (including HttpOnly ones, which the
     * in-page JS probe can never read). The value may arrive URI-encoded, so the decoded form is
     * accepted too.
     */
    fun readMediaUserTokenCookie(): String? {
        val jar = CookieManager.getInstance().getCookie(COOKIE_ORIGIN) ?: return null
        for (raw in jar.split(';')) {
            val entry = raw.trim()
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            if (!entry.substring(0, eq).equals("media-user-token", ignoreCase = true)) continue
            val value = entry.substring(eq + 1).trim().trim('"')
            if (looksLikeMediaUserToken(value)) return value
            val decoded = runCatching { android.net.Uri.decode(value) }.getOrDefault(value)
            if (looksLikeMediaUserToken(decoded)) return decoded
        }
        return null
    }

    fun finishLogin(mediaToken: String) {
        if (!handled.compareAndSet(false, true)) return
        scope.launch {
            val devToken =
                withContext(Dispatchers.IO) {
                    val pasted = context.dataStore.getAsync(AppleMusicDevTokenKey, "").trim()
                    if (pasted.isNotBlank()) {
                        pasted
                    } else {
                        // Honour the "Developer token is optional" promise: use the app-scraped
                        // web-player JWT so playback can engage without a pasted developer token.
                        runCatching { AppleMusicProvider.currentDevToken() }.getOrNull()
                    }
                }

            val verified =
                devToken != null &&
                    withContext(Dispatchers.IO) {
                        AppleMusicAudioProvider.verifyTokens(mediaToken, devToken)
                    }

            if (!verified) {
                Timber.tag(TAG).w("media-user-token captured but verification failed; not persisting")
                handled.set(false)
                toast(context.getString(R.string.lossless_applemusic_login_failed))
                return@launch
            }

            context.dataStore.edit { prefs ->
                prefs[AppleMusicMediaUserTokenKey] = mediaToken
                if (devToken.isNotBlank()) prefs[AppleMusicDevTokenKey] = devToken
                prefs[AppleMusicSourceEnabledKey] = true
                prefs[LosslessStreamingEnabledKey] = true
            }
            toast(context.getString(R.string.lossless_applemusic_login_success))
            onBack()
        }
    }

    fun probeForToken(view: WebView) {
        if (handled.get()) return
        // Cookie first: it is written by the Apple ID handshake itself and does not depend on the
        // web app's JS booting.
        readMediaUserTokenCookie()?.let { token ->
            if (!handled.get()) {
                finishLogin(token)
                return
            }
        }
        view.evaluateJavascript(TOKEN_PROBE_JS) { result ->
            if (result == null || result == "null" || result == "[]") return@evaluateJavascript
            val candidates =
                runCatching { JSONArray(result) }.getOrNull() ?: return@evaluateJavascript
            val mediaToken =
                (0 until candidates.length())
                    .mapNotNull { i -> candidates.optString(i).takeIf { it.isNotBlank() } }
                    .firstOrNull()
            if (mediaToken != null && !handled.get()) {
                finishLogin(mediaToken)
            }
        }
    }

    // The token can appear without any navigation once the web player boots after the Apple ID
    // handshake, so polling is the only reliable trigger.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            webView?.let { view ->
                if (!handled.get()) {
                    if (readMediaUserTokenCookie() != null || readSessionCookie()) {
                        view.post { probeForToken(view) }
                    }
                }
            }
        }
    }

    AuthWebViewScreen(
        title = stringResource(R.string.lossless_applemusic_login),
        subtitle = stringResource(R.string.lossless_applemusic_login_subtitle),
        onBack = onBack,
        onRelease = { released -> if (webView === released) webView = null },
        factory = { ctx ->
            WebView(ctx).apply {
                webView = this
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            if (url == null || !url.startsWith("https://music.apple.com")) return
                            if (!readSessionCookie() && readMediaUserTokenCookie() == null) return
                            probeForToken(view)
                        }
                    }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    loadUrl(LOGIN_URL)
                }
            }
        },
    )
}
