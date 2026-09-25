package org.litvin.ui.tabs.export

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable

/**
 * A vertical list of export cards in a scroll pane. The column follows the width of the scroll pane,
 * so only a vertical scroll bar can show and long text in a card wraps.
 */
class ExportCardColumn {
    private val column = object : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UiStyles.CARD_BG
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
    }

    val scrollPane = JScrollPane(column).apply {
        border = BorderFactory.createEmptyBorder()
        background = UiStyles.CARD_BG
        viewport.background = UiStyles.CARD_BG
        viewport.isOpaque = true
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
        applyDarkScrollbar(this, UiStyles.CARD_BG)
    }

    /** Replaces the cards. An empty list shows [placeholder]. */
    fun show(cards: List<JComponent>, placeholder: JComponent) {
        column.removeAll()
        if (cards.isEmpty()) {
            placeholder.alignmentX = 0f
            column.add(placeholder)
        }
        cards.forEachIndexed { index, card ->
            if (index > 0) column.add(Box.createRigidArea(Dimension(0, 8)))
            card.alignmentX = 0f
            card.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            column.add(card)
        }
        column.revalidate()
        column.repaint()
    }

    /** Height of the cards. The queue uses it to limit its scroll pane. */
    fun contentHeight(): Int = column.preferredSize.height
}
