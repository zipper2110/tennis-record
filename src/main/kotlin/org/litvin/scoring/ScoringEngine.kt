package org.litvin.scoring

import org.litvin.markup.PointV1

/**
 * Pure scoring computation engine.
 *
 * Given the chronological list of points and a map of outcomes per point id, it computes:
 * - MatchState snapshot after each point
 * - History of completed sets up to each point
 *
 * No side effects; suitable for reuse by different UIs.
 */
object ScoringEngine {
    // Computed match state snapshot for current selection/index
    data class MatchState(
        val p1Pts: Int, // internal tennis points counter (0..N) or tiebreak points when isTiebreak = true
        val p2Pts: Int,
        val gamesP1: Int,
        val gamesP2: Int,
        val setsP1: Int,
        val setsP2: Int,
        val lastGameWonBy: Int?, // 1,2 or null
        val lastSetWonBy: Int?,  // 1,2 or null
        val isTiebreak: Boolean
    ) {
        companion object {
            val INITIAL = MatchState(0, 0, 0, 0, 0, 0, null, null, false)
        }
    }

    data class SetScore(val p1: Int, val p2: Int, val tiebreak: Boolean)

    /**
     * Compute timeline snapshots for the whole match based on outcomes applied to points order.
     * Returns Pair(statesAfterPoint, setHistoryAfterPoint)
     */
    fun computeTimeline(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>
    ): Pair<MutableList<MatchState>, MutableList<List<SetScore>>> {
        val result = MutableList(points.size) { MatchState(0, 0, 0, 0, 0, 0, null, null, false) }
        val setsPerPoint = MutableList(points.size) { emptyList<SetScore>() }
        var p1Pts = 0
        var p2Pts = 0
        var gamesP1 = 0
        var gamesP2 = 0
        var setsP1 = 0
        var setsP2 = 0
        var lastGameWonBy: Int? = null
        var lastSetWonBy: Int? = null
        var isTiebreak = false
        val completedSets = mutableListOf<SetScore>()

        fun checkRegularGameWin(): Int? {
            if ((p1Pts >= 4 || p2Pts >= 4) && kotlin.math.abs(p1Pts - p2Pts) >= 2) {
                return if (p1Pts > p2Pts) 1 else 2
            }
            return null
        }

        fun addRegularGame(winner: Int) {
            if (winner == 1) gamesP1++ else gamesP2++
            p1Pts = 0; p2Pts = 0
            lastGameWonBy = winner
            val lead = kotlin.math.abs(gamesP1 - gamesP2)
            val maxGames = kotlin.math.max(gamesP1, gamesP2)
            if (maxGames >= 6 && lead >= 2) {
                // Record completed set score before reset
                completedSets.add(SetScore(gamesP1, gamesP2, false))
                if (gamesP1 > gamesP2) setsP1++ else setsP2++
                gamesP1 = 0; gamesP2 = 0
                lastSetWonBy = if (setsP1 > setsP2) 1 else 2
                lastGameWonBy = null
                isTiebreak = false
            } else if (gamesP1 == 6 && gamesP2 == 6) {
                isTiebreak = true
                lastGameWonBy = null
                lastSetWonBy = null
            } else {
                lastSetWonBy = null
            }
        }

        fun addTiebreakPoint(winner: Int) {
            if (winner == 1) p1Pts++ else p2Pts++
            val lead = kotlin.math.abs(p1Pts - p2Pts)
            val maxPts = kotlin.math.max(p1Pts, p2Pts)
            if (maxPts >= 7 && lead >= 2) {
                // Record tiebreak set as 7–6 for the winner
                if (p1Pts > p2Pts) completedSets.add(SetScore(7, 6, true)) else completedSets.add(SetScore(6, 7, true))
                if (p1Pts > p2Pts) setsP1++ else setsP2++
                gamesP1 = 0; gamesP2 = 0
                lastSetWonBy = if (setsP1 > setsP2) 1 else 2
                lastGameWonBy = null
                p1Pts = 0; p2Pts = 0
                isTiebreak = false
            } else {
                lastSetWonBy = null
            }
        }

        for (i in points.indices) {
            // If match is already won (best‑of‑3), keep final state snapshots for remaining points
            if (setsP1 >= 2 || setsP2 >= 2) {
                result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, false)
                setsPerPoint[i] = completedSets.toList()
                continue
            }
            val outcome = outcomesByPointId[points[i].id]
            if (outcome == null) {
                // Unscored point: carry current state
                result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                setsPerPoint[i] = completedSets.toList()
                continue
            }
            if (outcome == Outcome.NONE) {
                // Scored as NONE: state unchanged
                result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                setsPerPoint[i] = completedSets.toList()
                continue
            }
            if (isTiebreak) {
                addTiebreakPoint(if (outcome == Outcome.P1) 1 else 2)
                result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, lastSetWonBy, isTiebreak)
                setsPerPoint[i] = completedSets.toList()
                // After set win via tiebreak we reset pts/isTiebreak above; snapshot next loop will reflect reset
            } else {
                if (outcome == Outcome.P1) p1Pts++ else p2Pts++
                val gw = checkRegularGameWin()
                if (gw != null) {
                    addRegularGame(gw)
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, gw, lastSetWonBy, isTiebreak)
                    // Clear last markers if not a set win (so UI shows transient press)
                    if (lastSetWonBy == null) lastGameWonBy = null
                } else {
                    lastGameWonBy = null
                    lastSetWonBy = null
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                }
                setsPerPoint[i] = completedSets.toList()
            }
        }
        return Pair(result, setsPerPoint)
    }
}