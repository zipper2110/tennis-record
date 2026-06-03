package org.litvin.ui.tabs.markup

import org.litvin.ui.tabs.markup.components.Keybindings
import org.litvin.ui.tabs.markup.components.MarkupKeyActions
import java.awt.event.ActionEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MarkupKeybindingsTest {
    @Test
    fun installsFavoriteHotkeyAction() {
        val panel = JPanel()
        var favoriteToggles = 0
        val bindings = Keybindings(panel, { false }, object : MarkupKeyActions {
            override fun toggle() {}
            override fun startAtPlayhead() {}
            override fun endAtPlayhead() {}
            override fun toggleFavoriteSelected() { favoriteToggles++ }
            override fun deleteSelected() {}
            override fun nudge(deltaMs: Long) {}
        })

        val action = panel.actionMap.get("markup.toggleFavorite")
        assertNotNull(action)
        action.actionPerformed(ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "test"))

        assertEquals(1, favoriteToggles)
        bindings.uninstall()
    }
}
