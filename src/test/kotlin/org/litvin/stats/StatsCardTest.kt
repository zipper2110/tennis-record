package org.litvin.stats

import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsCardTest {
    @Test
    fun theContentHasOnlySelectedRowsWithAValue() {
        // No server marks: the serve rows have no value.
        val settings = StatsSettingsV1(videoMomentum = false, videoStats = listOf("points_won", "service_points_won", "duration"))

        val content = StatsCard.content(report("11112222"), settings)

        assertEquals(listOf(MatchStat.POINTS_WON, MatchStat.DURATION), content.rows.map { it.stat })
        assertEquals("Match statistics", content.title)
        assertEquals(PerPlayer("Alex", "Sam"), content.names)
        assertEquals(PerPlayer(0x4DA3FF, 0xFF6B6B), content.colors)
    }

    @Test
    fun hiddenPlayerColorsGiveNeutralColors() {
        val score = ScoreV1(scoreboard = ScoreboardSettingsV1(showPlayerColors = false))

        val content = StatsCard.content(report("1111", score), StatsSettingsV1(videoMomentum = false))

        assertEquals(PerPlayer(StatsCard.NEUTRAL_P1, StatsCard.NEUTRAL_P2), content.colors)
    }

    @Test
    fun aDarkPlayerColorGetsLighterOnTheCard() {
        val score = ScoreV1(player1ColorHex = "#000000", player2ColorHex = "#4DA3FF")

        val content = StatsCard.content(report("1111", score), StatsSettingsV1(videoMomentum = false))

        // Black becomes a gray that shows on the dark panel. A bright color does not change.
        assertTrue(StatsCard.luminance(content.colors.p1) >= 0.2, Integer.toHexString(content.colors.p1))
        assertTrue(StatsCard.luminance(content.colors.p1) < 0.22, Integer.toHexString(content.colors.p1))
        assertEquals(0x4DA3FF, content.colors.p2)
    }

    @Test
    fun aDarkColorKeepsItsHue() {
        val lighter = StatsCard.onPanel(0x0D1B6E)

        val red = (lighter shr 16) and 0xFF
        val blue = lighter and 0xFF
        assertTrue(blue > red, Integer.toHexString(lighter))
        assertTrue(StatsCard.luminance(lighter) >= 0.2, Integer.toHexString(lighter))
    }

    @Test
    fun theCardTransparencySetsThePanelOpacity() {
        val content = StatsCard.content(report("1111"), StatsSettingsV1(cardTransparencyPercent = 40, videoMomentum = false))

        val panel = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Box>()[1]

        assertEquals(0.6, panel.opacity, 1e-9)
    }

    @Test
    fun theCardTransparencyStaysInItsRange() {
        assertEquals(StatsSettingsV1.MAX_CARD_TRANSPARENCY_PERCENT, StatsSettingsV1(cardTransparencyPercent = 100).normalized().cardTransparencyPercent)
        assertEquals(0, StatsSettingsV1(cardTransparencyPercent = -5).normalized().cardTransparencyPercent)
    }

    @Test
    fun aSetScopeHasTheSetTitleAndScore() {
        val rules = MatchRulesV1(gamesPerSet = 1, setTiebreak = false)

        val content = StatsCard.content(report("11111111" + "2222", ScoreV1(rules = rules)), StatsSettingsV1(videoMomentum = false), scope = 2)

        assertEquals("Set 2 statistics", content.title)
        assertEquals("0-1", content.score)
    }

    @Test
    fun eachPageHasAtMostSevenRows() {
        val settings = StatsSettingsV1(videoMomentum = false, videoStats = MatchStat.entries.map { it.key })
        val content = StatsCard.content(report("1111" + "2222"), settings)

        val pages = StatsCard.pages(content)

        assertTrue(content.rows.size > StatsCard.ROWS_PER_PAGE)
        assertEquals((content.rows.size + StatsCard.ROWS_PER_PAGE - 1) / StatsCard.ROWS_PER_PAGE, pages.size)
        assertTrue(pages.first().labels().contains("1 / ${pages.size}"))
    }

    @Test
    fun thePagesShareTheRowsEqually() {
        val keys = listOf("points_won", "games_won", "sets_won", "tiebreaks_won", "longest_point_run", "largest_point_lead", "duration", "average_point")
        val content = StatsCard.content(report("1111" + "2222"), StatsSettingsV1(videoMomentum = false, videoStats = keys))

        val rowsOnPages = StatsCard.pages(content).map { page -> page.labels().count { it in keys.map { key -> MatchStat.ofKey(key)!!.label } } }

        assertEquals(listOf(4, 4), rowsOnPages)
    }

    @Test
    fun aGroupStaysOnOnePageWhenThePageCountDoesNotChange() {
        // 3 overview rows, 4 serve rows and 1 pressure row. An equal split (4 + 4) breaks the serve group.
        val rows = rowsOf(StatGroup.OVERVIEW, 3) + rowsOf(StatGroup.SERVE, 4) + rowsOf(StatGroup.PRESSURE, 1)

        val pages = StatsCard.splitRows(rows, 2)

        assertEquals(listOf(3, 5), pages.map { it.size })
    }

    @Test
    fun theRowsShareEquallyWhenTheGroupsDoNotFitOnThePages() {
        // 4 + 4 + 5 rows. Each split between the groups gives a page with more than 7 rows.
        val rows = rowsOf(StatGroup.OVERVIEW, 4) + rowsOf(StatGroup.SERVE, 4) + rowsOf(StatGroup.PRESSURE, 5)

        val pages = StatsCard.splitRows(rows, 2)

        assertEquals(listOf(7, 6), pages.map { it.size })
    }

    private fun rowsOf(group: StatGroup, count: Int): List<StatRow> =
        MatchStat.entries.filter { it.group == group }.take(count).map { StatRow(it) }.also { assertEquals(count, it.size) }

    @Test
    fun noRowsGiveNoPages() {
        val content = StatsCard.content(report("1111"), StatsSettingsV1(videoMomentum = false, videoStats = emptyList()))

        assertEquals(emptyList(), StatsCard.pages(content))
    }

    @Test
    fun theSceneCoversTheFrame() {
        val content = StatsCard.content(report("1111"), StatsSettingsV1(videoMomentum = false))

        val scene = StatsCard.pages(content, frameWidth = 1440.0).single()
        val dim = scene.items.first() as SceneItem.Box

        assertEquals(1440.0, scene.width)
        assertEquals(StatsCard.HEIGHT, scene.height)
        assertEquals(listOf(0.0, 0.0, 1440.0, StatsCard.HEIGHT), listOf(dim.x, dim.y, dim.width, dim.height))
    }

    @Test
    fun theBarSplitsByTheValues() {
        // Player 1 wins 6 of 8 points.
        val content = StatsCard.content(report("11112211"), StatsSettingsV1(videoMomentum = false, videoStats = listOf("points_won")))

        val bars = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Box>()
            .filter { it.rgb == 0x4DA3FF || it.rgb == 0xFF6B6B }

        assertEquals(2, bars.size)
        assertEquals(3.0, bars[0].width / bars[1].width, 0.001)
    }

    @Test
    fun aLongNameGetsSmallerAndShorter() {
        val name = "Maximilian Alexander von Longname-Featherstonehaugh"
        val content = StatsCard.content(report("1111", ScoreV1(player1Name = name)), StatsSettingsV1(videoMomentum = false))

        val label = StatsCard.pages(content).single().items.filterIsInstance<SceneItem.Label>().first { it.x < 500 }

        assertTrue(label.text.endsWith("…"))
        assertTrue(ScoreboardFonts.textWidth(label.text, label.font, label.bold, label.size) <= 1240 * 0.3)
    }

    @Test
    fun theMomentumChartIsTheLastPage() {
        val content = StatsCard.content(report("11" + "2222"), StatsSettingsV1())

        val pages = StatsCard.pages(content)

        assertEquals(2, pages.size)
        val chart = pages.last()
        assertTrue(chart.labels().containsAll(listOf("Point difference", "Alex +2", "Sam +2", "2 / 2")), chart.labels().toString())
        // One area for each player, and the line.
        assertEquals(listOf(content.colors.p1, content.colors.p2), chart.items.filterIsInstance<SceneItem.Polygon>().map { it.rgb })
        assertEquals(7, chart.items.filterIsInstance<SceneItem.Polyline>().single().points.size)
    }

    @Test
    fun aCardWithOnlyTheChartHasOnePageWithTheHeightOfFourRows() {
        val content = StatsCard.content(report("1122"), StatsSettingsV1(videoStats = emptyList()))

        val page = StatsCard.pages(content).single()

        val panel = page.items.filterIsInstance<SceneItem.Box>()[1]
        assertEquals(170.0 + 4 * 104.0 + 70.0, panel.height)
    }

    @Test
    fun theChartMarksTheStartOfEachSet() {
        // Sets to 1 game without a tiebreak: set 2 starts at point 9.
        val rules = MatchRulesV1(gamesPerSet = 1, setTiebreak = false)
        val content = StatsCard.content(report("11111111" + "2222", ScoreV1(player1Name = "Alex", player2Name = "Sam", rules = rules)), StatsSettingsV1(videoStats = emptyList()))

        val chart = StatsCard.pages(content).single()

        assertTrue("Set 2" in chart.labels(), chart.labels().toString())
    }

    private fun org.litvin.export.scoreboard.ScoreboardScene.labels(): List<String> =
        items.filterIsInstance<SceneItem.Label>().map { it.text }

    private fun report(winners: String, score: ScoreV1 = ScoreV1(player1Name = "Alex", player2Name = "Sam")): StatsReport {
        val points = (1..winners.length).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }
        val outcomes = points.zip(winners.toList()).associate { (point, c) -> point.id to if (c == '1') Outcome.P1 else Outcome.P2 }
        return StatsReport.build(EdlV1(points = points), score.copy(outcomes = outcomes))
    }
}
