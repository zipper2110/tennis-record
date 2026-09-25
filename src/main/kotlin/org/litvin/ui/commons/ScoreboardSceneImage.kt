package org.litvin.ui.commons

import org.litvin.export.scoreboard.Corners
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScenePoint
import org.litvin.export.scoreboard.ScoreboardFonts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws a [ScoreboardScene] with Java2D, for the scoreboard settings dialog and the statistics card preview.
 *
 * The video preview and the export use libass. This renderer uses the same font sizes and
 * anchors, so the dialog shows a close copy of the video result.
 */
object ScoreboardSceneImage {
    private const val CURVE = 0.5523

    fun render(scene: ScoreboardScene, scale: Double): BufferedImage {
        val width = ceil(scene.width * scale).toInt().coerceAtLeast(1)
        val height = ceil(scene.height * scale).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            draw(g, scene, 0.0, 0.0, scale)
        } finally {
            g.dispose()
        }
        return image
    }

    /** Draws the scene with its top-left corner at ([x], [y]) in [g] coordinates. */
    fun draw(g: Graphics2D, scene: ScoreboardScene, x: Double, y: Double, scale: Double) {
        val saved = g.transform
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.translate(x, y)
        g.scale(scale, scale)
        try {
            scene.items.forEach { item ->
                when (item) {
                    is SceneItem.Box -> drawBox(g, item)
                    is SceneItem.Label -> drawLabel(g, item)
                    is SceneItem.Polygon -> drawPolygon(g, item)
                    is SceneItem.Polyline -> drawPolyline(g, item)
                }
            }
        } finally {
            g.transform = saved
        }
    }

    private fun drawBox(g: Graphics2D, box: SceneItem.Box) {
        if (box.width <= 0.0 || box.height <= 0.0) return
        g.color = color(box.rgb, box.opacity)
        g.fill(roundedRect(box.x, box.y, box.width, box.height, box.corners))
    }

    private fun drawPolygon(g: Graphics2D, polygon: SceneItem.Polygon) {
        if (polygon.points.size < 3) return
        g.color = color(polygon.rgb, polygon.opacity)
        g.fill(path(polygon.points).apply { closePath() })
    }

    private fun drawPolyline(g: Graphics2D, line: SceneItem.Polyline) {
        if (line.points.size < 2 || line.width <= 0.0) return
        val savedStroke = g.stroke
        g.color = color(line.rgb, line.opacity)
        g.stroke = BasicStroke(line.width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(path(line.points))
        g.stroke = savedStroke
    }

    private fun path(points: List<ScenePoint>) = Path2D.Double().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }

    private fun drawLabel(g: Graphics2D, label: SceneItem.Label) {
        if (label.text.isEmpty()) return
        val font = ScoreboardFonts.awtFont(label.font, label.bold, label.size)
        val width = ScoreboardFonts.textWidth(label.text, label.font, label.bold, label.size, label.spacing)
        val left = when (label.anchor) {
            TextAnchor.TOP_LEFT, TextAnchor.MIDDLE_LEFT -> label.x
            TextAnchor.CENTER -> label.x - width / 2
            TextAnchor.TOP_RIGHT, TextAnchor.MIDDLE_RIGHT -> label.x - width
        }
        val lineTop = when (label.anchor) {
            TextAnchor.TOP_LEFT, TextAnchor.TOP_RIGHT -> label.y
            else -> label.y - label.size / 2
        }
        val baseline = lineTop + ScoreboardFonts.ascentShare(label.font, label.bold) * label.size

        val outline = Path2D.Double()
        var penX = left
        val frc = g.fontRenderContext
        val chunks = if (label.spacing == 0.0) listOf(label.text) else label.text.map { it.toString() }
        chunks.forEach { chunk ->
            val glyphs = font.createGlyphVector(frc, chunk)
            outline.append(glyphs.getOutline(penX.toFloat(), baseline.toFloat()), false)
            penX += glyphs.logicalBounds.width + label.spacing
        }
        if (label.outline > 0.0) {
            g.color = color(label.outlineRgb, label.opacity)
            g.stroke = BasicStroke((label.outline * 2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(outline)
        }
        g.color = color(label.rgb, label.opacity)
        g.fill(outline)
    }

    private fun roundedRect(x: Double, y: Double, w: Double, h: Double, corners: Corners): Path2D.Double {
        val limit = minOf(w, h) / 2
        val tl = corners.topLeft.coerceIn(0.0, limit)
        val tr = corners.topRight.coerceIn(0.0, limit)
        val br = corners.bottomRight.coerceIn(0.0, limit)
        val bl = corners.bottomLeft.coerceIn(0.0, limit)
        return Path2D.Double().apply {
            moveTo(x + tl, y)
            lineTo(x + w - tr, y)
            if (tr > 0) curveTo(x + w - tr + CURVE * tr, y, x + w, y + tr - CURVE * tr, x + w, y + tr)
            lineTo(x + w, y + h - br)
            if (br > 0) curveTo(x + w, y + h - br + CURVE * br, x + w - br + CURVE * br, y + h, x + w - br, y + h)
            lineTo(x + bl, y + h)
            if (bl > 0) curveTo(x + bl - CURVE * bl, y + h, x, y + h - bl + CURVE * bl, x, y + h - bl)
            lineTo(x, y + tl)
            if (tl > 0) curveTo(x, y + tl - CURVE * tl, x + tl - CURVE * tl, y, x + tl, y)
            closePath()
        }
    }

    private fun color(rgb: Int, opacity: Double): Color =
        Color(rgb and 0xFFFFFF or ((opacity.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24), true)
}
