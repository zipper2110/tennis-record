package org.litvin.ui.tabs.projects.components

import org.litvin.projects.NewProjectRules
import org.litvin.ui.UiStyles
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
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Opens the "Rename project" dialog and returns the new name, or null after Cancel. Tests replace the dialog with a fake. */
fun interface ProjectNameEditor {
    fun edit(parent: Component, currentName: String): String?
}

/**
 * Modal "Rename project" dialog with one field for the project name.
 *
 * "Rename" is disabled while the name is not correct. A message under the field tells the user what to correct.
 */
class RenameProjectDialog private constructor(
    owner: Window?,
    currentName: String,
) : JDialog(owner, "Rename project", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ProjectNameEditor {
        private val ERROR_FG = Color(0xFF, 0x7A, 0x7A)

        override fun edit(parent: Component, currentName: String): String? {
            val dialog = RenameProjectDialog(SwingUtilities.getWindowAncestor(parent), currentName)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var result: String? = null

    private val nameField = JTextField(currentName, 36).apply { name = "rename-project-name" }
    private val nameError = JLabel(" ").apply {
        name = "rename-project-name-error"
        foreground = ERROR_FG
        font = font.deriveFont(font.size2D - 1f)
    }
    private val renameButton = UiStyles.primarySmallButton("Rename") { rename() }.apply {
        name = "rename-project-save"
    }

    init {
        name = "rename-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = JButton("Cancel").apply {
            name = "rename-project-cancel"
            addActionListener { dispose() }
        }
        val field = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val c = GridBagConstraints().apply { anchor = GridBagConstraints.WEST }
            c.gridx = 0; c.gridy = 0; c.insets = Insets(4, 0, 0, 14)
            add(JLabel("Project name").apply { foreground = UiStyles.FG_SECONDARY }, c)
            c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL; c.insets = Insets(4, 0, 0, 0)
            add(nameField, c)
            c.gridy = 1; c.insets = Insets(2, 0, 0, 0)
            add(nameError, c)
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(cancelButton)
            add(renameButton)
        }

        contentPane = JPanel(BorderLayout(0, 16)).apply {
            border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
            add(field, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        rootPane.defaultButton = renameButton
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "rename-project-cancel")
        rootPane.actionMap.put("rename-project-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = dispose()
        })

        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { validateName() }
            override fun removeUpdate(e: DocumentEvent?) { validateName() }
            override fun changedUpdate(e: DocumentEvent?) { validateName() }
        })

        validateName()
        pack()
        minimumSize = size
        nameField.selectAll()
    }

    /** Shows the error message and enables "Rename" only for a correct name. Returns true for a correct name. */
    private fun validateName(): Boolean {
        val message = NewProjectRules.nameError(nameField.text)
        nameError.text = message ?: " "
        val valid = message == null
        renameButton.isEnabled = valid
        renameButton.background = if (valid) UiStyles.GREEN else UiStyles.FG_DISABLED
        return valid
    }

    private fun rename() {
        if (!validateName()) return
        result = nameField.text.trim()
        dispose()
    }
}
