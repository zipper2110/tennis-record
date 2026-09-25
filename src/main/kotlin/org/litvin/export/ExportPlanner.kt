package org.litvin.export

import org.litvin.ExportPreset
import org.litvin.OverlaySpan
import org.litvin.RenderJob
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.CommentOverlaySpan
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.ScoreV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import org.litvin.stats.StatsSettingsV1
import java.io.File

data class ExportResolution(
    val label: String,
    val width: Int,
    val height: Int,
)

data class ExportPointSummary(
    val pointCount: Int,
    val favoriteCount: Int,
    val totalMs: Long,
    val favoriteTotalMs: Long,
    val scoredCount: Int,
    val scoredTotalMs: Long = 0,
    /** Points that an "Only points" export keeps: no empty or overlapping points. */
    val validPointCount: Int = pointCount,
    val validTotalMs: Long = totalMs,
) {
    val allScored: Boolean
        get() = pointCount > 0 && scoredCount == pointCount
}

data class ExportReadiness(
    val enabled: Boolean,
    val disabledReason: String? = null,
)

data class ExportRenderPlan(
    val job: RenderJob,
    val allValidPoints: List<PointV1>,
    val keptPoints: List<PointV1>,
    val effectiveIdleTrim: Boolean,
    val overlayTimeline: List<OverlaySpan>,
    val commentOverlayTimeline: List<CommentOverlaySpan>,
)

data class ExportRenderPlanRequest(
    val manifest: ProjectManifestV1?,
    val sourcePath: String,
    val edl: EdlV1?,
    val score: ScoreV1,
    val preset: ExportPreset,
    val resolution: ExportResolution,
    val outputFrameRate: String? = null,
    val videoBitrateK: Int? = null,
    val encoderLabel: String,
    val idleTrim: Boolean,
    val favoriteOnly: Boolean,
    val includeScoreboard: Boolean,
    val outputPath: String,
    val includeComments: Boolean = false,
    /** Duration of the source video. A full video export uses it for the expected file size. */
    val sourceDurationMs: Long? = null,
    /** Adds the statistics card after the last point. */
    val includeStatsCard: Boolean = false,
    /** Adds a statistics card after the last exported point of each completed set. Only an export of points uses it. */
    val includeSetSummaries: Boolean = false,
    /** The rows of the statistics cards. */
    val statsSettings: StatsSettingsV1 = StatsSettingsV1(),
)

object ExportPlanner {
    fun validateEdl(raw: EdlV1?): List<PointV1> {
        if (raw == null) return emptyList()
        val points = raw.points.sortedBy { it.startMs }
        val keeps = ArrayList<PointV1>()
        var lastEnd = -1
        for (point in points) {
            if (point.startMs >= point.endMs) continue
            if (lastEnd >= 0 && point.startMs < lastEnd) continue
            keeps += point
            lastEnd = point.endMs
        }
        return keeps
    }

    fun summarize(edl: EdlV1?, score: ScoreV1): ExportPointSummary {
        val points = edl?.points ?: emptyList()
        val validPoints = validateEdl(edl)
        val validFavorites = validPoints.filter { it.favorite }
        val outcomes = score.outcomes
        val scored = points.filter { point -> outcomes.containsKey(point.id) }
        return ExportPointSummary(
            pointCount = points.size,
            favoriteCount = validFavorites.size,
            totalMs = points.sumOf { (it.endMs - it.startMs).coerceAtLeast(0).toLong() },
            favoriteTotalMs = validFavorites.sumOf { (it.endMs - it.startMs).toLong() },
            scoredCount = scored.size,
            scoredTotalMs = scored.sumOf { (it.endMs - it.startMs).coerceAtLeast(0).toLong() },
            validPointCount = validPoints.size,
            validTotalMs = validPoints.sumOf { (it.endMs - it.startMs).toLong() },
        )
    }

    fun selectedKeepPoints(
        validPoints: List<PointV1>,
        idleTrim: Boolean,
        favoriteOnly: Boolean,
    ): List<PointV1> {
        if (!idleTrim) return emptyList()
        return if (favoriteOnly) validPoints.filter { it.favorite } else validPoints
    }

    fun effectiveIdleTrim(idleTrim: Boolean, keptPoints: List<PointV1>): Boolean {
        return idleTrim && keptPoints.isNotEmpty()
    }

    fun parseResolution(selection: String): ExportResolution {
        val normalized = selection.removeSuffix(" (source)")
        return when (normalized) {
            "4K" -> ExportResolution(normalized, 3840, 2160)
            "1080p" -> ExportResolution(normalized, 1920, 1080)
            else -> {
                val parts = normalized.lowercase().split("x")
                if (parts.size == 2) {
                    ExportResolution(
                        label = normalized,
                        width = parts[0].toIntOrNull() ?: 1920,
                        height = parts[1].toIntOrNull() ?: 1080,
                    )
                } else {
                    ExportResolution(normalized, 1920, 1080)
                }
            }
        }
    }

    fun suggestFilename(
        projectName: String,
        presetId: String,
        resolutionLabel: String,
        defaultExtNoDot: String = "mp4",
    ): String {
        val base = projectName.ifBlank { "export" }
        val dims = resolutionLabel.replace('x', 'p')
        return "$base-${presetId.lowercase()}-$dims.$defaultExtNoDot"
    }

    fun ensureExtension(file: File, defaultExtNoDot: String = "mp4"): File {
        val safeName = file.name.trim().trimEnd('.')
        if (safeName.isEmpty()) return File(file.parentFile, "export.$defaultExtNoDot")
        return if (safeName.contains('.')) {
            File(file.parentFile, safeName)
        } else {
            File(file.parentFile, "$safeName.$defaultExtNoDot")
        }
    }

    fun initializationReadiness(
        hasProject: Boolean,
        sourceVideoExists: Boolean,
        idleTrim: Boolean,
        favoriteOnly: Boolean,
        validPoints: List<PointV1>,
    ): ExportReadiness {
        if (!hasProject) {
            return ExportReadiness(false, "Open a project first (Projects -> Open).")
        }
        if (!sourceVideoExists) {
            return ExportReadiness(false, "Source video not found. Set it in Projects/Points.")
        }
        if (!idleTrim) return ExportReadiness(true)

        val keptPoints = selectedKeepPoints(validPoints, idleTrim = true, favoriteOnly = favoriteOnly)
        return when {
            favoriteOnly && keptPoints.isEmpty() -> ExportReadiness(
                false,
                "No valid favorite points are available. Mark a point as favorite, or select Only points.",
            )
            keptPoints.isEmpty() -> ExportReadiness(
                false,
                "The project has no valid points. Mark points on the Points tab, or select Full video.",
            )
            else -> ExportReadiness(true)
        }
    }

    /**
     * The set cards that an export of [keptPoints] shows. A set without a kept point has no card,
     * because the video does not show that set.
     */
    fun setSummaryCards(
        edl: EdlV1?,
        score: ScoreV1,
        settings: StatsSettingsV1,
        keptPoints: List<PointV1>,
        outWidth: Int,
        outHeight: Int,
    ): List<SetSummaryCard> {
        if (keptPoints.isEmpty()) return emptyList()
        val keptIds = keptPoints.mapTo(HashSet()) { it.id }
        return StatsCardVideo.setSummaries(edl, score, settings, outWidth, outHeight)
            .filter { summary -> summary.pointIds.any { it in keptIds } }
    }

    fun buildRenderPlan(request: ExportRenderPlanRequest): ExportRenderPlan {
        val allValidPoints = validateEdl(request.edl)
        val keptPoints = selectedKeepPoints(
            validPoints = allValidPoints,
            idleTrim = request.idleTrim,
            favoriteOnly = request.favoriteOnly,
        )
        val effectiveIdleTrim = effectiveIdleTrim(request.idleTrim, keptPoints)
        val overlayTimeline = if (request.includeScoreboard) {
            buildOverlayTimeline(
                edl = request.edl,
                score = request.score,
                allValidPoints = allValidPoints,
                keptPoints = keptPoints,
                effectiveIdleTrim = effectiveIdleTrim,
            )
        } else {
            emptyList()
        }
        val commentOverlayTimeline = if (request.includeComments) {
            buildCommentOverlayTimeline(
                comments = request.edl?.comments.orEmpty(),
                keptPoints = keptPoints,
                idleTrim = effectiveIdleTrim,
            )
        } else {
            emptyList()
        }
        val statsCard = if (request.includeStatsCard) {
            StatsCardVideo.of(request.edl, request.score, request.statsSettings, request.resolution.width, request.resolution.height)
        } else {
            null
        }
        val setSummaries = if (request.includeSetSummaries && effectiveIdleTrim) {
            setSummaryCards(request.edl, request.score, request.statsSettings, keptPoints, request.resolution.width, request.resolution.height)
        } else {
            emptyList()
        }
        val cardsMs = (statsCard?.durationMs ?: 0L) + setSummaries.sumOf { it.card.durationMs }
        val outputDurationMs = if (effectiveIdleTrim) {
            ExportChunkPlanner.keptDurationMs(keptPoints)
        } else {
            request.sourceDurationMs
        }?.let { it + cardsMs }
        val expectedBytes = request.videoBitrateK?.let { bitrateK ->
            outputDurationMs?.let { ExportVideoOptions.estimatedBytes(bitrateK, it) }
        }

        val job = RenderJob(
            projectId = request.manifest?.id,
            projectName = request.manifest?.name,
            sourcePath = request.sourcePath,
            edlSnapshot = if (request.idleTrim) keptPoints else emptyList(),
            presetId = request.preset.id,
            outWidth = request.resolution.width,
            outHeight = request.resolution.height,
            outputFrameRate = request.outputFrameRate,
            videoBitrateK = request.videoBitrateK,
            expectedBytes = expectedBytes,
            encoderLabel = request.encoderLabel,
            idleTrim = effectiveIdleTrim,
            favoriteOnly = request.favoriteOnly,
            includeScoreboard = request.includeScoreboard,
            overlayTimeline = overlayTimeline,
            scoreboardSettings = request.score.scoreboard,
            outputPath = request.outputPath,
            includeComments = request.includeComments,
            commentOverlayTimeline = commentOverlayTimeline,
            statsCard = statsCard,
            setSummaries = setSummaries,
        )

        return ExportRenderPlan(
            job = job,
            allValidPoints = allValidPoints,
            keptPoints = keptPoints,
            effectiveIdleTrim = effectiveIdleTrim,
            overlayTimeline = overlayTimeline,
            commentOverlayTimeline = commentOverlayTimeline,
        )
    }

    fun buildCommentOverlayTimeline(
        comments: List<CommentV1>,
        keptPoints: List<PointV1>,
        idleTrim: Boolean,
    ): List<CommentOverlaySpan> {
        val validComments = comments
            .asSequence()
            .filter { comment ->
                comment.id > 0 &&
                    comment.startMs >= 0 &&
                    comment.durationMs > 0 &&
                    comment.text.isNotBlank() &&
                    EdlIO.normalizeColorHex(comment.colorHex) != null
            }
            .sortedWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id })
            .toList()
        if (!idleTrim) {
            return validComments.map { comment ->
                CommentOverlaySpan(
                    id = comment.id,
                    startMs = comment.startMs.toLong(),
                    endMs = comment.startMs.toLong() + comment.durationMs.toLong(),
                    text = comment.text,
                    colorHex = EdlIO.normalizeColorHex(comment.colorHex)!!,
                )
            }
        }

        val orderedPoints = keptPoints.sortedBy { it.startMs }
        return validComments.mapNotNull { comment ->
            var elapsedMs = 0L
            for (point in orderedPoints) {
                if (point.startMs <= comment.startMs && comment.startMs < point.endMs) {
                    val startMs = elapsedMs + (comment.startMs - point.startMs).toLong()
                    return@mapNotNull CommentOverlaySpan(
                        id = comment.id,
                        startMs = startMs,
                        endMs = startMs + comment.durationMs.toLong(),
                        text = comment.text,
                        colorHex = EdlIO.normalizeColorHex(comment.colorHex)!!,
                    )
                }
                elapsedMs += (point.endMs - point.startMs).toLong().coerceAtLeast(0L)
            }
            null
        }
    }

    private fun buildOverlayTimeline(
        edl: EdlV1?,
        score: ScoreV1,
        allValidPoints: List<PointV1>,
        keptPoints: List<PointV1>,
        effectiveIdleTrim: Boolean,
    ): List<OverlaySpan> {
        val scoringPoints = allValidPoints.ifEmpty { edl?.points ?: emptyList() }
        val overlayPoints = if (effectiveIdleTrim) keptPoints else scoringPoints
        return ScoreboardTimelineBuilder.build(
            points = scoringPoints,
            outcomes = score.outcomes,
            idleTrim = effectiveIdleTrim,
            player1Name = score.player1Name,
            player2Name = score.player2Name,
            player1ColorHex = score.player1ColorHex,
            player2ColorHex = score.player2ColorHex,
            rules = score.rules,
            manualMarks = score.manualMarks(),
            serverMarks = score.serverMarks,
            exportedPointIds = if (effectiveIdleTrim && overlayPoints.isNotEmpty()) {
                overlayPoints.map { it.id }.toSet()
            } else {
                null
            },
        )
    }
}
