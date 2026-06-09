package org.litvin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssPreviewScoreboardWriterTest {
    @Test
    fun writes_one_dialogue_event_per_span_for_vlc_preview() {
        val tmp = File.createTempFile("preview-score", ".ass")
        tmp.deleteOnExit()
        val spans = listOf(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                p1Name = "Dima",
                p2Name = "Stas",
                p1Pts = 1,
                p2Pts = 0,
                gamesP1 = 0,
                gamesP2 = 0,
            ),
            OverlaySpan(
                startMs = 1_000L,
                endMs = 2_000L,
                text = "",
                p1Name = "Dima",
                p2Name = "Stas",
                p1Pts = 2,
                p2Pts = 0,
                gamesP1 = 0,
                gamesP2 = 0,
            ),
        )

        AssPreviewScoreboardWriter.write(tmp, spans, outWidth = 1920, outHeight = 1080)

        val text = tmp.readText(Charsets.UTF_8)
        assertEquals(2, text.lineSequence().count { it.startsWith("Dialogue:") })
        assertTrue(text.contains("DIMA"))
        assertTrue(text.contains("STAS"))
        assertTrue(text.contains("TennisRecord app"))
    }
}
