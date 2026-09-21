package org.litvin.ui.tabs.export

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.createDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportSettingsPreferencesTest {
    @Test
    fun savesAndLoadsVideoExportSettings() {
        val node = Preferences.userRoot().node("/org/litvin/test/${UUID.randomUUID()}")
        try {
            val settings = ExportSettingsPreferences(node)

            settings.savePreset("quality")
            settings.saveResolution("4K")
            settings.saveEncoder("h264_nvenc")
            settings.saveOutputFrameRate("30000/1001")

            assertEquals(
                ExportVideoSettings("quality", "4K", "h264_nvenc", "30000/1001"),
                settings.load(),
            )
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun savesAndLoadsOutputDirectory() {
        val directory = Files.createTempDirectory("tennis-record-export")
            .resolve("videos")
            .createDirectory()
        withSettings { settings ->
            settings.saveOutputDirectory(directory.resolve("match.mp4").toFile())

            assertEquals(directory.toFile().absoluteFile, settings.loadOutputDirectory())
        }
    }

    @Test
    fun ignoresOutputDirectoryThatNoLongerExists() {
        val missingDirectory = Files.createTempDirectory("tennis-record-export")
            .resolve("missing")
            .toFile()
        withSettings { settings ->
            settings.saveOutputDirectory(missingDirectory.resolve("match.mp4"))

            assertNull(settings.loadOutputDirectory())
        }
    }

    @Test
    fun remembersIncludeCommentsPerProject() {
        withSettings { settings ->
            settings.saveIncludeComments("C:/projects/match-a", false)
            settings.saveIncludeComments("C:/projects/match-b", true)

            assertFalse(settings.loadIncludeComments("C:/projects/match-a")!!)
            assertTrue(settings.loadIncludeComments("C:/projects/match-b")!!)
        }
    }

    @Test
    fun hasNoIncludeCommentsChoiceUntilItIsSaved() {
        withSettings { settings ->
            assertNull(settings.loadIncludeComments("C:/projects/untouched"))
            assertNull(settings.loadIncludeComments("  "))
        }
    }

    private fun withSettings(block: (ExportSettingsPreferences) -> Unit) {
        val node = Preferences.userRoot().node("/org/litvin/test/${UUID.randomUUID()}")
        try {
            block(ExportSettingsPreferences(node))
        } finally {
            node.removeNode()
        }
    }
}
