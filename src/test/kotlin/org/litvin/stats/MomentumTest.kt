package org.litvin.stats

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreV1
import kotlin.test.Test
import kotlin.test.assertEquals

class MomentumTest {
    // One game wins a set with a two-game lead, so each set has 8 points: 2-0 in games.
    private val shortSets = MatchRulesV1(gamesPerSet = 1, setTiebreak = false)

    @Test
    fun theMatchMomentumAddsTheScoredPointsAndMarksTheSetStarts() {
        // Point 10 has no winner, so the chart does not show it.
        val momentum = Momentum.of(report("11111111" + "2.22", shortSets))

        assertEquals((1..8).toList() + listOf(7, 6, 5), momentum.points.map { it.difference })
        assertEquals(listOf(8), momentum.setStarts)
        assertEquals(listOf("p9", "p11", "p12"), momentum.points.drop(8).map { it.pointId })
        assertEquals(listOf(9, 11, 12), momentum.points.drop(8).map { it.number })
        assertEquals(setOf(2), momentum.points.drop(8).map { it.set }.toSet())
        assertEquals(PerPlayer(8, 0), momentum.maxLead)
    }

    @Test
    fun aSetMomentumStartsAtZero() {
        val momentum = Momentum.of(report("11111111" + "2.22", shortSets), scope = 2)

        assertEquals(listOf(-1, -2, -3), momentum.points.map { it.difference })
        assertEquals(emptyList(), momentum.setStarts)
        assertEquals(PerPlayer(0, 3), momentum.maxLead)
    }

    private fun report(winners: String, rules: MatchRulesV1): StatsReport {
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).mapNotNull { (point, c) ->
            when (c) {
                '1' -> point.id to Outcome.P1
                '2' -> point.id to Outcome.P2
                else -> null
            }
        }.toMap()
        return StatsReport.build(EdlV1(points = points), ScoreV1(outcomes = outcomes, rules = rules))
    }
}
