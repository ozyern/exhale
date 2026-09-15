/*
 * Exhale Project (2026)
 * Licensed Under GPL-3.0
 */

package com.ozyern.exhale.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AccountChannelHandleKey
import com.ozyern.exhale.constants.AccountEmailKey
import com.ozyern.exhale.constants.AccountNameKey
import com.ozyern.exhale.constants.DataSyncIdKey
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.PoTokenKey
import com.ozyern.exhale.constants.VisitorDataKey
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.utils.parseCookieString
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.reportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val LOGIN_ROUTE = "login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private class LoginJsInterface {
    var onVisitorDataReceived: ((String?) -> Unit)? = null
    var onDataSyncIdReceived: ((String?) -> Unit)? = null
    var onPoTokenReceived: ((String?) -> Unit)? = null

    @JavascriptInterface
    fun onRetrieveVisitorData(visitorData: String?) {
        onVisitorDataReceived?.invoke(visitorData)
    }

    @JavascriptInterface
    fun onRetrieveDataSyncId(dataSyncId: String?) {
        onDataSyncIdReceived?.invoke(dataSyncId)
    }

    @JavascriptInterface
    fun onRetrievePoToken(poToken: String?) {
        onPoTokenReceived?.invoke(poToken)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    startUrl: String? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val jsInterface = remember { LoginJsInterface() }

    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var poToken by rememberPreference(PoTokenKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isFinalizingLogin by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }

    fun checkAndFinalizeLogin(view: WebView) {
        if (isFinalizingLogin) return
        val cookieManager = CookieManager.getInstance()
        val currentCookie = mergeYouTubeCookies(cookieManager).orEmpty()
        if ("SAPISID" !in parseCookieString(currentCookie)) return

        isFinalizingLogin = true

        coroutineScope.launch(Dispatchers.IO) {
            var mergedCookie = ""
            var hasAuthCookie = false
            var extractedVisitorData: String? = null
            var extractedDataSyncId: String? = null
            var extractedPoToken: String? = null

            repeat(20) {
                mergedCookie = mergeYouTubeCookies(cookieManager).orEmpty()
                val cookieMap = runCatching { parseCookieString(mergedCookie) }.getOrDefault(emptyMap())
                hasAuthCookie = "SAPISID" in cookieMap

                val visitorDeferred = CompletableDeferred<String?>()
                val dataSyncDeferred = CompletableDeferred<String?>()
                val poTokenDeferred = CompletableDeferred<String?>()

                jsInterface.onVisitorDataReceived = { if (!visitorDeferred.isCompleted) visitorDeferred.complete(it) }
                jsInterface.onDataSyncIdReceived = { if (!dataSyncDeferred.isCompleted) dataSyncDeferred.complete(it) }
                jsInterface.onPoTokenReceived = { if (!poTokenDeferred.isCompleted) poTokenDeferred.complete(it) }

                withContext(Dispatchers.Main) {
                    view.loadUrl("javascript:Android.onRetrieveVisitorData(window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null)")
                    view.loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null)")
                    view.loadUrl("javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var t=c.get('PO_TOKEN');if(t){Android.onRetrievePoToken(t);return}}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"PO_TOKEN\":\"([^\"]+)\"/);if(m){Android.onRetrievePoToken(m[1]);return}}}catch(e){}})())")
                }

                withTimeoutOrNull(800) {
                    extractedVisitorData = visitorDeferred.await()
                    extractedDataSyncId = dataSyncDeferred.await()
                    extractedPoToken = poTokenDeferred.await()
                }

                if (hasAuthCookie && !extractedVisitorData.isNullOrBlank()) {
                    return@repeat
                }
                delay(400)
            }

            if (hasAuthCookie && mergedCookie.isNotBlank()) {
                val cleanDataSyncId = extractedDataSyncId?.substringBefore("||").orEmpty()

                innerTubeCookie = mergedCookie
                if (!extractedVisitorData.isNullOrBlank()) visitorData = extractedVisitorData!!
                if (cleanDataSyncId.isNotBlank()) dataSyncId = cleanDataSyncId
                if (!extractedPoToken.isNullOrBlank()) poToken = extractedPoToken!!

                YouTube.cookie = mergedCookie
                if (!extractedVisitorData.isNullOrBlank()) YouTube.visitorData = extractedVisitorData
                if (cleanDataSyncId.isNotBlank()) YouTube.dataSyncId = cleanDataSyncId
                if (!extractedPoToken.isNullOrBlank()) YouTube.poToken = extractedPoToken

                YouTube.accountInfo().onSuccess { info ->
                    accountName = info.name
                    accountEmail = info.email.orEmpty()
                    accountChannelHandle = info.channelHandle.orEmpty()
                }.onFailure {
                    reportException(it)
                }

                withContext(Dispatchers.Main) {
                    navController.navigateUp()
                }
            } else {
                withContext(Dispatchers.Main) {
                    isFinalizingLogin = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            checkAndFinalizeLogin(view)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            checkAndFinalizeLogin(view)
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    addJavascriptInterface(jsInterface, "Android")
                    webViewRef = this
                    loadUrl(startUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_LOGIN_URL)
                }
            }
        )

        if (isFinalizingLogin) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        TopAppBar(
            title = { Text(stringResource(R.string.login)) },
            navigationIcon = {
                LiquidBackButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                    icon = R.drawable.arrow_back,
                )
            },
            actions = {
                IconButton(
                    onClick = { showTokenDialog = true },
                    onLongClick = {}
                ) {
                    Icon(
                        painter = painterResource(R.drawable.token),
                        contentDescription = stringResource(R.string.advanced_login),
                    )
                }
            }
        )
    }

    if (showTokenDialog) {
        com.ozyern.exhale.ui.component.TokenEditorDialog(
            onDismiss = { showTokenDialog = false },
            onSuccess = {
                showTokenDialog = false
                navController.navigateUp()
            }
        )
    }

    BackHandler(enabled = webViewRef?.canGoBack() == true && !isFinalizingLogin) {
        webViewRef?.goBack()
    }
}

private fun mergeYouTubeCookies(cookieManager: CookieManager): String? {
    val cookieParts = linkedMapOf<String, String>()

    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    ).forEach { url ->
        cookieManager.getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty()) {
                    cookieParts[key] = value
                }
            }
    }

    return cookieParts.takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}
