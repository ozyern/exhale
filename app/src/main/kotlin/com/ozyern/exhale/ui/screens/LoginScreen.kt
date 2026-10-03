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
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.ozyern.exhale.ui.component.LoadingRing
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

/**
 * Whether [url] may be shown in the sign-in page.
 *
 * The page has no address bar and talks to the app through a JavaScript bridge, and its start URL
 * can arrive from outside the app (`exhale://login?url=…`). Anything but Google's and YouTube's
 * own https pages is refused, so a link cannot dress a page of its own up as Exhale's sign-in.
 */
internal fun isTrustedLoginUrl(url: String?): Boolean {
    val uri = runCatching { Uri.parse(url ?: return false) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return TrustedLoginHosts.any { host == it || host.endsWith(".$it") } ||
        // Google's regional sign-in domains: google.co.uk, accounts.google.com.br and the like.
        Regex("(^|\\.)google\\.(com?\\.)?[a-z]{2,3}$").containsMatchIn(host)
}

private val TrustedLoginHosts = listOf(
    "google.com",
    "youtube.com",
    "gstatic.com",
    "googleusercontent.com",
    "google.co.in",
)

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                    view.evaluateJavascript("Android.onRetrieveVisitorData(window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null)", null)
                    view.evaluateJavascript("Android.onRetrieveDataSyncId(window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null)", null)
                    view.evaluateJavascript("void((function(){try{var c=window.ytcfg;if(c&&c.get){var t=c.get('PO_TOKEN');if(t){Android.onRetrievePoToken(t);return}}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"PO_TOKEN\":\"([^\"]+)\"/);if(m){Android.onRetrievePoToken(m[1]);return}}}catch(e){}})())", null)
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

    // Its own window, not a page of the NavHost. Everything the NavHost draws is recorded a second
    // time for the glass chrome (Kyant's backdrop layer and Haze both read it), and a WebView
    // drawn into two places a frame is exactly what made Google's sign-in flicker. A dialog window
    // is composited by the system on its own, so the page is drawn once, the way a browser would.
    Dialog(
        onDismissRequest = { navController.navigateUp() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val surface = MaterialTheme.colorScheme.surface
        val onSurface = MaterialTheme.colorScheme.onSurface
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, surface) {
            if (window != null) {
                window.setDimAmount(0f)
                // The keyboard is handed to the page as insets, below, rather than panning the window.
                @Suppress("DEPRECATION")
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    val light = surface.luminance() > 0.5f
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            onDispose {}
        }

        // Hidden until Google's first page has painted, so the empty white of a new WebView never
        // shows; after that the WebView's own ground is the app's surface, so the redirects between
        // Google's sign-in steps don't flash white between them either.
        var firstPaint by remember { mutableStateOf(false) }
        val pageAlpha by animateFloatAsState(
            targetValue = if (firstPaint && !isFinalizingLogin) 1f else 0f,
            animationSpec = tween(220),
            label = "loginPage",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(surface),
        ) {
            TopAppBar(
                title = { com.ozyern.exhale.ui.component.HeadingStyle { Text(stringResource(R.string.login)) } },
                navigationIcon = {
                    LiquidBackButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        icon = R.drawable.chevron_back,
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Where the keyboard will be, taken at once: one resize as it starts to rise
                    // instead of the page relaying out on every frame of its slide.
                    .windowInsetsPadding(
                        WindowInsets.imeAnimationTarget
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = pageAlpha },
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(surface.toArgb())
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageCommitVisible(view: WebView, url: String?) {
                                    firstPaint = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    firstPaint = true
                                    if (isTrustedLoginUrl(url)) checkAndFinalizeLogin(view)
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    if (isTrustedLoginUrl(url)) checkAndFinalizeLogin(view)
                                }

                                // Sign-in only ever moves between Google's own https pages. Anything
                                // else — another site, an intent:// or file:// link — stays out of a
                                // page that holds the account's cookies and a bridge into the app.
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest,
                                ): Boolean = !isTrustedLoginUrl(request.url?.toString())
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            addJavascriptInterface(jsInterface, "Android")
                            webViewRef = this
                            loadUrl(startUrl?.takeIf { isTrustedLoginUrl(it) } ?: DEFAULT_LOGIN_URL)
                        }
                    },
                    onRelease = { it.destroy() },
                )

                // Before the first page, and while the account is being read once Google hands
                // back: YouTube Music loading behind the scenes is not something to watch.
                if (!firstPaint || isFinalizingLogin) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LoadingRing(modifier = Modifier.size(36.dp))
                        if (isFinalizingLogin) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.login_signing_in),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
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

        // Inside the dialog: its window has its own back dispatcher, and this is registered after
        // the dialog's dismiss so it wins while the page has somewhere to go back to.
        BackHandler(enabled = webViewRef?.canGoBack() == true && !isFinalizingLogin) {
            webViewRef?.goBack()
        }
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
