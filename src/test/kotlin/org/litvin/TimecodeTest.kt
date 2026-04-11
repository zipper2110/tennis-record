package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimecodeTest {
    @Test
    fun format_basic() {
        assertEquals("00:00:00.000", Timecode.format(0))
        assertEquals("00:00:00.001", Timecode.format(1))
        assertEquals("00:00:01.000", Timecode.format(1000))
        assertEquals("00:01:00.000", Timecode.format(60_000))
        assertEquals("01:00:00.000", Timecode.format(3_600_000))
        assertEquals("10:09:08.007", Timecode.format(10*3_600_000L + 9*60_000L + 8_000L + 7L))
    }

    @Test
    fun roundTo10ms() {
        assertEquals(0, Timecode.roundTo10ms(0))
        assertEquals(10, Timecode.roundTo10ms(6))
        assertEquals(10, Timecode.roundTo10ms(10))
        assertEquals(20, Timecode.roundTo10ms(15))
        assertEquals(100, Timecode.roundTo10ms(96))
    }

    @Test
    fun parse_variants() {
        assertEquals(0, Timecode.parse("0"))
        assertEquals(1, Timecode.parse("0.001"))
        assertEquals(1_234, Timecode.parse("1.234"))
        assertEquals(65_000, Timecode.parse("01:05"))
        assertEquals(3_666_000, Timecode.parse("01:01:06"))
        assertEquals(3_666_789, Timecode.parse("01:01:06.789"))
    }

    @Test
    fun parse_invalid() {
        assertFailsWith<IllegalArgumentException> { Timecode.parse("") }
        assertFailsWith<IllegalArgumentException> { Timecode.parse("1:60") }
        assertFailsWith<IllegalArgumentException> { Timecode.parse("1:2:60") }
    }
}
