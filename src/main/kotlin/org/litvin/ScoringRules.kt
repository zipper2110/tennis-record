package org.litvin.scoring

import org.litvin.markup.PointV1

/**
 * Shared scoring rules engine snapshot generator (v0.1.0, task 3.21).
 * Computes per-point state including tiebreaks (start at 6–6, win-by-2 to 7+).
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
    fun computeSnapshotsBefore(points: List<PointV1>, outcomes: Map<String, Outcome>): List<Snapshot> {
        val n = points.size
        if (n == 0) return emptyList()
        val res = ArrayList<Snapshot>(n)
        var p1Pts = 0
        var p2Pts = 0
        var gamesP1 = 0
        var gamesP2 = 0
        var setsP1 = 0
        var setsP2 = 0
        var isTb = false
        val completed = mutableListOf<Pair<Int, Int>>()

        fun checkRegularGameWin(): Int? {
            if ((p1Pts >= 4 || p2Pts >= 4) && kotlin.math.abs(p1Pts - p2Pts) >= 2) {
                return if (p1Pts > p2Pts) 1 else 2
            }
            return null
        }
        fun addRegularGame(winner: Int) {
            if (winner == 1) gamesP1++ else gamesP2++
            p1Pts = 0; p2Pts = 0
            val lead = kotlin.math.abs(gamesP1 - gamesP2)
            val maxG = kotlin.math.max(gamesP1, gamesP2)
            if (maxG >= 6 && lead >= 2) {
                completed += gamesP1 to gamesP2
                if (gamesP1 > gamesP2) setsP1++ else setsP2++
                gamesP1 = 0; gamesP2 = 0
                isTb = false
            } else if (gamesP1 == 6 && gamesP2 == 6) {
                isTb = true
            }
        }
        fun addTiebreakPoint(winner: Int) {
            if (winner == 1) p1Pts++ else p2Pts++
            val lead = kotlin.math.abs(p1Pts - p2Pts)
            val maxPts = kotlin.math.max(p1Pts, p2Pts)
            if (maxPts >= 7 && lead >= 2) {
                // Finalize tiebreak set as 7–6
                if (p1Pts > p2Pts) completed += 7 to 6 else completed += 6 to 7
                if (p1Pts > p2Pts) setsP1++ else setsP2++
                gamesP1 = 0; gamesP2 = 0
                p1Pts = 0; p2Pts = 0
                isTb = false
            }
        }

        for (i in 0 until n) {
            // Snapshot BEFORE applying outcome of point i
            val snap = Snapshot(
                p1Pts = p1Pts,
                p2Pts = p2Pts,
                gamesP1 = gamesP1,
                gamesP2 = gamesP2,
                setsP1 = setsP1,
                setsP2 = setsP2,
                isTiebreak = isTb,
                tbP1 = if (isTb) p1Pts else 0,
                tbP2 = if (isTb) p2Pts else 0,
                completedSets = completed.toList(),
            )
            res += snap

            // Apply outcome for the next iteration
            if (setsP1 >= 2 || setsP2 >= 2) continue // match finished (best of 3)
            when (outcomes[points[i].id]) {
                Outcome.P1 -> {
                    if (isTb) addTiebreakPoint(1) else {
                        p1Pts++
                        val gw = checkRegularGameWin()
                        if (gw != null) addRegularGame(gw)
                    }
                }
                Outcome.P2 -> {
                    if (isTb) addTiebreakPoint(2) else {
                        p2Pts++
                        val gw = checkRegularGameWin()
                        if (gw != null) addRegularGame(gw)
                    }
                }
                else -> { /* NONE or null → carry-forward */ }
            }
        }
        return res
    }
}
