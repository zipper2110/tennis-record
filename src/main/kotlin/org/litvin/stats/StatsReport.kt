package org.litvin.stats

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.MatchStats
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringEngine

/** The statistics of a project: for the full match, and for each set. */
data class StatsReport(
    val score: ScoreV1,
    /** The points in time order, as the Scoring tab uses them. */
    val points: List<PointV1>,
    val timeline: ScoringEngine.Timeline,
    val match: MatchStats,
    /** The point range of each set. See [MatchStats.setRanges]. */
    val setRanges: List<IntRange>,
    val sets: List<MatchStats>,
    /** The points after the point that wins the match. The statistics do not use them. */
    val pointsAfterMatchEnd: Int = 0,
) {
    val hasScoredPoints: Boolean get() = match.scoredPoints > 0

    /**
     * The score of each set, for example "6-4", "7-6" or "[10-7]" for a tiebreak that replaces a set.
     * A set that is not complete shows its current games.
     */
    fun setScores(): List<String> = setRanges.mapIndexed { index, range ->
        val completed = timeline.setsAfterPoint[range.last].getOrNull(index)
        when {
            completed == null -> timeline.statesAfterPoint[range.last].let { "${it.gamesP1}-${it.gamesP2}" }
            completed.tiebreak && completed.p1 + completed.p2 == 1 -> sets[index].pointsWon.let { "[${it.p1}-${it.p2}]" }
            else -> "${completed.p1}-${completed.p2}"
        }
    }

    companion object {
        fun build(edl: EdlV1, score: ScoreV1): StatsReport {
            val points = edl.points.sortedBy { it.startMs }
            val rules = score.rules.normalized()
            val serverMarks = score.serverMarks.filterValues { it != Outcome.NONE }
            val timeline = ScoringEngine.timeline(points, score.outcomes, rules, score.manualMarks(), serverMarks)
            val matchRange = 0..(MatchStats.matchEndIndex(timeline, rules) ?: points.lastIndex)
            val setRanges = MatchStats.setRanges(timeline, matchRange)
            return StatsReport(
                score = score,
                points = points,
                timeline = timeline,
                match = MatchStats.compute(points, score.outcomes, timeline, rules, matchRange),
                setRanges = setRanges,
                sets = setRanges.map { MatchStats.compute(points, score.outcomes, timeline, rules, it) },
                pointsAfterMatchEnd = points.size - (matchRange.last + 1),
            )
        }

        fun load(projectDir: String): StatsReport =
            build(EdlIO.readForProjectDir(projectDir), ScoreIO.readForProjectDir(projectDir))
    }
}
