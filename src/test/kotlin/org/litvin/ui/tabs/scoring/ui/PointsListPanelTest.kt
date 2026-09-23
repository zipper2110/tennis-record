package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.ui.UiStyles
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PointsListPanelTest {
    @Test
    fun favoriteControlTogglesPointWithoutTakingKeyboardFocusFromVideoPlayback() {
        SwingUtilities.invokeAndWait {
            val toggledPointIndexes = mutableListOf<Int>()
            val panel = PointsListPanel().apply {
                onToggleFavorite = { toggledPointIndexes += it }
                setData(
                    points = listOf(PointV1(id = "point-1", startMs = 0, endMs = 1_000)),
                    outcomesByPointId = emptyMap(),
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }

            val favoriteControl = panel.findNamed("favorite-point", JButton::class.java)
            assertNotNull(favoriteControl)

            favoriteControl.doClick()

            assertEquals(listOf(0), toggledPointIndexes)
            assertFalse(
                favoriteControl.isFocusable,
                "Favorite controls must not take keyboard focus away from the scoring video; Space should keep toggling playback.",
            )
        }
    }

    @Test
    fun rowsMarkThePointsThatWinAGameOrASet() {
        SwingUtilities.invokeAndWait {
            // 24 straight points for P1: a game every 4th point, and the set on the 24th.
            val points = (1..24).map { n ->
                PointV1(id = "point-$n", startMs = n * 1_000, endMs = n * 1_000 + 500)
            }
            val panel = PointsListPanel().apply {
                setData(
                    points = points,
                    outcomesByPointId = points.associate { it.id to Outcome.P1 },
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }

            val gameBadges = panel.collectNamed("game-won", JLabel::class.java)
            val setBadges = panel.collectNamed("set-won", JLabel::class.java)

            assertEquals(
                5,
                gameBadges.size,
                "One marker per game won, minus the 6th game, which is shown as the set instead.",
            )
            assertEquals(1, setBadges.size, "Only the point that clinched the set is marked.")
            assertEquals("Game won by Player 1", gameBadges.first().toolTipText)
            assertEquals("Set won by Player 1", setBadges.first().toolTipText)
        }
    }

    @Test
    fun scoredStatusTooltipDiffersForScoredAndUnscoredPoints() {
        SwingUtilities.invokeAndWait {
            val selected = mutableListOf<Int>()
            val points = (1..2).map { n ->
                PointV1(id = "point-$n", startMs = n * 1_000, endMs = n * 1_000 + 500)
            }
            val panel = PointsListPanel().apply {
                onSelect = { index, _ -> selected += index }
                setData(
                    points = points,
                    outcomesByPointId = mapOf("point-1" to Outcome.P1),
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }

            val scored = assertNotNull(panel.findNamed("point-scored", JLabel::class.java))
            val unscored = assertNotNull(panel.findNamed("point-unscored", JLabel::class.java))
            assertEquals("The point is scored", scored.toolTipText)
            assertEquals("Not scored. Choose who won this point", unscored.toolTipText)

            // A tooltip makes the label consume clicks; the label must still select its row.
            unscored.mouseListeners.forEach {
                it.mouseClicked(java.awt.event.MouseEvent(unscored, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false))
            }
            assertEquals(listOf(1), selected)
        }
    }

    @Test
    fun unscoredPointsCarryNoGameOrSetMarkers() {
        SwingUtilities.invokeAndWait {
            val points = (1..8).map { n ->
                PointV1(id = "point-$n", startMs = n * 1_000, endMs = n * 1_000 + 500)
            }
            val panel = PointsListPanel().apply {
                setData(
                    points = points,
                    outcomesByPointId = emptyMap(),
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }

            assertEquals(0, panel.collectNamed("game-won", JLabel::class.java).size)
            assertEquals(0, panel.collectNamed("set-won", JLabel::class.java).size)
        }
    }

    @Test
    fun milestoneMarkersArePaintedInTheWinningPlayerColor() {
        SwingUtilities.invokeAndWait {
            // P2 takes the first game, P1 the second.
            val points = (1..8).map { n ->
                PointV1(id = "point-$n", startMs = n * 1_000, endMs = n * 1_000 + 500)
            }
            val outcomes = points.mapIndexed { i, p ->
                p.id to (if (i < 4) Outcome.P2 else Outcome.P1)
            }.toMap()
            val panel = PointsListPanel().apply {
                setData(
                    points = points,
                    outcomesByPointId = outcomes,
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }

            val gameBadges = panel.collectNamed("game-won", JLabel::class.java)
            assertEquals(2, gameBadges.size)
            assertEquals(Color(0xFF, 0x6B, 0x6B), gameBadges[0].badgeFillColor())
            assertEquals(Color(0x4D, 0xA3, 0xFF), gameBadges[1].badgeFillColor())
        }
    }

    @Test
    fun milestoneTooltipsNameTheWinnerAndFollowRenames() {
        SwingUtilities.invokeAndWait {
            // P2 takes the first game, P1 the second.
            val points = (1..8).map { n ->
                PointV1(id = "point-$n", startMs = n * 1_000, endMs = n * 1_000 + 500)
            }
            val panel = PointsListPanel().apply {
                setData(
                    points = points,
                    outcomesByPointId = points.mapIndexed { i, p ->
                        p.id to (if (i < 4) Outcome.P2 else Outcome.P1)
                    }.toMap(),
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }

            val badges = panel.collectNamed("game-won", JLabel::class.java)
            assertEquals("Game won by Player 2", badges[0].toolTipText)
            assertEquals("Game won by Player 1", badges[1].toolTipText)

            panel.setPlayerNames("Alice", "Bob")

            assertEquals(
                "Game won by Bob",
                badges[0].toolTipText,
                "A rename must reach markers that are already on screen.",
            )
            assertEquals("Game won by Alice", badges[1].toolTipText)
        }
    }

    @Test
    fun setMarkerIsOutlinedWhileGameMarkerIsSolid() {
        SwingUtilities.invokeAndWait {
            val points = (1..24).map { n ->
                PointV1(id = "point-$n", startMs = n * 1_000, endMs = n * 1_000 + 500)
            }
            val panel = PointsListPanel().apply {
                setData(
                    points = points,
                    outcomesByPointId = points.associate { it.id to Outcome.P1 },
                    p1ColorHex = "#4DA3FF",
                    p2ColorHex = "#FF6B6B",
                )
            }
            val p1 = Color(0x4D, 0xA3, 0xFF)

            val game = panel.collectNamed("game-won", JLabel::class.java).first().paintBadge()
            val set = panel.collectNamed("set-won", JLabel::class.java).first().paintBadge()
            val midHeight = game.height / 2

            // The game marker is a solid block of the player color, edge to interior.
            assertEquals(p1, Color(game.getRGB(0, midHeight)))
            assertEquals(p1, Color(game.getRGB(3, midHeight)))

            // The set marker keeps the player color only on its border, over the contrasting fill.
            assertEquals(p1, Color(set.getRGB(0, midHeight)))
            assertEquals(UiStyles.contrastingTextColor(p1), Color(set.getRGB(3, midHeight)))
        }
    }

    /** Paints the badge icon on its own so individual pixels can be sampled. */
    private fun JLabel.paintBadge(): BufferedImage {
        val badge = requireNotNull(icon)
        val image = BufferedImage(badge.iconWidth, badge.iconHeight, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            badge.paintIcon(this, g, 0, 0)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun JLabel.badgeFillColor(): Color {
        val image = paintBadge()
        return Color(image.getRGB(2, image.height / 2))
    }

    private fun <T : Component> Container.collectNamed(name: String, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        for (component in components) {
            if (component.name == name && type.isInstance(component)) found += type.cast(component)
            if (component is Container) found += component.collectNamed(name, type)
        }
        return found
    }

    private fun <T : Component> Container.findNamed(name: String, type: Class<T>): T? {
        for (component in components) {
            if (component.name == name && type.isInstance(component)) return type.cast(component)
            if (component is Container) component.findNamed(name, type)?.let { return it }
        }
        return null
    }
}
