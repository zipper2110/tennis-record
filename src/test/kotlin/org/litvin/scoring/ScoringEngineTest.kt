package org.litvin.scoring

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoringEngineTest {
    @Test
    fun regularGameCyclesThroughDeuceAndAdvantageThenWinsByTwo() {
        val points = points(14)
        val outcomes = linkedMapOf(
            "p1" to Outcome.P1,
            "p2" to Outcome.P1,
            "p3" to Outcome.P1,
            "p4" to Outcome.P2,
            "p5" to Outcome.P2,
            "p6" to Outcome.P2,
            "p7" to Outcome.P1,
            "p8" to Outcome.P2,
            "p9" to Outcome.P1,
            "p10" to Outcome.P2,
            "p11" to Outcome.P1,
            "p12" to Outcome.P1,
            "p13" to Outcome.NONE,
            "p14" to Outcome.NONE,
        )

        val (states, _) = ScoringEngine.computeTimeline(points, outcomes)

        val gameWinner = states[11]
        assertEquals(1, gameWinner.gamesP1)
        assertEquals(0, gameWinner.gamesP2)
        assertEquals(0, gameWinner.p1Pts)
        assertEquals(0, gameWinner.p2Pts)
        assertEquals(1, gameWinner.lastGameWonBy)

        val afterNone = states[13]
        assertEquals(gameWinner.copy(lastGameWonBy = null), afterNone)
    }

    @Test
    fun noneAndMissingOutcomesCarryTheCurrentStateForward() {
        val points = points(3)
        val outcomes = mapOf(
            "p1" to Outcome.P1,
            "p2" to Outcome.NONE,
        )

        val (states, _) = ScoringEngine.computeTimeline(points, outcomes)

        assertEquals(1, states[0].p1Pts)
        assertEquals(states[0], states[1])
        assertEquals(states[0], states[2])
    }

    @Test
    fun setGoesToTiebreakAtSixAllAndFinishesSevenSix() {
        val points = mutableListOf<PointV1>()
        val outcomes = LinkedHashMap<String, Outcome>()
        var next = 1

        fun addGame(winner: Outcome) {
            repeat(4) {
                val id = "p${next++}"
                points += point(id, next)
                outcomes[id] = winner
            }
        }

        repeat(6) {
            addGame(Outcome.P1)
            addGame(Outcome.P2)
        }
        repeat(5) {
            val p1 = "p${next++}"
            points += point(p1, next)
            outcomes[p1] = Outcome.P1
            val p2 = "p${next++}"
            points += point(p2, next)
            outcomes[p2] = Outcome.P2
        }
        val sixth = "p${next++}"
        points += point(sixth, next)
        outcomes[sixth] = Outcome.P1
        val seventh = "p${next++}"
        points += point(seventh, next)
        outcomes[seventh] = Outcome.P1

        val (states, setsPerPoint) = ScoringEngine.computeTimeline(points, outcomes)
        val last = states.last()

        assertFalse(last.isTiebreak)
        assertEquals(1, last.setsP1)
        assertEquals(0, last.setsP2)
        assertEquals(0, last.gamesP1)
        assertEquals(0, last.gamesP2)
        assertEquals(ScoringEngine.SetScore(7, 6, true), setsPerPoint.last().single())
    }

    @Test
    fun flippingEarlierOutcomeRecomputesFollowingState() {
        val points = points(8)
        val initial = LinkedHashMap<String, Outcome>()
        (1..4).forEach { initial["p$it"] = Outcome.P1 }
        (5..8).forEach { initial["p$it"] = Outcome.P1 }

        val (initialStates, _) = ScoringEngine.computeTimeline(points, initial)
        assertEquals(2, initialStates[7].gamesP1)
        assertEquals(0, initialStates[7].gamesP2)

        val changed = LinkedHashMap(initial)
        (1..4).forEach { changed["p$it"] = Outcome.P2 }

        val (changedStates, _) = ScoringEngine.computeTimeline(points, changed)
        assertEquals(1, changedStates[7].gamesP1)
        assertEquals(1, changedStates[7].gamesP2)
    }

    @Test
    fun bestOfThreeWinnerCarriesFinalStateForward() {
        val points = mutableListOf<PointV1>()
        val outcomes = LinkedHashMap<String, Outcome>()
        var next = 1

        repeat(12) {
            repeat(4) {
                val id = "p${next++}"
                points += point(id, next)
                outcomes[id] = Outcome.P1
            }
        }
        val afterMatch = "p${next++}"
        points += point(afterMatch, next)
        outcomes[afterMatch] = Outcome.P2

        val (states, setsPerPoint) = ScoringEngine.computeTimeline(points, outcomes)
        val matchPoint = states[47]
        val extraPoint = states[48]

        assertEquals(2, matchPoint.setsP1)
        assertEquals(0, matchPoint.setsP2)
        assertEquals(0, extraPoint.gamesP1)
        assertEquals(0, extraPoint.gamesP2)
        assertEquals(2, extraPoint.setsP1)
        assertEquals(0, extraPoint.setsP2)
        assertEquals(0, extraPoint.p1Pts)
        assertEquals(0, extraPoint.p2Pts)
        assertEquals(null, extraPoint.lastGameWonBy)
        assertEquals(null, extraPoint.lastSetWonBy)
        assertEquals(2, setsPerPoint[48].size)
        assertTrue(setsPerPoint[48].all { !it.tiebreak })
    }

    private fun points(count: Int): List<PointV1> {
        return (1..count).map { point("p$it", it) }
    }

    private fun point(id: String, index: Int): PointV1 {
        val start = index * 1_000
        return PointV1(id = id, startMs = start, endMs = start + 500)
    }
}
