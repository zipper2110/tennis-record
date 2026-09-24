package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Column layout of the projects table. The header and each [ProjectCard] row use these widths.
 * Thus the cells of all rows align below the column names.
 */
object ProjectsTableColumns {
    /** The names of the figure columns, in display order. The project name column comes before them. */
    val FIGURE_COLUMNS = listOf("Duration", "Size", "Scored", "Favorites")

    private const val FIGURE_COLUMN_WIDTH = 96
    private const val ACTIONS_GAP = 16

    // The row border is a 1 px line and a 12 px empty border. The header uses the same horizontal inset.
    private const val ROW_HORIZONTAL_INSET = 13

    /** Makes the cells for the figure columns of one row, followed by the gap before the row actions. */
    fun figureCells(texts: List<String>, componentNames: List<String?>, color: Color, bold: Boolean = false): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            texts.forEachIndexed { index, text ->
                add(cell(text, color, bold).apply { name = componentNames.getOrNull(index) })
            }
            add(Box.createHorizontalStrut(ACTIONS_GAP))
        }
    }

    /** Makes the header row. [actionsWidth] is the width of the row actions, so that the columns align. */
    fun header(actionsWidth: Int): JComponent {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UiStyles.DARK_BG
            name = "projects-table-header"
            border = BorderFactory.createEmptyBorder(0, ROW_HORIZONTAL_INSET, 6, ROW_HORIZONTAL_INSET)
            add(headerLabel("Project"), BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(figureCells(FIGURE_COLUMNS, emptyList(), UiStyles.FG_SECONDARY, bold = true), BorderLayout.CENTER)
                add(Box.createHorizontalStrut(actionsWidth), BorderLayout.EAST)
            }, BorderLayout.EAST)
        }
    }

    private fun cell(text: String, color: Color, bold: Boolean): JLabel {
        return JLabel(text, SwingConstants.CENTER).apply {
            foreground = color
            if (bold) font = font.deriveFont(Font.BOLD)
            val size = Dimension(FIGURE_COLUMN_WIDTH, preferredSize.height)
            preferredSize = size
            minimumSize = size
            maximumSize = size
        }
    }

    private fun headerLabel(text: String): JLabel = JLabel(text).apply {
        foreground = UiStyles.FG_SECONDARY
        font = font.deriveFont(Font.BOLD)
    }
}
