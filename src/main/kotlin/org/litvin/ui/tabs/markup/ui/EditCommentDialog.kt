package org.litvin.ui.tabs.markup.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.tabs.markup.CommentDto
import org.litvin.ui.tabs.markup.CommentPatch
import org.litvin.ui.tabs.markup.MarkupActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.math.BigDecimal
import java.math.RoundingMode
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

/** Modal editor for creating or changing a source-time-pinned rally comment. */
object EditCommentDialog {
    fun showCreate(parent: Component, initialStartMs: Long, defaultColor: String, actions: MarkupActions) {
        show(parent, "Add comment", initialStartMs, 2_000L, "", defaultColor) { startMs, durationMs, text, color ->
            actions.createComment(startMs, durationMs, text, color)
        }
    }

    fun showEdit(parent: Component, comment: CommentDto, actions: MarkupActions) {
        show(parent, "Edit comment", comment.startMs, comment.durationMs, comment.text, comment.colorHex) { startMs, durationMs, text, color ->
            actions.editComment(
                comment.id,
                CommentPatch(startMs = startMs, durationMs = durationMs, text = text, colorHex = color),
            )
        }
    }

    private fun show(
        parent: Component,
        title: String,
        initialStartMs: Long,
        initialDurationMs: Long,
        initialText: String,
        initialColor: String,
        save: (Long, Long, String, String) -> Unit,
    ) {
        val owner = SwingUtilities.getWindowAncestor(parent)
        val dialog = JDialog(owner as? Window, title, Dialog.ModalityType.APPLICATION_MODAL)
        val text = JTextArea(initialText, 5, 30).apply {
            name = "rallies-comment-text"
            lineWrap = true
            wrapStyleWord = true
        }
        val start = JTextField(formatTimestamp(initialStartMs), 14).apply { name = "rallies-comment-start" }
        val duration = JTextField(formatSeconds(initialDurationMs), 8).apply { name = "rallies-comment-duration" }
        var colorHex = initialColor
        val color = JButton("Color").apply {
            name = "rallies-comment-color"
            foreground = colorFor(colorHex)
            toolTipText = colorHex
        }
        val error = JLabel(" ").apply { foreground = Color(0xFF, 0x6B, 0x6B) }
        val saveButton = JButton("Save").apply { name = "rallies-comment-save" }
        val cancelButton = JButton("Cancel")

        color.addActionListener {
            JColorChooser.showDialog(dialog, "Choose comment color", colorFor(colorHex))?.let { chosen ->
                colorHex = "#%06X".format(chosen.rgb and 0xFFFFFF)
                color.foreground = chosen
                color.toolTipText = colorHex
            }
        }

        val form = JPanel(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }
        fun addRow(row: Int, label: String, component: Component, fill: Int = GridBagConstraints.NONE) {
            constraints.gridx = 0; constraints.gridy = row; constraints.weightx = 0.0; constraints.fill = GridBagConstraints.NONE
            form.add(JLabel(label), constraints)
            constraints.gridx = 1; constraints.weightx = 1.0; constraints.fill = fill
            form.add(component, constraints)
        }
        addRow(0, "Text:", JScrollPane(text), GridBagConstraints.BOTH)
        addRow(1, "Start:", start)
        addRow(2, "Duration (seconds):", duration)
        addRow(3, "Text color:", color)
        addRow(4, "", error, GridBagConstraints.HORIZONTAL)

        saveButton.addActionListener {
            val parsed = parseFields(start.text, duration.text, text.text, colorHex)
            if (parsed == null) {
                error.text = "Enter non-negative start, positive duration, and comment text."
                return@addActionListener
            }
            save(parsed.startMs, parsed.durationMs, parsed.text, parsed.colorHex)
            dialog.dispose()
        }
        cancelButton.addActionListener { dialog.dispose() }

        dialog.contentPane = JPanel(BorderLayout(10, 10)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(form, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(cancelButton)
                add(saveButton)
            }, BorderLayout.SOUTH)
        }
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.pack()
        dialog.setLocationRelativeTo(parent)
        dialog.isVisible = true
    }

    private data class ParsedComment(val startMs: Long, val durationMs: Long, val text: String, val colorHex: String)

    private fun parseFields(start: String, durationSeconds: String, text: String, color: String): ParsedComment? = try {
        val startMs = Timecode.parse(start)
        val durationMs = BigDecimal(durationSeconds.trim())
            .movePointRight(3)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val normalizedColor = org.litvin.markup.EdlIO.normalizeColorHex(color) ?: return null
        val trimmedText = text.trim()
        if (startMs < 0 || durationMs <= 0 || trimmedText.isBlank()) null
        else ParsedComment(startMs, durationMs, trimmedText, normalizedColor)
    } catch (_: Throwable) {
        null
    }

    private fun colorFor(hex: String): Color = runCatching { Color.decode(hex) }.getOrDefault(Color.WHITE)
    private fun formatSeconds(durationMs: Long): String = BigDecimal(durationMs).movePointLeft(3).stripTrailingZeros().toPlainString()
    private fun formatTimestamp(totalMs: Long): String {
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1_000L
        val milliseconds = totalMs % 1_000L
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, milliseconds)
    }
}
