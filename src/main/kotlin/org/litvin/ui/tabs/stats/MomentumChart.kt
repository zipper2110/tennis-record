package org.litvin.ui.tabs.stats

import org.litvin.scoring.PerPlayer
import org.litvin.stats.Momentum
import org.litvin.ui.UiStyles
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The point difference over the match or one set. The area above the middle line has the color of
 * player 1, and the area below it has the color of player 2. A click opens the point under the mouse.
 */
class MomentumChart(private val onOpenPoint: (String) -> Unit) : JComponent() {
    var momentum: Momentum = Momentum(emptyList(), emptyList())
        set(value) {
            field = value
            hover = -1
            cursor = if (value.points.isEmpty()) Cursor.getDefaultCursor() else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            repaint()
        }
    var names: PerPlayer<String> = PerPlayer("Player 1", "Player 2")
    var colors: PerPlayer<Color> = PerPlayer(UiStyles.FG_PRIMARY, UiStyles.FG_SECONDARY)

    /** The position in [Momentum.points] under the mouse, or -1. */
    private var hover = -1

    init {
        name = "stats-momentum"
        isOpaque = false
        preferredSize = Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT)
        minimumSize = Dimension(200, PREFERRED_HEIGHT)
        ToolTipManager.sharedInstance().registerComponent(this)
        val mouse = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) = setHover(pointAt(e.x))
            override fun mouseExited(e: MouseEvent) = setHover(-1)
            override fun mouseClicked(e: MouseEvent) {
                val index = pointAt(e.x)
                if (index >= 0) onOpenPoint(momentum.points[index].pointId)
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val index = pointAt(event.x)
        if (index < 0) return null
        val point = momentum.points[index]
        val winner = if (point.winner == 1) names.p1 else names.p2
        val lead = when {
            point.difference > 0 -> "${names.p1} leads by ${points(point.difference)}."
            point.difference < 0 -> "${names.p2} leads by ${points(-point.difference)}."
            else -> "The points won are equal."
        }
        return "<html>Point ${point.number}, set ${point.set}: $winner won the point.<br>$lead<br>" +
            "Click to open the point in the Scoring tab.</html>"
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val plot = plotArea()
            val points = momentum.points
            val lead = momentum.maxLead
            // Each side has the height of the largest lead on that side, so the chart has no empty side.
            val above = maxOf(lead.p1, 1)
            val below = maxOf(lead.p2, 1)
            val middle = plot.y + above * plot.height / (above + below)

            // The labels show the largest lead of each player.
            g.font = font.deriveFont(font.size2D - 1f)
            val metrics = g.fontMetrics
            g.color = colors.p1
            g.drawString("${names.p1} +${lead.p1}", plot.x.toFloat(), (plot.y - 6).toFloat())
            g.color = colors.p2
            g.drawString("${names.p2} +${lead.p2}", plot.x.toFloat(), (plot.maxY + metrics.ascent + 4).toFloat())

            g.color = UiStyles.CARD_BORDER
            g.draw(Line2D.Double(plot.x, middle, plot.maxX, middle))
            if (points.isEmpty()) return

            fun x(position: Int) = plot.x + position * plot.width / points.size
            fun y(difference: Int) = middle - difference * plot.height / (above + below)

            // The set lines, with the set number at the top.
            g.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 4f), 0f)
            for (start in momentum.setStarts) {
                val lineX = x(start)
                g.color = UiStyles.FG_DISABLED
                g.draw(Line2D.Double(lineX, plot.y, lineX, plot.maxY))
                val label = "Set ${points[start].set}"
                g.color = UiStyles.FG_SECONDARY
                g.drawString(label, (lineX - metrics.stringWidth(label) / 2).toFloat(), (plot.y - 6).toFloat())
            }

            // The line starts at 0 before the first point.
            val line = Path2D.Double().apply {
                moveTo(x(0), middle)
                points.forEachIndexed { index, point -> lineTo(x(index + 1), y(point.difference)) }
            }
            val area = Path2D.Double(line).apply {
                lineTo(x(points.size), middle)
                closePath()
            }
            fill(g, area, Rectangle2D.Double(plot.x, plot.y - 1, plot.width, middle - plot.y + 1), colors.p1)
            fill(g, area, Rectangle2D.Double(plot.x, middle, plot.width, plot.maxY - middle + 1), colors.p2)
            g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = UiStyles.FG_PRIMARY
            g.draw(line)

            if (hover in points.indices) {
                val hoverX = x(hover + 1)
                val hoverY = y(points[hover].difference)
                g.stroke = BasicStroke(1f)
                g.color = UiStyles.FG_SECONDARY
                g.draw(Line2D.Double(hoverX, plot.y, hoverX, plot.maxY))
                g.color = if (points[hover].winner == 1) colors.p1 else colors.p2
                g.fill(Ellipse2D.Double(hoverX - 5, hoverY - 5, 10.0, 10.0))
            }
        } finally {
            g.dispose()
        }
    }

    private fun fill(g: Graphics2D, area: Shape, clip: Shape, color: Color) {
        val previous = g.clip
        g.clip(clip)
        g.color = Color(color.red, color.green, color.blue, AREA_ALPHA)
        g.fill(area)
        g.clip = previous
    }

    private fun plotArea(): Rectangle2D.Double {
        val labelHeight = getFontMetrics(font).height + 4.0
        return Rectangle2D.Double(
            PADDING,
            labelHeight + PADDING,
            (width - 2 * PADDING).coerceAtLeast(1.0),
            (height - 2 * labelHeight - 2 * PADDING).coerceAtLeast(1.0),
        )
    }

    /** The point nearest to [mouseX], or -1 when the chart has no points. */
    private fun pointAt(mouseX: Int): Int {
        val count = momentum.points.size
        if (count == 0) return -1
        val plot = plotArea()
        if (mouseX < plot.x - PADDING || mouseX > plot.maxX + PADDING) return -1
        val position = ((mouseX - plot.x) * count / plot.width).roundToInt()
        return (position - 1).coerceIn(0, count - 1)
    }

    private fun setHover(index: Int) {
        if (index == hover) return
        hover = index
        repaint()
    }

    private fun points(count: Int) = if (abs(count) == 1) "1 point" else "$count points"

    private companion object {
        const val PREFERRED_WIDTH = 560
        const val PREFERRED_HEIGHT = 170
        const val PADDING = 4.0
        const val AREA_ALPHA = 90
    }
}
