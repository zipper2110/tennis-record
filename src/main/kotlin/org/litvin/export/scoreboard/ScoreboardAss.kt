package org.litvin.export.scoreboard

import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import java.util.Locale
import kotlin.math.roundToInt

/** Where a board goes in a frame: the top-left corner and the factor from board units to frame pixels. */
data class BoardPlacement(val x: Double, val y: Double, val scale: Double)

/**
 * Converts a [ScoreboardScene] to ASS event texts (the Text field of a Dialogue line).
 *
 * The export writes these texts into the ASS file for FFmpeg. The scoring preview sends the same
 * texts to the mpv `osd-overlay` command. Both use libass, so the board looks the same.
 * Every event sets all of its override tags, so the event style has no effect on the result.
 */
object ScoreboardAss {
    /** The frame height at which one board unit is one pixel. */
    const val REFERENCE_HEIGHT = 1080.0

    /** The distance from the frame edges, in board units at 100 % size. */
    const val MARGIN = 48.0

    private const val CURVE = 0.5523

    /**
     * Places the board in a frame. The frame is the video area: the whole output frame for the export,
     * or the video area of the preview window.
     */
    fun place(
        scene: ScoreboardScene,
        settings: ScoreboardSettingsV1,
        frameX: Double,
        frameY: Double,
        frameWidth: Double,
        frameHeight: Double,
    ): BoardPlacement {
        val frameScale = frameHeight / REFERENCE_HEIGHT
        val margin = MARGIN * frameScale
        var scale = frameScale * settings.sizePercent.coerceIn(
            ScoreboardSettingsV1.MIN_SIZE_PERCENT,
            ScoreboardSettingsV1.MAX_SIZE_PERCENT,
        ) / 100.0
        // A narrow frame (for example a portrait video) gets a smaller board, so the board stays in the frame.
        val maxWidth = frameWidth - 2 * margin
        if (scene.width * scale > maxWidth && maxWidth > 0) scale = maxWidth / scene.width
        val boardWidth = scene.width * scale
        val boardHeight = scene.height * scale
        val left = frameX + margin
        val right = frameX + frameWidth - margin - boardWidth
        val top = frameY + margin
        val bottom = frameY + frameHeight - margin - boardHeight
        return when (settings.position) {
            ScoreboardPosition.TOP_LEFT -> BoardPlacement(left, top, scale)
            ScoreboardPosition.TOP_RIGHT -> BoardPlacement(right, top, scale)
            ScoreboardPosition.BOTTOM_LEFT -> BoardPlacement(left, bottom, scale)
            ScoreboardPosition.BOTTOM_RIGHT -> BoardPlacement(right, bottom, scale)
        }
    }

    /** Returns one ASS event text for each scene item, in drawing order. */
    fun events(scene: ScoreboardScene, placement: BoardPlacement): List<String> =
        scene.items.mapNotNull { item ->
            when (item) {
                is SceneItem.Box -> box(item, placement)
                is SceneItem.Label -> label(item, placement)
            }
        }

    private fun box(box: SceneItem.Box, placement: BoardPlacement): String? {
        val s = placement.scale
        val width = box.width * s
        val height = box.height * s
        if (width <= 0.0 || height <= 0.0 || box.opacity <= 0.0) return null
        val x = placement.x + box.x * s
        val y = placement.y + box.y * s
        // The path starts at (0, 0) of its bounds, so \an7\pos places the top-left corner exactly.
        val path = roundedRectPath(width, height, box.corners.scaled(s))
        return "{\\an7\\pos(${fmt(x)},${fmt(y)})\\bord0\\shad0\\blur0\\fscx100\\fscy100\\frz0" +
            "\\1c${color(box.rgb)}\\1a${alpha(box.opacity)}\\p1}$path{\\p0}"
    }

    private fun label(label: SceneItem.Label, placement: BoardPlacement): String? {
        if (label.text.isEmpty() || label.opacity <= 0.0) return null
        val s = placement.scale
        val x = placement.x + label.x * s
        val y = placement.y + label.y * s
        val tags = buildString {
            append("{\\an${label.anchor.ass}\\pos(${fmt(x)},${fmt(y)})\\q2")
            append("\\fn${label.font}\\fs${fmt(label.size * s)}\\b${if (label.bold) 1 else 0}\\i0\\u0\\s0")
            append("\\fsp${fmt(label.spacing * s)}\\fscx100\\fscy100\\frz0\\shad0\\blur0")
            append("\\1c${color(label.rgb)}\\1a${alpha(label.opacity)}")
            if (label.outline > 0.0) {
                append("\\bord${fmt(label.outline * s)}\\3c${color(label.outlineRgb)}\\3a${alpha(label.opacity)}")
            } else {
                append("\\bord0")
            }
            append("}")
        }
        return tags + escape(label.text)
    }

    /** A closed path clockwise from the top-left corner. Zero radii give sharp corners. */
    internal fun roundedRectPath(width: Double, height: Double, radii: Corners): String {
        val limit = minOf(width, height) / 2.0
        val tl = radii.topLeft.coerceIn(0.0, limit)
        val tr = radii.topRight.coerceIn(0.0, limit)
        val br = radii.bottomRight.coerceIn(0.0, limit)
        val bl = radii.bottomLeft.coerceIn(0.0, limit)
        val w = width
        val h = height
        return buildString {
            append("m ${fmt(tl)} 0 l ${fmt(w - tr)} 0")
            if (tr > 0) append(" b ${fmt(w - tr + CURVE * tr)} 0 ${fmt(w)} ${fmt(tr - CURVE * tr)} ${fmt(w)} ${fmt(tr)}")
            append(" l ${fmt(w)} ${fmt(h - br)}")
            if (br > 0) append(" b ${fmt(w)} ${fmt(h - br + CURVE * br)} ${fmt(w - br + CURVE * br)} ${fmt(h)} ${fmt(w - br)} ${fmt(h)}")
            append(" l ${fmt(bl)} ${fmt(h)}")
            if (bl > 0) append(" b ${fmt(bl - CURVE * bl)} ${fmt(h)} 0 ${fmt(h - bl + CURVE * bl)} 0 ${fmt(h - bl)}")
            append(" l 0 ${fmt(tl)}")
            if (tl > 0) append(" b 0 ${fmt(tl - CURVE * tl)} ${fmt(tl - CURVE * tl)} 0 ${fmt(tl)} 0")
        }
    }

    /** ASS colors are &HBBGGRR&. */
    internal fun color(rgb: Int): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return String.format(Locale.US, "&H%02X%02X%02X&", b, g, r)
    }

    /** ASS alpha is inverted: 00 is opaque and FF is transparent. */
    internal fun alpha(opacity: Double): String {
        val value = 255 - (opacity.coerceIn(0.0, 1.0) * 255).roundToInt()
        return String.format(Locale.US, "&H%02X&", value)
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")

    private fun fmt(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0')
        }
    }
}
