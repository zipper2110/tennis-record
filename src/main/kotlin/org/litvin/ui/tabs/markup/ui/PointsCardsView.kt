package org.litvin.ui.tabs.markup.ui

import org.litvin.markup.EdlIO
import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.commons.scrollIntoView
import org.litvin.ui.tabs.markup.CommentDto
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.MarkupEventDto
import org.litvin.ui.tabs.markup.MarkupViewState
import org.litvin.ui.tabs.markup.PointDto
import org.litvin.ui.tabs.markup.RallyEventDto
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/** Chronological Rallies & events card list for marked rallies and source-pinned comments. */
class PointsCardsView(
    private val actions: MarkupActions,
    private val chooseCommentColor: (Component, String) -> String? = { parent, hex ->
        val initial = runCatching { Color.decode(hex) }.getOrDefault(Color.WHITE)
        JColorChooser.showDialog(parent, "Choose comment color", initial)?.let { color ->
            "#%06X".format(color.rgb and 0xFFFFFF)
        }
    },
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
    private val pendingContainer = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(10, CARD_H_MARGIN, 10, CARD_H_MARGIN)
    }

    private val cardComponents = mutableListOf<JComponent>()
    private var lastState: MarkupViewState? = null
    private var renderedEvents: List<MarkupEventDto> = emptyList()

    init {
        isOpaque = true
        background = UiStyles.DARK_BG
        add(scroll, BorderLayout.CENTER)
        add(pendingContainer, BorderLayout.SOUTH)
        runCatching { applyDarkScrollbar(scroll, background) }
    }

    fun setState(state: MarkupViewState) {
        val previousKeys = lastState?.events.orEmpty().map { it.stableKey }.toSet()
        lastState = state
        val ordered = orderedEvents(state)
        val newIndex = ordered.indexOfLast { it.stableKey !in previousKeys }
            .takeIf { previousKeys.isNotEmpty() && it >= 0 }

        rebuild(state, ordered)
        newIndex?.let { index -> SwingUtilities.invokeLater { scrollToVisualIndex(index) } }
    }

    fun updateSelection(selectedIndex: Int) {
        val previous = lastState?.selectedVisualIndex
        lastState = lastState?.copy(selectedVisualIndex = selectedIndex) ?: lastState
        previous?.takeIf { it in cardComponents.indices }?.let { applyCardSelectionStyle(cardComponents[it], false) }
        selectedIndex.takeIf { it in cardComponents.indices }?.let { applyCardSelectionStyle(cardComponents[it], true) }
    }

    fun scrollToVisualIndex(index: Int) {
        if (index !in cardComponents.indices) return
        val isPending = lastState?.pendingDraftStartMs != null && index == cardComponents.lastIndex
        if (!isPending) cardComponents[index].scrollIntoView()
    }

    internal fun visibleTitles(): List<String> = renderedEvents.map {
        when (it) {
            is CommentDto -> "Comment #${it.id}"
            is RallyEventDto -> "#${rallyOrdinal(it)}"
        }
    }

    internal fun visibleText(): String = renderedEvents.joinToString("\n") {
        when (it) {
            is CommentDto -> it.text
            is RallyEventDto -> it.point.label.orEmpty()
        }
    }

    internal fun chooseCommentColorForTest(id: Int) = chooseCommentColorFor(id)

    private fun rebuild(state: MarkupViewState, ordered: List<MarkupEventDto>) {
        listPanel.removeAll()
        pendingContainer.removeAll()
        cardComponents.clear()
        renderedEvents = ordered

        ordered.forEachIndexed { visualIndex, event ->
            val card = when (event) {
                is RallyEventDto -> buildRallyCard(visualIndex, event.point)
                is CommentDto -> buildCommentCard(visualIndex, event)
            }
            cardComponents += card
            listPanel.add(card)
            listPanel.add(Box.createVerticalStrut(10))
        }

        state.pendingDraftStartMs?.let { startMs ->
            val card = buildPendingCard(cardComponents.size, startMs)
            cardComponents += card
            pendingContainer.add(card, BorderLayout.CENTER)
            pendingContainer.isVisible = true
        } ?: run { pendingContainer.isVisible = false }

        listPanel.revalidate()
        listPanel.repaint()
        pendingContainer.revalidate()
        pendingContainer.repaint()
    }

    private fun orderedEvents(state: MarkupViewState): List<MarkupEventDto> =
        state.events.sortedWith(compareBy<MarkupEventDto> { it.startMs }.thenBy { it.stableKey })

    private fun rallyOrdinal(rally: RallyEventDto): Int =
        renderedEvents.filterIsInstance<RallyEventDto>().indexOfFirst { it.point.id == rally.point.id } + 1

    private fun buildPendingCard(visualIndex: Int, startMs: Long): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UiStyles.SURFACE_HIGH
        border = cardBorder()
        alignmentX = LEFT_ALIGNMENT
        fixedSize(this, CARD_HEIGHT)
        add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Pending…").apply { foreground = UiStyles.FG_SECONDARY; font = font.deriveFont(Font.BOLD) })
            add(Box.createVerticalStrut(4))
            add(JLabel(Timecode.format(startMs)).apply { foreground = UiStyles.FG_PRIMARY })
        }, BorderLayout.CENTER)
        add(stripe(visualIndex), BorderLayout.WEST)
        toolTipText = "Pending point — press V to set End"
        applyCardSelectionStyle(this, visualIndex == lastState?.selectedVisualIndex)
    }

    private fun buildRallyCard(visualIndex: Int, point: PointDto): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UiStyles.CARD_BG
        border = cardBorder()
        alignmentX = LEFT_ALIGNMENT
        fixedSize(this, CARD_HEIGHT)
        add(stripe(visualIndex), BorderLayout.WEST)

        add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("#${rallyOrdinal(RallyEventDto(point))}").apply { foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(Font.BOLD) })
            add(Box.createHorizontalStrut(12))
            add(JPanel(FlowLayout(FlowLayout.LEFT, 14, 0)).apply {
                isOpaque = false
                add(JLabel(Timecode.format(point.startMs)).apply { foreground = UiStyles.FG_SECONDARY })
                add(JLabel(point.endMs?.let(Timecode::format) ?: "—").apply { foreground = UiStyles.FG_SECONDARY })
            })
        }, BorderLayout.CENTER)

        val actionsPanel = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.X_AXIS) }
        val favorite = UiStyles.smallIconButton(UiStyles.favoriteIcon(18, point.favorite), "Favorite [A]") { actions.toggleFavorite(point.id) }.apply {
            name = "favorite-point-${point.id}"
            text = "[A]"
            font = font.deriveFont(Font.BOLD, 10f)
            foreground = if (point.favorite) UiStyles.YELLOW else UiStyles.FG_SECONDARY
        }
        val edit = UiStyles.smallIconButton(UiStyles.pencilIcon(), "Edit times/label") {
            EditPointDialog.show(this@PointsCardsView, point, actions)
        }
        val delete = UiStyles.smallIconButton(UiStyles.crossIcon(), "Delete point") {
            actions.selectByVisualIndex(visualIndex)
            actions.deletePoint(point.id)
        }
        actionsPanel.add(favorite)
        actionsPanel.add(Box.createHorizontalStrut(8))
        actionsPanel.add(edit)
        actionsPanel.add(Box.createHorizontalStrut(8))
        actionsPanel.add(delete)
        add(actionsPanel, BorderLayout.EAST)
        installHoverActions(this, actionsPanel, listOf(favorite, edit, delete), point.favorite)
        installSelectAndSeek(this, visualIndex, point.startMs)
        applyCardSelectionStyle(this, visualIndex == lastState?.selectedVisualIndex)
    }

    private fun buildCommentCard(visualIndex: Int, comment: CommentDto): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UiStyles.CARD_BG
        border = cardBorder()
        alignmentX = LEFT_ALIGNMENT
        fixedSize(this, COMMENT_CARD_HEIGHT)
        add(stripe(visualIndex), BorderLayout.WEST)

        val content = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        content.add(JLabel("Comment #${comment.id}").apply { foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(Font.BOLD) })
        content.add(Box.createVerticalStrut(3))
        content.add(JLabel("${Timecode.format(comment.startMs)}  ·  ${formatDuration(comment.durationMs)}").apply { foreground = UiStyles.FG_SECONDARY })
        content.add(Box.createVerticalStrut(5))
        content.add(JTextArea(comment.text).apply {
            isEditable = false
            isOpaque = false
            foreground = UiStyles.FG_PRIMARY
            lineWrap = true
            wrapStyleWord = true
            rows = 2
            font = font.deriveFont(12f)
            border = BorderFactory.createEmptyBorder()
        })
        add(content, BorderLayout.CENTER)

        val actionsPanel = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.X_AXIS) }
        val color = JButton("Color").apply {
            name = "comment-color-${comment.id}"
            toolTipText = "Change comment color"
            foreground = runCatching { Color.decode(comment.colorHex) }.getOrDefault(UiStyles.FG_PRIMARY)
            UiStyles.styleSecondary(this)
            addActionListener { chooseCommentColorFor(comment.id) }
        }
        val edit = UiStyles.smallIconButton(UiStyles.pencilIcon(), "Edit comment") {
            EditCommentDialog.showEdit(this@PointsCardsView, comment, actions)
        }
        val delete = UiStyles.smallIconButton(UiStyles.crossIcon(), "Delete comment") {
            actions.selectByVisualIndex(visualIndex)
            actions.deleteComment(comment.id)
        }
        actionsPanel.add(color)
        actionsPanel.add(Box.createHorizontalStrut(6))
        actionsPanel.add(edit)
        actionsPanel.add(Box.createHorizontalStrut(6))
        actionsPanel.add(delete)
        add(actionsPanel, BorderLayout.EAST)
        installHoverActions(this, actionsPanel, listOf(edit, delete), false)
        installSelectAndSeek(this, visualIndex, comment.startMs)
        applyCardSelectionStyle(this, visualIndex == lastState?.selectedVisualIndex)
    }

    private fun chooseCommentColorFor(id: Int) {
        val comment = renderedEvents.filterIsInstance<CommentDto>().firstOrNull { it.id == id } ?: return
        val color = chooseCommentColor(this, comment.colorHex) ?: return
        EdlIO.normalizeColorHex(color)?.let { actions.updateCommentColor(id, it) }
    }

    private fun installSelectAndSeek(card: JComponent, visualIndex: Int, startMs: Long) {
        card.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    actions.selectByVisualIndex(visualIndex)
                    actions.seekTo(startMs)
                }
            }
        })
    }

    private fun installHoverActions(card: JComponent, panel: JPanel, buttons: List<JButton>, favoriteInitiallyVisible: Boolean) {
        buttons.forEach { it.isVisible = false }
        val toggle = object : MouseAdapter() {
            private fun show() {
                buttons.forEach { it.isVisible = true }
                card.revalidate(); card.repaint()
            }
            private fun hideIfOutside() {
                SwingUtilities.invokeLater {
                    val inside = runCatching {
                        val pointer = java.awt.MouseInfo.getPointerInfo()?.location ?: return@runCatching false
                        java.awt.Rectangle(card.locationOnScreen, card.size).contains(pointer)
                    }.getOrDefault(false)
                    if (!inside) {
                        buttons.forEachIndexed { index, button -> button.isVisible = index == 0 && favoriteInitiallyVisible }
                        card.revalidate(); card.repaint()
                    }
                }
            }
            override fun mouseEntered(event: MouseEvent) = show()
            override fun mouseExited(event: MouseEvent) = hideIfOutside()
        }
        card.addMouseListener(toggle)
        panel.addMouseListener(toggle)
        buttons.forEach { it.addMouseListener(toggle) }
    }

    private fun stripe(visualIndex: Int): JComponent = object : JComponent() {
        override fun getPreferredSize() = Dimension(4, 1)
        override fun paintComponent(graphics: Graphics) {
            graphics.color = if (visualIndex == lastState?.selectedVisualIndex) UiStyles.LIME else UiStyles.CARD_BORDER
            graphics.fillRect(0, 0, width, height)
        }
    }

    private fun cardBorder() = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
        BorderFactory.createEmptyBorder(10, 12, 10, 12),
    )

    private fun fixedSize(component: JComponent, height: Int) {
        val size = Dimension(CARD_WIDTH, height)
        component.minimumSize = size
        component.preferredSize = size
        component.maximumSize = size
    }

    private fun applyCardSelectionStyle(card: JComponent, selected: Boolean) {
        card.background = if (selected) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
        card.repaint()
    }

    private fun formatDuration(durationMs: Long): String = "%.1f s".format(durationMs / 1_000.0)

    private companion object {
        const val RIGHT_PANEL_WIDTH = 340
        const val CARD_H_MARGIN = 10
        const val CARD_WIDTH = RIGHT_PANEL_WIDTH - CARD_H_MARGIN * 2
        const val CARD_HEIGHT = 44
        const val COMMENT_CARD_HEIGHT = 94
    }
}
