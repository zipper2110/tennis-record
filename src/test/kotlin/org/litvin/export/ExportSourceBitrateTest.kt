package org.litvin.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportSourceBitrateTest {
    @Test
    fun `parses a positive bit rate from ffprobe output`() {
        assertEquals(ExportSourceBitrate(4_500_000), ExportSourceBitrates.parse("4500000"))
        assertEquals(ExportSourceBitrate(4_500_000), ExportSourceBitrates.parse("  4500000 "))
    }

    @Test
    fun `rejects unusable ffprobe values`() {
        assertNull(ExportSourceBitrates.parse(null))
        assertNull(ExportSourceBitrates.parse("N/A"))
        assertNull(ExportSourceBitrates.parse(""))
        assertNull(ExportSourceBitrates.parse("0"))
    }

    @Test
    fun `shows one decimal below ten megabits and whole numbers above`() {
        assertEquals("4.5", ExportSourceBitrate(4_500_000).displayMegabits)
        assertEquals("9.9", ExportSourceBitrate(9_940_000).displayMegabits)
        assertEquals("12", ExportSourceBitrate(12_400_000).displayMegabits)
        assertEquals("83", ExportSourceBitrate(82_600_000).displayMegabits)
    }
}
