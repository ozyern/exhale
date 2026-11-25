package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Which client's audio URL actually downloads to the end, by three ways of asking. */
class ClientFullDownloadProbe {
    private val http = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()

    private fun drain(url: String, ua: String, range: String?, origin: String?): String = runCatching {
        val r = Request.Builder().url(url).header("User-Agent", ua)
            .apply { range?.let { header("Range", it) }; origin?.let { header("Origin", it); header("Referer", "$it/") } }.build()
        http.newCall(r).execute().use { resp ->
            var n = 0L; val buf = ByteArray(65536); val ins = resp.body.byteStream()
            try { while (true) { val k = ins.read(buf); if (k < 0) break; n += k } } catch (e: Exception) { return@use "HTTP ${resp.code} got $n then ${e::class.simpleName}" }
            "HTTP ${resp.code} got $n"
        }
    }.getOrElse { "ERR ${it.message}" }

    @Test
    fun probe() = runBlocking {
        assumeTrue(System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val id = "kIft-LUHHVA"
        val sts = NewPipeUtils.getSignatureTimestamp(id).getOrNull()
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.IOS, YouTubeClient.MOBILE, YouTubeClient.ANDROID_MUSIC, YouTubeClient.IOS_MUSIC,
            YouTubeClient.ANDROID_VR_1_61_48, YouTubeClient.ANDROID_VR_1_43_32, YouTubeClient.ANDROID_CREATOR, YouTubeClient.ANDROID_TESTSUITE,
            YouTubeClient.ANDROID_UNPLUGGED, YouTubeClient.IPADOS, YouTubeClient.VISIONOS, YouTubeClient.TVHTML5,
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER, YouTubeClient.WEB, YouTubeClient.WEB_CREATOR, YouTubeClient.WEB_REMIX,
        )
        for (c in clients) {
            if (c.loginRequired) { println("PROBE ${c.clientName}: needs login"); continue }
            val resp = YouTube.player(id, null, c, sts).getOrElse { println("PROBE ${c.clientName}: player failed ${it.message?.take(80)}"); null } ?: continue
            if (resp.playabilityStatus.status != "OK") { println("PROBE ${c.clientName}: ${resp.playabilityStatus.status} ${resp.playabilityStatus.reason?.take(60)}"); continue }
            val fmt = resp.streamingData?.adaptiveFormats.orEmpty().filter { it.isAudio && (it.url != null || it.signatureCipher != null || it.cipher != null) }
                .sortedWith(compareByDescending<com.ozyern.exhale.innertube.models.response.PlayerResponse.StreamingData.Format> { it.url != null }.thenByDescending { it.bitrate }).firstOrNull()
            if (fmt == null) { println("PROBE ${c.clientName}: no audio format (formats=${resp.streamingData?.adaptiveFormats?.size})"); continue }
            val url = NewPipeUtils.getStreamUrl(fmt, id, c).getOrElse { println("PROBE ${c.clientName}: decipher failed ${it.message?.take(60)}"); null } ?: continue
            val clen = Regex("""[?&]clen=(\d+)""").find(url)?.groupValues?.get(1)
            val web = c.clientName.startsWith("WEB") || c.clientName.startsWith("TVHTML5")
            val origin = if (web) c.requestOrigin() else null
            println("PROBE ${c.clientName} itag=${fmt.itag} clen=$clen")
            println("PROBE   plain      : ${drain(url, c.userAgent, null, origin)}")
            println("PROBE   bytes=0-   : ${drain(url, c.userAgent, "bytes=0-", origin)}")
            println("PROBE   &range=0-N : ${drain("$url&range=0-${(clen?.toLong() ?: 5_000_000) - 1}", c.userAgent, null, origin)}")
        }
    }
}
