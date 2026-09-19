package org.litvin.media.mpv

import org.litvin.media.OverlayShape
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvAssOverlayTest {
    @Test
    fun `filled rectangle is one drawing placed at its top-left corner`() {
        val event = MpvAssOverlay.events(
            listOf(OverlayShape.Rect(10.0, 20.0, 100.0, 50.0, fill = Color(0, 0, 0, 148))),
            scale = 1.0,
        )

        assertTrue(event.startsWith("""{\an7\pos(10,20)"""), event)
        assertTrue(event.contains("""\1c&H000000&\1a&H6B&"""), event)
        assertTrue(event.contains("""\bord0"""), event)
        assertTrue(event.endsWith("""\p1}m 0 0 l 100 0 100 50 0 50{\p0}"""), event)
    }

    @Test
    fun `stroke only rectangle has a transparent fill and a half-width border`() {
        val event = MpvAssOverlay.events(
            listOf(OverlayShape.Rect(0.0, 0.0, 10.0, 10.0, stroke = Color(0x11, 0x22, 0x33), strokeWidth = 2.0)),
            scale = 1.0,
        )

        assertTrue(event.contains("""\1a&HFF&"""), event)
        assertTrue(event.contains("""\bord1\3c&H332211&\3a&H00&"""), event)
    }

    @Test
    fun `scale converts component pixels to OSD pixels`() {
        val event = MpvAssOverlay.events(listOf(OverlayShape.Rect(10.0, 20.0, 100.0, 50.0, fill = Color.WHITE)), 1.5)

        assertTrue(event.startsWith("""{\an7\pos(15,30)"""), event)
        assertTrue(event.contains("m 0 0 l 150 0 150 75 0 75"), event)
    }

    @Test
    fun `each shape is one event line and empty shapes are skipped`() {
        val events = MpvAssOverlay.events(
            listOf(
                OverlayShape.Rect(0.0, 0.0, 0.0, 10.0, fill = Color.WHITE),
                OverlayShape.Circle(50.0, 50.0, 5.0, fill = Color.WHITE),
                OverlayShape.Line(0.0, 0.0, 0.0, 30.0, Color.WHITE, 2.0),
            ),
            scale = 1.0,
        )

        assertEquals(2, events.lines().size)
        assertTrue(events.lines()[0].startsWith("""{\an7\pos(45,45)"""), events)
        assertTrue(events.lines()[1].startsWith("""{\an7\pos(-1,0)"""), events)
    }
}
