package org.litvin.ui.tabs.export

import java.io.File
import java.security.MessageDigest
import java.util.prefs.Preferences

data class ExportVideoSettings(
    val presetId: String?,
    val resolution: String?,
    val encoderId: String?,
    val outputFrameRate: String?,
)

class ExportSettingsPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(SwingExportPanel::class.java),
) {
    fun load(): ExportVideoSettings = ExportVideoSettings(
        presetId = preferences.get(KEY_PRESET, null),
        resolution = preferences.get(KEY_RESOLUTION, null),
        encoderId = preferences.get(KEY_ENCODER, null),
        outputFrameRate = preferences.get(KEY_OUTPUT_FRAME_RATE, null),
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

    fun saveOutputFrameRate(frameRate: String) {
        preferences.put(KEY_OUTPUT_FRAME_RATE, frameRate)
    }

    fun loadOutputDirectory(): File? {
        val path = preferences.get(KEY_OUTPUT_DIRECTORY, null) ?: return null
        return File(path).takeIf { it.isDirectory }
    }

    fun saveOutputDirectory(outputFile: File) {
        val directory = outputFile.absoluteFile.parentFile ?: return
        preferences.put(KEY_OUTPUT_DIRECTORY, directory.absolutePath)
    }

    /**
     * Reads the "Include comments" choice of one project.
     * Returns null if the user did not set the checkbox for that project.
     */
    fun loadIncludeComments(projectDir: String): Boolean? {
        val key = includeCommentsKey(projectDir) ?: return null
        if (preferences.get(key, null) == null) return null
        return preferences.getBoolean(key, false)
    }

    fun saveIncludeComments(projectDir: String, includeComments: Boolean) {
        val key = includeCommentsKey(projectDir) ?: return
        preferences.putBoolean(key, includeComments)
    }

    /** A preferences key has a maximum length of 80 characters. Use a digest of the project path. */
    private fun includeCommentsKey(projectDir: String): String? {
        val normalized = projectDir.trim().takeIf { it.isNotEmpty() }
            ?.let { File(it).absolutePath.lowercase() }
            ?: return null
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return KEY_COMMENTS_PREFIX + digest
    }

    private companion object {
        const val KEY_PRESET = "export.video.preset"
        const val KEY_RESOLUTION = "export.video.resolution"
        const val KEY_ENCODER = "export.video.encoder"
        const val KEY_OUTPUT_FRAME_RATE = "export.video.frameRate"
        const val KEY_OUTPUT_DIRECTORY = "export.output.directory"
        const val KEY_COMMENTS_PREFIX = "export.comments."
    }
}
