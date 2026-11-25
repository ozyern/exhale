package com.my.kizzy.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection

/**
 * Turns an image URL into an asset Discord will draw, through Discord's own external-assets
 * endpoint: the one its clients use to show pictures hosted elsewhere. Needs the account token
 * and the presence's application id, which the RPC hands over before it resolves its images.
 *
 * Added by ozyern: the third-party proxy this used to rely on alone is why album art went missing
 * whenever it was slow or down.
 */
object DiscordAssets {
    @Volatile private var token: String? = null
    @Volatile private var applicationId: String? = null

    fun use(token: String?, applicationId: String?) {
        this.token = token?.takeIf { it.isNotBlank() }
        this.applicationId = applicationId?.takeIf { it.isNotBlank() }
    }

    suspend fun externalize(url: String): String? = withContext(Dispatchers.IO) {
        val auth = token ?: return@withContext null
        val app = applicationId ?: return@withContext null
        if (!url.startsWith("http")) return@withContext null
        runCatching {
            val connection = java.net.URI("https://discord.com/api/v9/applications/$app/external-assets").toURL()
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", auth)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { out ->
                out.write(JSONObject().put("urls", JSONArray().put(url)).toString().toByteArray())
            }
            if (connection.responseCode !in 200..299) return@runCatching null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONArray(body).optJSONObject(0)?.optString("external_asset_path")
                ?.takeIf { it.isNotBlank() }
                ?.let { "mp:$it" }
        }.getOrNull()
    }
}
