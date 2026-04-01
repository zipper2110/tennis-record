package org.litvin

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.Window
import java.io.File

class NewProjectDialog(private val owner: Window?) {
    data class Result(val directory: String, val name: String)

    private val invalidChars = Regex("""[<>:"/\\|?*]""")

    fun showAndWait(defaultDir: File? = null): Result? {
        val stage = Stage().apply {
            title = "Create New Project"
            initModality(Modality.WINDOW_MODAL)
            if (owner != null) initOwner(owner)
            isResizable = false
        }

        val dirField = TextField().apply {
            promptText = "Choose or enter a folder..."
            text = (defaultDir ?: defaultBaseDir()).absolutePath
        }
        val browseBtn = Button("Browse...")
        val nameField = TextField().apply { promptText = "Project name" }
        val errorLabel = Label("").apply {
            styleClass.add("error-label")
        }

        fun validate(): String? {
            val name = nameField.text?.trim() ?: ""
            if (name.isEmpty()) return "Project name cannot be empty"
            if (name.length > 64) return "Project name is too long (max 64)"
            if (invalidChars.containsMatchIn(name)) return "Name contains invalid characters: <>:\"/\\|?*"
            val dir = File(dirField.text)
            if (!dir.exists() && !dir.parentFile?.exists().orDefault(false)) return "Parent directory does not exist"
            return null
        }

        fun updateError() {
            errorLabel.text = validate() ?: ""
        }

        browseBtn.setOnAction {
            val chooser = DirectoryChooser().apply {
                title = "Select Project Folder"
                initialDirectory = when {
                    File(dirField.text).exists() -> File(dirField.text)
                    defaultDir?.exists() == true -> defaultDir
                    else -> defaultBaseDir()
                }
            }
            val chosen = chooser.showDialog(stage)
            if (chosen != null) dirField.text = chosen.absolutePath
            updateError()
        }

        nameField.textProperty().addListener { _, _, _ -> updateError() }
        dirField.textProperty().addListener { _, _, _ -> updateError() }

        val okBtn = Button("Create").apply { isDefaultButton = true }
        val cancelBtn = Button("Cancel").apply { isCancelButton = true }

        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 12.0
            padding = Insets(16.0)
            add(Label("Folder:"), 0, 0)
            add(HBox(8.0, dirField, browseBtn).apply { HBox.setHgrow(dirField, Priority.ALWAYS) }, 1, 0)
            add(Label("Project name:"), 0, 1)
            add(nameField, 1, 1)
            add(errorLabel, 1, 2)
            add(HBox(8.0, okBtn, cancelBtn).apply { alignment = Pos.CENTER_RIGHT }, 1, 3)
        }

        val scene = Scene(grid)
        stage.scene = scene

        var result: Result? = null

        fun attemptCreate() {
            val err = validate()
            if (err != null) {
                errorLabel.text = err
                return
            }
            val base = File(dirField.text)
            val name = nameField.text.trim()
            result = Result(base.absolutePath, name)
            stage.close()
        }

        okBtn.setOnAction { attemptCreate() }
        cancelBtn.setOnAction { result = null; stage.close() }

        updateError()
        stage.showAndWait()
        return result
    }

    private fun defaultBaseDir(): File {
        val userHome = System.getProperty("user.home") ?: "."
        val base = File(userHome, "Documents\\TennisRecord\\Projects")
        if (!base.exists()) base.mkdirs()
        return base
    }
}

private fun Boolean?.orDefault(def: Boolean) = this ?: def
