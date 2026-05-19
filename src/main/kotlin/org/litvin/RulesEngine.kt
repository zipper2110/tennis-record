package org.litvin

import org.litvin.scoring.Outcome

/**
 * Pure tennis rules engine used by Scoring (Task 4.8) and covered by tests in Task 4.14.
 *
 * Features:
 * - Regular games: 0,15,30,40 then deuce/advantage; win-by-2.
 * - Sets: first to 6 with 2-game lead, otherwise tiebreak at 6–6 (standard 7-point win-by-2).
 * - Match: best-of-3 (default) — first to 2 sets wins; timeline keeps final state for remaining points.
 * - Outcome NONE does not change the state but is considered "scored" in UI elsewhere.
 */
object RulesEngine {
    data class MatchState(
        val p1Pts: Int,
        val p2Pts: Int,
        val gamesP1: Int,
        val gamesP2: Int,
        val setsP1: Int,
        val setsP2: Int,
        val lastGameWonBy: Int?,
        val lastSetWonBy: Int?,
        val isTiebreak: Boolean,
    )

    data class SetScore(val p1: Int, val p2: Int, val tiebreak: Boolean)

    /**
     * Compute state after each point from the ordered list of pointIds and outcomes map.
     * Unknown/missing ids in the map are treated as unscored (no change at that step).
     */
    fun computeTimeline(
        pointIds: List<String>,
        outcomesByPointId: Map<String, Outcome>,
        bestOfSets: Int = 3,
    ): Pair<List<MatchState>, List<List<SetScore>>> {
        val result = MutableList(pointIds.size) { MatchState(0, 0, 0, 0, 0, 0, null, null, false) }
        val setsPerPoint = MutableList(pointIds.size) { emptyList<SetScore>() }

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

        val setsToWin = (bestOfSets / 2) + 1

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
                // Completed set (no tiebreak)
                completedSets.add(SetScore(gamesP1, gamesP2, false))
                if (gamesP1 > gamesP2) setsP1++ else setsP2++
                gamesP1 = 0; gamesP2 = 0
                lastSetWonBy = if (setsP1 > setsP2) 1 else 2
                lastGameWonBy = null
                isTiebreak = false
            } else if (gamesP1 == 6 && gamesP2 == 6) {
                // Enter tiebreak
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
                // Tiebreak won; record set as 7–6 for the winner
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

        for (i in pointIds.indices) {
            // If match is already won, keep final snapshot for remaining points
            if (setsP1 >= setsToWin || setsP2 >= setsToWin) {
                result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, false)
                setsPerPoint[i] = completedSets.toList()
                continue
            }
            when (outcomesByPointId[pointIds[i]]) {
                null -> {
                    // Unscored point: carry current state
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                    setsPerPoint[i] = completedSets.toList()
                }
                Outcome.NONE -> {
                    // Scored as NONE: unchanged
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                    setsPerPoint[i] = completedSets.toList()
                }
                Outcome.P1, Outcome.P2 -> {
                    if (isTiebreak) {
                        addTiebreakPoint(if (outcomesByPointId[pointIds[i]] == Outcome.P1) 1 else 2)
                        result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, lastSetWonBy, isTiebreak)
                        setsPerPoint[i] = completedSets.toList()
                    } else {
                        if (outcomesByPointId[pointIds[i]] == Outcome.P1) p1Pts++ else p2Pts++
                        val gw = checkRegularGameWin()
                        if (gw != null) {
                            addRegularGame(gw)
                            result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, gw, lastSetWonBy, isTiebreak)
                            if (lastSetWonBy == null) lastGameWonBy = null
                        } else {
                            lastGameWonBy = null
                            lastSetWonBy = null
                            result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                        }
                        setsPerPoint[i] = completedSets.toList()
                    }
                }
            }
        }
        return Pair(result, setsPerPoint)
    }
}
