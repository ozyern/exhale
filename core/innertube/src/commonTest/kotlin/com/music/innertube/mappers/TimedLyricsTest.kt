/** GPL-3.0. Third-party portions: see NOTICE. */
package com.music.innertube.mappers

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimedLyricsTest {
    @Test
    fun turnsTheLyricsTabTimingsIntoLrc() {
        val page = Json.parseToJsonElement(
            """
            {"contents":{"elementRenderer":{"newElement":{"type":{"componentType":{"model":{
              "timedLyricsModel":{"lyricsData":{"timedLyricsData":[
                {"lyricLine":"Second line","cueRange":{"startTimeMilliseconds":"75250","endTimeMilliseconds":"79000"}},
                {"lyricLine":"First line","cueRange":{"startTimeMilliseconds":"12340","endTimeMilliseconds":"15000"}},
                {"lyricLine":"   ","cueRange":{"startTimeMilliseconds":"80000"}}
              ]}}}}}}}}}
            """.trimIndent(),
        )
        assertEquals("[00:12.34]First line\n[01:15.25]Second line", TimedLyrics.lrc(page))
    }

    @Test
    fun aPlainLyricsPageHasNoTimedLyrics() {
        val page = Json.parseToJsonElement(
            """{"contents":{"sectionListRenderer":{"contents":[{"musicDescriptionShelfRenderer":{"description":{"runs":[{"text":"Plain"}]}}}]}}}""",
        )
        assertNull(TimedLyrics.lrc(page))
    }
}
