package org.litvin.ui.tabs.points.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.points.CommentDto
import org.litvin.ui.tabs.points.CommentPatch
import org.litvin.ui.tabs.points.PointsActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.math.BigDecimal
import java.math.RoundingMode
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** Modal editor for creating or changing a source-time-pinned point comment. */
object EditCommentDialog {
    /** How long a new comment stays on screen until the user changes it. */
    private const val DEFAULT_DURATION_MS = 5_000L

    fun showCreate(parent: Component, initialStartMs: Long, defaultColor: String, actions: PointsActions) {
        show(parent, "Add comment", initialStartMs, DEFAULT_DURATION_MS, "", defaultColor) { startMs, durationMs, text, color ->
            actions.createComment(startMs, durationMs, text, color)
        }
    }

    fun showEdit(parent: Component, comment: CommentDto, actions: PointsActions) {
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
            name = "points-comment-text"
            lineWrap = true
            wrapStyleWord = true
        }
        val start = JTextField(formatTimestamp(initialStartMs), 14).apply { name = "points-comment-start" }
        val duration = JTextField(formatSeconds(initialDurationMs), 8).apply { name = "points-comment-duration" }
        var colorHex = initialColor
        val color = JButton("Change…").apply {
            name = "points-comment-color"
            icon = UiStyles.colorSwatchIcon(colorFor(colorHex))
            toolTipText = colorHex
        }
        val error = JLabel(" ").apply { foreground = Color(0xFF, 0x6B, 0x6B) }
        val saveButton = JButton("Save").apply { name = "points-comment-save" }
        val cancelButton = JButton("Cancel")

        color.addActionListener {
            JColorChooser.showDialog(dialog, "Choose comment color", colorFor(colorHex))?.let { chosen ->
                colorHex = "#%06X".format(chosen.rgb and 0xFFFFFF)
                color.icon = UiStyles.colorSwatchIcon(chosen)
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
        start.toolTipText = "hh:mm:ss.mmm, mm:ss.mmm, or seconds"
        duration.toolTipText = "How long the comment stays on screen, in seconds"

        addRow(0, "Text:", JScrollPane(text), GridBagConstraints.BOTH)
        addRow(1, "Start:", start)
        addRow(2, "Duration (seconds):", duration)
        addRow(3, "Text color:", color)
        addRow(4, "", error, GridBagConstraints.HORIZONTAL)

        val doSave = {
            val problem = firstProblem(start.text, duration.text, text.text)
            if (problem != null) {
                error.text = problem
            } else {
                val parsed = parseFields(start.text, duration.text, text.text, colorHex)
                if (parsed == null) {
                    error.text = "Text color must use the #RRGGBB format."
                } else {
                    save(parsed.startMs, parsed.durationMs, parsed.text, parsed.colorHex)
                    dialog.dispose()
                }
            }
        }
        saveButton.addActionListener { doSave() }
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
        // Enter saves from the single-line fields; the text area keeps Enter for new lines.
        dialog.rootPane.defaultButton = saveButton
        dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("ESCAPE"), "points-comment-cancel")
        dialog.rootPane.actionMap.put("points-comment-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = dialog.dispose()
        })
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent?) {
                text.requestFocusInWindow()
                text.caretPosition = text.document.length
            }
        })
        dialog.pack()
        dialog.setLocationRelativeTo(parent)
        dialog.isVisible = true
    }

    /** Returns a message for the first invalid field, or null when every field is usable. */
    private fun firstProblem(start: String, durationSeconds: String, text: String): String? {
        if (text.isBlank()) return "Enter the comment text."
        val startMs = runCatching { Timecode.parse(start) }.getOrNull()
            ?: return "Start must use the hh:mm:ss.mmm format."
        if (startMs < 0) return "Start cannot be negative."
        val durationMs = runCatching {
            BigDecimal(durationSeconds.trim()).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull() ?: return "Duration must be a number of seconds."
        if (durationMs <= 0) return "Duration must be greater than zero."
        return null
    }

    private data class ParsedComment(val startMs: Long, val durationMs: Long, val text: String, val colorHex: String)

    private fun parseFields(start: String, durationSeconds: String, text: String, color: String): ParsedComment? = try {
        val startMs = Timecode.parse(start)
        val durationMs = BigDecimal(durationSeconds.trim())
            .movePointRight(3)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val normalizedColor = org.litvin.points.EdlIO.normalizeColorHex(color) ?: return null
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
