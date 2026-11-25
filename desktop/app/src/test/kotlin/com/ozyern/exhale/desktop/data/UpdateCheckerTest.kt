/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun windowsTagsCompareByTheirVersion() {
        assertTrue(UpdateChecker.isNewer("v1.0.507-windows", than = "1.0.506"))
        assertTrue(UpdateChecker.isNewer("1.0.510-windows", than = "1.0.506"))
        assertFalse(UpdateChecker.isNewer("v1.0.506-windows", than = "1.0.506"))
        assertFalse(UpdateChecker.isNewer("v1.0.505-windows", than = "1.0.506"))
    }

    @Test
    fun windowsTagsCompareWithEachOther() {
        assertTrue(UpdateChecker.isNewer("v1.0.510-windows", than = "v1.0.509-windows"))
        assertTrue(UpdateChecker.isNewer("v1.1.0-windows", than = "v1.0.999-windows"))
    }
}
