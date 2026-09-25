package org.litvin.ui.tabs.stats

import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.ScoreboardSceneImage
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.roundToInt

/**
 * Shows one page of the statistics card in a 16:9 frame, as the exported video shows it.
 * The export puts the card on a frozen frame of the video. The preview shows the same frame when it has it,
 * and a drawn tennis court until then.
 */
class StatsCardPreview : JComponent() {
    var pages: List<ScoreboardScene> = emptyList()
        set(value) {
            field = value
            page = page.coerceIn(0, (value.size - 1).coerceAtLeast(0))
            repaint()
        }

    var page: Int = 0
        set(value) {
            field = value
            repaint()
        }

    /** The video frame under the card, or null for the drawn court. */
    var background: BufferedImage? = null
        set(value) {
            field = value
            repaint()
        }

    /** The text that shows when the card has no pages. */
    var emptyText: String = "Select at least one row with a value."

    init {
        name = "stats-preview"
        preferredSize = Dimension(512, 288)
        minimumSize = Dimension(320, 180)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // The largest 16:9 frame that fits, centered.
            val frameWidth = minOf(width.toDouble(), height * 16.0 / 9.0)
            val frameHeight = frameWidth * 9.0 / 16.0
            val x = (width - frameWidth) / 2
            val y = (height - frameHeight) / 2
            val frame = background
            if (frame == null) drawCourt(g, x, y, frameWidth, frameHeight) else drawFrame(g, frame, x, y, frameWidth, frameHeight)
            val scene = pages.getOrNull(page)
            if (scene == null) {
                g.color = Color(0, 0, 0, 150)
                g.fill(java.awt.geom.Rectangle2D.Double(x, y, frameWidth, frameHeight))
                g.color = UiStyles.FG_SECONDARY
                val metrics = g.fontMetrics
                g.drawString(emptyText, (width - metrics.stringWidth(emptyText)) / 2, height / 2 + metrics.ascent / 2)
            } else {
                ScoreboardSceneImage.draw(g, scene, x, y, frameHeight / scene.height)
            }
        } finally {
            g.dispose()
        }
    }

    /** The video frame, centered and scaled to fill the 16:9 frame. The parts outside the frame are cut off. */
    private fun drawFrame(g: Graphics2D, image: BufferedImage, x: Double, y: Double, w: Double, h: Double) {
        val scale = maxOf(w / image.width, h / image.height)
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        val savedClip = g.clip
        g.clip(Rectangle2D.Double(x, y, w, h))
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(
            image,
            (x + (w - drawWidth) / 2).roundToInt(),
            (y + (h - drawHeight) / 2).roundToInt(),
            drawWidth.roundToInt(),
            drawHeight.roundToInt(),
            null,
        )
        g.clip = savedClip
    }

    /** A simple tennis court in perspective, in place of the video frame. */
    private fun drawCourt(g: Graphics2D, x: Double, y: Double, w: Double, h: Double) {
        g.color = Color(0x2E, 0x4B, 0x3A)
        g.fill(java.awt.geom.Rectangle2D.Double(x, y, w, h))
        g.color = Color(0x3D, 0x6B, 0x8C)
        val court = Path2D.Double().apply {
            moveTo(x + w * 0.33, y + h * 0.22)
            lineTo(x + w * 0.67, y + h * 0.22)
            lineTo(x + w * 0.86, y + h * 0.94)
            lineTo(x + w * 0.14, y + h * 0.94)
            closePath()
        }
        g.fill(court)
        g.color = Color(255, 255, 255, 170)
        g.stroke = BasicStroke((h / 180).toFloat().coerceAtLeast(1f))
        g.draw(court)
        val netY = y + h * 0.5
        g.draw(java.awt.geom.Line2D.Double(x + w * 0.24, netY, x + w * 0.76, netY))
        g.draw(java.awt.geom.Line2D.Double(x + w * 0.5, y + h * 0.33, x + w * 0.5, y + h * 0.75))
    }
}
