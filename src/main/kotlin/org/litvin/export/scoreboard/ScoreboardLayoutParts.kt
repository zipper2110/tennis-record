package org.litvin.export.scoreboard

import org.litvin.ScoreboardDisplay
import kotlin.math.max

// Shared parts of the scoreboard layouts in ScoreboardLayouts and ScoreboardMoreLayouts.

internal const val SEGOE = "Segoe UI"
internal const val SEGOE_BLACK = "Segoe UI Black"
internal const val ARIAL = "Arial"
internal const val ARIAL_BLACK = "Arial Black"
internal const val GEORGIA = "Georgia"
internal const val TREBUCHET = "Trebuchet MS"
internal const val TAHOMA = "Tahoma"
internal const val CONSOLAS = "Consolas"
internal const val WHITE = 0xFFFFFF
internal const val BLACK = 0x000000

/**
 * [credit] is the text of the line at the bottom of the board, or null when the line is hidden.
 * [playerColors] is false when the board does not show the color markers of the players.
 */
internal data class Look(val title: String?, val accentRgb: Int, val opacity: Double, val credit: String?, val playerColors: Boolean)

/** [wonSets] tells, for each completed set, if this player won it. */
internal class Row(
    val name: String,
    val rgb: Int,
    val sets: List<Int>,
    val wonSets: List<Boolean>,
    val games: Int,
    val points: String,
    val leading: Boolean,
    val trailing: Boolean,
)

internal fun rows(display: ScoreboardDisplay): List<Row> = listOf(
    Row(
        name = display.player1Name,
        rgb = display.player1Rgb,
        sets = display.completedSets.map { it.first },
        wonSets = display.completedSets.map { it.first > it.second },
        games = display.player1Games,
        points = display.player1PointText,
        leading = display.pointLeader == 1,
        trailing = display.pointLeader == 2,
    ),
    Row(
        name = display.player2Name,
        rgb = display.player2Rgb,
        sets = display.completedSets.map { it.second },
        wonSets = display.completedSets.map { it.second > it.first },
        games = display.player2Games,
        points = display.player2PointText,
        leading = display.pointLeader == 2,
        trailing = display.pointLeader == 1,
    ),
)

/** A label whose capital letters and digits are centered on [capCenterY]. */
internal fun centered(
    x: Double,
    capCenterY: Double,
    text: String,
    font: String,
    size: Double,
    rgb: Int,
    anchor: TextAnchor,
    bold: Boolean = true,
    opacity: Double = 1.0,
    spacing: Double = 0.0,
    outline: Double = 0.0,
) = SceneItem.Label(
    x = x,
    y = ScoreboardFonts.middleYForCapCenter(capCenterY, font, bold, size),
    text = text,
    font = font,
    size = size,
    rgb = rgb,
    bold = bold,
    anchor = anchor,
    opacity = opacity,
    spacing = spacing,
    outline = outline,
)

internal fun nameWidth(rows: List<Row>, font: String, size: Double, minimum: Double, bold: Boolean = true): Double =
    max(minimum, rows.maxOf { ScoreboardFonts.textWidth(it.name, font, bold, size) })

/**
 * The games of one completed set. The set winner is bold. The set loser is regular,
 * with [lostRgb] and [lostOpacity].
 */
internal fun setGames(
    x: Double,
    cy: Double,
    row: Row,
    setIndex: Int,
    font: String,
    size: Double,
    wonRgb: Int,
    lostRgb: Int,
    lostOpacity: Double = 1.0,
    outline: Double = 0.0,
): SceneItem.Label {
    val won = row.wonSets[setIndex]
    return centered(
        x, cy, row.sets[setIndex].toString(), font, size,
        if (won) wonRgb else lostRgb, TextAnchor.CENTER,
        bold = won, opacity = if (won) 1.0 else lostOpacity, outline = outline,
    )
}

/** A frame of four bars inside the rectangle. Use it on boards with sharp corners. */
internal fun frame(x: Double, y: Double, width: Double, height: Double, thickness: Double, rgb: Int, opacity: Double = 1.0) = listOf(
    SceneItem.Box(x, y, width, thickness, rgb, opacity),
    SceneItem.Box(x, y + height - thickness, width, thickness, rgb, opacity),
    SceneItem.Box(x, y + thickness, thickness, height - 2 * thickness, rgb, opacity),
    SceneItem.Box(x + width - thickness, y + thickness, thickness, height - 2 * thickness, rgb, opacity),
)
