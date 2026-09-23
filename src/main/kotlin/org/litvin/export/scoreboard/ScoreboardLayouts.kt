package org.litvin.export.scoreboard

import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardDisplay
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import java.util.Locale
import kotlin.math.max

/** The default colors of a style. The settings can change the accent color and the background opacity. */
data class ScoreboardStyleDefaults(
    val accentRgb: Int,
    val backgroundOpacityPercent: Int,
)

/** Builds the [ScoreboardScene] of each scoreboard style. */
object ScoreboardLayouts {
    fun defaults(style: ScoreboardStyleId): ScoreboardStyleDefaults = when (style) {
        ScoreboardStyleId.BROADCAST -> ScoreboardStyleDefaults(accentRgb = 0xC4FF4D, backgroundOpacityPercent = 84)
        ScoreboardStyleId.CLASSIC -> ScoreboardStyleDefaults(accentRgb = 0xC4FF4D, backgroundOpacityPercent = 69)
        ScoreboardStyleId.CENTER_COURT -> ScoreboardStyleDefaults(accentRgb = 0xD7FF3F, backgroundOpacityPercent = 96)
        ScoreboardStyleId.COMPACT -> ScoreboardStyleDefaults(accentRgb = 0xC4FF4D, backgroundOpacityPercent = 72)
        ScoreboardStyleId.GRASS_COURT -> ScoreboardStyleDefaults(accentRgb = 0xD9C27A, backgroundOpacityPercent = 92)
        ScoreboardStyleId.CLAY_COURT -> ScoreboardStyleDefaults(accentRgb = 0xFFE0A3, backgroundOpacityPercent = 94)
        ScoreboardStyleId.HARD_COURT -> ScoreboardStyleDefaults(accentRgb = 0xE9F24A, backgroundOpacityPercent = 92)
        ScoreboardStyleId.NIGHT_SESSION -> ScoreboardStyleDefaults(accentRgb = 0x2FE6FF, backgroundOpacityPercent = 88)
        ScoreboardStyleId.LED_BOARD -> ScoreboardStyleDefaults(accentRgb = 0xFFB000, backgroundOpacityPercent = 95)
        ScoreboardStyleId.MINIMAL -> ScoreboardStyleDefaults(accentRgb = 0xC4FF4D, backgroundOpacityPercent = 30)
        ScoreboardStyleId.TILES -> ScoreboardStyleDefaults(accentRgb = 0xC4FF4D, backgroundOpacityPercent = 88)
        ScoreboardStyleId.TICKER -> ScoreboardStyleDefaults(accentRgb = 0xC4FF4D, backgroundOpacityPercent = 88)
        ScoreboardStyleId.RETRO -> ScoreboardStyleDefaults(accentRgb = 0xF2C14E, backgroundOpacityPercent = 94)
        ScoreboardStyleId.BOLD_BLOCK -> ScoreboardStyleDefaults(accentRgb = 0xFFD000, backgroundOpacityPercent = 97)
    }

    fun scene(display: ScoreboardDisplay, settings: ScoreboardSettingsV1): ScoreboardScene {
        val normalized = settings.normalized()
        val defaults = defaults(normalized.style)
        val look = Look(
            title = normalized.title.takeIf { normalized.showTitle && it.isNotBlank() },
            accentRgb = ScoreboardComponent.parseHexRgbOrDefault(normalized.accentColorHex, defaults.accentRgb),
            opacity = (normalized.backgroundOpacityPercent ?: defaults.backgroundOpacityPercent) / 100.0,
            credit = ScoreboardSettingsV1.APP_CREDIT.takeIf { normalized.showAppCredit },
        )
        return when (normalized.style) {
            ScoreboardStyleId.BROADCAST -> broadcast(display, look)
            ScoreboardStyleId.CLASSIC -> classic(display, look)
            ScoreboardStyleId.CENTER_COURT -> centerCourt(display, look)
            ScoreboardStyleId.COMPACT -> compact(display, look)
            ScoreboardStyleId.GRASS_COURT -> ScoreboardMoreLayouts.grassCourt(display, look)
            ScoreboardStyleId.CLAY_COURT -> ScoreboardMoreLayouts.clayCourt(display, look)
            ScoreboardStyleId.HARD_COURT -> ScoreboardMoreLayouts.hardCourt(display, look)
            ScoreboardStyleId.NIGHT_SESSION -> ScoreboardMoreLayouts.nightSession(display, look)
            ScoreboardStyleId.LED_BOARD -> ScoreboardMoreLayouts.ledBoard(display, look)
            ScoreboardStyleId.MINIMAL -> ScoreboardMoreLayouts.minimal(display, look)
            ScoreboardStyleId.TILES -> ScoreboardMoreLayouts.tiles(display, look)
            ScoreboardStyleId.TICKER -> ScoreboardMoreLayouts.ticker(display, look)
            ScoreboardStyleId.RETRO -> ScoreboardMoreLayouts.retro(display, look)
            ScoreboardStyleId.BOLD_BLOCK -> ScoreboardMoreLayouts.boldBlock(display, look)
        }
    }

    /**
     * Broadcast: a dark glass panel with a title bar, set columns with thin rules
     * and a highlighted point column. This is the default style.
     */
    private fun broadcast(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display)
        val title = look.title?.uppercase(Locale.US)
        val radius = 10.0
        val padX = 22.0
        val headerH = if (title != null) 46.0 else 0.0
        val rowH = 60.0
        val nameSize = 27.0
        val setSize = 28.0
        val pointSize = 38.0
        val titleSize = 20.0
        val titleSpacing = 1.6
        val square = 12.0
        val nameX = padX + square + 14.0
        val setColW = 54.0
        val pointColW = 88.0
        val columns = display.completedSets.size + 1

        val contentW = nameX + nameWidth(rows, SEGOE, nameSize, 150.0) + 24.0 + columns * setColW + pointColW
        val titleW = if (title != null) {
            padX + 10.0 + 12.0 + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + padX
        } else {
            0.0
        }
        val width = max(contentW, titleW)
        val rowsBottom = headerH + 2 * rowH
        val footerH = if (look.credit != null) 32.0 else 0.0
        val height = rowsBottom + footerH
        // The point column reaches the bottom corner only when there is no credit line.
        val bottomRadius = if (look.credit != null) 0.0 else radius
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val rule = 0xFFFFFF
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, 0x0A0E0C, look.opacity, Corners.all(radius))
        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, width, headerH, BLACK, look.opacity * 0.28, Corners(radius, radius, 0.0, 0.0))
            items += SceneItem.Box(padX, headerH / 2 - 5.0, 10.0, 10.0, look.accentRgb, 1.0, Corners.all(5.0))
            items += centered(padX + 22.0, headerH / 2, title, SEGOE, titleSize, look.accentRgb, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
            items += SceneItem.Box(0.0, headerH - 1.0, width, 1.0, rule, 0.10)
        }
        // The point column is a little darker, and the leading player's cell has an accent tint.
        items += SceneItem.Box(pointX, headerH, pointColW, 2 * rowH, BLACK, 0.22, Corners(0.0, if (title == null) radius else 0.0, bottomRadius, 0.0))
        rows.forEachIndexed { index, row ->
            if (!row.leading) return@forEachIndexed
            val top = headerH + index * rowH
            val corners = Corners(
                topLeft = 0.0,
                topRight = if (index == 0 && title == null) radius else 0.0,
                bottomRight = if (index == 1) bottomRadius else 0.0,
                bottomLeft = 0.0,
            )
            items += SceneItem.Box(pointX, top, pointColW, rowH, look.accentRgb, 0.16, corners)
        }
        items += SceneItem.Box(0.0, headerH + rowH - 0.5, width, 1.0, rule, 0.08)
        for (column in 0..columns) {
            items += SceneItem.Box(setsX + column * setColW - 0.5, headerH, 1.0, 2 * rowH, rule, 0.08)
        }

        rows.forEachIndexed { index, row ->
            val cy = headerH + index * rowH + rowH / 2
            items += SceneItem.Box(padX, cy - square / 2, square, square, row.rgb, 1.0, Corners.all(2.5))
            items += centered(nameX, cy, row.name, SEGOE, nameSize, 0xF4F6F5, TextAnchor.MIDDLE_LEFT)
            // The winner of a completed set is bright and bold. The loser is dim.
            row.sets.forEachIndexed { setIndex, games ->
                val won = row.wonSets[setIndex]
                items += centered(
                    setsX + setIndex * setColW + setColW / 2, cy, games.toString(), SEGOE, setSize,
                    if (won) 0xF4F6F5 else 0x6E7571, TextAnchor.CENTER, bold = won,
                )
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), SEGOE, setSize, 0xDDE2DF, TextAnchor.CENTER)
            val pointRgb = when {
                row.leading -> look.accentRgb
                row.trailing -> 0x8A918D
                else -> 0xF4F6F5
            }
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, pointRgb, TextAnchor.CENTER)
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom - 0.5, width, 1.0, rule, 0.10)
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 19.2, 0x8A918D, TextAnchor.CENTER, bold = false, spacing = 0.6)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Classic: the original TennisRecord scoreboard with Arial text and neon points. */
    private fun classic(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display)
        val title = look.title
        val headerH = if (title != null) 56.0 else 0.0
        val nameSize = 30.0
        val cellSize = 36.0
        val pointSize = 42.0
        val nameX = 60.0
        val cellStep = 66.0
        val pointAreaW = 110.0
        val rowTop = headerH + 15.0
        val rowGap = 64.0

        val cellsX = nameX + max(190.0, nameWidth(rows, ARIAL, nameSize, 0.0) + 24.0)
        val cellsW = (display.completedSets.size + 1) * cellStep
        val titleW = if (title != null) 48.0 + ScoreboardFonts.textWidth(title, ARIAL, true, 28.0, 2.0) + 24.0 else 0.0
        val width = maxOf(420.0, cellsX + cellsW + pointAreaW, titleW)
        val rowsBottom = rowTop + rowGap + 18.0 + 36.0
        val footerH = if (look.credit != null) 38.0 else 0.0
        val height = rowsBottom + footerH
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, 0x0E1116, look.opacity)
        if (title != null) {
            items += SceneItem.Box(20.0, headerH / 2 - 6.0, 12.0, 12.0, look.accentRgb, 1.0, Corners.all(6.0))
            items += centered(44.0, headerH / 2, title, ARIAL, 28.0, look.accentRgb, TextAnchor.MIDDLE_LEFT, spacing = 2.0, outline = 1.5)
        }
        rows.forEachIndexed { index, row ->
            val cy = rowTop + index * rowGap + 18.0
            items += SceneItem.Box(24.0, cy - 12.0, 24.0, 24.0, row.rgb, 0.85)
            items += centered(nameX, cy, row.name, ARIAL, nameSize, 0xE7ECEF, TextAnchor.MIDDLE_LEFT, bold = false, opacity = 0.93, outline = 1.2)
            // The winner of a completed set is white and bold. The loser is dim.
            row.sets.forEachIndexed { cell, games ->
                val won = row.wonSets[cell]
                items += centered(
                    cellsX + cell * cellStep + 22.0, cy, games.toString(), ARIAL, cellSize,
                    if (won) WHITE else 0xCCCCCC, TextAnchor.CENTER, bold = won, opacity = if (won) 1.0 else 0.5,
                )
            }
            items += centered(cellsX + row.sets.size * cellStep + 22.0, cy, row.games.toString(), ARIAL, cellSize, 0xCCCCCC, TextAnchor.CENTER, bold = false)
            items += centered(width - 24.0, cy, row.points, ARIAL, pointSize, look.accentRgb, TextAnchor.MIDDLE_RIGHT, outline = if (row.leading) 0.0 else 1.2)
        }
        if (look.credit != null) {
            items += SceneItem.Box(24.0, rowsBottom - 6.0, width - 48.0, 1.0, WHITE, 0.14)
            items += centered(width / 2, rowsBottom + footerH / 2 - 4.0, look.credit, ARIAL, 24.0, 0xCCCCCC, TextAnchor.CENTER, bold = false, opacity = 0.8)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Center Court: a light card with a navy title bar and a navy point column. */
    private fun centerCourt(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display)
        val navy = 0x13233F
        val title = look.title?.uppercase(Locale.US)
        val radius = 8.0
        val headerH = if (title != null) 40.0 else 0.0
        val rowH = 56.0
        val nameSize = 26.0
        val setSize = 27.0
        val pointSize = 34.0
        val titleSize = 18.0
        val titleSpacing = 1.4
        val nameX = 26.0
        val setColW = 50.0
        val pointColW = 80.0
        val columns = display.completedSets.size + 1

        val contentW = nameX + nameWidth(rows, SEGOE, nameSize, 150.0) + 22.0 + columns * setColW + pointColW
        val titleW = if (title != null) 18.0 + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + 18.0 else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = headerH + 2 * rowH
        val footerH = if (look.credit != null) 32.0 else 0.0
        val height = rowsBottom + footerH
        val bottomRadius = if (look.credit != null) 0.0 else radius
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, WHITE, look.opacity, Corners.all(radius))
        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, width, headerH, navy, look.opacity, Corners(radius, radius, 0.0, 0.0))
            items += SceneItem.Box(0.0, headerH - 3.0, width, 3.0, look.accentRgb, 1.0)
            items += centered(18.0, (headerH - 3.0) / 2, title, SEGOE, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        }
        items += SceneItem.Box(pointX, headerH, pointColW, 2 * rowH, navy, look.opacity, Corners(0.0, if (title == null) radius else 0.0, bottomRadius, 0.0))
        items += SceneItem.Box(0.0, headerH + rowH - 0.5, pointX, 1.0, 0xD9DEE7, 1.0)
        items += SceneItem.Box(pointX, headerH + rowH - 0.5, pointColW, 1.0, WHITE, 0.14)

        rows.forEachIndexed { index, row ->
            val top = headerH + index * rowH
            val cy = top + rowH / 2
            val barCorners = Corners(
                topLeft = if (index == 0 && title == null) radius else 0.0,
                bottomLeft = if (index == 1) bottomRadius else 0.0,
            )
            items += SceneItem.Box(0.0, top, 7.0, rowH, row.rgb, 1.0, barCorners)
            items += centered(nameX, cy, row.name, SEGOE, nameSize, navy, TextAnchor.MIDDLE_LEFT)
            // The winner of a completed set is navy and bold. The loser is light gray.
            row.sets.forEachIndexed { setIndex, games ->
                val won = row.wonSets[setIndex]
                items += centered(
                    setsX + setIndex * setColW + setColW / 2, cy, games.toString(), SEGOE, setSize,
                    if (won) navy else 0xA3AAB7, TextAnchor.CENTER, bold = won,
                )
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), SEGOE, setSize, navy, TextAnchor.CENTER)
            val pointRgb = if (row.leading) look.accentRgb else WHITE
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, pointRgb, TextAnchor.CENTER, opacity = if (row.trailing) 0.7 else 1.0)
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom, width, footerH, 0xEEF1F5, look.opacity, Corners(0.0, 0.0, radius, radius))
            items += SceneItem.Box(0.0, rowsBottom - 0.5, width, 1.0, 0xD9DEE7, 1.0)
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 19.2, 0x5E6878, TextAnchor.CENTER, bold = false, spacing = 0.6)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Compact: a slim two-row bar with an accent point column and an optional title tab. */
    private fun compact(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display)
        val title = look.title?.uppercase(Locale.US)
        val radius = 6.0
        val tabH = if (title != null) 26.0 else 0.0
        val rowH = 42.0
        val nameSize = 22.0
        val setSize = 22.0
        val pointSize = 25.0
        val titleSize = 15.0
        val titleSpacing = 1.2
        val nameX = 24.0
        val setColW = 38.0
        val pointColW = 60.0
        val columns = display.completedSets.size + 1
        val dark = 0x0B0F0D

        val width = nameX + nameWidth(rows, SEGOE, nameSize, 110.0) + 18.0 + columns * setColW + pointColW
        val tabW = if (title != null) {
            minOf(width, 10.0 + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + 10.0)
        } else {
            0.0
        }
        val rowsBottom = tabH + 2 * rowH
        val footerH = if (look.credit != null) 25.0 else 0.0
        val height = rowsBottom + footerH
        val bottomRadius = if (look.credit != null) 0.0 else radius
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, tabW, tabH, look.accentRgb, 1.0, Corners(radius, radius, 0.0, 0.0))
            items += centered(10.0, tabH / 2, title, SEGOE, titleSize, dark, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        }
        val boardCorners = Corners(if (title == null) radius else 0.0, radius, radius, radius)
        items += SceneItem.Box(0.0, tabH, width, 2 * rowH + footerH, BLACK, look.opacity, boardCorners)
        items += SceneItem.Box(pointX, tabH, pointColW, 2 * rowH, look.accentRgb, 1.0, Corners(0.0, radius, bottomRadius, 0.0))
        items += SceneItem.Box(0.0, tabH + rowH - 0.5, pointX, 1.0, WHITE, 0.12)
        items += SceneItem.Box(pointX, tabH + rowH - 0.5, pointColW, 1.0, dark, 0.18)

        rows.forEachIndexed { index, row ->
            val cy = tabH + index * rowH + rowH / 2
            items += SceneItem.Box(10.0, cy - 10.0, 4.0, 20.0, row.rgb, 1.0, Corners.all(2.0))
            items += centered(nameX, cy, row.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            // The winner of a completed set is white and bold. The loser is dim.
            row.sets.forEachIndexed { setIndex, games ->
                val won = row.wonSets[setIndex]
                items += centered(
                    setsX + setIndex * setColW + setColW / 2, cy, games.toString(), SEGOE, setSize,
                    if (won) WHITE else 0x7F8582, TextAnchor.CENTER, bold = won,
                )
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER)
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, dark, TextAnchor.CENTER, opacity = if (row.trailing) 0.55 else 1.0)
        }
        if (look.credit != null) {
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 16.8, WHITE, TextAnchor.CENTER, bold = false, opacity = 0.6, spacing = 0.5)
        }
        return ScoreboardScene(width, height, items)
    }
}
