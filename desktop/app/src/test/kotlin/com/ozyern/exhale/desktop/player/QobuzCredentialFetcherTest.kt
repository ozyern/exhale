/*
 * Exhale Project (2026)
 * Licensed Under GPL-3.0
 */

package com.ozyern.exhale.desktop.player

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class QobuzCredentialFetcherTest {
    @Test
    fun fetchesAndExtractsLiveQobuzCredentials() {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val creds = QobuzCredentialFetcher.getCredentials(client)
        assertNotNull("Credentials should be fetched from live play.qobuz.com bundle", creds)
        assertTrue("App ID should not be blank", creds!!.appId.isNotBlank())
        assertEquals("App Secret should be a 32-character hex string", 32, creds.appSecret.length)
        assertTrue("App Secret should be hexadecimal", creds.appSecret.matches(Regex("^[a-f0-9]{32}$")))
    }
}
