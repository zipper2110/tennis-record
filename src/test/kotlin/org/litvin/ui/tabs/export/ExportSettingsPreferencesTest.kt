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

            assertEquals(ExportVideoSettings(null, false, null), settings.load())

            settings.saveSimplePreset("balanced")
            settings.saveAdvancedMode(true)
            settings.saveEncoder("h264_nvenc")

            assertEquals(
                ExportVideoSettings("balanced", true, "h264_nvenc"),
                settings.load(),
            )
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun ignoresAndRemovesTheEncoderOfTheEarlierExportPanel() {
        val node = Preferences.userRoot().node("/org/litvin/test/${UUID.randomUUID()}")
        try {
            node.put("export.video.encoder", "libx264")
            node.put("export.video.preset", "very-high")

            assertNull(ExportSettingsPreferences(node).load().encoderId)
            assertNull(node.get("export.video.encoder", null))
            assertNull(node.get("export.video.preset", null))
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
