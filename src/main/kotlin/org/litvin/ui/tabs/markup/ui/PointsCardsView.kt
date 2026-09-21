package org.litvin.ui.tabs.markup.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Html
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
import javax.swing.JButton
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

    /** Title of the selected card, or null when nothing is selected. */
    internal fun selectedTitle(): String? =
        lastState?.selectedVisualIndex?.let { visibleTitles().getOrNull(it) }

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

    private fun buildPendingCard(visualIndex: Int, startMs: Long): JComponent = JPanel(BorderLayout(CARD_GAP, 0)).apply {
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

    private fun buildRallyCard(visualIndex: Int, point: PointDto): JComponent = JPanel(BorderLayout(CARD_GAP, 0)).apply {
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

    private fun buildCommentCard(visualIndex: Int, comment: CommentDto): JComponent = JPanel(BorderLayout(CARD_GAP, 0)).apply {
        isOpaque = true
        background = UiStyles.CARD_BG
        border = cardBorder()
        alignmentX = LEFT_ALIGNMENT
        fixedSize(this, COMMENT_CARD_HEIGHT)
        // A comment has no active state, so the card keeps one look and the stripe shows its color.
        putClientProperty(SELECTABLE, false)

        val commentColor = runCatching { Color.decode(comment.colorHex) }.getOrDefault(UiStyles.FG_PRIMARY)
        add(colorStripe(commentColor), BorderLayout.WEST)

        val header = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("Comment #${comment.id}").apply {
                foreground = commentColor
                font = font.deriveFont(Font.BOLD, 11f)
            })
            add(Box.createHorizontalStrut(8))
            add(JLabel("${Timecode.format(comment.startMs)} · ${formatDuration(comment.durationMs)}").apply {
                foreground = UiStyles.FG_SECONDARY
                font = font.deriveFont(11f)
            })
            add(Box.createHorizontalGlue())
        }
        val body = JTextArea(comment.text).apply {
            name = "comment-text-${comment.id}"
            isEditable = false
            isOpaque = false
            isFocusable = false
            foreground = UiStyles.FG_PRIMARY
            lineWrap = true
            wrapStyleWord = true
            rows = 2
            font = font.deriveFont(12f)
            border = BorderFactory.createEmptyBorder()
        }
        val actionsPanel = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.X_AXIS) }
        val edit = UiStyles.smallIconButton(UiStyles.pencilIcon(), "Edit comment") {
            EditCommentDialog.showEdit(this@PointsCardsView, comment, actions)
        }
        val delete = UiStyles.smallIconButton(UiStyles.crossIcon(), "Delete comment") {
            actions.deleteComment(comment.id)
        }
        actionsPanel.add(edit)
        actionsPanel.add(Box.createHorizontalStrut(6))
        actionsPanel.add(delete)

        // Only the header shares its row with the buttons, so the text can use the full card width.
        val headerRow = JPanel(BorderLayout(CARD_GAP, 0)).apply {
            isOpaque = false
            add(header, BorderLayout.CENTER)
            add(actionsPanel, BorderLayout.EAST)
        }
        // The hover buttons are taller than the labels. Reserve their height now, while they are
        // still visible, so neither row moves when the pointer enters or leaves the card.
        val rowHeight = headerRow.preferredSize.height
        headerRow.preferredSize = Dimension(CARD_WIDTH, rowHeight)
        headerRow.minimumSize = Dimension(0, rowHeight)
        headerRow.maximumSize = Dimension(Int.MAX_VALUE, rowHeight)

        // BorderLayout keeps both rows against the left edge; BoxLayout would center the short labels.
        add(JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(headerRow, BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
        }, BorderLayout.CENTER)

        // The card is too small for long comments, so keep the full text available on hover.
        // Children need the same tooltip, because Swing reads it from the component under the pointer.
        val tooltip = tooltipFor(comment)
        forEachNonButtonComponent(this) { (it as? JComponent)?.toolTipText = tooltip }
        installHoverActions(this, actionsPanel, listOf(edit, delete), false)
        installSelectAndSeek(this, visualIndex, comment.startMs)
    }

    /** Full comment text for the card tooltip. HTML keeps long text on several lines. */
    private fun tooltipFor(comment: CommentDto): String = Html.wrappedTooltip(comment.text)

    private fun installSelectAndSeek(card: JComponent, visualIndex: Int, startMs: Long) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    actions.selectByVisualIndex(visualIndex)
                    actions.seekTo(startMs)
                }
            }
        }
        // Children such as the comment text area consume mouse events, so bind them too.
        forEachNonButtonComponent(card) { it.addMouseListener(listener) }
    }

    /** Applies the action to the component and to each descendant that is not a button. */
    private fun forEachNonButtonComponent(root: Component, action: (Component) -> Unit) {
        if (root is JButton) return
        action(root)
        if (root is Container) root.components.forEach { forEachNonButtonComponent(it, action) }
    }

    private fun installHoverActions(card: JComponent, panel: JPanel, buttons: List<JButton>, favoriteInitiallyVisible: Boolean) {
        // The star of a favorite rally belongs to the card at rest, not only while the pointer is over it.
        fun restVisibility() = buttons.forEachIndexed { index, button -> button.isVisible = index == 0 && favoriteInitiallyVisible }
        restVisibility()
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
                        restVisibility()
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

    private fun colorStripe(color: Color): JComponent = object : JComponent() {
        override fun getPreferredSize() = Dimension(4, 1)
        override fun paintComponent(graphics: Graphics) {
            graphics.color = color
            graphics.fillRect(0, 0, width, height)
        }
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
        if (card.getClientProperty(SELECTABLE) == false) return
        card.background = if (selected) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
        card.repaint()
    }

    private fun formatDuration(durationMs: Long): String =
        String.format(java.util.Locale.ROOT, "%.1f s", durationMs / 1_000.0)

    companion object {
        /** Horizontal margin between the right panel edge and a card. */
        const val CARD_H_MARGIN = 10

        /** Client property marking a card that has no selected state. */
        private const val SELECTABLE = "markup.card.selectable"
        private const val RIGHT_PANEL_WIDTH = 340

        /** Gap between the color stripe, the card content, and the action buttons. */
        private const val CARD_GAP = 10
        private const val CARD_WIDTH = RIGHT_PANEL_WIDTH - CARD_H_MARGIN * 2
        private const val CARD_HEIGHT = 44
        private const val COMMENT_CARD_HEIGHT = 88
    }
}
