package org.litvin.ui.tabs.markup.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.MarkupViewState
import org.litvin.ui.tabs.markup.PointDto
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * PointsCardsView — vertical list of point "cards" for the Markup tab.
 *
 * Responsibilities:
 * - Render a pending card when state.pendingDraftStartMs is set
 * - Render a card per completed point from state.points
 * - Reflect selection visuals based on state.selectedVisualIndex
 * - Emit click-to-select (and optional seek via actions) on user interactions
 * - Provide scrollToVisualIndex(index) API for the container
 *
 * Notes:
 * - Depends only on Markup contracts and UI commons.
 */
class PointsCardsView(
    private val actions: MarkupActions,
) : JPanel(BorderLayout()) {

    private val listPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, CARD_H_MARGIN, 0, CARD_H_MARGIN)
    }
    private val scroll = JScrollPane(listPanel).apply {
        border = BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 16
        verticalScrollBar.blockIncrement = 120
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        isOpaque = false
        viewport.isOpaque = false
    }

    private val cardComponents: MutableList<JComponent> = mutableListOf()
    private var lastState: MarkupViewState? = null

    init {
        isOpaque = true
        background = UiStyles.DARK_BG
        add(scroll, BorderLayout.CENTER)
    }

    fun setState(state: MarkupViewState) {
        lastState = state
        rebuild(state)
    }

    fun updateSelection(selectedIndex: Int) {
        try {
            val prev = lastState?.selectedVisualIndex
            lastState = lastState?.copy(selectedVisualIndex = selectedIndex) ?: lastState
            if (prev != null && prev >= 0 && prev < cardComponents.size) {
                applyCardSelectionStyle(cardComponents[prev], false)
                cardComponents[prev].repaint()
            }
            if (selectedIndex >= 0 && selectedIndex < cardComponents.size) {
                applyCardSelectionStyle(cardComponents[selectedIndex], true)
                cardComponents[selectedIndex].repaint()
            }
        } catch (_: Throwable) { }
    }

    fun scrollToVisualIndex(index: Int) {
        try {
            if (index < 0 || index >= cardComponents.size) return
            val r = cardComponents[index].bounds
            listPanel.scrollRectToVisible(r)
        } catch (_: Throwable) { }
    }

    private fun rebuild(state: MarkupViewState) {
        try {
            listPanel.removeAll()
            cardComponents.clear()
            var visualIndex = 0
            // Pending card at top
            state.pendingDraftStartMs?.let { s ->
                val card = buildPendingCard(visualIndex, s)
                cardComponents.add(card)
                listPanel.add(card)
                listPanel.add(Box.createVerticalStrut(10))
                visualIndex++
            }
            // Completed points
            state.points.forEachIndexed { dataIndex, p ->
                val card = buildPointCard(visualIndex, dataIndex, p)
                cardComponents.add(card)
                listPanel.add(card)
                listPanel.add(Box.createVerticalStrut(10))
                visualIndex++
            }
            listPanel.revalidate()
            listPanel.repaint()
        } catch (_: Throwable) { }
    }

    private fun buildPendingCard(visualIndex: Int, startMs: Long): JComponent {
        val card = JPanel(BorderLayout())
        card.isOpaque = true
        card.background = UiStyles.SURFACE_HIGH
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        )
        card.alignmentX = LEFT_ALIGNMENT
        // Fixed card size
        run {
            val sz = Dimension(CARD_WIDTH, CARD_HEIGHT)
            card.minimumSize = sz
            card.preferredSize = sz
            card.maximumSize = sz
        }
        val content = JPanel()
        content.isOpaque = false
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.add(JLabel("Pending…").apply { foreground = UiStyles.FG_SECONDARY; font = font.deriveFont(Font.BOLD) })
        content.add(Box.createVerticalStrut(4))
        content.add(JLabel(Timecode.format(startMs)).apply { foreground = UiStyles.FG_PRIMARY })
        card.add(content, BorderLayout.CENTER)
        // Highlight stripe
        card.add(object: JComponent(){
            override fun getPreferredSize() = Dimension(4, 1)
            override fun paintComponent(g: Graphics) { g.color = UiStyles.LIME; g.fillRect(0,0,width,height) }
        }, BorderLayout.WEST)
        // Tooltip and selection
        card.toolTipText = "Pending point — press V to set End"
        applyCardSelectionStyle(card, visualIndex == (lastState?.selectedVisualIndex ?: -1))
        return card
    }

    private fun buildPointCard(visualIndex: Int, dataIndex: Int, p: PointDto): JComponent {
        val card = JPanel(BorderLayout())
        card.isOpaque = true
        card.background = UiStyles.CARD_BG
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        )
        card.alignmentX = LEFT_ALIGNMENT
        // Fixed card size
        run {
            val sz = Dimension(CARD_WIDTH, CARD_HEIGHT)
            card.minimumSize = sz
            card.preferredSize = sz
            card.maximumSize = sz
        }
        // Left stripe for active
        val stripe = object: JComponent(){
            override fun getPreferredSize() = Dimension(4, 1)
            override fun paintComponent(g: Graphics) {
                g.color = if (visualIndex == (lastState?.selectedVisualIndex ?: -1)) UiStyles.LIME else UiStyles.CARD_BORDER
                g.fillRect(0,0,width,height)
            }
        }
        card.add(stripe, BorderLayout.WEST)
        val center = JPanel()
        center.isOpaque = false
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)
        val title = JLabel("Point ${dataIndex + 1}").apply { foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(
            Font.BOLD) }
        val times = JPanel(FlowLayout(FlowLayout.LEFT, 14, 0)).apply {
            isOpaque = false
            add(JLabel(Timecode.format(p.startMs)).apply { foreground = UiStyles.FG_SECONDARY })
            add(JLabel(p.endMs?.let { Timecode.format(it) } ?: "—").apply { foreground = UiStyles.FG_SECONDARY })
        }
        center.add(title)
        center.add(Box.createVerticalStrut(6))
        center.add(times)
        card.add(center, BorderLayout.CENTER)
        // Right-side actions (Go, Edit, Delete)
        run {
            val actionsPanel = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
            }
            val goBtn = UiStyles.smallIconButton(UiStyles.targetIcon(), "Go to marked point") {
                actions.seekTo(p.startMs)
                actions.selectByVisualIndex(visualIndex)
            }
            val editBtn = UiStyles.smallIconButton(UiStyles.pencilIcon(), "Edit times/label") {
                try { EditPointDialog.show(this@PointsCardsView, p, actions) } catch (_: Throwable) { }
            }
            val delBtn = UiStyles.smallIconButton(UiStyles.crossIcon(), "Delete point") {
                actions.selectByVisualIndex(visualIndex)
                try { actions.deletePoint(p.id) } catch (_: Throwable) { }
            }
            actionsPanel.add(goBtn)
            actionsPanel.add(Box.createHorizontalStrut(8))
            actionsPanel.add(editBtn)
            actionsPanel.add(Box.createHorizontalStrut(8))
            actionsPanel.add(delBtn)
            card.add(actionsPanel, BorderLayout.EAST)
        }
        // Click selects (and seeks to start)
        card.addMouseListener(object: MouseAdapter(){
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    actions.selectByVisualIndex(visualIndex)
                    actions.seekTo(p.startMs)
                }
            }
        })
        applyCardSelectionStyle(card, visualIndex == (lastState?.selectedVisualIndex ?: -1))
        return card
    }

    private fun applyCardSelectionStyle(card: JComponent, selected: Boolean) {
        card.background = if (selected) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
        card.repaint()
    }

    companion object {
        private const val RIGHT_PANEL_WIDTH = 420
        private const val CARD_H_MARGIN = 10
        private const val CARD_WIDTH = RIGHT_PANEL_WIDTH - CARD_H_MARGIN * 2
        private const val CARD_HEIGHT = 68
    }
}