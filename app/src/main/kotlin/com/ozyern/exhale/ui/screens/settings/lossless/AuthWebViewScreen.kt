/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0.
 * Origin: ui/component/AuthWebViewScreen.kt + utils/WebAuthSessionCleaner.kt.
 *
 * Shared chrome for the provider sign-in WebViews (Tidal, Qobuz, Deezer, Apple Music).
 * ArchiveTune renders these as a full-height ModalBottomSheet route; Exhale's navigation
 * graph hosts full-screen destinations inside ScreenWrapper, so this is a Scaffold with a
 * top bar instead. Behaviour is identical: the captured WebView lives in Compose state (so
 * BackHandler stays enabled across recompositions), system back walks the WebView history
 * before leaving, and an optional footer (manual ARL entry etc.) sits under the page.
 */

package com.ozyern.exhale.ui.screens.settings.lossless

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWebViewScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRelease: ((WebView) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    factory: (Context) -> WebView,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        onClick = onBack,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large),
                factory = { ctx ->
                    factory(ctx).also { created ->
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(created.settings, true) }
                        }
                        webView = created
                    }
                },
                onRelease = { released ->
                    onRelease?.invoke(released)
                    if (webView === released) webView = null
                    released.stopLoading()
                    released.destroy()
                },
            )

            footer?.invoke()
        }
    }

    BackHandler(enabled = webView != null) {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onBack()
        }
    }
}

// ── Session helpers (ArchiveTune utils/WebAuthSessionCleaner.kt) ─────────────────────────────

/**
 * Resets the WebView to a clean state before loading a provider's login page so a previous
 * account (or a stale, half-finished sign-in) never leaks into the new session. Cookies are
 * cleared asynchronously; [onReady] runs once the jar is empty.
 */
fun resetAuthWebViewSession(
    context: Context,
    webView: WebView,
    clearCookies: Boolean = true,
    onReady: () -> Unit,
) {
    webView.stopLoading()
    webView.clearHistory()
    webView.clearFormData()
    webView.clearCache(true)
    clearWebAuthStorage(context)

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    if (!clearCookies) {
        onReady()
        return
    }

    cookieManager.removeSessionCookies {
        cookieManager.removeAllCookies {
            cookieManager.flush()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            onReady()
        }
    }
}

fun clearPlaybackWebAuthSession(context: Context) {
    clearWebAuthStorage(context)
    val cookieManager = CookieManager.getInstance()
    cookieManager.removeSessionCookies(null)
    cookieManager.removeAllCookies(null)
    cookieManager.flush()
}

private fun clearWebAuthStorage(context: Context) {
    val appContext = context.applicationContext
    WebStorage.getInstance().deleteAllData()
    WebViewDatabase.getInstance(appContext).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
        @Suppress("DEPRECATION")
        clearUsernamePassword()
    }
}
