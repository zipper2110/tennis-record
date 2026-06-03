package org.litvin

import org.litvin.markup.PointV1
import org.litvin.scoring.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreboardTimelineBuilderTest {
    private fun pts(id: String, s: Int, e: Int) = PointV1(id = id, startMs = s, endMs = e)

    @Test
    fun timeline_idleTrim_ON_aligns_to_concatenated_output_and_carry_forward() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val outcomes = mapOf(
            "A" to Outcome.P1,
            // B is intentionally missing → carry-forward state (no change)
            "C" to Outcome.P2,
        )
        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true, player1Name = "Alice", player2Name = "Bob")
        // Expect three output intervals back-to-back with total 3 seconds
        assertEquals(3, spans.size)
        assertEquals(0L, spans[0].startMs); assertEquals(1_000L, spans[0].endMs)
        assertEquals(1_000L, spans[1].startMs); assertEquals(2_000L, spans[1].endMs)
        assertEquals(2_000L, spans[2].startMs); assertEquals(3_000L, spans[2].endMs)
        // Names should be threaded through
        assertEquals("Alice", spans[0].p1Name); assertEquals("Bob", spans[0].p2Name)
        // Text progression: before first point completes → 0; before second → P1 has 15; before third → still carry-forward 15
        assertTrue(spans[0].text.contains("Player 1: "))
        assertTrue(spans[0].text.contains("pts 0"))
        assertTrue(spans[1].text.contains("pts 15"), "Expected P1 points to be 15 before point B")
        assertTrue(spans[2].text.contains("pts 15"), "Carry-forward expected before point C outcome is applied at its end")
    }

    @Test
    fun timeline_idleTrim_OFF_uses_source_time_domain_boundaries() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val spans = ScoreboardTimelineBuilder.build(points, outcomes = emptyMap(), idleTrim = false)
        // Expect spans to fill from 0 to last end with boundaries at original end times
        assertEquals(3, spans.size)
        assertEquals(0L, spans[0].startMs); assertEquals(1_000L, spans[0].endMs)
        assertEquals(1_000L, spans[1].startMs); assertEquals(3_000L, spans[1].endMs)
        assertEquals(3_000L, spans[2].startMs); assertEquals(5_000L, spans[2].endMs)
    }

    @Test
    fun timeline_advances_games_when_enough_points_to_win_game() {
        // Four consecutive P1 point wins → one game up, then displays 0 points at next interval
        val points = listOf(
            pts("P1", 0, 1_000),
            pts("P2", 1_100, 2_100),
            pts("P3", 2_200, 3_200),
            pts("P4", 3_300, 4_300),
            pts("P5", 4_400, 5_400),
        )
        val outcomes = mapOf(
            "P1" to Outcome.P1,
            "P2" to Outcome.P1,
            "P3" to Outcome.P1,
            "P4" to Outcome.P1, // game should increment after this completes
        )
        val spans = ScoreboardTimelineBuilder.build(points, outcomes, idleTrim = true)
        // After four points are applied, the fifth span should show gamesP1 == 1 and points reset
        assertEquals(5, spans.size)
        // The FIFTH span text is computed before P5 outcome → after P4 applied → new game started
        val t5 = spans[4].text
        assertTrue(t5.contains("games 1"), "Expected gamesP1 to be 1 in span 5 text: $t5")
        assertTrue(t5.contains("Player 1: "))
        // And points should be reset to 0/0, so avoid showing 15/30/40/Ad; check for "pts 0"
        assertTrue(t5.contains("pts 0"), "Expected points reset in new game: $t5")
    }

    @Test
    fun timeline_exportFilter_emitsOnlyFavoritesButScoresFromAllPoints() {
        val points = listOf(
            pts("A", 0, 1_000),
            pts("B", 2_000, 3_000),
            pts("C", 4_000, 5_000),
        )
        val outcomes = mapOf(
            "A" to Outcome.P1,
            "B" to Outcome.P1,
        )

        val spans = ScoreboardTimelineBuilder.build(
            points = points,
            outcomes = outcomes,
            idleTrim = true,
            exportedPointIds = setOf("C"),
        )

        assertEquals(1, spans.size)
        assertEquals(0L, spans[0].startMs)
        assertEquals(1_000L, spans[0].endMs)
        assertTrue(spans[0].text.contains("pts 30"), "Expected score before C to include A and B outcomes: ${spans[0].text}")
    }
}
