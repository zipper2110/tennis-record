package org.litvin.ui.tabs.markup.ui

import org.litvin.markup.CommentV1
import org.litvin.markup.PointV1
import org.litvin.shared.util.Timecode
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

internal data class CommentMarkerLayout(
    val id: Int,
    val startMs: Long,
    val lineX: Int,
    val boxBounds: Rectangle,
    val lane: Int,
    val lineTop: Int,
    val lineBottom: Int,
)

/** Three-track editor timeline for VIDEO, MARKS, and source-pinned COMMENTS. */
class SwingTimelineComponent(
    private val timeProvider: () -> Long,
    private val durationProvider: () -> Long,
    private val pointsProvider: () -> List<PointV1>,
    private val onSeekRequested: (Long) -> Unit,
    private val commentsProvider: () -> List<CommentV1> = { emptyList() },
    private val onCommentSelected: (Int) -> Unit = {},
) : JComponent() {

    private val bg = Color(0x16, 0x16, 0x16)
    private val fg = Color(0xE6, 0xE6, 0xE6)
    private val trackBg = Color(0x22, 0x22, 0x22)
    private val videoColor = Color(0x44, 0x88, 0xFF)
    private val markColor = Color(0x4C, 0xAF, 0x50)
    private val commentColor = Color(0xFF, 0xC1, 0x07)
    private val rulerColor = Color(0x55, 0x55, 0x55)
    private val playheadColor = Color(0xFF, 0x55, 0x55)
    private val fontSmall = Font("Dialog", Font.PLAIN, 11)
    private var lastKnownLaneCount = -1

    init {
        isOpaque = true
        background = bg
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = handleClick(event)
        })
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                val laneCount = commentLaneCountFor(width.coerceAtLeast(1))
                if (laneCount != lastKnownLaneCount) {
                    lastKnownLaneCount = laneCount
                    revalidate()
                }
            }
        })
    }

    private fun handleClick(event: MouseEvent) {
        val total = max(1L, durationProvider())
        val comment = commentMarkerLayouts(total, width.coerceAtLeast(1)).firstOrNull { it.boxBounds.contains(event.point) }
        if (comment != null) {
            onCommentSelected(comment.id)
            onSeekRequested(comment.startMs)
            return
        }

        val t = ((event.x.toDouble() / width.coerceAtLeast(1).toDouble()) * total).toLong().coerceIn(0L, total)
        val markTop = marksTrackTop()
        val markRect = Rectangle(0, markTop, width, trackHeight())
        if (markRect.contains(event.point)) {
            val pxPerMs = pxPerMs(total, width)
            pointsProvider().firstOrNull { point ->
                val x1 = (point.startMs * pxPerMs).toInt()
                val x2 = (point.endMs * pxPerMs).toInt()
                event.x in x1..max(x1 + 1, x2)
            }?.let {
                onSeekRequested(it.startMs.toLong())
                return
            }
        }
        onSeekRequested(t)
    }

    private fun rulerHeight(): Int = 16
    private fun trackHeight(): Int = 18
    private fun trackGap(): Int = 6
    private fun commentLabelHeight(): Int = 18
    private fun commentLaneGap(): Int = 3
    private fun videoTrackTop(): Int = rulerHeight()
    private fun marksTrackTop(): Int = videoTrackTop() + trackHeight() + trackGap()
    private fun commentsTrackTop(): Int = marksTrackTop() + trackHeight() + trackGap()

    private fun commentTrackHeight(laneCount: Int): Int = max(
        trackHeight(),
        laneCount * (commentLabelHeight() + commentLaneGap()) + 4,
    )

    private fun pxPerMs(totalMs: Long, availableWidth: Int): Double =
        if (totalMs <= 0) 0.0 else availableWidth.toDouble() / totalMs.toDouble()

    override fun paintComponent(graphics: Graphics) {
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = bg
        g.fillRect(0, 0, width, height)

        val total = max(1L, durationProvider())
        val points = pointsProvider()
        val comments = commentsProvider()
        val layouts = commentMarkerLayouts(total, width.coerceAtLeast(1), comments)
        val laneCount = (layouts.maxOfOrNull { it.lane } ?: -1) + 1
        val commentTop = commentsTrackTop()
        val commentHeight = commentTrackHeight(laneCount)
        val pxPerMs = pxPerMs(total, width)

        paintRuler(g, total)
        paintVideoTrack(g)
        paintMarksTrack(g, points, pxPerMs)

        g.color = trackBg
        g.fillRect(0, commentTop, width, commentHeight)
        g.color = fg
        g.font = fontSmall
        g.drawString("COMMENTS", 6, commentTop + trackHeight() - 4)

        val colors = comments.associateBy { it.id }
        for (layout in layouts) {
            g.color = colors[layout.id]?.colorHex?.let(::colorFor) ?: commentColor
            g.fillRect(layout.lineX - 1, layout.lineTop, 3, max(1, layout.lineBottom - layout.lineTop))
        }
        for (layout in layouts) {
            val comment = colors[layout.id] ?: continue
            g.color = colorFor(comment.colorHex)
            g.fillRoundRect(layout.boxBounds.x, layout.boxBounds.y, layout.boxBounds.width, layout.boxBounds.height, 5, 5)
            g.color = Color(0x16, 0x16, 0x16)
            g.font = fontSmall.deriveFont(Font.BOLD)
            g.drawString("#${layout.id}", layout.boxBounds.x + 5, layout.boxBounds.y + layout.boxBounds.height - 5)
        }

        val current = timeProvider().coerceIn(0L, total)
        val x = (current * pxPerMs).toInt()
        g.color = playheadColor
        g.drawLine(x, 0, x, height)
    }

    private fun paintRuler(g: Graphics2D, total: Long) {
        val height = rulerHeight()
        g.color = rulerColor
        g.fillRect(0, 0, width, height)
        g.font = fontSmall
        val majorCount = 10
        for (index in 0..majorCount) {
            val x = (index * (width.toDouble() / majorCount)).toInt()
            g.color = Color(0x88, 0x88, 0x88)
            g.drawLine(x, 0, x, height)
            g.color = fg
            val time = index * (total / majorCount.toLong())
            g.drawString(Timecode.format(time), min(width - 30, max(0, x - 20)), height - 3)
        }
    }

    private fun paintVideoTrack(g: Graphics2D) {
        val top = videoTrackTop()
        g.color = trackBg
        g.fillRect(0, top, width, trackHeight())
        g.color = videoColor
        g.fillRect(0, top + 2, width, trackHeight() - 4)
        g.color = fg
        g.font = fontSmall
        g.drawString("VIDEO", 6, top + trackHeight() - 4)
    }

    private fun paintMarksTrack(g: Graphics2D, points: List<PointV1>, pxPerMs: Double) {
        val top = marksTrackTop()
        g.color = trackBg
        g.fillRect(0, top, width, trackHeight())
        g.color = markColor
        points.forEach { point ->
            val x1 = (point.startMs * pxPerMs).toInt()
            val x2 = (point.endMs * pxPerMs).toInt()
            g.fillRect(x1, top + 2, max(1, x2 - x1), trackHeight() - 4)
        }
        g.color = fg
        g.font = fontSmall
        g.drawString("MARKS", 6, top + trackHeight() - 4)
    }

    private fun commentMarkerLayouts(
        totalMs: Long,
        availableWidth: Int,
        comments: List<CommentV1> = commentsProvider(),
    ): List<CommentMarkerLayout> {
        val pxPerMs = pxPerMs(totalMs, availableWidth)
        val laneEnds = mutableListOf<Int>()
        val result = mutableListOf<CommentMarkerLayout>()
        val sorted = comments.asSequence()
            .filter { it.id > 0 && it.startMs >= 0 }
            .sortedWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id })
            .toList()
        val lineTop = videoTrackTop()
        for (comment in sorted) {
            val lineX = (comment.startMs * pxPerMs).toInt().coerceIn(0, availableWidth)
            val boxLeft = lineX + 3
            val boxWidth = max(22, 10 + "#${comment.id}".length * 7)
            val lane = laneEnds.indexOfFirst { previousEnd -> previousEnd < boxLeft }
                .let { if (it >= 0) it else laneEnds.size }
            if (lane == laneEnds.size) laneEnds += boxLeft + boxWidth else laneEnds[lane] = boxLeft + boxWidth
            val laneCount = laneEnds.size
            val boxTop = commentsTrackTop() + 2 + lane * (commentLabelHeight() + commentLaneGap())
            result += CommentMarkerLayout(
                id = comment.id,
                startMs = comment.startMs.toLong(),
                lineX = lineX,
                boxBounds = Rectangle(boxLeft, boxTop, boxWidth, commentLabelHeight()),
                lane = lane,
                lineTop = lineTop,
                lineBottom = commentsTrackTop() + commentTrackHeight(laneCount),
            )
        }
        val finalBottom = commentsTrackTop() + commentTrackHeight(laneEnds.size)
        return result.map { it.copy(lineBottom = finalBottom) }
    }

    private fun commentLaneCountFor(availableWidth: Int): Int {
        val layouts = commentMarkerLayouts(max(1L, durationProvider()), availableWidth)
        return (layouts.maxOfOrNull { it.lane } ?: -1) + 1
    }

    private fun colorFor(hex: String): Color = runCatching { Color.decode(hex) }.getOrDefault(commentColor)

    internal fun commentMarkerLayoutsForTest(): List<CommentMarkerLayout> =
        commentMarkerLayouts(max(1L, durationProvider()), width.coerceAtLeast(1))

    internal fun commentLaneCountForTest(): Int = commentLaneCountFor(width.coerceAtLeast(1))
    internal fun videoTrackTopForTest(): Int = videoTrackTop()
    internal fun commentTrackBottomForTest(): Int = commentsTrackTop() + commentTrackHeight(commentLaneCountForTest())
    internal fun commentTrackCenterY(): Int = (commentsTrackTop() + commentTrackBottomForTest()) / 2

    override fun getPreferredSize(): Dimension {
        val laneCount = commentLaneCountFor(width.takeIf { it > 0 } ?: 400)
        return Dimension(400, commentsTrackTop() + commentTrackHeight(laneCount))
    }
}
