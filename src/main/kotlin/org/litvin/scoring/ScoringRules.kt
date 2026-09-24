package org.litvin.scoring

import org.litvin.points.PointV1

/**
 * Shared scoring rules engine snapshot generator (v0.1.0, task 3.21).
 * Computes per-point state including tiebreaks. [ScoringEngine] applies the match rules.
 */
object ScoringRules {
    data class Snapshot(
        val p1Pts: Int,
        val p2Pts: Int,
        val gamesP1: Int,
        val gamesP2: Int,
        val setsP1: Int,
        val setsP2: Int,
        val isTiebreak: Boolean,
        val tbP1: Int,
        val tbP2: Int,
        val completedSets: List<Pair<Int, Int>>,
    )

    /**
     * Returns a list of state snapshots BEFORE each point outcome is applied.
     * Length equals points.size. Outcomes can be missing or NONE → carry-forward.
     */
    fun computeSnapshotsBefore(
        points: List<PointV1>,
        outcomes: Map<String, Outcome>,
        rules: MatchRulesV1 = MatchRulesV1(),
        manualMarks: ManualScoreMarks = ManualScoreMarks(),
    ): List<Snapshot> {
        if (points.isEmpty()) return emptyList()
        val timeline = ScoringEngine.timeline(points, outcomes, rules, manualMarks)
        return points.indices.map { i ->
            val state = if (i == 0) timeline.initial else timeline.statesAfterPoint[i - 1]
            val sets = if (i == 0) emptyList() else timeline.setsAfterPoint[i - 1]
            Snapshot(
                p1Pts = state.p1Pts,
                p2Pts = state.p2Pts,
                gamesP1 = state.gamesP1,
                gamesP2 = state.gamesP2,
                setsP1 = state.setsP1,
                setsP2 = state.setsP2,
                isTiebreak = state.isTiebreak,
                tbP1 = if (state.isTiebreak) state.p1Pts else 0,
                tbP2 = if (state.isTiebreak) state.p2Pts else 0,
                completedSets = sets.map { it.p1 to it.p2 },
            )
        }
    }
}
