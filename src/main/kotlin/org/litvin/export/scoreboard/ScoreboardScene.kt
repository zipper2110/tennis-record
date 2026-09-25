package org.litvin.export.scoreboard

/**
 * A scoreboard drawing that does not depend on the output. The export (FFmpeg + libass), the scoring
 * preview (mpv + libass) and the settings dialog (Java2D) all draw the same scene.
 *
 * The coordinates are board units: one unit is one pixel of a 1080p frame at 100 % size.
 * The origin is the top-left corner of the board. Items are drawn in list order.
 */
data class ScoreboardScene(
    val width: Double,
    val height: Double,
    val items: List<SceneItem>,
)

/** Corner radii, clockwise from the top-left corner. */
data class Corners(
    val topLeft: Double = 0.0,
    val topRight: Double = 0.0,
    val bottomRight: Double = 0.0,
    val bottomLeft: Double = 0.0,
) {
    fun scaled(factor: Double) = Corners(topLeft * factor, topRight * factor, bottomRight * factor, bottomLeft * factor)

    companion object {
        val NONE = Corners()
        fun all(radius: Double) = Corners(radius, radius, radius, radius)
    }
}

/**
 * The text anchor. The values are ASS numpad alignments. "Middle" is the vertical middle of the
 * line box (ascent + descent), as libass uses it.
 */
enum class TextAnchor(val ass: Int) {
    TOP_LEFT(7),
    TOP_RIGHT(9),
    MIDDLE_LEFT(4),
    CENTER(5),
    MIDDLE_RIGHT(6),
}

/** A point of a [SceneItem.Polygon] or a [SceneItem.Polyline], in board units. */
data class ScenePoint(val x: Double, val y: Double)

sealed interface SceneItem {
    /** A filled shape through [points]. The last point connects to the first point. */
    data class Polygon(
        val points: List<ScenePoint>,
        val rgb: Int,
        val opacity: Double = 1.0,
    ) : SceneItem

    /** A line through [points], [width] units wide, with round joins and round ends. */
    data class Polyline(
        val points: List<ScenePoint>,
        val width: Double,
        val rgb: Int,
        val opacity: Double = 1.0,
    ) : SceneItem

    /** A filled rectangle with rounded corners. [opacity] is 0.0 (transparent) to 1.0 (opaque). */
    data class Box(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val rgb: Int,
        val opacity: Double = 1.0,
        val corners: Corners = Corners.NONE,
    ) : SceneItem

    /**
     * One line of text. [size] is the libass font size: the height of the line box, not the em size.
     * [spacing] is the extra space after each character.
     */
    data class Label(
        val x: Double,
        val y: Double,
        val text: String,
        val font: String,
        val size: Double,
        val rgb: Int,
        val bold: Boolean = true,
        val anchor: TextAnchor = TextAnchor.MIDDLE_LEFT,
        val opacity: Double = 1.0,
        val spacing: Double = 0.0,
        val outline: Double = 0.0,
        val outlineRgb: Int = 0x000000,
    ) : SceneItem
}
