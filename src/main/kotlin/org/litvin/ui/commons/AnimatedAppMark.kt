package org.litvin.ui.commons

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RenderingHints
import java.awt.event.HierarchyEvent
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.Point2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The app mark (a tennis ball that is also a play button) with a gradient that moves across the ball.
 * The ball spins one full turn around a diagonal axis, then stays still, and then spins again.
 * The play triangle is printed on the ball and turns with it. At rest, the mark is close to `icons/app-icon.svg`.
 * The animation runs only while the mark is on screen.
 */
class AnimatedAppMark(
    private val size: Int,
    private val nanoClock: () -> Long = System::nanoTime,
) : JComponent() {
    private val startNanos = nanoClock()
    private val timer = Timer(REST_FRAME_MILLIS) {
        (it.source as Timer).delay = if (isSpinning()) SPIN_FRAME_MILLIS else REST_FRAME_MILLIS
        repaint()
    }

    init {
        isOpaque = false
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) updateTimer()
        }
    }

    override fun addNotify() {
        super.addNotify()
        updateTimer()
    }

    override fun removeNotify() {
        timer.stop()
        super.removeNotify()
    }

    internal val isAnimating: Boolean get() = timer.isRunning

    private fun updateTimer() {
        if (isShowing) timer.start() else timer.stop()
    }

    private fun elapsedNanos(): Long = nanoClock() - startNanos

    private fun isSpinning(): Boolean = Math.floorMod(elapsedNanos(), SPIN_CYCLE_NANOS) < SPIN_NANOS

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            val scale = minOf(width, height) / VIEW_SIZE
            g2.translate((width - VIEW_SIZE * scale) / 2.0, (height - VIEW_SIZE * scale) / 2.0)
            g2.scale(scale, scale)
            g2.translate(-VIEW_ORIGIN, -VIEW_ORIGIN)
            val elapsed = elapsedNanos()

            g2.paint = ballPaint(elapsed)
            g2.fill(BALL)

            val angle = spinAngle(elapsed)
            g2.color = DETAIL
            g2.stroke = DETAIL_STROKE
            g2.clip(BALL)
            g2.draw(seamPath(angle))
            playPath(angle)?.let(g2::fill)
        } finally {
            g2.dispose()
        }
    }

    /** A diagonal gradient that repeats and moves by one full cycle in each [GRADIENT_CYCLE_NANOS]. */
    private fun ballPaint(elapsed: Long): LinearGradientPaint {
        val phase = Math.floorMod(elapsed, GRADIENT_CYCLE_NANOS).toDouble() / GRADIENT_CYCLE_NANOS
        val shift = (phase * GRADIENT_LENGTH).toFloat()
        val start = Point2D.Float(VIEW_ORIGIN.toFloat() + shift, VIEW_ORIGIN.toFloat() + shift)
        val end = Point2D.Float(start.x + GRADIENT_LENGTH, start.y + GRADIENT_LENGTH)
        return LinearGradientPaint(start, end, STOPS, COLORS, MultipleGradientPaint.CycleMethod.REPEAT)
    }

    /** The ball turns once in the first [SPIN_NANOS] of each cycle, with ease-in and ease-out. */
    private fun spinAngle(elapsed: Long): Double {
        val inCycle = Math.floorMod(elapsed, SPIN_CYCLE_NANOS)
        if (inCycle >= SPIN_NANOS) return 0.0
        val t = inCycle.toDouble() / SPIN_NANOS
        val eased = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
        return 2 * PI * eased
    }

    /** Projects the seam points that face the viewer (z >= 0) after the rotation. */
    private fun seamPath(angle: Double): Path2D {
        val path = Path2D.Double()
        val cosA = cos(angle)
        val sinA = sin(angle)
        val point = DoubleArray(3)
        var prevX = 0.0
        var prevY = 0.0
        var prevZ = -1.0
        var drawing = false
        for (i in 0..SEAM_POINTS) {
            val index = i % SEAM_POINTS
            rotate(SEAM_X[index], SEAM_Y[index], SEAM_Z[index], cosA, sinA, point)
            val (x, y, z) = Triple(point[0], point[1], point[2])
            if (z >= 0) {
                if (!drawing) {
                    if (i == 0) {
                        path.moveTo(screen(x), screen(y))
                    } else {
                        val f = prevZ / (prevZ - z)
                        path.moveTo(screen(prevX + (x - prevX) * f), screen(prevY + (y - prevY) * f))
                        path.lineTo(screen(x), screen(y))
                    }
                    drawing = true
                } else {
                    path.lineTo(screen(x), screen(y))
                }
            } else if (drawing) {
                val f = prevZ / (prevZ - z)
                path.lineTo(screen(prevX + (x - prevX) * f), screen(prevY + (y - prevY) * f))
                drawing = false
            }
            prevX = x
            prevY = y
            prevZ = z
        }
        return path
    }

    /**
     * Projects the play triangle after the rotation. Outline points on the back of the ball move to the
     * nearest point of the ball edge, so the path covers only the part that faces the viewer.
     * Returns null when the whole triangle is on the back.
     */
    private fun playPath(angle: Double): Path2D? {
        val path = Path2D.Double()
        val cosA = cos(angle)
        val sinA = sin(angle)
        val point = DoubleArray(3)
        var anyFront = false
        for (outline in PLAY_OUTLINES) {
            for (i in outline.indices) {
                val p = outline[i]
                rotate(p[0], p[1], p[2], cosA, sinA, point)
                var x = point[0]
                var y = point[1]
                if (point[2] >= 0) {
                    anyFront = true
                } else {
                    val r = hypot(x, y)
                    if (r > 0) { x /= r; y /= r }
                }
                if (i == 0) path.moveTo(screen(x), screen(y)) else path.lineTo(screen(x), screen(y))
            }
            path.closePath()
        }
        return if (anyFront) path else null
    }

    private companion object {
        const val REST_FRAME_MILLIS = 40
        const val SPIN_FRAME_MILLIS = 16
        const val GRADIENT_CYCLE_NANOS = 6_000_000_000L
        const val SPIN_NANOS = 1_000_000_000L
        const val SPIN_CYCLE_NANOS = SPIN_NANOS + 15_000_000_000L
        const val VIEW_ORIGIN = 11.0
        const val VIEW_SIZE = 42.0
        const val GRADIENT_LENGTH = 42f
        const val BALL_CENTER = 32.0
        const val BALL_RADIUS = 21.0
        const val SEAM_POINTS = 192

        val DETAIL = Color(0x0E, 0x0E, 0x0E)

        // Lime and pale lime come from the Import button gradient. The yellow comes from the design palette.
        val STOPS = floatArrayOf(0f, 0.35f, 0.65f, 1f)
        val COLORS = arrayOf(
            Color(0xA1, 0xFE, 0x00),
            Color(0xDD, 0xFF, 0xB0),
            Color(0xED, 0xE4, 0x50),
            Color(0xA1, 0xFE, 0x00),
        )

        val DETAIL_STROKE = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val BALL = Ellipse2D.Double(11.0, 11.0, 42.0, 42.0)
        val PLAY = Path2D.Double().apply {
            moveTo(25.6, 23.5); lineTo(39.1, 32.0); lineTo(25.6, 40.5); closePath()
        }

        // The outline of the play triangle with its rounded corners, as points on the front of the unit
        // sphere. Long straight parts are cut into short parts, so that they bend on the ball.
        val PLAY_OUTLINES: List<List<DoubleArray>> = run {
            val rounded = Area(PLAY).apply { add(Area(DETAIL_STROKE.createStrokedShape(PLAY))) }
            val outlines = mutableListOf<MutableList<DoubleArray>>()
            val coords = DoubleArray(6)
            var lastX = 0.0
            var lastY = 0.0
            val iterator = rounded.getPathIterator(null, 0.05)
            while (!iterator.isDone) {
                when (iterator.currentSegment(coords)) {
                    PathIterator.SEG_MOVETO -> {
                        outlines += mutableListOf(onSphere(coords[0], coords[1]))
                        lastX = coords[0]
                        lastY = coords[1]
                    }
                    PathIterator.SEG_LINETO -> {
                        val steps = ceil(hypot(coords[0] - lastX, coords[1] - lastY) / 0.5).toInt().coerceAtLeast(1)
                        for (step in 1..steps) {
                            val f = step.toDouble() / steps
                            outlines.last() += onSphere(lastX + (coords[0] - lastX) * f, lastY + (coords[1] - lastY) * f)
                        }
                        lastX = coords[0]
                        lastY = coords[1]
                    }
                }
                iterator.next()
            }
            outlines
        }

        // The seam of a tennis ball on a unit sphere: (a cos t + b cos 3t, a sin t - b sin 3t, c sin 2t),
        // with a + b = 1 and c = 2 sqrt(ab). It is turned 45 degrees so that at rest the two front arcs
        // are at the left and the right, with their inner points at x = ±(a - b). A larger b gives more
        // curved arcs, but then the right arc comes nearer to the tip of the play triangle.
        // Screen axes: x to the right, y down, z to the viewer.
        const val SEAM_B = 0.2
        const val SEAM_A = 1 - SEAM_B
        val SEAM_C = 2 * sqrt(SEAM_A * SEAM_B)
        val SEAM_X = DoubleArray(SEAM_POINTS)
        val SEAM_Y = DoubleArray(SEAM_POINTS)
        val SEAM_Z = DoubleArray(SEAM_POINTS)

        // The ball turns around the diagonal from the bottom left to the top right,
        // so its front moves to the bottom right.
        val AXIS = doubleArrayOf(-1 / sqrt(2.0), 1 / sqrt(2.0), 0.0)

        init {
            for (i in 0 until SEAM_POINTS) {
                val t = 2 * PI * i / SEAM_POINTS
                val x = SEAM_A * cos(t) + SEAM_B * cos(3 * t)
                val y = SEAM_A * sin(t) - SEAM_B * sin(3 * t)
                SEAM_X[i] = (x + y) / sqrt(2.0)
                SEAM_Y[i] = (y - x) / sqrt(2.0)
                SEAM_Z[i] = SEAM_C * sin(2 * t)
            }
        }

        fun screen(unit: Double): Double = BALL_CENTER + BALL_RADIUS * unit

        /** Moves a view-box point straight back onto the front of the unit sphere. */
        fun onSphere(x: Double, y: Double): DoubleArray {
            val ux = (x - BALL_CENTER) / BALL_RADIUS
            val uy = (y - BALL_CENTER) / BALL_RADIUS
            return doubleArrayOf(ux, uy, sqrt((1 - ux * ux - uy * uy).coerceAtLeast(0.0)))
        }

        /** Rodrigues rotation of (x, y, z) around [AXIS]. */
        fun rotate(x: Double, y: Double, z: Double, cosA: Double, sinA: Double, out: DoubleArray) {
            val (ux, uy, uz) = Triple(AXIS[0], AXIS[1], AXIS[2])
            val dot = ux * x + uy * y + uz * z
            out[0] = x * cosA + (uy * z - uz * y) * sinA + ux * dot * (1 - cosA)
            out[1] = y * cosA + (uz * x - ux * z) * sinA + uy * dot * (1 - cosA)
            out[2] = z * cosA + (ux * y - uy * x) * sinA + uz * dot * (1 - cosA)
        }
    }
}
