package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectsHeader(
    onImportNewMatch: () -> Unit,
    onHelp: () -> Unit,
) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        val titleBox = Box.createVerticalBox().apply {
            add(headerTitle("TENNIS RECORD"))
            add(Box.createVerticalStrut(2))
            add(headerSubtitle("TENNIS VIDEO ANALYTICS & EDITING SUITE"))
        }
        val actions = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("Help [F1]").apply {
                name = "projects-help"
                toolTipText = "F1 - Help"
                UiStyles.styleSecondary(this)
                addActionListener { onHelp() }
            })
            add(Box.createHorizontalStrut(8))
            add(UiStyles.primaryButton("IMPORT NEW MATCH") { onImportNewMatch() })
        }
        add(titleBox, BorderLayout.WEST)
        add(actions, BorderLayout.EAST)
    }

    private fun headerTitle(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD, 22f)
        foreground = UiStyles.FG_PRIMARY
    }

    private fun headerSubtitle(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(font.size2D - 1f)
        foreground = UiStyles.FG_SECONDARY
    }
}
