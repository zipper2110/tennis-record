package org.litvin.export.scoreboard

import org.litvin.ScoreboardDisplay
import java.util.Locale
import kotlin.math.max

/**
 * More scoreboard styles. [ScoreboardLayouts] selects the style.
 *
 * All styles show the same data: the optional title, the two players with their optional colors, the optional serve
 * ball, the completed sets (the set winner is bold), the games of the current set, the points and the optional app
 * credit line.
 */
internal object ScoreboardMoreLayouts {
    private const val CREDIT_SIZE = 19.2

    /** Grass Court: deep green with a purple top stripe, a serif title and a cream point column. */
    fun grassCourt(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val green = 0x0E3B2A
        val purple = 0x5B2C83
        val cream = 0xF3EBD3
        val title = look.title
        val radius = 3.0
        val stripeH = 6.0
        val headerH = if (title != null) 46.0 else 0.0
        val rowH = 58.0
        val nameSize = 25.0
        val setSize = 27.0
        val pointSize = 34.0
        val titleSize = 24.0
        val nameX = if (look.playerColors) 26.0 else 20.0
        val setColW = 50.0
        val pointColW = 84.0
        val columns = display.completedSets.size + 1

        val top = stripeH + headerH
        val serveW = serveColumnWidth(display, look, 28.0)
        val contentW = nameX + nameWidth(rows, GEORGIA, nameSize, 150.0) + 22.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) 20.0 + ScoreboardFonts.textWidth(title, GEORGIA, true, titleSize) + 20.0 else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = top + 2 * rowH
        val footerH = if (look.credit != null) 32.0 else 0.0
        val height = rowsBottom + footerH
        val bottomRadius = if (look.credit != null) 0.0 else radius
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, green, look.opacity, Corners.all(radius))
        items += SceneItem.Box(0.0, 0.0, width, stripeH, purple, 1.0, Corners(radius, radius, 0.0, 0.0))
        if (title != null) {
            items += centered(20.0, stripeH + headerH / 2, title, GEORGIA, titleSize, look.accentRgb, TextAnchor.MIDDLE_LEFT)
            items += SceneItem.Box(0.0, top - 1.0, width, 1.0, look.accentRgb, 0.35)
        }
        items += SceneItem.Box(pointX, top, pointColW, 2 * rowH, cream, look.opacity, Corners(0.0, 0.0, bottomRadius, 0.0))
        items += SceneItem.Box(0.0, top + rowH - 0.5, pointX, 1.0, WHITE, 0.14)
        items += SceneItem.Box(pointX, top + rowH - 0.5, pointColW, 1.0, green, 0.18)

        rows.forEachIndexed { index, row ->
            val rowTop = top + index * rowH
            val cy = rowTop + rowH / 2
            if (look.playerColors) {
                items += SceneItem.Box(0.0, rowTop, 6.0, rowH, row.rgb, 1.0, Corners(bottomLeft = if (index == 1) bottomRadius else 0.0))
            }
            items += centered(nameX, cy, row.name, GEORGIA, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, WHITE, 0x7FA08F)
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), SEGOE, setSize, 0xE9F1EC, TextAnchor.CENTER)
            if (row.leading) {
                val corners = Corners(bottomRight = if (index == 1) bottomRadius else 0.0)
                items += SceneItem.Box(pointX, rowTop, pointColW, rowH, look.accentRgb, 0.45, corners)
            }
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, green, TextAnchor.CENTER, opacity = if (row.trailing) 0.5 else 1.0)
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom - 0.5, width, 1.0, WHITE, 0.14)
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, GEORGIA, CREDIT_SIZE, 0xB9CBBF, TextAnchor.CENTER, bold = false)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Clay Court: terracotta with a dark title bar, a dark point column and cream leading points. */
    fun clayCourt(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val clay = 0xB5532D
        val dark = 0x8E3D1F
        val deep = 0x6E2E16
        val title = look.title?.uppercase(Locale.US)
        val radius = 6.0
        val headerH = if (title != null) 42.0 else 0.0
        val rowH = 56.0
        val nameSize = 25.0
        val setSize = 27.0
        val pointSize = 34.0
        val titleSize = 19.0
        val titleSpacing = 1.4
        val nameX = if (look.playerColors) 44.0 else 18.0
        val setColW = 50.0
        val pointColW = 82.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 28.0)
        val contentW = nameX + nameWidth(rows, TREBUCHET, nameSize, 150.0) + 20.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) 18.0 + ScoreboardFonts.textWidth(title, TREBUCHET, true, titleSize, titleSpacing) + 18.0 else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = headerH + 2 * rowH
        val footerH = if (look.credit != null) 32.0 else 0.0
        val height = rowsBottom + footerH
        val bottomRadius = if (look.credit != null) 0.0 else radius
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val gamesX = setsX + (columns - 1) * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, clay, look.opacity, Corners.all(radius))
        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, width, headerH, dark, look.opacity, Corners(radius, radius, 0.0, 0.0))
            items += centered(18.0, headerH / 2, title, TREBUCHET, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        }
        items += SceneItem.Box(gamesX, headerH, setColW, 2 * rowH, dark, 0.35)
        items += SceneItem.Box(pointX, headerH, pointColW, 2 * rowH, deep, look.opacity, Corners(0.0, if (title == null) radius else 0.0, bottomRadius, 0.0))
        items += SceneItem.Box(0.0, headerH + rowH - 0.5, width, 1.0, WHITE, 0.22)

        rows.forEachIndexed { index, row ->
            val cy = headerH + index * rowH + rowH / 2
            if (look.playerColors) items += SceneItem.Box(18.0, cy - 7.0, 14.0, 14.0, row.rgb, 1.0, Corners.all(2.0))
            items += centered(nameX, cy, row.name, TREBUCHET, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, TREBUCHET, setSize, WHITE, 0xF2C9B5, lostOpacity = 0.75)
            }
            items += centered(gamesX + setColW / 2, cy, row.games.toString(), TREBUCHET, setSize, WHITE, TextAnchor.CENTER)
            val pointRgb = if (row.leading) look.accentRgb else WHITE
            items += centered(pointX + pointColW / 2, cy, row.points, TREBUCHET, pointSize, pointRgb, TextAnchor.CENTER, opacity = if (row.trailing) 0.6 else 1.0)
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom, width, footerH, dark, look.opacity, Corners(0.0, 0.0, radius, radius))
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, TREBUCHET, CREDIT_SIZE, 0xF7D9C9, TextAnchor.CENTER, bold = false, opacity = 0.9)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Hard Court: a blue court with a green title bar, white court lines and ball-yellow leading points. */
    fun hardCourt(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val blue = 0x1C4E96
        val green = 0x2E7A4E
        val title = look.title?.uppercase(Locale.US)
        val radius = 4.0
        val headerH = if (title != null) 40.0 else 0.0
        val rowH = 56.0
        val nameSize = 24.0
        val setSize = 26.0
        val pointSize = 34.0
        val titleSize = 18.0
        val titleSpacing = 1.4
        val nameX = if (look.playerColors) 44.0 else 18.0
        val setColW = 50.0
        val pointColW = 82.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 28.0)
        val contentW = nameX + nameWidth(rows, TAHOMA, nameSize, 150.0) + 20.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) 18.0 + ScoreboardFonts.textWidth(title, TAHOMA, true, titleSize, titleSpacing) + 18.0 else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = headerH + 2 * rowH
        val footerH = if (look.credit != null) 32.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, blue, look.opacity, Corners.all(radius))
        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, width, headerH, green, look.opacity, Corners(radius, radius, 0.0, 0.0))
            items += centered(18.0, headerH / 2, title, TAHOMA, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
            items += SceneItem.Box(0.0, headerH - 1.0, width, 2.0, WHITE, 0.9)
        }
        // White court lines between the rows and before the point column.
        items += SceneItem.Box(0.0, headerH + rowH - 1.0, width, 2.0, WHITE, 0.85)
        items += SceneItem.Box(pointX - 1.0, headerH, 2.0, 2 * rowH, WHITE, 0.85)

        rows.forEachIndexed { index, row ->
            val cy = headerH + index * rowH + rowH / 2
            if (look.playerColors) items += SceneItem.Box(20.0, cy - 6.0, 12.0, 12.0, row.rgb, 1.0, Corners.all(6.0))
            items += centered(nameX, cy, row.name, TAHOMA, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, TAHOMA, setSize, WHITE, 0x8FB0D9)
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), TAHOMA, setSize, WHITE, TextAnchor.CENTER)
            val pointRgb = if (row.leading) look.accentRgb else WHITE
            items += centered(pointX + pointColW / 2, cy, row.points, TAHOMA, pointSize, pointRgb, TextAnchor.CENTER, opacity = if (row.trailing) 0.6 else 1.0)
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom - 1.0, width, 2.0, WHITE, 0.85)
            items += SceneItem.Box(0.0, rowsBottom + 1.0, width, footerH - 1.0, green, look.opacity, Corners(0.0, 0.0, radius, radius))
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, TAHOMA, 18.0, 0xDDEBDF, TextAnchor.CENTER, bold = false)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Night Session: a near-black board with a thin accent frame and bright accent points. */
    fun nightSession(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val night = 0x06080F
        val muted = 0x566074
        val title = look.title?.uppercase(Locale.US)
        val border = 2.0
        val padX = 18.0
        val headerH = if (title != null) 44.0 else 0.0
        val rowH = 58.0
        val nameSize = 25.0
        val setSize = 27.0
        val pointSize = 38.0
        val titleSize = 17.0
        val titleSpacing = 3.0
        val nameX = if (look.playerColors) padX + 4.0 + 14.0 else padX
        val setColW = 50.0
        val pointColW = 90.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 28.0)
        val contentW = nameX + nameWidth(rows, SEGOE, nameSize, 150.0) + 22.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) padX + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + padX else 0.0
        val width = max(contentW, titleW)
        val top = if (title != null) headerH else border
        val rowsBottom = top + 2 * rowH
        val footerH = if (look.credit != null) 32.0 else 0.0
        val height = rowsBottom + footerH + border
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, night, look.opacity)
        if (title != null) {
            items += centered(padX, headerH / 2 + 1.0, title, SEGOE, titleSize, look.accentRgb, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
            items += SceneItem.Box(padX, headerH - 1.0, width - 2 * padX, 1.0, look.accentRgb, 0.35)
        }
        items += SceneItem.Box(padX, top + rowH - 0.5, width - 2 * padX, 1.0, WHITE, 0.08)

        rows.forEachIndexed { index, row ->
            val cy = top + index * rowH + rowH / 2
            if (look.playerColors) items += SceneItem.Box(padX, cy - 14.0, 4.0, 28.0, row.rgb, 1.0, Corners.all(2.0))
            items += centered(nameX, cy, row.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, WHITE, muted)
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), SEGOE, setSize, 0xE6F9FF, TextAnchor.CENTER)
            val pointRgb = when {
                row.leading -> look.accentRgb
                row.trailing -> muted
                else -> WHITE
            }
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, pointRgb, TextAnchor.CENTER)
        }
        if (look.credit != null) {
            items += SceneItem.Box(padX, rowsBottom - 0.5, width - 2 * padX, 1.0, look.accentRgb, 0.2)
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 17.0, muted, TextAnchor.CENTER, bold = false, spacing = 2.0)
        }
        items += frame(0.0, 0.0, width, height, border, look.accentRgb)
        return ScoreboardScene(width, height, items)
    }

    /** LED Board: a black stadium board with one-color digits in dark tiles. */
    fun ledBoard(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val board = 0x0B0B0B
        val tile = 0x1C1C1C
        val led = look.accentRgb
        val title = look.title?.uppercase(Locale.US)
        val border = 3.0
        val padX = 16.0
        val headerH = if (title != null) 42.0 else 0.0
        val rowH = 52.0
        val tileH = 40.0
        val nameSize = 24.0
        val digitSize = 30.0
        val pointSize = 32.0
        val titleSize = 19.0
        val titleSpacing = 2.0
        val nameX = if (look.playerColors) padX + 12.0 + 12.0 else padX
        val cellW = 42.0
        val pointTileW = 72.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 26.0)
        val contentW = nameX + nameWidth(rows, CONSOLAS, nameSize, 140.0) + 16.0 + serveW + columns * cellW + 8.0 + pointTileW + padX
        val titleW = if (title != null) padX + ScoreboardFonts.textWidth(title, CONSOLAS, true, titleSize, titleSpacing) + padX else 0.0
        val width = max(contentW, titleW)
        val top = if (title != null) headerH + 4.0 else 8.0
        val rowsBottom = top + 2 * rowH + 6.0
        val footerH = if (look.credit != null) 30.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - padX - pointTileW
        val cellsX = pointX - 8.0 - columns * cellW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, board, look.opacity)
        if (title != null) {
            items += centered(padX, headerH / 2 + 2.0, title, CONSOLAS, titleSize, led, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
            items += SceneItem.Box(padX, headerH, width - 2 * padX, 1.0, led, 0.25)
        }

        rows.forEachIndexed { index, row ->
            val cy = top + index * rowH + rowH / 2
            if (look.playerColors) items += SceneItem.Box(padX, cy - 6.0, 12.0, 12.0, row.rgb, 1.0)
            items += centered(nameX, cy, row.name, CONSOLAS, nameSize, led, TextAnchor.MIDDLE_LEFT, opacity = 0.95)
            if (row.serving) items += serveBall(cellsX - serveW / 2, cy, 12.0, led)
            for (cell in 0 until columns) {
                items += SceneItem.Box(cellsX + cell * cellW + 3.0, cy - tileH / 2, cellW - 6.0, tileH, tile, 1.0, Corners.all(3.0))
            }
            row.sets.indices.forEach { setIndex ->
                items += setGames(cellsX + setIndex * cellW + cellW / 2, cy, row, setIndex, CONSOLAS, digitSize, led, led, lostOpacity = 0.4)
            }
            items += centered(cellsX + row.sets.size * cellW + cellW / 2, cy, row.games.toString(), CONSOLAS, digitSize, led, TextAnchor.CENTER)
            items += SceneItem.Box(pointX, cy - tileH / 2, pointTileW, tileH, tile, 1.0, Corners.all(3.0))
            items += centered(pointX + pointTileW / 2, cy, row.points, CONSOLAS, pointSize, led, TextAnchor.CENTER, opacity = if (row.trailing) 0.5 else 1.0)
        }
        if (look.credit != null) {
            items += centered(width / 2, rowsBottom + footerH / 2 - 2.0, look.credit, CONSOLAS, 17.0, led, TextAnchor.CENTER, bold = false, opacity = 0.5, spacing = 1.0)
        }
        items += frame(0.0, 0.0, width, height, border, 0x2B2B2B)
        return ScoreboardScene(width, height, items)
    }

    /** Minimal: outlined text on a faint rounded shade. The background opacity sets the shade. */
    fun minimal(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val title = look.title?.uppercase(Locale.US)
        val outline = 1.4
        val radius = 10.0
        val padX = 18.0
        val headerH = if (title != null) 38.0 else 0.0
        val rowH = 46.0
        val nameSize = 25.0
        val setSize = 26.0
        val pointSize = 32.0
        val titleSize = 18.0
        val titleSpacing = 1.6
        val nameX = if (look.playerColors) padX + 10.0 + 12.0 else padX
        val setColW = 44.0
        val pointColW = 70.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 26.0)
        val contentW = nameX + nameWidth(rows, SEGOE, nameSize, 130.0) + 18.0 + serveW + columns * setColW + pointColW + 8.0
        val titleW = if (title != null) padX + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + padX else 0.0
        val width = max(contentW, titleW)
        val top = if (title != null) headerH else 8.0
        val rowsBottom = top + 2 * rowH + 6.0
        val footerH = if (look.credit != null) 28.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - 8.0 - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, BLACK, look.opacity, Corners.all(radius))
        if (title != null) {
            items += centered(padX, headerH / 2 + 4.0, title, SEGOE, titleSize, WHITE, TextAnchor.MIDDLE_LEFT, opacity = 0.85, spacing = titleSpacing, outline = outline)
        }
        rows.forEachIndexed { index, row ->
            val cy = top + index * rowH + rowH / 2
            if (look.playerColors) items += SceneItem.Box(padX, cy - 5.0, 10.0, 10.0, row.rgb, 1.0, Corners.all(5.0))
            items += centered(nameX, cy, row.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT, outline = outline)
            // A black ring, as the text outline, keeps the ball visible on a light video.
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 14.0, look.accentRgb, ringRgb = BLACK)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, WHITE, WHITE, lostOpacity = 0.6, outline = outline)
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER, outline = outline)
            val pointRgb = if (row.leading) look.accentRgb else WHITE
            items += centered(
                pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, pointRgb, TextAnchor.CENTER,
                opacity = if (row.trailing) 0.6 else 1.0, outline = outline,
            )
        }
        if (look.credit != null) {
            items += centered(width / 2, rowsBottom + footerH / 2 - 4.0, look.credit, SEGOE, 17.0, WHITE, TextAnchor.CENTER, bold = false, opacity = 0.7, outline = 1.0)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Tiles: every part of the board is a separate rounded tile with small gaps. */
    fun tiles(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val tile = 0x15181D
        val tileHigh = 0x262B33
        val light = 0xF2F4F7
        val ink = 0x0B0F0D
        val title = look.title?.uppercase(Locale.US)
        val gap = 4.0
        val radius = 6.0
        val titleH = if (title != null) 34.0 else 0.0
        val rowH = 48.0
        val nameSize = 23.0
        val setSize = 24.0
        val pointSize = 28.0
        val titleSize = 16.0
        val titleSpacing = 1.2
        val colorW = 8.0
        // Without the player colors, the name tile starts at the left edge.
        val nameTileX = if (look.playerColors) colorW + gap else 0.0
        val setTileW = 42.0
        val pointTileW = 66.0
        val columns = display.completedSets.size + 1

        val titleW = if (title != null) 14.0 + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + 14.0 else 0.0
        val cellsW = columns * (setTileW + gap) + pointTileW
        val serveW = serveColumnWidth(display, look, 24.0)
        val nameTileW = max(16.0 + nameWidth(rows, SEGOE, nameSize, 120.0) + serveW + 16.0, titleW - nameTileX - gap - cellsW)
        val width = nameTileX + nameTileW + gap + cellsW
        val top = if (title != null) titleH + gap else 0.0
        val rowsBottom = top + 2 * rowH + gap
        val creditH = if (look.credit != null) 28.0 else 0.0
        val height = if (look.credit != null) rowsBottom + gap + creditH else rowsBottom
        val cellsX = nameTileX + nameTileW + gap
        val pointX = width - pointTileW
        val items = mutableListOf<SceneItem>()

        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, titleW, titleH, look.accentRgb, 1.0, Corners.all(radius))
            items += centered(14.0, titleH / 2, title, SEGOE, titleSize, ink, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        }
        rows.forEachIndexed { index, row ->
            val y = top + index * (rowH + gap)
            val cy = y + rowH / 2
            if (look.playerColors) items += SceneItem.Box(0.0, y, colorW, rowH, row.rgb, 1.0, Corners.all(3.0))
            items += SceneItem.Box(nameTileX, y, nameTileW, rowH, tile, look.opacity, Corners.all(radius))
            items += centered(nameTileX + 16.0, cy, row.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
            if (row.serving) items += serveBall(nameTileX + nameTileW - 12.0 - serveW / 2, cy, 12.0, look.accentRgb)
            for (cell in 0 until columns) {
                val current = cell == columns - 1
                items += SceneItem.Box(cellsX + cell * (setTileW + gap), y, setTileW, rowH, if (current) tileHigh else tile, look.opacity, Corners.all(radius))
            }
            row.sets.indices.forEach { setIndex ->
                items += setGames(cellsX + setIndex * (setTileW + gap) + setTileW / 2, cy, row, setIndex, SEGOE, setSize, WHITE, 0x6F7682)
            }
            items += centered(cellsX + row.sets.size * (setTileW + gap) + setTileW / 2, cy, row.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER)
            items += SceneItem.Box(pointX, y, pointTileW, rowH, if (row.leading) look.accentRgb else light, look.opacity, Corners.all(radius))
            items += centered(pointX + pointTileW / 2, cy, row.points, SEGOE, pointSize, ink, TextAnchor.CENTER, opacity = if (row.trailing) 0.55 else 1.0)
        }
        if (look.credit != null) {
            val y = rowsBottom + gap
            items += SceneItem.Box(0.0, y, width, creditH, tile, look.opacity, Corners.all(radius))
            items += centered(width / 2, y + creditH / 2, look.credit, SEGOE, 16.8, 0x9AA1AC, TextAnchor.CENTER, bold = false, spacing = 0.5)
        }
        return ScoreboardScene(width, height, items)
    }

    /**
     * Ticker: one horizontal bar. Player 1 is on the left and player 2 is on the right.
     * The points are in the middle. The sets of player 2 are in mirror order, so each set is at the same
     * distance from the middle for both players.
     */
    fun ticker(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val bar = 0x101418
        val ink = 0x0B0F0D
        val title = look.title?.uppercase(Locale.US)
        val radius = 6.0
        val tabH = if (title != null) 28.0 else 0.0
        val barH = 56.0
        val nameSize = 24.0
        val setSize = 25.0
        val pointSize = 30.0
        val titleSize = 15.0
        val titleSpacing = 1.2
        val colorW = if (look.playerColors) 6.0 else 0.0
        val padX = 14.0
        val setW = 40.0
        val pointCellW = 58.0
        val columns = display.completedSets.size + 1
        val first = rows[0]
        val second = rows[1]

        val nameW = nameWidth(rows, SEGOE, nameSize, 100.0)
        val serveW = serveColumnWidth(display, look, 24.0)
        val sideW = colorW + padX + nameW + 16.0 + serveW + columns * setW
        val width = 2 * sideW + 2 * pointCellW
        val tabW = if (title != null) minOf(width, 10.0 + ScoreboardFonts.textWidth(title, SEGOE, true, titleSize, titleSpacing) + 10.0) else 0.0
        val footerH = if (look.credit != null) 26.0 else 0.0
        val height = tabH + barH + footerH
        val barBottomRadius = if (look.credit != null) 0.0 else radius
        val cy = tabH + barH / 2
        val leftSetsX = sideW - columns * setW
        val middleX = sideW
        val rightSetsX = sideW + 2 * pointCellW
        val items = mutableListOf<SceneItem>()

        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, tabW, tabH, look.accentRgb, 1.0, Corners(radius, radius, 0.0, 0.0))
            items += centered(10.0, tabH / 2, title, SEGOE, titleSize, ink, TextAnchor.MIDDLE_LEFT, spacing = titleSpacing)
        }
        val topLeft = if (title != null) 0.0 else radius
        items += SceneItem.Box(0.0, tabH, width, barH + footerH, bar, look.opacity, Corners(topLeft, radius, radius, radius))
        if (look.playerColors) {
            items += SceneItem.Box(0.0, tabH, colorW, barH, first.rgb, 1.0, Corners(topLeft = topLeft, bottomLeft = barBottomRadius))
            items += SceneItem.Box(width - colorW, tabH, colorW, barH, second.rgb, 1.0, Corners(topRight = radius, bottomRight = barBottomRadius))
        }
        items += SceneItem.Box(leftSetsX + first.sets.size * setW, tabH, setW, barH, WHITE, 0.07)
        items += SceneItem.Box(rightSetsX, tabH, setW, barH, WHITE, 0.07)

        items += centered(colorW + padX, cy, first.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_LEFT)
        // The serve ball of each player is on the outer side of the sets, next to the name.
        if (first.serving) items += serveBall(leftSetsX - serveW / 2, cy, 12.0, look.accentRgb)
        if (second.serving) items += serveBall(rightSetsX + columns * setW + serveW / 2, cy, 12.0, look.accentRgb)
        first.sets.indices.forEach { setIndex ->
            items += setGames(leftSetsX + setIndex * setW + setW / 2, cy, first, setIndex, SEGOE, setSize, WHITE, 0x6E7571)
        }
        items += centered(leftSetsX + first.sets.size * setW + setW / 2, cy, first.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER)

        items += SceneItem.Box(middleX, tabH, 2 * pointCellW, barH, look.accentRgb, 1.0)
        items += SceneItem.Box(middleX + pointCellW - 0.5, tabH + 12.0, 1.0, barH - 24.0, ink, 0.3)
        items += centered(middleX + pointCellW / 2, cy, first.points, SEGOE, pointSize, ink, TextAnchor.CENTER, opacity = if (first.trailing) 0.5 else 1.0)
        items += centered(middleX + pointCellW * 1.5, cy, second.points, SEGOE, pointSize, ink, TextAnchor.CENTER, opacity = if (second.trailing) 0.5 else 1.0)

        items += centered(rightSetsX + setW / 2, cy, second.games.toString(), SEGOE, setSize, WHITE, TextAnchor.CENTER)
        second.sets.indices.forEach { setIndex ->
            val fromMiddle = second.sets.size - setIndex
            items += setGames(rightSetsX + fromMiddle * setW + setW / 2, cy, second, setIndex, SEGOE, setSize, WHITE, 0x6E7571)
        }
        items += centered(width - colorW - padX, cy, second.name, SEGOE, nameSize, WHITE, TextAnchor.MIDDLE_RIGHT)

        if (look.credit != null) {
            items += SceneItem.Box(0.0, tabH + barH - 0.5, width, 1.0, WHITE, 0.1)
            items += centered(width / 2, tabH + barH + footerH / 2, look.credit, SEGOE, 16.8, WHITE, TextAnchor.CENTER, bold = false, opacity = 0.55, spacing = 0.5)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Retro: a brown board with orange and yellow stripes, heavy names and pill-shaped points. */
    fun retro(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val brown = 0x2B1A12
        val cream = 0xF6E7C8
        val stripes = listOf(0xD9502B, 0xE8893B, 0xF2C14E)
        val title = look.title?.uppercase(Locale.US)
        val radius = 10.0
        val padX = 18.0
        val headerH = if (title != null) 46.0 else 0.0
        val stripeH = 4.0
        val rowH = 54.0
        val nameSize = 24.0
        val setSize = 28.0
        val pointSize = 32.0
        val titleSize = 22.0
        val titleSpacing = 1.0
        val nameX = if (look.playerColors) padX + 14.0 + 12.0 else padX
        val setColW = 48.0
        val pointColW = 84.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 28.0)
        val contentW = nameX + nameWidth(rows, ARIAL_BLACK, nameSize, 150.0, bold = false) + 20.0 + serveW + columns * setColW + pointColW + 6.0
        val titleW = if (title != null) padX + ScoreboardFonts.textWidth(title, ARIAL_BLACK, false, titleSize, titleSpacing) + padX else 0.0
        val width = max(contentW, titleW)
        val stripeTop = if (title != null) headerH - 4.0 else 12.0
        val top = stripeTop + stripes.size * stripeH + 6.0
        val rowsBottom = top + 2 * rowH + 6.0
        val footerH = if (look.credit != null) 30.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - 6.0 - pointColW
        val setsX = pointX - columns * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, brown, look.opacity, Corners.all(radius))
        if (title != null) {
            items += centered(padX, (headerH - 4.0) / 2 + 2.0, title, ARIAL_BLACK, titleSize, cream, TextAnchor.MIDDLE_LEFT, bold = false, spacing = titleSpacing)
        }
        stripes.forEachIndexed { index, rgb ->
            items += SceneItem.Box(padX - 4.0, stripeTop + index * stripeH, width - 2 * (padX - 4.0), stripeH, rgb, 1.0)
        }
        rows.forEachIndexed { index, row ->
            val rowTop = top + index * rowH
            val cy = rowTop + rowH / 2
            if (look.playerColors) items += SceneItem.Box(padX, cy - 7.0, 14.0, 14.0, row.rgb, 1.0, Corners.all(7.0))
            items += centered(nameX, cy, row.name, ARIAL_BLACK, nameSize, cream, TextAnchor.MIDDLE_LEFT, bold = false)
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 13.0, look.accentRgb)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, ARIAL, setSize, cream, 0x9C7B62)
            }
            items += centered(setsX + row.sets.size * setColW + setColW / 2, cy, row.games.toString(), ARIAL, setSize, cream, TextAnchor.CENTER)
            // The leading player's points are in a solid pill. The other pill is faint.
            items += SceneItem.Box(pointX + 6.0, rowTop + 6.0, pointColW - 12.0, rowH - 12.0, look.accentRgb, if (row.leading) 1.0 else 0.22, Corners.all(21.0))
            val pointRgb = if (row.leading) brown else cream
            items += centered(pointX + pointColW / 2, cy, row.points, ARIAL, pointSize, pointRgb, TextAnchor.CENTER, opacity = if (row.trailing) 0.7 else 1.0)
        }
        if (look.credit != null) {
            items += centered(width / 2, rowsBottom + footerH / 2 - 4.0, look.credit, ARIAL, 18.0, 0xC9A98A, TextAnchor.CENTER, bold = false)
        }
        return ScoreboardScene(width, height, items)
    }

    /** Bold Block: white rows with heavy black names, a black title bar and a black point column. */
    fun boldBlock(display: ScoreboardDisplay, look: Look): ScoreboardScene {
        val rows = rows(display, look)
        val ink = 0x0A0A0A
        val title = look.title?.uppercase(Locale.US)
        val headerH = if (title != null) 44.0 else 0.0
        val accentBarH = 4.0
        val rowH = 56.0
        val nameSize = 26.0
        val setSize = 27.0
        val pointSize = 34.0
        val titleSize = 20.0
        val titleSpacing = 1.0
        val colorW = 10.0
        val nameX = if (look.playerColors) colorW + 16.0 else 18.0
        val setColW = 50.0
        val pointColW = 86.0
        val columns = display.completedSets.size + 1

        val serveW = serveColumnWidth(display, look, 28.0)
        val contentW = nameX + nameWidth(rows, SEGOE_BLACK, nameSize, 150.0, bold = false) + 20.0 + serveW + columns * setColW + pointColW
        val titleW = if (title != null) 18.0 + ScoreboardFonts.textWidth(title, SEGOE_BLACK, false, titleSize, titleSpacing) + 18.0 else 0.0
        val width = max(contentW, titleW)
        val rowsBottom = headerH + 2 * rowH
        val footerH = if (look.credit != null) 30.0 else 0.0
        val height = rowsBottom + footerH
        val pointX = width - pointColW
        val setsX = pointX - columns * setColW
        val gamesX = setsX + (columns - 1) * setColW
        val items = mutableListOf<SceneItem>()

        items += SceneItem.Box(0.0, 0.0, width, height, WHITE, look.opacity)
        if (title != null) {
            items += SceneItem.Box(0.0, 0.0, width, headerH, ink, look.opacity)
            items += SceneItem.Box(0.0, headerH - accentBarH, width, accentBarH, look.accentRgb, 1.0)
            items += centered(18.0, (headerH - accentBarH) / 2, title, SEGOE_BLACK, titleSize, look.accentRgb, TextAnchor.MIDDLE_LEFT, bold = false, spacing = titleSpacing)
        }
        items += SceneItem.Box(gamesX, headerH, setColW, 2 * rowH, 0xE6E6E6, look.opacity)
        items += SceneItem.Box(pointX, headerH, pointColW, 2 * rowH, ink, look.opacity)
        items += SceneItem.Box(0.0, headerH + rowH - 1.0, pointX, 2.0, ink, 0.1)

        rows.forEachIndexed { index, row ->
            val rowTop = headerH + index * rowH
            val cy = rowTop + rowH / 2
            if (look.playerColors) items += SceneItem.Box(0.0, rowTop, colorW, rowH, row.rgb, 1.0)
            items += centered(nameX, cy, row.name, SEGOE_BLACK, nameSize, ink, TextAnchor.MIDDLE_LEFT, bold = false)
            // The accent color is light, so a black ring makes the ball visible on the white rows.
            if (row.serving) items += serveBall(setsX - serveW / 2, cy, 15.0, look.accentRgb, ringRgb = ink)
            row.sets.indices.forEach { setIndex ->
                items += setGames(setsX + setIndex * setColW + setColW / 2, cy, row, setIndex, SEGOE, setSize, ink, 0xA3A3A3)
            }
            items += centered(gamesX + setColW / 2, cy, row.games.toString(), SEGOE, setSize, ink, TextAnchor.CENTER)
            val pointRgb = if (row.leading) look.accentRgb else WHITE
            items += centered(pointX + pointColW / 2, cy, row.points, SEGOE, pointSize, pointRgb, TextAnchor.CENTER, opacity = if (row.trailing) 0.55 else 1.0)
        }
        if (look.credit != null) {
            items += SceneItem.Box(0.0, rowsBottom, width, footerH, ink, look.opacity)
            items += centered(width / 2, rowsBottom + footerH / 2, look.credit, SEGOE, 17.0, WHITE, TextAnchor.CENTER, bold = false, opacity = 0.75, spacing = 0.8)
        }
        return ScoreboardScene(width, height, items)
    }
}
