package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import java.awt.Color
import java.awt.Component
import java.awt.geom.Rectangle2D
import java.io.File

interface SwingMediaPlayer : AutoCloseable {
    val component: Component
    var onReady: (() -> Unit)?
    var onStatusChanged: ((PlayerStatus) -> Unit)?
    var onTimeChanged: ((Long) -> Unit)?

    fun load(file: File)
    fun play()
    fun pause()
    fun seek(ms: Long)
    fun setRate(rate: Float)
    fun status(): PlayerStatus
    fun currentTimeMs(): Long
    fun totalDurationMs(): Long
    fun isAdjustSupported(): Boolean
    fun applyColorAdjustments(adj: AdjustmentsV1)
    fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean
    fun applyPreviewAdjustments(adj: AdjustmentsV1)
    fun applyPreviewRotation(rotationDeg: Float, reason: String = "apply rotation")
    /** Draws [overlay] over the video, for example the scoreboard. Null removes it. */
    fun setPreviewOverlay(overlay: VideoOverlay?)
    fun stepFrameForward(maximumTimeMs: Long = Long.MAX_VALUE): Long
    fun stepFrameBackward(minimumTimeMs: Long = 0L): Long
    fun nextFrame()
    fun setSubtitleFile(file: File): Boolean
    fun activatePreview(reason: String = "activate")
    fun deactivatePreview(reason: String = "deactivate")
    override fun close()

    /**
     * True shows the rotated full frame without the crop, for the Crop/Rotate editor.
     * False shows the export result (rotation and crop). Engines without this feature ignore it.
     */
    fun setCropEditing(enabled: Boolean) {}

    /** The area of [component] that shows the video, in component pixels. Null when it is not known. */
    fun videoBounds(): Rectangle2D.Double? = null

    /** Called on the event dispatch thread when [videoBounds] changes. */
    var onVideoBoundsChanged: (() -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /**
     * Draws the shapes over the video. The coordinates are component pixels. Null removes the shapes.
     * Engines without this feature ignore it.
     */
    fun setEditorOverlay(shapes: List<OverlayShape>?) {}
}

/**
 * ASS events that a player draws over the video with libass. The export burns the same events
 * into the video, so the preview and the export look the same.
 */
fun interface VideoOverlay {
    /**
     * Returns the ASS event texts (the Text field of Dialogue lines) in drawing order.
     * [videoArea] is the area that shows the video, in overlay pixels. The events use the same pixels.
     */
    fun assEvents(videoArea: Rectangle2D.Double): List<String>
}

/** A vector shape that a player draws over the video. The coordinates are component pixels. */
sealed interface OverlayShape {
    data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val fill: Color? = null,
        val stroke: Color? = null,
        val strokeWidth: Double = 0.0,
    ) : OverlayShape

    data class Circle(
        val centerX: Double,
        val centerY: Double,
        val radius: Double,
        val fill: Color? = null,
        val stroke: Color? = null,
        val strokeWidth: Double = 0.0,
    ) : OverlayShape

    data class Line(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val color: Color,
        val width: Double,
    ) : OverlayShape
}
