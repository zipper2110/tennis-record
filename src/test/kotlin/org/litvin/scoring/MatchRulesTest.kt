package org.litvin.scoring

import org.litvin.points.PointV1
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchRulesTest {
    /** Point outcomes as text: '1' = point for player 1, '2' = point for player 2, '0' = no point. */
    private class Match(sequence: String) {
        val points: List<PointV1> = sequence.indices.map { i ->
            PointV1(id = "p${i + 1}", startMs = (i + 1) * 1_000, endMs = (i + 1) * 1_000 + 500)
        }
        val outcomes: Map<String, Outcome> = sequence.withIndex().associate { (i, c) ->
            "p${i + 1}" to when (c) {
                '1' -> Outcome.P1
                '2' -> Outcome.P2
                else -> Outcome.NONE
            }
        }

        fun timeline(rules: MatchRulesV1, marks: ManualScoreMarks = ManualScoreMarks()) =
            ScoringEngine.timeline(points, outcomes, rules, marks)
    }

    private fun game(player: Int) = player.toString().repeat(4)
    private fun games(vararg players: Int) = players.joinToString("") { game(it) }
    private fun set(player: Int, gamesPerSet: Int = 6) = game(player).repeat(gamesPerSet)

    @Test
    fun noAdScoringWinsTheGameOnTheDecidingPoint() {
        val sequence = "1112221"
        val advantage = Match(sequence).timeline(MatchRulesV1()).statesAfterPoint.last()
        val noAd = Match(sequence).timeline(MatchRulesV1(deuce = DeuceRule.NO_AD)).statesAfterPoint.last()

        assertEquals(0, advantage.gamesP1)
        assertEquals(4, advantage.p1Pts)
        assertEquals(1, noAd.gamesP1)
        assertEquals(0, noAd.p1Pts)
        assertEquals(1, noAd.lastGameWonBy)
    }

    @Test
    fun shortSetsEndAtFourGamesAndPlayATiebreakAtFourAll() {
        val rules = MatchRulesV1(gamesPerSet = 4)
        val straight = Match(set(1, gamesPerSet = 4)).timeline(rules)
        assertEquals(listOf(ScoringEngine.SetScore(4, 0, false)), straight.setsAfterPoint.last())

        val toFourAll = games(1, 2, 1, 2, 1, 2, 1, 2)
        val tiebreak = Match(toFourAll + "2222222").timeline(rules)
        val atFourAll = tiebreak.statesAfterPoint[toFourAll.length - 1]
        assertTrue(atFourAll.isTiebreak)
        assertEquals(4, atFourAll.gamesP1)
        assertEquals(4, atFourAll.gamesP2)

        val last = tiebreak.statesAfterPoint.last()
        assertEquals(ScoringEngine.SetScore(4, 5, true), tiebreak.setsAfterPoint.last().single())
        assertEquals(2, last.lastSetWonBy)
        assertEquals(2, last.lastGameWonBy)
        assertFalse(last.isTiebreak)
    }

    @Test
    fun advantageSetContinuesUntilATwoGameLead() {
        val toSixAll = games(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
        val timeline = Match(toSixAll + games(1, 1)).timeline(MatchRulesV1(setTiebreak = false))

        assertFalse(timeline.statesAfterPoint[toSixAll.length - 1].isTiebreak)
        assertEquals(ScoringEngine.SetScore(8, 6, false), timeline.setsAfterPoint.last().single())
    }

    @Test
    fun matchTiebreakDecidesTheMatchAtOneSetAll() {
        val rules = MatchRulesV1(finalSet = FinalSetRule.MATCH_TIEBREAK)
        val setsOneAll = set(1) + set(2)
        val timeline = Match(setsOneAll + "1".repeat(10) + "2").timeline(rules)

        val afterSecondSet = timeline.statesAfterPoint[setsOneAll.length - 1]
        assertEquals(2, afterSecondSet.lastSetWonBy, "Player 2 won the second set")
        assertTrue(afterSecondSet.isTiebreak)
        assertEquals(0, afterSecondSet.gamesP1)
        assertEquals(0, afterSecondSet.gamesP2)

        val matchPoint = timeline.statesAfterPoint[setsOneAll.length + 9]
        assertEquals(2, matchPoint.setsP1)
        assertEquals(1, matchPoint.setsP2)
        assertEquals(ScoringEngine.SetScore(1, 0, true), timeline.setsAfterPoint[setsOneAll.length + 9].last())

        // The match is over: the last point does not count
        val afterMatch = timeline.statesAfterPoint.last()
        assertEquals(matchPoint.copy(lastGameWonBy = null, lastSetWonBy = null), afterMatch)
    }

    @Test
    fun singleTiebreakNeedsATwoPointLead() {
        val timeline = Match("111111222222" + "11").timeline(MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK))

        assertTrue(timeline.initial.isTiebreak)
        assertTrue(timeline.statesAfterPoint[11].isTiebreak)
        assertEquals(6, timeline.statesAfterPoint[11].p2Pts)
        val last = timeline.statesAfterPoint.last()
        assertEquals(1, last.setsP1)
        assertEquals(1, last.lastSetWonBy)
        assertEquals(ScoringEngine.SetScore(1, 0, true), timeline.setsAfterPoint.last().single())
    }

    @Test
    fun matchTiebreakPresetPlaysToTenPoints() {
        val rules = MatchFormatPreset.MATCH_TIEBREAK.applyTo(MatchRulesV1())
        val timeline = Match("1".repeat(10)).timeline(rules)

        assertTrue(timeline.statesAfterPoint[8].isTiebreak)
        assertEquals(9, timeline.statesAfterPoint[8].p1Pts)
        assertEquals(1, timeline.statesAfterPoint.last().setsP1)
    }

    @Test
    fun gamesOnlyCountsGamesWithoutSets() {
        val last = Match(game(1).repeat(7)).timeline(MatchRulesV1(structure = MatchStructure.GAMES_ONLY))
            .statesAfterPoint.last()

        assertEquals(7, last.gamesP1)
        assertEquals(0, last.setsP1)
        assertNull(last.lastSetWonBy)
    }

    @Test
    fun bestOfFiveNeedsThreeSetsAndOneSetEndsAfterTheFirstSet() {
        val bestOfFive = Match(set(1) + set(1) + game(1) + set(1)).timeline(MatchRulesV1(bestOfSets = 5))
        assertEquals(2, bestOfFive.statesAfterPoint[2 * 24 - 1].setsP1)
        assertEquals(1, bestOfFive.statesAfterPoint[2 * 24 + 3].gamesP1, "The third set is played")
        assertEquals(3, bestOfFive.statesAfterPoint.last().setsP1)

        val oneSet = Match(set(1) + game(2)).timeline(MatchRulesV1(bestOfSets = 1)).statesAfterPoint.last()
        assertEquals(1, oneSet.setsP1)
        assertEquals(0, oneSet.gamesP2, "Points after the match do not count")
    }

    @Test
    fun manualScoringCountsPointsOnlyAndUsesTheMarks() {
        val match = Match("111111" + "2")
        val marks = ManualScoreMarks(
            gameWins = mapOf("p6" to Outcome.P1),
            setWins = mapOf("p6" to Outcome.P1),
        )

        val manual = match.timeline(MatchRulesV1(manualScoring = true), marks)
        val beforeMark = manual.statesAfterPoint[4]
        assertEquals(5, beforeMark.p1Pts, "No automatic game win")
        assertEquals(0, beforeMark.gamesP1)
        val marked = manual.statesAfterPoint[5]
        assertEquals(1, marked.lastGameWonBy)
        assertEquals(1, marked.lastSetWonBy)
        assertEquals(1, marked.setsP1)
        assertEquals(0, marked.p1Pts)
        assertEquals(ScoringEngine.SetScore(1, 0, false), manual.setsAfterPoint[5].single())
        assertEquals(1, manual.statesAfterPoint.last().p2Pts)

        val automatic = match.timeline(MatchRulesV1(), marks).statesAfterPoint[5]
        assertEquals(1, automatic.gamesP1, "Automatic scoring ignores the manual marks")
        assertEquals(0, automatic.setsP1)
    }

    @Test
    fun snapshotsBeforeEachPointStartInsideASingleTiebreak() {
        val match = Match("12")
        val snapshots = ScoringRules.computeSnapshotsBefore(
            match.points,
            match.outcomes,
            MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK),
        )

        assertTrue(snapshots[0].isTiebreak)
        assertEquals(1, snapshots[1].tbP1)
    }

    @Test
    fun scoreboardShowsTheGamesOfTheSetDuringATiebreak() {
        val setTiebreak = ScoreboardComponent.display(
            OverlaySpan(0, 1, "", isTiebreak = true, tbP1 = 3, tbP2 = 1, gamesP1 = 4, gamesP2 = 4),
        )
        val matchTiebreak = ScoreboardComponent.display(
            OverlaySpan(0, 1, "", isTiebreak = true, tbP1 = 3, tbP2 = 1, setsP1 = 1, setsP2 = 1),
        )

        assertEquals(4, setTiebreak.player1Games)
        assertEquals("3", setTiebreak.player1PointText)
        assertEquals(0, matchTiebreak.player1Games)
    }

    @Test
    fun presetOfFindsTheFormatAndIgnoresDeuceAndManualScoring() {
        assertEquals(MatchFormatPreset.BEST_OF_3, MatchFormatPreset.of(MatchRulesV1()))
        assertEquals(MatchFormatPreset.BEST_OF_3, MatchFormatPreset.of(MatchRulesV1(deuce = DeuceRule.NO_AD, manualScoring = true)))
        assertEquals(MatchFormatPreset.PRO_SET, MatchFormatPreset.of(MatchRulesV1(bestOfSets = 1, gamesPerSet = 8)))
        assertEquals(
            MatchFormatPreset.ONE_SET,
            MatchFormatPreset.of(MatchRulesV1(bestOfSets = 1, finalSet = FinalSetRule.MATCH_TIEBREAK)),
            "A one-set match has no deciding set",
        )
        assertEquals(
            MatchFormatPreset.GAMES_ONLY,
            MatchFormatPreset.of(MatchRulesV1(structure = MatchStructure.GAMES_ONLY, bestOfSets = 5, gamesPerSet = 4)),
        )
        assertEquals(MatchFormatPreset.CUSTOM, MatchFormatPreset.of(MatchRulesV1(gamesPerSet = 8)))

        val noAdManual = MatchRulesV1(deuce = DeuceRule.NO_AD, manualScoring = true)
        val applied = MatchFormatPreset.BEST_OF_5.applyTo(noAdManual)
        assertEquals(5, applied.bestOfSets)
        assertEquals(DeuceRule.NO_AD, applied.deuce)
        assertTrue(applied.manualScoring)
        assertEquals(noAdManual, MatchFormatPreset.CUSTOM.applyTo(noAdManual))
    }
}
