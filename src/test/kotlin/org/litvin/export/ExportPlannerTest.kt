package org.litvin.export

import org.litvin.ExportPreset
import org.litvin.CommentOverlaySpan
import org.litvin.points.CommentV1
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportPlannerTest {
    private val preset = ExportPreset(id = "balanced", label = "Balanced")

    @Test
    fun validateEdlSortsPointsAndSkipsInvalidOrOverlappingIntervals() {
        val edl = EdlV1(
            points = listOf(
                point("late", 2_000, 3_000),
                point("invalid", 5_000, 4_000),
                point("first", 0, 1_000),
                point("overlap", 900, 1_200),
                point("middle", 1_200, 1_500),
            )
        )

        val valid = ExportPlanner.validateEdl(edl)

        assertEquals(listOf("first", "middle", "late"), valid.map { it.id })
    }

    @Test
    fun summarizeCountsRawPointsValidFavoritesDurationAndScoredOutcomes() {
        val edl = EdlV1(
            points = listOf(
                point("p1", 0, 1_000, favorite = true),
                point("p2", 1_000, 1_500),
                point("bad", 2_000, 1_900, favorite = true),
            )
        )
        val score = ScoreV1(outcomes = mapOf("p1" to Outcome.P1, "bad" to Outcome.NONE))

        val summary = ExportPlanner.summarize(edl, score)

        assertEquals(3, summary.pointCount)
        assertEquals(1, summary.favoriteCount)
        assertEquals(1_500, summary.totalMs)
        assertEquals(1_000, summary.favoriteTotalMs)
        assertEquals(2, summary.scoredCount)
        assertFalse(summary.allScored)
    }

    @Test
    fun buildRenderPlanUsesFavoriteSubsetForIdleTrimAndOverlayExportIds() {
        val edl = EdlV1(
            points = listOf(
                point("p1", 0, 1_000, favorite = true),
                point("p2", 2_000, 3_000),
            )
        )
        val score = ScoreV1(
            outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.P2),
            player1Name = "A",
            player2Name = "B",
        )

        val plan = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest(),
                sourcePath = "input.mp4",
                edl = edl,
                score = score,
                preset = preset,
                resolution = ExportResolution("1080p", 1920, 1080),
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                favoriteOnly = true,
                includeScoreboard = true,
                outputPath = "out.mp4",
                outputFrameRate = "30000/1001",
            )
        )

        assertEquals(listOf("p1"), plan.keptPoints.map { it.id })
        assertTrue(plan.effectiveIdleTrim)
        assertEquals(listOf("p1"), plan.job.edlSnapshot.map { it.id })
        assertEquals(true, plan.job.favoriteOnly)
        assertEquals(true, plan.job.includeScoreboard)
        assertEquals("30000/1001", plan.job.outputFrameRate)
        assertEquals(1, plan.overlayTimeline.size)
        assertEquals(0, plan.overlayTimeline.single().startMs)
        assertEquals(1_000, plan.overlayTimeline.single().endMs)
    }

    @Test
    fun filenamesExtensionsAndResolutionParsingMatchExportUiDefaults() {
        assertEquals(
            "Match-balanced-1080p.mp4",
            ExportPlanner.suggestFilename("Match", "balanced", "1080p"),
        )
        assertEquals(
            "Match-quality-4K.mp4",
            ExportPlanner.suggestFilename("Match", "quality", "4K"),
        )
        assertEquals(
            File("render.mp4"),
            ExportPlanner.ensureExtension(File("render"), "mp4"),
        )
        assertEquals(
            File("render.mov"),
            ExportPlanner.ensureExtension(File("render.mov"), "mp4"),
        )
        assertEquals(ExportResolution("4K", 3840, 2160), ExportPlanner.parseResolution("4K"))
        assertEquals(ExportResolution("4K", 3840, 2160), ExportPlanner.parseResolution("4K (source)"))
        assertEquals(ExportResolution("2560x1440", 2560, 1440), ExportPlanner.parseResolution("2560x1440"))
    }

    @Test
    fun initializationReadinessExplainsDisabledStates() {
        assertEquals(
            "Open a project first (Projects -> Open).",
            ExportPlanner.initializationReadiness(
                hasProject = false,
                sourceVideoExists = false,
                idleTrim = true,
                favoriteOnly = false,
                validPoints = emptyList(),
            ).disabledReason,
        )

        assertEquals(
            "Source video not found. Set it in Projects/Points.",
            ExportPlanner.initializationReadiness(
                hasProject = true,
                sourceVideoExists = false,
                idleTrim = true,
                favoriteOnly = false,
                validPoints = listOf(point("p1", 0, 1_000)),
            ).disabledReason,
        )

        val fullRender = ExportPlanner.initializationReadiness(
            hasProject = true,
            sourceVideoExists = true,
            idleTrim = false,
            favoriteOnly = false,
            validPoints = emptyList(),
        )
        assertTrue(fullRender.enabled)
        assertNull(fullRender.disabledReason)
    }

    @Test
    fun renderFormattingFormatsSizesAndDurations() {
        assertEquals("999 B", RenderFormatting.formatSize(999))
        assertEquals("1.50 KB", RenderFormatting.formatSize(1_500))
        assertEquals("2.00 MB", RenderFormatting.formatSize(2_000_000))
        assertEquals("1:02:03", RenderFormatting.formatDuration(3_723_000))
    }

    @Test
    fun renderFormattingDescribesHowTheVideoWasCut() {
        assertEquals("Full video", RenderFormatting.formatCutMode(idleTrim = false, favoriteOnly = false))
        assertEquals("Full video", RenderFormatting.formatCutMode(idleTrim = false, favoriteOnly = true))
        assertEquals("Cut points", RenderFormatting.formatCutMode(idleTrim = true, favoriteOnly = false))
        assertEquals("Favorite points", RenderFormatting.formatCutMode(idleTrim = true, favoriteOnly = true))
    }

    @Test
    fun renderFormattingDescribesOutputFrameRate() {
        assertEquals("60 FPS", RenderFormatting.formatFrameRate("60"))
        assertEquals("29.97 FPS", RenderFormatting.formatFrameRate("30000/1001"))
        assertNull(RenderFormatting.formatFrameRate(null))
        assertNull(RenderFormatting.formatFrameRate("not-a-rate"))
    }

    @Test
    fun trimmedExport_includesCommentStartingInKeptPointAndCarriesDurationAcrossGap() {
        val edl = EdlV1(
            points = listOf(point("p1", 1_000, 4_000), point("p2", 10_000, 14_000)),
            comments = listOf(CommentV1(3, 3_000, 5_000, "Line call", "#FFFFFF")),
        )

        val plan = buildPlan(edl, idleTrim = true, favoriteOnly = false, includeComments = true)

        assertEquals(
            listOf(CommentOverlaySpan(3, 2_000, 7_000, "Line call", "#FFFFFF")),
            plan.commentOverlayTimeline,
        )
        assertEquals(plan.commentOverlayTimeline, plan.job.commentOverlayTimeline)
    }

    @Test
    fun trimmedExport_omitsCommentsBeginningOutsideKeptIntervals() {
        val edl = EdlV1(
            points = listOf(point("fav", 0, 1_000, favorite = true), point("other", 2_000, 3_000)),
            comments = listOf(
                CommentV1(1, 1_000, 500, "Boundary", "#FFFFFF"),
                CommentV1(2, 1_500, 500, "Gap", "#FFFFFF"),
                CommentV1(3, 2_100, 500, "Non-favorite", "#FFFFFF"),
            ),
        )

        val plan = buildPlan(edl, idleTrim = true, favoriteOnly = true, includeComments = true)

        assertEquals(emptyList(), plan.commentOverlayTimeline)
    }

    @Test
    fun fullVideoExport_keepsSourceTimeAndConfiguredDuration() {
        val edl = EdlV1(comments = listOf(CommentV1(5, 6_000, 750, "Net cord", "#112233")))

        val plan = buildPlan(edl, idleTrim = false, favoriteOnly = false, includeComments = true)

        assertEquals(
            listOf(CommentOverlaySpan(5, 6_000, 6_750, "Net cord", "#112233")),
            plan.commentOverlayTimeline,
        )
    }

    @Test
    fun disabledCommentsCheckbox_omitsCommentSnapshot() {
        val edl = EdlV1(
            points = listOf(point("p1", 0, 1_000)),
            comments = listOf(CommentV1(1, 500, 500, "In", "#FFFFFF")),
        )

        val plan = buildPlan(edl, idleTrim = true, favoriteOnly = false, includeComments = false)

        assertEquals(emptyList(), plan.commentOverlayTimeline)
        assertEquals(false, plan.job.includeComments)
        assertEquals(emptyList(), plan.job.commentOverlayTimeline)
    }

    private fun point(id: String, startMs: Int, endMs: Int, favorite: Boolean = false): PointV1 {
        return PointV1(id = id, startMs = startMs, endMs = endMs, favorite = favorite)
    }

    private fun buildPlan(
        edl: EdlV1,
        idleTrim: Boolean,
        favoriteOnly: Boolean,
        includeComments: Boolean,
    ): ExportRenderPlan = ExportPlanner.buildRenderPlan(
        ExportRenderPlanRequest(
            manifest = manifest(),
            sourcePath = "input.mp4",
            edl = edl,
            score = ScoreV1(),
            preset = preset,
            resolution = ExportResolution("1080p", 1920, 1080),
            encoderLabel = "H.264 (libx264)",
            idleTrim = idleTrim,
            favoriteOnly = favoriteOnly,
            includeScoreboard = false,
            includeComments = includeComments,
            outputPath = "out.mp4",
        ),
    )

    private fun manifest(): ProjectManifestV1 {
        return ProjectManifestV1(
            id = "project-id",
            name = "Project",
            createdAt = "2026-08-03T00:00:00Z",
            lastOpenedAt = "2026-08-03T00:00:00Z",
            sourceVideo = "input.mp4",
        )
    }
}
