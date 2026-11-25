/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AccountInfo
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class YouTubeAccount(val name: String, val email: String?, val handle: String?, val avatar: String?)

/**
 * The signed-in YouTube Music session: what the sign-in window captured, persisted in
 * [DesktopPrefs] and pushed into innertube's globals. Signed in, Home comes from the account —
 * which is the one feed YouTube doesn't build from the caller's IP.
 */
object AccountSession {
    private val _account = MutableStateFlow(savedAccount())
    val account: StateFlow<YouTubeAccount?> = _account.asStateFlow()

    val isSignedIn: Boolean
        get() = DesktopPrefs.cookie?.contains("SAPISID") == true

    private val _expired = MutableStateFlow(false)

    /** True once YouTube has turned the saved session away; cleared by the next sign-in. */
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    /** Set by the sign-in browser so signing out also forgets the browser's own cookies. */
    var clearBrowserCookies: (() -> Unit)? = null

    /**
     * Asks YouTube whether the saved session still stands, before anything is loaded with it.
     *
     * Google can end a session Exhale saved — a password change, a sign-out elsewhere, or a session
     * bound to the browser that captured it. Sent anyway, the dead cookie fails every request, Home
     * included, and the whole app reads as offline. So a session YouTube rejects is dropped here and
     * the app carries on signed out. A failure to reach YouTube at all proves nothing and keeps it.
     */
    suspend fun verifySaved() {
        if (!isSignedIn) return
        withContext(Dispatchers.IO) { YouTube.accountInfo() }
            .onSuccess(::storeAccount)
            .onFailure { if (it.meansSignedOut()) expire() }
    }

    private fun Throwable.meansSignedOut(): Boolean =
        this is IllegalStateException || (this is ClientRequestException && response.status.value in 401..403)

    private fun expire() {
        signOut()
        _expired.value = true
    }

    fun applySaved() {
        YouTube.cookie = DesktopPrefs.cookie
        YouTube.dataSyncId = DesktopPrefs.dataSyncId
        YouTube.useLoginForBrowse = isSignedIn && DesktopPrefs.useAccountForHome
    }

    suspend fun completeSignIn(cookie: String, dataSyncId: String?, visitorData: String?) {
        DesktopPrefs.cookie = cookie
        dataSyncId?.takeIf { it.isNotBlank() }?.let { DesktopPrefs.dataSyncId = it.substringBefore("||") }
        visitorData?.takeIf { it.isNotBlank() }?.let {
            // The page's visitor id belongs to this session; keep it rather than reminting.
            DesktopPrefs.visitorData = it
            DesktopPrefs.visitorDataLocale = "${YouTube.locale.hl}|${YouTube.locale.gl}"
            YouTube.visitorData = it
        }
        _expired.value = false
        applySaved()
        refreshAccountInfo()
    }

    suspend fun refreshAccountInfo() {
        if (!isSignedIn) return
        withContext(Dispatchers.IO) { YouTube.accountInfo() }.onSuccess(::storeAccount)
    }

    private fun storeAccount(info: AccountInfo) {
        DesktopPrefs.accountName = info.name
        DesktopPrefs.accountEmail = info.email
        DesktopPrefs.accountHandle = info.channelHandle
        DesktopPrefs.accountAvatar = info.thumbnailUrl
        _account.value = YouTubeAccount(info.name, info.email, info.channelHandle, info.thumbnailUrl)
    }

    fun setUseAccountForHome(enabled: Boolean) {
        DesktopPrefs.useAccountForHome = enabled
        applySaved()
    }

    fun signOut() {
        DesktopPrefs.cookie = null
        DesktopPrefs.dataSyncId = null
        DesktopPrefs.accountName = null
        DesktopPrefs.accountEmail = null
        DesktopPrefs.accountHandle = null
        DesktopPrefs.accountAvatar = null
        // The visitor id was bound to the session; the next request mints a fresh anonymous one.
        DesktopPrefs.visitorData = null
        YouTube.visitorData = null
        applySaved()
        _account.value = null
        clearBrowserCookies?.invoke()
    }

    private fun savedAccount(): YouTubeAccount? {
        if (DesktopPrefs.cookie?.contains("SAPISID") != true) return null
        val name = DesktopPrefs.accountName ?: return null
        return YouTubeAccount(name, DesktopPrefs.accountEmail, DesktopPrefs.accountHandle, DesktopPrefs.accountAvatar)
    }
}
