package org.litvin.ui.tabs.points

import org.litvin.ui.tabs.points.components.Keybindings
import org.litvin.ui.tabs.points.components.PointsKeyActions
import java.awt.event.ActionEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PointsKeybindingsTest {
    @Test
    fun installsFavoriteHotkeyAction() {
        val panel = JPanel()
        var favoriteToggles = 0
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun endAtPlayhead() {}
            override fun toggleFavoriteSelected() { favoriteToggles++ }
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) {}
        })

        val action = panel.actionMap.get("points.toggleFavorite")
        assertNotNull(action)
        action.actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(1, favoriteToggles)
        bindings.uninstall()
    }

    @Test
    fun shiftArrowActionsNudgeByFiveSeconds() {
        val panel = JPanel()
        val nudges = mutableListOf<Long>()
        val bindings = Keybindings(panel, { false }, object : PointsKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun endAtPlayhead() {}
            override fun toggleFavoriteSelected() {}
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) { nudges += deltaMs }
        })

        panel.actionMap.get("points.seekLeftBig")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))
        panel.actionMap.get("points.seekRightBig")
            .actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(listOf(-5_000L, 5_000L), nudges)
        bindings.uninstall()
    }
}
