package org.litvin.scoring

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals

class ServeTimelineTest {
    @Test
    fun withoutServerMarksNoPointHasAServer() {
        val points = points(8)
        val outcomes = outcomes(points, "11112222")

        val servers = ScoringEngine.timeline(points, outcomes).serverOfPoint

        assertEquals(List(8) { null }, servers)
    }

    @Test
    fun theServerChangesAfterEachGame() {
        val points = points(12)
        val outcomes = outcomes(points, "111122221111")

        val servers = ScoringEngine.timeline(points, outcomes, serverMarks = mapOf("p1" to Outcome.P1)).serverOfPoint

        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1), servers)
    }

    @Test
    fun thePointsBeforeTheFirstMarkGetTheServersThatLeadToIt() {
        val points = points(8)
        val outcomes = outcomes(points, "11112222")

        // Player 1 serves the second game, so player 2 served the first game.
        val servers = ScoringEngine.timeline(points, outcomes, serverMarks = mapOf("p6" to Outcome.P1)).serverOfPoint

        assertEquals(listOf(2, 2, 2, 2, 1, 1, 1, 1), servers)
    }

    @Test
    fun aMarkInsideAGameStaysUntilTheEndOfTheGame() {
        val points = points(8)
        val outcomes = outcomes(points, "11112222")
        val marks = mapOf("p1" to Outcome.P1, "p3" to Outcome.P2)

        val servers = ScoringEngine.timeline(points, outcomes, serverMarks = marks).serverOfPoint

        // The mark on point 3 changes the server of points 3 and 4. The next game continues from the mark.
        assertEquals(listOf(1, 1, 2, 2, 1, 1, 1, 1), servers)
    }

    @Test
    fun noPointAndUnscoredPointsKeepTheServerOfTheNextPoint() {
        val points = points(7)
        val outcomes = outcomes(points, "11-1.12")

        val servers = ScoringEngine.timeline(points, outcomes, serverMarks = mapOf("p1" to Outcome.P2)).serverOfPoint

        assertEquals(listOf(2, 2, 2, 2, 2, 2, 1), servers)
    }

    @Test
    fun aSetTiebreakChangesTheServerAfterTheFirstPointAndThenAfterEveryTwoPoints() {
        val rules = MatchRulesV1(gamesPerSet = 1)
        // Game 1 to player 1, game 2 to player 2 (1–1), a tiebreak to 7–0, then the first game of set 2.
        val points = points(20)
        val outcomes = outcomes(points, "1111" + "2222" + "1111111" + "1")

        val servers = ScoringEngine.timeline(points, outcomes, rules, serverMarks = mapOf("p1" to Outcome.P1)).serverOfPoint

        assertEquals(listOf(1, 1, 1, 1), servers.subList(0, 4))
        assertEquals(listOf(2, 2, 2, 2), servers.subList(4, 8))
        // Player 1 serves the tiebreak because player 1 is next in the rotation.
        assertEquals(listOf(1, 2, 2, 1, 1, 2, 2), servers.subList(8, 15))
        // The player who received first in the tiebreak serves the first game of the next set.
        assertEquals(2, servers[15])
    }

    @Test
    fun aMarkInATiebreakMovesTheRestOfTheTiebreak() {
        val rules = MatchRulesV1(gamesPerSet = 1)
        val points = points(16)
        val outcomes = outcomes(points, "1111" + "2222" + "1111111" + "1")
        val marks = mapOf("p1" to Outcome.P1, "p10" to Outcome.P1)

        val servers = ScoringEngine.timeline(points, outcomes, rules, serverMarks = marks).serverOfPoint

        // Tiebreak point 2 is marked for player 1. The rotation continues from the mark: 1, 1, 2, 2, 1, 1.
        assertEquals(listOf(1, 1, 1, 2, 2, 1, 1), servers.subList(8, 15))
        // For the rotation, player 2 served the first tiebreak point, so player 1 serves the next set.
        assertEquals(1, servers[15])
    }

    @Test
    fun aSingleTiebreakMatchUsesTheTiebreakRotation() {
        val rules = MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 7)
        val points = points(6)
        val outcomes = outcomes(points, "121212")

        val servers = ScoringEngine.timeline(points, outcomes, rules, serverMarks = mapOf("p1" to Outcome.P2)).serverOfPoint

        assertEquals(listOf(2, 1, 1, 2, 2, 1), servers)
    }

    @Test
    fun plainPointsUseTheTiebreakRotationAfterTheTiebreakPoints() {
        val rules = MatchRulesV1(structure = MatchStructure.PLAIN_POINTS)
        val points = points(12)
        val outcomes = outcomes(points, "111111111111")

        val servers = ScoringEngine.timeline(points, outcomes, rules, serverMarks = mapOf("p1" to Outcome.P1)).serverOfPoint

        // A 7-point tiebreak ends at 7–0. Plain points continue with the same rotation.
        assertEquals(listOf(1, 2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 1), servers)
    }

    @Test
    fun manualScoringChangesTheServerAfterEachMarkedGame() {
        val rules = MatchRulesV1(manualScoring = true)
        val points = points(4)
        val outcomes = outcomes(points, "1122")
        val manual = ManualScoreMarks(gameWins = mapOf("p2" to Outcome.P1))

        val servers = ScoringEngine.timeline(points, outcomes, rules, manual, mapOf("p1" to Outcome.P1)).serverOfPoint

        assertEquals(listOf(1, 1, 2, 2), servers)
    }

    @Test
    fun snapshotsBeforeEachPointCarryTheServer() {
        val points = points(5)
        val outcomes = outcomes(points, "11112")

        val snapshots = ScoringRules.computeSnapshotsBefore(points, outcomes, serverMarks = mapOf("p1" to Outcome.P2))

        assertEquals(listOf(2, 2, 2, 2, 1), snapshots.map { it.server })
    }

    /** One character per point: 1 or 2 for the winner, - for no point, . for an unscored point. */
    private fun outcomes(points: List<PointV1>, winners: String): Map<String, Outcome> =
        points.zip(winners.toList()).mapNotNull { (point, c) ->
            when (c) {
                '1' -> point.id to Outcome.P1
                '2' -> point.id to Outcome.P2
                '-' -> point.id to Outcome.NONE
                else -> null
            }
        }.toMap()

    private fun points(count: Int): List<PointV1> =
        (1..count).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
}
