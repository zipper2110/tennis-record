package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiebreakTimelineTest {
    private fun pts(id: String, idx: Int): PointV1 = PointV1(id = id, startMs = idx * 1000, endMs = idx * 1000 + 900)

    @Test
    fun timeline_marks_tiebreak_and_finalizes_set_7_6() {
        // Build 12 games to reach 6–6 (alternate winners), then a tiebreak won by P1 7–5
        val points = mutableListOf<PointV1>()
        val outcomes = mutableMapOf<String, Outcome>()
        var idx = 0
        fun addGame(winner: Outcome) {
            repeat(4) {
                val id = "G${idx}"
                points += pts(id, idx)
                outcomes[id] = winner
                idx++
            }
        }
        // 12 games alternating → 6–6
        repeat(6) {
            addGame(Outcome.P1)
            addGame(Outcome.P2)
        }
        // Now tiebreak points (P1 wins 7-5)
        // Score progression before applying each outcome is what spans reflect.
        val tbSeq = listOf(
            Outcome.P1, // 1-0
            Outcome.P2, // 1-1
            Outcome.P1, // 2-1
            Outcome.P1, // 3-1
            Outcome.P2, // 3-2
            Outcome.P2, // 3-3
            Outcome.P1, // 4-3
            Outcome.P1, // 5-3
            Outcome.P2, // 5-4
            Outcome.P2, // 5-5
            Outcome.P1, // 6-5
            Outcome.P1, // 7-5 -> ends
        )
        tbSeq.forEach { o ->
            val id = "TB${idx}"
            points += pts(id, idx)
            outcomes[id] = o
            idx++
        }
        // Add a trailing dummy point with NONE outcome so the final span reflects completed set
        val trailingId = "POST${idx}"
        points += pts(trailingId, idx)
        outcomes[trailingId] = Outcome.NONE
        idx++

        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true, player1Name = "Alice", player2Name = "Bob")
        assertEquals(points.size, spans.size)

        // Find first index where isTiebreak becomes true
        val tbStart = spans.indexOfFirst { it.isTiebreak }
        assertTrue(tbStart >= 0, "Expected a tiebreak segment present")

        // During tiebreak, games should remain 6-6 and tb counters progress
        // Check several steps
        run {
            val s0 = spans[tbStart]
            assertTrue(s0.isTiebreak)
            assertEquals(6, s0.gamesP1)
            assertEquals(6, s0.gamesP2)
        }
        run {
            val sMid = spans[tbStart + 5] // around 3-3 in tb
            assertTrue(sMid.isTiebreak)
            assertEquals(6, sMid.gamesP1)
            assertEquals(6, sMid.gamesP2)
        }

        // After tiebreak completes, the span after last TB point must include 7–6 in completed sets
        val postSpan = spans.last()
        assertTrue(postSpan.completedSets.isNotEmpty(), "Expected at least one completed set after tiebreak completion")
        val (p1, p2) = postSpan.completedSets.last()
        assertEquals(7, p1)
        assertEquals(6, p2)
        // Sets counter must advance for P1
        assertTrue(postSpan.setsP1 >= 1)
    }
}
