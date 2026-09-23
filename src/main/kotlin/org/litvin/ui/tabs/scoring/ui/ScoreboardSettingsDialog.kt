package org.litvin.ui.tabs.scoring.ui

import org.litvin.ScoreboardDisplay
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.ui.UiStyles
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.geom.Line2D
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/**
 * Modal dialog that sets up the scoreboard: style, title, app credit line, position, size,
 * background opacity and accent color.
 *
 * The dialog shows the result in a 16:9 frame. It also sends each change to the `onPreview` callback,
 * so the video preview can show the change at once.
 */
class ScoreboardSettingsDialog private constructor(
    owner: Window?,
    initial: ScoreboardSettingsV1,
    private val sample: ScoreboardDisplay,
    private val onPreview: (ScoreboardSettingsV1) -> Unit,
) : JDialog(owner, "Scoreboard settings", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object {
        private const val THUMBNAIL_SCALE = 0.42
        private const val THUMBNAIL_MAX_WIDTH = 212.0
        private const val THUMBNAIL_MAX_HEIGHT = 84.0
        private const val STYLE_COLUMNS = 2
        private const val STYLE_CARD_WIDTH = 236
        private const val STYLE_CARD_HEIGHT = 128
        private const val STYLE_LIST_HEIGHT = 560
        private const val FRAME_WIDTH = 576
        private const val FRAME_HEIGHT = 324

        /**
         * Shows the dialog and waits until it closes. Returns the new settings after Save,
         * or null after Cancel. [sample] is the score that the previews show.
         */
        fun show(
            parent: Component,
            initial: ScoreboardSettingsV1,
            sample: ScoreboardDisplay,
            onPreview: (ScoreboardSettingsV1) -> Unit = {},
        ): ScoreboardSettingsV1? {
            val dialog = ScoreboardSettingsDialog(SwingUtilities.getWindowAncestor(parent), initial.normalized(), sample, onPreview)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var settings = initial
    private var result: ScoreboardSettingsV1? = null
    private var updatingControls = false

    private val styleButtons = ScoreboardStyleId.entries.associateWith { style -> styleCard(style) }
    private val positionButtons = ScoreboardPosition.entries.associateWith { position ->
        JToggleButton(position.title).apply {
            name = "scoreboard-position-${position.name.lowercase().replace('_', '-')}"
            isFocusPainted = false
            addActionListener { update { it.copy(position = position) } }
        }
    }
    private val titleField = JTextField(16).apply {
        name = "scoreboard-title"
        (document as? AbstractDocument)?.documentFilter = MaxLengthFilter(ScoreboardSettingsV1.MAX_TITLE_LENGTH)
        toolTipText = "The text in the title bar of the scoreboard"
    }
    private val showTitle = JCheckBox("Show title").apply {
        name = "scoreboard-show-title"
        isOpaque = false
    }
    private val showAppCredit = JCheckBox("Show \u201C${ScoreboardSettingsV1.APP_CREDIT}\u201D line").apply {
        name = "scoreboard-show-app-credit"
        isOpaque = false
        toolTipText = "Show a line with the app name at the bottom of the scoreboard"
    }
    private val sizeSlider = slider(ScoreboardSettingsV1.MIN_SIZE_PERCENT, ScoreboardSettingsV1.MAX_SIZE_PERCENT, "scoreboard-size")
    private val sizeValue = valueLabel()
    private val opacitySlider = slider(ScoreboardSettingsV1.MIN_OPACITY_PERCENT, 100, "scoreboard-opacity")
    private val opacityValue = valueLabel()
    private val accentButton = JButton("Change…").apply {
        name = "scoreboard-accent"
        isFocusPainted = false
    }
    private val accentDefault = JButton("Use style color").apply {
        name = "scoreboard-accent-default"
        isFocusPainted = false
    }
    private val framePreview = FramePreview()

    init {
        name = "scoreboard-settings-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val styleGroup = ButtonGroup()
        styleButtons.values.forEach(styleGroup::add)
        val positionGroup = ButtonGroup()
        positionButtons.values.forEach(positionGroup::add)

        titleField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTitleChanged()
            override fun removeUpdate(e: DocumentEvent) = onTitleChanged()
            override fun changedUpdate(e: DocumentEvent) = onTitleChanged()
        })
        showTitle.addActionListener {
            update { it.copy(showTitle = showTitle.isSelected) }
        }
        showAppCredit.addActionListener {
            update { it.copy(showAppCredit = showAppCredit.isSelected) }
        }
        sizeSlider.addChangeListener { update { it.copy(sizePercent = sizeSlider.value) } }
        opacitySlider.addChangeListener { update { it.copy(backgroundOpacityPercent = opacitySlider.value) } }
        accentButton.addActionListener {
            val chosen = JColorChooser.showDialog(this, "Choose accent color", Color(accentRgb()))
            if (chosen != null) update { it.copy(accentColorHex = "#%06X".format(chosen.rgb and 0xFFFFFF)) }
        }
        accentDefault.addActionListener { update { it.copy(accentColorHex = null) } }

        val saveButton = UiStyles.primarySmallButton("Save") {
            result = settings.normalized()
            dispose()
        }.apply { name = "scoreboard-save" }
        val cancelButton = JButton("Cancel").apply {
            name = "scoreboard-cancel"
            addActionListener { cancel() }
        }
        val resetButton = JButton("Reset to defaults").apply {
            name = "scoreboard-reset"
            toolTipText = "Use the default style, title, position, size and colors"
            addActionListener { update { ScoreboardSettingsV1() } }
        }

        val styles = JPanel(GridLayout(0, STYLE_COLUMNS, 8, 8)).apply {
            isOpaque = false
            ScoreboardStyleId.entries.forEach { add(styleButtons.getValue(it)) }
        }
        val styleScroll = JScrollPane(styles).apply {
            name = "scoreboard-style-list"
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = STYLE_CARD_HEIGHT / 4
            val scrollBarW = verticalScrollBar.preferredSize.width
            preferredSize = Dimension(STYLE_COLUMNS * STYLE_CARD_WIDTH + (STYLE_COLUMNS - 1) * 8 + scrollBarW + 4, STYLE_LIST_HEIGHT)
        }
        val left = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            add(sectionLabel("Style"), BorderLayout.NORTH)
            add(styleScroll, BorderLayout.CENTER)
        }
        val right = JPanel(BorderLayout(0, 12)).apply {
            isOpaque = false
            add(JPanel(BorderLayout(0, 8)).apply {
                isOpaque = false
                add(sectionLabel("Preview"), BorderLayout.NORTH)
                add(framePreview, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(form(), BorderLayout.CENTER)
        }
        val buttons = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(resetButton, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
                add(cancelButton)
                add(saveButton)
            }, BorderLayout.EAST)
        }
        contentPane = JPanel(BorderLayout(20, 16)).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "scoreboard-cancel")
        rootPane.actionMap.put("scoreboard-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = cancel()
        })

        syncControls()
        pack()
        minimumSize = size
        // Show the selected style when the list is longer than the visible part.
        SwingUtilities.invokeLater {
            styleButtons[settings.style]?.let { it.scrollRectToVisible(Rectangle(0, 0, it.width, it.height)) }
        }
    }

    private fun form(): JPanel {
        val form = JPanel(GridBagLayout()).apply { isOpaque = false }
        val c = GridBagConstraints().apply {
            insets = Insets(4, 0, 4, 10)
            anchor = GridBagConstraints.WEST
        }
        var row = 0
        fun addRow(label: String, component: JComponent) {
            c.gridx = 0; c.gridy = row; c.weightx = 0.0; c.fill = GridBagConstraints.NONE
            form.add(JLabel(label).apply {
                foreground = UiStyles.FG_SECONDARY
                border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
                minimumSize = preferredSize
            }, c)
            c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL
            form.add(component, c)
            row++
        }
        addRow("Title", inline(titleField, showTitle))
        addRow("Position", JPanel(GridLayout(1, 0, 4, 0)).apply {
            isOpaque = false
            ScoreboardPosition.entries.forEach { add(positionButtons.getValue(it)) }
        })
        addRow("Size", inline(sizeSlider, sizeValue))
        addRow("Background", inline(opacitySlider, opacityValue))
        addRow("Bottom line", showAppCredit)
        addRow("Accent color", JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(accentButton)
            add(Box.createHorizontalStrut(8))
            add(accentDefault)
        })
        return form
    }

    private fun inline(main: JComponent, trailing: JComponent) = JPanel(BorderLayout(10, 0)).apply {
        isOpaque = false
        add(main, BorderLayout.CENTER)
        add(trailing, BorderLayout.EAST)
    }

    private fun onTitleChanged() {
        update { it.copy(title = titleField.text) }
    }

    /** Closes without a result. The caller restores the saved settings on the video. */
    private fun cancel() = dispose()

    /** Applies [change] to the settings, then refreshes the controls and the previews. */
    private fun update(change: (ScoreboardSettingsV1) -> ScoreboardSettingsV1) {
        if (updatingControls) return
        settings = change(settings)
        syncControls()
        onPreview(settings.normalized())
    }

    private fun syncControls() {
        updatingControls = true
        try {
            val defaults = ScoreboardLayouts.defaults(settings.style)
            styleButtons.forEach { (style, button) -> button.isSelected = style == settings.style }
            positionButtons.forEach { (position, button) -> button.isSelected = position == settings.position }
            if (titleField.text != settings.title) titleField.text = settings.title
            showTitle.isSelected = settings.showTitle
            titleField.isEnabled = settings.showTitle
            showAppCredit.isSelected = settings.showAppCredit
            sizeSlider.value = settings.sizePercent
            sizeValue.text = "${settings.sizePercent} %"
            val opacity = settings.backgroundOpacityPercent ?: defaults.backgroundOpacityPercent
            opacitySlider.value = opacity
            opacityValue.text = "$opacity %"
            accentButton.icon = UiStyles.colorSwatchIcon(Color(accentRgb()))
            accentDefault.isEnabled = settings.accentColorHex != null
            styleButtons.forEach { (style, button) ->
                // A style card shows that style with the other current settings.
                val cardSettings = settings.copy(style = style, backgroundOpacityPercent = null, sizePercent = 100)
                val scene = ScoreboardLayouts.scene(sample, cardSettings)
                // Wide or tall styles get a smaller thumbnail, so that every card has the same size.
                val scale = minOf(THUMBNAIL_SCALE, THUMBNAIL_MAX_WIDTH / scene.width, THUMBNAIL_MAX_HEIGHT / scene.height)
                button.icon = ImageIcon(ScoreboardSceneImage.render(scene, scale))
            }
            framePreview.repaint()
        } finally {
            updatingControls = false
        }
    }

    private fun accentRgb(): Int {
        val hex = settings.normalized().accentColorHex
        return hex?.removePrefix("#")?.toIntOrNull(16) ?: ScoreboardLayouts.defaults(settings.style).accentRgb
    }

    private fun styleCard(style: ScoreboardStyleId): JToggleButton = object : JToggleButton(style.title) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = if (isSelected) Color(0x24, 0x2B, 0x1C) else if (model.isRollover) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
                g2.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)
                g2.color = if (isSelected) UiStyles.LIME else UiStyles.CARD_BORDER
                g2.stroke = BasicStroke(if (isSelected) 2f else 1f)
                g2.drawRoundRect(1, 1, width - 3, height - 3, 12, 12)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }.apply {
        name = "scoreboard-style-${style.name.lowercase().replace('_', '-')}"
        horizontalTextPosition = SwingConstants.CENTER
        verticalTextPosition = SwingConstants.BOTTOM
        iconTextGap = 8
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        isRolloverEnabled = true
        foreground = UiStyles.FG_PRIMARY
        font = font.deriveFont(Font.BOLD)
        border = BorderFactory.createEmptyBorder(10, 12, 8, 12)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(STYLE_CARD_WIDTH, STYLE_CARD_HEIGHT)
        addActionListener { update { it.copy(style = style) } }
    }

    private fun slider(min: Int, max: Int, componentName: String) = JSlider(min, max).apply {
        name = componentName
        isOpaque = false
        majorTickSpacing = 10
        snapToTicks = false
    }

    private fun valueLabel() = JLabel().apply {
        preferredSize = Dimension(52, preferredSize.height)
        horizontalAlignment = SwingConstants.RIGHT
        foreground = UiStyles.FG_PRIMARY
    }

    private fun sectionLabel(text: String) = JLabel(text.uppercase()).apply {
        foreground = UiStyles.FG_SECONDARY
        font = font.deriveFont(Font.BOLD, font.size2D - 1f)
    }

    /** A 16:9 frame with a court backdrop. It places the board with the same rules as the video. */
    private inner class FramePreview : JComponent() {
        init {
            preferredSize = Dimension(FRAME_WIDTH, FRAME_HEIGHT)
            minimumSize = preferredSize
            name = "scoreboard-frame-preview"
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val w = width.toDouble()
                val h = height.toDouble()
                g2.paint = GradientPaint(0f, 0f, Color(0x3A, 0x6E, 0x46), 0f, h.toFloat(), Color(0x1D, 0x3F, 0x28))
                g2.fillRoundRect(0, 0, width, height, 10, 10)
                // Court lines in perspective, so the frame reads as a match video.
                g2.color = Color(255, 255, 255, 70)
                g2.stroke = BasicStroke(2f)
                g2.draw(Line2D.Double(w * 0.22, h * 0.95, w * 0.36, h * 0.30))
                g2.draw(Line2D.Double(w * 0.78, h * 0.95, w * 0.64, h * 0.30))
                g2.draw(Line2D.Double(w * 0.36, h * 0.30, w * 0.64, h * 0.30))
                g2.draw(Line2D.Double(w * 0.28, h * 0.70, w * 0.72, h * 0.70))
                g2.draw(Line2D.Double(w * 0.50, h * 0.70, w * 0.50, h * 0.30))
                g2.color = Color(255, 255, 255, 110)
                g2.stroke = BasicStroke(3f)
                g2.draw(Line2D.Double(w * 0.14, h * 0.52, w * 0.86, h * 0.52))

                val current = settings.normalized()
                val scene = ScoreboardLayouts.scene(sample, current)
                val placement = ScoreboardAss.place(scene, current, 0.0, 0.0, w, h)
                ScoreboardSceneImage.draw(g2, scene, placement.x, placement.y, placement.scale)
            } finally {
                g2.dispose()
            }
        }
    }

    private class MaxLengthFilter(private val maxLength: Int) : DocumentFilter() {
        override fun insertString(fb: FilterBypass, offset: Int, text: String?, attr: AttributeSet?) {
            replace(fb, offset, 0, text, attr)
        }

        override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
            val allowed = maxLength - (fb.document.length - length)
            val clipped = text.orEmpty().take(allowed.coerceAtLeast(0))
            super.replace(fb, offset, length, clipped, attrs)
        }
    }
}
