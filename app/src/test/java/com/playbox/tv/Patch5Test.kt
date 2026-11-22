package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Patch 5: the pure helpers behind the gear sheet. */
class Patch5Test {

    @Test
    fun parseTimeToMs_secondsOnly() {
        assertEquals(90_000L, parseTimeToMs("90"))
    }

    @Test
    fun parseTimeToMs_minutesSeconds() {
        assertEquals(90_000L, parseTimeToMs("1:30"))
    }

    @Test
    fun parseTimeToMs_hoursMinutesSeconds() {
        assertEquals(3_750_000L, parseTimeToMs("1:02:30"))
    }

    @Test
    fun parseTimeToMs_badInput_isNull() {
        assertNull(parseTimeToMs("abc"))
        assertNull(parseTimeToMs("1:2:3:4"))
        assertNull(parseTimeToMs("-5"))
        assertNull(parseTimeToMs(""))
    }

    @Test
    fun rotatedVideoSize_swapsOnSideways() {
        assertEquals(240 to 320, rotatedVideoSize(320, 240, 90))
        assertEquals(240 to 320, rotatedVideoSize(320, 240, 270))
        assertEquals(320 to 240, rotatedVideoSize(320, 240, 0))
        assertEquals(320 to 240, rotatedVideoSize(320, 240, 180))
    }
}
