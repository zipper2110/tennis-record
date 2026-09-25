package org.litvin.ui.tabs.export

import java.io.File
import java.security.MessageDigest
import java.util.prefs.Preferences

/**
 * The quality choices that the export keeps between sessions. The resolution, frame rate and bitrate
 * of the Advanced tab are not kept: they start from the source video of each project.
 */
data class ExportVideoSettings(
    val simplePresetId: String?,
    val advancedMode: Boolean,
    val encoderId: String?,
)

class ExportSettingsPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(SwingExportPanel::class.java),
) {
    fun load(): ExportVideoSettings {
        removeObsoleteKeys()
        return ExportVideoSettings(
            simplePresetId = preferences.get(KEY_SIMPLE_PRESET, null),
            advancedMode = preferences.getBoolean(KEY_ADVANCED_MODE, false),
            encoderId = preferences.get(KEY_ENCODER, null),
        )
    }

    /**
     * Removes the values of the earlier export panel. Its encoder drop-down saved an encoder
     * without a click of the user, so that value must not replace the best encoder of the new panel.
     */
    private fun removeObsoleteKeys() {
        OBSOLETE_KEYS.forEach(preferences::remove)
    }

    fun saveSimplePreset(id: String) {
        preferences.put(KEY_SIMPLE_PRESET, id)
    }

    fun saveAdvancedMode(advanced: Boolean) {
        preferences.putBoolean(KEY_ADVANCED_MODE, advanced)
    }

    /** The encoder that the user selected on the Advanced tab. */
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

    /**
     * Reads the "Include comments" choice of one project.
     * Returns null if the user did not set the checkbox for that project.
     */
    fun loadIncludeComments(projectDir: String): Boolean? = loadProjectChoice(KEY_COMMENTS_PREFIX, projectDir)

    fun saveIncludeComments(projectDir: String, includeComments: Boolean) =
        saveProjectChoice(KEY_COMMENTS_PREFIX, projectDir, includeComments)

    /**
     * Reads the "Include statistics card" choice of one project.
     * Returns null if the user did not set the checkbox for that project.
     */
    fun loadIncludeStatsCard(projectDir: String): Boolean? = loadProjectChoice(KEY_STATS_CARD_PREFIX, projectDir)

    fun saveIncludeStatsCard(projectDir: String, include: Boolean) =
        saveProjectChoice(KEY_STATS_CARD_PREFIX, projectDir, include)

    /**
     * Reads the "Include set summaries" choice of one project.
     * Returns null if the user did not set the checkbox for that project.
     */
    fun loadIncludeSetSummaries(projectDir: String): Boolean? = loadProjectChoice(KEY_SET_SUMMARIES_PREFIX, projectDir)

    fun saveIncludeSetSummaries(projectDir: String, include: Boolean) =
        saveProjectChoice(KEY_SET_SUMMARIES_PREFIX, projectDir, include)

    private fun loadProjectChoice(prefix: String, projectDir: String): Boolean? {
        val key = projectKey(prefix, projectDir) ?: return null
        if (preferences.get(key, null) == null) return null
        return preferences.getBoolean(key, false)
    }

    private fun saveProjectChoice(prefix: String, projectDir: String, value: Boolean) {
        val key = projectKey(prefix, projectDir) ?: return
        preferences.putBoolean(key, value)
    }

    /** A preferences key has a maximum length of 80 characters. Use a digest of the project path. */
    private fun projectKey(prefix: String, projectDir: String): String? {
        val normalized = projectDir.trim().takeIf { it.isNotEmpty() }
            ?.let { File(it).absolutePath.lowercase() }
            ?: return null
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return prefix + digest
    }

    private companion object {
        const val KEY_SIMPLE_PRESET = "export.video.simplePreset"
        const val KEY_ADVANCED_MODE = "export.video.advancedMode"
        const val KEY_ENCODER = "export.video.advancedEncoder"
        val OBSOLETE_KEYS = listOf(
            "export.video.preset",
            "export.video.resolution",
            "export.video.encoder",
            "export.video.frameRate",
        )
        const val KEY_OUTPUT_DIRECTORY = "export.output.directory"
        const val KEY_COMMENTS_PREFIX = "export.comments."
        const val KEY_STATS_CARD_PREFIX = "export.statsCard."
        const val KEY_SET_SUMMARIES_PREFIX = "export.setSummaries."
    }
}
