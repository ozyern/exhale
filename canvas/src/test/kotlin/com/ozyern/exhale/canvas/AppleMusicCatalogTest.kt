package com.ozyern.exhale.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicCatalogTest {

    @Test
    fun `normalize strips decorations accents and punctuation`() {
        assertEquals("faded eyes", AppleMusicCatalog.normalize("Faded Eyes - Single"))
        assertEquals("we found love", AppleMusicCatalog.normalize("We Found Love (feat. Calvin Harris)"))
        assertEquals("gymnopedie no 1", AppleMusicCatalog.normalize("Gymnopédie No.1"))
        assertEquals("simon and garfunkel", AppleMusicCatalog.normalize("Simon & Garfunkel"))
    }

    @Test
    fun `artist match is whole word and tolerant of featured lists`() {
        assertTrue(AppleMusicCatalog.artistMatches("Rihanna, Calvin Harris", "Rihanna"))
        assertTrue(AppleMusicCatalog.artistMatches("Khalid", "Khalid & Disclosure"))
        assertFalse(AppleMusicCatalog.artistMatches("Drake", "Drakeo the Ruler"))
        assertTrue(AppleMusicCatalog.artistMatches(null, "Anyone"))
    }

    @Test
    fun `artwork parses colours and sizes template capped at source`() {
        val o = Json.parseToJsonElement(
            """{"url":"https://x/{w}x{h}bb.jpg","width":600,"height":300,"bgColor":"9097a8","textColor1":"141214"}""",
        ).jsonObject
        val art = AppleMusicCatalog.artwork(o)!!
        assertEquals(0x9097a8, art.bgColor)
        assertEquals(0x141214, art.textColor1)
        assertNull(art.textColor3)
        assertEquals("https://x/600x300bb.jpg", art.sized(1200))
        assertEquals("https://x/400x200bb.jpg", art.sized(400))
        assertEquals("https://x/400x200bb.png", art.sized(400, png = true))
    }

    @Test
    fun `key colour is found at any depth`() {
        val o = Json.parseToJsonElement("""{"a":{"b":[{"keyColor":"0198c7"}]}}""")
        assertEquals(0x0198c7, AppleMusicCatalog.findKeyColor(o))
        assertNull(AppleMusicCatalog.findKeyColor(Json.parseToJsonElement("""{"bgColor":"ffffff"}""")))
    }

    private fun fakeJwt(header: String, payload: String): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString(header.toByteArray()) + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun `web player token is picked by issuer whatever the header order`() {
        val webPlay = fakeJwt("""{"typ":"JWT","alg":"ES256"}""", """{"iss":"AMPWebPlay","exp":1792680924}""")
        val other = fakeJwt("""{"typ":"JWT","alg":"ES256"}""", """{"iss":"5IKPP2IECQ","exp":1792680924}""")
        val bundle = """a=1;${'$'}c="$webPlay",sme="$other";"""
        val reversed = """sme="$other",${'$'}c="$webPlay";"""
        assertEquals(webPlay, AppleMusicToken.pickWebPlayToken(bundle, nowSeconds = 1_786_000_000))
        assertEquals(webPlay, AppleMusicToken.pickWebPlayToken(reversed, nowSeconds = 1_786_000_000))
        assertNull(AppleMusicToken.pickWebPlayToken(bundle, nowSeconds = 1_793_000_000))
    }

    @Test
    fun `seed token expiry is read from the jwt`() {
        // Payload exp = 1781713982 (2026-06-17).
        val token = "x.eyJleHAiOjE3ODE3MTM5ODJ9.y"
        assertFalse(AppleMusicToken.isExpired(token, nowSeconds = 1_770_000_000))
        assertTrue(AppleMusicToken.isExpired(token, nowSeconds = 1_790_000_000))
        assertTrue(AppleMusicToken.isExpired("garbage"))
    }
}
