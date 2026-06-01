package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectsEmptyListCard(message: String) : JPanel(BorderLayout()) {
    init {
        isOpaque = true
        background = UiStyles.CARD_BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12),
        )
        alignmentX = 0f
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        minimumSize = Dimension(200, 48)
        add(JLabel(message).apply { foreground = UiStyles.FG_SECONDARY }, BorderLayout.CENTER)
    }
}
