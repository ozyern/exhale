package com.ozyern.exhale.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TotalLengthStringTest {
    @Test
    fun roundsUpIntoTheNextHour_ratherThanSixtyMinutes() {
        assertEquals("4 hr", totalLengthString(3 * 3600L + 59 * 60 + 40))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("1 hr 5 min", totalLengthString(3600L + 5 * 60))
        assertEquals("50 min", totalLengthString(50 * 60L))
    }

    @Test
    fun shortAndEmpty() {
        assertEquals("1 min", totalLengthString(31))
        assertEquals("20 sec", totalLengthString(20))
        assertEquals("", totalLengthString(0))
    }
}
