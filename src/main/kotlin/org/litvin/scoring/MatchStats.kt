package org.litvin.scoring

import org.litvin.points.PointV1
import org.litvin.scoring.ScoringEngine.Stake
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One value for each player. */
data class PerPlayer<T>(val p1: T, val p2: T) {
    operator fun get(player: Int): T = if (player == 1) p1 else p2
}

/** [won] of [total], for example 3 break points won of 7. */
data class Ratio(val won: Int, val total: Int) {
    /** The rounded percentage, or null when [total] is 0. */
    val percent: Int? get() = if (total == 0) null else (won * 100.0 / total).roundToInt()
}

/** Statistics that need the server of each point. */
data class ServeStats(
    /** The points that the player won on the player's own serve, of the points that the player served. */
    val servicePointsWon: PerPlayer<Ratio>,
    /** The points that the player won on the opponent's serve, of the points that the player received. */
    val returnPointsWon: PerPlayer<Ratio>,
    /** The service games that the player won (held), of the service games that the player completed. Tiebreaks are not service games. */
    val serviceGamesWon: PerPlayer<Ratio>,
    /** The break points that the player won, of the break points that the player had. */
    val breakPointsWon: PerPlayer<Ratio>,
) {
    /** The break points that the player saved, of the break points that the player faced. */
    val breakPointsSaved: PerPlayer<Ratio>
        get() = PerPlayer(lostBy(breakPointsWon.p2), lostBy(breakPointsWon.p1))

    /** The service games of the opponent that the player won (broke), of the service games that the opponent completed. */
    val returnGamesWon: PerPlayer<Ratio>
        get() = PerPlayer(lostBy(serviceGamesWon.p2), lostBy(serviceGamesWon.p1))

    /** The chances of the opponent that the opponent did not win. */
    private fun lostBy(opponent: Ratio) = Ratio(opponent.total - opponent.won, opponent.total)
}

/** Statistics from the start and end times of the points. */
data class TimeStats(
    /** From the start of the first point to the end of the last point. */
    val durationMs: Long,
    /** The sum of the point durations. */
    val playingMs: Long,
    /** The average point duration, or null when there are no points. */
    val averagePointMs: Long?,
    /** The point with the longest duration, or null when there are no points. */
    val longestPoint: PointV1?,
    /** The average duration of the points that each player won, or null when the player won no points. */
    val averagePointMsWon: PerPlayer<Long?>,
    /** The average time from the end of a point to the start of the next point, or null when no two points follow in order. */
    val averageGapMs: Long?,
    /** The duration and the winner of each point that has a winner. */
    val scoredDurations: List<ScoredDuration> = emptyList(),
)

/** The duration of a point that has a winner. [winner] is 1 or 2. */
data class ScoredDuration(val durationMs: Long, val winner: Int)

/** The points that some statistics come from. The Stats tab opens them in the Scoring tab. */
data class KeyPoints(
    /** The first point of the longest point run of each player. Null when the player won no point. */
    val longestPointRunStart: PerPlayer<String?> = PerPlayer(null, null),
    /** The point where the player first has the largest lead. Null when the player never leads. */
    val largestPointLeadAt: PerPlayer<String?> = PerPlayer(null, null),
)

/**
 * Match statistics for a range of points: the full match or one set.
 *
 * Only the points that have a winner count. An unscored point or a point with no outcome does not stop a run of points.
 */
data class MatchStats(
    /** The points in the range. */
    val points: Int,
    /** The points in the range that have a winner. */
    val scoredPoints: Int,
    val pointsWon: PerPlayer<Int>,
    val gamesWon: PerPlayer<Int>,
    val setsWon: PerPlayer<Int>,
    /** Set tiebreaks and match tiebreaks. Only matches with sets have them. */
    val tiebreaksWon: PerPlayer<Int>,
    /** Null when the user did not mark a server. */
    val serve: ServeStats?,
    /** The points that win a set (match points too) that the player won, of the set points that the player had. */
    val setPointsWon: PerPlayer<Ratio>,
    /** The match points that the player won, of the match points that the player had. */
    val matchPointsWon: PerPlayer<Ratio>,
    /** The points played at deuce (40–40 or later) that the player won. Tiebreaks and manual scoring have no deuce points. */
    val deucePointsWon: PerPlayer<Int>,
    /** The most points in a row that the player won. */
    val longestPointRun: PerPlayer<Int>,
    /** The most games in a row that the player won. */
    val longestGameRun: PerPlayer<Int>,
    /** The largest lead in points won that the player had. */
    val largestPointLead: PerPlayer<Int>,
    val time: TimeStats,
    val keyPoints: KeyPoints = KeyPoints(),
) {
    companion object {
        /**
         * Calculates the statistics for the points in [range].
         * [timeline] must come from [ScoringEngine.timeline] with the same [points] and [outcomes].
         */
        fun compute(
            points: List<PointV1>,
            outcomes: Map<String, Outcome>,
            timeline: ScoringEngine.Timeline,
            rules: MatchRulesV1,
            range: IntRange = points.indices,
        ): MatchStats {
            val indices = range.filter { it in points.indices }
            val winners = points.map { winner(outcomes[it.id]) }
            val scored = indices.filter { winners[it] != null }

            return MatchStats(
                points = indices.size,
                scoredPoints = scored.size,
                pointsWon = count(scored) { winners[it] },
                gamesWon = count(indices) { timeline.statesAfterPoint[it].lastGameWonBy },
                setsWon = count(indices) { timeline.statesAfterPoint[it].lastSetWonBy },
                tiebreaksWon = count(indices.filter { rules.structure == MatchStructure.SETS && timeline.stateBefore(it).isTiebreak }) {
                    val after = timeline.statesAfterPoint[it]
                    after.lastSetWonBy ?: after.lastGameWonBy
                },
                serve = serveStats(indices, scored, winners, timeline),
                setPointsWon = chances(scored, winners, timeline, Stake.SET),
                matchPointsWon = chances(scored, winners, timeline, Stake.MATCH),
                deucePointsWon = count(scored.filter { isDeucePoint(timeline.stateBefore(it), rules) }) { winners[it] },
                longestPointRun = longestRun(scored.map { winners[it]!! }),
                longestGameRun = longestRun(indices.mapNotNull { timeline.statesAfterPoint[it].lastGameWonBy }),
                largestPointLead = largestLead(scored.map { winners[it]!! }),
                time = timeStats(indices.map { points[it] }, indices.map { winners[it] }),
                keyPoints = scored.map { winners[it]!! }.let { players ->
                    fun idOf(position: Int?) = position?.let { points[scored[it]].id }
                    val runs = longestRunStarts(players)
                    val leads = largestLeadPoints(players)
                    KeyPoints(
                        longestPointRunStart = PerPlayer(idOf(runs.p1), idOf(runs.p2)),
                        largestPointLeadAt = PerPlayer(idOf(leads.p1), idOf(leads.p2)),
                    )
                },
            )
        }

        /**
         * The index of the point that wins the match, or null when nobody has won the match yet.
         * The engine ignores the points after this point. Formats without an end and manual scoring return null.
         */
        fun matchEndIndex(timeline: ScoringEngine.Timeline, rules: MatchRulesV1): Int? {
            if (rules.manualScoring) return null
            val setsToWin = when (rules.structure) {
                MatchStructure.SETS -> rules.normalized().setsToWin()
                MatchStructure.SINGLE_TIEBREAK -> 1
                MatchStructure.GAMES_ONLY, MatchStructure.PLAIN_POINTS -> return null
            }
            return timeline.statesAfterPoint.indexOfFirst { maxOf(it.setsP1, it.setsP2) >= setsToWin }.takeIf { it >= 0 }
        }

        /**
         * The point ranges of the sets in [points]. A set ends at the point that wins it.
         * The points after the last completed set make one more range. Without completed sets, all points are one range.
         */
        fun setRanges(
            timeline: ScoringEngine.Timeline,
            points: IntRange = timeline.statesAfterPoint.indices,
        ): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var start = points.first
            for (index in points) {
                if (timeline.statesAfterPoint[index].lastSetWonBy != null) {
                    ranges += start..index
                    start = index + 1
                }
            }
            if (start <= points.last) ranges += start..points.last
            return ranges
        }

        private fun winner(outcome: Outcome?): Int? = when (outcome) {
            Outcome.P1 -> 1
            Outcome.P2 -> 2
            Outcome.NONE, null -> null
        }

        private fun count(indices: List<Int>, playerOf: (Int) -> Int?): PerPlayer<Int> {
            val players = indices.mapNotNull(playerOf)
            return PerPlayer(players.count { it == 1 }, players.count { it == 2 })
        }

        private fun serveStats(
            indices: List<Int>,
            scored: List<Int>,
            winners: List<Int?>,
            timeline: ScoringEngine.Timeline,
        ): ServeStats? {
            if (indices.none { timeline.serverOfPoint[it] != null }) return null
            val served = IntArray(3)
            val servedWon = IntArray(3)
            val breakChances = IntArray(3)
            val breaksWon = IntArray(3)
            for (i in scored) {
                val server = timeline.serverOfPoint[i] ?: continue
                val receiver = 3 - server
                val winner = winners[i]!!
                served[server]++
                if (winner == server) servedWon[server]++
                val isBreakPoint = !timeline.stateBefore(i).isTiebreak && timeline.stakesOfPoint[i].of(receiver) >= Stake.GAME
                if (isBreakPoint) {
                    breakChances[receiver]++
                    if (winner == receiver) breaksWon[receiver]++
                }
            }
            val serviceGames = IntArray(3)
            val holds = IntArray(3)
            for (i in indices) {
                val gameWinner = timeline.statesAfterPoint[i].lastGameWonBy ?: continue
                val server = timeline.serverOfPoint[i] ?: continue
                if (timeline.stateBefore(i).isTiebreak) continue
                serviceGames[server]++
                if (gameWinner == server) holds[server]++
            }
            return ServeStats(
                servicePointsWon = PerPlayer(Ratio(servedWon[1], served[1]), Ratio(servedWon[2], served[2])),
                returnPointsWon = PerPlayer(
                    Ratio(served[2] - servedWon[2], served[2]),
                    Ratio(served[1] - servedWon[1], served[1]),
                ),
                serviceGamesWon = PerPlayer(Ratio(holds[1], serviceGames[1]), Ratio(holds[2], serviceGames[2])),
                breakPointsWon = PerPlayer(Ratio(breaksWon[1], breakChances[1]), Ratio(breaksWon[2], breakChances[2])),
            )
        }

        /** The points where a player could win at least [stake], and how many of them the player won. */
        private fun chances(
            scored: List<Int>,
            winners: List<Int?>,
            timeline: ScoringEngine.Timeline,
            stake: Stake,
        ): PerPlayer<Ratio> {
            fun forPlayer(player: Int): Ratio {
                val chances = scored.filter { timeline.stakesOfPoint[it].of(player) >= stake }
                return Ratio(chances.count { winners[it] == player }, chances.size)
            }
            return PerPlayer(forPlayer(1), forPlayer(2))
        }

        private fun isDeucePoint(before: ScoringEngine.MatchState, rules: MatchRulesV1): Boolean =
            !rules.manualScoring && !before.isTiebreak && before.p1Pts == before.p2Pts && before.p1Pts >= 3

        private fun longestRun(players: List<Int>): PerPlayer<Int> {
            val best = IntArray(3)
            var current = 0
            players.forEachIndexed { index, player ->
                current = if (index > 0 && players[index - 1] == player) current + 1 else 1
                if (current > best[player]) best[player] = current
            }
            return PerPlayer(best[1], best[2])
        }

        /** The position of the first point of the first longest run of each player. */
        private fun longestRunStarts(players: List<Int>): PerPlayer<Int?> {
            val best = IntArray(3)
            val bestStart = arrayOfNulls<Int>(3)
            var current = 0
            players.forEachIndexed { index, player ->
                current = if (index > 0 && players[index - 1] == player) current + 1 else 1
                if (current > best[player]) {
                    best[player] = current
                    bestStart[player] = index - current + 1
                }
            }
            return PerPlayer(bestStart[1], bestStart[2])
        }

        /** The position of the point where each player first has the largest lead. */
        private fun largestLeadPoints(players: List<Int>): PerPlayer<Int?> {
            var difference = 0
            var p1Lead = 0
            var p2Lead = 0
            var p1At: Int? = null
            var p2At: Int? = null
            players.forEachIndexed { index, player ->
                difference += if (player == 1) 1 else -1
                if (difference > p1Lead) {
                    p1Lead = difference
                    p1At = index
                }
                if (-difference > p2Lead) {
                    p2Lead = -difference
                    p2At = index
                }
            }
            return PerPlayer(p1At, p2At)
        }

        private fun largestLead(players: List<Int>): PerPlayer<Int> {
            var difference = 0
            var p1Lead = 0
            var p2Lead = 0
            for (player in players) {
                difference += if (player == 1) 1 else -1
                p1Lead = maxOf(p1Lead, difference)
                p2Lead = maxOf(p2Lead, -difference)
            }
            return PerPlayer(p1Lead, p2Lead)
        }

        private fun timeStats(points: List<PointV1>, winners: List<Int?>): TimeStats {
            val durations = points.map { (it.endMs - it.startMs).coerceAtLeast(0).toLong() }
            val gaps = points.zipWithNext { a, b -> (b.startMs - a.endMs).toLong() }.filter { it >= 0 }
            fun averageWonBy(player: Int): Long? =
                durations.filterIndexed { index, _ -> winners[index] == player }.averageOrNull()
            return TimeStats(
                durationMs = if (points.isEmpty()) 0 else (points.last().endMs - points.first().startMs).coerceAtLeast(0).toLong(),
                playingMs = durations.sum(),
                averagePointMs = durations.averageOrNull(),
                longestPoint = points.indices.maxByOrNull { durations[it] }?.let(points::get),
                averagePointMsWon = PerPlayer(averageWonBy(1), averageWonBy(2)),
                averageGapMs = gaps.averageOrNull(),
                scoredDurations = durations.indices.mapNotNull { index ->
                    winners[index]?.let { ScoredDuration(durations[index], it) }
                },
            )
        }

        private fun List<Long>.averageOrNull(): Long? = if (isEmpty()) null else average().roundToLong()
    }
}
