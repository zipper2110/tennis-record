package org.litvin

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OverlayProgressParsingWithFiltersTest {
    @Test
    fun parses_time_and_size_from_stderr_with_subtitles_logs_present() {
        val p = Paths.get("src", "test", "resources", "logs", "overlay1.txt")
        val lines = Files.readAllLines(p)
        // Pick the first line which looks like a typical progress line
        val first = lines.first()
        val size = ProgressParser.parseSizeBytes(first)
        val time = ProgressParser.parseTimeMs(first)
        val speed = ProgressParser.parseSpeed(first)
        assertNotNull(size)
        assertEquals(512_000L, size)
        assertNotNull(time)
        assertEquals(700, time)
        assertNotNull(speed)
        assertEquals(2.40, speed!!, 1e-6)
    }
}
