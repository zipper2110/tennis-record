package org.litvin.stats

import org.litvin.scoring.Outcome
import org.litvin.scoring.PerPlayer

/** One scored point on the momentum chart. */
data class MomentumPoint(
    val pointId: String,
    /** The number of the point in the project, from 1. */
    val number: Int,
    /** The set of the point, from 1. */
    val set: Int,
    /** 1 or 2. */
    val winner: Int,
    /** The points won by player 1 minus the points won by player 2, from the start of the scope to this point. */
    val difference: Int,
)

/**
 * The point difference over the match or over one set. The line goes up when player 1 wins a point,
 * and down when player 2 wins a point. Points without a winner are not on the chart.
 */
data class Momentum(
    val points: List<MomentumPoint>,
    /** The positions in [points] where a new set starts. The first set has no entry. */
    val setStarts: List<Int>,
) {
    /** The largest lead of each player, for the scale of the chart. */
    val maxLead: PerPlayer<Int> get() = PerPlayer(
        points.maxOfOrNull { it.difference }?.coerceAtLeast(0) ?: 0,
        points.maxOfOrNull { -it.difference }?.coerceAtLeast(0) ?: 0,
    )

    companion object {
        /** The momentum of [scope]: 0 is the full match, 1 and more are the sets. */
        fun of(report: StatsReport, scope: Int = 0): Momentum {
            val ranges = report.setRanges
            if (ranges.isEmpty()) return Momentum(emptyList(), emptyList())
            val range = if (scope == 0) ranges.first().first..ranges.last().last else ranges[scope - 1]
            val points = mutableListOf<MomentumPoint>()
            val setStarts = mutableListOf<Int>()
            var difference = 0
            var lastSet = 0
            for (index in range) {
                val point = report.points[index]
                val winner = when (report.score.outcomes[point.id]) {
                    Outcome.P1 -> 1
                    Outcome.P2 -> 2
                    else -> null
                } ?: continue
                val set = ranges.indexOfFirst { index in it } + 1
                if (lastSet != 0 && set != lastSet) setStarts += points.size
                lastSet = set
                difference += if (winner == 1) 1 else -1
                points += MomentumPoint(point.id, index + 1, set, winner, difference)
            }
            return Momentum(points, setStarts)
        }
    }
}
