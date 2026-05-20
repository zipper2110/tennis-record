package org.litvin.ui.tabs.markup.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.PointDto
import org.litvin.ui.tabs.markup.PointPatch
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * EditPointDialog — a small modal editor for a single markup point.
 *
 * Responsibilities:
 * - Show/edit fields: start time, end time, label
 * - Support Confirm (OK) and Delete actions; Cancel just closes the dialog
 * - Communicate changes strictly via MarkupActions
 * - Keep work lightweight on EDT (no long-running operations)
 */
object EditPointDialog {

    /**
     * Shows the dialog. On OK, calls actions.editPoint(). On Delete, asks for confirmation and calls actions.deletePoint().
     * Parsing/formatting uses Timecode utilities. Any parsing error will show an error dialog and keep the editor open.
     */
    fun show(parent: Component, point: PointDto, actions: MarkupActions) {
        val panel = JPanel(GridBagLayout())
        val gc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }
        val tfStart = JTextField(Timecode.format(point.startMs), 14)
        val tfEnd = JTextField(point.endMs?.let { Timecode.format(it) } ?: "", 14)
        val tfLabel = JTextField(point.label ?: "", 18)

        fun addRow(y: Int, label: String, comp: JComponent) {
            gc.gridx = 0; gc.gridy = y; panel.add(JLabel(label), gc)
            gc.gridx = 1; panel.add(comp, gc)
        }

        addRow(0, "Start:", tfStart)
        addRow(1, "End:", tfEnd)
        addRow(2, "Label:", tfLabel)

        // Custom option pane with Delete button
        val options = arrayOf<Any>("OK", "Delete", "Cancel")
        while (true) {
            val res = JOptionPane.showOptionDialog(
                parent,
                panel,
                "Edit point",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
            )
            when (res) {
                0 -> { // OK
                    try {
                        val newStart = Timecode.parse(tfStart.text)
                        val newEnd = Timecode.parse(tfEnd.text)
                        val newLabel = tfLabel.text
                        actions.editPoint(point.id, PointPatch(startMs = newStart, endMs = newEnd, label = newLabel))
                        return
                    } catch (t: Throwable) {
                        try {
                            JOptionPane.showMessageDialog(parent, t.message ?: "Invalid time format", "Invalid input", JOptionPane.ERROR_MESSAGE)
                        } catch (_: Throwable) { /* ignore dialog errors */ }
                        // Loop back to let user fix input
                    }
                }
                1 -> { // Delete
                    val name = try {
                        Timecode.format(point.startMs) + " - " + (point.endMs?.let { Timecode.format(it) } ?: "?")
                    } catch (_: Throwable) { point.id }
                    val confirm = try {
                        JOptionPane.showConfirmDialog(parent, "Delete marked point $name?", "Confirm delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                    } catch (_: Throwable) { JOptionPane.CANCEL_OPTION }
                    if (confirm == JOptionPane.OK_OPTION) {
                        actions.deletePoint(point.id)
                        return
                    }
                }
                else -> return // Cancel or window closed
            }
        }
    }
}