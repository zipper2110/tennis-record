package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.scoring.Outcome
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExportConfigurationUiFlowIT {
    @Test
    fun `opening export after scoring a point includes the scoreboard by default`(context: UiFlowContext) {
        val project = context.fixtures.emptyProject()
        val output = context.workspace.resolve("exports").resolve("scored-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.rallies.markPoint(1_000, 2_000)
        val pointId = application.rallies.assertSinglePointPersisted(project.directory, 1_000, 2_000)
        application.scoring.open()
            .awardPointToPlayer1()
            .assertOutcomePersisted(project.directory, pointId, Outcome.P1)

        application.export.open()
            .initialize(output)
            .assertExactlyOneRenderQueued()

        assertEquals(true, context.renderService.jobs.single().includeScoreboard)
    }

    @Test
    fun `opening export after a no-point outcome leaves the scoreboard unchecked`(context: UiFlowContext) {
        val project = context.fixtures.emptyProject()
        val output = context.workspace.resolve("exports").resolve("no-point-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.rallies.markPoint(1_000, 2_000)
        val pointId = application.rallies.assertSinglePointPersisted(project.directory, 1_000, 2_000)
        application.scoring.open()
            .awardNoPoint()
            .assertOutcomePersisted(project.directory, pointId, Outcome.NONE)

        application.export.open()
            .initialize(output)
            .assertExactlyOneRenderQueued()

        assertEquals(false, context.renderService.jobs.single().includeScoreboard)
    }

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
        assertEquals(output.toAbsolutePath().normalize(), Path.of(job.outputPath).toAbsolutePath().normalize())
        assertFalse(Files.exists(output), "Fake render service must not create the output file")
        assertFalse(Files.exists(Path.of("$output.part")), "Fake render service must not create an FFmpeg part file")
    }

    @Test
    fun `changing bitrate quality keeps the selected resolution`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("four-k-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .selectResolution("4K")
            .selectBitrateQuality("Balanced")
            .initialize(output)
            .assertExactlyOneRenderQueued()

        val job = context.renderService.jobs.single()
        assertEquals(3840, job.outWidth)
        assertEquals(2160, job.outHeight)
    }

    @Test
    fun `source resolution option is labelled and exports at source dimensions`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("source-resolution-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .selectResolution("720x486 (source)")
            .initialize(output)
            .assertExactlyOneRenderQueued()

        val job = context.renderService.jobs.single()
        assertEquals(720, job.outWidth)
        assertEquals(486, job.outHeight)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
