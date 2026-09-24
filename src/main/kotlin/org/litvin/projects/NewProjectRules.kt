package org.litvin.projects

import java.io.File

/** Rules for the name and the source video of a new project. */
object NewProjectRules {
    val VIDEO_EXTENSIONS = listOf("mp4", "mov", "mkv", "avi", "m4v", "wmv")
    const val MAX_NAME_LENGTH = 80
    const val DEFAULT_NAME = "Untitled Match"

    // The project name is also the name of the project folder. These characters are not permitted in a Windows file name.
    private const val INVALID_NAME_CHARACTERS = "<>:\"/\\|?*"
    private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

    /** Returns the file name of the video without the extension. */
    fun suggestedName(sourceVideoPath: String): String =
        File(sourceVideoPath.trim()).name.substringBeforeLast('.').trim().ifBlank { DEFAULT_NAME }

    /** Returns an error message for an incorrect project name, or null for a correct name. */
    fun nameError(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Type a project name."
            trimmed.length > MAX_NAME_LENGTH -> "Use a maximum of $MAX_NAME_LENGTH characters in the project name."
            trimmed.any { it in INVALID_NAME_CHARACTERS || it.isISOControl() } ->
                "Do not use these characters in the project name: ${INVALID_NAME_CHARACTERS.toList().joinToString(" ")}"
            trimmed.endsWith('.') -> "Do not use a period at the end of the project name."
            trimmed.substringBefore('.').uppercase() in RESERVED_NAMES -> "Windows reserves the name \"$trimmed\". Use a different name."
            else -> null
        }
    }

    /** Returns an error message for a path that is not a supported video file, or null for a correct path. */
    fun sourceVideoError(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "Select the match video."
        val file = File(trimmed)
        return when {
            !file.isFile -> "The video file does not exist."
            file.extension.lowercase() !in VIDEO_EXTENSIONS ->
                "Select a video file (${VIDEO_EXTENSIONS.joinToString(", ") { it.uppercase() }})."
            file.length() == 0L -> "The video file is empty."
            else -> null
        }
    }
}
