package org.litvin.ui.tabs.crop

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.CropGeometryMath
import org.litvin.adjustments.CropRect
import org.litvin.media.OverlayShape
import org.litvin.media.SwingMediaPlayer
import org.litvin.ui.UiStyles
import java.awt.Color
import java.awt.Cursor
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Crop/Rotate editor on the live video.
 *
 * The player shows the rotated full frame (crop editing mode). This class draws the crop rectangle,
 * the dim mask, the resize handles, and the rotation handle through [SwingMediaPlayer.setEditorOverlay].
 * It reads the mouse on [SwingMediaPlayer.component]. All coordinates are component pixels, and the
 * content box is the video area that [SwingMediaPlayer.videoBounds] gives.
 */
class CropEditorOverlay(
    private val player: SwingMediaPlayer,
    private val onAdjustmentsChanged: (AdjustmentsV1) -> Unit,
) {
    private enum class HitTarget {
        NONE, MOVE, N, S, E, W, NW, NE, SW, SE, ROTATE
    }

    private companion object {
        private const val HANDLE_SIZE = 10.0
        private const val HANDLE_HIT_SIZE = 14.0
        private const val ROTATION_HANDLE_OFFSET = 24.0
        private val DIM = Color(0, 0, 0, 148)
    }

    private val target = player.component
    private var adjustments = AdjustmentsV1()
    private var hoverTarget = HitTarget.NONE
    private var dragTarget = HitTarget.NONE
    private var dragStartPoint = Point2D.Double()
    private var dragStartRect = CropRect(0.0, 0.0, 0.0, 0.0)
    private var dragStartAdjustments = AdjustmentsV1()

    init {
        val adapter = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                updateHover(hitTest(e.x.toDouble(), e.y.toDouble()))
            }

            override fun mousePressed(e: MouseEvent) {
                val content = player.videoBounds() ?: return
                dragTarget = hitTest(e.x.toDouble(), e.y.toDouble())
                dragStartPoint = Point2D.Double(e.x.toDouble(), e.y.toDouble())
                dragStartRect = overlayRect(content)
                dragStartAdjustments = adjustments
                redraw()
            }

            override fun mouseDragged(e: MouseEvent) {
                if (dragTarget == HitTarget.NONE) return
                handleDrag(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                dragTarget = HitTarget.NONE
                updateHover(hitTest(e.x.toDouble(), e.y.toDouble()), force = true)
            }

            override fun mouseExited(e: MouseEvent) {
                if (dragTarget == HitTarget.NONE) updateHover(HitTarget.NONE)
            }
        }
        target.addMouseListener(adapter)
        target.addMouseMotionListener(adapter)
        player.onVideoBoundsChanged = { redraw() }
    }

    fun render(next: AdjustmentsV1) {
        adjustments = next
        redraw()
    }

    /** Moves the crop rectangle by [dx], [dy] component pixels. */
    fun nudge(dx: Int, dy: Int) {
        val content = player.videoBounds() ?: return
        val current = overlayRect(content)
        val moved = CropGeometryMath.clampInside(
            current.copy(x = current.x + dx, y = current.y + dy),
            allowedBounds(content),
            aspect(content),
        )
        emit(modelFrom(content, moved))
    }

    private fun updateHover(next: HitTarget, force: Boolean = false) {
        if (next == hoverTarget && !force) return
        hoverTarget = next
        target.cursor = cursorFor(next)
        redraw()
    }

    private fun handleDrag(e: MouseEvent) {
        val content = player.videoBounds() ?: return
        val bounds = allowedBounds(content)
        val aspect = aspect(content)
        val dx = e.x - dragStartPoint.x
        val dy = e.y - dragStartPoint.y
        if (dragTarget == HitTarget.ROTATE) {
            val center = overlayRect(content).let { it.x + it.width / 2.0 to it.y + it.height / 2.0 }
            val angle = Math.toDegrees(atan2(e.y - center.second, e.x - center.first)).toFloat() + 90.0f
            val snapped = CropGeometryMath.snapRotation(angle, (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0)
            emit(dragStartAdjustments.copy(rotationDeg = snapped))
            return
        }
        val nextRect = when (dragTarget) {
            HitTarget.MOVE -> CropGeometryMath.clampInside(
                dragStartRect.copy(x = dragStartRect.x + dx, y = dragStartRect.y + dy),
                bounds,
                aspect,
            )
            HitTarget.N, HitTarget.S -> {
                val delta = if (dragTarget == HitTarget.N) dy else -dy
                CropGeometryMath.centeredResize(dragStartRect, bounds, aspect, -2.0 * delta)
            }
            HitTarget.E, HitTarget.W -> {
                val delta = if (dragTarget == HitTarget.E) dx else -dx
                CropGeometryMath.centeredResize(dragStartRect, bounds, aspect, 2.0 * delta)
            }
            HitTarget.NW, HitTarget.NE, HitTarget.SW, HitTarget.SE ->
                CropGeometryMath.centeredResize(dragStartRect, bounds, aspect, 2.0 * cornerDelta(dx, dy, aspect))
            else -> dragStartRect
        }
        emit(modelFrom(content, nextRect))
    }

    private fun cornerDelta(dx: Double, dy: Double, aspect: Double): Double {
        val sx = when (dragTarget) {
            HitTarget.NE, HitTarget.SE -> dx
            HitTarget.NW, HitTarget.SW -> -dx
            else -> 0.0
        }
        val sy = when (dragTarget) {
            HitTarget.SW, HitTarget.SE -> dy
            HitTarget.NW, HitTarget.NE -> -dy
            else -> 0.0
        }
        return if (abs(sx) > abs(sy)) sx else sy * aspect
    }

    private fun modelFrom(content: Rectangle2D.Double, rect: CropRect): AdjustmentsV1 {
        val local = rect.copy(x = rect.x - content.x, y = rect.y - content.y)
        return CropGeometryMath.modelFromOverlay(content.width, content.height, local, adjustments)
            .copy(rotationDeg = adjustments.rotationDeg)
    }

    private fun emit(next: AdjustmentsV1) {
        adjustments = next
        onAdjustmentsChanged(next)
        redraw()
    }

    private fun redraw() {
        val content = player.videoBounds()
        player.setEditorOverlay(content?.let { shapes(it) })
    }

    private fun shapes(content: Rectangle2D.Double): List<OverlayShape> {
        val overlay = overlayRect(content)
        val active = hoverTarget != HitTarget.NONE || dragTarget != HitTarget.NONE
        val stroke = if (active) UiStyles.LIME else Color.WHITE
        val shapes = mutableListOf<OverlayShape>()

        // Dim mask: four bands of the content box around the crop rectangle.
        val right = overlay.x + overlay.width
        val bottom = overlay.y + overlay.height
        val contentRight = content.x + content.width
        val contentBottom = content.y + content.height
        shapes += OverlayShape.Rect(content.x, content.y, content.width, overlay.y - content.y, fill = DIM)
        shapes += OverlayShape.Rect(content.x, bottom, content.width, contentBottom - bottom, fill = DIM)
        shapes += OverlayShape.Rect(content.x, overlay.y, overlay.x - content.x, overlay.height, fill = DIM)
        shapes += OverlayShape.Rect(right, overlay.y, contentRight - right, overlay.height, fill = DIM)

        shapes += OverlayShape.Rect(overlay.x, overlay.y, overlay.width, overlay.height, stroke = stroke, strokeWidth = 2.0)
        handleRects(overlay, HANDLE_SIZE).forEach { (_, rect) ->
            shapes += OverlayShape.Rect(
                rect.x, rect.y, rect.width, rect.height,
                fill = UiStyles.DARK_BG, stroke = stroke, strokeWidth = 1.5,
            )
        }
        val rotation = rotationHandleCenter(overlay)
        shapes += OverlayShape.Line(overlay.x + overlay.width / 2.0, overlay.y, rotation.x, rotation.y, stroke, 1.4)
        shapes += OverlayShape.Circle(
            rotation.x, rotation.y, HANDLE_SIZE / 2.0,
            fill = UiStyles.DARK_BG, stroke = stroke, strokeWidth = 1.5,
        )
        return shapes
    }

    private fun hitTest(x: Double, y: Double): HitTarget {
        val content = player.videoBounds() ?: return HitTarget.NONE
        val overlay = overlayRect(content)
        val rotation = rotationHandleCenter(overlay)
        if (rotation.distance(x, y) <= HANDLE_HIT_SIZE) return HitTarget.ROTATE
        handleRects(overlay, HANDLE_HIT_SIZE).forEach { (hit, rect) ->
            if (x >= rect.x && x <= rect.x + rect.width && y >= rect.y && y <= rect.y + rect.height) return hit
        }
        if (x >= overlay.x && x <= overlay.x + overlay.width && y >= overlay.y && y <= overlay.y + overlay.height) {
            return HitTarget.MOVE
        }
        return HitTarget.NONE
    }

    private fun cursorFor(hit: HitTarget): Cursor = when (hit) {
        HitTarget.MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        HitTarget.N, HitTarget.S -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        HitTarget.E, HitTarget.W -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        HitTarget.NW, HitTarget.SE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
        HitTarget.NE, HitTarget.SW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        HitTarget.ROTATE -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        HitTarget.NONE -> Cursor.getDefaultCursor()
    }

    private fun aspect(content: Rectangle2D.Double): Double =
        if (content.height > 0.0) content.width / content.height else 16.0 / 9.0

    /** The crop rectangle in component pixels, clamped inside the rotated frame. */
    private fun overlayRect(content: Rectangle2D.Double): CropRect {
        val model = CropGeometryMath.overlayFromModel(content.width, content.height, adjustments)
        return CropGeometryMath.clampInside(
            model.copy(x = model.x + content.x, y = model.y + content.y),
            allowedBounds(content),
            aspect(content),
        )
    }

    private fun allowedBounds(content: Rectangle2D.Double): CropRect {
        val local = CropGeometryMath.largestCenteredInscribedRect(
            content.width,
            content.height,
            adjustments.rotationDeg.toDouble(),
            aspect(content),
        )
        return local.copy(x = content.x + local.x, y = content.y + local.y)
    }

    private fun handleRects(overlay: CropRect, size: Double): List<Pair<HitTarget, CropRect>> {
        val half = size / 2.0
        val cx = overlay.x + overlay.width / 2.0
        val cy = overlay.y + overlay.height / 2.0
        val left = overlay.x
        val right = overlay.x + overlay.width
        val top = overlay.y
        val bottom = overlay.y + overlay.height
        fun rect(x: Double, y: Double) = CropRect(x - half, y - half, size, size)
        return listOf(
            HitTarget.NW to rect(left, top),
            HitTarget.N to rect(cx, top),
            HitTarget.NE to rect(right, top),
            HitTarget.E to rect(right, cy),
            HitTarget.SE to rect(right, bottom),
            HitTarget.S to rect(cx, bottom),
            HitTarget.SW to rect(left, bottom),
            HitTarget.W to rect(left, cy),
        )
    }

    private fun rotationHandleCenter(overlay: CropRect): Point2D.Double =
        Point2D.Double(overlay.x + overlay.width / 2.0, overlay.y - ROTATION_HANDLE_OFFSET)
}
