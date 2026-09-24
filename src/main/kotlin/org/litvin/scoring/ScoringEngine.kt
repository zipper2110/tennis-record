package org.litvin.scoring

import org.litvin.points.PointV1
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure scoring computation engine.
 *
 * Given the chronological list of points, the outcome of each point and the match rules, it computes:
 * - MatchState snapshot after each point
 * - History of completed sets up to each point
 * - The server of each point
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
     * The state before the first point, the state after each point, and the completed sets after each point.
     * [serverOfPoint] is the player (1 or 2) who serves each point, or null when the user did not mark a server.
     */
    data class Timeline(
        val initial: MatchState,
        val statesAfterPoint: List<MatchState>,
        val setsAfterPoint: List<List<SetScore>>,
        val serverOfPoint: List<Int?> = List(statesAfterPoint.size) { null },
    )

    /**
     * Compute timeline snapshots for the whole match based on outcomes applied to points order.
     * Returns Pair(statesAfterPoint, setHistoryAfterPoint)
     */
    fun computeTimeline(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>,
        rules: MatchRulesV1 = MatchRulesV1(),
        manualMarks: ManualScoreMarks = ManualScoreMarks(),
    ): Pair<MutableList<MatchState>, MutableList<List<SetScore>>> {
        val timeline = timeline(points, outcomesByPointId, rules, manualMarks)
        return Pair(timeline.statesAfterPoint.toMutableList(), timeline.setsAfterPoint.toMutableList())
    }

    /**
     * Computes the full [Timeline]. Missing and [Outcome.NONE] outcomes keep the score.
     *
     * Serve: [serverMarks] holds the server that the user marked on a point. Without marks, no point has a server.
     * With marks, every point has a server:
     * - The server changes after each game. In a tiebreak, the first server serves one point,
     *   then the players serve two points each. The player who received first in a set tiebreak
     *   serves the first game of the next set.
     * - A mark sets the server of its point. The next points of the same game (or tiebreak) continue from the mark.
     * - The points before the first mark get the servers that lead to the first mark.
     */
    fun timeline(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>,
        rules: MatchRulesV1 = MatchRulesV1(),
        manualMarks: ManualScoreMarks = ManualScoreMarks(),
        serverMarks: Map<String, Outcome> = emptyMap(),
    ): Timeline {
        val match = Match(rules.normalized())
        val initial = match.snapshot()
        val states = ArrayList<MatchState>(points.size)
        val sets = ArrayList<List<SetScore>>(points.size)
        val servers = ArrayList<Int>(points.size)
        var marked = false
        for (point in points) {
            match.startPoint()
            serverMarks[point.id]?.playerNumber()?.let { server ->
                // Before the first mark, the servers are a guess that starts with player 1. Swap them when the guess is wrong.
                if (!marked && match.server() != server) servers.replaceAll(::otherPlayer)
                marked = true
                match.setServer(server)
            }
            servers += match.server()
            when (outcomesByPointId[point.id]) {
                Outcome.P1 -> match.pointWonBy(1)
                Outcome.P2 -> match.pointWonBy(2)
                Outcome.NONE, null -> Unit
            }
            if (match.rules.manualScoring) {
                manualMarks.gameWins[point.id]?.playerNumber()?.let(match::markGameWon)
                manualMarks.setWins[point.id]?.playerNumber()?.let(match::markSetWon)
            }
            states += match.snapshot()
            sets += match.completedSets.toList()
        }
        return Timeline(initial, states, sets, if (marked) servers else servers.map { null })
    }

    /** The tiebreak target of [MatchStructure.PLAIN_POINTS]. No score gets to it, so the tiebreak does not end. */
    private const val ENDLESS_TIEBREAK_TARGET = Int.MAX_VALUE

    private fun otherPlayer(player: Int): Int = 3 - player

    private fun Outcome.playerNumber(): Int? = when (this) {
        Outcome.P1 -> 1
        Outcome.P2 -> 2
        Outcome.NONE -> null
    }

    /** Mutable match score. It applies the point outcomes one by one. */
    private class Match(val rules: MatchRulesV1) {
        var p1Pts = 0
        var p2Pts = 0
        var gamesP1 = 0
        var gamesP2 = 0
        var setsP1 = 0
        var setsP2 = 0
        var isTiebreak = false

        /** The points that win the current tiebreak. */
        var tiebreakTarget = rules.tiebreakPoints

        /** True for a match tiebreak or a single tiebreak: its winner gets a 1–0 set, not a game. */
        var tiebreakReplacesSet = false

        /** The server of the current game, or the first server of the current tiebreak. */
        var gameServer = 1
        var matchWonBy: Int? = null
        var gameWonBy: Int? = null
        var setWonBy: Int? = null
        val completedSets = mutableListOf<SetScore>()

        init {
            if (!rules.manualScoring) {
                when (rules.structure) {
                    MatchStructure.SINGLE_TIEBREAK -> startTiebreak(rules.tiebreakPoints, replacesSet = true)
                    MatchStructure.PLAIN_POINTS -> startTiebreak(ENDLESS_TIEBREAK_TARGET, replacesSet = true)
                    MatchStructure.SETS, MatchStructure.GAMES_ONLY -> Unit
                }
            }
        }

        fun startPoint() {
            gameWonBy = null
            setWonBy = null
        }

        fun pointWonBy(player: Int) {
            if (matchWonBy != null) return
            if (player == 1) p1Pts++ else p2Pts++
            if (rules.manualScoring) return
            if (isTiebreak) checkTiebreak() else checkGame()
        }

        fun markGameWon(player: Int) {
            addGame(player)
            p1Pts = 0; p2Pts = 0
            gameWonBy = player
            changeServer()
        }

        /** The server of the next point. */
        fun server(): Int = if (isTiebreak && tiebreakReceiverServes()) otherPlayer(gameServer) else gameServer

        /** Makes [player] the server of the next point. The rest of the game or tiebreak continues from it. */
        fun setServer(player: Int) {
            gameServer = if (isTiebreak && tiebreakReceiverServes()) otherPlayer(player) else player
        }

        /** Tiebreak: the first server serves point 1, then each player serves two points (2–3, 4–5, ...). */
        private fun tiebreakReceiverServes(): Boolean = ((p1Pts + p2Pts + 1) / 2) % 2 == 1

        /** After a game, the other player serves. After a tiebreak, the player who received first serves. */
        private fun changeServer() {
            gameServer = otherPlayer(gameServer)
        }

        fun markSetWon(player: Int) {
            completeSet(player, SetScore(gamesP1, gamesP2, false))
        }

        fun snapshot() = MatchState(
            p1Pts = p1Pts,
            p2Pts = p2Pts,
            gamesP1 = gamesP1,
            gamesP2 = gamesP2,
            setsP1 = setsP1,
            setsP2 = setsP2,
            lastGameWonBy = gameWonBy,
            lastSetWonBy = setWonBy,
            isTiebreak = isTiebreak,
        )

        private fun checkGame() {
            val lead = abs(p1Pts - p2Pts)
            val top = max(p1Pts, p2Pts)
            val won = when (rules.deuce) {
                DeuceRule.ADVANTAGE -> top >= 4 && lead >= 2
                DeuceRule.NO_AD -> top >= 4
            }
            if (won) winGame(if (p1Pts > p2Pts) 1 else 2)
        }

        private fun winGame(player: Int) {
            markGameWon(player)
            if (rules.structure != MatchStructure.SETS) return
            val lead = abs(gamesP1 - gamesP2)
            val top = max(gamesP1, gamesP2)
            if (top >= rules.gamesPerSet && lead >= 2) {
                completeSet(player, SetScore(gamesP1, gamesP2, false))
            } else if (rules.setTiebreak && gamesP1 == rules.gamesPerSet && gamesP2 == rules.gamesPerSet) {
                startTiebreak(rules.tiebreakPoints, replacesSet = false)
            }
        }

        private fun checkTiebreak() {
            val lead = abs(p1Pts - p2Pts)
            if (max(p1Pts, p2Pts) < tiebreakTarget || lead < 2) return
            val player = if (p1Pts > p2Pts) 1 else 2
            if (tiebreakReplacesSet) {
                changeServer()
                completeSet(player, if (player == 1) SetScore(1, 0, true) else SetScore(0, 1, true))
            } else {
                // The tiebreak winner wins the last game of the set, for example 7–6.
                markGameWon(player)
                completeSet(player, SetScore(gamesP1, gamesP2, true))
            }
        }

        private fun addGame(player: Int) {
            if (player == 1) gamesP1++ else gamesP2++
        }

        private fun completeSet(player: Int, score: SetScore) {
            completedSets += score
            if (player == 1) setsP1++ else setsP2++
            gamesP1 = 0; gamesP2 = 0
            p1Pts = 0; p2Pts = 0
            isTiebreak = false
            setWonBy = player
            if (rules.manualScoring) return
            val setsToWin = if (rules.structure == MatchStructure.SINGLE_TIEBREAK) 1 else rules.setsToWin()
            if (max(setsP1, setsP2) >= setsToWin) {
                matchWonBy = player
            } else if (rules.hasMatchTiebreakDecider() && setsP1 == setsToWin - 1 && setsP2 == setsToWin - 1) {
                startTiebreak(MatchRulesV1.MATCH_TIEBREAK_POINTS, replacesSet = true)
            }
        }

        private fun startTiebreak(target: Int, replacesSet: Boolean) {
            isTiebreak = true
            tiebreakTarget = target
            tiebreakReplacesSet = replacesSet
            p1Pts = 0; p2Pts = 0
        }
    }
}
