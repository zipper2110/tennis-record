package org.litvin.media.mpv

import org.litvin.media.OverlayShape
import java.awt.Color
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Converts [OverlayShape]s to ASS events for the mpv `osd-overlay` command (format `ass-events`).
 *
 * Each shape is one ASS drawing event. The drawing coordinates start at (0, 0) of the shape bounds,
 * and `\an7\pos(x,y)` places that corner. This does not depend on how libass aligns drawings.
 * [scale] converts component pixels to OSD pixels (the OSD resolution is the window size in device pixels).
 */
internal object MpvAssOverlay {
    private const val CIRCLE_K = 0.5523

    fun events(shapes: List<OverlayShape>, scale: Double): String =
        shapes.mapNotNull { shape -> event(shape, scale) }.joinToString("\n")

    private fun event(shape: OverlayShape, scale: Double): String? = when (shape) {
        is OverlayShape.Rect -> {
            val w = (shape.width * scale).roundToInt()
            val h = (shape.height * scale).roundToInt()
            if (w <= 0 || h <= 0) {
                null
            } else {
                drawing(
                    x = shape.x * scale,
                    y = shape.y * scale,
                    path = "m 0 0 l $w 0 $w $h 0 $h",
                    fill = shape.fill,
                    stroke = shape.stroke,
                    strokeWidth = shape.strokeWidth * scale,
                )
            }
        }
        is OverlayShape.Circle -> {
            val r = shape.radius * scale
            if (r <= 0.0) {
                null
            } else {
                val d = 2.0 * r
                val k = CIRCLE_K * r
                fun n(value: Double) = fmt(value)
                val path = "m ${n(d)} ${n(r)} " +
                    "b ${n(d)} ${n(r + k)} ${n(r + k)} ${n(d)} ${n(r)} ${n(d)} " +
                    "b ${n(r - k)} ${n(d)} 0 ${n(r + k)} 0 ${n(r)} " +
                    "b 0 ${n(r - k)} ${n(r - k)} 0 ${n(r)} 0 " +
                    "b ${n(r + k)} 0 ${n(d)} ${n(r - k)} ${n(d)} ${n(r)}"
                drawing(
                    x = (shape.centerX * scale) - r,
                    y = (shape.centerY * scale) - r,
                    path = path,
                    fill = shape.fill,
                    stroke = shape.stroke,
                    strokeWidth = shape.strokeWidth * scale,
                )
            }
        }
        is OverlayShape.Line -> {
            val x1 = shape.x1 * scale
            val y1 = shape.y1 * scale
            val x2 = shape.x2 * scale
            val y2 = shape.y2 * scale
            val length = hypot(x2 - x1, y2 - y1)
            if (length <= 0.0) {
                null
            } else {
                // A line is a thin quadrilateral around the segment.
                val half = max(shape.width * scale, 1.0) / 2.0
                val nx = -(y2 - y1) / length * half
                val ny = (x2 - x1) / length * half
                val points = listOf(x1 + nx to y1 + ny, x2 + nx to y2 + ny, x2 - nx to y2 - ny, x1 - nx to y1 - ny)
                val minX = points.minOf { it.first }
                val minY = points.minOf { it.second }
                val path = points.mapIndexed { index, (px, py) ->
                    (if (index == 0) "m " else if (index == 1) "l " else "") + "${fmt(px - minX)} ${fmt(py - minY)}"
                }.joinToString(" ")
                drawing(minX, minY, path, fill = shape.color, stroke = null, strokeWidth = 0.0)
            }
        }
    }

    private fun drawing(x: Double, y: Double, path: String, fill: Color?, stroke: Color?, strokeWidth: Double): String {
        val tags = buildString {
            append("{\\an7\\pos(${fmt(x)},${fmt(y)})\\shad0\\blur0")
            if (fill != null) {
                append("\\1c${assColor(fill)}\\1a${assAlpha(fill)}")
            } else {
                append("\\1a&HFF&")
            }
            if (stroke != null && strokeWidth > 0.0) {
                append("\\bord${fmt(strokeWidth / 2.0)}\\3c${assColor(stroke)}\\3a${assAlpha(stroke)}")
            } else {
                append("\\bord0")
            }
            append("\\p1}")
        }
        return "$tags$path{\\p0}"
    }

    /** ASS colors are &HBBGGRR&. */
    private fun assColor(color: Color): String = String.format(Locale.US, "&H%02X%02X%02X&", color.blue, color.green, color.red)

    /** ASS alpha is inverted: 00 is opaque and FF is transparent. */
    private fun assAlpha(color: Color): String = String.format(Locale.US, "&H%02X&", 255 - color.alpha)

    private fun fmt(value: Double): String {
        val rounded = (value * 10.0).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
