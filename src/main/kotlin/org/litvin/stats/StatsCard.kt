package org.litvin.stats

import org.litvin.export.scoreboard.Corners
import org.litvin.export.scoreboard.SEGOE
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScenePoint
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.scoring.PerPlayer
import org.litvin.scoring.ScoreboardSettingsV1

/**
 * The content of a statistics card: who played, the score, and the rows that the card shows.
 * [rows] holds only rows that have a value.
 */
data class StatsCardContent(
    val names: PerPlayer<String>,
    val colors: PerPlayer<Int>,
    val title: String,
    val score: String,
    val rows: List<StatRow>,
    /** The line at the bottom of the card, or null when the card has no such line. */
    val credit: String? = null,
    /** The chart on the last page, or null when the card has no chart page. */
    val momentum: Momentum? = null,
    /** The opacity of the card panel, from 0 to 1. */
    val panelOpacity: Double = 1.0 - StatsSettingsV1.DEFAULT_CARD_TRANSPARENCY_PERCENT / 100.0,
)

/**
 * Draws the statistics card of the exported video, as one [ScoreboardScene] for each page.
 *
 * The scene covers the full video frame. Its units are pixels of a 1080p frame: the scene is 1080 units high,
 * and its width follows the frame aspect ratio. The export draws the pages with libass, and the Stats tab
 * draws the same pages with Java2D.
 */
object StatsCard {
    const val HEIGHT = 1080.0
    const val WIDTH_16_9 = 1920.0
    const val ROWS_PER_PAGE = 7

    /** The neutral bar colors, when the scoreboard does not show the player colors. */
    const val NEUTRAL_P1 = 0xF2F2F2
    const val NEUTRAL_P2 = 0x8A8A8A

    /**
     * Makes the card content for the full match ([scope] 0) or for one set ([scope] 1 and more).
     * The rows are the rows that [settings] selects for the video and that have a value.
     * The card makes dark player colors lighter, so that they show on the dark panel.
     */
    fun content(report: StatsReport, settings: StatsSettingsV1, scope: Int = 0): StatsCardContent {
        val score = report.score
        val scoreboard = score.scoreboard.normalized()
        val stats = if (scope == 0) report.match else report.sets[scope - 1]
        val setScores = report.setScores()
        val colors = if (scoreboard.showPlayerColors) {
            PerPlayer(onPanel(rgb(score.player1ColorHex, NEUTRAL_P1)), onPanel(rgb(score.player2ColorHex, NEUTRAL_P2)))
        } else {
            PerPlayer(NEUTRAL_P1, NEUTRAL_P2)
        }
        return StatsCardContent(
            names = PerPlayer(score.player1Name.trim().ifEmpty { "Player 1" }, score.player2Name.trim().ifEmpty { "Player 2" }),
            colors = colors,
            title = if (scope == 0) "Match statistics" else "Set $scope statistics",
            score = if (scope == 0) setScores.joinToString("   ") else setScores.getOrElse(scope - 1) { "" },
            rows = StatRows.build(stats, score.rules.normalized(), settings).filter { it.available && settings.inVideo(it.stat) },
            credit = ScoreboardSettingsV1.APP_CREDIT.takeIf { scoreboard.showAppCredit },
            momentum = Momentum.of(report, scope).takeIf { settings.videoMomentum && it.points.size >= MIN_CHART_POINTS },
            panelOpacity = 1.0 - settings.normalized().cardTransparencyPercent / 100.0,
        )
    }

    /**
     * The pages of the card, with at most [ROWS_PER_PAGE] rows on each page, and then the momentum chart.
     * A card without rows and without a chart has no pages.
     */
    fun pages(content: StatsCardContent, frameWidth: Double = WIDTH_16_9): List<ScoreboardScene> {
        val pageCount = (content.rows.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE
        val chunks = splitRows(content.rows, pageCount)
        val chart = content.momentum
        val count = chunks.size + if (chart != null) 1 else 0
        // All pages have the same height, so the card does not change size between pages.
        // The chart needs the height of some rows.
        val mostRows = chunks.maxOfOrNull { it.size } ?: 0
        val slots = if (chart != null) maxOf(mostRows, CHART_SLOTS) else mostRows
        val pages = chunks.mapIndexed { index, rows ->
            page(content, slots, index, count, frameWidth) { area -> rowItems(content, rows, area) }
        }
        return if (chart == null) pages else pages + page(content, slots, chunks.size, count, frameWidth) { area ->
            chartItems(content, chart, area)
        }
    }

    /**
     * Splits [rows] into [pageCount] pages with at most [ROWS_PER_PAGE] rows.
     * When possible, the pages break only between groups, so that a group stays on one page.
     * Of these splits, the split with the smallest largest page wins.
     * When no such split is possible, the pages share the rows equally, for example 8 rows into 4 + 4 and not 7 + 1.
     */
    internal fun splitRows(rows: List<StatRow>, pageCount: Int): List<List<StatRow>> {
        if (pageCount <= 0 || rows.isEmpty()) return emptyList()
        val groupStarts = rows.indices.filter { it > 0 && rows[it].stat.group != rows[it - 1].stat.group }
        var best: List<Int>? = null
        fun pageSizes(breaks: List<Int>) = (listOf(0) + breaks).zip(breaks + rows.size) { start, end -> end - start }
        fun search(from: Int, breaks: List<Int>) {
            if (breaks.size == pageCount - 1) {
                val sizes = pageSizes(breaks)
                if (sizes.max() <= ROWS_PER_PAGE && (best == null || sizes.max() < pageSizes(best!!).max())) best = breaks
                return
            }
            for (index in from until groupStarts.size) search(index + 1, breaks + groupStarts[index])
        }
        search(0, emptyList())
        val breaks = best ?: return rows.chunked((rows.size + pageCount - 1) / pageCount)
        return (listOf(0) + breaks).zip(breaks + rows.size) { start, end -> rows.subList(start, end) }
    }

    /** The part of a page between the header and the footer. */
    private class Area(val left: Double, val right: Double, val top: Double, val bottom: Double) {
        val center: Double get() = (left + right) / 2
    }

    /** A page: the dim frame, the panel, the header, the footer, and the [body] between the header and the footer. */
    private fun page(
        content: StatsCardContent,
        slots: Int,
        index: Int,
        count: Int,
        frameWidth: Double,
        body: (Area) -> List<SceneItem>,
    ): ScoreboardScene {
        val items = mutableListOf<SceneItem>()
        items += SceneItem.Box(0.0, 0.0, frameWidth, HEIGHT, BLACK, opacity = DIM_OPACITY)

        val panelWidth = minOf(PANEL_WIDTH, frameWidth - 2 * FRAME_MARGIN)
        val panelHeight = HEADER_HEIGHT + ROW_HEIGHT * slots + FOOTER_HEIGHT
        val left = (frameWidth - panelWidth) / 2
        val top = (HEIGHT - panelHeight) / 2
        val right = left + panelWidth
        val center = left + panelWidth / 2
        val inner = left + PADDING
        val innerRight = right - PADDING
        items += SceneItem.Box(left, top, panelWidth, panelHeight, PANEL_RGB, opacity = content.panelOpacity, corners = Corners.all(28.0))

        // Header: the names at the sides, the title and the score in the middle.
        val nameWidth = panelWidth * 0.3
        items += label(inner, top + 78, content.names.p1, NAME_SIZE, content.colors.p1, TextAnchor.MIDDLE_LEFT, maxWidth = nameWidth)
        items += label(innerRight, top + 78, content.names.p2, NAME_SIZE, content.colors.p2, TextAnchor.MIDDLE_RIGHT, maxWidth = nameWidth)
        items += label(center, top + 50, content.title.uppercase(), 26.0, MUTED, TextAnchor.CENTER, bold = false, spacing = 3.0)
        items += label(center, top + 100, content.score, 44.0, WHITE, TextAnchor.CENTER, maxWidth = panelWidth - 2 * nameWidth - 2 * PADDING)
        items += SceneItem.Box(inner, top + HEADER_HEIGHT - 22, innerRight - inner, 2.0, WHITE, opacity = 0.16)

        items += body(Area(inner, innerRight, top + HEADER_HEIGHT, top + HEADER_HEIGHT + ROW_HEIGHT * slots))

        val footerY = top + panelHeight - FOOTER_HEIGHT / 2
        if (count > 1) items += label(center, footerY, "${index + 1} / $count", 26.0, MUTED, TextAnchor.CENTER, bold = false)
        content.credit?.let { items += label(innerRight, footerY, it, 24.0, MUTED, TextAnchor.MIDDLE_RIGHT, bold = false, opacity = 0.8) }
        return ScoreboardScene(frameWidth, HEIGHT, items)
    }

    private fun rowItems(content: StatsCardContent, rows: List<StatRow>, area: Area): List<SceneItem> {
        val items = mutableListOf<SceneItem>()
        rows.forEachIndexed { i, row ->
            val rowTop = area.top + i * ROW_HEIGHT
            val values = row.values
            if (values == null) {
                items += label(area.center, rowTop + 30, row.label, LABEL_SIZE, SOFT, TextAnchor.CENTER, bold = false)
                items += label(area.center, rowTop + 72, row.shared?.text.orEmpty(), 46.0, WHITE, TextAnchor.CENTER)
            } else {
                items += label(area.center, rowTop + 30, row.label, LABEL_SIZE, SOFT, TextAnchor.CENTER, bold = false)
                items += label(area.left, rowTop + 52, values.p1.text, VALUE_SIZE, WHITE, TextAnchor.MIDDLE_LEFT)
                items += label(area.right, rowTop + 52, values.p2.text, VALUE_SIZE, WHITE, TextAnchor.MIDDLE_RIGHT)
                row.bar?.let { items += bar(it, content.colors, area.left + BAR_INSET, area.right - BAR_INSET, rowTop + 62) }
            }
        }
        return items
    }

    /**
     * The momentum chart: the point difference after each point. The area above the zero line has the color
     * of player 1, and the area below it has the color of player 2. Dashed lines mark the start of each set.
     */
    private fun chartItems(content: StatsCardContent, momentum: Momentum, area: Area): List<SceneItem> {
        val items = mutableListOf<SceneItem>()
        val lead = momentum.maxLead
        val labelTop = area.top + 26
        val labelBottom = area.bottom - 26
        val plotTop = area.top + 58
        val plotBottom = area.bottom - 58
        // Each side has the height of the largest lead on that side, so the chart has no empty side.
        val above = maxOf(lead.p1, 1)
        val below = maxOf(lead.p2, 1)
        val unit = (plotBottom - plotTop) / (above + below)
        val zeroY = plotTop + above * unit
        val points = momentum.points
        fun x(position: Double) = area.left + position * (area.right - area.left) / points.size
        fun y(difference: Double) = zeroY - difference * unit

        items += label(area.right, labelBottom, "Point difference", 26.0, MUTED, TextAnchor.MIDDLE_RIGHT, bold = false)
        val p1Label = label(area.left, labelTop, "${content.names.p1} +${lead.p1}", 30.0, content.colors.p1, TextAnchor.MIDDLE_LEFT,
            maxWidth = (area.right - area.left) * 0.3)
        items += p1Label
        items += label(area.left, labelBottom, "${content.names.p2} +${lead.p2}", 30.0, content.colors.p2, TextAnchor.MIDDLE_LEFT,
            maxWidth = (area.right - area.left) * 0.3)

        // The set lines. A set label that would cover the label of player 1 is left out.
        val p1LabelRight = area.left + ScoreboardFonts.textWidth(p1Label.text, SEGOE, p1Label.bold, p1Label.size)
        for (start in momentum.setStarts) {
            val lineX = x(start.toDouble())
            var dashY = plotTop
            while (dashY < plotBottom) {
                items += SceneItem.Box(lineX - 1, dashY, 2.0, minOf(DASH, plotBottom - dashY), MUTED, opacity = 0.7)
                dashY += DASH + DASH_GAP
            }
            val text = "Set ${points[start].set}"
            val halfWidth = ScoreboardFonts.textWidth(text, SEGOE, false, 26.0) / 2
            if (lineX - halfWidth > p1LabelRight + 20) {
                items += label(lineX, plotTop - 18, text, 26.0, MUTED, TextAnchor.CENTER, bold = false)
            }
        }

        // The line starts at 0 before the first point. A line that crosses zero gets a point at zero,
        // so the two areas meet exactly on the zero line.
        val line = mutableListOf(0.0 to 0.0)
        points.forEachIndexed { index, point ->
            val (x0, d0) = line.last()
            val d1 = point.difference.toDouble()
            if (d0 * d1 < 0) line += x0 + d0 / (d0 - d1) to 0.0
            line += (index + 1).toDouble() to d1
        }
        fun area(select: (Double) -> Double) = SceneItem.Polygon(
            line.map { (position, difference) -> ScenePoint(x(position), y(select(difference))) } +
                ScenePoint(x(points.size.toDouble()), zeroY),
            0, AREA_OPACITY,
        )
        if (lead.p1 > 0) items += area { maxOf(it, 0.0) }.copy(rgb = content.colors.p1)
        if (lead.p2 > 0) items += area { minOf(it, 0.0) }.copy(rgb = content.colors.p2)
        items += SceneItem.Box(area.left, zeroY - 1, area.right - area.left, 2.0, WHITE, opacity = 0.3)
        items += SceneItem.Polyline(line.map { (position, difference) -> ScenePoint(x(position), y(difference)) }, 4.0, WHITE)
        return items
    }

    /** A bar in two parts. The part of each player is its share of the sum. Two zeros give a dim bar in two equal parts. */
    private fun bar(values: PerPlayer<Double>, colors: PerPlayer<Int>, x0: Double, x1: Double, y: Double): List<SceneItem> {
        val total = values.p1 + values.p2
        val share = if (total > 0) values.p1 / total else 0.5
        val opacity = if (total > 0) 1.0 else 0.3
        val width = x1 - x0 - BAR_GAP
        val p1Width = width * share
        val p2Width = width - p1Width
        val radius = BAR_HEIGHT / 2
        return listOfNotNull(
            SceneItem.Box(x0, y, p1Width, BAR_HEIGHT, colors.p1, opacity, Corners.all(radius)).takeIf { p1Width > 0.5 },
            SceneItem.Box(x1 - p2Width, y, p2Width, BAR_HEIGHT, colors.p2, opacity, Corners.all(radius)).takeIf { p2Width > 0.5 },
        )
    }

    /** A label that gets smaller, and then shorter with an ellipsis, when it is wider than [maxWidth]. */
    private fun label(
        x: Double,
        y: Double,
        text: String,
        size: Double,
        rgb: Int,
        anchor: TextAnchor,
        bold: Boolean = true,
        spacing: Double = 0.0,
        opacity: Double = 1.0,
        maxWidth: Double = Double.MAX_VALUE,
    ): SceneItem.Label {
        var fitted = text
        var fittedSize = size
        fun width() = ScoreboardFonts.textWidth(fitted, SEGOE, bold, fittedSize, spacing)
        while (width() > maxWidth && fittedSize > size * MIN_SHRINK) fittedSize -= 1.0
        while (width() > maxWidth && fitted.length > 1) fitted = fitted.dropLast(2) + "…"
        val middleY = if (anchor == TextAnchor.TOP_LEFT || anchor == TextAnchor.TOP_RIGHT) y else
            ScoreboardFonts.middleYForCapCenter(y, SEGOE, bold, fittedSize)
        return SceneItem.Label(x, middleY, fitted, SEGOE, fittedSize, rgb, bold, anchor, opacity, spacing)
    }

    private fun rgb(hex: String, fallback: Int): Int = hex.removePrefix("#").toIntOrNull(16) ?: fallback

    /**
     * Returns [rgb], or a lighter color when [rgb] is too dark for the dark panel.
     * The lighter color is a mix with white that has the minimum relative luminance.
     */
    fun onPanel(rgb: Int): Int {
        if (luminance(rgb) >= MIN_LUMINANCE) return rgb
        var white = 0.0
        var mixed = rgb
        while (luminance(mixed) < MIN_LUMINANCE && white < 1.0) {
            white += 0.01
            mixed = mix(rgb, WHITE, white)
        }
        return mixed
    }

    /** The relative luminance of the WCAG definition, from 0 (black) to 1 (white). */
    internal fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((rgb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun mix(from: Int, to: Int, part: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * part).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
    private const val SOFT = 0xD6D6D6
    private const val MUTED = 0xA8A8A8
    private const val PANEL_RGB = 0x0B0F14
    /** A player color darker than this is difficult to see on the panel. The neutral gray of player 2 is just above it. */
    private const val MIN_LUMINANCE = 0.2
    private const val DIM_OPACITY = 0.45
    private const val PANEL_WIDTH = 1240.0
    private const val FRAME_MARGIN = 48.0
    private const val PADDING = 64.0
    private const val HEADER_HEIGHT = 170.0
    private const val ROW_HEIGHT = 104.0
    private const val FOOTER_HEIGHT = 70.0
    private const val NAME_SIZE = 58.0
    private const val LABEL_SIZE = 30.0
    private const val VALUE_SIZE = 54.0
    private const val BAR_INSET = 190.0
    private const val BAR_HEIGHT = 10.0
    private const val BAR_GAP = 6.0
    private const val MIN_SHRINK = 0.7
    /** The chart page has the height of this number of rows, or more when a page has more rows. */
    private const val CHART_SLOTS = 4
    private const val MIN_CHART_POINTS = 2
    private const val AREA_OPACITY = 0.55
    private const val DASH = 10.0
    private const val DASH_GAP = 8.0
}
