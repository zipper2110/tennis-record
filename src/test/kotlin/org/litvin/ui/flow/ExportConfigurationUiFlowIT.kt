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
        application.points.markPoint(1_000, 2_000)
        val pointId = application.points.assertSinglePointPersisted(project.directory, 1_000, 2_000)
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
        application.points.markPoint(1_000, 2_000)
        val pointId = application.points.assertSinglePointPersisted(project.directory, 1_000, 2_000)
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
                content = "points",
                scoreboard = true,
                comments = true,
            )
            .selectSimpleQuality("balanced")
            .initialize(output)
            .assertExactlyOneRenderQueued()

        val job = context.renderService.jobs.single()
        assertEquals(project.manifest.id, job.projectId)
        assertEquals(project.manifest.name, job.projectName)
        assertEquals(project.manifest.sourceVideo, job.sourcePath)
        assertEquals(listOf("point-1", "point-2"), job.edlSnapshot.map { it.id })
        assertEquals(listOf(500L to 2_500L, 4_000L to 6_000L), job.edlSnapshot.map { it.startMs.toLong() to it.endMs.toLong() })
        assertEquals("balanced", job.presetId)
        // The fixture video is 720x486, 30 fps, 2206 kbit/s. Balanced keeps the size and uses 75% of the bitrate.
        assertEquals(720, job.outWidth)
        assertEquals(486, job.outHeight)
        assertEquals("30/1", job.outputFrameRate)
        assertEquals(1_654, job.videoBitrateK)
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

    @Test
    fun `opening export in a project with comments includes the comments by default`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("commented-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .initialize(output)
            .assertExactlyOneRenderQueued()

        assertEquals(true, context.renderService.jobs.single().includeComments)
    }

    @Test
    fun `opening export in a project without comments leaves the comments unchecked`(context: UiFlowContext) {
        val project = context.fixtures.emptyProject()
        val output = context.workspace.resolve("exports").resolve("uncommented-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.points.markPoint(1_000, 2_000)

        application.export.open()
            .initialize(output)
            .assertExactlyOneRenderQueued()

        assertEquals(false, context.renderService.jobs.single().includeComments)
    }

    @Test
    fun `export keeps the comments choice of the user for the project`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("comments-off-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open().setComments(false)
        application.points.open()
        application.export.open()
            .assertComments(false)
            .initialize(output)
            .assertExactlyOneRenderQueued()

        assertEquals(false, context.renderService.jobs.single().includeComments)
    }

    @Test
    fun `best quality is the default and keeps the source video settings`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("best-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .initialize(output)
            .assertExactlyOneRenderQueued()

        val job = context.renderService.jobs.single()
        assertEquals("best", job.presetId)
        assertEquals(720, job.outWidth)
        assertEquals(486, job.outHeight)
        assertEquals(2_205, job.videoBitrateK)
    }

    @Test
    fun `advanced mode offers only resolutions up to the source and exports the selected frame rate`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject()
        val output = context.workspace.resolve("exports").resolve("advanced-match.mp4")
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .assertAdvancedCardEnabled("export-resolution-source", true)
            .assertAdvancedCardEnabled("export-resolution-720p", false)
            .assertAdvancedCardEnabled("export-resolution-1080p", false)
            .assertAdvancedCardEnabled("export-fps-60", false)
            .selectAdvancedFrameRate("24")
            .initialize(output)
            .assertExactlyOneRenderQueued()

        val job = context.renderService.jobs.single()
        assertEquals("custom", job.presetId)
        assertEquals(720, job.outWidth)
        assertEquals(486, job.outHeight)
        assertEquals("24", job.outputFrameRate)
        assertEquals(2_205, job.videoBitrateK)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
