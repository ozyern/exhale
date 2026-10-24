/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.openflac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFlacTest {
    @Test
    fun licenceNames() {
        assertEquals("CC BY-NC-SA 3.0", OpenFlac.licenceName("https://creativecommons.org/licenses/by-nc-sa/3.0/"))
        assertEquals("Public Domain", OpenFlac.licenceName("https://creativecommons.org/publicdomain/mark/1.0/"))
        assertNull(OpenFlac.licenceName(null))
    }

    @Test
    fun lengths() {
        assertEquals(274, OpenFlac.parseLength("274.45"))
        assertEquals(274, OpenFlac.parseLength("4:34"))
        assertNull(OpenFlac.parseLength(""))
    }
}
