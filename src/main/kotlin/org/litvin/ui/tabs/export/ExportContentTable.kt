package org.litvin.ui.tabs.export

import org.litvin.export.ExportPointSummary
import org.litvin.export.RenderFormatting
import org.litvin.ui.UiStyles
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingConstants

/**
 * The content choice as a table. Each row is one radio button with the length of the exported video
 * and the number of points in it. The panel owns the radio buttons and their ButtonGroup.
 */
class ExportContentTable(
    private val fullVideo: JRadioButton,
    private val points: JRadioButton,
    private val favorites: JRadioButton,
) : JPanel(GridBagLayout()) {
    private class Row(val radio: JRadioButton, val length: JLabel, val count: JLabel)

    // Declared before the rows, because the property initializers run in order.
    private val countColors = HashMap<Row, Color?>()

    private val fullRow = row(0, fullVideo, "export-content-full")
    private val pointsRow = row(1, points, "export-content-points")
    private val favoritesRow = row(2, favorites, "export-content-favorites")

    init {
        name = "export-content-table"
        isOpaque = false
        alignmentX = 0f
        header("Length", 1)
        header("Points", 2)
        show(null, null)
    }

    /**
     * Shows the values. The lengths are for the valid points only, because the export skips
     * empty and overlapping points. A null value shows a dash. The full video has no point count.
     */
    fun show(summary: ExportPointSummary?, sourceDurationMs: Long?) {
        fullRow.length.text = sourceDurationMs?.let(RenderFormatting::formatDurationWords) ?: DASH
        fullRow.count.text = ""
        if (summary == null) {
            listOf(pointsRow, favoritesRow).forEach {
                it.length.text = DASH
                it.count.text = DASH
            }
            countColors[pointsRow] = null
            countColors[favoritesRow] = null
        } else {
            pointsRow.length.text = RenderFormatting.formatDurationWords(summary.validTotalMs)
            pointsRow.count.text = summary.validPointCount.toString()
            countColors[pointsRow] = UiStyles.YELLOW.takeIf { summary.validPointCount == 0 }
            favoritesRow.length.text = RenderFormatting.formatDurationWords(summary.favoriteTotalMs)
            favoritesRow.count.text = summary.favoriteCount.toString()
            countColors[favoritesRow] = UiStyles.YELLOW.takeIf { summary.favoriteCount == 0 }
        }
        updateColors()
    }

    /** Call after a radio button changes its enabled state. */
    fun updateColors() {
        listOf(fullRow, pointsRow, favoritesRow).forEach { row ->
            val enabled = row.radio.isEnabled
            row.length.foreground = if (enabled) UiStyles.FG_PRIMARY else UiStyles.FG_DISABLED
            row.count.foreground = if (enabled) countColors[row] ?: UiStyles.FG_PRIMARY else UiStyles.FG_DISABLED
        }
    }

    private fun header(text: String, column: Int) {
        val label = JLabel(text, SwingConstants.RIGHT)
        UiStyles.styleHelper(label)
        add(label, constraints(column, 0))
    }

    private fun row(index: Int, radio: JRadioButton, componentName: String): Row {
        val gridY = index + 1
        val length = value("$componentName-length")
        val count = value("$componentName-count")
        add(radio, constraints(0, gridY).apply { anchor = GridBagConstraints.WEST; weightx = 1.0 })
        add(length, constraints(1, gridY))
        add(count, constraints(2, gridY))
        radio.addPropertyChangeListener("enabled") { updateColors() }
        return Row(radio, length, count)
    }

    private fun value(componentName: String) = JLabel(DASH, SwingConstants.RIGHT).apply {
        name = componentName
        font = font.deriveFont(Font.BOLD)
    }

    private fun constraints(column: Int, rowIndex: Int) = GridBagConstraints().apply {
        gridx = column
        gridy = rowIndex
        anchor = GridBagConstraints.EAST
        fill = GridBagConstraints.NONE
        insets = Insets(1, if (column == 0) 0 else 16, 1, 0)
    }

    private companion object {
        const val DASH = "—"
    }
}
