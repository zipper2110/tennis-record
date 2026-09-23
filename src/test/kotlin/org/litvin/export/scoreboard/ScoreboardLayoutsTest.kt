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
}
