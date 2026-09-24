package org.litvin.projects

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NewProjectRulesTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun suggestedNameIsTheVideoFileNameWithoutTheExtension() {
        assertEquals("Club final", NewProjectRules.suggestedName("C:\\videos\\Club final.mp4"))
        assertEquals("match.2026", NewProjectRules.suggestedName("C:\\videos\\match.2026.mov"))
        assertEquals(NewProjectRules.DEFAULT_NAME, NewProjectRules.suggestedName("C:\\videos\\.mp4"))
    }

    @Test
    fun nameMustNotBeEmptyAndMustBeAValidFolderName() {
        assertNull(NewProjectRules.nameError("Club final"))
        assertNull(NewProjectRules.nameError("  Club final (2)  "))
        assertNotNull(NewProjectRules.nameError(""))
        assertNotNull(NewProjectRules.nameError("   "))
        assertNotNull(NewProjectRules.nameError("Final: day 2"))
        assertNotNull(NewProjectRules.nameError("a/b"))
        assertNotNull(NewProjectRules.nameError("Final."))
        assertNotNull(NewProjectRules.nameError("con"))
        assertNotNull(NewProjectRules.nameError("x".repeat(NewProjectRules.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun sourceVideoMustBeAnExistingNonEmptySupportedVideoFile() {
        val video = tempDir.resolve("match.MP4").toFile().apply { writeText("video") }
        val empty = tempDir.resolve("empty.mp4").toFile().apply { createNewFile() }
        val text = tempDir.resolve("notes.txt").toFile().apply { writeText("notes") }

        assertNull(NewProjectRules.sourceVideoError(video.absolutePath))
        assertNull(NewProjectRules.sourceVideoError("  ${video.absolutePath}  "))
        assertNotNull(NewProjectRules.sourceVideoError(""))
        assertNotNull(NewProjectRules.sourceVideoError(tempDir.resolve("missing.mp4").toString()))
        assertNotNull(NewProjectRules.sourceVideoError(tempDir.toString()))
        assertNotNull(NewProjectRules.sourceVideoError(empty.absolutePath))
        assertNotNull(NewProjectRules.sourceVideoError(text.absolutePath))
    }
}
