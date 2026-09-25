package org.litvin.export

import org.litvin.ExportPreset
import org.litvin.CommentOverlaySpan
import org.litvin.points.CommentV1
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
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
        assertEquals(1_000, summary.scoredTotalMs)
        assertEquals(2, summary.validPointCount)
        assertEquals(1_500, summary.validTotalMs)
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
            scoreboard = ScoreboardSettingsV1(style = ScoreboardStyleId.COMPACT, title = "Club final"),
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
        assertEquals(ScoreboardStyleId.COMPACT, plan.job.scoreboardSettings.style)
        assertEquals("Club final", plan.job.scoreboardSettings.title)
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
        assertEquals("2h 30min 10sec", RenderFormatting.formatDurationWords(9_010_000))
        assertEquals("1h 0min 5sec", RenderFormatting.formatDurationWords(3_605_000))
        assertEquals("5min 10sec", RenderFormatting.formatDurationWords(310_000))
        assertEquals("45sec", RenderFormatting.formatDurationWords(45_900))
    }

    @Test
    fun renderFormattingDescribesHowTheVideoWasCut() {
        assertEquals("Full video", RenderFormatting.formatCutMode(idleTrim = false, favoriteOnly = false))
        assertEquals("Full video", RenderFormatting.formatCutMode(idleTrim = false, favoriteOnly = true))
        assertEquals("Only points", RenderFormatting.formatCutMode(idleTrim = true, favoriteOnly = false))
        assertEquals("Only favorites", RenderFormatting.formatCutMode(idleTrim = true, favoriteOnly = true))
    }

    @Test
    fun renderFormattingDescribesTheContentSettings() {
        assertEquals(
            "Only points (12 points)  ·  Scoreboard: on  ·  Comments: off",
            RenderFormatting.formatContent(idleTrim = true, favoriteOnly = false, pointCount = 12, includeScoreboard = true, includeComments = false),
        )
        assertEquals(
            "Only favorites (1 point)  ·  Scoreboard: off  ·  Comments: on",
            RenderFormatting.formatContent(idleTrim = true, favoriteOnly = true, pointCount = 1, includeScoreboard = false, includeComments = true),
        )
        assertEquals(
            "Full video  ·  Scoreboard: off  ·  Comments: off",
            RenderFormatting.formatContent(idleTrim = false, favoriteOnly = false, pointCount = 0, includeScoreboard = false, includeComments = false),
        )
    }

    @Test
    fun renderFormattingDescribesTheVideoSettings() {
        assertEquals(
            "Balanced  ·  1080p  ·  60 FPS  ·  12 Mbit/s  ·  H.264 (NVENC)",
            RenderFormatting.formatVideo("balanced", 1920, 1080, "60", 12_000, "H.264 (NVENC)"),
        )
        // A job that keeps the source rate has no frame rate. Old entries have no bitrate and can have an old preset id.
        assertEquals(
            "Original quality  ·  2704×1520  ·  Original FPS  ·  H.264 (libx264)",
            RenderFormatting.formatVideo("maximum", 2704, 1520, null, null, "H.264 (libx264)"),
        )
        assertEquals(
            "4K  ·  30 FPS  ·  8.5 Mbit/s  ·  H.264 (QSV)",
            RenderFormatting.formatVideo("unknown-preset", 3840, 2160, "30", 8_500, "H.264 (QSV)"),
        )
        // A size near a standard size keeps its pixels, so the user sees that it is not exactly 1080p.
        assertEquals(
            "1080p (1920×1088)  ·  30 FPS  ·  H.264 (QSV)",
            RenderFormatting.formatVideo(null, 1920, 1088, "30", null, "H.264 (QSV)"),
        )
    }

    @Test
    fun renderFormattingShowsTheWrittenAndTheExpectedSize() {
        assertEquals("120.50 MB / ~480.00 MB", RenderFormatting.formatSizeProgress(120_500_000, 480_000_000))
        assertEquals("0 B", RenderFormatting.formatSizeProgress(0, null))
    }

    @Test
    fun buildRenderPlanEstimatesTheSizeFromTheKeptPointsOrTheSourceDuration() {
        val edl = EdlV1(points = listOf(point("p1", 0, 10_000), point("p2", 20_000, 30_000)))
        fun expectedBytes(idleTrim: Boolean, bitrateK: Int?, sourceDurationMs: Long?) = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest(),
                sourcePath = "input.mp4",
                edl = edl,
                score = ScoreV1(),
                preset = preset,
                resolution = ExportResolution("1080p", 1920, 1080),
                videoBitrateK = bitrateK,
                encoderLabel = "H.264 (libx264)",
                idleTrim = idleTrim,
                favoriteOnly = false,
                includeScoreboard = false,
                outputPath = "out.mp4",
                sourceDurationMs = sourceDurationMs,
            )
        ).job.expectedBytes

        // 20 s of points at 8,000 + 192 kbit/s.
        assertEquals(20_480_000L, expectedBytes(idleTrim = true, bitrateK = 8_000, sourceDurationMs = 60_000))
        assertEquals(61_440_000L, expectedBytes(idleTrim = false, bitrateK = 8_000, sourceDurationMs = 60_000))
        assertNull(expectedBytes(idleTrim = false, bitrateK = 8_000, sourceDurationMs = null))
        assertNull(expectedBytes(idleTrim = true, bitrateK = null, sourceDurationMs = 60_000))
    }

    @Test
    fun theStatisticsCardIsInTheJobAndInTheSizeEstimate() {
        val edl = EdlV1(points = listOf(point("p1", 0, 10_000), point("p2", 20_000, 30_000)))
        fun plan(includeStatsCard: Boolean, score: ScoreV1) = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest(),
                sourcePath = "input.mp4",
                edl = edl,
                score = score,
                preset = preset,
                resolution = ExportResolution("1080p", 1920, 1080),
                videoBitrateK = 8_000,
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                favoriteOnly = false,
                includeScoreboard = false,
                outputPath = "out.mp4",
                includeStatsCard = includeStatsCard,
            )
        ).job
        val scored = ScoreV1(outcomes = mapOf("p1" to Outcome.P1, "p2" to Outcome.P2))

        val job = plan(includeStatsCard = true, scored)

        // The default rows fit on one page. The momentum chart is on a second page.
        assertEquals(2, job.statsCard?.pages?.size)
        assertEquals(1920.0, job.statsCard?.pages?.first()?.width)
        // 20 s of points and 12 s of card at 8,000 + 192 kbit/s.
        assertEquals(32_768_000L, job.expectedBytes)
        assertNull(plan(includeStatsCard = false, scored).statsCard)
        assertNull(plan(includeStatsCard = true, ScoreV1()).statsCard, "Without scored points the export has no card")
    }

    @Test
    fun theSetCardsAreInTheJobForTheSetsThatTheVideoShows() {
        // One game wins a set with a two-game lead: sets of 8 points. Set 3 is not complete.
        val winners = "11111111" + "22222222" + "1111"
        val points = winners.indices.map { point("p${it + 1}", it * 10_000, it * 10_000 + 5_000, favorite = it >= 8) }
        val score = ScoreV1(
            outcomes = points.zip(winners.toList()).associate { (p, c) -> p.id to if (c == '1') Outcome.P1 else Outcome.P2 },
            rules = org.litvin.scoring.MatchRulesV1(gamesPerSet = 1, setTiebreak = false),
        )
        fun job(idleTrim: Boolean, favoriteOnly: Boolean) = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest(),
                sourcePath = "input.mp4",
                edl = EdlV1(points = points),
                score = score,
                preset = preset,
                resolution = ExportResolution("1080p", 1920, 1080),
                videoBitrateK = 8_000,
                encoderLabel = "H.264 (libx264)",
                idleTrim = idleTrim,
                favoriteOnly = favoriteOnly,
                includeScoreboard = false,
                outputPath = "out.mp4",
                sourceDurationMs = 300_000,
                includeSetSummaries = true,
            )
        ).job

        val all = job(idleTrim = true, favoriteOnly = false)
        assertEquals(listOf(1, 2), all.setSummaries.map { it.setNumber })
        // 100 s of points and two cards of 12 s (rows and chart) at 8,000 + 192 kbit/s.
        assertEquals(126_976_000L, all.expectedBytes)
        // The favorites start in set 2, so the video does not show set 1.
        assertEquals(listOf(2), job(idleTrim = true, favoriteOnly = true).setSummaries.map { it.setNumber })
        assertEquals(emptyList(), job(idleTrim = false, favoriteOnly = false).setSummaries)
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
