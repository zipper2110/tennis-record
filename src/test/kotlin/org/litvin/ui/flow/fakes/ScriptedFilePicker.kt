package org.litvin.ui.flow.fakes

import org.litvin.ui.commons.FilePicker
import java.awt.Component
import java.io.File
import java.util.ArrayDeque

sealed interface FilePickerOutcome {
    data class Selected(val file: File) : FilePickerOutcome
    data object Cancelled : FilePickerOutcome
}

data class FilePickerCall(
    val kind: String,
    val title: String,
    val initialDirectory: File?,
    val suggestedFile: File?,
)

class ScriptedFilePicker : FilePicker {
    private val sourceOutcomes = ArrayDeque<FilePickerOutcome>()
    private val exportOutcomes = ArrayDeque<FilePickerOutcome>()
    private val recordedCalls = mutableListOf<FilePickerCall>()
    val calls: List<FilePickerCall> get() = synchronized(this) { recordedCalls.toList() }

    @Synchronized
    fun scriptSource(vararg outcomes: FilePickerOutcome) = outcomes.forEach { sourceOutcomes += it }

    @Synchronized
    fun scriptExport(vararg outcomes: FilePickerOutcome) = outcomes.forEach { exportOutcomes += it }

    override fun chooseSourceVideo(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File? =
        take("source", title, initialDirectory, suggestedFile, sourceOutcomes)

    override fun chooseExportDestination(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File? =
        take("export", title, initialDirectory, suggestedFile, exportOutcomes)

    @Synchronized
    private fun take(
        kind: String,
        title: String,
        initialDirectory: File?,
        suggestedFile: File?,
        outcomes: ArrayDeque<FilePickerOutcome>,
    ): File? {
        recordedCalls += FilePickerCall(kind, title, initialDirectory, suggestedFile)
        val outcome: FilePickerOutcome = (if (outcomes.isEmpty()) null else outcomes.removeFirst())
            ?: error("Unexpected $kind file-picker call '$title'; recorded calls: $recordedCalls")
        return when (outcome) {
            is FilePickerOutcome.Selected -> outcome.file
            FilePickerOutcome.Cancelled -> null
        }
    }
}
