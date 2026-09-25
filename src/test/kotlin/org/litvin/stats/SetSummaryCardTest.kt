package org.litvin.stats

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetSummaryCardTest {
    // One game wins a set with a two-game lead, so each set has 8 points: 2-0 in games.
    private val shortSets = MatchRulesV1(gamesPerSet = 1, setTiebreak = false)

    @Test
    fun eachCompletedSetHasACardWithThePointsOfTheSet() {
        val (edl, score) = project("11111111" + "22222222" + "1111", shortSets)

        val summaries = StatsCardVideo.setSummaries(edl, score, StatsSettingsV1(), 1920, 1080)

        assertEquals(listOf(1, 2), summaries.map { it.setNumber })
        assertEquals((1..8).map { "p$it" }.toSet(), summaries[0].pointIds)
        assertEquals((9..16).map { "p$it" }.toSet(), summaries[1].pointIds)
        assertTrue(summaries.all { it.card.pages.isNotEmpty() })
    }

    @Test
    fun aSetCardHasTheChartOfItsSet() {
        val (edl, score) = project("11111111" + "22222222" + "1111", shortSets)

        val card = StatsCardVideo.setSummaries(edl, score, StatsSettingsV1(), 1920, 1080)[1].card

        assertEquals(2, card.pages.size)
        val line = card.pages.last().items.filterIsInstance<org.litvin.export.scoreboard.SceneItem.Polyline>().single()
        // The start at zero and the 8 points of set 2.
        assertEquals(9, line.points.size)
    }

    @Test
    fun aOneSetMatchHasNoSetCards() {
        val (edl, score) = project("11111111", shortSets.copy(bestOfSets = 1))

        assertEquals(emptyList(), StatsCardVideo.setSummaries(edl, score, StatsSettingsV1(), 1920, 1080))
    }

    @Test
    fun withoutSelectedStatisticsTheSetsHaveNoCards() {
        val (edl, score) = project("11111111" + "22222222" + "1111", shortSets)

        val summaries = StatsCardVideo.setSummaries(edl, score, StatsSettingsV1(videoStats = emptyList(), videoMomentum = false), 1920, 1080)

        assertEquals(emptyList(), summaries)
    }

    private fun project(winners: String, rules: MatchRulesV1): Pair<EdlV1, ScoreV1> {
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        return EdlV1(points = points) to ScoreV1(outcomes = outcomes, rules = rules)
    }
}
