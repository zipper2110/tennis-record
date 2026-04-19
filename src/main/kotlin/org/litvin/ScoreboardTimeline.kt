package org.litvin

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

        // Scoring state
        var p1Pts = 0
        var p2Pts = 0
        var gamesP1 = 0
        var gamesP2 = 0
        var setsP1 = 0
        var setsP2 = 0
        val completedSets = mutableListOf<Pair<Int, Int>>()

        fun displayPoints(forP1: Boolean): String {
            val mine = if (forP1) p1Pts else p2Pts
            val other = if (forP1) p2Pts else p1Pts
            val base = arrayOf("0", "15", "30", "40")
            return if (mine < 4 && other < 4) base[mine.coerceIn(0, 3)]
            else if (mine == other) "40" else if (mine > other) "Ad" else "40"
        }

        fun stateText(): String {
            val setsBrief = if (completedSets.isEmpty()) "" else completedSets.joinToString(
                prefix = " [", postfix = "]", separator = ","
            ) { "${it.first}-${it.second}" }
            return buildString {
                append("Player 1: ")
                append("pts ").append(displayPoints(true))
                append(", games ").append(gamesP1)
                append(", sets ").append(setsP1)
                append("  |  ")
                append("Player 2: ")
                append("pts ").append(displayPoints(false))
                append(", games ").append(gamesP2)
                append(", sets ").append(setsP2)
                if (setsBrief.isNotEmpty()) append(setsBrief)
            }
        }

        fun applyOutcome(o: Outcome?) {
            when (o) {
                Outcome.P1 -> p1Pts += 1
                Outcome.P2 -> p2Pts += 1
                else -> return // NONE or null → carry-forward
            }
            // Check game win (no tiebreaks in v0.1.x)
            fun maybeWinGame(): Int? {
                if (p1Pts >= 4 && p1Pts - p2Pts >= 2) return 1
                if (p2Pts >= 4 && p2Pts - p1Pts >= 2) return 2
                return null
            }
            when (maybeWinGame()) {
                1 -> {
                    gamesP1 += 1
                    p1Pts = 0; p2Pts = 0
                }
                2 -> {
                    gamesP2 += 1
                    p1Pts = 0; p2Pts = 0
                }
                else -> {}
            }
            // Check set win (no tiebreaks)
            fun maybeWinSet(): Int? {
                if (gamesP1 >= 6 && gamesP1 - gamesP2 >= 2) return 1
                if (gamesP2 >= 6 && gamesP2 - gamesP1 >= 2) return 2
                return null
            }
            when (maybeWinSet()) {
                1 -> {
                    setsP1 += 1
                    completedSets += gamesP1 to gamesP2
                    gamesP1 = 0; gamesP2 = 0
                }
                2 -> {
                    setsP2 += 1
                    completedSets += gamesP1 to gamesP2
                    gamesP1 = 0; gamesP2 = 0
                }
                else -> {}
            }
        }

        val out = ArrayList<OverlaySpan>(ordered.size)
        val n1 = player1Name.ifBlank { "Player 1" }
        val n2 = player2Name.ifBlank { "Player 2" }
        // Initial state applies during the FIRST point interval (advance after point completes)
        for (i in ordered.indices) {
            val start = startsOut[i]
            val end = endsOut[i]
            if (end <= start) continue
            val text = stateText()
            out += OverlaySpan(startMs = start, endMs = end, text = text, p1Name = n1, p2Name = n2)
            // Then apply the outcome of the point i to advance state for the next interval
            val pid = ordered[i].id
            val o = outcomes[pid]
            applyOutcome(o)
        }
        return out
    }
}
