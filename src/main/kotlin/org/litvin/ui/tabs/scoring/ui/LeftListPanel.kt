package org.litvin.ui.tabs.scoring.ui

import org.litvin.markup.PointV1
import org.litvin.scoring.Outcome
import org.litvin.ui.tabs.scoring.NavigationActions
import org.litvin.ui.tabs.scoring.ScoringActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.DocumentFilter

/**
 *
 * Responsibilities:
 * - Hosts the timeline list of points (via TimelineSection)
 * - Provides footer actions (Next Point) and placeholder buttons
 * - Provides player name fields with length limits and change callbacks
 *
 */
class LeftListPanel(
    private val actions: NavigationActions,
    private val onNamesChanged: (p1: String, p2: String) -> Unit,
    private val onColorsChanged: (c1Hex: String, c2Hex: String) -> Unit,
) : JPanel(BorderLayout()) {

    private fun parseHexOrNull(s: String?): Color? {
        if (s == null) return null
        val t = s.trim().removePrefix("#")
        if (t.length != 6) return null
        return try {
            val r = t.substring(0, 2).toInt(16)
            val g = t.substring(2, 4).toInt(16)
            val b = t.substring(4, 6).toInt(16)
            Color(r, g, b)
        } catch (_: Throwable) { null }
    }

    private val timeline = TimelineSection(actions)
    private val nextPointBtn: JButton
    private val p1NameField: JTextField
    private val p2NameField: JTextField
    private lateinit var p1ColorBtn: JButton
    private lateinit var p2ColorBtn: JButton

    private var isUpdatingNameFields: Boolean = false
    private var isUpdatingColors: Boolean = false

    init {
        background = Color(0x15, 0x15, 0x15)
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        preferredSize = Dimension(280, 0)

        add(timeline, BorderLayout.CENTER)

        val footer = JPanel()
        footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)
        footer.border = EmptyBorder(6, 6, 6, 6)
        footer.isOpaque = false

        fun fullButton(text: String): JButton {
            val b = JButton(text)
            b.isFocusPainted = false
            b.background = Color(0x26, 0x26, 0x26)
            b.foreground = Color(0xDD, 0xFF, 0xB0)
            b.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x55), 1),
                EmptyBorder(8, 8, 8, 8)
            )
            val prefH = b.preferredSize.height
            b.maximumSize = Dimension(Int.MAX_VALUE, prefH)
            b.minimumSize = Dimension(0, prefH)
            b.alignmentX = 0f
            return b
        }

        nextPointBtn = fullButton("Next Point  [R]")
        nextPointBtn.name = "next-point"
        nextPointBtn.toolTipText = "R — Next Point"
        nextPointBtn.addActionListener { actions.advanceToNextPoint() }
        nextPointBtn.isEnabled = false

        val manualBtn = fullButton("Manual Marker")
        manualBtn.name = "manual-marker"
        manualBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        manualBtn.isEnabled = false
        manualBtn.toolTipText = "Temporarily disabled"

        val settingsBtn = fullButton("Scoreboard Settings")
        settingsBtn.name = "scoreboard-settings"
        settingsBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        settingsBtn.isEnabled = false
        settingsBtn.toolTipText = "Temporarily disabled"

        fun nameField(): JTextField {
            val tf = JTextField()
            tf.background = Color(0x26, 0x26, 0x26)
            tf.foreground = Color(0xFF, 0xFF, 0xFF)
            tf.caretColor = Color(0xFF, 0xFF, 0xFF)
            tf.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x55), 1),
                EmptyBorder(6, 6, 6, 6)
            )
            tf.maximumSize = Dimension(Int.MAX_VALUE, tf.preferredSize.height)
            tf.alignmentX = 0f
            val doc = tf.document
            if (doc is AbstractDocument) {
                doc.documentFilter = object : DocumentFilter() {
                    @Throws(BadLocationException::class)
                    override fun insertString(fb: FilterBypass, offs: Int, str: String, a: AttributeSet?) {
                        val newLen = fb.document.length + (str.length)
                        if (newLen <= 24) super.insertString(fb, offs, str, a) else {
                            val allowed = 24 - fb.document.length
                            if (allowed > 0) super.insertString(fb, offs, str.substring(0, allowed), a)
                        }
                    }

                    @Throws(BadLocationException::class)
                    override fun replace(fb: FilterBypass, offs: Int, length: Int, str: String?, a: AttributeSet?) {
                        val currentLen = fb.document.length
                        val addLen = str?.length ?: 0
                        val newLen = currentLen - length + addLen
                        if (newLen <= 24) super.replace(fb, offs, length, str, a) else {
                            val allowed = 24 - (currentLen - length)
                            if (allowed > 0 && str != null) super.replace(
                                fb, offs, length, str.substring(0, allowed), a
                            )
                        }
                    }
                }
            }
            return tf
        }

        val p1Label = JLabel("Player 1 name")
        p1Label.foreground = Color(0xAD, 0xAA, 0xAA)
        p1Label.font = p1Label.font.deriveFont(Font.BOLD, 10f)
        p1NameField = nameField()
        val p2Label = JLabel("Player 2 name")
        p2Label.foreground = Color(0xAD, 0xAA, 0xAA)
        p2Label.font = p2Label.font.deriveFont(Font.BOLD, 10f)
        p2NameField = nameField()

        // Color pickers
        fun colorButton(): JButton {
            val b = JButton()
            b.text = "Pick color"
            b.isFocusPainted = false
            b.background = Color(0x26, 0x26, 0x26)
            b.foreground = Color(0xFF, 0xFF, 0xFF)
            b.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x55), 1),
                EmptyBorder(4, 6, 4, 6)
            )
            b.maximumSize = Dimension(Int.MAX_VALUE, b.preferredSize.height)
            b.alignmentX = 0f
            return b
        }
        p1ColorBtn = colorButton()
        p2ColorBtn = colorButton()
        p1ColorBtn.toolTipText = "Pick Player 1 color"
        p2ColorBtn.toolTipText = "Pick Player 2 color"

        fun showPicker(cur: Color?, onSel: (Color) -> Unit) {
            val initial = cur ?: Color(0x4D, 0xA3, 0xFF)
            val chosen = JColorChooser.showDialog(this, "Choose Color", initial)
            if (chosen != null) onSel(chosen)
        }
        fun colorHex(c: Color): String = "#%02X%02X%02X".format(c.red, c.green, c.blue)
        fun parseHexOrNull(s: String?): Color? {
            if (s == null) return null
            val t = s.trim().removePrefix("#")
            if (t.length != 6) return null
            return try {
                val r = t.substring(0, 2).toInt(16)
                val g = t.substring(2, 4).toInt(16)
                val b = t.substring(4, 6).toInt(16)
                Color(r, g, b)
            } catch (_: Throwable) { null }
        }
        fun notifyNamesChanged() {
            if (isUpdatingNameFields) return
            val n1 = p1NameField.text.trim()
            val n2 = p2NameField.text.trim()
            onNamesChanged(n1, n2)
        }
        fun notifyColorsChanged() {
            if (isUpdatingColors) return
            val c1 = p1ColorBtn.background
            val c2 = p2ColorBtn.background
            onColorsChanged(colorHex(c1), colorHex(c2))
        }
        p1NameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = notifyNamesChanged()
            override fun removeUpdate(e: DocumentEvent) = notifyNamesChanged()
            override fun changedUpdate(e: DocumentEvent) = notifyNamesChanged()
        })
        p2NameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = notifyNamesChanged()
            override fun removeUpdate(e: DocumentEvent) = notifyNamesChanged()
            override fun changedUpdate(e: DocumentEvent) = notifyNamesChanged()
        })
        p1ColorBtn.addActionListener {
            showPicker(p1ColorBtn.background) { c ->
                p1ColorBtn.background = c
                notifyColorsChanged()
            }
        }
        p2ColorBtn.addActionListener {
            showPicker(p2ColorBtn.background) { c ->
                p2ColorBtn.background = c
                notifyColorsChanged()
            }
        }

        footer.add(nextPointBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(manualBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(settingsBtn)
        footer.add(Box.createVerticalStrut(10))
        footer.add(p1Label)
        footer.add(Box.createVerticalStrut(3))
        footer.add(p1NameField)
        footer.add(Box.createVerticalStrut(4))
        footer.add(p1ColorBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(p2Label)
        footer.add(Box.createVerticalStrut(3))
        footer.add(p2NameField)
        footer.add(Box.createVerticalStrut(4))
        footer.add(p2ColorBtn)

        val footerWrap = JPanel(BorderLayout())
        footerWrap.isOpaque = false
        footerWrap.add(footer, BorderLayout.NORTH)
        add(footerWrap, BorderLayout.SOUTH)
    }

    // API
    fun setList(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>,
        p1ColorHex: String,
        p2ColorHex: String,
    ) {
        timeline.setList(points, outcomesByPointId, p1ColorHex, p2ColorHex)
    }

    fun setSelectedIndex(index: Int, userInitiated: Boolean) {
        timeline.setSelectedIndex(index, userInitiated)
    }

    fun scrollIntoView(index: Int) {
        timeline.scrollIntoView(index)
    }

    fun setNextEnabled(enabled: Boolean) {
        nextPointBtn.isEnabled = enabled
    }

    fun setPlayerNames(p1: String, p2: String) {
        isUpdatingNameFields = true
        try {
            p1NameField.text = p1
            p2NameField.text = p2
        } finally {
            isUpdatingNameFields = false
        }
    }

    fun setPlayerColors(c1Hex: String, c2Hex: String) {
        isUpdatingColors = true
        try {
            parseHexOrNull(c1Hex)?.let { p1ColorBtn.background = it }
            parseHexOrNull(c2Hex)?.let { p2ColorBtn.background = it }
        } finally {
            isUpdatingColors = false
        }
    }
}
