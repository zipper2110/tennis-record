package org.litvin.ui.tabs.markup.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.commons.scrollIntoView
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.MarkupViewState
import org.litvin.ui.tabs.markup.PointDto
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

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
    // Fixed pending area at the bottom (always visible)
    private val pendingContainer = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(10, CARD_H_MARGIN, 10, CARD_H_MARGIN)
    }

    private val cardComponents: MutableList<JComponent> = mutableListOf()
    private var lastState: MarkupViewState? = null

    init {
        isOpaque = true
        background = UiStyles.DARK_BG
        add(scroll, BorderLayout.CENTER)
        add(pendingContainer, BorderLayout.SOUTH)
        try {
            applyDarkScrollbar(scroll, background)
        } catch (_: Throwable) {
        }
    }

    fun setState(state: MarkupViewState) {
        val prev = lastState
        lastState = state

        // Determine if new completed point(s) were added compared to previous state
        var targetIndexToScroll: Int? = null
        if (prev != null) {
            try {
                val prevIds = prev.points.map { it.id }.toSet()
                val newIds = state.points.map { it.id }.toSet()
                val addedIds = newIds - prevIds
                val grew = state.points.size > prev.points.size
                if (grew && addedIds.isNotEmpty()) {
                    val lastAddedDataIndex = state.points.indexOfLast { it.id in addedIds }
                    if (lastAddedDataIndex >= 0) {
                        // Visual index for completed points equals their data index
                        targetIndexToScroll = lastAddedDataIndex
                    }
                }
            } catch (_: Throwable) {
            }
        }

        rebuild(state)

        // Defer scrolling until after layout is realized
        targetIndexToScroll?.let { idx ->
            try {
                SwingUtilities.invokeLater { scrollToVisualIndex(idx) }
            } catch (_: Throwable) {
            }
        }
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
        } catch (_: Throwable) {
        }
    }

    fun scrollToVisualIndex(index: Int) {
        if (index < 0 || index >= cardComponents.size) return
        // If the index points to the pending card fixed at bottom — it's always visible, no need to scroll
        val isPendingAtBottom = lastState?.pendingDraftStartMs != null && index == cardComponents.lastIndex
        if (isPendingAtBottom) return
        cardComponents[index].scrollIntoView()
    }

    private fun rebuild(state: MarkupViewState) {
        // Clear containers
        listPanel.removeAll()
        pendingContainer.removeAll()
        cardComponents.clear()
        var visualIndex = 0

        // Completed points first (scrollable list)
        state.points.forEachIndexed { dataIndex, p ->
            val card = buildPointCard(visualIndex, dataIndex, p)
            cardComponents.add(card)
            listPanel.add(card)
            listPanel.add(Box.createVerticalStrut(10))
            visualIndex++
        }

        // Pending card fixed at the bottom (outside of scroll), visually last
        state.pendingDraftStartMs?.let { s ->
            val pendingCard = buildPendingCard(visualIndex, s)
            cardComponents.add(pendingCard)
            pendingContainer.add(pendingCard, BorderLayout.CENTER)
            pendingContainer.isVisible = true
        } ?: run {
            pendingContainer.isVisible = false
        }

        listPanel.revalidate()
        listPanel.repaint()
        pendingContainer.revalidate()
        pendingContainer.repaint()
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
        card.add(object : JComponent() {
            override fun getPreferredSize() = Dimension(4, 1)
            override fun paintComponent(g: Graphics) {
                g.color = UiStyles.LIME; g.fillRect(0, 0, width, height)
            }
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
        val stripe = object : JComponent() {
            override fun getPreferredSize() = Dimension(4, 1)
            override fun paintComponent(g: Graphics) {
                g.color =
                    if (visualIndex == (lastState?.selectedVisualIndex ?: -1)) UiStyles.LIME else UiStyles.CARD_BORDER
                g.fillRect(0, 0, width, height)
            }
        }
        card.add(stripe, BorderLayout.WEST)
        val center = JPanel()
        center.isOpaque = false
        center.layout = BoxLayout(center, BoxLayout.X_AXIS)
        val title = JLabel("#${dataIndex + 1}").apply {
            foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(
                Font.BOLD
            )
        }
        val times = JPanel(FlowLayout(FlowLayout.LEFT, 14, 0)).apply {
            isOpaque = false
            add(JLabel(Timecode.format(p.startMs)).apply { foreground = UiStyles.FG_SECONDARY })
            add(JLabel(p.endMs?.let { Timecode.format(it) } ?: "—").apply { foreground = UiStyles.FG_SECONDARY })
        }
        center.add(title)
        center.add(Box.createHorizontalStrut(12))
        center.add(times)
        card.add(center, BorderLayout.CENTER)
        // Right-side actions (Edit, Delete) — show on hover
        run {
            val actionsPanel = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
            }
            val editBtn = UiStyles.smallIconButton(UiStyles.pencilIcon(), "Edit times/label") {
                try {
                    EditPointDialog.show(this@PointsCardsView, p, actions)
                } catch (_: Throwable) {
                }
            }
            val delBtn = UiStyles.smallIconButton(UiStyles.crossIcon(), "Delete point") {
                actions.selectByVisualIndex(visualIndex)
                try {
                    actions.deletePoint(p.id)
                } catch (_: Throwable) {
                }
            }
            actionsPanel.add(editBtn)
            actionsPanel.add(Box.createHorizontalStrut(8))
            actionsPanel.add(delBtn)
            actionsPanel.isVisible = false
            card.add(actionsPanel, BorderLayout.EAST)

            // Hover behavior: show actions on hover; hide only when mouse truly leaves the card area (not when moving to children)
            val toggle = object : MouseAdapter() {
                private fun show() {
                    if (!actionsPanel.isVisible) {
                        actionsPanel.isVisible = true
                        card.revalidate(); card.repaint()
                    }
                }
                private fun maybeHide() {
                    // Defer to the next tick to allow enter events on children to fire first
                    SwingUtilities.invokeLater {
                        try {
                            val pointer = java.awt.MouseInfo.getPointerInfo()?.location
                            if (pointer != null) {
                                val loc = card.locationOnScreen
                                val rect = Rectangle(loc, card.size)
                                if (rect.contains(pointer)) return@invokeLater // still inside the card → keep visible
                            }
                        } catch (_: Throwable) { }
                        actionsPanel.isVisible = false
                        card.revalidate(); card.repaint()
                    }
                }
                override fun mouseEntered(e: MouseEvent) { show() }
                override fun mouseExited(e: MouseEvent) { maybeHide() }
            }
            // Attach to card and key children so moving between them doesn't hide the panel
            card.addMouseListener(toggle)
            actionsPanel.addMouseListener(toggle)
            editBtn.addMouseListener(toggle)
            delBtn.addMouseListener(toggle)
        }
        // Click selects (and seeks to start)
        card.addMouseListener(object : MouseAdapter() {
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
        private const val RIGHT_PANEL_WIDTH = 340
        private const val CARD_H_MARGIN = 10
        private const val CARD_WIDTH = RIGHT_PANEL_WIDTH - CARD_H_MARGIN * 2
        private const val CARD_HEIGHT = 44
    }
}