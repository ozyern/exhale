/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import android.widget.Toast
import com.music.innertube.YouTube
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.DataSyncIdKey
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.SavedAccount
import com.ozyern.exhale.constants.SavedAccountsKey
import com.ozyern.exhale.constants.toJson
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.launch
import com.ozyern.exhale.ui.component.ScreenScaffold

/**
 * Lets an already-logged-in user switch which YouTube channel on their Google
 * Account the app acts as, without repeating the whole sign-in flow.
 *
 * Deliberately has NO Compose UI drawn over the WebView (no button, no
 * overlay) — that's what made [LoginScreen] glitch and eat touches when it
 * sat over YouTube's own in-page content (e.g. the Premium upsell card).
 * Instead this just opens music.youtube.com (already logged in via the
 * existing cookie jar) and watches DATASYNC_ID: the moment the user taps
 * their own profile picture and picks a different channel — pure YouTube UI,
 * nothing of ours drawn on top of it — YouTube reloads the page with a new
 * DATASYNC_ID, which this detects and saves automatically. No manual
 * confirm step needed.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchChannelScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var savedAccountsJson by rememberPreference(SavedAccountsKey, "")
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var switched by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (switched) R.string.switch_channel_switched_title else R.string.switch_channel_title),
        // A login WebView does not scroll under the bar, so there is no large
        // title to hand off from — the compact one simply stays.
        titleAlpha = 1f,
        onBack = navController::navigateUp,
        onBackLongClick = navController::backToMain,
    ) {
        AndroidView(
            // Deliberately NOT the chrome's contentModifier: recording a
            // hardware-accelerated WebView into a backdrop layer makes the page
            // tear (see AppRoutePolicy.isWebViewRoute).
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onRetrieveDataSyncId(newDataSyncIdRaw: String?) {
                            // Same WebView JS-bridge marshaling quirk as LoginScreen — can hand
                            // this back still percent-encoded ("%3D%3D" instead of "=="),
                            // corrupting every authenticated request's session context. Decode
                            // is a no-op on an already-clean value.
                            val newDataSyncId = newDataSyncIdRaw?.let { android.net.Uri.decode(it) }
                            if (newDataSyncId != null && newDataSyncId.isNotEmpty() && newDataSyncId != dataSyncId) {
                                dataSyncId = newDataSyncId
                                YouTube.dataSyncId = newDataSyncId

                                // Refresh the cookie because Google may rotate it during a channel switch.
                                CookieManager.getInstance().getCookie("https://music.youtube.com")
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { freshCookie ->
                                        innerTubeCookie = freshCookie
                                        YouTube.cookie = freshCookie
                                    }

                                switched = true
                                Toast.makeText(context, context.getString(R.string.switch_channel_success), Toast.LENGTH_SHORT).show()

                                // Refresh the saved-account list so a channel discovered
                                // through this WebView flow is also available for instant
                                // tap-to-switch afterwards, without a WebView round-trip.
                                coroutineScope.launch {
                                    YouTube.getAccountChannels().getOrNull()
                                        ?.mapNotNull { c ->
                                            c.dataSyncId?.let { id ->
                                                SavedAccount(id, c.name, c.channelHandle, c.thumbnailUrl)
                                            }
                                        }
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let { channels -> savedAccountsJson = channels.toJson() }
                                }
                            }
                        }
                    }, "Android")
                    webViewRef.value = this
                    loadUrl("https://music.youtube.com")
                }
            }
        )

    }

    BackHandler(enabled = webViewRef.value?.canGoBack() == true) {
        webViewRef.value?.goBack()
    }
}
