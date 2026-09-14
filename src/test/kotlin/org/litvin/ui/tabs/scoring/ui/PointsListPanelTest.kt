package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.markup.PointV1
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
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

    private fun <T : Component> Container.findNamed(name: String, type: Class<T>): T? {
        for (component in components) {
            if (component.name == name && type.isInstance(component)) return type.cast(component)
            if (component is Container) component.findNamed(name, type)?.let { return it }
        }
        return null
    }
}
