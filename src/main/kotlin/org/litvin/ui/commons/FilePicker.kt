package org.litvin.ui.commons

import java.awt.Component
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

interface FilePicker {
    fun chooseSourceVideo(
        parent: Component?,
        title: String,
        initialDirectory: File? = null,
        suggestedFile: File? = null,
    ): File?

    fun chooseExportDestination(
        parent: Component?,
        title: String,
        initialDirectory: File? = null,
        suggestedFile: File? = null,
    ): File?
}

class SwingFilePicker : FilePicker {
    override fun chooseSourceVideo(
        parent: Component?,
        title: String,
        initialDirectory: File?,
        suggestedFile: File?,
    ): File? = chooser(title, initialDirectory, suggestedFile).apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("Video Files", "mp4", "mov", "mkv", "avi", "m4v", "wmv")
    }.let { chooser ->
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun chooseExportDestination(
        parent: Component?,
        title: String,
        initialDirectory: File?,
        suggestedFile: File?,
    ): File? = chooser(title, initialDirectory, suggestedFile).let { chooser ->
        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun chooser(title: String, initialDirectory: File?, suggestedFile: File?): JFileChooser =
        JFileChooser(initialDirectory?.takeIf { it.exists() }).apply {
            dialogTitle = title
            selectedFile = suggestedFile
        }
}
