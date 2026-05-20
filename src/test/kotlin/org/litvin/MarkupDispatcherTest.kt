package org.litvin

import org.litvin.markup.components.MarkupDispatcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkupDispatcherTest {

    private fun newDispatcher(): MarkupDispatcher {
        val d = MarkupDispatcher()
        // drain potential messages
        d.consumeUserMessage()
        return d
    }

    @Test
    fun create_point_happyPath_and_adjacent_allowed() {
        val d = newDispatcher()
        // First point: [0, 1000)
        d.onPointStart(0)
        d.onPointEnd(1000)
        assertEquals(1, d.getCompletedPoints().size)
        assertNull(d.getPendingStart())

        // Adjacent start at 1000 is allowed (half-open)
        d.onPointStart(1000)
        d.onPointEnd(1500)
        assertEquals(2, d.getCompletedPoints().size)
        val p1 = d.getCompletedPoints()[0]
        val p2 = d.getCompletedPoints()[1]
        assertEquals(0, p1.startMs)
        assertEquals(1000, p1.endMs)
        assertEquals(1000, p2.startMs)
        assertEquals(1500, p2.endMs)
    }

    @Test
    fun start_inside_existing_is_blocked() {
        val d = newDispatcher()
        d.onPointStart(0)
        d.onPointEnd(2000)
        assertEquals(1, d.getCompletedPoints().size)

        // Try to place Start at 1000 which is inside existing [0,2000)
        d.onPointStart(1000)
        // Should not set pending start
        assertNull(d.getPendingStart())
        val msg = d.consumeUserMessage()
        assertTrue(msg != null && msg.contains("inside an existing point"), "Expect hint about inside existing interval, but was: $msg")
    }

    @Test
    fun overlap_on_finalize_is_blocked_and_too_short_rejected() {
        val d = newDispatcher()
        d.onPointStart(0)
        d.onPointEnd(2000)
        assertEquals(1, d.getCompletedPoints().size)

        // Create another existing point after a gap: [3000, 4000)
        d.onPointStart(3000)
        d.onPointEnd(4000)
        assertEquals(2, d.getCompletedPoints().size)

        // Overlap attempt: start in the gap [2000,3000), but end crosses into [3000,4000)
        d.onPointStart(2500)
        d.onPointEnd(3500)
        // Not created
        assertEquals(2, d.getCompletedPoints().size)
        var msg = d.consumeUserMessage()
        assertTrue(msg != null && msg.contains("Overlap blocked"), "Expect overlap hint, but was: $msg")

        // Too short: duration < 200 ms rejected (start in gap)
        d.onPointStart(2100)
        d.onPointEnd(2250)
        assertEquals(2, d.getCompletedPoints().size)
        msg = d.consumeUserMessage()
        assertTrue(msg != null && msg.contains("duration"), "Expect duration hint, but was: $msg")

        // Negative or zero duration: End <= Start rejected (outside all points)
        d.onPointStart(4500)
        d.onPointEnd(4400)
        assertEquals(2, d.getCompletedPoints().size)
        msg = d.consumeUserMessage()
        assertTrue(msg != null && msg.contains("End must be > Start"), "Expect invalid end/start hint, but was: $msg")
    }
}
