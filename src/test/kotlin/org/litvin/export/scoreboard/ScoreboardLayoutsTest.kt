package org.litvin.export.scoreboard

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardLayoutsTest {
    private val display = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L,
            endMs = 1_000L,
            text = "",
            p1Name = "Alice",
            p2Name = "Bob",
            p1ColorHex = "#112233",
            p2ColorHex = "#445566",
            p1Pts = 3,
            p2Pts = 1,
            gamesP1 = 4,
            gamesP2 = 2,
            completedSets = listOf(6 to 3),
        ),
    )

    private fun ScoreboardScene.texts() = items.filterIsInstance<SceneItem.Label>().map { it.text }
    private fun ScoreboardScene.label(text: String) = items.filterIsInstance<SceneItem.Label>().first { it.text == text }

    @Test
    fun everyStyleShowsNamesSetsGamesAndPoints() {
        ScoreboardStyleId.entries.forEach { style ->
            val scene = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style))
            val texts = scene.texts()

            assertTrue(texts.containsAll(listOf("ALICE", "BOB", "6", "3", "4", "2", "40", "15")), "$style: $texts")
            assertTrue(scene.width > 0 && scene.height > 0, "$style")
            // Every item is inside the board.
            scene.items.filterIsInstance<SceneItem.Box>().forEach { box ->
                assertTrue(box.x >= -1 && box.x + box.width <= scene.width + 1, "$style: $box")
                assertTrue(box.y >= -1 && box.y + box.height <= scene.height + 1, "$style: $box")
            }
            // The player colors appear on the board.
            val colors = scene.items.filterIsInstance<SceneItem.Box>().map { it.rgb }
            assertTrue(colors.containsAll(listOf(0x112233, 0x445566)), "$style")
        }
    }

    @Test
    fun hiddenPlayerColorsRemoveTheColorMarkersAndMoveTheNamesLeft() {
        ScoreboardStyleId.entries.forEach { style ->
            val withColors = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style))
            val withoutColors = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style, showPlayerColors = false))

            val colors = withoutColors.items.filterIsInstance<SceneItem.Box>().map { it.rgb }
            assertFalse(colors.contains(0x112233) || colors.contains(0x445566), "$style")
            assertTrue(withoutColors.texts().containsAll(listOf("ALICE", "BOB")), "$style")
            assertTrue(withoutColors.width <= withColors.width, "$style")
            assertTrue(withoutColors.label("ALICE").x < withColors.label("ALICE").x, "$style")
        }
    }

    @Test
    fun broadcastUsesTheAccentColorForTheLeadingPointsAndTheTitle() {
        val scene = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(title = "Batumi Raketo League", accentColorHex = "#FF8800"))

        assertEquals(0xFF8800, scene.label("40").rgb)
        assertTrue(scene.label("15").rgb != 0xFF8800)
        assertEquals(0xFF8800, scene.label("BATUMI RAKETO LEAGUE").rgb)
    }

    @Test
    fun hiddenTitleMakesTheBoardShorter() {
        ScoreboardStyleId.entries.forEach { style ->
            val withTitle = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style, title = "League"))
            val withoutTitle = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style, title = "League", showTitle = false))

            assertTrue(withoutTitle.height < withTitle.height, "$style")
            assertFalse(withoutTitle.texts().any { it.equals("League", ignoreCase = true) }, "$style")
        }
    }

    @Test
    fun everyStyleShowsTheAppCreditLineAtTheBottomUntilTheSettingsHideIt() {
        ScoreboardStyleId.entries.forEach { style ->
            val withCredit = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style))
            val withoutCredit = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style, showAppCredit = false))

            val credit = withCredit.label(ScoreboardSettingsV1.APP_CREDIT)
            val lowestOtherLabel = withCredit.items.filterIsInstance<SceneItem.Label>()
                .filter { it.text != ScoreboardSettingsV1.APP_CREDIT }
                .maxOf { it.y }
            assertTrue(credit.y > lowestOtherLabel, "$style: the credit line must be the lowest line")
            assertTrue(credit.y < withCredit.height, "$style")
            assertFalse(withoutCredit.texts().contains(ScoreboardSettingsV1.APP_CREDIT), "$style")
            assertTrue(withoutCredit.height < withCredit.height, "$style")
        }
    }

    @Test
    fun everyStyleShowsTheSetWinnerInBoldAndTheSetLoserInRegularText() {
        ScoreboardStyleId.entries.forEach { style ->
            val scene = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(style = style))
            val winner = scene.label("6")
            val loser = scene.label("3")

            assertTrue(winner.bold, "$style: the set winner must be bold")
            assertFalse(loser.bold, "$style: the set loser must not be bold")
            assertTrue(winner.rgb != loser.rgb || winner.opacity > loser.opacity, "$style: the set loser must be dimmer")
        }
    }

    @Test
    fun aTiedCompletedSetHasNoWinner() {
        val tied = ScoreboardComponent.display(
            OverlaySpan(0L, 1_000L, "", p1Name = "Alice", p2Name = "Bob", gamesP1 = 1, gamesP2 = 2, completedSets = listOf(5 to 5)),
        )
        ScoreboardStyleId.entries.forEach { style ->
            val scene = ScoreboardLayouts.scene(tied, ScoreboardSettingsV1(style = style))
            val setLabels = scene.items.filterIsInstance<SceneItem.Label>().filter { it.text == "5" }

            assertEquals(2, setLabels.size, "$style")
            assertTrue(setLabels.none { it.bold }, "$style")
        }
    }

    @Test
    fun tickerShowsPlayer2OnTheRightWithTheSetsInMirrorOrder() {
        val twoSets = display.copy(completedSets = listOf(6 to 3, 5 to 7))
        val scene = ScoreboardLayouts.scene(twoSets, ScoreboardSettingsV1(style = ScoreboardStyleId.TICKER))

        assertTrue(scene.label("ALICE").x < scene.label("40").x)
        assertTrue(scene.label("40").x < scene.label("15").x)
        assertTrue(scene.label("15").x < scene.label("BOB").x)
        // The first set of player 2 (3) is farther from the middle than the second set (7).
        assertTrue(scene.label("7").x < scene.label("3").x)
        // The first set of player 1 (6) is farther from the middle than the second set (5).
        assertTrue(scene.label("6").x < scene.label("5").x)
    }

    @Test
    fun backgroundOpacityDefaultsToTheStyleValueAndCanBeChanged() {
        val default = ScoreboardLayouts.scene(display, ScoreboardSettingsV1())
        val custom = ScoreboardLayouts.scene(display, ScoreboardSettingsV1(backgroundOpacityPercent = 40))

        val defaultPanel = default.items.first() as SceneItem.Box
        val customPanel = custom.items.first() as SceneItem.Box
        assertEquals(ScoreboardLayouts.defaults(ScoreboardStyleId.BROADCAST).backgroundOpacityPercent / 100.0, defaultPanel.opacity)
        assertEquals(0.4, customPanel.opacity)
    }

    @Test
    fun moreSetsMakeTheBoardWider() {
        val oneSet = ScoreboardLayouts.scene(display, ScoreboardSettingsV1())
        val twoSets = ScoreboardLayouts.scene(display.copy(completedSets = listOf(6 to 3, 4 to 6)), ScoreboardSettingsV1())

        assertTrue(twoSets.width > oneSet.width)
    }

    @Test
    fun everyStyleShowsTheServeBallNextToTheServerUntilTheSettingsHideIt() {
        val serving = display.copy(server = 2)
        ScoreboardStyleId.entries.forEach { style ->
            val defaults = ScoreboardLayouts.defaults(style)
            // Without the title, because some titles have a round accent dot.
            val settings = ScoreboardSettingsV1(style = style, showTitle = false)
            val withServe = ScoreboardLayouts.scene(serving, settings)
            val hidden = ScoreboardLayouts.scene(serving, settings.copy(showServe = false))
            val unknown = ScoreboardLayouts.scene(display, settings)

            val balls = withServe.items.filterIsInstance<SceneItem.Box>().filter { it.isServeBall(defaults.accentRgb) }
            assertEquals(1, balls.size, "$style: $balls")
            val ball = balls.single()
            // The ball is in the row of player 2 and between the name and the points.
            val bob = withServe.label("BOB")
            assertTrue(kotlin.math.abs(ball.y + ball.height / 2 - withServe.label("ALICE").y) >
                kotlin.math.abs(ball.y + ball.height / 2 - bob.y) || style == ScoreboardStyleId.TICKER, "$style")
            assertTrue(ball.x >= 0 && ball.x + ball.width <= withServe.width, "$style")

            assertTrue(hidden.items.filterIsInstance<SceneItem.Box>().none { it.isServeBall(defaults.accentRgb) }, "$style")
            // Without a known server, or with the ball hidden, the board keeps its size.
            assertEquals(unknown.width, hidden.width, "$style")
            assertTrue(withServe.width > unknown.width, "$style")
        }
    }

    @Test
    fun theTickerShowsTheServeBallOnTheSideOfTheServer() {
        val settings = ScoreboardSettingsV1(style = ScoreboardStyleId.TICKER, showTitle = false)
        val accent = ScoreboardLayouts.defaults(ScoreboardStyleId.TICKER).accentRgb
        fun ballX(server: Int): Double {
            val scene = ScoreboardLayouts.scene(display.copy(server = server), settings)
            return scene.items.filterIsInstance<SceneItem.Box>().single { it.isServeBall(accent) }.x
        }
        val scene = ScoreboardLayouts.scene(display.copy(server = 1), settings)

        assertTrue(ballX(1) < scene.label("40").x)
        assertTrue(ballX(2) > scene.label("15").x)
    }

    /** A serve ball is a small circle in the accent color (LED Board: the LED color). */
    private fun SceneItem.Box.isServeBall(accentRgb: Int): Boolean =
        rgb == accentRgb && width == height && width < 20.0 && corners.topLeft == width / 2
}
