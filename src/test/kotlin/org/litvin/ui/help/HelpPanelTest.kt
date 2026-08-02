package org.litvin.ui.help

import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class HelpPanelTest {
    @Test
    fun selectingPagesUpdatesContentSelectionAndNavigationOrder() {
        System.setProperty("java.awt.headless", "true")

        SwingUtilities.invokeAndWait {
            val panel = HelpPanel()
            assertEquals(HelpPage.entries.map { it.title }, panel.pageTitles())
            assertEquals(HelpPage.OVERVIEW, panel.selectedPage)

            panel.selectPage(HelpPage.EXPORT)
            assertEquals(HelpPage.EXPORT, panel.selectedPage)

            panel.selectPage(HelpPage.CROP)
            assertEquals(HelpPage.CROP, panel.selectedPage)
        }
    }
}
