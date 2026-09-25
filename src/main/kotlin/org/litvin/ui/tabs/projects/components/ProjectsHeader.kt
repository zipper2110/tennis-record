package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AnimatedAppMark
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectsHeader(
    onImportNewMatch: () -> Unit,
) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        val titleText = Box.createVerticalBox().apply {
            add(headerTitle("TENNIS RECORD"))
            add(Box.createVerticalStrut(2))
            add(headerSubtitle("TENNIS VIDEO EDITING SUITE"))
        }
        val titleBox = Box.createHorizontalBox().apply {
            add(AnimatedAppMark(LOGO_SIZE).apply {
                name = "projects-app-logo"
                alignmentY = Component.CENTER_ALIGNMENT
            })
            add(Box.createHorizontalStrut(12))
            add(titleText.apply { alignmentY = Component.CENTER_ALIGNMENT })
        }
        val actions = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(UiStyles.primaryButton("IMPORT NEW MATCH") { onImportNewMatch() }.apply {
                name = "projects-import-match"
            })
        }
        add(titleBox, BorderLayout.WEST)
        add(actions, BorderLayout.EAST)
    }

    private companion object {
        const val LOGO_SIZE = 44
    }

    private fun headerTitle(text: String): JComponent = WideTextLabel(text).apply {
        font = font.deriveFont(Font.BOLD, 22f)
        foreground = UiStyles.FG_PRIMARY
    }

    private fun headerSubtitle(text: String): JComponent = WideTextLabel(text).apply {
        font = font.deriveFont(font.size2D - 1f)
        foreground = UiStyles.FG_SECONDARY
    }

    /** A label with more width than Swing measures, so that the painted text does not get "...". */
    private class WideTextLabel(text: String) : JLabel(text) {
        override fun getPreferredSize(): Dimension = UiStyles.widenForText(this, super.getPreferredSize(), text)
        override fun getMaximumSize(): Dimension = preferredSize
    }
}
