/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Lyric lines typed the way people remember them: does a lyrics search find the song? EXHALE_SMOKE=1 to run. */
class LyricSearchProbe {
    @Test
    fun genius() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val lines = listOf(
            "his palms are sweaty knees weak arms are heavy",
            "i'm working late cause i'm a singer",
            "say you cant sleep baby i know",
            "i got my peaches out in georgia",
            "is this the real life is this just fantasy",
            "hum tere bin ab reh nahi sakte",
            "kesariya tera ishq hai piya",
            "we're no strangers to love",
            "cause baby you're a firework",
            "shawty keep wanna come round",
        )
        for (line in lines) {
            val started = System.currentTimeMillis()
            val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder().addQueryParameter("q", line).build()
            val body = OnlineHttp.get(url.toString())
            val hits = body?.let {
                runCatching {
                    val sections = OnlineHttp.json.parseToJsonElement(it).jsonObject["response"]!!.jsonObject["sections"]!!.jsonArray
                    sections.flatMap { s ->
                        val type = (s as JsonObject)["type"]?.jsonPrimitive?.contentOrNull
                        s["hits"]!!.jsonArray.mapNotNull { h ->
                            val r = (h as JsonObject)["result"]?.jsonObject ?: return@mapNotNull null
                            if (r["_type"]?.jsonPrimitive?.contentOrNull != "song") return@mapNotNull null
                            "$type: ${r["title"]?.jsonPrimitive?.contentOrNull} — ${r["artist_names"]?.jsonPrimitive?.contentOrNull}"
                        }
                    }.distinct().take(3)
                }.getOrNull()
            }
            println("\"$line\" (${System.currentTimeMillis() - started} ms)")
            hits.orEmpty().forEach { println("    $it") }
        }
    }
}
