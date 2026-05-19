package org.litvin

import org.litvin.markup.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringRules

/**
 * Task 3.18 — Scoreboard overlay: timeline generation
 *
 * Produces an intermediate representation (IR) mapping output-time intervals to
 * a human-readable scoreboard text that can be burned into the video later (3.19).
 */

data class OverlaySpan(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val p1Name: String? = null,
    val p2Name: String? = null,
    // 3.21 — Tiebreak support fields (optional; fall back to parsing text when absent)
    val isTiebreak: Boolean = false,
    val tbP1: Int = 0,
    val tbP2: Int = 0,
    val p1Pts: Int = 0,
    val p2Pts: Int = 0,
    val gamesP1: Int = 0,
    val gamesP2: Int = 0,
    val setsP1: Int = 0,
    val setsP2: Int = 0,
    val completedSets: List<Pair<Int, Int>> = emptyList(),
)

object ScoreboardTimelineBuilder {
    /** Builds overlay spans in OUTPUT time domain. */
    fun build(points: List<PointV1>, outcomes: Map<String, Outcome>, idleTrim: Boolean, player1Name: String = "Player 1", player2Name: String = "Player 2"): List<OverlaySpan> {
        if (points.isEmpty()) return emptyList()
        val ordered = points.sortedBy { it.startMs }

        // Precompute output-time mapping for each source point interval
        val startsOut = LongArray(ordered.size)
        val endsOut = LongArray(ordered.size)
        if (idleTrim) {
            var acc: Long = 0
            ordered.forEachIndexed { i, p ->
                val dur = (p.endMs - p.startMs).toLong().coerceAtLeast(0)
                startsOut[i] = acc
                endsOut[i] = acc + dur
                acc += dur
            }
        } else {
            ordered.forEachIndexed { i, p ->
                // For full render, overlay uses original source times but should initialize at export start (t=0)
                endsOut[i] = p.endMs.toLong()
                startsOut[i] = if (i == 0) 0L else endsOut[i - 1]
            }
        }

        // Produce unified scoring snapshots (Task 3.21 — includes tiebreak support)
        val snaps = ScoringRules.computeSnapshotsBefore(ordered, outcomes)

        fun displayPoints(pMine: Int, pOther: Int): String {
            val base = arrayOf("0", "15", "30", "40")
            return if (pMine < 4 && pOther < 4) base[pMine.coerceIn(0, 3)]
            else if (pMine == pOther) "40" else if (pMine > pOther) "Ad" else "40"
        }

        val out = ArrayList<OverlaySpan>(ordered.size)
        val n1 = player1Name.ifBlank { "Player 1" }
        val n2 = player2Name.ifBlank { "Player 2" }
        for (i in ordered.indices) {
            val start = startsOut[i]
            val end = endsOut[i]
            if (end <= start) continue
            val st = snaps.getOrNull(i)
            val completedSets = st?.completedSets ?: emptyList()
            val setsBrief = if (completedSets.isEmpty()) "" else completedSets.joinToString(prefix = " [", postfix = "]", separator = ",") { "${'$'}{it.first}-${'$'}{it.second}" }
            val text = buildString {
                append("Player 1: ")
                append("pts ")
                append(displayPoints(st?.p1Pts ?: 0, st?.p2Pts ?: 0))
                append(", games ")
                append(st?.gamesP1 ?: 0)
                append(", sets ")
                append(st?.setsP1 ?: 0)
                append("  |  ")
                append("Player 2: ")
                append("pts ")
                append(displayPoints(st?.p2Pts ?: 0, st?.p1Pts ?: 0))
                append(", games ")
                append(st?.gamesP2 ?: 0)
                append(", sets ")
                append(st?.setsP2 ?: 0)
                if (setsBrief.isNotEmpty()) append(setsBrief)
            }
            out += OverlaySpan(
                startMs = start,
                endMs = end,
                text = text,
                p1Name = n1,
                p2Name = n2,
                isTiebreak = st?.isTiebreak ?: false,
                tbP1 = st?.tbP1 ?: 0,
                tbP2 = st?.tbP2 ?: 0,
                p1Pts = st?.p1Pts ?: 0,
                p2Pts = st?.p2Pts ?: 0,
                gamesP1 = st?.gamesP1 ?: 0,
                gamesP2 = st?.gamesP2 ?: 0,
                setsP1 = st?.setsP1 ?: 0,
                setsP2 = st?.setsP2 ?: 0,
                completedSets = completedSets,
            )
        }
        return out
    }
}
