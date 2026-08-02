package org.litvin.ui.tabs.export

import java.io.File
import java.util.prefs.Preferences

data class ExportVideoSettings(
    val presetId: String?,
    val resolution: String?,
    val encoderId: String?,
)

class ExportSettingsPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(SwingExportPanel::class.java),
) {
    fun load(): ExportVideoSettings = ExportVideoSettings(
        presetId = preferences.get(KEY_PRESET, null),
        resolution = preferences.get(KEY_RESOLUTION, null),
        encoderId = preferences.get(KEY_ENCODER, null),
    )

    fun savePreset(id: String) {
        preferences.put(KEY_PRESET, id)
    }

    fun saveResolution(resolution: String) {
        preferences.put(KEY_RESOLUTION, resolution)
    }

    fun saveEncoder(id: String) {
        preferences.put(KEY_ENCODER, id)
    }

    fun loadOutputDirectory(): File? {
        val path = preferences.get(KEY_OUTPUT_DIRECTORY, null) ?: return null
        return File(path).takeIf { it.isDirectory }
    }

    fun saveOutputDirectory(outputFile: File) {
        val directory = outputFile.absoluteFile.parentFile ?: return
        preferences.put(KEY_OUTPUT_DIRECTORY, directory.absolutePath)
    }

    private companion object {
        const val KEY_PRESET = "export.video.preset"
        const val KEY_RESOLUTION = "export.video.resolution"
        const val KEY_ENCODER = "export.video.encoder"
        const val KEY_OUTPUT_DIRECTORY = "export.output.directory"
    }
}
