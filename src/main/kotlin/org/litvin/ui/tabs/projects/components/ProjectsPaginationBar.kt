package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectsPaginationBar(
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
) : JPanel() {
    private val prevButton = JButton("Prev")
    private val nextButton = JButton("Next")
    private val pageLabel = JLabel("Page 1 / 1")

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
        UiStyles.styleSecondary(prevButton)
        UiStyles.styleSecondary(nextButton)
        pageLabel.foreground = UiStyles.FG_SECONDARY
        prevButton.addActionListener { onPrevious() }
        nextButton.addActionListener { onNext() }
        add(prevButton)
        add(Box.createHorizontalStrut(8))
        add(pageLabel)
        add(Box.createHorizontalStrut(8))
        add(nextButton)
        add(Box.createHorizontalGlue())
        minimumSize = Dimension(200, preferredSize.height)
    }

    fun render(currentPage: Int, totalPages: Int, canGoPrevious: Boolean, canGoNext: Boolean, isVisible: Boolean) {
        pageLabel.text = "Page $currentPage / $totalPages"
        prevButton.isEnabled = canGoPrevious
        nextButton.isEnabled = canGoNext
        this.isVisible = isVisible
        revalidate()
        repaint()
    }
}
