package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoringRulesTest {

    @Test
    fun regularGame_deuce_advantage_cycles_then_win_by_two() {
        // One game worth of points that goes to multiple deuces and P1 wins 8-6 in points
        val pointIds = (1..14).map { "p$it" }
        val outcomes = LinkedHashMap<String, Outcome>()
        // Sequence to reach 3-3 (40-40)
        outcomes["p1"] = Outcome.P1
        outcomes["p2"] = Outcome.P1
        outcomes["p3"] = Outcome.P1
        outcomes["p4"] = Outcome.P2
        outcomes["p5"] = Outcome.P2
        outcomes["p6"] = Outcome.P2
        // Now deuce cycles: P1 gets Ad, back to deuce, etc.
        // P1, P2, P1, P2, P1, P1 (final two for 2-point lead)
        outcomes["p7"] = Outcome.P1  // Ad P1
        outcomes["p8"] = Outcome.P2  // back to deuce
        outcomes["p9"] = Outcome.P1  // Ad P1
        outcomes["p10"] = Outcome.P2 // back to deuce
        outcomes["p11"] = Outcome.P1 // Ad P1
        outcomes["p12"] = Outcome.P1 // Game P1
        // Fill remaining ids as NONE (no effect)
        outcomes["p13"] = Outcome.NONE
        outcomes["p14"] = Outcome.NONE

        val (timeline, _) = RulesEngine.computeTimeline(pointIds, outcomes)
        // After p12, game should be awarded to P1, gamesP1==1 and points reset
        val st12 = timeline[11]
        assertEquals(1, st12.gamesP1, "P1 should have 1 game after win")
        assertEquals(0, st12.gamesP2)
        assertEquals(0, st12.p1Pts)
        assertEquals(0, st12.p2Pts)
        assertEquals(1, st12.lastGameWonBy)
        // After p14 (NONEs), state unchanged
        val st14 = timeline[13]
        assertEquals(st12.gamesP1, st14.gamesP1)
        assertEquals(st12.gamesP2, st14.gamesP2)
        assertEquals(st12.p1Pts, st14.p1Pts)
        assertEquals(st12.p2Pts, st14.p2Pts)
    }

    @Test
    fun set_goes_to_tiebreak_at_6_6_and_finishes_7_6() {
        // Build 12 short games (4 points each) alternating winners to reach 6–6
        val pointIds = mutableListOf<String>()
        val outcomes = LinkedHashMap<String, Outcome>()
        var id = 1
        fun addGame(winner: Outcome) {
            repeat(4) { _ ->
                val pid = "p${id++}"; pointIds += pid; outcomes[pid] = winner
            }
        }
        // Alternate wins to reach 6–6 within the same set (no 2-game lead)
        repeat(6) {
            addGame(Outcome.P1) // P1 goes +1
            addGame(Outcome.P2) // P2 equalizes
        }
        // Now tiebreak: P1 wins 7–5
        // Sequence with no need to be exact rally-by-rally; just ensure a 2-pt lead at >=7
        repeat(5) { // 5-5
            val p1 = "p${id++}"; pointIds += p1; outcomes[p1] = Outcome.P1
            val p2 = "p${id++}"; pointIds += p2; outcomes[p2] = Outcome.P2
        }
        // Now P1 takes two in a row to 7–5
        val pA = "p${id++}"; pointIds += pA; outcomes[pA] = Outcome.P1 // 6-5
        val pB = "p${id++}"; pointIds += pB; outcomes[pB] = Outcome.P1 // 7-5 -> set

        val (timeline, setsPerPoint) = RulesEngine.computeTimeline(pointIds, outcomes)
        val lastIdx = timeline.lastIndex
        val st = timeline[lastIdx]
        // Sets should be 1-0 for P1, games reset to 0 after set
        assertEquals(1, st.setsP1)
        assertEquals(0, st.setsP2)
        assertEquals(0, st.gamesP1)
        assertEquals(0, st.gamesP2)
        // Completed set recorded as 7–6 with tiebreak=true
        val completed = setsPerPoint[lastIdx].last()
        assertEquals(7, completed.p1)
        assertEquals(6, completed.p2)
        assertEquals(true, completed.tiebreak)
    }

    @Test
    fun flipping_early_outcome_recomputes_following_state() {
        // Two short games, initially both for P1 -> 2-0, then flip first game to P2 -> becomes 1-1
        val pointIds = (1..8).map { "p$it" }
        val initial = LinkedHashMap<String, Outcome>()
        // First game (p1..p4) P1, second game (p5..p8) P1
        (1..4).forEach { initial["p$it"] = Outcome.P1 }
        (5..8).forEach { initial["p$it"] = Outcome.P1 }
        val (tl1, _) = RulesEngine.computeTimeline(pointIds, initial)
        val stAfterG2 = tl1[7]
        assertEquals(2, stAfterG2.gamesP1)
        assertEquals(0, stAfterG2.gamesP2)

        // Flip early outcome: make first game's first point for P2, and rest P2 so P2 wins that game
        val changed = LinkedHashMap(initial)
        (1..4).forEach { changed["p$it"] = Outcome.P2 }
        val (tl2, _) = RulesEngine.computeTimeline(pointIds, changed)
        val stAfterG2Changed = tl2[7]
        assertEquals(1, stAfterG2Changed.gamesP1)
        assertEquals(1, stAfterG2Changed.gamesP2)
    }
}
