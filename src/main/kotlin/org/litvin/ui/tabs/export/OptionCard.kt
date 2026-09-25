package org.litvin.ui.tabs.export

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Html
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * A selectable card with a bold title, an optional subtitle, some detail lines and an optional tag.
 * Put the cards of one choice in a ButtonGroup. The selected card has a lime border.
 * A card can also have expandable details, which show only while the card is selected.
 *
 * The card is a toggle button, so the keyboard, accessibility and the UI tests use it as one.
 * The child labels have no mouse listeners, so a click anywhere on the card selects it.
 */
class OptionCard(
    componentName: String,
    /** Width of the wrapped detail text, or null for short lines that do not wrap. */
    private val wrapWidthPx: Int? = null,
) : JToggleButton() {
    private val titleLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD)
        alignmentX = 0f
    }
    private val subtitleLabel = JLabel().apply {
        alignmentX = 0f
        isVisible = false
        border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
    }
    private val chevronLabel = JLabel().apply { isVisible = false }
    private val noteTag = TagLabel("").apply { isVisible = false }
    private val noteRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        alignmentX = 0f
        border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        isVisible = false
        add(noteTag)
    }
    private val lines = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = 0f
    }
    private val expandable = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = 0f
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        isVisible = false
    }
    private var hasExpandable = false
    private val detailLabels = mutableListOf<JLabel>()

    init {
        name = componentName
        layout = BorderLayout()
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        val headerText = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(titleLabel)
            add(subtitleLabel)
        }
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = 0f
            add(headerText, BorderLayout.CENTER)
            add(chevronLabel, BorderLayout.EAST)
        }
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(header)
            add(lines)
            add(noteRow)
            add(expandable)
        }
        // A row of cards gives all cards the same height. NORTH keeps the content at the top of a taller card.
        add(body, BorderLayout.NORTH)
        addItemListener {
            updateExpanded()
            repaint()
        }
        updateColors()
    }

    fun setContent(title: String, details: List<String> = emptyList(), note: String? = null) {
        titleLabel.text = title
        lines.removeAll()
        detailLabels.clear()
        details.forEach { detail ->
            val label = JLabel().apply { alignmentX = 0f }
            UiStyles.styleHelper(label)
            val width = wrapWidthPx
            if (width != null) Html.setWrapped(label, detail, width) else label.text = detail
            stretch(label)
            detailLabels += label
            lines.add(Box.createRigidArea(Dimension(0, 2)))
            lines.add(label)
        }
        noteTag.text = note.orEmpty()
        noteTag.isVisible = !note.isNullOrEmpty()
        noteRow.isVisible = noteTag.isVisible
        stretch(titleLabel)
        getAccessibleContext().accessibleName = (listOf(title, subtitleLabel.text.orEmpty()) + details + listOfNotNull(note))
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        updateColors()
        revalidate()
        repaint()
    }

    /**
     * A second header line under the title. It is brighter and larger than the detail lines.
     * A card with a subtitle also gets a larger title, so that the two header levels are clear.
     */
    fun setSubtitle(text: String?) {
        if (!subtitleLabel.isVisible && !text.isNullOrEmpty()) {
            titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 2f)
            stretch(titleLabel)
        }
        subtitleLabel.text = text.orEmpty()
        subtitleLabel.isVisible = !text.isNullOrEmpty()
        stretch(subtitleLabel)
        revalidate()
    }

    /** Details that show only while the card is selected. A chevron in the header shows the state. */
    fun setExpandableContent(content: JComponent) {
        expandable.removeAll()
        expandable.add(content, BorderLayout.CENTER)
        hasExpandable = true
        chevronLabel.isVisible = true
        updateExpanded()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        cursor = Cursor.getPredefinedCursor(if (enabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        updateColors()
    }

    // The button UI measures only text and icon. The card size comes from its child labels.
    override fun getPreferredSize(): Dimension = layout.preferredLayoutSize(this)
    override fun getMinimumSize(): Dimension = layout.minimumLayoutSize(this)
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = when {
                !isEnabled -> UiStyles.CARD_BG
                isSelected -> SELECTED_BG
                model.isRollover -> UiStyles.SURFACE_HIGH
                else -> UiStyles.CARD_BG
            }
            g2.fillRoundRect(0, 0, width - 1, height - 1, ARC, ARC)
            g2.color = if (isSelected && isEnabled) UiStyles.LIME else BORDER
            g2.stroke = BasicStroke(if (isSelected && isEnabled) 2f else 1f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, ARC, ARC)
        } finally {
            g2.dispose()
        }
    }

    private fun updateExpanded() {
        if (!hasExpandable) return
        expandable.isVisible = isSelected
        chevronLabel.icon = if (isSelected) UiStyles.chevronDownIcon() else UiStyles.chevronRightIcon()
        revalidate()
        // The card height changes, so the column around it must lay out again.
        parent?.revalidate()
    }

    /**
     * Lets a label use the full width of the card. A label that keeps its preferred width
     * can cut off the last characters when the painted text is a little wider than the measured text.
     */
    private fun stretch(label: JLabel) {
        label.maximumSize = Dimension(Int.MAX_VALUE, label.preferredSize.height)
    }

    private fun updateColors() {
        titleLabel.foreground = if (isEnabled) UiStyles.FG_PRIMARY else UiStyles.FG_DISABLED
        subtitleLabel.foreground = if (isEnabled) UiStyles.FG_PRIMARY else UiStyles.FG_DISABLED
        detailLabels.forEach { it.foreground = if (isEnabled) UiStyles.FG_SECONDARY else UiStyles.FG_DISABLED }
        noteTag.isEnabled = isEnabled
    }

    private companion object {
        const val ARC = 12
        val SELECTED_BG = Color(0x24, 0x2B, 0x1C)
        val BORDER = Color(0x3A, 0x3A, 0x3A)
    }
}

/** Small text in a rectangle with slightly round corners, for example "original". */
class TagLabel(text: String) : JLabel(text) {
    init {
        font = font.deriveFont(Font.BOLD, (font.size2D - 2f).coerceAtLeast(10f))
        border = BorderFactory.createEmptyBorder(1, 5, 1, 5)
        foreground = UiStyles.LIME
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        foreground = if (enabled) UiStyles.LIME else UiStyles.FG_DISABLED
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = if (isEnabled) TAG_BG else UiStyles.CARD_BG
            g2.fillRoundRect(0, 0, width - 1, height - 1, 4, 4)
            g2.color = if (isEnabled) TAG_BORDER else UiStyles.FG_DISABLED
            g2.drawRoundRect(0, 0, width - 1, height - 1, 4, 4)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    private companion object {
        val TAG_BG = Color(0x2A, 0x36, 0x14)
        val TAG_BORDER = Color(0x6F, 0xA8, 0x10)
    }
}
