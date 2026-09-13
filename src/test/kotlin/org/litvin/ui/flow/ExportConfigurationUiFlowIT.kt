package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExportConfigurationUiFlowIT {
    @Test
    fun `configured export queues one complete render snapshot without running ffmpeg`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("balanced-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .configure(
                preset = "Balanced",
                resolution = "1080p",
                idleTrim = true,
                favoritesOnly = false,
                scoreboard = true,
                comments = true,
            )
            .initialize(output)
            .assertExactlyOneRenderQueued()

        val job = context.renderService.jobs.single()
        assertEquals(project.manifest.id, job.projectId)
        assertEquals(project.manifest.name, job.projectName)
        assertEquals(project.manifest.sourceVideo, job.sourcePath)
        assertEquals(listOf("point-1", "point-2"), job.edlSnapshot.map { it.id })
        assertEquals(listOf(500L to 2_500L, 4_000L to 6_000L), job.edlSnapshot.map { it.startMs.toLong() to it.endMs.toLong() })
        assertEquals("balanced", job.presetId)
        assertEquals(1920, job.outWidth)
        assertEquals(1080, job.outHeight)
        assertEquals("H.264 (libx264)", job.encoderLabel)
        assertEquals(true, job.idleTrim)
        assertEquals(false, job.favoriteOnly)
        assertEquals(true, job.includeScoreboard)
        assertEquals(true, job.includeComments)
        assertEquals(listOf(1), job.commentOverlayTimeline.map { it.id })
        assertEquals(output.toAbsolutePath().normalize(), Path.of(job.outputPath).toAbsolutePath().normalize())
        assertFalse(Files.exists(output), "Fake render service must not create the output file")
        assertFalse(Files.exists(Path.of("$output.part")), "Fake render service must not create an FFmpeg part file")
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
