package org.litvin.stats

import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.EdlV1
import org.litvin.scoring.ScoreV1

/**
 * The statistics card of one export: its pages, and how long the video shows each page.
 * The pages have the aspect ratio of the output frame.
 */
data class StatsCardVideo(
    val pages: List<ScoreboardScene>,
    val pageDurationMs: Long = PAGE_DURATION_MS,
) {
    val durationMs: Long get() = pages.size * pageDurationMs

    companion object {
        const val PAGE_DURATION_MS = 6_000L

        /**
         * Makes the card for an export of [outWidth] x [outHeight].
         * Returns null when the card has no pages, for example when no row has a value.
         */
        fun of(edl: EdlV1?, score: ScoreV1, settings: StatsSettingsV1, outWidth: Int, outHeight: Int): StatsCardVideo? {
            if (edl == null || outWidth <= 0 || outHeight <= 0) return null
            val report = StatsReport.build(edl, score)
            if (!report.hasScoredPoints) return null
            val frameWidth = StatsCard.HEIGHT * outWidth / outHeight
            val pages = StatsCard.pages(StatsCard.content(report, settings), frameWidth)
            return if (pages.isEmpty()) null else StatsCardVideo(pages)
        }

        /**
         * Makes one card for each completed set, for an export of [outWidth] x [outHeight].
         * A match with only one set has no set cards, because the match card shows the same statistics.
         */
        fun setSummaries(
            edl: EdlV1?,
            score: ScoreV1,
            settings: StatsSettingsV1,
            outWidth: Int,
            outHeight: Int,
        ): List<SetSummaryCard> {
            if (edl == null || outWidth <= 0 || outHeight <= 0) return emptyList()
            val report = StatsReport.build(edl, score)
            if (!report.hasScoredPoints || report.setRanges.size < 2) return emptyList()
            val frameWidth = StatsCard.HEIGHT * outWidth / outHeight
            return report.setRanges.mapIndexedNotNull { index, range ->
                val completed = report.timeline.setsAfterPoint[range.last].getOrNull(index) != null
                if (!completed) return@mapIndexedNotNull null
                val pages = StatsCard.pages(StatsCard.content(report, settings, scope = index + 1), frameWidth)
                if (pages.isEmpty()) return@mapIndexedNotNull null
                SetSummaryCard(
                    setNumber = index + 1,
                    pointIds = range.map { report.points[it].id }.toSet(),
                    card = StatsCardVideo(pages),
                )
            }
        }
    }
}

/** The statistics card of one completed set. The export shows it after the last exported point of [pointIds]. */
data class SetSummaryCard(
    val setNumber: Int,
    val pointIds: Set<String>,
    val card: StatsCardVideo,
)
