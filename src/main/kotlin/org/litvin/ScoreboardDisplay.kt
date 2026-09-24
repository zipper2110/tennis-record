package org.litvin

import java.util.Locale

/** The values that a scoreboard shows for one score state. All scoreboard styles use this model. */
data class ScoreboardDisplay(
    val player1Name: String,
    val player2Name: String,
    val player1Rgb: Int,
    val player2Rgb: Int,
    val player1PointText: String,
    val player2PointText: String,
    val player1Games: Int,
    val player2Games: Int,
    val completedSets: List<Pair<Int, Int>>,
    /** 1 or 2 when that player leads the current game, 0 when the points are equal. */
    val pointLeader: Int,
) {
    val player1Leading: Boolean
        get() = pointLeader == 1
}

object ScoreboardComponent {
    const val DEFAULT_PLAYER1_RGB = 0x4DA3FF
    const val DEFAULT_PLAYER2_RGB = 0xFF6B6B
    const val PLAYER_NAME_MAX_CHARS = 20

    /** The number of completed sets that a scoreboard shows. Older sets are not shown. */
    const val VISIBLE_COMPLETED_SETS = 2

    fun display(span: OverlaySpan): ScoreboardDisplay {
        val rawP1 = (span.p1Name ?: "Player 1").ifBlank { "Player 1" }
        val rawP2 = (span.p2Name ?: "Player 2").ifBlank { "Player 2" }
        val leader = if (span.isTiebreak) {
            compareValues(span.tbP1, span.tbP2)
        } else {
            // Raw points also order the deuce states: 5-4 is advantage for player 1.
            compareValues(span.p1Pts, span.p2Pts)
        }

        return ScoreboardDisplay(
            player1Name = ellipsize(rawP1.trim().uppercase(Locale.US), PLAYER_NAME_MAX_CHARS),
            player2Name = ellipsize(rawP2.trim().uppercase(Locale.US), PLAYER_NAME_MAX_CHARS),
            player1Rgb = parseHexRgbOrDefault(span.p1ColorHex, DEFAULT_PLAYER1_RGB),
            player2Rgb = parseHexRgbOrDefault(span.p2ColorHex, DEFAULT_PLAYER2_RGB),
            player1PointText = if (span.isTiebreak) span.tbP1.toString() else pointsLabel(span.p1Pts, span.p2Pts),
            player2PointText = if (span.isTiebreak) span.tbP2.toString() else pointsLabel(span.p2Pts, span.p1Pts),
            // A set tiebreak keeps the games (for example 6–6). A match tiebreak starts at 0–0.
            player1Games = span.gamesP1,
            player2Games = span.gamesP2,
            completedSets = span.completedSets.takeLast(VISIBLE_COMPLETED_SETS),
            pointLeader = when {
                leader > 0 -> 1
                leader < 0 -> 2
                else -> 0
            },
        )
    }

    fun parseHexRgbOrDefault(hex: String?, defaultRgb: Int): Int {
        val clean = hex?.trim()?.removePrefix("#") ?: return defaultRgb
        return if (clean.length == 6) clean.toIntOrNull(16) ?: defaultRgb else defaultRgb
    }

    fun pointsLabel(mine: Int, other: Int): String {
        val base = arrayOf("0", "15", "30", "40")
        if (mine < 4 && other < 4) return base[mine.coerceIn(0, 3)]
        if (mine == other) return "40"
        return if (mine > other) "Ad" else "40"
    }

    private fun ellipsize(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.substring(0, (maxChars - 1).coerceAtLeast(0)).trimEnd() + "…"
    }
}
