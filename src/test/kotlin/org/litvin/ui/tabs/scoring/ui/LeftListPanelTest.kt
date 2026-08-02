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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class LeftListPanelTest {
    @Test
    fun playerColorsUseSeparateSwatchesAndKeepButtonsReadable() {
        SwingUtilities.invokeAndWait {
            val panel = LeftListPanel(
                actions = object : NavigationActions {
                    override fun navigateToPoint(index: Int) = Unit
                    override fun advanceToNextPoint() = Unit
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
