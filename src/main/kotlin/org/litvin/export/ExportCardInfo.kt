package org.litvin.export

import org.litvin.CompletedRender
import org.litvin.RenderJob

/** The text that an export card on the Export tab shows. The active, queued and completed cards use it. */
data class ExportCardInfo(
    val outputPath: String,
    val projectName: String?,
    val content: String,
    val video: String,
    val size: String,
) {
    companion object {
        fun of(job: RenderJob): ExportCardInfo = ExportCardInfo(
            outputPath = job.outputPath,
            projectName = job.projectName?.takeIf { it.isNotBlank() },
            content = RenderFormatting.formatContent(
                idleTrim = job.idleTrim,
                favoriteOnly = job.favoriteOnly,
                pointCount = job.edlSnapshot.size,
                includeScoreboard = job.includeScoreboard,
                includeComments = job.includeComments,
            ),
            video = RenderFormatting.formatVideo(
                presetId = job.presetId,
                width = job.outWidth,
                height = job.outHeight,
                frameRate = job.outputFrameRate,
                videoBitrateK = job.videoBitrateK,
                encoderLabel = job.encoderLabel,
            ),
            size = RenderFormatting.formatSizeProgress(job.bytesWritten, job.expectedBytes),
        )

        fun of(item: CompletedRender): ExportCardInfo = ExportCardInfo(
            outputPath = item.outputPath,
            projectName = item.projectName?.takeIf { it.isNotBlank() },
            content = RenderFormatting.formatContent(
                idleTrim = item.idleTrim,
                favoriteOnly = item.favoriteOnly,
                pointCount = item.pointCount,
                includeScoreboard = item.includeScoreboard,
                includeComments = item.includeComments,
            ),
            video = RenderFormatting.formatVideo(
                presetId = item.presetId,
                width = item.outWidth,
                height = item.outHeight,
                frameRate = item.outputFrameRate,
                videoBitrateK = item.videoBitrateK,
                encoderLabel = item.encoderLabel,
            ),
            size = RenderFormatting.formatSizeProgress(item.bytesWritten, item.expectedBytes),
        )
    }
}
