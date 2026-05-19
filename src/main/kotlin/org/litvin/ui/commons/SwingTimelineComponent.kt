package org.litvin.ui.commons

import org.litvin.markup.PointV1
import org.litvin.shared.util.Timecode
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 3 — Custom timeline Swing component.
 *
 * Renders:
 * - Ruler (simple hash marks)
 * - VIDEO track (single span)
 * - MARKS track (completed points from dispatcher)
 * - Playhead line bound to media time
 *
 * Interactions:
 * - Click anywhere to seek to that time
 * - Click on a mark span to seek to its start
 */
class SwingTimelineComponent(
    private val timeProvider: () -> Long,
    private val durationProvider: () -> Long,
    private val pointsProvider: () -> List<PointV1>,
    private val onSeekRequested: (Long) -> Unit,
) : JComponent() {

    private val bg = Color(0x16, 0x16, 0x16)
    private val fg = Color(0xE6, 0xE6, 0xE6)
    private val trackBg = Color(0x22, 0x22, 0x22)
    private val videoColor = Color(0x44, 0x88, 0xFF)
    private val markColor = Color(0x4CAF50)
    private val rulerColor = Color(0x55, 0x55, 0x55)
    private val playheadColor = Color(0xFF, 0x55, 0x55)

    private val fontSmall = Font("Dialog", Font.PLAIN, 11)

    init {
        isOpaque = true
        background = bg

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val total = max(1L, durationProvider())
                val t = ((e.x.toDouble() / width.toDouble()) * total).toLong().coerceIn(0L, total)
                // Check if clicking on a mark span: prefer snapping to its start for convenience
                val points = pointsProvider()
                val pxPerMs = pxPerMs(total)
                val markTop = rulerHeight() + trackHeight() + trackGap()
                val markRect = Rectangle(0, markTop, width, trackHeight())
                if (markRect.contains(e.point)) {
                    val clicked = points.firstOrNull { p ->
                        val x1 = (p.startMs * pxPerMs).toInt()
                        val x2 = (p.endMs * pxPerMs).toInt()
                        e.x in x1..max(x1 + 1, x2)
                    }
                    if (clicked != null) {
                        onSeekRequested(clicked.startMs.toLong())
                        return
                    }
                }
                onSeekRequested(t)
            }
        })
    }

    private fun rulerHeight(): Int = 16
    private fun trackHeight(): Int = 18
    private fun trackGap(): Int = 6

    private fun pxPerMs(totalMs: Long): Double = if (totalMs <= 0) 0.0 else width.toDouble() / totalMs.toDouble()

    override fun paintComponent(g0: Graphics) {
        val g = g0 as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = bg
        g.fillRect(0, 0, width, height)

        val total = max(1L, durationProvider())
        val points = pointsProvider()
        val pxPerMs = pxPerMs(total)

        // Ruler
        val rH = rulerHeight()
        g.color = rulerColor
        g.fillRect(0, 0, width, rH)
        g.color = fg
        g.font = fontSmall
        val majorCount = 10
        for (i in 0..majorCount) {
            val x = (i * (width.toDouble() / majorCount)).toInt()
            g.color = Color(0x88, 0x88, 0x88)
            g.drawLine(x, 0, x, rH)
            g.color = fg
            val t = (i * (total / majorCount.toLong()))
            val label = Timecode.format(t)
            g.drawString(label, min(width - 30, max(0, x - 20)), rH - 3)
        }

        // VIDEO track row
        var y = rH
        g.color = trackBg
        g.fillRect(0, y, width, trackHeight())
        g.color = videoColor
        g.fillRect(0, y + 2, width, trackHeight() - 4)
        g.color = fg
        g.font = fontSmall
        g.drawString("VIDEO", 6, y + trackHeight() - 4)

        // MARKS track row
        y += trackHeight() + trackGap()
        g.color = trackBg
        g.fillRect(0, y, width, trackHeight())
        g.color = markColor
        for (p in points) {
            val x1 = (p.startMs * pxPerMs).toInt()
            val x2 = (p.endMs * pxPerMs).toInt()
            g.fillRect(x1, y + 2, max(1, x2 - x1), trackHeight() - 4)
        }
        g.color = fg
        g.drawString("MARKS", 6, y + trackHeight() - 4)

        // Playhead
        val cur = timeProvider().coerceIn(0L, total)
        val xPh = (cur * pxPerMs).toInt()
        g.color = playheadColor
        g.drawLine(xPh, 0, xPh, height)
    }

    override fun getPreferredSize(): Dimension {
        val h = rulerHeight() + trackHeight() + trackGap() + trackHeight()
        return Dimension(400, h)
    }
}
