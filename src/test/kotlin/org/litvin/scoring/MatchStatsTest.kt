package org.litvin.scoring

import org.litvin.points.PointV1
import org.litvin.scoring.ScoringEngine.Stake
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchStatsTest {
    @Test
    fun countsPointsGamesAndSets() {
        val stats = stats("1111" + "2222" + "1112" + "1")

        assertEquals(13, stats.points)
        assertEquals(PerPlayer(8, 5), stats.pointsWon)
        assertEquals(PerPlayer(2, 1), stats.gamesWon)
        assertEquals(PerPlayer(0, 0), stats.setsWon)
    }

    @Test
    fun unscoredPointsDoNotCount() {
        val stats = stats("11-.2")

        assertEquals(5, stats.points)
        assertEquals(3, stats.scoredPoints)
        assertEquals(PerPlayer(2, 1), stats.pointsWon)
    }

    @Test
    fun withoutServerMarksTheServeStatisticsAreNull() {
        assertNull(stats("11112222").serve)
    }

    @Test
    fun serveReturnAndBreakPoints() {
        // Player 1 holds to love. Then player 1 breaks from 40–15: two break points, the second one wins.
        val serve = stats("1111" + "112121", serverMarks = mapOf("p1" to Outcome.P1)).serve!!

        assertEquals(PerPlayer(Ratio(4, 4), Ratio(2, 6)), serve.servicePointsWon)
        assertEquals(PerPlayer(Ratio(4, 6), Ratio(0, 4)), serve.returnPointsWon)
        assertEquals(PerPlayer(Ratio(1, 1), Ratio(0, 1)), serve.serviceGamesWon)
        assertEquals(PerPlayer(Ratio(1, 2), Ratio(0, 0)), serve.breakPointsWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(1, 2)), serve.breakPointsSaved)
    }

    @Test
    fun aTiebreakIsNotAServiceGameAndHasNoBreakPoints() {
        // Sets to 1 game: at 1–1 the players play a tiebreak.
        val rules = MatchRulesV1(gamesPerSet = 1)
        val stats = stats("1111" + "2222" + "1111111", rules, serverMarks = mapOf("p1" to Outcome.P1))

        assertEquals(PerPlayer(1, 0), stats.tiebreaksWon)
        assertEquals(PerPlayer(2, 1), stats.gamesWon)
        assertEquals(PerPlayer(1, 0), stats.setsWon)
        assertEquals(PerPlayer(Ratio(1, 1), Ratio(1, 1)), stats.serve!!.serviceGamesWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(0, 0)), stats.serve!!.breakPointsWon)
    }

    @Test
    fun setPointsAndMatchPoints() {
        // One tiebreak to 3 points: at 2–0 and at 2–1 player 1 has a match point.
        val rules = MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 3)
        val stats = stats("1121", rules)

        assertEquals(PerPlayer(Ratio(1, 2), Ratio(0, 0)), stats.matchPointsWon)
        assertEquals(PerPlayer(Ratio(1, 2), Ratio(0, 0)), stats.setPointsWon)
    }

    @Test
    fun aSetPointThatDoesNotEndTheMatchIsNotAMatchPoint() {
        val stats = stats("1".repeat(24))

        assertEquals(PerPlayer(Ratio(1, 1), Ratio(0, 0)), stats.setPointsWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(0, 0)), stats.matchPointsWon)
    }

    @Test
    fun deucePointsAreThePointsAtEqualScoreFrom40All() {
        // 40–40, advantage player 1, deuce, advantage player 1, game.
        assertEquals(PerPlayer(2, 0), stats("111222" + "1211").deucePointsWon)
        // With no-ad scoring, the deciding point is the only deuce point.
        assertEquals(PerPlayer(1, 0), stats("111222" + "1", MatchRulesV1(deuce = DeuceRule.NO_AD)).deucePointsWon)
    }

    @Test
    fun manualScoringHasNoStakesAndNoDeucePoints() {
        val stats = stats("111222" + "1211", MatchRulesV1(manualScoring = true))

        assertEquals(PerPlayer(0, 0), stats.deucePointsWon)
        assertEquals(PerPlayer(Ratio(0, 0), Ratio(0, 0)), stats.setPointsWon)
    }

    @Test
    fun runsAndLargestLead() {
        val stats = stats("11122111")

        assertEquals(PerPlayer(3, 2), stats.longestPointRun)
        assertEquals(PerPlayer(4, 0), stats.largestPointLead)
    }

    @Test
    fun anUnscoredPointDoesNotStopARun() {
        assertEquals(PerPlayer(3, 0), stats("11-1").longestPointRun)
    }

    @Test
    fun longestGameRun() {
        assertEquals(PerPlayer(2, 1), stats("1111" + "2222" + "1111" + "1111").longestGameRun)
    }

    @Test
    fun theStakesShowABreakPoint() {
        val points = points(6)
        val timeline = ScoringEngine.timeline(points, outcomes(points, "222"), serverMarks = mapOf("p1" to Outcome.P1))

        // At 0–40 the receiver (player 2) wins the game with the next point.
        assertEquals(ScoringEngine.PointStakes(Stake.NONE, Stake.GAME), timeline.stakesOfPoint[3])
    }

    @Test
    fun setRangesEndAtTheSetWinningPoint() {
        val rules = MatchRulesV1(gamesPerSet = 4)
        val points = points(19)
        val outcomes = outcomes(points, "1".repeat(16) + "221")
        val timeline = ScoringEngine.timeline(points, outcomes, rules)

        val ranges = MatchStats.setRanges(timeline)

        assertEquals(listOf(0..15, 16..18), ranges)
        assertEquals(PerPlayer(1, 2), MatchStats.compute(points, outcomes, timeline, rules, ranges[1]).pointsWon)
    }

    @Test
    fun theMatchEndsAtThePointThatWinsTheLastSet() {
        // Best of 1 set to 1 game without a tiebreak: player 1 wins 2–0 at point 8.
        val rules = MatchRulesV1(bestOfSets = 1, gamesPerSet = 1, setTiebreak = false)
        val points = points(10)
        val timeline = ScoringEngine.timeline(points, outcomes(points, "11111111" + "22"), rules)

        assertEquals(7, MatchStats.matchEndIndex(timeline, rules))
        assertEquals(listOf(0..7), MatchStats.setRanges(timeline, 0..7))
    }

    @Test
    fun manualScoringAndOpenFormatsHaveNoMatchEnd() {
        val points = points(8)
        val outcomes = outcomes(points, "11111111")
        for (rules in listOf(MatchRulesV1(manualScoring = true), MatchRulesV1(structure = MatchStructure.GAMES_ONLY))) {
            assertNull(MatchStats.matchEndIndex(ScoringEngine.timeline(points, outcomes, rules), rules))
        }
    }

    @Test
    fun withoutCompletedSetsAllPointsAreOneRange() {
        val points = points(5)
        val timeline = ScoringEngine.timeline(points, outcomes(points, "11221"))

        assertEquals(listOf(0..4), MatchStats.setRanges(timeline))
    }

    @Test
    fun timeStatistics() {
        val points = listOf(
            PointV1(id = "p1", startMs = 0, endMs = 5_000),
            PointV1(id = "p2", startMs = 7_000, endMs = 10_000),
            PointV1(id = "p3", startMs = 12_000, endMs = 20_000),
        )
        val outcomes = outcomes(points, "121")
        val timeline = ScoringEngine.timeline(points, outcomes)

        val time = MatchStats.compute(points, outcomes, timeline, MatchRulesV1()).time

        assertEquals(20_000L, time.durationMs)
        assertEquals(16_000L, time.playingMs)
        assertEquals(5_333L, time.averagePointMs)
        assertEquals("p3", time.longestPoint?.id)
        assertEquals(PerPlayer<Long?>(6_500, 3_000), time.averagePointMsWon)
        assertEquals(2_000L, time.averageGapMs)
    }

    @Test
    fun noPointsGiveEmptyStatistics() {
        val stats = stats("")

        assertEquals(0, stats.points)
        assertEquals(0L, stats.time.durationMs)
        assertNull(stats.time.averagePointMs)
        assertNull(stats.time.longestPoint)
        assertEquals(PerPlayer(0, 0), stats.longestPointRun)
    }

    @Test
    fun theKeyPointsAreTheStartOfTheLongestRunAndThePointOfTheLargestLead() {
        // An unscored point (p6) does not stop the run of player 1: p5, p7 and p8.
        val stats = stats("12221.11")

        assertEquals(PerPlayer(3, 3), stats.longestPointRun)
        assertEquals(PerPlayer<String?>("p5", "p2"), stats.keyPoints.longestPointRunStart)
        assertEquals(PerPlayer(1, 2), stats.largestPointLead)
        assertEquals(PerPlayer<String?>("p1", "p4"), stats.keyPoints.largestPointLeadAt)
    }

    @Test
    fun aPlayerWithoutPointsHasNoKeyPoints() {
        val stats = stats("111")

        assertNull(stats.keyPoints.longestPointRunStart.p2)
        assertNull(stats.keyPoints.largestPointLeadAt.p2)
        assertEquals("p3", stats.keyPoints.largestPointLeadAt.p1)
    }

    private fun stats(
        winners: String,
        rules: MatchRulesV1 = MatchRulesV1(),
        serverMarks: Map<String, Outcome> = emptyMap(),
    ): MatchStats {
        val points = points(winners.length)
        val outcomes = outcomes(points, winners)
        val timeline = ScoringEngine.timeline(points, outcomes, rules, serverMarks = serverMarks)
        return MatchStats.compute(points, outcomes, timeline, rules)
    }

    /** '1' and '2' are the point winners, '-' is no point, and any other character leaves the point unscored. */
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
