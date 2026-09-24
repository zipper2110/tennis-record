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

/** The name and the source video of a new project. */
data class NewProjectRequest(val name: String, val sourceVideoPath: String)

/** Opens the "New project" dialog and returns the confirmed values, or null after Cancel. Tests replace the dialog with a fake. */
fun interface NewProjectEditor {
    /**
     * Shows [initial] for edit.
     * [chooseVideo] opens the video picker over the dialog at the current path and returns the selected path,
     * or null after Cancel.
     */
    fun edit(
        parent: Component,
        initial: NewProjectRequest,
        chooseVideo: (dialog: Component, currentPath: String) -> String?,
    ): NewProjectRequest?
}

/**
 * Modal "New project" dialog: the project name and the match video.
 *
 * "Create project" is disabled while the name is empty or the path is not a supported video file.
 * A message under each field tells the user what to correct.
 */
class NewProjectDialog private constructor(
    owner: Window?,
    initial: NewProjectRequest,
    private val chooseVideo: (dialog: Component, currentPath: String) -> String?,
) : JDialog(owner, "New project", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : NewProjectEditor {
        private val ERROR_FG = Color(0xFF, 0x7A, 0x7A)

        override fun edit(
            parent: Component,
            initial: NewProjectRequest,
            chooseVideo: (dialog: Component, currentPath: String) -> String?,
        ): NewProjectRequest? {
            val dialog = NewProjectDialog(SwingUtilities.getWindowAncestor(parent), initial, chooseVideo)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var result: NewProjectRequest? = null

    /** The name that the dialog suggested for the current video. The user did not change the name while it is equal to this value. */
    private var suggestedName = initial.name

    private val nameField = JTextField(initial.name, 36).apply { name = "new-project-name" }
    private val nameError = errorLabel("new-project-name-error")
    private val videoField = JTextField(initial.sourceVideoPath, 36).apply { name = "new-project-video" }
    private val videoError = errorLabel("new-project-video-error")
    private val browseButton = JButton("Browse…").apply {
        name = "new-project-browse"
        isFocusPainted = false
        toolTipText = "Select a different match video"
        addActionListener { browse() }
    }
    private val createButton = UiStyles.primarySmallButton("Create project") { create() }.apply {
        name = "new-project-create"
    }

    init {
        name = "new-project-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val cancelButton = JButton("Cancel").apply {
            name = "new-project-cancel"
            addActionListener { dispose() }
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(cancelButton)
            add(createButton)
        }

        contentPane = JPanel(BorderLayout(0, 16)).apply {
            border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
            add(form(), BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        rootPane.defaultButton = createButton
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "new-project-cancel")
        rootPane.actionMap.put("new-project-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = dispose()
        })

        val revalidate = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { validateFields() }
            override fun removeUpdate(e: DocumentEvent?) { validateFields() }
            override fun changedUpdate(e: DocumentEvent?) { validateFields() }
        }
        nameField.document.addDocumentListener(revalidate)
        videoField.document.addDocumentListener(revalidate)

        validateFields()
        pack()
        minimumSize = size
        nameField.selectAll()
    }

    private fun form(): JPanel {
        val form = JPanel(GridBagLayout()).apply { isOpaque = false }
        val c = GridBagConstraints().apply { anchor = GridBagConstraints.WEST }
        var row = 0
        fun addRow(label: String, component: JComponent, error: JLabel) {
            c.gridx = 0; c.gridy = row; c.weightx = 0.0; c.fill = GridBagConstraints.NONE
            c.insets = Insets(4, 0, 0, 14)
            form.add(JLabel(label).apply { foreground = UiStyles.FG_SECONDARY }, c)
            c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(4, 0, 0, 0)
            form.add(component, c)
            row++
            c.gridy = row
            c.insets = Insets(2, 0, 6, 0)
            form.add(error, c)
            row++
        }

        addRow("Project name", nameField, nameError)
        addRow("Match video", JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(videoField, BorderLayout.CENTER)
            add(browseButton, BorderLayout.EAST)
        }, videoError)
        return form
    }

    /** Shows the error messages and enables "Create project" only for correct values. Returns true for correct values. */
    private fun validateFields(): Boolean {
        val nameMessage = NewProjectRules.nameError(nameField.text)
        val videoMessage = NewProjectRules.sourceVideoError(videoField.text)
        showError(nameError, nameMessage)
        showError(videoError, videoMessage)
        val valid = nameMessage == null && videoMessage == null
        createButton.isEnabled = valid
        createButton.background = if (valid) UiStyles.GREEN else UiStyles.FG_DISABLED
        return valid
    }

    private fun browse() {
        val selected = chooseVideo(this, videoField.text.trim()) ?: return
        // Follow the new video with the name, but keep a name that the user typed.
        if (nameField.text.trim() == suggestedName.trim()) {
            nameField.text = NewProjectRules.suggestedName(selected)
        }
        suggestedName = NewProjectRules.suggestedName(selected)
        videoField.text = selected
    }

    private fun create() {
        if (!validateFields()) return
        result = NewProjectRequest(name = nameField.text.trim(), sourceVideoPath = videoField.text.trim())
        dispose()
    }

    private fun showError(label: JLabel, message: String?) {
        // A space keeps the height of the row, so the dialog does not change size.
        label.text = message ?: " "
    }

    private fun errorLabel(componentName: String) = JLabel(" ").apply {
        name = componentName
        foreground = ERROR_FG
        font = font.deriveFont(font.size2D - 1f)
    }
}
