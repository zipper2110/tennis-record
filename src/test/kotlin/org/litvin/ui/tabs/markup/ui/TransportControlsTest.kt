package org.litvin.ui.tabs.markup.ui

import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.PointPatch
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransportControlsTest {
    @Test
    fun centersVideoControlsAndPlacesCurrentTimeAtRightEdge() {
        SwingUtilities.invokeAndWait {
            val controls = TransportControls(NoOpActions, onNudge = {})
            controls.setTimeText("00:21:56.300")
            controls.setSize(1600, 64)
            controls.layoutRecursively()

            val videoControls = controls.findByName("markup-video-controls")
            val timePanel = controls.findByName("markup-current-time")
            assertNotNull(videoControls)
            assertNotNull(timePanel)

            val controlsCenter = SwingUtilities.convertPoint(
                videoControls,
                videoControls.width / 2,
                0,
                controls,
            ).x
            assertTrue(
                abs(controlsCenter - controls.width / 2) <= 1,
                "Video controls center=$controlsCenter, panel center=${controls.width / 2}, " +
                    "bounds=${videoControls.bounds}",
            )

            val timeRight = SwingUtilities.convertPoint(
                timePanel,
                timePanel.width,
                0,
                controls,
            ).x
            assertEquals(controls.width - 12, timeRight)
            assertTrue(
                timePanel.descendants().filterIsInstance<JLabel>().any { it.text == "Current time:" },
            )
            assertTrue(
                timePanel.descendants().filterIsInstance<JLabel>().any { it.text == "00:21:56.300" },
            )
        }
    }

    private fun Component.layoutRecursively() {
        if (this is Container) {
            doLayout()
            components.forEach { it.layoutRecursively() }
        }
    }

    private fun Component.findByName(target: String): Component? {
        if (name == target) return this
        if (this !is Container) return null
        return components.firstNotNullOfOrNull { it.findByName(target) }
    }

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }

    private object NoOpActions : MarkupActions {
        override fun togglePlayPause() = Unit
        override fun seekTo(ms: Long) = Unit
        override fun jumpToSelected() = Unit
        override fun setStartAtPlayhead() = Unit
        override fun setEndAtPlayhead() = Unit
        override fun createPointAt(ms: Long) = Unit
        override fun editPoint(id: String, patch: PointPatch) = Unit
        override fun deletePoint(id: String) = Unit
        override fun toggleFavorite(id: String) = Unit
        override fun selectByVisualIndex(index: Int) = Unit
        override fun saveNow() = Unit
    }
}
