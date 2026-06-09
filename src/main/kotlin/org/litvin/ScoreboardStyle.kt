package org.litvin

import java.util.Locale

enum class ScoreboardStyleId {
    CLASSIC,
}

data class ScoreboardStyle(
    val id: ScoreboardStyleId,
    val title: String,
    val fontFamily: String,
    val previewFontFamily: String,
    val palette: ScoreboardPalette,
    val layout: ScoreboardLayout,
)

data class ScoreboardPalette(
    val accentRgb: Int,
    val textPrimaryRgb: Int,
    val textMutedRgb: Int,
    val panelBgRgb: Int,
    val player1Rgb: Int,
    val player2Rgb: Int,
)

data class ScoreboardLayout(
    val marginBase: Int,
    val panelWidthBase: Int,
    val panelHeightBase: Int,
    val headerHeightBase: Int,
    val playerNameMaxChars: Int,
    val previewNameMaxChars: Int,
)

object ScoreboardStyles {
    val Classic = ScoreboardStyle(
        id = ScoreboardStyleId.CLASSIC,
        title = "TennisRecord app",
        fontFamily = "Arial",
        previewFontFamily = "Consolas",
        palette = ScoreboardPalette(
            accentRgb = 0xC4FF4D,
            textPrimaryRgb = 0xE7ECEF,
            textMutedRgb = 0xCCCCCC,
            panelBgRgb = 0x0E1116,
            player1Rgb = 0x4DA3FF,
            player2Rgb = 0xFF6B6B,
        ),
        layout = ScoreboardLayout(
            marginBase = 48,
            panelWidthBase = 420,
            panelHeightBase = 200,
            headerHeightBase = 56,
            playerNameMaxChars = 20,
            previewNameMaxChars = 12,
        )
    )

    fun default(): ScoreboardStyle = Classic
}

data class ScoreboardDisplay(
    val style: ScoreboardStyle,
    val player1Name: String,
    val player2Name: String,
    val player1PreviewName: String,
    val player2PreviewName: String,
    val player1Rgb: Int,
    val player2Rgb: Int,
    val player1PointText: String,
    val player2PointText: String,
    val player1Games: Int,
    val player2Games: Int,
    val completedSets: List<Pair<Int, Int>>,
    val player1Leading: Boolean,
)

object ScoreboardComponent {
    fun display(span: OverlaySpan, style: ScoreboardStyle = ScoreboardStyles.default()): ScoreboardDisplay {
        val player1Rgb = parseHexRgbOrDefault(span.p1ColorHex, style.palette.player1Rgb)
        val player2Rgb = parseHexRgbOrDefault(span.p2ColorHex, style.palette.player2Rgb)
        val rawP1 = (span.p1Name ?: "Player 1").ifBlank { "Player 1" }
        val rawP2 = (span.p2Name ?: "Player 2").ifBlank { "Player 2" }
        val p1Name = ellipsize(rawP1.uppercase(Locale.US), style.layout.playerNameMaxChars)
        val p2Name = ellipsize(rawP2.uppercase(Locale.US), style.layout.playerNameMaxChars)
        val p1Preview = ellipsize(rawP1.uppercase(Locale.US), style.layout.previewNameMaxChars)
        val p2Preview = ellipsize(rawP2.uppercase(Locale.US), style.layout.previewNameMaxChars)

        return ScoreboardDisplay(
            style = style,
            player1Name = p1Name,
            player2Name = p2Name,
            player1PreviewName = p1Preview,
            player2PreviewName = p2Preview,
            player1Rgb = player1Rgb,
            player2Rgb = player2Rgb,
            player1PointText = if (span.isTiebreak) span.tbP1.toString() else pointsLabel(span.p1Pts, span.p2Pts),
            player2PointText = if (span.isTiebreak) span.tbP2.toString() else pointsLabel(span.p2Pts, span.p1Pts),
            player1Games = if (span.isTiebreak) 6 else span.gamesP1,
            player2Games = if (span.isTiebreak) 6 else span.gamesP2,
            completedSets = span.completedSets.takeLast(2),
            player1Leading = if (span.isTiebreak) span.tbP1 > span.tbP2 else isLeading(span.p1Pts, span.p2Pts),
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

    private fun isLeading(mine: Int, other: Int): Boolean {
        val mineMapped = if (mine >= 4) 4 else mine
        val otherMapped = if (other >= 4) 4 else other
        return mineMapped > otherMapped
    }

    private fun ellipsize(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.substring(0, (maxChars - 1).coerceAtLeast(0)) + "..."
    }
}
