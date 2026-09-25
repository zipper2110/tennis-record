package org.litvin.stats

import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStructure
import org.litvin.scoring.Outcome
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreV1
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatRowsTest {
    @Test
    fun theRowsHaveTheCatalogOrder() {
        val rows = rows(report("1111"))

        assertEquals(MatchStat.entries.toList(), rows.map { it.stat })
    }

    @Test
    fun valuesShowTheMainTextAndTheDetail() {
        // Player 1 holds to love. Then player 1 breaks from 40–15.
        val rows = rows(report("1111" + "112121", serverMarks = mapOf("p1" to Outcome.P1)))

        assertEquals(PerPlayer(StatValue("8", "80%"), StatValue("2", "20%")), rows.of(MatchStat.POINTS_WON).values)
        assertEquals(PerPlayer(StatValue("100%", "4/4"), StatValue("33%", "2/6")), rows.of(MatchStat.SERVICE_POINTS_WON).values)
        assertEquals(PerPlayer(StatValue("1/2", "50%"), StatValue("0/0")), rows.of(MatchStat.BREAK_POINTS_WON).values)
        assertEquals(PerPlayer(StatValue("2", "100%"), StatValue("0", "0%")), rows.of(MatchStat.GAMES_WON).values)
        assertEquals(PerPlayer(StatValue("100%", "1/1"), StatValue("0%", "0/1")), rows.of(MatchStat.RETURN_GAMES_WON).values)
    }

    @Test
    fun shortAndLongPointsUseTheLimitsOfTheSettings() {
        // The points last 3 s, 7 s, 10 s and 20 s.
        val report = report("1122", durationsSeconds = listOf(3, 7, 10, 20))

        val defaults = StatRows.build(report.match, report.score.rules)
        assertEquals(PerPlayer(StatValue("100%", "2/2"), StatValue("0%", "0/2")), defaults.of(MatchStat.SHORT_POINTS_WON).values)
        assertEquals(PerPlayer(StatValue("0%", "0/1"), StatValue("100%", "1/1")), defaults.of(MatchStat.LONG_POINTS_WON).values)
        assertEquals("Short points won (\u2264 7 s)", defaults.of(MatchStat.SHORT_POINTS_WON).label)
        assertEquals("Long points won (\u2265 15 s)", defaults.of(MatchStat.LONG_POINTS_WON).label)

        val custom = StatRows.build(report.match, report.score.rules, StatsSettingsV1(shortPointMaxSeconds = 3, longPointMinSeconds = 10))
        assertEquals(PerPlayer(StatValue("100%", "1/1"), StatValue("0%", "0/1")), custom.of(MatchStat.SHORT_POINTS_WON).values)
        assertEquals(PerPlayer(StatValue("0%", "0/2"), StatValue("100%", "2/2")), custom.of(MatchStat.LONG_POINTS_WON).values)
    }

    @Test
    fun aLengthWithoutPointsHasNoValue() {
        val rows = StatRows.build(
            report("1122", durationsSeconds = listOf(3, 7, 10, 20)).match,
            MatchRulesV1(),
            StatsSettingsV1(shortPointMaxSeconds = 1),
        )

        assertEquals(StatRows.NO_POINTS_OF_LENGTH, rows.of(MatchStat.SHORT_POINTS_WON).unavailableReason)
    }

    @Test
    fun aLongPointIsAlwaysLongerThanAShortPoint() {
        val settings = StatsSettingsV1(shortPointMaxSeconds = 20, longPointMinSeconds = 10).normalized()

        assertEquals(20, settings.shortPointMaxSeconds)
        assertEquals(21, settings.longPointMinSeconds)
    }

    @Test
    fun serveRowsNeedAServerMark() {
        val row = rows(report("11112222")).of(MatchStat.SERVICE_POINTS_WON)

        assertFalse(row.available)
        assertEquals(StatRows.NEEDS_SERVER, row.unavailableReason)
    }

    @Test
    fun manualScoringHasNoBreakPointsButKeepsTheServeShare() {
        val rules = MatchRulesV1(manualScoring = true)
        val rows = rows(report("11112222", rules, serverMarks = mapOf("p1" to Outcome.P1)))

        assertEquals(StatRows.MANUAL_SCORING, rows.of(MatchStat.BREAK_POINTS_WON).unavailableReason)
        assertTrue(rows.of(MatchStat.SERVICE_POINTS_WON).available)
    }

    @Test
    fun formatsWithoutGamesHideTheGameRows() {
        val rows = rows(report("1212", MatchRulesV1(structure = MatchStructure.PLAIN_POINTS)))

        assertEquals(StatRows.NO_GAMES, rows.of(MatchStat.GAMES_WON).unavailableReason)
        assertEquals(StatRows.NO_MATCH_END, rows.of(MatchStat.MATCH_POINTS_WON).unavailableReason)
        assertTrue(rows.of(MatchStat.LONGEST_POINT_RUN).available)
    }

    @Test
    fun timeRowsHaveOneValueForBothPlayers() {
        // The test points start every 10 s and last 5 s.
        val rows = rows(report("12"))

        assertEquals(StatValue("0:15"), rows.of(MatchStat.DURATION).shared)
        assertEquals(StatValue("5.0 s"), rows.of(MatchStat.AVERAGE_POINT).shared)
        assertNull(rows.of(MatchStat.DURATION).values)
    }

    @Test
    fun clockShowsHoursOnlyWhenNecessary() {
        assertEquals("42:07", StatRows.clock(42 * 60_000L + 7_000))
        assertEquals("1:02:07", StatRows.clock(3_600_000L + 2 * 60_000 + 7_000))
    }

    @Test
    fun setScoresShowCompletedSetsTheMatchTiebreakAndTheCurrentSet() {
        // Sets to 1 game, match tiebreak at one set all.
        val rules = MatchRulesV1(gamesPerSet = 1, setTiebreak = false, finalSet = org.litvin.scoring.FinalSetRule.MATCH_TIEBREAK)
        val setOne = "1111" + "1111"
        val setTwo = "2222" + "2222"
        val matchTiebreak = "1".repeat(10)
        val report = report(setOne + setTwo + matchTiebreak, rules)

        assertEquals(listOf("2-0", "0-2", "[10-0]"), report.setScores())
    }

    @Test
    fun thePointsAfterTheEndOfTheMatchDoNotCount() {
        // One tiebreak to 3 points: player 1 wins 3–0. Then the user scored two more points.
        val report = report("111" + "22", MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 3))

        assertEquals(2, report.pointsAfterMatchEnd)
        assertEquals(PerPlayer(3, 0), report.match.pointsWon)
        assertEquals(listOf(0..2), report.setRanges)
    }

    @Test
    fun formatsWithoutAnEndUseAllPoints() {
        val report = report("1111" + "2222", MatchRulesV1(structure = MatchStructure.GAMES_ONLY))

        assertEquals(0, report.pointsAfterMatchEnd)
        assertEquals(8, report.match.points)
    }

    @Test
    fun anIncompleteSetShowsItsCurrentGames() {
        assertEquals(listOf("2-1"), report("1111" + "2222" + "1111", MatchRulesV1(gamesPerSet = 4)).setScores())
    }

    @Test
    fun settingsKeepTheCatalogOrder() {
        val settings = StatsSettingsV1(videoStats = listOf(MatchStat.DURATION.key))
            .withInVideo(MatchStat.POINTS_WON, true)
            .withInVideo(MatchStat.DURATION, false)
            .withInVideo(MatchStat.GAMES_WON, true)

        assertEquals(listOf(MatchStat.POINTS_WON.key, MatchStat.GAMES_WON.key), settings.videoStats)
    }

    @Test
    fun settingsRoundTripAndMissingFileGivesDefaults() {
        val dir = Files.createTempDirectory("stats-io").toFile()
        try {
            assertEquals(MatchStat.defaultVideoKeys, StatsIO.readForProjectDir(dir.path).videoStats)

            val settings = StatsSettingsV1(videoStats = listOf("games_won", "unknown_future_stat"))
            StatsIO.writeForProjectDir(dir.path, settings)

            assertEquals(settings, StatsIO.readForProjectDir(dir.path))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun rows(report: StatsReport): List<StatRow> = StatRows.build(report.match, report.score.rules)

    private fun List<StatRow>.of(stat: MatchStat): StatRow = single { it.stat == stat }

    private fun report(
        winners: String,
        rules: MatchRulesV1 = MatchRulesV1(),
        serverMarks: Map<String, Outcome> = emptyMap(),
        durationsSeconds: List<Int>? = null,
    ): StatsReport {
        // Points of 5 s every 10 s. Points with a set duration start every 100 s, so that they do not overlap.
        val spacingMs = if (durationsSeconds == null) 10_000 else 100_000
        val points = (1..winners.length).map {
            val durationMs = durationsSeconds?.get(it - 1)?.times(1_000) ?: 5_000
            PointV1(id = "p$it", startMs = it * spacingMs, endMs = it * spacingMs + durationMs)
        }
        val outcomes = points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        return StatsReport.build(EdlV1(points = points), ScoreV1(outcomes = outcomes, rules = rules, serverMarks = serverMarks))
    }
}
