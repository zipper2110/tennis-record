package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.scoring.NavigationActions
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeftListPanelTest {
    private open class NoNavigation : NavigationActions {
        override fun navigateToPoint(index: Int) = Unit
        override fun advanceToNextPoint() = Unit
        override fun goToPreviousPoint() = Unit
        override fun toggleFavorite(index: Int) = Unit
    }

    @Test
    fun previousPointButtonSitsUnderNextPointAndTriggersNavigation() {
        SwingUtilities.invokeAndWait {
            var previousRequests = 0
            val panel = LeftListPanel(
                actions = object : NoNavigation() {
                    override fun goToPreviousPoint() {
                        previousRequests++
                    }
                },
            )

            val next = panel.findNamed("next-point", JButton::class.java)
            val previous = panel.findNamed("previous-point", JButton::class.java)
            assertNotNull(next)
            assertNotNull(previous)
            assertEquals("Previous Point  [Shift+R]", previous.text)

            val footer = previous.parent
            assertEquals(next.parent, footer)
            assertTrue(footer.components.indexOf(previous) > footer.components.indexOf(next))

            assertFalse(previous.isEnabled)
            panel.setPreviousEnabled(true)
            assertTrue(previous.isEnabled)

            previous.doClick()
            assertEquals(1, previousRequests)
        }
    }

    @Test
    fun pointCounterSitsAboveNextPointAndTogglesTheCurrentFavorite() {
        SwingUtilities.invokeAndWait {
            val favoriteRequests = mutableListOf<Int>()
            val panel = LeftListPanel(
                actions = object : NoNavigation() {
                    override fun toggleFavorite(index: Int) {
                        favoriteRequests += index
                    }
                },
            )
            val label = panel.findNamed("current-point-label", JLabel::class.java)
            val favorite = panel.findNamed("current-point-favorite", JButton::class.java)
            val next = panel.findNamed("next-point", JButton::class.java)
            assertNotNull(label)
            assertNotNull(favorite)
            assertNotNull(next)

            assertEquals("No point selected", label.text)
            assertFalse(favorite.isEnabled)

            panel.setCurrentPoint(2, 8, favorite = true)
            assertEquals("Point 3 / 8", label.text)
            assertTrue(favorite.isEnabled)
            favorite.doClick()
            assertEquals(listOf(2), favoriteRequests)

            // The counter row is the first item of the footer, above the Next Point button
            val footer = next.parent
            val counterRow = footer.components.first { it is Container && label in it.components }
            assertTrue(footer.components.indexOf(counterRow) < footer.components.indexOf(next))
        }
    }

    @Test
    fun scoreSettingsSitsAboveScoreboardStyleAndBothOpenTheirDialogs() {
        SwingUtilities.invokeAndWait {
            val requests = mutableListOf<String>()
            val panel = LeftListPanel(
                actions = NoNavigation(),
                onScoreSettings = { requests += "score" },
                onScoreboardStyle = { requests += "style" },
            )

            val scoreSettings = panel.findNamed("score-settings", JButton::class.java)
            val scoreboardStyle = panel.findNamed("scoreboard-style", JButton::class.java)
            assertNotNull(scoreSettings)
            assertNotNull(scoreboardStyle)
            assertEquals("Scoring Settings", scoreSettings.text)
            assertEquals("Scoreboard Style", scoreboardStyle.text)
            val footer = scoreSettings.parent
            assertTrue(footer.components.indexOf(scoreSettings) < footer.components.indexOf(scoreboardStyle))

            scoreSettings.doClick()
            scoreboardStyle.doClick()
            assertEquals(listOf("score", "style"), requests)
        }
    }

    @Test
    fun manualMarkerAndPlayerFieldsAreGone() {
        SwingUtilities.invokeAndWait {
            val panel = LeftListPanel(actions = NoNavigation())

            assertNull(panel.findNamed("manual-marker", JButton::class.java))
            assertNull(panel.findNamed("scoring-player-1-name", Component::class.java))
            assertNull(panel.findNamed("player-1-color-button", Component::class.java))
        }
    }

    private fun <T : Component> Container.findNamed(name: String, type: Class<T>): T? {
        for (component in components) {
            if (component.name == name && type.isInstance(component)) return type.cast(component)
            if (component is Container) {
                component.findNamed(name, type)?.let { return it }
            }
        }
        return null
    }
}
