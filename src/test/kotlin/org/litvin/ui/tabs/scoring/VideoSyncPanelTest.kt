package org.litvin.ui.tabs.scoring

import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.scoring.ui.VideoSyncPanel
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VideoSyncPanelTest {
    @Test
    fun shiftArrowButtonsSeekByFiveSeconds() {
        SwingUtilities.invokeAndWait {
            val seeks = mutableListOf<Long>()
            val panel = VideoSyncPanel(object : VideoPlayerActions {
                override fun playPause() {}
                override fun seekBy(milliseconds: Long) { seeks += milliseconds }
                override fun setSpeedMultiplier(multiplier: Float) {}
                override fun setFrameStepEnabled(enabled: Boolean) {}
            }) {}

            val back = panel.descendants()
                .filterIsInstance<JButton>()
                .firstOrNull { it.text.startsWith("-5s") }
            val forward = panel.descendants()
                .filterIsInstance<JButton>()
                .firstOrNull { it.text.startsWith("+5s") }
            assertNotNull(back)
            assertNotNull(forward)

            back.doClick()
            forward.doClick()

            assertEquals(listOf(-5_000L, 5_000L), seeks)
        }
    }

    @Test
    fun speedSelectorIncludesOnePointFiveMultiplier() {
        SwingUtilities.invokeAndWait {
            val selectedSpeeds = mutableListOf<Float>()
            val panel = VideoSyncPanel(object : VideoPlayerActions {
                override fun playPause() {}
                override fun seekBy(milliseconds: Long) {}
                override fun setSpeedMultiplier(multiplier: Float) { selectedSpeeds += multiplier }
                override fun setFrameStepEnabled(enabled: Boolean) {}
            }) {}

            val speedCombo = panel.descendants()
                .filterIsInstance<JComboBox<*>>()
                .firstOrNull { it.name == "speed-dropdown" }
            assertNotNull(speedCombo)
            assertEquals(
                listOf("2x", "1.5x", "1.25x", "1x", "0.5x"),
                (0 until speedCombo.itemCount).map { speedCombo.getItemAt(it) },
            )

            speedCombo.selectedIndex = 1

            assertEquals(1.5f, selectedSpeeds.last())
        }
    }

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }
}
