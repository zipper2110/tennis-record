package org.litvin.export

import org.litvin.points.PointV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo

/**
 * Puts the encode passes of one export in output order: the video chunks, the set cards after
 * the last exported point of each set, and the match card at the end.
 *
 * A card pass shows a frozen frame of the source. The scoreboard and the comments stay on the
 * video timeline, so [ExportChunkPlanner.Chunk.outputOffsetMs] does not include the cards.
 */
object ExportPassPlanner {
    sealed interface Pass {
        /** Output time where this pass starts, with the cards before it. The progress bar uses it. */
        val progressBaseMs: Long
    }

    data class Video(
        val chunk: ExportChunkPlanner.Chunk,
        override val progressBaseMs: Long,
    ) : Pass

    data class Card(
        val card: StatsCardVideo,
        /** Source time just after the frozen frame, or null for the end of the source. */
        val freezeAtMs: Long?,
        override val progressBaseMs: Long,
    ) : Pass

    /**
     * Plans the passes. Without [keeps] the export is the full video: one video pass, and no set cards.
     * [fullVideoDurationMs] is the source duration for a full video export, when it is known.
     */
    fun plan(
        keeps: List<PointV1>,
        setSummaries: List<SetSummaryCard> = emptyList(),
        endCard: StatsCardVideo? = null,
        fullVideoDurationMs: Long? = null,
        maxSegmentsPerChunk: Int = ExportChunkPlanner.maxSegmentsPerChunk(),
    ): List<Pass> {
        val passes = mutableListOf<Pass>()
        if (keeps.isEmpty()) {
            passes += Video(ExportChunkPlanner.Chunk(emptyList(), 0L), 0L)
            endCard?.let { passes += Card(it, freezeAtMs = null, progressBaseMs = fullVideoDurationMs ?: 0L) }
            return passes
        }

        // The set cards after each kept point. A set without a kept point has no card.
        val cardsAfter = setSummaries
            .sortedBy { it.setNumber }
            .mapNotNull { summary ->
                val last = keeps.indexOfLast { it.id in summary.pointIds }
                if (last < 0) null else last to summary.card
            }
            .groupBy({ it.first }, { it.second })

        var videoMs = 0L
        var cardMs = 0L
        var groupStart = 0
        fun addVideo(until: Int) {
            if (until <= groupStart) return
            keeps.subList(groupStart, until).chunked(maxSegmentsPerChunk.coerceAtLeast(1)).forEach { group ->
                passes += Video(ExportChunkPlanner.Chunk(group, videoMs), videoMs + cardMs)
                videoMs += ExportChunkPlanner.keptDurationMs(group)
            }
            groupStart = until
        }
        fun addCard(card: StatsCardVideo, atMs: Long) {
            passes += Card(card, atMs, videoMs + cardMs)
            cardMs += card.durationMs
        }

        for ((last, cards) in cardsAfter.toSortedMap()) {
            addVideo(last + 1)
            cards.forEach { addCard(it, keeps[last].endMs.toLong()) }
        }
        addVideo(keeps.size)
        endCard?.let { addCard(it, keeps.last().endMs.toLong()) }
        return passes
    }

    /** The duration of all cards in [passes]. */
    fun cardDurationMs(passes: List<Pass>): Long = passes.filterIsInstance<Card>().sumOf { it.card.durationMs }
}
