package com.ozyern.exhale.utils.tidal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class TidalAccountCountryTest {
    @Test
    fun readsTheAccountCountryFromTheAccessToken() {
        val token = jwt("""{"type":"o2_access","uid":1,"scope":"r_usr w_usr","cid":6432,"cc":"NZ","exp":1}""")

        assertEquals("NZ", TidalAccount.countryFromAccessToken(token))
    }

    @Test
    fun normalisesCaseAndToleratesSpacing() {
        assertEquals("NZ", TidalAccount.countryFromAccessToken(jwt("""{"cc" : "nz"}""")))
    }

    @Test
    fun returnsNullWhenTheTokenCarriesNoCountry() {
        assertNull(TidalAccount.countryFromAccessToken(jwt("""{"type":"o2_refresh","uid":1}""")))
        assertNull(TidalAccount.countryFromAccessToken("not-a-jwt"))
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("""{"alg":"ES256"}""", payload, "signature")
            .joinToString(".") { encoder.encodeToString(it.toByteArray()) }
    }
}
