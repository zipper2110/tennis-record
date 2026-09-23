package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.scoring.NavigationActions
import java.awt.Color
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeftListPanelTest {
    @Test
    fun playerColorsUseSeparateSwatchesAndKeepButtonsReadable() {
        SwingUtilities.invokeAndWait {
            val panel = LeftListPanel(
                actions = object : NavigationActions {
                    override fun navigateToPoint(index: Int) = Unit
                    override fun advanceToNextPoint() = Unit
                    override fun goToPreviousPoint() = Unit
                    override fun toggleFavorite(index: Int) = Unit
                },
                onNamesChanged = { _, _ -> },
                onColorsChanged = { _, _ -> },
            )

            panel.setPlayerColors("#112233", "#F0E0D0")

            val p1Button = panel.findNamed("player-1-color-button", JButton::class.java)
            val p2Button = panel.findNamed("player-2-color-button", JButton::class.java)
            val p1Swatch = panel.findNamed("player-1-color-swatch", JPanel::class.java)
            val p2Swatch = panel.findNamed("player-2-color-swatch", JPanel::class.java)

            assertNotNull(p1Button)
            assertNotNull(p2Button)
            assertNotNull(p1Swatch)
            assertNotNull(p2Swatch)
            assertEquals(Color(0x26, 0x26, 0x26), p1Button.background)
            assertEquals(p1Button.background, p2Button.background)
            assertEquals(Color.WHITE, p1Button.foreground)
            assertEquals(Color(0x11, 0x22, 0x33), p1Swatch.background)
            assertEquals(Color(0xF0, 0xE0, 0xD0), p2Swatch.background)
            assertNotEquals(p1Swatch.background, p1Button.background)
        }
    }

    @Test
    fun previousPointButtonSitsUnderNextPointAndTriggersNavigation() {
        SwingUtilities.invokeAndWait {
            var previousRequests = 0
            val panel = LeftListPanel(
                actions = object : NavigationActions {
                    override fun navigateToPoint(index: Int) = Unit
                    override fun advanceToNextPoint() = Unit
                    override fun goToPreviousPoint() {
                        previousRequests++
                    }
                    override fun toggleFavorite(index: Int) = Unit
                },
                onNamesChanged = { _, _ -> },
                onColorsChanged = { _, _ -> },
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
    fun scoreboardSettingsButtonsAreEnabledAndOpenTheSettings() {
        SwingUtilities.invokeAndWait {
            var requests = 0
            val panel = LeftListPanel(
                actions = object : NavigationActions {
                    override fun navigateToPoint(index: Int) = Unit
                    override fun advanceToNextPoint() = Unit
                    override fun goToPreviousPoint() = Unit
                    override fun toggleFavorite(index: Int) = Unit
                },
                onNamesChanged = { _, _ -> },
                onColorsChanged = { _, _ -> },
                onScoreboardSettings = { requests++ },
            )
            val toolbar = ControlsToolbar(onScoreboardSettings = { requests++ })

            val sideButton = panel.findNamed("scoreboard-settings", JButton::class.java)
            val toolbarButton = toolbar.findNamed("toolbar-scoreboard-settings", JButton::class.java)
            assertNotNull(sideButton)
            assertNotNull(toolbarButton)
            assertTrue(sideButton.isEnabled)
            assertTrue(toolbarButton.isEnabled)

            sideButton.doClick()
            toolbarButton.doClick()
            assertEquals(2, requests)
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
