package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderProgressParsingTest {
    @Test
    fun parse_duration_time_size_speed_from_samples() {
        val probe = """
            Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'input.mp4':
              Metadata:
                major_brand     : isom
              Duration: 00:12:34.56, start: 0.000000, bitrate: 4500 kb/s
        """.trimIndent()
        val dur = probe.lineSequence().mapNotNull { ProgressParser.parseDurationMs(it) }.firstOrNull()
        assertEquals(754_560, dur) // 00:12:34.56 => 12 min (720_000 ms) + 34_560 ms = 754_560 ms
    }

    @Test
    fun parse_individual_lines() {
        val line1 = "size=   12.3MiB time=00:00:08.04 bitrate=12500.1kbits/s speed=1.50x"
        val size = ProgressParser.parseSizeBytes(line1)
        // 12.3 MiB — assume metric MB as our parser uses 10^6 for simplicity
        assertEquals(12_300_000L, size)
        val t = ProgressParser.parseTimeMs(line1)
        assertEquals(8_040, t)
        val sp = ProgressParser.parseSpeed(line1)
        assertEquals(1.5, sp)

        val line2 = "frame=  240 fps=30 q=-1.0 Lsize=  120.0kB time=00:00:10.00 bitrate= 98.4kbits/s speed=1.00x"
        assertEquals(120_000L, ProgressParser.parseSizeBytes(line2))
        assertEquals(10_000, ProgressParser.parseTimeMs(line2))
        assertEquals(1.0, ProgressParser.parseSpeed(line2))

        val noMatch = "random log line"
        assertNull(ProgressParser.parseTimeMs(noMatch))
        assertNull(ProgressParser.parseSizeBytes(noMatch))
        assertNull(ProgressParser.parseSpeed(noMatch))
    }

    @Test
    fun hms_to_ms_helper() {
        assertEquals(0, ProgressParser.hmsToMs("00", "00", "00.00"))
        assertEquals(1_000, ProgressParser.hmsToMs("00", "00", "01.00"))
        assertEquals(61_230, ProgressParser.hmsToMs("00", "01", "01.23"))
        assertEquals(3_600_000 + 2_000 + 345, ProgressParser.hmsToMs("01", "00", "02.345"))
    }
}
